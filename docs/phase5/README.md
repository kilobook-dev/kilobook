# Phase 5 — 제출용 MVP 화면

확인일: 2026-11-09 / 에뮬레이터: Pleos Connect v2.1.0 (Android 14, 2560×1440, 160 dpi) / Vehicle SDK 2.3.2

기록 규칙(`recorder/`)과 차량 연결(`vehicle/`)은 바꾸지 않았다. 바뀐 것은 화면, 화면 설정, 예시 기록, DB 버전(1 → 2)이다.

## 화면 구조

```
킬로북  [홈] [기록] [설정] [디버그*]          * 설정 › 디버그 패널 보기가 켜져 있을 때만
├── 홈 (정차·알 수 없음)   용도 확인 카드(한 건씩) · 기록 상태 · 이번 달 요약
├── 홈 (주행 중)           "● 기록 중 · 이번 주행 X km"만. 탭은 홈(+디버그)만 남는다
├── 기록                   날짜별 목록, 계기판 전후 거리, 용도, "기록되지 않은 주행"·"예시" 표시, 용도 고르기
├── 설정                   디버그 패널 보기(저장됨) · 오픈소스 라이선스 · 앱 정보
│   └── 오픈소스 라이선스  라이브러리 목록 + Apache License 2.0 전문
└── 디버그                 Phase 2~4 화면(실시간 값·API 결과, 기록 상태·설정, 디버그 로그) + 예시 기록 버튼
```

| 화면 | 캡처 |
|---|---|
| 홈 (예시 기록 포함) | [01-home.png](01-home.png) |
| 주행 중 | [02-driving.png](02-driving.png) |
| 기록 (예시 기록 포함) | [03-trips.png](03-trips.png) |
| 설정 | [04-settings.png](04-settings.png) |
| 오픈소스 라이선스 | [05-licenses.png](05-licenses.png) |
| 디버그 | [06-debug.png](06-debug.png) |
| 홈, 세로 창 (내비와 1:1) | [07-home-portrait.png](07-home-portrait.png) |

## 5-0 기준 확인 (Pleos 디자인 가이드)

출처: Pleos Connect 개발자 가이드 › 디자인 가이드 (document.pleos.ai). 수치는 페이지 안 그림(SVG)에서 읽었다.

| 항목 | 문서 값 | 앱에 쓴 값 (`ui/theme/KiloTheme.kt`) |
|---|---|---|
| Typography › Headline | Large 60 / Medium 56 / Small 48 (Extra Bold, Bold) | 주행 중 거리·카드 거리 60, 요약 값 48 |
| Typography › Title | 40 / 36 / 32, 줄 높이 52 / 44 / 40 | 화면·카드 제목 32, 주행 중 "기록 중" 40 |
| Typography › Body | 30 / 28 / 26 | 본문 26 |
| Typography › Label | Medium 24 (줄 높이 28) / Small 20 (줄 높이 24) | 보조 글자 24, 가장 작은 글자 20 |
| 최소 글자 | 12sp (가이드가 인용한 Android 접근성 기준), 실제 높이 18~20px 이상 권장 | 운전자 화면은 20 이상. 디버그 탭만 12~16 유지 |
| Basic button 크기 | Large 100 / Medium 80 / Small 64 (그림에서 잰 높이) | 용도 버튼·주행 종료 100, 탭·목록 버튼·설정 줄 80 |
| 최소 터치 크기 | **문서에 수치 없음** (Pleos 디자인 가이드, Android for Cars 디자인 문서 모두) | 위 버튼 크기를 기준으로 씀. 설정의 스위치는 줄 전체(높이 100)를 누르는 영역으로 함 |
| 운전 중 원칙 | 동영상·애니메이션·자동 스크롤 금지, 한 손 조작, 1~2초 안에 파악, 입력 응답 0.25초 이내 | 주행 중 화면은 글자 3줄, 버튼 없음, 움직이는 요소 없음 |
| 창 | 앱 창은 가로·세로 두 레이아웃에 맞아야 함 (Display Layout) | 폭 1100dp 미만이면 요약 칸을 2×2로. 세로 창(07)에서 확인 |

