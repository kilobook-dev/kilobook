#!/bin/bash
# Demo video helper: sends the Virtual Console signals from the terminal, one command per scene,
# and prints the app's own log lines while each scene runs. Usage and scenes: docs/demo/README.md
#
#   scripts/demo.sh start            port forward + console heartbeat, then status
#   scripts/demo.sh drive [km]       scene 1: D, odometer up (default 1.6 km)
#   scripts/demo.sh park             scene 2: P -> voice question
#   scripts/demo.sh reboard [--now]  scene 3: leave, car off, come back after 2 min -> waiting card
#   scripts/demo.sh missed [km]      scene 4: app off, drive (default 0.7 km), open -> missed trip
#   scripts/demo.sh boot [km] [--now] scene 5: after an emulator restart, record without opening the app (S8)
#   scripts/demo.sh open | status | reset | stop
set -uo pipefail

PKG=com.kilobook.app
ACTIVITY="$PKG/.MainActivity"
PORT="${DEMO_PORT:-8088}"            # host port; the console listens on 8088 inside the emulator
BASE="http://127.0.0.1:$PORT"
STATE_DIR="${TMPDIR:-/tmp}/kilobook-demo"
JUST_ENDED_SEC=120                   # VoiceAssistant.JUST_ENDED_MILLIS: reopened sooner, the app asks the question again
mkdir -p "$STATE_DIR"

if command -v adb >/dev/null 2>&1; then
  ADB=adb
else
  ADB="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}/platform-tools/adb"
  [ -x "$ADB" ] || { echo "adb를 찾을 수 없습니다. ANDROID_HOME을 설정하세요." >&2; exit 1; }
fi

say() { printf '\n[%s] %s\n' "$(date +%T)" "$*"; }
note() { printf '  %s\n' "$*"; }
die() { printf '\n중단: %s\n' "$*" >&2; exit 1; }

# --- console ---------------------------------------------------------------

odometer_json() { curl -s -m 5 "$BASE/vehicle/odometer"; }
json_field() { sed -nE "s/.*\"$1\":\"?([^,\"}]*).*/\1/p" | head -1; }
odo_now() { odometer_json | json_field traveled_distance_km | awk '{ printf "%.1f", $1 }'; }
gear_now() { curl -s -m 5 "$BASE/vehicle/state" | grep -oE '"actual":\{"value":"(Park|Reverse|Neutral|Drive)"' | head -1 | cut -d'"' -f6; }

put() {  # put PATH?QUERY: a console write; stops the scene if the console refuses it
  local out
  out=$(curl -s -m 5 -X PUT "$BASE$1") || die "콘솔에 연결할 수 없습니다 ($BASE). scripts/demo.sh start"
  case "$out" in *'"error"'*) die "콘솔이 거부했습니다: $out" ;; esac
}
gear() { put "/vehicle/gear?gear=$1"; }
odo() { put "/vehicle/odometer?traveled_distance_km=$1"; }

heartbeat_alive() { [ -f "$STATE_DIR/heartbeat.pid" ] && kill -0 "$(cat "$STATE_DIR/heartbeat.pid")" 2>/dev/null; }

ensure_console() {
  [ "$("$ADB" get-state 2>/dev/null)" = "device" ] || die "에뮬레이터가 연결되어 있지 않습니다 (adb devices)."
  "$ADB" forward "tcp:$PORT" tcp:8088 >/dev/null || die "adb forward 실패"
  if ! heartbeat_alive; then
    # The console page says "I am open" every second; without it writes fail with emulator_inactive.
    nohup bash -c "while :; do curl -s -m 2 -o /dev/null -X POST '$BASE/browser/session?client=kilobook-demo'; sleep 1; done" \
      >/dev/null 2>&1 &
    echo $! > "$STATE_DIR/heartbeat.pid"
  fi
  local i json
  for i in $(seq 1 20); do
    json=$(odometer_json)
    case "$json" in *'"emulator_active":true'*'"bridge_ready":true'*) return 0 ;; esac
    sleep 1
  done
  die "콘솔이 준비되지 않았습니다: $(printf '%s' "$json" | cut -c1-120)"
}

ramp() {  # ramp KM SECONDS: raise the odometer step by step, like driving
  local km=$1 secs=${2:-8} from to i v
  from=$(odo_now)
  to=$(awk -v a="$from" -v b="$km" 'BEGIN { printf "%.1f", a + b }')
  note "총 주행거리 $from → $to km (${secs}초)"
  for i in $(seq 1 "$secs"); do
    v=$(awk -v a="$from" -v b="$km" -v i="$i" -v n="$secs" 'BEGIN { printf "%.2f", a + b * i / n }')
    odo "$v"
    sleep 1
  done
}

# --- app -------------------------------------------------------------------

