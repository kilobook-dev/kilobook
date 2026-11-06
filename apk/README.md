# 제출용 APK

공모전 규칙: 저장소 `apk/` 폴더에 실행 가능한 release APK, 파일명 `팀명_앱이름_release.apk`.

- 파일명: `<teamName>_<appNameEn>_release.apk`. 두 값은 저장소 루트 `app.properties`에서 온다 (지금 값: `KiloBook_KiloBook_release.apk`).
  개인 참가라 팀명 자리에 앱 이름을 쓴다 (운영사무국 답변).
- 만드는 법: `./gradlew releaseApk` → 서명된 release APK를 이 폴더에 위 이름으로 복사한다.
  서명 값이 없으면 빌드 전에 멈춘다. 서명 키 만들기: `scripts/create-release-key.sh` (CLAUDE.md "맥북 세팅 순서").
- 설치: `adb install -r apk/<파일명>`
