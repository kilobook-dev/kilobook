# Phase 3 — 주행 기록 (Trip 저장 · 종료 판정 · 용도 확인 · 누락 감지 · 백그라운드 기록)

확인일: 2026-11-01, 2026-11-04 / 에뮬레이터: Pleos Connect v2.1.0 (Android 14) / Vehicle SDK 2.3.2

## 데이터 (Room)

`trips` 테이블 (`data/Trip.kt`)

| 필드 | 내용 |
|---|---|
| `id` | 자동 증가 |
| `startOdometer`, `endOdometer` | 총 주행거리 (km, SDK 단위 그대로) |
| `startTime`, `endTime` | epoch ms |
| `purpose` | `BUSINESS` / `COMMUTE` / `PERSONAL` / `UNSET` |
| `status` | `OPEN` / `AWAITING_PURPOSE` / `DONE` |
| `source` | `DETECTED` (주행을 직접 관찰) / `GAP` (앱이 보지 못한 사이 늘어난 거리) |
| `askCount`, `lastAskedAt` | "나중에"를 누른 횟수와 시각 |
| `endReason` | `GEAR_PARK` / `PARKING_BRAKE` / `ODOMETER_IDLE` / `MANUAL_BUTTON` / `STALE_ON_RESTART` |

`app_state` 테이블: 한 줄 (`lastKnownOdometer`, `lastKnownAt`).

## 규칙 구현 (`recorder/TripRecorder.kt`)

모든 입력(주행거리, 기어·주차 브레이크·주행 규제, 버튼, 화면 표시/숨김)을 한 채널로 받아 순서대로 처리한다.
DB 읽기·쓰기가 서로 겹치지 않는다.

1. **시작**: 열린 Trip이 없고 총 주행거리가 늘면 `OPEN` Trip을 만든다. `startOdometer` = 늘기 직전 값.
2. **종료**: `GEAR_SELECTION`이 P → `GEAR_PARK`, `PARKING_BRAKE_ON`이 true → `PARKING_BRAKE`.
   둘 다 읽을 수 없으면 대체 규칙: 총 주행거리가 N분(디버그 1분, 화면에서 조절) 동안 그대로면 `ODOMETER_IDLE`
   (종료 시각 = 마지막으로 바뀐 시각), 또는 큰 "주행 종료" 버튼 → `MANUAL_BUTTON`.
   에뮬레이터는 기어를 읽을 수 있으므로 디버그 설정 "대체 규칙 강제"로 대체 규칙을 시험한다.
   무변화 판정은 폴링이 아니라 마지막 변화 시각 기준으로 한 번만 예약하는 지연 작업이다.
3. **용도 확인**: 종료되면 `AWAITING_PURPOSE`가 되고 카드(업무/출퇴근/개인/나중에, 높이 72dp)가 뜬다. 선택하면 `DONE`.
4. **나중에**: `askCount`+1, `lastAskedAt` 기록, 이번 기회에는 카드를 숨긴다.
   다시 묻는 기회 = 화면이 다시 보일 때(`onStart`) 또는 주행 → 정차로 바뀔 때.
   주행 중(주행 규제 활성 또는 기어가 P가 아님)에는 카드를 띄우지 않는다.
   이 판정에 쓰는 신호를 하나도 읽지 못하면 정차로 보지 않는다 (모르면 묻지 않음).
5. **누락 감지**: 앱 시작·화면 복귀 때 현재 값이 `lastKnownOdometer`보다 0.1 km 이상 크고 열린 Trip이 없으면
   `GAP` Trip (`AWAITING_PURPOSE`)을 만든다. 화면에는 "기록되지 않은 주행 X km"로 표시.
6. **마지막 값 저장**: 값이 바뀔 때만 `lastKnownOdometer`와 `lastKnownAt`을 저장하고, 화면이 숨겨질 때(`onStop`) 한 번 더 저장한다.

추가 요구사항 A — **재시작 시 열린 Trip 정리**: 앱 시작 때 `OPEN` Trip이 있고 `lastKnownAt`으로부터
10분(디버그 1분, 화면에서 조절) 이상 지났으면 그 Trip을 `lastKnownOdometer`·`lastKnownAt` 기준으로 종료
(`STALE_ON_RESTART`, `AWAITING_PURPOSE`)하고, 그 뒤 늘어난 거리는 규칙 5로 별도 `GAP` Trip이 된다.
덜 지났으면 같은 Trip을 이어서 기록한다.

**총 주행거리 감소 값**: 실행 중에는 현재 값보다 작은 값을 쓰지 않는다 (Trip 시작·저장 안 함).
로그와 화면("무시한 총 주행거리 감소 값 N회")에 남긴다. 아래 "SDK 호출 결과"의 재시작 직후 이전 값 콜백 때문.
앱 시작 때 받은 첫 값이 마지막 기록보다 작으면 (에뮬레이터 재부팅으로 콘솔 값이 0이 된 경우) 그 값을 새 기준으로 쓴다.