app_in_front() { "$ADB" shell dumpsys activity activities 2>/dev/null | grep -m1 topResumedActivity | grep -q "$PKG/"; }
app_windows() { "$ADB" shell dumpsys window windows 2>/dev/null | grep -c "$PKG/$PKG.MainActivity"; }
open_app() { "$ADB" shell am start -n "$ACTIVITY" >/dev/null; }
leave_app() { "$ADB" shell input keyevent KEYCODE_HOME; }
kill_app() { "$ADB" shell am force-stop "$PKG"; }

# App log lines worth showing on screen (AppLog tag KiloBook). Streamed live while a scene runs.
LOG_PATTERN='Recorder: (주행|누락|용도|나중에|시작:)|Voice:|TextToSpeech\.speak\(|SpeechToText -> onUpdated\(.*completed=true|SpeechToText: 결과 없음|BootReceiver|startForeground|감소 값 무시'
LOG_PID=
log_on() {
  # Start from now without clearing the device log, so earlier lines stay for later diagnosis.
  local since
  since=$("$ADB" shell "date '+%m-%d %H:%M:%S.000'" | tr -d '\r')   # one string: adb shell re-splits arguments
  : > "$STATE_DIR/scene.log"
  # The pattern goes through the environment: awk -v would turn "\(" into "(".
  "$ADB" logcat -v time -T "$since" -s KiloBook:I 2>/dev/null > >(LOG_PATTERN="$LOG_PATTERN" awk -v out="$STATE_DIR/scene.log" '
    {
      i = index($0, ": "); if (i == 0) next
      msg = substr($0, i + 2); t = substr($2, 1, 8)
      if (msg ~ ENVIRON["LOG_PATTERN"]) { printf "  %s  앱 로그 · %s\n", t, msg; print msg >> out; fflush(); fflush(out) }
    }') &
  LOG_PID=$!
}
log_off() {
  [ -n "$LOG_PID" ] || return 0
  kill "$LOG_PID" 2>/dev/null; wait "$LOG_PID" 2>/dev/null; LOG_PID=
  sleep 0.3
}
trap log_off EXIT
seen() { grep -qE "$1" "$STATE_DIR/scene.log" 2>/dev/null; }

# --- scenes ----------------------------------------------------------------

cmd_status() {
  say "상태"
  note "총 주행거리 $(odo_now) km, 기어 $(gear_now)"
  note "콘솔: $(odometer_json | grep -oE '"(emulator_active|bridge_ready)":(true|false)' | tr '\n' ' ')"
  note "콘솔 신호: $(heartbeat_alive && echo 보내는 중 || echo 꺼짐)"
  note "앱: $(app_in_front && echo '화면 맨 앞' || echo '화면에 없음')"
}

cmd_start() {
  say "준비: 포트 연결과 콘솔 신호"
  ensure_console
  cmd_status
}

cmd_drive() {
  local km=${1:-1.6}
  ensure_console
  app_in_front || note "참고: 앱 화면이 보이지 않습니다 (scripts/demo.sh open)."
  log_on
  say "장면 1. 주행 시작: 기어 D"
  gear Drive
  sleep 1   # shift into D first, then start moving
  ramp "$km" 8
  sleep 4
  log_off
  seen "주행 시작" && note "기록 중입니다. 다음: scripts/demo.sh park" || note "주행 시작 로그가 아직 없습니다. 몇 초 뒤 앱 화면을 확인하세요."
}

cmd_park() {
  ensure_console
  app_in_front || note "참고: 앱 화면이 보일 때만 음성으로 묻습니다."
  log_on
  say "장면 2. 정차: 기어 P"
  gear Park
  date +%s > "$STATE_DIR/park-time"
  note "질문이 끝나면 7초 동안 \"업무\" / \"출퇴근\" / \"개인\"으로 답할 수 있습니다."
  sleep 16
  log_off
  seen "주행 종료" || note "이번에 끝난 주행이 없습니다. 먼저 scripts/demo.sh drive"
  seen "용도 선택" && note "음성 답으로 저장했습니다." || note "답하지 않았습니다. 카드가 남아 있습니다 (장면 3: scripts/demo.sh reboard)."
}

cmd_reboard() {
  ensure_console
  say "장면 3. 차에서 내림: 화면 닫기, 차량 전원 끔 (앱 프로세스 종료)"
  leave_app
  sleep 2
  kill_app
  if [ "${1:-}" != "--now" ] && [ -f "$STATE_DIR/park-time" ]; then
    local left=$(( $(cat "$STATE_DIR/park-time") + JUST_ENDED_SEC + 5 - $(date +%s) ))
    if [ "$left" -gt 0 ]; then
      note "정차 2분 안에 다시 열면 \"이번 주행 …\"으로 다시 묻습니다. ${left}초 기다립니다 (영상에서는 잘라 냅니다)."
      while [ "$left" -gt 0 ]; do printf '\r  재탑승까지 %3d초 ' "$left"; sleep 1; left=$((left - 1)); done
      printf '\r  재탑승까지   0초 \n'
    fi
  fi
  log_on
  say "재탑승: 앱 열기"
  open_app
  sleep 12
  log_off
}

cmd_missed() {
  local km=${1:-0.7}
  ensure_console
  say "장면 4. 앱 종료 (기록 서비스도 멈춤)"
  leave_app
  sleep 2
  kill_app
  note "앱 프로세스: $("$ADB" shell pidof "$PKG" | tr -d '\r' | grep -q . && echo 남아 있음 || echo 없음)"
  say "앱이 꺼진 동안 주행: D → $km km → P"
  gear Drive
  sleep 1   # shift into D first, then start moving
  ramp "$km" 5
  gear Park
  sleep 2
  log_on
  say "앱 열기"
  open_app
  sleep 12
  log_off
  seen "누락 감지" || note "누락 감지 로그가 없습니다. 앱 화면과 디버그 로그를 확인하세요."
}

uptime_sec() { "$ADB" shell cut -d. -f1 /proc/uptime 2>/dev/null | tr -d '\r'; }

cmd_boot() {
  local km=1.0 now=0 up start i arg
  for arg in "$@"; do
    case "$arg" in --now) now=1 ;; "") ;; *) km=$arg ;; esac
  done
  up=$(uptime_sec)
  # Booted within 3 minutes (or --now): this is the restart. Otherwise wait for one.
  if [ "$now" = 0 ] && { [ -z "$up" ] || [ "$up" -gt 180 ]; }; then
    say "장면 5. 에뮬레이터 창을 닫고 Android Studio에서 다시 켜세요 (Cold Boot). 앱은 열지 않습니다."
    note "다시 켜지기를 기다립니다 (최대 15분). 이미 다시 켰다면 Ctrl+C 뒤 scripts/demo.sh boot --now"
    start=${up:-999999}
    for i in $(seq 1 180); do
      up=$(uptime_sec)
      if [ -n "$up" ] && [ "$up" -lt "$start" ] && [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then break; fi
      [ -n "$up" ] && [ "$up" -gt "$start" ] && start=$up
      sleep 5
    done
  else
    "$ADB" wait-for-device
  fi
  up=$(uptime_sec)
  [ -n "$up" ] && [ "$up" -le 900 ] || die "다시 켠 에뮬레이터를 찾지 못했습니다."
  say "부팅 완료 (가동 ${up}초). 앱을 열지 않고 기다립니다."
  for i in $(seq 1 60); do
    "$ADB" logcat -d -s KiloBook:I 2>/dev/null | grep -q "startForeground" && break
    sleep 3
  done
  "$ADB" logcat -d -v time -s KiloBook:I 2>/dev/null | grep -E "BootReceiver|startForeground" \
    | awk '{ i = index($0, ": "); printf "  %s  앱 로그 · %s\n", substr($2, 1, 8), substr($0, i + 2) }'
  note "앱 화면: $(app_windows)개 (앱을 열지 않음)"
  ensure_console
  log_on
  say "앱을 열지 않은 채 주행: D → $km km → P"
  gear Drive
  sleep 1   # shift into D first, then start moving
  ramp "$km" 5
  sleep 4
  gear Park
  sleep 5
  log_off
  note "앱 화면: $(app_windows)개. 앱을 열면 이 주행의 카드가 보입니다 (아이콘 또는 scripts/demo.sh open)."
}

