# CLAUDE.md — 인수인계 문서

이 저장소에서 작업을 이어받는 사람(과 Claude)이 먼저 읽는 문서. 답변과 문서는 **한국어**로 쓴다.

## 1. 프로젝트

- **무엇**: 현대자동차그룹 차량용 앱 개발 공모전(Pleos Connect, Android Automotive) 출품 앱.
- **한 줄 소개**: 차량이 직접 읽은 총 주행거리(계기판 값)로 업무용 운행기록을 남기고, 운전자는 주행이 끝난 뒤 용도(업무/출퇴근/개인)만 고르는 차량 화면 앱.
- **개발자**: iOS(SwiftUI) 개발자, Android는 처음. 심사 Q&A에서 구조를 직접 설명해야 하므로 새 Android 개념마다 iOS 비유를 붙인다 (아래 9절).
- **일정** (공식 사이트 appdevchallenge.auto-hmg.io, 2026-11-04 확인):
  - 1차 접수 마감 **2026-11-15(일) KST**, 시각 미공지 → 11/14까지 메일 발송 목표
  - 1차 결과 11/23 · 2차 오프라인 PT 12/03~04 (**실제 동작 시연 필수**: 에뮬레이터 또는 기기) · 최종 10팀은 전시용 최종 APK 제출
- **제출물**: 필수는 접수양식(기획서)과 개인정보 동의서 (사용자가 작성). 선택(가산점)은 GitHub 저장소(소스 + `apk/`), 시연 영상, 숏폼.

## 2. 앱 이름 — 킬로북 (KiloBook)

> **앱 이름 킬로북 확정 (기존 가칭 오도로그, 같은 이름의 해외 앱이 있어 변경).**

이름이 들어가는 빌드 값은 저장소 루트 `app.properties`(UTF-8) 한 곳에서 온다.

| 값 | 지금 | 쓰이는 곳 |
|---|---|---|
| `appName` | 킬로북 | 런처 이름, 화면 제목, 서비스 알림 제목 (`resValue` → `R.string.app_name`) |
| `appNameEn` | KiloBook | 제출 APK 파일명, 서명 인증서 `O=<appNameEn>` |
| `appSlug` | kilobook | 패키지 이름 `com.<appSlug>.<appIdSuffix>`, 서명 인증서 `CN=<appSlug>-dev`, 키 파일 이름 |
| `appIdSuffix` | app | 패키지 이름 마지막 부분 |
| `teamName` | KiloBook | 제출 APK 파일명 `apk/<teamName>_<appNameEn>_release.apk`. 개인 참가라 팀명 자리에 앱 이름을 쓴다 (운영사무국 답변) |

결과: 패키지 `com.kilobook.app`, 제출 APK `apk/KiloBook_KiloBook_release.apk`, 서명 인증서 `CN=kilobook-dev, O=KiloBook`.

`app.properties`로 바뀌지 않고 코드에 직접 적힌 이름 (사용자에게는 보이지 않음):

- Kotlin 패키지 `com.kilobook.app` = `app/build.gradle.kts`의 `namespace`, 소스 폴더 `app/src/main/java/com/kilobook/app/`
- 클래스 `KiloBookApp`(매니페스트 `android:name=".KiloBookApp"`), `KiloBookDatabase`, DB 파일 이름 `kilobook.db`
- logcat 태그 `KiloBook`(`AppLog.TAG`), `settings.gradle.kts`의 `rootProject.name = "KiloBook"`

서명 키는 인증서 이름이 키를 만들 때 고정된다. `scripts/create-release-key.sh`가 `app.properties` 값으로 만든다.

## 3. 현재 상태

- Phase 0~4(기술 검증) 완료, 태그 **`spike-v1`** = Phase 4 커밋.
- 그 뒤: 앱 이름 설정 한 곳으로 모음(`app.properties`), release 서명·`releaseApk` 준비, 문서 `docs/spike/`로 정리, 이 문서, 앱 이름 킬로북 반영(패키지 `com.kilobook.app`),
  운영사무국 답변 반영(권한 점검, 오픈소스 라이선스 목록, TTS/STT 동작 확인), `CAR_VENDOR_EXTENSION` 권한 삭제,
  이전 커밋 작성자를 `kilobook-dev`로 다시 씀(`spike-v1`도 새 Phase 4 커밋에 다시 닮).
