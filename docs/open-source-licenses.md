# 오픈소스 라이선스

확인일: 2026-11-06. release APK에 들어가는 외부 라이브러리와 라이선스.

확인 방법: `./gradlew :app:dependencies --configuration releaseRuntimeClasspath`로 라이브러리 109개(BOM 제외)를 뽑고,
Gradle 캐시에 있는 각 라이브러리 POM의 `<licenses>`를 읽었다 (POM에 없으면 상위 POM).

- 오픈소스 라이브러리 106개: 모두 **Apache License 2.0**.
- Pleos Vehicle SDK 3개(`Vehicle`, `vehicle-aidl`, `vehicle-data`): 오픈소스가 아니다. POM에 라이선스가 없고 Pleos Playground 이용약관(2025-03-28 시행)을 따른다.
- 목록에서 뺀 것: 빌드할 때만 쓰고 APK에 들어가지 않는 도구(Room compiler·KSP, Kotlin 컴파일러, Android Gradle Plugin)와 차량 OS가 제공하는 `android.car` 라이브러리(`useLibrary`).
- APK 안에 NOTICE 파일은 없다 (AndroidX의 `META-INF/androidx/**/LICENSE.txt` 3개만 있음).
- 라이브러리를 더하거나 버전을 바꾸면 이 문서를 다시 만든다. TTS/STT를 넣으면 Pleos TextToSpeech·SpeechToText SDK와 `ai.pleos.playground.security:universal-access`가 늘어난다.

## 앱 설정 화면 표기용 목록 (Phase 5)

Phase 5 설정 화면의 "오픈소스 라이선스" 항목은 아래 표와 Apache License 2.0 전문 한 부를 보여 준다.
Apache 2.0은 배포할 때 받는 사람에게 라이선스 사본을 주도록 한다 (4(a)항). 전문은 https://www.apache.org/licenses/LICENSE-2.0.txt 를 앱 리소스로 넣는다.

| 표시 이름 | 버전 | 라이선스 | 링크 |
|---|---|---|---|
| AndroidX (Activity, Annotation, Arch Core, Autofill, Collection, Concurrent, Core, CustomView, Emoji2, Fragment, Graphics, Interpolator, Lifecycle, Loader, ProfileInstaller, Room, SavedState, SQLite, Startup, Tracing, VersionedParcelable, ViewPager) | 라이브러리별 (부록) | Apache License 2.0 | https://developer.android.com/jetpack/androidx |
| Jetpack Compose (Animation, Foundation, Runtime, UI, Material, Material 3) | 1.7.8 · Material 1.6.6 · Material 3 1.2.1 | Apache License 2.0 | https://developer.android.com/jetpack/compose |
| Kotlin Standard Library, Kotlin Reflect | 2.0.21 · 1.9.25 | Apache License 2.0 | https://kotlinlang.org/ |
| kotlinx.coroutines | 1.7.3 | Apache License 2.0 | https://github.com/Kotlin/kotlinx.coroutines |
| kotlinx.serialization | 1.7.3 | Apache License 2.0 | https://github.com/Kotlin/kotlinx.serialization |
| JetBrains Java Annotations | 23.0.0 | Apache License 2.0 | https://github.com/JetBrains/java-annotations |
| Dagger, Hilt | 2.51.1 | Apache License 2.0 | https://github.com/google/dagger |
| Gson | 2.10.1 | Apache License 2.0 | https://github.com/google/gson |
| Guava ListenableFuture | 1.0 | Apache License 2.0 | https://github.com/google/guava |
| JSR 305 Annotations | 3.0.2 | Apache License 2.0 | http://findbugs.sourceforge.net/ |
| javax.inject | 1 | Apache License 2.0 | http://code.google.com/p/atinject/ (POM에 적힌 주소) |
| JSpecify | 1.0.0 | Apache License 2.0 | https://jspecify.org/ |
| Pleos Vehicle SDK (오픈소스 아님) | 2.3.2 | Pleos Playground 이용약관 | https://developers.pleos.ai/playground/external/use-policy?partnerId=672505ad-2e37-4500-8162-000bcfc05b5c&locale=en-US |

