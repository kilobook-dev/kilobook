# Phase 4 — 디버그 로그와 검증 시나리오 S1~S8

확인일: 2026-11-04 / 에뮬레이터: Pleos Connect v2.1.0 (Android 14) / Vehicle SDK 2.3.2

## 디버그 로그

화면 맨 아래 "디버그 로그 N건" → **펼치기**. 최신순, 최근 300건, 한 줄 = 시각 · 호출(또는 단계) · 결과.

- "전체 보는 중" 버튼을 누르면 **SDK·차량 API만** 보도록 바뀐다 (기록 판단, 세션, 서비스, 부팅 로그를 뺀다).
- "로그 지우기"는 화면 로그만 지운다. logcat에는 같은 내용이 남는다: `adb logcat -s OdologSpike:I`
- 같은 내용이 연달아 오면 한 줄로 묶는다. 결과 뒤에 `(×6, 처음 04:15:24.433)`처럼 횟수와 처음 시각이 붙고, 맨 앞 시각은 마지막으로 온 시각이다.
  총 주행거리 콜백은 값이 그대로여도 몇 초마다 다시 오기 때문이다. logcat에는 묶지 않고 모두 남는다.
- 로그는 앱 프로세스 메모리에 있다. 앱이 강제 종료되면 비워지고, 부팅으로 서비스가 먼저 시작된 경우에는 그때부터 쌓여 있다.

예:

```
10:12:58.248  getCurrentDistanceDriven                 1.8
10:12:58.256  Recorder: 시작: getCurrentDistanceDriven 1.8 km, 마지막 기록 1.0 km
10:12:58.282  registerDistanceDriven                   onDistanceDrivenUpdated(1.0)
10:12:58.284  Recorder: 총 주행거리 감소 값 무시: 1.8 → 1.0 km
10:13:20.433  registerDistanceDriven                   onDistanceDrivenUpdated(1.8)  (×5, 처음 10:12:59.433)
```

## 시작하기 전에 (모든 시나리오 공통)

1. 에뮬레이터 `Pleos_Connect_v2.1.0`을 켠다 (Android Studio Device Manager, Cold Boot 권장).
2. Virtual Console을 연다: 터미널에서 `adb forward tcp:8088 tcp:8088` → 브라우저에서 http://127.0.0.1:8088
3. 앱 목록에서 **오도로그 스파이크**를 연다 (또는 `adb shell am start -n com.odolog.spike/.MainActivity`).

콘솔 조작

- 기어: **Gear** 버튼 (Park / Reverse / Neutral / Drive)
- 총 주행거리: **Odometer** 항목 (화면 슬라이더는 1 km 단위). 소수점이 필요하면 터미널에서
  `curl -X PUT "http://127.0.0.1:8088/vehicle/odometer?traveled_distance_km=12.5"`

### 주의사항

- **콘솔 페이지를 열어 둔 채로 한다.** 페이지가 1초마다 신호를 보내고, 3초 넘게 끊기면 값 변경이 `{"error":"emulator_inactive"}`로 거부된다.
- **에뮬레이터를 다시 켜면 콘솔의 총 주행거리가 0으로 돌아간다** (실제 차에는 없는 일). 앱은 시작할 때 받은 값이 마지막 기록보다 작으면 그 값을 새 기준으로 쓴다.
  총 주행거리는 줄일 수 없다 (`traveled_distance_must_not_decrease`).
- **Android Studio의 Stop(■) 버튼은 강제 종료다.** 화면과 백그라운드 서비스가 함께 꺼진다. 강제 종료된 앱은 한 번 다시 열기 전까지 부팅 신호도 받지 못한다 (S8 전에 주의).
- **재부팅은 `adb reboot`으로 하지 않는다.** 이 에뮬레이터는 `adb reboot` 9초 뒤 그래픽 모듈에서 크래시하며 꺼졌다
  (`libgfxstream_backend` SIGSEGV). 에뮬레이터 창을 닫고 Device Manager에서 다시 켠다.
  이 AVD는 설정상 항상 Cold Boot로 켜진다 (`fastboot.forceColdBoot=yes`). 다시 켠 뒤 `adb forward tcp:8088 tcp:8088`과 콘솔 페이지 새로고침을 다시 한다.