## 백그라운드 기록 (추가 요구사항 B)

### Pleos 정책 ("앱 구조 및 동작 모델" 문서 요약)

- 화면에서 사라진 앱은 종료되지 않고 백그라운드에 남을 수 있으며, 다시 포그라운드로 올 수 있음을 고려해야 한다.
- 백그라운드 전환 시 화면 갱신·애니메이션·불필요한 센서 수집처럼 바로 멈출 수 있는 작업은 정리한다.
- 백그라운드에서도 계속할 작업(문서 예: 오디오 재생, 통신, 다운로드)은 **작업별 실행 조건과 중단·재개 정책을 명확히 정의**한다.
- 백그라운드에서 불필요한 상주, 타이머, 폴링, 네트워크 요청을 최소화한다.
- 앱이 종료되거나 프로세스가 다시 만들어져도 사용자 데이터와 진행 상태를 복원할 수 있어야 한다.

판단: 백그라운드 작업을 금지하지 않고 조건을 요구하므로 허용으로 보고, 기록을 Foreground Service에서 하도록 바꿨다.
차량 신호 관찰용 Foreground Service 유형이나 상주 서비스 심사 기준은 문서에 없다 → Pleos 확인 필요.

### 구성

```
화면(DashboardViewModel) ─┐
                          ├─ RecordingSession (소유자 수를 셈) ─ VehicleMonitor + TripRecorder
RecorderService (FGS) ────┘
```

- 기록 세션은 화면과 서비스 중 하나라도 잡고 있으면 유지된다. Phase 2와 달리 화면을 닫아도 서비스가 있으면 SDK를 해제하지 않는다.
- 실행 조건: 화면을 열 때 "백그라운드 기록"이 켜져 있으면 서비스를 시작한다. 디버그 스위치로 끄면 멈춘다.
- 서비스 안에서도 이벤트 방식이다 (차량 리스너만 사용). 타이머는 대체 규칙의 무변화 판정 하나뿐이고 열린 Trip이 있을 때만 존재한다.
- 프로세스가 다시 만들어져도 기록은 Room에 있고, 열린 Trip은 요구사항 A로 정리된다.

### 매니페스트 선언 (`app/src/main/AndroidManifest.xml`)