Dagger·Hilt, javax.inject, JSR 305, Gson, Kotlin Reflect는 Pleos Vehicle SDK가 함께 가져오는 라이브러리다.

## 부록: 라이브러리별 목록 (109개)

| 그룹 | 라이브러리 | 버전 | 라이선스 |
|---|---|---|---|
| `ai.pleos.playground` | `Vehicle` | 2.3.2 | 라이선스 표기 없음 (Pleos Playground 이용약관) |
| `ai.pleos.playground` | `vehicle-aidl` | 1.1.1 | 라이선스 표기 없음 (Pleos Playground 이용약관) |
| `ai.pleos.playground` | `vehicle-data` | 1.1.11 | 라이선스 표기 없음 (Pleos Playground 이용약관) |
| `androidx.activity` | `activity` | 1.10.1 | Apache License 2.0 |
| `androidx.activity` | `activity-compose` | 1.10.1 | Apache License 2.0 |
| `androidx.activity` | `activity-ktx` | 1.10.1 | Apache License 2.0 |
| `androidx.annotation` | `annotation` | 1.9.1 | Apache License 2.0 |
| `androidx.annotation` | `annotation-experimental` | 1.4.1 | Apache License 2.0 |
| `androidx.annotation` | `annotation-jvm` | 1.9.1 | Apache License 2.0 |
| `androidx.arch.core` | `core-common` | 2.2.0 | Apache License 2.0 |
| `androidx.arch.core` | `core-runtime` | 2.2.0 | Apache License 2.0 |
| `androidx.autofill` | `autofill` | 1.0.0 | Apache License 2.0 |
| `androidx.collection` | `collection` | 1.4.4 | Apache License 2.0 |
| `androidx.collection` | `collection-jvm` | 1.4.4 | Apache License 2.0 |
| `androidx.collection` | `collection-ktx` | 1.4.4 | Apache License 2.0 |
| `androidx.compose.animation` | `animation` | 1.7.8 | Apache License 2.0 |
| `androidx.compose.animation` | `animation-android` | 1.7.8 | Apache License 2.0 |
| `androidx.compose.animation` | `animation-core` | 1.7.8 | Apache License 2.0 |
| `androidx.compose.animation` | `animation-core-android` | 1.7.8 | Apache License 2.0 |
| `androidx.compose.foundation` | `foundation` | 1.7.8 | Apache License 2.0 |
| `androidx.compose.foundation` | `foundation-android` | 1.7.8 | Apache License 2.0 |
| `androidx.compose.foundation` | `foundation-layout` | 1.7.8 | Apache License 2.0 |
| `androidx.compose.foundation` | `foundation-layout-android` | 1.7.8 | Apache License 2.0 |
| `androidx.compose.material` | `material-icons-core` | 1.6.6 | Apache License 2.0 |
| `androidx.compose.material` | `material-icons-core-android` | 1.6.6 | Apache License 2.0 |
| `androidx.compose.material` | `material-ripple` | 1.6.6 | Apache License 2.0 |
| `androidx.compose.material` | `material-ripple-android` | 1.6.6 | Apache License 2.0 |
| `androidx.compose.material3` | `material3` | 1.2.1 | Apache License 2.0 |
| `androidx.compose.material3` | `material3-android` | 1.2.1 | Apache License 2.0 |
| `androidx.compose.runtime` | `runtime` | 1.7.8 | Apache License 2.0 |
| `androidx.compose.runtime` | `runtime-android` | 1.7.8 | Apache License 2.0 |
| `androidx.compose.runtime` | `runtime-saveable` | 1.7.8 | Apache License 2.0 |
| `androidx.compose.runtime` | `runtime-saveable-android` | 1.7.8 | Apache License 2.0 |
| `androidx.compose.ui` | `ui` | 1.7.8 | Apache License 2.0 |
| `androidx.compose.ui` | `ui-android` | 1.7.8 | Apache License 2.0 |
| `androidx.compose.ui` | `ui-geometry` | 1.7.8 | Apache License 2.0 |
| `androidx.compose.ui` | `ui-geometry-android` | 1.7.8 | Apache License 2.0 |
| `androidx.compose.ui` | `ui-graphics` | 1.7.8 | Apache License 2.0 |
| `androidx.compose.ui` | `ui-graphics-android` | 1.7.8 | Apache License 2.0 |
| `androidx.compose.ui` | `ui-text` | 1.7.8 | Apache License 2.0 |
| `androidx.compose.ui` | `ui-text-android` | 1.7.8 | Apache License 2.0 |
| `androidx.compose.ui` | `ui-unit` | 1.7.8 | Apache License 2.0 |
| `androidx.compose.ui` | `ui-unit-android` | 1.7.8 | Apache License 2.0 |
| `androidx.compose.ui` | `ui-util` | 1.7.8 | Apache License 2.0 |
| `androidx.compose.ui` | `ui-util-android` | 1.7.8 | Apache License 2.0 |
| `androidx.concurrent` | `concurrent-futures` | 1.1.0 | Apache License 2.0 |
| `androidx.core` | `core` | 1.16.0 | Apache License 2.0 |
| `androidx.core` | `core-ktx` | 1.16.0 | Apache License 2.0 |
| `androidx.core` | `core-viewtree` | 1.0.0 | Apache License 2.0 |
| `androidx.customview` | `customview` | 1.0.0 | Apache License 2.0 |
| `androidx.customview` | `customview-poolingcontainer` | 1.0.0 | Apache License 2.0 |
| `androidx.emoji2` | `emoji2` | 1.3.0 | Apache License 2.0 |
| `androidx.fragment` | `fragment` | 1.5.1 | Apache License 2.0 |
| `androidx.graphics` | `graphics-path` | 1.0.1 | Apache License 2.0 |
| `androidx.interpolator` | `interpolator` | 1.0.0 | Apache License 2.0 |
| `androidx.lifecycle` | `lifecycle-common` | 2.9.0 | Apache License 2.0 |
| `androidx.lifecycle` | `lifecycle-common-java8` | 2.9.0 | Apache License 2.0 |
| `androidx.lifecycle` | `lifecycle-common-jvm` | 2.9.0 | Apache License 2.0 |
| `androidx.lifecycle` | `lifecycle-livedata` | 2.9.0 | Apache License 2.0 |
| `androidx.lifecycle` | `lifecycle-livedata-core` | 2.9.0 | Apache License 2.0 |
| `androidx.lifecycle` | `lifecycle-livedata-core-ktx` | 2.9.0 | Apache License 2.0 |
| `androidx.lifecycle` | `lifecycle-process` | 2.9.0 | Apache License 2.0 |
| `androidx.lifecycle` | `lifecycle-runtime` | 2.9.0 | Apache License 2.0 |
| `androidx.lifecycle` | `lifecycle-runtime-android` | 2.9.0 | Apache License 2.0 |
| `androidx.lifecycle` | `lifecycle-runtime-compose` | 2.9.0 | Apache License 2.0 |
| `androidx.lifecycle` | `lifecycle-runtime-compose-android` | 2.9.0 | Apache License 2.0 |
| `androidx.lifecycle` | `lifecycle-runtime-ktx` | 2.9.0 | Apache License 2.0 |
| `androidx.lifecycle` | `lifecycle-runtime-ktx-android` | 2.9.0 | Apache License 2.0 |
| `androidx.lifecycle` | `lifecycle-viewmodel` | 2.9.0 | Apache License 2.0 |
| `androidx.lifecycle` | `lifecycle-viewmodel-android` | 2.9.0 | Apache License 2.0 |
| `androidx.lifecycle` | `lifecycle-viewmodel-compose` | 2.9.0 | Apache License 2.0 |
| `androidx.lifecycle` | `lifecycle-viewmodel-compose-android` | 2.9.0 | Apache License 2.0 |
| `androidx.lifecycle` | `lifecycle-viewmodel-ktx` | 2.9.0 | Apache License 2.0 |
| `androidx.lifecycle` | `lifecycle-viewmodel-savedstate` | 2.9.0 | Apache License 2.0 |
| `androidx.lifecycle` | `lifecycle-viewmodel-savedstate-android` | 2.9.0 | Apache License 2.0 |
| `androidx.loader` | `loader` | 1.0.0 | Apache License 2.0 |
| `androidx.profileinstaller` | `profileinstaller` | 1.4.0 | Apache License 2.0 |
| `androidx.room` | `room-common` | 2.6.1 | Apache License 2.0 |
| `androidx.room` | `room-ktx` | 2.6.1 | Apache License 2.0 |
| `androidx.room` | `room-runtime` | 2.6.1 | Apache License 2.0 |
| `androidx.savedstate` | `savedstate` | 1.3.0 | Apache License 2.0 |
| `androidx.savedstate` | `savedstate-android` | 1.3.0 | Apache License 2.0 |
| `androidx.savedstate` | `savedstate-ktx` | 1.3.0 | Apache License 2.0 |
| `androidx.sqlite` | `sqlite` | 2.4.0 | Apache License 2.0 |
| `androidx.sqlite` | `sqlite-framework` | 2.4.0 | Apache License 2.0 |
| `androidx.startup` | `startup-runtime` | 1.1.1 | Apache License 2.0 |
| `androidx.tracing` | `tracing` | 1.2.0 | Apache License 2.0 |
| `androidx.versionedparcelable` | `versionedparcelable` | 1.1.1 | Apache License 2.0 |
| `androidx.viewpager` | `viewpager` | 1.0.0 | Apache License 2.0 |
| `com.google.code.findbugs` | `jsr305` | 3.0.2 | Apache License 2.0 |
| `com.google.code.gson` | `gson` | 2.10.1 | Apache License 2.0 |
| `com.google.dagger` | `dagger` | 2.51.1 | Apache License 2.0 |
| `com.google.dagger` | `dagger-lint-aar` | 2.51.1 | Apache License 2.0 |
| `com.google.dagger` | `hilt-android` | 2.51.1 | Apache License 2.0 |
| `com.google.dagger` | `hilt-core` | 2.51.1 | Apache License 2.0 |
| `com.google.guava` | `listenablefuture` | 1.0 | Apache License 2.0 |
| `javax.inject` | `javax.inject` | 1 | Apache License 2.0 |
| `org.jetbrains` | `annotations` | 23.0.0 | Apache License 2.0 |
| `org.jetbrains.kotlin` | `kotlin-reflect` | 1.9.25 | Apache License 2.0 |
| `org.jetbrains.kotlin` | `kotlin-stdlib` | 2.0.21 | Apache License 2.0 |
| `org.jetbrains.kotlin` | `kotlin-stdlib-common` | 2.0.21 | Apache License 2.0 |
| `org.jetbrains.kotlin` | `kotlin-stdlib-jdk7` | 1.8.22 | Apache License 2.0 |
| `org.jetbrains.kotlin` | `kotlin-stdlib-jdk8` | 1.8.22 | Apache License 2.0 |
| `org.jetbrains.kotlinx` | `kotlinx-coroutines-android` | 1.7.3 | Apache License 2.0 |
| `org.jetbrains.kotlinx` | `kotlinx-coroutines-core` | 1.7.3 | Apache License 2.0 |
| `org.jetbrains.kotlinx` | `kotlinx-coroutines-core-jvm` | 1.7.3 | Apache License 2.0 |
| `org.jetbrains.kotlinx` | `kotlinx-serialization-core` | 1.7.3 | Apache License 2.0 |
| `org.jetbrains.kotlinx` | `kotlinx-serialization-core-jvm` | 1.7.3 | Apache License 2.0 |
| `org.jspecify` | `jspecify` | 1.0.0 | Apache License 2.0 |