- 에뮬레이터 시간대가 `America/Los_Angeles`라 앱에 보이는 시각이 한국 시간보다 17시간 이르다 (11월 기준). 기기 시계가 틀린 것은 아니다.
- 이 차량 홈 화면에서는 Back 키로 앱이 닫히지 않는다. 다른 앱 뒤로 보내려면 하단 독에서 다른 앱(예: 차량 제어)을 누른다.
- **확인 대기 주행이 여러 건이면 카드가 길어진다.** 아래 목록과 설정은 스크롤해서 본다 (디자인은 범위 밖이라 그대로 둠).
- 깨끗한 상태에서 시작하려면 화면 아래 설정 영역의 **기록 초기화**를 누른다.
- 설정 영역의 기본값: 무변화 종료 N=1분, 재시작 정리 기준 1분(운영 10분), 대체 규칙 강제 꺼짐, 백그라운드 기록 켜짐.

## S1 — 총 주행거리 읽기

1. 콘솔 Odometer를 현재보다 큰 값(예: +1 km)으로 바꾼다.
2. 앱을 연다.

예상 결과

- "총 주행거리"에 콘솔 값이 보인다. 옆 줄: `Odometer.getCurrentDistanceDriven / registerDistanceDriven → Supported · accessType=READ`
- "시작 기준값"에 같은 값 (getCurrentDistanceDriven 결과).
- 디버그 로그: `checkCurrentDistanceDrivenCapabilities → CapabilityConfig(accessType=READ)`, `getCurrentDistanceDriven → (값)`
- 트립 주행거리·주행시간 줄은 `Unsupported · ERROR_NOT_SUPPORTED_PROPERTY` (이 에뮬레이터에서 정상 결과).

## S2 — 실시간 갱신

1. 앱을 연 채로 콘솔 Odometer를 +1 km 바꾼다.

예상 결과

- 1~4초 안에 "총 주행거리"가 바뀌고 갱신 시각이 바뀐다.
- 디버그 로그: `registerDistanceDriven → onDistanceDrivenUpdated(값)`. 값이 그대로여도 몇 초마다 같은 값이 다시 오며, 그때는 같은 줄의 `(×N)`만 늘어난다 (앱은 바뀐 값만 처리).

## S3 — 기어 P로 주행 종료

1. Gear **Drive** → 화면 "기어 D", "주행 규제 주행".
2. Odometer +1 km → 목록 맨 위에 "진행 중" 주행.
3. Gear **Park**.

예상 결과

- 카드 "용도 확인이 필요한 주행 1건" (날짜·시각·거리) + 업무 / 출퇴근 / 개인 / 나중에.
- "업무"를 누르면 카드가 사라지고 목록 배지가 "완료", "용도 업무", "종료 기어 P".
- 디버그 로그: `Recorder: 주행 시작 #n`, `Recorder: 주행 종료 #n (GEAR_PARK)`, `Recorder: 용도 선택 #n: BUSINESS`

## S4 — 앱이 꺼진 동안 늘어난 거리 (누락 감지)

1. Gear Park 상태에서 Android Studio **Stop(■)** 으로 앱을 끈다.
2. 콘솔 Odometer +1 km.
3. 앱을 연다.

예상 결과

- 카드에 "기록되지 않은 주행 1.0 km · (마지막 기록 시각) ~ (지금) 사이", 목록 배지 "기록되지 않은 주행".
- 디버그 로그: `Recorder: 시작: getCurrentDistanceDriven X km, 마지막 기록 Y km`, `Recorder: 누락 감지 #n`
- `Recorder: 총 주행거리 감소 값 무시: …`가 함께 보일 수 있다. 다시 실행한 직후 SDK 첫 콜백이 이전 값을 주기 때문이며, 설정 영역 "무시한 총 주행거리 감소 값" 횟수가 늘어난다 (기록에는 영향 없음).