- 판정: **에뮬레이터에서 기술적으로 가능.** 총 주행거리로 주행을 만들고, 기어 P로 끝내고, 화면·앱이 없을 때와 부팅 직후에도 기록된다.
- **Phase 5(제출용 MVP 화면) 완료 (2026-11-09).** 홈 / 기록 / 설정 / 디버그 탭, 정차 시 한 건씩 묻는 용도 카드, 주행 중 화면, 이번 달 요약,
  예시 기록, 오픈소스 라이선스 화면. 기록 규칙은 바꾸지 않았다. DB 버전 2(`isSample` 열, 1→2 마이그레이션). S1~S8 다시 통과.
  결과·캡처: `docs/phase5/README.md`.
- **Phase 6(제출용 release) 완료 (2026-11-09).** 서명 키 `~/Keys/kilobook/kilobook-release.jks`(저장소 밖), 서명 값은 `~/.gradle/gradle.properties`의 `kilobook.release.*`.
  `apk/KiloBook_KiloBook_release.apk` v1.0.0 (서명자 `CN=kilobook-dev, O=KiloBook`). Cold Boot·새 설치로 S1·S3·S4·S8 통과. README를 공모전 필수 4항목으로 다시 씀. 태그 `v1.0.0`.
  결과: `apk/README.md` "확인 결과".

| 시나리오 | 결과 | 요지 |
|---|---|---|
| S1 총 주행거리 읽기 | 통과 | `getCurrentDistanceDriven` km(Float). 트립 API 3종은 `ERROR_NOT_SUPPORTED_PROPERTY` |
| S2 실시간 갱신 | 통과 | `registerDistanceDriven` 1~5초마다, 같은 값도 반복해서 옴 |
| S3 기어 P로 주행 종료 | 통과 | 카드 → 용도 선택 → 완료 |
| S4 앱이 꺼진 동안 늘어난 거리 | 통과 | 시작 기준값으로 "기록되지 않은 주행" 생성, 이전 값 콜백은 무시 |
| S5 답하지 않은 주행 다시 묻기 | 통과 (Phase 3) | 화면 복귀·주행→정차 때 다시 물음, 주행 중 숨김 |
| S6 재부팅 후 기록 유지 | 통과 | 콘솔 값이 0이 되면 새 기준으로 씀 |
| S7 화면 없을 때 기록 | 통과 | 다른 앱 뒤 / 창 닫힘(서비스만) 모두 기록 |
| S8 부팅 시 자동 시작 | 통과 | 앱을 열지 않아도 `BOOT_COMPLETED`(user 10) → `specialUse` 서비스 시작 → 기록 |
| 주차 브레이크로 종료 | 검증 불가 | 콘솔에 조작 항목 없음, 항상 false. 판정 코드만 있음 |

자세한 결과: `docs/spike/phase4-scenarios.md` "결과 기록", `docs/spike/phase3-trip-recording.md` "테스트 결과".

## 4. 확정된 결정

**기록 규칙** (`recorder/TripRecorder.kt`, 모든 입력을 한 채널로 순서대로 처리)

1. **시작**: 열린 Trip이 없고 총 주행거리가 늘면 `OPEN` Trip. `startOdometer` = 늘기 직전 값.
2. **종료**: 기어 P → `GEAR_PARK`, 주차 브레이크 → `PARKING_BRAKE`. 둘 다 못 읽으면 대체 규칙: N분 무변화 → `ODOMETER_IDLE`(종료 시각 = 마지막 변화), 또는 "주행 종료" 버튼 → `MANUAL_BUTTON`.
3. **용도 확인**: 종료 → `AWAITING_PURPOSE`, 카드(업무/출퇴근/개인/나중에). 선택 → `DONE`.
4. **나중에**: `askCount`+1. 화면 복귀나 주행→정차 때 다시 물음. 주행 중이거나 판단 신호를 못 읽으면 묻지 않음.
5. **누락 감지**: 시작 때 현재 값이 `lastKnownOdometer`보다 0.1 km 이상 크고 열린 Trip이 없으면 `GAP` Trip.
6. **마지막 값 저장**: 값이 바뀔 때만 저장 + 화면이 숨겨질 때 한 번 더.
- **재시작 정리(A)**: 열린 Trip이 있고 `lastKnownAt`에서 10분(디버그 1분) 넘게 지났으면 `STALE_ON_RESTART`로 닫고, 이후 거리는 규칙 5.