- 문서 값의 단위는 px이다. 이 에뮬레이터 화면은 160 dpi라 1 px = 1 dp다. 다른 밀도의 실제 차량 화면에서는 값이 다를 수 있다 (미확인).
- Headline·Body의 줄 높이는 문서에 없어 글자 크기의 약 1.2배로 정했다.
- 시스템 글꼴(Asta Sans)은 넣지 않았다 (디자인 범위 밖).

## 한 일

| # | 내용 | 파일 |
|---|---|---|
| 5-1 | 화면을 홈 / 기록 / 설정 / 디버그로 나눔. 디버그 탭은 설정 스위치로 켜고, 값은 SharedPreferences에 저장 (기본 꺼짐) | `ui/MainScreen.kt`, `DisplaySettings.kt` |
| 5-2 | 정차 시 용도 확인 카드: 방금 끝난 주행 1건, 거리 60 크기, 업무 / 출퇴근 / 개인 / 나중에 (높이 100) | `ui/HomeScreen.kt` |
| 5-3 | 확인 대기가 여러 건이면 최신부터 한 건씩. "확인 대기 N건" 표시. 한 카드에는 한 번만 답하고, 새 카드가 뜬 뒤 0.5초 동안은 누름을 무시 (두 번 눌러 다음 주행까지 답하는 것을 막음) | `ui/HomeScreen.kt` |
| 5-4 | 기록 목록: 시작한 날짜별 묶음(건수·거리 합), 시각, 거리, 용도, 계기판 전후 거리, "기록되지 않은 주행"·"예시"·"진행 중" 표시. 용도 미확인 주행은 목록에서 바로 고름 | `ui/TripListScreen.kt` |
| 5-5 | 이번 달 요약: 총 주행거리, 업무용 사용거리(업무 + 출퇴근), 업무사용비율, 용도 미확인 거리. 진행 중인 주행은 끝난 뒤 더함 | `data/MonthSummary.kt` |
| 5-6 | 글자·버튼 크기를 한 파일에 모음 | `ui/theme/KiloTheme.kt` |
| 5-7 | 주행 중: 카드·목록·설정 숨김, "● 기록 중 · 이번 주행 X km"만 | `ui/HomeScreen.kt` (`DrivingView`) |
| 5-8 | 디버그 탭의 "예시 기록 넣기"(7건) / "예시 기록 모두 지우기". 예시는 `isSample`로 표시하고 화면·요약에 "예시"로 보임 | `data/SampleTrips.kt`, `ui/DebugScreen.kt` |
| 5-9 | S1~S8 다시 실행 + 새 화면 시나리오, 캡처, 이 문서 | `docs/phase5/` |
| 5-10 | 설정 › 오픈소스 라이선스: `docs/open-source-licenses.md`의 표기용 목록 + Apache License 2.0 전문 (`assets/licenses/LICENSE-2.0.txt`, apache.org 원문) | `ui/LicensesScreen.kt` |

판단 기준 (기록 규칙 4와 같음)

- 주행 중 = 주행 규제(`requiresDistractionOptimization`)가 켜져 있거나, 기어를 읽을 수 있고 P가 아님.
- 카드는 "정차"라는 신호가 있을 때만 보인다. 신호를 못 읽으면(주행도 정차도 아님) 홈은 보이지만 카드는 묻지 않고, 상태 카드에 "확인 대기 N건 · 정차한 것을 확인하면 물어봅니다" + "기록에서 고르기"를 보인다.
- 주행 중에도 디버그 탭은 남긴다 (디버그 패널을 켠 시험용). 기본값은 꺼짐이다.

DB 버전 2

- `trips`에 `isSample INTEGER NOT NULL DEFAULT 0` 열을 더했다 (`KiloBookDatabase.MIGRATION_1_2`). 기존 기록은 그대로 남고 예시가 아닌 것으로 표시된다.
- 확인: Phase 4 앱(버전 1)으로 주행 1건을 만든 뒤 Phase 5 앱을 덮어 설치 → 그 주행이 홈 카드에 그대로 나옴.