## S5 — 답하지 않은 주행 다시 묻기

1. S3처럼 주행을 하나 만들고 P에서 카드가 뜨면 **나중에**.
2. 카드가 사라지고 목록에 "나중에 1회".
3. 다시 묻는 기회 — 아래 둘 중 하나:
   - 하단 독에서 다른 앱을 띄웠다가 오도로그로 돌아온다 (화면 복귀).
   - Gear Drive → Park (주행 → 정차).
4. Gear Drive인 동안에는 카드가 안 보여야 한다.

예상 결과

- 3에서 카드가 다시 뜬다. 디버그 로그 `Recorder: 다시 물을 기회: 화면이 다시 보임` 또는 `…: 정차로 바뀜`.
- 4에서 설정 영역에 "확인 대기 N건 (주행 중이라 숨김)".
- 앱을 Stop 후 다시 열어도 확인 대기 주행은 그대로 남아 다시 물어본다.

## S6 — 에뮬레이터 재부팅 후 기록 유지

1. 기록이 몇 건 있는 상태에서 Device Manager → **Cold Boot Now**.
2. 콘솔 Odometer가 0으로 돌아간 것을 확인한다.
3. 앱을 연다.

예상 결과

- 목록의 기록과 확인 대기 주행이 그대로 있다.
- 디버그 로그: `Recorder: 시작 값(0.0 km)이 마지막 기록(X km)보다 작음. 현재 값을 새 기준으로 사용`
- 참고: 에뮬레이터가 **비정상 종료**되면 직전 약 1분 안에 저장한 내용이 사라질 수 있다 (DB 동기화 모드 NORMAL).
  이 경우에도 다음 실행 때 사라진 거리만큼 "기록되지 않은 주행"이 생긴다.

## S7 — 앱이 화면에 없을 때 기록 (백그라운드)

설정 영역에 "백그라운드 서비스 실행 중"인지 먼저 확인한다.

A. 다른 앱 뒤에 있을 때

1. 하단 독에서 차량 제어 앱을 띄워 오도로그를 가린다.
2. Gear Drive → Odometer +1 km → Gear Park.
3. 오도로그로 돌아온다.

B. 앱 창을 닫았을 때 (서비스만 남음)

1. 터미널: `adb shell am stack list | grep odolog` → `taskId=1000057: com.odolog.spike/com.odolog.spike.MainActivity …` 같은 줄이 나온다.
   그 번호로 `adb shell am stack remove 1000057`. 오도로그 창이 사라진다.
2. Gear Drive → Odometer +1 km → Gear Park.
3. 앱을 다시 연다.

예상 결과

- 두 경우 모두 돌아왔을 때 카드에 새 주행 (종료 기어 P).
- B의 디버그 로그: `Session: release(screen) -> owners=[service]` 다음에 `Recorder: 주행 시작 #n`, `주행 종료 #n (GEAR_PARK)`.
- 비교: 설정 영역에서 "백그라운드 기록: 켜짐"을 눌러 끄고 B를 하면, 그동안의 거리는 S4처럼 "기록되지 않은 주행"이 된다.

## S8 — 부팅 시 자동 시작 (앱을 열지 않음)

준비

- 앱을 한 번 연 상태여야 한다. **Stop(■)으로 끈 뒤에는 앱을 한 번 다시 열고** 진행한다 (강제 종료 상태의 앱은 부팅 신호를 받지 못함).
- 설정 영역 "백그라운드 기록: 켜짐".

1. 에뮬레이터 창을 닫고 Device Manager에서 다시 켠다 (Cold Boot). 부팅 후 **앱을 열지 않는다.** `adb reboot`은 쓰지 않는다 (위 주의사항).
2. 1분쯤 기다린 뒤 터미널: `adb shell dumpsys activity services com.odolog.spike | grep isForeground`
   (앱 창이 없는지도 확인: `adb shell am stack list | grep odolog` → 아무것도 안 나와야 함)