**그 밖의 결정**

- **작은 값 무시**: 실행 중 현재보다 작은 총 주행거리는 쓰지 않는다 (재시작 직후 첫 콜백이 이전 값을 줌). 횟수는 화면에 표시.
- **시작 기준값**: 앱·서비스 시작 직후 누락 감지는 첫 콜백이 아니라 `getCurrentDistanceDriven` 결과로 판단. 그 값이 마지막 기록보다 작으면(에뮬레이터 재시작) 새 기준으로 쓴다.
- **DB**: Room, WAL + `syncMode=NORMAL` 유지. 갑작스러운 종료로 마지막 쓰기가 사라져도 누락 감지가 거리를 되살린다.
- **백그라운드 기록**: Foreground Service `specialUse` + `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`, `START_STICKY`. 기록 세션은 화면·서비스 소유자 수로 유지. `POST_NOTIFICATIONS`는 요청하지 않음(알림은 안 보임 — Pleos 문의 4번).
- **부팅 자동 시작**: `service/BootReceiver.kt`가 `BOOT_COMPLETED`를 받아 서비스 시작 (설정에서 백그라운드 기록이 켜져 있을 때만). 실제 차량이 Suspend-to-RAM에서 깨어날 때도 오는지는 미확인 → "앱을 열 때 누락분 정리"를 보완 경로로 유지 (Pleos 문의 5번).
- **DO 선언 위치**: `app/src/main/AndroidManifest.xml`의 `<activity android:name=".MainActivity">` 안
  `<meta-data android:name="distractionOptimized" android:value="true" />`. 주행 중에도 화면은 유지하되 카드는 숨긴다.
- **화면** (Phase 5): 탭 홈 / 기록 / 설정, 디버그 탭은 설정 › 디버그 패널 보기(기본 꺼짐, 저장)로만 보인다.
  주행 중에는 "● 기록 중 · 이번 주행 X km"만 보이고 기록·설정 탭이 숨는다 (디버그 탭은 켜져 있으면 남음).
  용도 카드는 최신부터 한 건씩, 카드 하나에 한 번만 답하고 새 카드가 뜬 뒤 0.5초는 누름 무시. "주행 종료" 버튼은 확인 카드 위.
  디버그 로그는 연속 중복을 한 줄로 묶음(×N).
- **크기 기준** (`ui/theme/KiloTheme.kt`, Pleos 디자인 가이드 2026-11-09 확인): 글자 Headline 60/48, Title 40/32, Body 26, Label 24/20 (운전자 화면 최소 20).
  버튼 높이 Large 100(용도·주행 종료) / Medium 80(탭·목록·설정). Pleos·Android 차량 문서에 최소 터치 크기 수치는 없다.
- **예시 기록**: 디버그 탭에서만 넣고 지운다 (7건, `isSample`). 홈·목록·요약에 "예시"로 표시된다. 제출 시연 전에 지울지 정한다.
- **업무사용비율** = (업무 + 출퇴근) km ÷ 총 주행거리. 국세청 업무용승용차 운행기록부 기준: ⑫ 업무용 사용거리 = ⑧ 출·퇴근용 + ⑨ 일반 업무용, ⑬ 업무사용비율 = ⑫ ÷ ⑪ 총주행거리. 용도 미확인 주행은 분모에만 넣고 "미확인 X km"로 따로 보여 준다.
- **예시 기록 버튼**: 디버그 패널 전용. 화면과 기록에 "예시"로 표시하고 한 번에 지울 수 있게 한다.
- **제출 APK**: `apk/KiloBook_KiloBook_release.apk`, release 서명, R8(코드 축소) 끔. 서명 키 파일은 저장소 밖, 비밀번호는 `local.properties`(git 제외).
- **작업 환경**: 2026-11-09부터 다른 PC에서 GitHub 저장소를 clone해 작업한다 (7절).

**운영사무국 확인 (2026-11-06)**

