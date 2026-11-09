# 권한 점검

확인일: 2026-11-06, `CAR_VENDOR_EXTENSION` 삭제 후 2026-11-09 다시 확인 / 에뮬레이터: Pleos Connect v2.1.0 (Android 14) / 앱 `com.kilobook.app` 디버그 빌드

운영사무국 기준: **Android 표준 API는 Normal·Dangerous 권한만 쓸 수 있다. signature·privileged 권한은 쓸 수 없다.**

확인 방법:

- 최종 APK에 들어간 권한 (라이브러리가 매니페스트 병합으로 넣은 것 포함): `aapt2 dump permissions app/build/outputs/apk/debug/app-debug.apk`
- 보호 수준: `adb shell pm list permissions -g -f` 의 `protectionLevel` (`-g` 없이 실행하면 그룹에 속한 권한이 빠진다)
- 부여 결과: `adb shell dumpsys package com.kilobook.app | grep granted=`
- Pleos SDK가 요구하는 권한: SDK 클래스의 `@RequiresPermission` (`javap -v`로 확인)

## 1. 앱에 들어간 권한 (7개)

| 권한 | 들어온 곳 | 정의한 패키지 | protectionLevel | 부여 | 쓰는 기능 | 판정 |
|---|---|---|---|---|---|---|
| `pleos.car.permission.CAR_DRIVE` | 앱 매니페스트 | ai.pleos.playground.service.vehicle | normal | 예 | Pleos Odometer(총 주행거리), Pleos Gear(기어, deprecated). 두 API의 `@RequiresPermission` | 사용 가능 |
| `android.car.permission.CAR_POWERTRAIN` | 앱 매니페스트 | com.android.car.updatable | normal | 예 | `GEAR_SELECTION`, `CURRENT_GEAR`, `PARKING_BRAKE_ON`, `IGNITION_STATE` (기어 P로 주행 종료) | 사용 가능 |
| `android.permission.FOREGROUND_SERVICE` | 앱 매니페스트 | android | normal\|instant | 예 | 백그라운드 기록 서비스 | 사용 가능 |
| `android.permission.FOREGROUND_SERVICE_SPECIAL_USE` | 앱 매니페스트 | android | normal\|appop\|instant | 예 | 서비스 유형 `specialUse` | 사용 가능 (기본 수준 normal. `appop`은 시스템 설정으로 끌 수 있다는 표시) |
| `android.permission.RECEIVE_BOOT_COMPLETED` | 앱 매니페스트 | android | normal | 예 | 부팅 시 자동 시작 (S8) | 사용 가능 |
| `ai.pleos.playground.service.vehicle.VEHICLE_BINDING` | Pleos Vehicle SDK (병합) | ai.pleos.playground.service.vehicle | normal | 예 | SDK가 Pleos 차량 서비스에 연결 | 사용 가능 |
| `com.kilobook.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | AndroidX Core (병합) | com.kilobook.app (앱 자신) | signature | 예 | AndroidX가 앱 내부 수신기를 다른 앱이 부르지 못하게 막는 용도 | 표준 API 권한이 아니라 앱이 스스로 정의한 권한. 운영 기준 대상인지 확인 권장 |

권한 없이 쓰는 API: `CarUxRestrictionsManager`(주행 규제)는 권한 선언 없이 동작한다 (에뮬레이터에서 `registerListener` 성공 확인).

## 2. 삭제한 권한: `CAR_VENDOR_EXTENSION` (signature|privileged)

- **쓰던 곳**: Phase 2에서 Virtual Console이 보내는 벤더 기어 값 `VENDOR_GEAR_SELECTION`(0x21400400)을 표준 `GEAR_SELECTION`과 비교해 보려고 넣었다.
  거부될 것을 알고 그 결과를 기록하려고 선언했고, 기록 판단에는 쓰지 않았다. 권한은 부여되지 않았다 (`granted` 없음).
- **삭제 (2026-11-09)**: `app/src/main/AndroidManifest.xml`의 선언과 주석, `vehicle/CarStandardSource.kt`의 `VENDOR_GEAR_SELECTION` 구독과 상수,
  `vehicle/DashboardState.kt`의 `vendorGear`, `ui/DashboardScreen.kt` 기어 줄의 "VENDOR: …" 표시.
- **삭제 후 확인**: `aapt2 dump permissions` 결과 7개 (이 권한 없음), 7개 모두 `granted=true`.
  앱 실행 시 `GEAR_SELECTION`·`CURRENT_GEAR` = 4(P), Pleos Gear = `GEAR_PARK`로 기어 판단은 전과 같다. 디버그 화면 기어 줄의 비교 값은 `CURRENT_GEAR`, Pleos Gear 두 개가 남았다.
  "권한 거부됨" 결과는 `docs/spike/` 기록에 남아 있다.
- **기어는 normal 권한으로 읽는다**: `GEAR_SELECTION`(`CAR_POWERTRAIN`), Pleos Gear(`CAR_DRIVE`).
- **실제 차량에서 기어를 못 읽을 때의 대체 (이미 있는 규칙 2)**: 주행 규제(CarUxRestrictions, 권한 불필요)로 주행/정차를 보고,
  총 주행거리 N분 무변화 → `ODOMETER_IDLE` 또는 "주행 종료" 버튼 → `MANUAL_BUTTON`으로 끝낸다.

## 3. 참고: 선언하지 않았지만 관련 있는 권한

| 권한 | protectionLevel | 관련 기능 | 지금 |
|---|---|---|---|
| `android.car.permission.CAR_MILEAGE` | signature\|privileged | AAOS 표준 `PERF_ODOMETER` 직접 읽기 | 쓰지 않음. 총 주행거리는 Pleos Odometer(`CAR_DRIVE`, normal)로 읽는다 |
| `android.car.permission.CAR_SPEED` | dangerous | 차속 | 쓰지 않음 |
| `android.permission.POST_NOTIFICATIONS` | dangerous | 서비스 알림 표시 | 요청하지 않음 (Pleos 문의 4번) |
| `pleos.car.permission.TTS_SERVICE` | normal | Pleos TextToSpeech | Phase 5 이후 ([speech-check.md](speech-check.md)) |
| `pleos.car.permission.STT_SERVICE` | normal | Pleos SpeechToText | Phase 5 이후 |
| `android.permission.RECORD_AUDIO` | dangerous | 음성 인식 (마이크) | Phase 5 이후 |
| `android.permission.INTERNET` | normal | Pleos TTS/STT SDK가 병합으로 추가 | TTS/STT를 넣으면 함께 들어온다 |

## 4. 참고: Pleos 앱 마켓 심사 기준

Pleos 문서의 앱 심사 가이드(기능·성능·안정성)에는 "Vehicle SDK 권한은 Pleos App Market과 미리 협의·승인된 것만 선언할 수 있고, 승인되지 않은 권한을 선언한 앱은 심사를 통과할 수 없다"는 취지의 문장이 있다
(https://document.pleos.ai/en/docs/pleos-only/distribute/app-review-guide/review-functionality-performance-stability).
앱 마켓 배포 기준이며, 공모전 기준은 위의 운영사무국 답변을 따른다.
