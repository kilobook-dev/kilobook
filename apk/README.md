# 제출용 APK

공모전 규칙: 저장소 `apk/` 폴더에 실행 가능한 release APK, 파일명 `팀명_앱이름_release.apk`.

- 파일: `KiloBook_KiloBook_release.apk` (`<teamName>_<appNameEn>_release.apk`, 두 값은 저장소 루트 `app.properties`).
  개인 참가자도 팀명·닉네임을 쓸 수 있다는 답변에 따라 팀명 자리에 앱 이름을 그대로 쓴다. 용량 제한은 600MB 이하 (운영사무국 답변).
- 설치: `adb install -r apk/KiloBook_KiloBook_release.apk` (에뮬레이터 준비는 저장소 README "테스트 환경").
- 서명 인증서: `CN=kilobook-dev, O=KiloBook` (`apksigner verify --print-certs apk/KiloBook_KiloBook_release.apk`로 확인).

## 다시 만드는 법

1. 서명 키 (처음 한 번): `scripts/create-release-key.sh` → `~/Keys/kilobook/kilobook-release.jks` (저장소 밖). keytool이 비밀번호를 묻는다.
2. 서명 값을 `~/.gradle/gradle.properties`(저장소 밖)에 넣는다. 비밀번호가 화면과 셸 기록에 남지 않게 zsh에서:
   ```zsh
   printf '%s\n' 'kilobook.release.storeFile=~/Keys/kilobook/kilobook-release.jks' 'kilobook.release.keyAlias=release' >> ~/.gradle/gradle.properties
   read -rs 'pw?키 저장소 비밀번호: ' && printf 'kilobook.release.storePassword=%s\nkilobook.release.keyPassword=%s\n' "$pw" "$pw" >> ~/.gradle/gradle.properties; unset pw; echo
   chmod 600 ~/.gradle/gradle.properties
   ```
   PKCS12 키 저장소라 키 비밀번호는 저장소 비밀번호와 같다. properties 파일은 `\`를 특수 문자로 읽으므로 비밀번호에 `\`를 쓰지 않는다. `-P` 옵션이나 `local.properties`의 `release.*` 4줄로도 줄 수 있다 (`app/build.gradle.kts`).
3. `./gradlew releaseApk` → 서명된 release APK를 이 폴더에 위 이름으로 복사한다. 서명 값이 없으면 빌드 전에 멈춘다.

## 확인 결과 (2026-11-12, v1.1.1)

| 항목 | 결과 |
|---|---|
| 서명 | `apksigner verify --print-certs`: 서명자 1명 `CN=kilobook-dev, O=KiloBook`, RSA 2048, APK Signature Scheme v2 (minSdk 28이라 v1 불필요). 인증서 SHA-256 `9a70bd5a719aefc5b36653db1e382c79d3f11fe59d57f93ad8c8b75b456ebadd` (v1.0.0·v1.1.0과 같은 키) |
| 패키지 | `com.kilobook.app`, versionName `1.1.1`, versionCode 3, debuggable 아님, 권한 10개 (Dangerous 없음. AndroidX가 넣은 앱 자체 signature 권한 1개 외 모두 normal, [permissions.md](../docs/permissions.md)) |
| 파일 | 40.6 MB (40,629,175 바이트), SHA-256 `ae5d7615ee022f767c7117e34e79ecf7b122d696f6f2d2524a2c834fa354d88b`. v1.0.0(22.6 MB)보다 큰 것은 Pleos 음성 SDK와 함께 들어온 라이브러리(dex)와 Universal Access 네이티브 라이브러리 `libuacore.so`(ABI 3종, 약 10 MB) 때문 |
| 개인 정보 | APK를 풀어 홈 폴더 경로, 개인 이메일, 컴퓨터 이름을 찾음: 없음. 걸린 문자열은 외부 라이브러리 것뿐 (Universal Access·JNA 네이티브 라이브러리의 빌드 경로, 도메인 목록 정규식, Android 상수 이름) |
| 깨끗한 설치 | 에뮬레이터 Cold Boot → 기존 앱 삭제 → 이 APK 설치. 탭은 홈·기록·설정뿐 (디버그 패널 기본 꺼짐), 설정 › 앱 정보 "킬로북 1.1.1", 음성 안내·음성으로 답하기 기본 켬 |
| S1 총 주행거리 읽기 | 통과. `checkCurrentDistanceDrivenCapabilities -> CapabilityConfig(accessType=READ)`, `getCurrentDistanceDriven -> 1.0`, 홈 "차량 총 주행거리 1.0 km" |
| S3 기어 P로 종료 + 음성 질문 | 통과. `주행 종료 #1 (GEAR_PARK): 1.0 → 2.0 km` → `speak("이번 주행 일 킬로미터, 업무였나요?")` → 7초 듣기(답 없음) → 카드 → 업무 버튼 `용도 선택 #1: BUSINESS` |
| S4 누락 감지 + 대기 안내 개수 (1.1.1 고침) | 통과. 대기 주행 2건이 있는 채로 앱 종료 중 3.0 → 3.7 km. `누락 감지 #4: 기록되지 않은 주행 0.7 km` → `Voice: 말하기 전에 카드 목록이 바뀜 -> 대기 안내 3건` → "용도 확인이 필요한 주행이 3건 있어요" (화면 "확인 대기 3건"과 같음) |
| S8 부팅 자동 시작 | 통과 (`scripts/demo.sh boot`로 진행). 다시 Cold Boot, 앱을 열지 않음. 부팅 완료 17초 뒤 `BOOT_COMPLETED`(user 10) → `startForeground(specialUse) -> ok`, 앱 창 0개. D → 0.0 → 1.0 km → P `#8 (GEAR_PARK)`, 화면이 없어 음성 없음. 앱을 열자 카드에 #8과 질문, 재부팅 전 기록 7건도 유지 (8건 · 7.7 km) |

이전 버전:
- v1.1.0 (2026-11-12, 태그 `v1.1.0`) 40.6 MB, SHA-256 `65bbec0be6e0d99bf6078f3deacbf0f64694bda945e41626f7e3f41c67f9f860`. S1·S3·S4·S8 통과. 대기 안내 개수가 화면과 어긋날 수 있어 1.1.1로 고침 ([docs/phase7](../docs/phase7/README.md) 6절).
- v1.0.0 (2026-11-09, 태그 `v1.0.0`) 22.6 MB, SHA-256 `1749baea05f32c664266a5da55e7b304850c72a57407008f41ad8f3b793b3d82`, S1·S3·S4·S8 통과.