- **화면 없는 Foreground Service가 주행 중 차량 신호를 받을 수 있다** (운영 측 확인). 이 앱의 S7 B·S8 결과와 같다.
- **권한**: Android 표준 API는 Normal·Dangerous 권한만 쓸 수 있고, signature·privileged 권한은 쓸 수 없다.
  점검 결과는 `docs/permissions.md`. 걸리던 `CAR_VENDOR_EXTENSION`(signature|privileged, 디버그 비교 표시용)은 삭제했다 (2026-11-09). 지금 7개 모두 Normal이다.
- **공모전 자료**는 Pleos Playground 공개 문서가 전부이고, 공개 API 외에는 제공되지 않는다.
- 개인 참가자는 제출 APK 파일명의 팀명 자리에 앱 이름을 써도 된다 → `KiloBook_KiloBook_release.apk`.

**TTS/STT와 라이선스**

- **TTS/STT**: Pleos SDK 3.3.1 `ON_DEVICE`가 에뮬레이터에서 동작한다 (TTS 재생 확인, STT 엔진 응답). Android 표준 `TextToSpeech`·`SpeechRecognizer`는 동작하지 않는다.
  구현은 Phase 5 이후 승인 받고 한다. 결과와 계획은 `docs/speech-check.md`.
- **오픈소스 라이선스**: 목록은 `docs/open-source-licenses.md`, README "오픈소스 라이선스" 절. 앱 설정 화면 표기는 Phase 5 (5-10). 라이브러리가 바뀌면 목록을 다시 만든다.

## 5. 작업 규칙

1. **Phase 단위**로 진행한다. Phase가 끝나면 멈추고 보고한 뒤 승인을 받고 다음으로 간다.
2. **보고 형식**: ① 한 일(3줄 이내) ② 사용자가 직접 확인할 방법 ③ SDK 호출 결과 표(API | 결과 | 비고) ④ 새 Android 개념의 iOS 대응 ⑤ 막힌 점과 제안.
3. API 이름·권한·단위는 추측하지 않는다. Pleos 공식 문서로 확인하고 다르면 보고한다.
4. SDK가 Unsupported·null·오류를 주면 우회 코드로 덮지 않는다. 그 결과를 화면과 보고에 그대로 남긴다.
5. 새 Android 개념마다 iOS/SwiftUI 대응을 2~3줄로 설명한다.
6. Phase마다 git 커밋한다. 커밋 메시지에 `Co-Authored-By` 같은 공동 작성자 줄을 넣지 않는다 (사용자 지시. 이전 커밋에서도 지웠다, 2026-11-09).
7. 범위 밖: TTS/STT, NaviHelper, Toll, 네트워크, 파일 내보내기, 디자인. (Phase 5의 큰 글씨·큰 버튼은 승인된 범위. 그 이상 꾸미지 않는다. TTS/STT는 확인과 계획만 했고, 구현은 Phase 5 이후 따로 승인 받는다.)
8. 권한은 Normal·Dangerous만 쓴다. 새 권한을 넣을 때는 에뮬레이터에서 `adb shell pm list permissions -g -f`로 `protectionLevel`을 확인하고 `docs/permissions.md`에 적는다.
9. **블라인드 심사** (접수양식 DOCX 02 항목: 링크·첨부를 포함해 실명·계정 노출 금지):
   - 커밋 작성자는 저장소 로컬 설정 `kilobook-dev` / `354364919+kilobook-dev@users.noreply.github.com` (GitHub 계정 `kilobook-dev`의 noreply 주소. 이 형식이어야 커밋이 계정에 연결된다).
     이 PC의 전역 git 설정은 쓰지 않는다. 이전 커밋도 모두 이 이름·주소로 바꿨다 (2026-11-09).
   - 커밋, 파일, 문서, APK에 실명·개인 이메일·개인 계정·개인 경로(홈 폴더 경로, 기기 이름)를 넣지 않는다.
   - 서명 인증서 이름(CN/O)도 프로젝트 이름으로 만든다 (APK를 받은 누구나 볼 수 있음).
