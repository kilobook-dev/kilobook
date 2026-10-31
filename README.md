# 오도로그 스파이크 (Odolog Spike)

Pleos Connect(Android Automotive) 에뮬레이터에서 "차량 주행거리로 운행기록을 남기는 앱"이
기술적으로 되는지 판정하기 위한 검증용 앱. 완성품이 아니며 UI 디자인은 하지 않는다.

- 패키지: `com.odolog.spike` · Kotlin + Jetpack Compose · minSdk 28 · 단일 Activity
- Vehicle SDK: `ai.pleos.playground:Vehicle:2.3.2`
- 툴체인: AGP 8.8.2 · Gradle 8.10.2 · Kotlin 2.0.0 (공식 샘플 앱과 동일)

## 빌드·설치

JDK 17 또는 21이 필요하다 (Gradle 8.10.2는 JDK 25에서 실행되지 않음).

```bash
# 이 맥미니 기준 경로 (외장 SSD). 다른 PC는 자기 경로로 바꾼다.
export JAVA_HOME=/Volumes/SamsungSSD/Android/jdk/jdk-21.0.13+9/Contents/Home
export ANDROID_HOME=/Volumes/SamsungSSD/Android/sdk

./gradlew installDebug
$ANDROID_HOME/platform-tools/adb shell am start -n com.odolog.spike/.MainActivity
```

`local.properties`(git 제외)에 `sdk.dir`을 적어두면 `ANDROID_HOME` 없이도 된다.
Android Studio에서는 Settings › Build Tools › Gradle › Gradle JDK를 JDK 17/21로 맞춘다.

로그 보기: `adb logcat -s OdologSpike:I`

## 구조

```
app/src/main/java/com/odolog/spike/
├── MainActivity.kt            Compose 화면을 띄우는 단일 Activity
├── DashboardViewModel.kt      차량 연결의 수명 담당 (화면 종료 시 해제)
├── ui/DashboardScreen.kt      실시간 패널
└── vehicle/
    ├── DashboardState.kt      화면 상태 (값 + Capability 결과)
    ├── VehicleMonitor.kt      두 소스를 하나의 StateFlow로 합침
    ├── PleosVehicleSource.kt  Pleos Vehicle SDK (Odometer, deprecated Gear)
    └── CarStandardSource.kt   AAOS 표준 API (CarPropertyManager, CarUxRestrictionsManager)
```

## 문서

- [Phase 1 — 샘플 앱 연결 확인](docs/phase1-sample-app.md)
- [Phase 2 — 실시간 값 표시](docs/phase2-live-panel.md)

`reference/`에는 공식 샘플 앱을 받아 두며 git에 포함하지 않는다.
