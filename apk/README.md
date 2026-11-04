# 제출용 APK

공모전 규칙: 저장소 `apk/` 폴더에 실행 가능한 release APK, 파일명 `팀명_앱이름_release.apk`.

- 파일명: `<teamName>_<AppSlug>_release.apk`. 두 값은 저장소 루트 `app.properties`에서 온다 (지금 값: `TEAMNAME_Odolog_release.apk`).
- 만드는 법: `./gradlew releaseApk` → 서명된 release APK를 이 폴더에 위 이름으로 복사한다.
  서명 값이 없으면 빌드 전에 멈춘다. 서명 키 만들기: `scripts/create-release-key.sh` (CLAUDE.md "맥북 세팅 순서").
- 설치: `adb install -r apk/<파일명>`