10. **원격 저장소**: `origin` = `git@github-kilobook:kilobook-dev/kilobook.git` (Private). push는 `kilobook-dev` 전용 SSH 키의 별칭(`github-kilobook`, 7절 4번)으로만 한다.
    PC의 `gh`와 기본 SSH 키는 개인 계정일 수 있으니 이 저장소에서 `gh` 명령을 쓰지 않는다. 강제 push 하지 않는다.

## 6. 남은 일

**바로 다음**

- ~~새 PC로 옮기기~~ (2026-11-09 완료). 실제 절차(별칭을 config 맨 위에, `AddKeysToAgent no`, git 훅, Claude Code 표기 끄기)를 7절에 반영했다.
- 제출 준비 남은 일 (아래). Phase 5·6은 끝났다.

**Phase 5 — 제출용 MVP 화면** (2026-11-09 완료, 결과 `docs/phase5/README.md`)

| # | 작업 | 내용 | 예상 |
|---|---|---|---|
| 5-0 | 기준 확인 | 차량 화면 최소 터치 크기·글자 크기를 공식 디자인 문서로 확인 (지금 버튼 72dp) | 0.5시간 |
| 5-1 | 화면 나누기 | 운전자 화면 / 설정 / 디버그 패널로 분리. 설정에 "디버그 패널 보기" 스위치(값 저장). 기록 로직은 그대로 | 1.5시간 |
| 5-2 | 정차 시 용도 확인 카드 | 방금 끝난 주행 1건을 크게 + 업무/출퇴근/개인 큰 버튼 + 나중에 | 1.5시간 |
| 5-3 | 확인 대기 카드 | "확인 대기 N건" → 한 건씩 차례로 고름 (카드가 길어지지 않게) | 1시간 |
| 5-4 | 기록 목록 | 날짜별 묶음, 거리·용도·시각, 누락 주행 표시 | 1.5시간 |
| 5-5 | 이번 달 요약 | 총 주행거리, 업무용 거리, 업무사용비율 %, 미확인 거리 (4절 공식) | 1.5시간 |
| 5-6 | 큰 글씨·큰 버튼 | 5-0의 크기를 한 곳에 모아 적용 | 1시간 |
| 5-7 | 주행 중 동작 | 카드 숨김, "기록 중 · 이번 주행 X km"만 표시 | 0.5시간 |
| 5-8 | 예시 기록 버튼 | 디버그 패널 전용, "예시" 표시, 한 번에 지우기 | 0.5시간 |
| 5-9 | 검증·문서 | S1~S8 다시 실행 + 새 화면 시나리오, 화면 캡처, `docs/phase5` | 2시간 |
| 5-10 | 오픈소스 라이선스 | 설정에 "오픈소스 라이선스": `docs/open-source-licenses.md`의 표기용 목록 + Apache 2.0 전문 | 0.5시간 |

**제출 준비**

- ~~`versionName`~~ 1.0.0으로 바꿈 (Phase 6).
- ~~서명 키 → `./gradlew releaseApk` → Cold Boot·새 설치로 S1·S3·S4·S8 → `apksigner verify --print-certs`~~ (Phase 6, `apk/README.md`).
- ~~README를 공모전 필수 4항목으로~~ (Phase 6): 앱 이름 및 한 줄 소개 / 실행 방법 / 테스트 환경 및 필요한 설정 / APK 파일 위치.
- 서명 키 백업 1부를 이 PC 밖에 둔다 (사용자).
- 시연 영상 (에뮬레이터 시간대는 한국 표준시로 바꿔 둠). 예시 기록을 쓸지, 쓴다면 화면의 "예시" 표시를 그대로 둔다.
- 비공개 저장소라면 심사용 GitHub 계정을 Collaborator로 초대 (계정 이름은 공모전 공지에서 확인).
- 기획서·동의서는 사용자가 작성. 개발 계획(SDK/API 사용 계획)은 `docs/spike/` 결과를 쓴다.
- `docs/pleos-questions.md`를 Pleos에 보낼지는 사용자가 정한다.

**일정 제안**: Phase 5·6 11/09 완료 → 시연 영상 11/10~12 → 메일 제출 11/13~14.

## 7. 새 PC 세팅 순서

개인 GitHub 계정(`gh` 로그인, `~/.ssh/id_*` 기본 키, 전역 git 설정의 실명·개인 이메일)이 있는 PC를 전제로 한다. 이 저장소에서는 `gh`를 쓰지 않고, 전역 git 설정은 바꾸지 않는다.