## 시험 결과

2026-11-09, Virtual Console HTTP API + adb로 직접 실행. 시나리오 순서는 [../spike/phase4-scenarios.md](../spike/phase4-scenarios.md) (화면 위치는 그 문서 위의 "Phase 5 이후 화면 위치" 참고).

| 시나리오 | 결과 | 근거 |
|---|---|---|
| S1 총 주행거리 읽기 | 통과 | `checkCurrentDistanceDrivenCapabilities -> CapabilityConfig(accessType=READ)`, `getCurrentDistanceDriven -> 3.0`. 홈 "차량 총 주행거리 3.0 km" |
| S2 실시간 갱신 | 통과 | 콘솔 3.0 → 3.5 km, 홈 글자가 4초 안에 바뀜 |
| S3 기어 P로 종료 | 통과 | D 동안 주행 중 화면 "이번 주행 1.0 km" → P → 카드 → 업무. `주행 종료 #12 (GEAR_PARK)` |
| S4 누락 감지 | 통과 | 강제 종료 중 2.4 → 3.0 km. `누락 감지 #10: 기록되지 않은 주행 0.6 km (2.4 → 3.0)`, 카드에 "기록되지 않은 주행 · 앱이 꺼져 있던 동안 늘어난 거리" |
| S5 다시 묻기 | 통과 | 나중에 → 카드 사라짐 → 설정 앱 갔다 돌아옴 `다시 물을 기회: 화면이 다시 보임` → 나중에 → D(카드 없음) → P `다시 물을 기회: 정차로 바뀜` |
| S6 재부팅 후 유지 | 통과 | 에뮬레이터 종료 후 다시 켬(Cold Boot). 재부팅 전 기록 8건 유지 (재부팅 뒤 S8 주행을 더해 9건). `시작 값(0.0 km)이 마지막 기록(7.5 km)보다 작음. 현재 값을 새 기준으로 사용` |
| S7A 다른 앱 뒤 | 통과 | 설정 앱이 앞에 있는 동안 D → 4.5 → 5.5 km → P. 돌아오자 카드에 1.0 km |
| S7B 창 닫음 | 통과 | `am stack remove` → `Session: release(screen) -> owners=[service]`, `isForeground=true foregroundId=1 types=40000000` → `주행 시작 #14` · `주행 종료 #14 (GEAR_PARK)` |
| S8 부팅 자동 시작 | 통과 | 부팅 완료 18:46:03 → `BOOT_COMPLETED` 수신 18:46:32 (user 10) → `startForeground(specialUse) -> ok`. 앱 창 0개인 채 D → 0.0 → 1.0 km → P `#16 (GEAR_PARK)`. 앱을 열자 카드에 #16, 디버그 탭 S8 줄 ok |

새 화면 시나리오

| # | 확인 | 결과 |
|---|---|---|
| N1 | 디버그 탭: 기본 숨김 → 설정에서 켜면 탭 생김 → 재부팅 뒤에도 유지 | 통과 |
| N2 | 주행 중 화면: 탭은 홈(+디버그)만, 카드·버튼 없음, 거리 갱신 | 통과 |
| N3 | 카드 한 건씩: 확인 대기 2건 → 최신(1.4 km)부터, 답하면 다음(1.0 km) | 통과 |
| N4 | 두 번 연속 누름: `용도 선택`이 한 번만 기록되고 다음 주행은 그대로 | 통과 (처음 만든 화면은 같은 주행에 두 번 기록돼 고침) |
| N5 | 나중에 → 상태 카드 "확인 대기 N건" + "기록에서 고르기" → 목록에서 출퇴근 선택 | 통과 |
| N6 | 기록 목록: 날짜 묶음, 계기판 전후, "기록되지 않은 주행"·"예시" 표시 | 통과 |
| N7 | 이번 달 요약을 손 계산과 비교 (실제 2건 + 예시 7건): 총 99.2 km, 업무용 78.9 km(업무 52.3 + 출퇴근 26.6), 비율 79.5%, 미확인 14.1 km | 통과 |
| N8 | 예시 기록: 7건 넣기 → 모두 지우기 7건, 실제 기록은 남음 | 통과 |
| N9 | 대체 규칙 강제: 주행 중 아님 + 열린 주행이면 홈에 "주행 종료"(높이 100)가 카드 위 → 누르면 `MANUAL_BUTTON` | 통과 |
| N10 | 오픈소스 라이선스: 목록 13개 + Apache 2.0 전문 끝("APPENDIX…")까지 보임, "‹ 설정"으로 돌아감 | 통과 |
| N11 | DB 버전 1 → 2: 이전 앱의 기록 유지 | 통과 |
| N12 | 세로 창(내비와 1:1): 요약 2×2, 용도 버튼 4개 한 줄, 목록 정렬 유지 | 통과 |

