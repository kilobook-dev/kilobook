# Phase 1 — 공식 샘플 앱으로 연결 확인

확인일: 2026-10-31 / 에뮬레이터: Pleos Connect v2.1.0 (Android 14, API 34)

## 환경 (이 맥미니 기준, 모두 외장 SSD)

| 항목 | 경로 / 버전 |
|---|---|
| JDK | `/Volumes/SamsungSSD/Android/jdk/jdk-21.0.13+9/Contents/Home` (Temurin 21.0.13) |
| Android SDK | `/Volumes/SamsungSSD/Android/sdk` |
| Gradle 캐시 | `~/.gradle` → `/Volumes/SamsungSSD/Android/gradle` (심볼릭 링크) |
| 샘플 앱 | `reference/pleos-sample-vehicle-app` (git 제외) |

샘플 빌드·설치:

```bash
cd reference/pleos-sample-vehicle-app
export JAVA_HOME=/Volumes/SamsungSSD/Android/jdk/jdk-21.0.13+9/Contents/Home
export ANDROID_HOME=/Volumes/SamsungSSD/Android/sdk
bash ./gradlew assembleDebug installDebug   # gradlew에 실행 권한이 없어 bash로 실행
```

### 샘플 저장소와 실제가 달랐던 점

- `gradle/libs.versions.toml`이 `vehicleSdk = "2.3.1"`인데, Pleos Maven에는 2.0.3과 2.3.2만 있다 (2.3.1은 404).
  README와 공식 문서는 2.3.2를 쓴다. 로컬 사본만 2.3.2로 고쳐서 빌드했다.
- README는 JDK 17을 요구하지만 JDK 21로 빌드·설치가 문제없이 됐다.

## SDK 호출 결과 (샘플 앱 화면에서 확인)

앱 상단 표시: `SDK 2.3.2 · Vehicle Service 2.3.22 · SDK initialized`

| API | 결과 | 비고 |
|---|---|---|
| `Vehicle.initialize()` | 성공 | 상단 "SDK initialized" |
| `checkCurrentDistanceDrivenCapabilities` | Supported | `CapabilityConfig(accessType=READ)` |
| `getCurrentDistanceDriven` | 성공: `0.0` | Virtual Console Odometer 0 km와 같음 |
| `registerDistanceDriven` | 성공: 첫 콜백 `0.0` | 등록 직후 현재값이 한 번 들어옴 |
| `checkTripCurrentDrivingDistanceCapabilities` | Unsupported | `ERROR_NOT_SUPPORTED_PROPERTY` |
| `checkTripCurrentDrivingTimeCapabilities` | Unsupported | `ERROR_NOT_SUPPORTED_PROPERTY` |
| `checkTripCumulativeDrivingDistanceCapabilities` | Unsupported | `ERROR_NOT_SUPPORTED_PROPERTY` |
| `checkTripCumulativeDrivingTimeCapabilities` | Unsupported | `ERROR_NOT_SUPPORTED_PROPERTY` |
| `checkTripDrivingDistanceCapabilities` | Unsupported | `ERROR_NOT_SUPPORTED_PROPERTY` |
| `checkTripDrivingTimeCapabilities` | Unsupported | `ERROR_NOT_SUPPORTED_PROPERTY` (Virtual Console에는 Trip Driving Time 조작 항목이 있음) |
| `checkTripSinceRefillDrivingDistanceCapabilities` | Unsupported | `ERROR_NOT_SUPPORTED_PROPERTY` |
| `checkTripSinceRefillDrivingTimeCapabilities` | Unsupported | `ERROR_NOT_SUPPORTED_PROPERTY` |
| `getTripInstantaneousDrivingDistance` 계열 | 미확인 | 샘플 앱 목록에 없음. Phase 2에서 직접 호출 |

## 주행 규제 관찰

- 기어가 D면 속도 0이어도 샘플 앱이 "주행 중에는 사용할 수 없습니다" 화면으로 가려진다.
- `dumpsys car_service` 규제 설정: parked = 0x0, idling = 0x50, moving = 0x50 (idling/moving 모두 DO 필요).
- 즉 "정차"를 기어 D + 속도 0으로 보면 안 되고, P(parked)여야 규제가 풀린다.