3. 앱을 열지 않은 채 Gear Drive → Odometer +1 km → Gear Park.
4. 앱을 연다.

예상 결과

- 2에서 `isForeground=true foregroundId=1 types=40000000` (`0x40000000` = specialUse). 앱 창은 없다.
  부팅 신호는 부팅 완료 뒤 바로 오지 않는다. 이번 확인에서는 약 12초, 이전 부팅에서는 1분 넘게 대기 중이었던 적이 있다.
  안 보이면 1~2분 더 기다린 뒤 다시 확인한다.
- 4에서 카드에 3의 주행 (종료 기어 P).
- 설정 영역: `부팅 시 자동 시작(S8): (시각) · android.intent.action.BOOT_COMPLETED · RecorderService.start(boot) → ok`
- 디버그 로그 맨 아래(가장 오래된 쪽): `BootReceiver: android.intent.action.BOOT_COMPLETED 수신 (UserHandle{10})`,
  `BootReceiver: RecorderService.start(boot) -> ok`, `RecorderService: onStartCommand(reason=boot)`,
  `RecorderService: startForeground(specialUse) -> ok`, `Session: acquire(service) -> owners=[service]`.
  앱을 열면 `Session: acquire(screen) -> owners=[service, screen]`이 더해진다.
- 설정 영역이 "BOOT_COMPLETED를 받은 기록 없음"이면: Stop(■) 뒤 앱을 열지 않았거나 (강제 종료 상태), 신호가 아직 안 왔다.
  "백그라운드 기록 꺼짐 -> 서비스 시작 안 함"이면 설정이 꺼져 있던 것이다.
  어느 경우든 앱을 열면 시작 기준값(`getCurrentDistanceDriven`)으로 그동안의 거리를 "기록되지 않은 주행"으로 정리한다 (S4와 같음).

## 결과 기록

확인일 2026-11-04, Virtual Console HTTP API + adb로 직접 실행.

| 시나리오 | 결과 | 근거 (로그·화면) |
|---|---|---|
| S1 | 통과 | `checkCurrentDistanceDrivenCapabilities → CapabilityConfig(accessType=READ)`, `getCurrentDistanceDriven → 2.0`. 트립 3종은 `ERROR_NOT_SUPPORTED_PROPERTY` |
| S2 | 통과 | `onDistanceDrivenUpdated` 1~5초 간격. 같은 값은 한 줄로 묶여 `(×6, 처음 04:15:24.433)` |
| S3 | 통과 | #16 2.0 → 2.5 km `GEAR_PARK`, 카드 표시 |
| S4 + 시작 기준값 | 통과 | 강제 종료 중 2.5 → 3.2 km. 다시 실행하자 `시작: getCurrentDistanceDriven 3.2 km, 마지막 기록 2.5 km` → `누락 감지 #17: 0.7 km (2.5 → 3.2)` → 첫 콜백 2.5는 `감소 값 무시`. 가짜 주행 없음 |
| S5 | Phase 3에서 확인 (T2) | Phase 4에서 관련 코드 변경 없음, 다시 하지 않음 |
| S6 | 통과 | 재부팅(아래 크래시 포함) 뒤 #16~#18 유지. `시작 값(0.0 km)이 마지막 기록(3.7 km)보다 작음. 현재 값을 새 기준으로 사용` |
| S7A | Phase 3에서 확인 | Phase 4에서 다시 하지 않음 |
| S7B | 통과 | `am stack remove 1000057` → `Session: release(screen) -> owners=[service]` → #18 3.2 → 3.7 km `GEAR_PARK`. `isForeground=true` |
| S8 | 통과 | 아래 |

### S8 — 부팅 시 자동 시작 결과