SDK·차량 API 결과 (이번 시험에서 본 값. Phase 5에서 새로 부르는 API는 없다)

| API | 결과 | 비고 |
|---|---|---|
| `Odometer.checkCurrentDistanceDrivenCapabilities` | `CapabilityConfig(accessType=READ)` | |
| `Odometer.getCurrentDistanceDriven` | 3.0 / 재부팅 직후 0.0 | km (Float) |
| `Odometer.registerDistanceDriven` | 값 변경 후 1~4초 안에 콜백 | 같은 값도 반복해서 옴 |
| 트립 거리·시간 3종 | `Unsupported · ERROR_NOT_SUPPORTED_PROPERTY` | Phase 1~4와 같음 |
| `CarPropertyManager` `GEAR_SELECTION` | `access=READ`, P=4 / D=8 | |
| `CarPropertyManager` `PARKING_BRAKE_ON` | `access=READ`, 항상 false | Phase 4와 같음 |
| `CarPropertyManager` `IGNITION_STATE` | config 없음, `IllegalArgumentException` | Phase 4와 같음 |
| `CarUxRestrictionsManager` | P에서 `requiresDO=false`, D에서 true | 주행 중 화면 전환에 씀 |

## 시험할 때 알게 된 것

- **콘솔 페이지 없이 시험하기**: Virtual Console 페이지는 1초마다 `POST /browser/session?client=<아무 ID>`를 보낸다. 터미널에서 같은 요청을 1초마다 보내면 브라우저 없이도 값 변경이 받아들여진다.
  기어: `curl -X PUT "http://127.0.0.1:8088/vehicle/gear?gear=Drive"` (`Park`, `Reverse`, `Neutral`, `Drive`). 총 주행거리: `…/vehicle/odometer?traveled_distance_km=12.5`.
- **시간대**: 이번에 에뮬레이터를 한국 표준시로 바꿨다 (설정 › 날짜 및 시간, `persist.sys.timezone` = `Asia/Seoul`). 재부팅 뒤에도 유지된다.
  바꾸기 전에 남긴 기록은 같은 순간을 새 시간대로 보여 준다 (예: 미 태평양 오전 1:28 → 한국 오후 6:28).
- 에뮬레이터 재부팅은 `adb emu kill` 뒤 `emulator -avd Pleos_Connect_v2.1.0`으로 했다 (`adb reboot`은 쓰지 않음). 다시 켠 뒤 `adb forward tcp:8088 tcp:8088`을 다시 한다.

## 남은 것과 제안

- 앱 정보의 버전이 `0.1.0-spike`로 보였다 → Phase 6에서 `1.0.0`으로 바꿈.
- 실제 차량 화면 밀도와 Connect-W(가로로 긴 화면)에서는 확인하지 않았다. Virtual Console의 폼 팩터 시험(가상 디스플레이)으로 확인할 수 있다.
- 완료된 주행의 용도를 나중에 바꾸는 기능은 넣지 않았다 (지금은 미확인 주행만 목록에서 고름).