## 공식 문서로 확인한 Odometer API

출처: document.pleos.ai › Vehicle SDK › API Reference › Odometer (각 메서드 페이지)

| 메서드 | 시그니처 | 단위 | 권한 |
|---|---|---|---|
| `checkCurrentDistanceDrivenCapabilities` | `(onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit)` | - | `CAR_DRIVE` |
| `getCurrentDistanceDriven` | `(onComplete: (Float?) -> Unit, onFailed: (Exception) -> Unit)` | **km**, 값이 없으면 null | `CAR_DRIVE` |
| `registerDistanceDriven` / `unregisterDistanceDriven` | `(listener: DistanceDrivenListener)` | **km** | `CAR_DRIVE` |
| `getTripCurrentDrivingDistance` | `(onComplete: (Float?) -> Unit, onFailed)` | **m (미터)** | `CAR_DRIVE` |
| `getTripCurrentDrivingTime` | `(onComplete: (Int?) -> Unit, onFailed)` | **분** | `CAR_DRIVE` |
| `registerTripCurrentDrivingDistance` | `(listener: DistanceDrivenListener)` | **m** | `CAR_DRIVE` |
| `registerTripCurrentDrivingTime` | `(listener: DrivingTimeListener)` | 분 | `CAR_DRIVE` |
| `getTripCumulativeDrivingDistance` | `(onComplete: (Float?) -> Unit, onFailed)` | **m** | `CAR_DRIVE` |

- `DistanceDrivenListener.onDistanceDrivenUpdated(value: Float?)` — 단위는 등록한 메서드에 따름 (총 주행거리 km, 트립 m).
- `DrivingTimeListener.onDrivingTimeUpdated(value: Int?)`
- 두 리스너 모두 `VehicleListener`를 상속한다 (문서). 샘플의 `DoorPositionListener`처럼 `onFailed(e: Exception)`도 있을 것으로 보이며, Phase 2 컴파일 때 확인한다.
- `CapabilityConfig(val accessType: AccessType = AccessType.NONE)` — Odometer 같은 전역 속성은 area 맵 없이 config만 온다.
- 권한: Odometer는 `pleos.car.permission.CAR_DRIVE` 하나면 된다. 샘플 매니페스트의 `CAR_MILEAGE`는 Odometer API의 `@RequiresPermission`에 없다.

### 프롬프트와 문서가 다른 점

- 권한: 프롬프트는 CAR_MILEAGE를 예로 들었지만, 문서상 Odometer는 `CAR_DRIVE`.
- 트립 거리 단위는 km가 아니라 **m**, 트립 시간은 **분**.

## 샘플 코드 패턴 요약

1. **세션 생성·초기화** — `VehicleClient`가 `Vehicle(context.applicationContext)`를 만들고 `initialize()`를 한 번 호출한다.
   샘플은 `initialize()`를 반환값·콜백 없이 호출하고 예외만 잡는다. 예외가 나면 오류로 표시하고 이후 호출을 막는다 (`isInitialized` 플래그).
2. **도메인 획득** — `vehicle.getOdometer()`, `vehicle.getDoor()`처럼 `getXxx()`로 얻는다. 직접 생성하지 않는다.
3. **Capability 먼저** — `checkXxxCapabilities(onComplete, onFailed)`. 지원하지 않으면 `onFailed`로 `ERROR_NOT_SUPPORTED_PROPERTY`가 온다.
4. **읽기** — `getCurrentXxx(onComplete, onFailed)`. 값은 null일 수 있다.
5. **리스너** — 리스너 객체를 필드로 하나 만들어 두고, 같은 인스턴스로 `registerXxx` / `unregisterXxx` 한다.
6. **정리** — 화면/컴포넌트가 끝날 때 모든 리스너를 unregister한 뒤 `release()`. 콜백이 끝나기 전에 release하지 말 것 (문서 경고).
7. 모든 결과는 IPC 비동기 콜백. 다음 단계는 이전 콜백 안에서 시작한다.