1. Android Studio와 Android SDK를 설치한다. Gradle 8.10.2는 JDK 17 또는 21이 필요하다 (JDK 25에서는 안 됨).
   Android Studio에 들어 있는 JDK가 25일 수 있다. 터미널 빌드는 `export JAVA_HOME="$(/usr/libexec/java_home -v 17)"`(또는 21)로, Android Studio는 Settings › Build Tools › Gradle › Gradle JDK에서 맞춘다.
   첫 빌드 때 Android SDK Platform 35가 없으면 Gradle이 자동으로 설치한다.
2. Pleos Connect Emulator v2.1.0 시스템 이미지와 AVD(`Pleos_Connect_v2.1.0`, Android 14 / API 34)를 Pleos 문서대로 설정한다:
   https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator
3. 먼저 확인만 한다 (개인 계정이 끼어들 수 있는 곳):
   ```bash
   git config --global --get core.sshCommand; echo "GIT_SSH_COMMAND=$GIT_SSH_COMMAND GIT_SSH=$GIT_SSH"
   gh auth status                                         # 로그인돼 있어도 이 저장소에서는 쓰지 않는다
   grep -n -E '^[[:space:]]*(Host|IdentityFile|AddKeysToAgent)' ~/.ssh/config
   ssh-add -l                                             # 에이전트에 올라간 키
   ```
4. `kilobook-dev` 전용 SSH 키를 PC마다 따로 만들고, 별칭 블록을 `~/.ssh/config` **맨 위에** 넣는다 (백업 후, 기존 내용은 그대로):
   ```bash
   ssh-keygen -t ed25519 -f ~/.ssh/kilobook_dev_ed25519 -C kilobook-dev -N ""
   cp ~/.ssh/config ~/.ssh/config.bak-kilobook 2>/dev/null
   { printf '%s\n' '# KiloBook project account (kilobook-dev)' 'Host github-kilobook' '    HostName github.com' '    User git' '    IdentityFile ~/.ssh/kilobook_dev_ed25519' '    IdentitiesOnly yes' '    AddKeysToAgent no' ''; cat ~/.ssh/config 2>/dev/null; } > ~/.ssh/config.new && mv ~/.ssh/config.new ~/.ssh/config && chmod 600 ~/.ssh/config
   ssh -G github-kilobook | grep -E '^(hostname|user|identityfile|identitiesonly|addkeystoagent) '
   ```
   - `identityfile`은 `~/.ssh/kilobook_dev_ed25519` 한 줄만 나와야 한다. ssh는 맞는 `Host` 블록의 `IdentityFile`을 모두 모아 앞에 적힌 것부터 쓰기 때문에 블록을 맨 위에 둔다.
     `Host *` 블록의 다른 키가 함께 나오면 그 줄을 `Host * !github-kilobook`으로 바꾼다.
   - 이 키를 `ssh-add`로 에이전트에 넣지 않는다 (`AddKeysToAgent no`). 넣으면 개인 저장소에 접속할 때 이 키가 먼저 쓰여 다른 계정으로 로그인될 수 있다.
   - 공개 키 등록: `pbcopy < ~/.ssh/kilobook_dev_ed25519.pub` → 브라우저 오른쪽 위 계정이 `kilobook-dev`인지 먼저 확인 → Settings › SSH and GPG keys › New SSH key (Title은 PC를 구분하는 이름).
     다른 PC에서 등록한 키는 지우지 않는다.
   - `ssh -T git@github-kilobook` → `Hi kilobook-dev!`. 다른 이름이 나오면 멈춘다.
5. clone하고, **바로** 저장소 안에서 작성자를 설정한다:
   ```bash
   git clone git@github-kilobook:kilobook-dev/kilobook.git
   cd kilobook
   git config user.name "kilobook-dev"
   git config user.email "354364919+kilobook-dev@users.noreply.github.com"
   git config --show-origin user.name; git config --show-origin user.email   # 둘 다 file:.git/config
   git remote -v                                                             # git@github-kilobook:kilobook-dev/kilobook.git 만
   ```
   - 3번에서 전역 `core.sshCommand`가 있었다면 `git config core.sshCommand ssh`도 저장소에 넣는다 (전역 설정이 다른 키를 지정하지 못하게).
   - `git@github.com:…`이나 `https://github.com/…` 주소로 이 저장소를 clone·fetch·push하지 않는다.
