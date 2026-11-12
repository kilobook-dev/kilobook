# 시연 영상: 터미널로 차량 신호 보내기

녹화 중에 Virtual Console 페이지를 오가지 않도록, 장면마다 명령 하나로 기어와 총 주행거리를 바꾼다.
명령을 실행하는 동안 터미널에는 앱이 남긴 로그(주행 시작·종료, 음성 질문, 누락 감지, 부팅 시작)가 함께 나온다.

스크립트: [`scripts/demo.sh`](../../scripts/demo.sh) (macOS·Linux의 bash, `adb`, `curl`이 필요하다)

## 준비

1. 에뮬레이터(`Pleos_Connect_v2.1.0`)를 켜고 앱을 설치한 뒤 한 번 연다: `adb install -r apk/KiloBook_KiloBook_release.apk`
2. 음성으로 답하는 모습을 찍으려면 마이크를 설정한다 ([README 3절 "마이크"](../../README.md#3-테스트-환경-및-필요한-설정)).
   Extended Controls › Microphone › **Enable Host Microphone Access** 켬, **Simulate Insert Headset** 끔, 컴퓨터 입력은 **내장 마이크**.
3. `scripts/demo.sh start`: 포트 연결(`adb forward tcp:8088 tcp:8088`)과 콘솔 신호. Virtual Console 페이지는 열지 않아도 된다.
4. (선택) `scripts/demo.sh reset`: 에뮬레이터에 있는 킬로북 기록과 설정을 지우고 앱을 다시 연다 (확인을 묻는다).
5. 화면 배치: 에뮬레이터 창과 터미널을 나란히 둔다. 질문 음성은 컴퓨터 스피커(또는 이어폰)로 나오므로, 녹화 도구가 시스템 소리를 담는지 먼저 확인한다.

## 장면

| # | 명령 | 보내는 신호 | 화면과 소리 | 시간 |
|---|---|---|---|---|
| 1 | `scripts/demo.sh drive [km]` (기본 1.6) | 기어 D → 1초 뒤부터 총 주행거리를 8초에 걸쳐 올림 | "● 기록 중 · 이번 주행 X km" | 약 12초 |
| 2 | `scripts/demo.sh park` | 기어 P | 용도 카드 + "이번 주행 일 점 육 킬로미터, 업무였나요?" → 7초 동안 듣기 | 약 16초 |
| 3 | `scripts/demo.sh reboard` | 홈 화면으로 나감 → 앱 프로세스 종료(차량 전원 끔) → 정차 2분 뒤 앱 열기 | 남은 주행 카드 + "용도 확인이 필요한 주행이 1건 있어요" | 최대 2분 + 12초 |
| 4 | `scripts/demo.sh missed [km]` (기본 0.7) | 앱 종료 → D → 총 주행거리 증가 → P → 앱 열기 | "기록되지 않은 주행 · 앱이 꺼져 있던 동안 늘어난 거리" 카드 + 대기 안내 | 약 20초 |
| 5 | `scripts/demo.sh boot [km]` (기본 1.0) → 에뮬레이터 다시 켜기 | 부팅 확인 → D → 총 주행거리 증가 → P (앱은 열지 않음) | 터미널: `BOOT_COMPLETED` → 기록 서비스 시작, 앱 화면 0개, 주행 종료. 그 뒤 아이콘으로 앱을 열면 카드 | 부팅 + 약 1분 |

- **장면 2**: 답하면(업무 / 출퇴근 / 개인) 버튼을 누른 것과 같이 저장된다. 장면 3을 찍으려면 **답하지 않는다**.
- **장면 3**: 정차 뒤 2분 안에 다시 열면 앱은 방금 끝난 주행으로 보고 "이번 주행 …, 업무였나요?"를 다시 묻는다.
  그래서 스크립트가 정차 2분 뒤까지 기다린다 (영상에서는 잘라 낸다). `reboard --now`는 기다리지 않는다.
- **장면 3·4의 앱 종료**는 `am force-stop`이다. 차량 전원을 끄거나 앱이 꺼진 것과 같아서, 백그라운드 기록 서비스도 함께 멈춘다.
- **장면 5**: 명령을 먼저 실행해 두고, Android Studio Device Manager에서 에뮬레이터를 ■(정지) → ▶(시작)으로 다시 켠다 (이 AVD는 항상 Cold Boot).
  켜진 지 3분이 넘은 에뮬레이터면 다시 켜지기를 기다리고, 3분 안이면 바로 진행한다. 이미 다시 켠 뒤 늦게 실행했다면 `boot --now`.
  2026-11-12 v1.1.1 시험(S8)을 이 명령으로 진행했다: 부팅 17초 뒤 `BOOT_COMPLETED` → 기록 서비스 시작 → 앱 화면 0개로 1.0 km 기록.
  부팅 뒤에는 **앱을 열지 않는다.** 강제 종료한 뒤 한 번도 열지 않은 앱은 부팅 신호를 받지 못하므로, 장면 3·4처럼 앱을 연 뒤에 한다.

## 그 밖의 명령

| 명령 | 하는 일 |
|---|---|
| `scripts/demo.sh open` | 앱 열기 (아이콘을 누른 것과 같음) |
| `scripts/demo.sh status` | 총 주행거리, 기어, 콘솔 상태, 앱이 맨 앞인지 |
| `scripts/demo.sh reset` | 에뮬레이터의 킬로북 기록과 설정 지우기 (확인을 묻는다) |
| `scripts/demo.sh stop` | 콘솔 신호 멈춤 |

## 터미널 출력 예

```
[18:51:52] 장면 2. 정차: 기어 P
  질문이 끝나면 7초 동안 "업무" / "출퇴근" / "개인"으로 답할 수 있습니다.
  18:51:52  앱 로그 · Recorder: 주행 종료 #1 (GEAR_PARK): 2.6 → 4.2 km
  18:51:53  앱 로그 · TextToSpeech.speak("이번 주행 일 점 육 킬로미터, 업무였나요?") -> requested
  18:52:03  앱 로그 · SpeechToText: 결과 없음 (듣기 7초, RMS 69회 최소 1.1 최대 169.1)
  답하지 않았습니다. 카드가 남아 있습니다 (장면 3: scripts/demo.sh reboard).
```

```
[18:54:28] 앱 열기
  18:54:29  앱 로그 · RecorderService: startForeground(specialUse) -> ok
  18:54:30  앱 로그 · Recorder: 시작: getCurrentDistanceDriven 4.9 km, 마지막 기록 4.2 km
  18:54:30  앱 로그 · Recorder: 누락 감지 #2: 기록되지 않은 주행 0.7 km (4.2 → 4.9)
```

## 동작 방식

- Virtual Console 페이지가 보내는 HTTP 요청을 그대로 보낸다: `PUT /vehicle/gear?gear=Drive|Park`, `PUT /vehicle/odometer?traveled_distance_km=…`, 상태 읽기 `GET /vehicle/odometer`, `GET /vehicle/state`.
  공식 API 문서에 있는 것이 아니라 Pleos Connect Emulator v2.1.0의 콘솔 페이지에서 확인한 요청이므로, 에뮬레이터 버전이 바뀌면 달라질 수 있다.
- 콘솔은 페이지가 열려 있다는 신호(`POST /browser/session`, 1초마다)가 3초 넘게 없으면 값 변경을 `{"error":"emulator_inactive"}`로 거부한다.
  `start`와 각 장면 명령이 이 신호를 백그라운드로 보내고, `stop`이 멈춘다.
- 앱 로그는 `adb logcat -s KiloBook:I`에서 주행·음성·누락·부팅 줄만 골라, 장면을 시작한 시각부터 보여 준다. 에뮬레이터 로그는 지우지 않는다 (나중에 원인을 볼 수 있게).
- 환경 변수: `DEMO_PORT`(컴퓨터 쪽 포트, 기본 8088), `ANDROID_HOME`(`adb`가 PATH에 없을 때).

## 문제 해결

| 증상 | 할 일 |
|---|---|
| "콘솔이 준비되지 않았습니다" | 에뮬레이터가 막 켜졌으면 잠시 뒤 다시 실행. `scripts/demo.sh status`로 확인 |
| "콘솔이 거부했습니다: emulator_inactive" | `scripts/demo.sh start` |
| 질문이 나오지 않음 | 앱 화면이 맨 앞이어야 하고 설정 › 음성 안내가 켜져 있어야 한다. 총 주행거리가 늘지 않은 D → P는 주행이 아니다 |
| 음성 답을 못 알아들음 | README 마이크 설정. 블루투스 이어폰 마이크로는 인식되지 않았다 |
| 주행 하나가 둘로 나뉨 | 한 번 관찰했고 원인 미확인 (첫 거리 값 직후 기어 P 신호가 한 번 들어옴). 다시 찍는다. 원인을 보려면 `adb logcat -s KiloBook:I`의 `onSelected`·`onChangeEvent(GEAR_SELECTION)` 줄 ([docs/phase7](../phase7/README.md) 6절) |
| 장면 5가 다시 켜기를 기다리지 않고 바로 진행 | 에뮬레이터를 켠 지 3분 안이면 이미 다시 켠 것으로 본다. 켠 지 3분이 지난 뒤 실행하고 다시 켠다 |
