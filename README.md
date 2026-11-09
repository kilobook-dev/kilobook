# 킬로북 (KiloBook)

> 이름이 들어가는 빌드 값(패키지, 표시 이름, 제출 APK 파일명, 서명 인증서 이름)은 모두 [`app.properties`](app.properties) 한 곳에서 온다.

Pleos Connect(Android Automotive) 차량용 앱. 차량이 직접 읽은 총 주행거리로 업무용 운행기록을 남기고,
운전자는 주행이 끝난 뒤 용도(업무/출퇴근/개인)만 고른다. 기술 검증(`spike-v1`)과 제출용 화면(Phase 5)을 마쳤다. 화면과 시험 결과: [docs/phase5](docs/phase5/README.md)

- 패키지: `com.kilobook.app` (`app.properties`의 `appSlug`·`appIdSuffix`) · Kotlin + Jetpack Compose · minSdk 28 · 단일 Activity
- Vehicle SDK: `ai.pleos.playground:Vehicle:2.3.2`
- 툴체인: AGP 8.8.2 · Gradle 8.10.2 · Kotlin 2.0.0 (공식 샘플 앱과 동일)

## 빌드·설치

JDK 17 또는 21이 필요하다 (Gradle 8.10.2는 JDK 25에서 실행되지 않음).

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"   # macOS. 다른 OS는 JDK 21 경로
export ANDROID_HOME="$HOME/Library/Android/sdk"     # Android Studio 기본 SDK 위치

./gradlew installDebug
$ANDROID_HOME/platform-tools/adb shell am start -n com.kilobook.app/.MainActivity
```

`local.properties`(git 제외)에 `sdk.dir`을 적어두면 `ANDROID_HOME` 없이도 된다.
Android Studio에서는 Settings › Build Tools › Gradle › Gradle JDK를 JDK 17/21로 맞춘다.

로그 보기: `adb logcat -s KiloBook:I`

테스트 환경(Pleos Connect Emulator v2.1.0, Virtual Console 조작, 시간대 설정)과 시나리오 S1~S8: [docs/spike/phase4-scenarios.md](docs/spike/phase4-scenarios.md)

## 제출용 release APK

```bash
scripts/create-release-key.sh   # 처음 한 번: 저장소 밖에 서명 키 생성, local.properties에 넣을 줄 출력
./gradlew releaseApk            # apk/KiloBook_KiloBook_release.apk (이름은 app.properties의 teamName·appNameEn)
```

서명 값이 없으면 `releaseApk`는 빌드 전에 멈춘다. 자세한 내용은 [apk/README.md](apk/README.md).

## 구조

```
app.properties                 앱 이름 설정 (appName, appNameEn, appSlug, appIdSuffix, teamName)
scripts/create-release-key.sh  release 서명 키 생성 (저장소 밖)
apk/                           제출용 release APK 위치
app/src/main/java/com/kilobook/app/
├── KiloBookApp.kt             Application: DB, 설정, 기록 세션을 한 곳에서 만듦
├── AppLog.kt                  logcat과 화면 디버그 로그에 함께 남기는 로그 (최근 300건, 연속 중복은 묶음)
├── DisplaySettings.kt         화면 설정 (디버그 패널 보기, 저장됨)
├── MainActivity.kt            Compose 화면을 띄우는 단일 Activity (onStart/onStop을 ViewModel에 전달)
├── DashboardViewModel.kt      화면 쪽 기록 세션 소유자, 버튼 동작, 주행 중 판단, 예시 기록
├── ui/
│   ├── MainScreen.kt          탭(홈/기록/설정/디버그)과 주행 중 화면 전환
│   ├── HomeScreen.kt          용도 확인 카드(한 건씩), 기록 상태, 이번 달 요약, 주행 중 화면
│   ├── TripListScreen.kt      날짜별 기록 목록, 목록에서 용도 고르기
│   ├── SettingsScreen.kt      디버그 패널 스위치, 오픈소스 라이선스, 앱 정보
│   ├── LicensesScreen.kt      오픈소스 라이선스 목록 + Apache 2.0 전문 (assets/licenses/)
│   ├── DebugScreen.kt         실시간 값·API 결과, 기록 상태·디버그 설정, 예시 기록, 상세 목록, 디버그 로그
│   ├── Components.kt, Format.kt  공통 카드·버튼, 거리·시각 표시 형식
│   └── theme/KiloTheme.kt     글자·버튼 크기 (Pleos 디자인 가이드 기준)
├── data/
│   ├── Trip.kt                Trip, AppState (Room 엔티티)
│   ├── KiloBookDatabase.kt    Room DB와 DAO (버전 2, 1→2 마이그레이션)
│   ├── MonthSummary.kt        이번 달 요약 (업무사용비율 계산)
│   └── SampleTrips.kt         디버그용 예시 기록
├── recorder/
│   ├── TripRecorder.kt        주행 기록 규칙 1~6, 재시작 시 열린 Trip 정리
│   ├── RecordingSession.kt    화면과 서비스가 함께 쓰는 기록 세션 (소유자 수를 셈)
│   └── RecorderSettings.kt    디버그 설정 (무변화 N분, 재시작 기준, 대체 규칙 강제, 백그라운드 기록)
├── service/
│   ├── RecorderService.kt     Foreground Service: 화면이 없어도 기록 세션 유지
│   └── BootReceiver.kt        BOOT_COMPLETED 수신 시 서비스 시작 (S8), 마지막 결과 저장
└── vehicle/
    ├── DashboardState.kt      차량 상태 (값 + Capability 결과)
    ├── VehicleMonitor.kt      두 소스를 하나의 StateFlow로 합침
    ├── PleosVehicleSource.kt  Pleos Vehicle SDK (Odometer, deprecated Gear)
    └── CarStandardSource.kt   AAOS 표준 API (CarPropertyManager, CarUxRestrictionsManager)
```

## 오픈소스 라이선스

이 앱은 아래 오픈소스 라이브러리를 쓴다. 모두 **Apache License 2.0**이다 (https://www.apache.org/licenses/LICENSE-2.0).

- AndroidX, Jetpack Compose (Material 3 포함), Room
- Kotlin Standard Library, Kotlin Reflect, kotlinx.coroutines, kotlinx.serialization, JetBrains Java Annotations
- Dagger·Hilt, Gson, Guava ListenableFuture, JSR 305 Annotations, javax.inject, JSpecify

Pleos Vehicle SDK는 오픈소스가 아니며 Pleos Playground 이용약관을 따른다.
라이브러리별 버전과 라이선스 전체 목록(109개)은 [docs/open-source-licenses.md](docs/open-source-licenses.md). 앱에서는 설정 › 오픈소스 라이선스에 목록과 Apache License 2.0 전문이 있다.

## 문서

- [CLAUDE.md](CLAUDE.md) — 인수인계: 현재 상태, 확정된 결정, 작업 규칙, 남은 일, 새 PC 세팅 순서
- [Phase 5 — 제출용 화면, 크기 기준, 시험 결과](docs/phase5/README.md)
- [스파이크 기록 (Phase 1~4, 시나리오 S1~S8)](docs/spike/README.md)
- [권한 점검](docs/permissions.md) — 선언한 권한과 보호 수준 (Normal·Dangerous만 사용)
- [오픈소스 라이선스](docs/open-source-licenses.md)
- [TTS/STT 동작 확인과 계획](docs/speech-check.md)
- [Pleos 문의 목록](docs/pleos-questions.md)

`reference/`에는 공식 샘플 앱을 받아 두며 git에 포함하지 않는다.