6. 실수 방지 git 훅을 설치한다 (`.git/hooks`는 커밋되지 않아 PC마다 한다):
   ```bash
   cp scripts/git-hooks/commit-msg scripts/git-hooks/pre-push .git/hooks/ && chmod +x .git/hooks/commit-msg .git/hooks/pre-push
   git commit --allow-empty -m "hook test" -m "Co-Authored-By: test <test@example.com>"   # 거부돼야 한다
   ```
   - `commit-msg`: 공동 작성자·Claude 표기 줄이 있는 커밋 메시지를 막는다.
   - `pre-push`: 원격 주소가 `github-kilobook` 별칭인지, 올릴 커밋의 작성자·커미터 이메일이 위 noreply 주소뿐인지, 공동 작성자 줄이 없는지 확인한다.
7. Claude Code의 공동 작성자·PR 표기를 끈다 (git에 올라가지 않는 로컬 설정):
   ```bash
   mkdir -p .claude && printf '%s\n' '{' '  "attribution": { "commit": "", "pr": "", "sessionUrl": false }' '}' > .claude/settings.local.json
   printf '\n.claude/settings.local.json\n' >> .git/info/exclude
   git check-ignore -v .claude/settings.local.json && git status --short   # 제외 규칙이 나오고, status는 비어 있어야 한다
   ```
8. `local.properties`(`sdk.dir`): Android Studio로 열면 생긴다. 터미널만 쓰면 `printf 'sdk.dir=%s\n' "$HOME/Library/Android/sdk" > local.properties`. git에 올라가지 않는다.
9. 빌드·설치: `./gradlew installDebug` → `adb shell am start -n com.kilobook.app/.MainActivity`
10. 에뮬레이터 준비: 시간대를 한국 표준시로 (`docs/spike/phase4-scenarios.md` "시간대 설정", 한 번 바꾸면 유지), `adb forward tcp:8088 tcp:8088` → http://127.0.0.1:8088 콘솔 열기 → S1로 연결 확인.
    콘솔 페이지 없이 터미널에서 시험할 때는 `docs/phase5/README.md` "시험할 때 알게 된 것".
11. 공식 샘플 앱이 필요하면 `reference/`에 받는다 (git 제외): `git clone https://github.com/Pleos-Playground/pleos-sample-vehicle-app reference/pleos-sample-vehicle-app`
12. 서명 키 (제출 APK를 만들 때, PC마다 새로 만들지 않는다. 처음 만든 키를 옮겨 쓴다):
    - 처음 한 번: `scripts/create-release-key.sh` → 키는 `~/Keys/kilobook/kilobook-release.jks` (저장소 밖), 인증서 `CN=kilobook-dev, O=KiloBook`. 다른 곳에 백업 1부 (잃어버리면 다시 만들 수 없음).
    - 서명 값은 `~/.gradle/gradle.properties`(저장소 밖)에 `kilobook.release.*` 4줄로 넣는다. 넣는 법은 `apk/README.md`.
    - `./gradlew releaseApk` → `apk/KiloBook_KiloBook_release.apk`

## 8. 에뮬레이터·콘솔 주의사항 (검증 중 알게 된 것)

- Virtual Console 페이지를 열어 둔다. 3초 넘게 끊기면 값 변경이 `{"error":"emulator_inactive"}`로 거부된다.
- 콘솔 총 주행거리는 줄일 수 없고, 에뮬레이터를 다시 켜면 0이 된다.
- 재부팅에 `adb reboot`을 쓰지 않는다 (에뮬레이터 크래시). 창을 닫고 다시 켠다. 이 AVD는 항상 Cold Boot로 켜진다. Quick Boot(스냅샷)로는 `BOOT_COMPLETED`가 오지 않는다.
- Android Studio Stop(■)은 강제 종료다. 그 뒤 앱을 한 번 열기 전까지는 부팅 신호를 받지 못한다.
- Back 키로 앱이 닫히지 않는다. 창 닫기: `adb shell am stack list | grep kilobook` → `adb shell am stack remove <taskId>`.
- 운전자는 user 10이다. `BOOT_COMPLETED`는 부팅 완료 후 10초~1분 넘게 걸려 온다.
- 재시작 직후 `registerDistanceDriven` 첫 콜백은 이전 값을 준다 (약 1초 뒤 현재 값).
- `PARKING_BRAKE_ON`은 항상 false, `IGNITION_STATE`는 미지원, `VENDOR_GEAR_SELECTION`은 `CAR_VENDOR_EXTENSION`(signature) 권한 거부 (그 뒤 권한과 읽기 코드 삭제).