cmd_open() { say "앱 열기"; open_app; }

cmd_reset() {
  printf '에뮬레이터에 있는 킬로북 기록과 설정을 모두 지웁니다. 계속할까요? [y/N] '
  read -r answer
  [ "$answer" = "y" ] || [ "$answer" = "Y" ] || { echo "취소했습니다."; return 0; }
  "$ADB" shell pm clear --user current "$PKG" >/dev/null || die "pm clear 실패"
  rm -f "$STATE_DIR/park-time"
  say "기록을 지웠습니다. 앱을 다시 엽니다."
  open_app
}

cmd_stop() {
  if heartbeat_alive; then kill "$(cat "$STATE_DIR/heartbeat.pid")"; fi
  rm -f "$STATE_DIR/heartbeat.pid"
  say "콘솔 신호를 멈췄습니다."
}

case "${1:-}" in
  start) cmd_start ;;
  drive) cmd_drive "${2:-}" ;;
  park) cmd_park ;;
  reboard) cmd_reboard "${2:-}" ;;
  missed) cmd_missed "${2:-}" ;;
  boot) shift; cmd_boot "$@" ;;
  open) cmd_open ;;
  status) cmd_status ;;
  reset) cmd_reset ;;
  stop) cmd_stop ;;
  *) sed -n '2,11p' "$0" | sed 's/^# \{0,1\}//'; exit 1 ;;
esac