| 선언 | 위치 | 목적 |
|---|---|---|
| `uses-permission pleos.car.permission.CAR_DRIVE` | manifest | Odometer API |
| `uses-permission android.car.permission.CAR_POWERTRAIN` | manifest | 기어, 주차 브레이크 |
| `uses-permission android.car.permission.CAR_VENDOR_EXTENSION` | manifest | 결과 기록용 (거부됨) |
| `uses-permission android.permission.FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | manifest | 백그라운드 기록 |
| `meta-data distractionOptimized=true` | `<activity .MainActivity>` 안 | 주행 중 화면 유지 (DO) |
| `service .service.RecorderService foregroundServiceType="specialUse"` + `property PROPERTY_SPECIAL_USE_FGS_SUBTYPE` | `<application>` 안 | 백그라운드 기록 |

`POST_NOTIFICATIONS`는 요청하지 않는다. 서비스는 동작하지만 알림은 표시되지 않는다 (아래 "남은 확인 사항").

## 화면

위에서부터: 실시간 패널(Phase 2를 줄인 것) → 용도 확인 카드 → "주행 종료" 버튼(대체 규칙일 때만) →
주행 기록 목록(최신순, 상태 배지, 종료 이유, "나중에" 횟수) → 기록 상태·설정(디버그).

## 테스트 결과 (Virtual Console + adb)

| # | 시나리오 | 결과 | 근거 (로그·화면) |
|---|---|---|---|
| T1 | D → 총 주행거리 증가 → P | 통과 | 20.0 → 21.2 km Trip이 `GEAR_PARK`로 종료, 카드 표시, "업무" → `DONE` |
| T2 | "나중에" | 통과 | `askCount` 1→3. 누르면 숨김 → 주행→정차 전환 시 다시 표시, 화면 복귀 시 다시 표시. D에서는 숨김 ("주행 중이라 숨김") |
| S7a | 앱이 다른 앱(차량 제어) 뒤에 있을 때 주행 | 통과 | 22.0 → 23.0 km Trip 기록, 앱으로 돌아오자 카드 2건 |
| S7b | 앱 창을 닫고(`am stack remove`) 서비스만 남았을 때 주행 | 통과 | 세션 소유자 `[service]`, 23.0 → 24.5 km Trip 기록. dumpsys: `isForeground=true types=0x40000000`(specialUse) |
| T3 | 앱 전체 강제 종료(서비스 포함) 중 주행 → 다시 실행 | 통과 (버그 수정 후 재확인) | 0.8 km `GAP` Trip. 수정 전에는 아래 SDK 이전 값 콜백 때문에 가짜 Trip이 하나 더 생겼음 |
| 저장 | 화면이 숨겨질 때 한 번 더 저장 | 통과 | `lastKnownOdometer=… 저장 (화면 숨김)` |
| T4 | 요구사항 A: 열린 Trip을 둔 채 강제 종료 → 70초 뒤 거리 증가 → 실행 | 통과 | 3.0 → 3.5 km Trip이 `STALE_ON_RESTART`(종료 시각 = `lastKnownAt`), 이후 0.7 km는 별도 `GAP` |
| T4b | 같은 상황에서 6초 만에 다시 실행 | 통과 | 같은 Trip을 이어서 기록, P에서 4.2 → 5.0 km `GEAR_PARK` |
| T5a | 대체 규칙 강제 + 1분 무변화 | 통과 | 강제 모드에서는 P로 바꿔도 종료 안 됨. 마지막 변화 60초 뒤 `ODOMETER_IDLE`, 종료 시각 = 마지막 변화 시각 |
| T5b | 대체 규칙 강제 + "주행 종료" 버튼 | 통과 | `MANUAL_BUTTON` |
| S6 사전 확인 | 에뮬레이터 재부팅 뒤 기록 유지 | 통과 | 기록 6건 유지. 콘솔 값 0 km를 새 기준으로 받음 |
| — | 주차 브레이크로 종료 | 검증 불가 | 콘솔에 조작 항목이 없어 항상 `false`. 판정 코드만 있음 |

에뮬레이터가 비정상 종료됐을 때(응답 감시로 스스로 종료): 종료 직전 약 1분 안에 저장한 내용(Trip 시작, `lastKnownOdometer` 2.3 km)이 사라졌다.
`dumpsys dbinfo`: `journalMode=WAL, syncMode=NORMAL` (Room·Android 기본값). 이 모드는 갑작스러운 종료 때 마지막 커밋이 사라질 수 있다.
다음 실행에서 마지막으로 남은 값(1.8 km)부터 늘어난 1.2 km가 `GAP` Trip으로 잡혀 거리 자체는 빠지지 않았다.

## SDK 호출 결과 (Phase 3에서 새로 알게 된 것)

| API | 결과 | 비고 |
|---|---|---|
| `getCurrentDistanceDriven` (앱 시작 직후) | 성공 | 현재 값을 줌 |
| `registerDistanceDriven` 첫 콜백 (앱 강제 종료 뒤 다시 실행) | 이전 값 | 앱이 꺼지기 전 마지막 값을 먼저 줌 (예: 현재 1.8인데 1.0). 약 1초 뒤 현재 값. 3번 재현. 감소 값 무시 규칙으로 처리하고 화면에 횟수 표시 |
| `registerDistanceDriven` 반복 | 성공 | 값이 그대로여도 약 1~4초마다 같은 값이 옴. 앱은 바뀐 값만 처리 |
| `GEAR_SELECTION` | 성공 | 종료 판정의 주 신호 |
| `PARKING_BRAKE_ON` | 성공: `false` | 바꿔볼 수 없음 |

## Virtual Console에 대해 알게 된 점

- 콘솔 웹 페이지가 열려 있어야 값을 바꿀 수 있다. 페이지가 1초마다 `POST /browser/session`을 보내고, 3초 넘게 끊기면
  `{"error":"emulator_inactive"}`로 거부한다. 자동 테스트는 같은 요청을 스크립트로 보냈다.
- 에뮬레이터를 다시 켜면 콘솔 주행거리가 0으로 돌아간다 (실제 차에는 없는 일). 이후 다시 올릴 수 있다.
- 콘솔 값은 float라 21.2가 21.200001로 저장된다. 앱은 SDK의 Float를 문자열로 바꿔 Double로 읽어 21.2로 유지한다.

## 남은 확인 사항

- Pleos 문의: Foreground Service 유형(`specialUse`) 허용 여부, 상주 서비스 심사 기준, DO 선언 배포 허용 여부.
- 서비스 알림 표시: `POST_NOTIFICATIONS`를 요청하지 않는다. 확인 결과 서비스는 포그라운드로 동작하지만 (`isForeground=true`)
  `dumpsys notification`에 이 앱의 알림 기록이 없다 (화면에 알림이 뜨지 않음). 알림을 보여야 하는지는 Pleos 정책 확인 필요.
- 비정상 종료 때 마지막 저장 손실: `synchronous=FULL`로 바꾸면 줄일 수 있다 (쓰기 비용 증가). 지금은 누락 감지가 거리를 메운다.