## 9. iOS 비유 (지금까지 나온 개념)

| Android | iOS/SwiftUI에 가까운 것 |
|---|---|
| Activity (단일) | 앱의 Scene / 루트 뷰 컨트롤러 |
| Jetpack Compose, `remember`/`rememberSaveable` | SwiftUI, `@State` / `@SceneStorage` |
| ViewModel + `StateFlow` | `ObservableObject` + `@Published` |
| Room | SwiftData / Core Data |
| SharedPreferences | UserDefaults |
| `object` 싱글턴 | `static let shared` |
| AndroidManifest.xml | Info.plist + 권한(entitlements) 선언 |
| Foreground Service | 직접 대응 없음. 백그라운드 모드(위치·오디오)로 계속 실행하는 것에 가까움 |
| BroadcastReceiver + `BOOT_COMPLETED` | 직접 대응 없음. 시스템이 앱을 깨우는 백그라운드 실행(BGTaskScheduler 등)에 가까움 |
| Gradle, `app.properties` → `resValue` | Xcode 빌드 설정, `.xcconfig` → Info.plist 값 |
| 권한 보호 수준 normal / dangerous / signature·privileged | normal ≈ 선언만 하면 되는 기능, dangerous ≈ 사용자 동의 팝업(카메라·마이크), signature·privileged ≈ Apple이 승인해야 받는 entitlement |
| `TextToSpeech` / `SpeechRecognizer` (Pleos SDK도 같은 역할) | `AVSpeechSynthesizer` / `SFSpeechRecognizer` |
| 오픈소스 라이선스 목록 화면 | 설정 앱의 Acknowledgements(Settings.bundle) 또는 앱 안 "라이선스" 화면 |
| Room `Migration(1, 2)` (`ALTER TABLE`) | Core Data 경량 마이그레이션 / SwiftData `SchemaMigrationPlan`의 단계 하나 |
| `LazyColumn` / `verticalScroll` | `List`·`LazyVStack` / `ScrollView` |
| `BoxWithConstraints` (폭에 따라 4칸·2×2) | `GeometryReader` 또는 `ViewThatFits` |
| `produceState` (1분마다 이번 달 다시 계산) | `.task { }` 안의 반복 + `@State` |
| `BackHandler` | 내비게이션 스택의 뒤로 가기를 가로채는 것 (`NavigationStack` 경로 pop) |
| `assets/` 폴더 (라이선스 전문) | 앱 번들 리소스 (`Bundle.main.url(forResource:)`) |
| `Modifier.toggleable` (줄 전체를 스위치로) | `Toggle`을 `Button` 스타일 줄 전체에 붙인 것 |

## 10. 문서 위치

- `README.md` — 빌드·설치, 구조
- `docs/phase5/` — Phase 5 화면 구조, Pleos 크기 기준, 시험 결과(S1~S8 + 새 화면 N1~N12), 캡처
- `docs/spike/` — Phase 1~4 기록과 시나리오 S1~S8 (`phase4-scenarios.md`는 회귀 테스트 안내로 계속 쓴다. 맨 위에 Phase 5 화면 위치)
- `docs/permissions.md` — 권한 점검 (protectionLevel, 운영 기준 Normal·Dangerous만)
- `docs/open-source-licenses.md` — 오픈소스 라이선스 (설정 화면 표기용 목록 + 라이브러리별 부록)
- `docs/speech-check.md` — TTS/STT 동작 확인과 계획
- `docs/pleos-questions.md` — Pleos 문의 목록 (DO 배포, specialUse, 상주 서비스 기준, 알림, 부팅 자동 시작)
- `apk/README.md` — 제출 APK 규칙
