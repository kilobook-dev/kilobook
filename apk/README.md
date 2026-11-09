# 제출용 APK

공모전 규칙: 저장소 `apk/` 폴더에 실행 가능한 release APK, 파일명 `팀명_앱이름_release.apk`.

- 파일: `KiloBook_KiloBook_release.apk` (`<teamName>_<appNameEn>_release.apk`, 두 값은 저장소 루트 `app.properties`).
  개인 참가라 팀명 자리에 앱 이름을 쓴다 (운영사무국 답변).
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

## 확인 결과 (2026-11-09, v1.0.0)

| 항목 | 결과 |
|---|---|
| 서명 | `apksigner verify --print-certs`: 서명자 1명 `CN=kilobook-dev, O=KiloBook`, RSA 2048, APK Signature Scheme v2 (minSdk 28이라 v1 불필요). 인증서 SHA-256 `9a70bd5a719aefc5b36653db1e382c79d3f11fe59d57f93ad8c8b75b456ebadd` |
| 패키지 | `com.kilobook.app`, versionName `1.0.0`, versionCode 1, debuggable 아님, 권한 7개 (모두 Normal) |
| 파일 | 22.6 MB, SHA-256 `1749baea05f32c664266a5da55e7b304850c72a57407008f41ad8f3b793b3d82` |
| 개인 정보 | APK를 풀어 홈 폴더 경로, 개인 이메일, 컴퓨터 이름을 찾음: 없음 |
| 깨끗한 설치 | 에뮬레이터 Cold Boot → 기존 앱 삭제 → 이 APK 설치. 탭은 홈·기록·설정뿐 (디버그 패널 기본 꺼짐), 설정 › 앱 정보 "킬로북 1.0.0" |
| S1 총 주행거리 읽기 | 통과. `checkCurrentDistanceDrivenCapabilities -> CapabilityConfig(accessType=READ)`, `getCurrentDistanceDriven -> 1.0`, 홈 "차량 총 주행거리 1.0 km" |
| S3 기어 P로 종료 | 통과. D 동안 "● 기록 중 · 이번 주행 1.0 km" → P → 카드 → 업무. `주행 종료 #1 (GEAR_PARK): 1.0 → 2.0 km` |
| S4 누락 감지 | 통과. 강제 종료 중 2.0 → 2.7 km. `누락 감지 #2: 기록되지 않은 주행 0.7 km`, 첫 콜백 2.0은 `감소 값 무시` |
| S8 부팅 자동 시작 | 통과. 다시 Cold Boot, 앱을 열지 않음. 부팅 완료 9초 뒤 `BOOT_COMPLETED`(user 10) → `startForeground(specialUse) -> ok`, `isForeground=true types=40000000`, 앱 창 0개. D → 0.0 → 1.0 km → P `#3 (GEAR_PARK)`. 앱을 열자 카드에 #3, 재부팅 전 기록 2건도 유지 |
