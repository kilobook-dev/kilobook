# Phase 2 — 스파이크 앱 뼈대와 실시간 값 표시

확인일: 2026-10-31 / 에뮬레이터: Pleos Connect v2.1.0 (Android 14) / Vehicle SDK 2.3.2, Vehicle Service 2.3.22

## 화면

첫 화면 한 장에 값마다 한 줄씩: 큰 글씨 값 + 그 값을 읽은 API와 Capability 결과(Supported / Unsupported / 오류).
트립 값은 SDK 결과를 그대로 두고, 오른쪽에 "앱 계산" 값을 따로 표시한다.

| 줄 | 읽는 방법 | 갱신 |
|---|---|---|
| 총 주행거리 | Pleos `Odometer.getCurrentDistanceDriven` (km) | `registerDistanceDriven` |
| 트립 주행거리 | Pleos `getTripCurrentDrivingDistance`, `getTripInstantaneousDrivingDistance` (m) + 앱 계산 | register (지원 시) |
| 트립 주행시간 | Pleos `getTripCurrentDrivingTime` (분) + 앱 계산 | register (지원 시) |
| 기어 | AAOS `GEAR_SELECTION` (비교용: `CURRENT_GEAR`, `VENDOR_GEAR_SELECTION`, Pleos Gear) | `CarPropertyManager.registerCallback` |
| 주차 브레이크 | AAOS `PARKING_BRAKE_ON` | `registerCallback` |
| 주행 규제 | `CarUxRestrictionsManager` (`isRequiresDistractionOptimization`) | `registerListener` |
| 시동 (참고) | AAOS `IGNITION_STATE` | `registerCallback` |

앱 계산 트립(이번 Phase 한정): 화면을 연 뒤 처음 읽은 총 주행거리를 기준으로 증가분과,
총 주행거리가 처음 늘어난 시각부터의 경과 시간. Phase 3에서 저장된 Trip 기록으로 바꾼다.

## SDK·API 호출 결과

| API | 결과 | 비고 |
|---|---|---|
| `Vehicle.initialize()` | 성공 | |
| `checkCurrentDistanceDrivenCapabilities` | Supported | `accessType=READ` |
| `getCurrentDistanceDriven` / `registerDistanceDriven` | 성공 | 콘솔에서 12.3으로 바꾸자 즉시 12.3 수신. 변화가 없어도 약 1~2초마다 같은 값이 반복해서 옴 |
| `checkTripCurrentDrivingDistanceCapabilities` | Unsupported | `ERROR_NOT_SUPPORTED_PROPERTY` |
| `checkTripInstantaneousDrivingDistanceCapabilities` | Unsupported | `ERROR_NOT_SUPPORTED_PROPERTY`. SDK에서 deprecated ("uses TripStatisticType.INSTANTANEOUS") |
| `checkTripCurrentDrivingTimeCapabilities` | Unsupported | `ERROR_NOT_SUPPORTED_PROPERTY` |
| `Gear.getCurrentGearSelectionState` / `registerGearSelection` | 성공 | 문서에 없는 deprecated API ("use the Google GEAR_SELECTION property"). 내부적으로 `VENDOR_GEAR_SELECTION`을 읽음 |
| `GEAR_SELECTION` (CarPropertyManager) | 성공 | 콘솔 D → 8, P → 4. 실시간 반영 |
| `CURRENT_GEAR` | 값은 읽힘, 신뢰 불가 | 콘솔이 D여도 계속 4(P). 콘솔이 이 속성을 바꾸지 않음 |
| `VENDOR_GEAR_SELECTION` (0x21400400) | 권한 오류 | `SecurityException: requires android.car.permission.CAR_VENDOR_EXTENSION` (signature 권한) |
| `PARKING_BRAKE_ON` | 성공: `false` | 읽기는 되지만 Virtual Console에 조작 항목이 없어 바꿔볼 수 없음 (경고등 Telltale만 있음) |
| `IGNITION_STATE` | Unsupported | `IllegalArgumentException: property ID is not in carPropertyConfig list` |
| `CarUxRestrictionsManager` | 성공 | D: `requiresDO=true, 0x50` / P: `requiresDO=false, 0x0`. 실시간 반영 |

## 주행 중 화면 유지 (DO 선언)

- Pleos 정책 문서: DO 미선언 앱은 Parking에서만 사용 가능, Drive에서는 사용 불가.
- `MainActivity`에 `<meta-data android:name="distractionOptimized" android:value="true"/>`를 선언했다.
- 결과: adb로 설치한 디버그 빌드도 기어 D(규제 활성 0x50)에서 화면이 가려지지 않았다. (샘플 앱은 같은 조건에서 가려짐)
- 선언 방법은 Pleos 문서에 없고 Android 표준 방식이다. 양산 차량·스토어 배포에서 같은지는 확인 필요.
- 문서: DO 선언 앱도 규제가 활성화되면 `activeRestrictions`를 보고 기능별 제한(키보드, 영상 등)을 직접 적용해야 한다.

## 수명 주기 확인

화면을 닫으면 (`am stack remove`로 작업 제거) 다음 순서로 호출됨을 로그로 확인했다.

```
unregisterGearSelection -> ok
unregisterDistanceDriven -> ok
Vehicle.release() -> ok
CarPropertyManager.unregisterCallback x3 -> done
CarUxRestrictionsManager.unregisterListener -> ok
Car.disconnect() -> ok
```

- 이 차량 홈 화면에서는 Back 키로 앱이 닫히지 않는다 (앱이 홈 화면 창 안에 떠 있음).
- 화면을 닫은 뒤 늦게 도착한 Capability 응답은 리스너를 등록하지 않도록 막았다 (`active` 플래그).

## Virtual Console에 대해 알게 된 점

- 주행거리 변경: `PUT /vehicle/odometer?traveled_distance_km=12.3` — 소수점 가능 (화면 슬라이더는 1 km 단위).
- 주행거리는 줄일 수 없다: `{"error":"traveled_distance_must_not_decrease"}`. 현재 콘솔 값은 12.3 km.
- 기어 변경: `PUT /vehicle/gear?gear=Park|Reverse|Neutral|Drive`.