| 단계 | 시각 (KST) | 결과 |
|---|---|---|
| 에뮬레이터 시작 (명령줄 `emulator -avd Pleos_Connect_v2.1.0`, Cold Boot) | 21:21:51 | — |
| `sys.boot_completed=1` | 21:22:07 | — |
| `BOOT_COMPLETED` 수신 (user 10, 운전자 사용자) | 21:22:19 | `BootReceiver: … 수신 (UserHandle{10})` |
| `RecorderService.start(boot)` (`startForegroundService`) | 21:22:19 | ok (예외 없음) |
| `startForeground(…, FOREGROUND_SERVICE_TYPE_SPECIAL_USE)` | 21:22:19 | ok |
| `dumpsys activity services` | 21:22:28 | `isForeground=true types=40000000`, `startRequested=true`. 앱 창 0개, 프로세스 `u10_a175` |
| 앱을 열지 않은 채 D → 0.0 → 1.0 km → P | 21:22:56 ~ 21:23:02 | #19 `GEAR_PARK` 기록 |
| 앱을 엶 | 21:23:12 | 카드에 #19, 설정 영역 S8 줄 ok, 디버그 로그에 부팅 이후 51건 |

`dumpsys activity broadcasts history`: `BOOT_COMPLETED/u0`과 `BOOT_COMPLETED/u10`이 따로 보내진다. 앱은 user 10에서 받았다.

공식 문서 확인 (Android Developers)

- `BOOT_COMPLETED`·`LOCKED_BOOT_COMPLETED`·`MY_PACKAGE_REPLACED` 수신은 Android 12+ "백그라운드에서 Foreground Service 시작 제한"의 예외다.
- `BOOT_COMPLETED` 수신기에서 시작할 수 없는 유형: `dataSync`, `camera`, `mediaPlayback`, `phoneCall`, `mediaProjection`, `microphone`.
  Android 15 동작 변경 문서이며 (targetSdk 35 이상 + Android 15 기기), `microphone`은 Android 14부터. **`specialUse`는 목록에 없다.**
- 문서 간 차이: "백그라운드 시작 제한" 페이지는 "Android 14 이상을 타깃하면 BOOT_COMPLETED 수신기에서 특정 유형 시작이 제한된다"고 쓴다.
  어느 페이지 목록에도 `specialUse`는 없다.
- 이 에뮬레이터는 Android 14다. 앱은 targetSdk 35라 Android 15 기기에서는 Android 15 제한이 적용되지만, 목록상 `specialUse`는 대상이 아니다 (Android 15 기기에서는 미확인).
- 문서에 없는 것: 알림 권한(`POST_NOTIFICATIONS`)이 없을 때의 부팅 시작 — 이번 결과로는 문제없이 시작됐다 (알림은 표시되지 않음).
- 실제 차량과의 차이 (미확인): AAOS 전원 문서상 차량은 Suspend-to-RAM에서 깨어날 수 있다. 이때 `BOOT_COMPLETED`가 오는지는 문서에 없다.
  일반 앱이 받을 수 있는 "차량 깨어남" 신호도 문서에서 찾지 못했다 (`CarPowerManager`는 시스템 앱 전용).
  → 부팅 자동 시작은 동작하지만, 시동마다 반드시 온다는 보장이 없으므로 "앱을 열 때 누락분 정리"를 보완 경로로 유지한다. Pleos 문의 5번.

### 진행 중 생긴 일

- `adb reboot`으로 에뮬레이터가 크래시했다 (macOS 크래시 리포트 `qemu-system-aarch64-2026-11-04-211656.ips`, `EglContext::~EglContext` SIGSEGV).
  재부팅 직전에 호스트와 에뮬레이터 모두 `sync`를 해 두어 기록은 사라지지 않았다.
- 외장 SSD가 작업 중 강제 추출되어, 설치해 둔 Phase 4 빌드와 APK가 디스크에 남지 않았다 (에뮬레이터에는 Phase 3 앱이 남아 있었음).
  다시 빌드·설치한 뒤 매번 `sync`와 `adb shell sync`를 한다.
