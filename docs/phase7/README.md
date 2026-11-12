# Phase 7 — 음성 용도 확인 (TTS / STT)

작업일: 2026-11-10 / 에뮬레이터: Pleos Connect v2.1.0 (Android 14) / 앱 1.1.0 (versionCode 2), 대기 안내 고침 1.1.1 (versionCode 3, 2026-11-12)

정차해서 용도 카드가 뜨면 차가 음성으로 한 번 묻고, 운전자는 버튼을 누르거나 "업무 / 출퇴근 / 개인"이라고 말해 답한다.
기록 규칙(4절 규칙 1~6)은 바꾸지 않았다. 음성은 화면의 용도 카드 위에 얹은 기능이며, 실패하면 버튼만 쓴다.

## 1. 언제 말하는가

| 상황 | 말하는 내용 |
|---|---|
| 방금 끝난 주행 (기어 P로 끝난 지 2분 이내, 처음 묻는 주행) | "이번 주행 {거리}, 업무였나요?" 예: 12.3 km → "이번 주행 십이 점 삼 킬로미터, 업무였나요?" |
| 그 밖에 용도 확인을 기다리는 주행이 있을 때 (앱을 열 때, "나중에" 뒤 다시 정차) | "용도 확인이 필요한 주행이 {N}건 있어요" |

- 거리는 소수 첫째 자리까지, 한국어 수사(한자어)로 읽는다 (`speech/VoicePrompts.kt`). 숫자를 그대로 넘기면 엔진이 "12.3"을 어떻게 읽을지 정할 수 없어서다.
- **말하지 않는 때**: 주행 중(주행 규제 `requiresDO` 또는 기어가 P가 아님. R·N 포함), 정차 신호를 못 읽을 때, 통화 중, 설정 › 음성 안내가 꺼져 있을 때, TTS 엔진이 준비되지 않았을 때(3초 기다림).
- **바로 멈추는 때**: 주행 시작(D·R), 용도 카드가 닫힘(버튼으로 답함), 앱 화면이 숨겨짐, 오디오 포커스를 잃음. 멈추면 마이크도 끄고 포커스를 돌려준다.
- **반복하지 않는다**: 카드(주행) 하나에 한 번. 끝까지 말한 주행만 "말함"으로 기억하고, 주행·화면 전환으로 끊긴 안내는 다음 정차 때 다시 말한다.
  기억은 앱 프로세스가 살아 있는 동안 유지된다 (차를 다시 켜면 대기 안내를 한 번 더 한다).

## 2. 오디오 포커스와 선행 묵음 (Pleos 가이드 기준)

- [Pleos 오디오 포커스 개발 가이드](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/audio-focus-development-guide/audio-focus-development):
  재생 전에 `AudioAttributes` + `AudioFocusRequest`로 포커스를 요청하고, 변화(LOSS / LOSS_TRANSIENT / CAN_DUCK)를 처리하고, 끝나면 반환한다. 요청과 결과는 로그에 남긴다.
- 요청 값: `USAGE_NOTIFICATION` + `CONTENT_TYPE_SPEECH`, `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` (`speech/VoiceFocus.kt`).
  가이드의 Interaction Matrix v1.0에서 새로 들어오는 NOTIFICATION은 MUSIC·NAVIGATION과 함께 재생(C)되고, VOICE_COMMAND(Gleo)·CALL_RING·EMERGENCY가 잡고 있으면 거절(R)된다.
  CALL이 잡고 있으면 C이지만, 이 앱은 통화 중에는 말하지 않는다는 규칙을 더했다.
- 포커스는 질문과 듣기를 묶은 한 번의 대화 동안 잡고 있다가 반환한다. 질문 음성은 Pleos 음성 서비스가 재생해서 앱이 소리를 줄일 수 없으므로, 어떤 상실이든 대화를 멈춘다.
- [Pleos 음성 안내 선행 묵음 가이드](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/voice-guidance-preroll-silence-guide):
  음성 앞에 150~200 ms 묵음을 두라고 하고, 오디오 데이터 안에 넣는 방식을 권한다. Pleos TTS SDK는 PCM이나 묵음 옵션을 내주지 않으므로,
  **포커스를 받은 뒤 200 ms 기다렸다가 `speak()`를 부른다.** (가이드의 권장 방식과 다른 점)

## 3. 음성으로 답하기 (STT)

- 질문이 끝나면 `SpeechToText.request()`로 한 번 듣는다. 마이크가 열린(`onReady`) 뒤 7초 동안 기다리고, 끝나면 반드시 `stop()`으로 마이크를 끈다.
- 인식한 문장 → 용도 (`VoicePrompts.purposeFromSpeech`): "출퇴근/출근/퇴근" → 출퇴근, "업무" → 업무, "개인/사적" → 개인.
  질문이 "업무였나요?"이므로 "네/예/응/맞아/맞습니다/그래(요)"만 말하면 업무. "아니요, 개인이요"처럼 부정 뒤에 업무가 아닌 용도가 하나 나오면 그 용도.
  두 용도가 함께 나오거나("업무 아니고 개인"), 부정 뒤에 업무가 나오거나, 용도가 없으면 고르지 않고 버튼을 기다린다.
  단위 테스트: `app/src/test/java/com/kilobook/app/speech/VoicePromptsTest.kt` (`./gradlew :app:testDebugUnitTest`, 6개 통과).
- 알아들은 용도는 버튼을 누른 것과 같은 경로(`choosePurpose`)로 저장한다. 카드에는 "음성으로 묻는 중", "● 듣고 있어요 · "업무", "출퇴근", "개인" 중 하나를 말해 주세요"가 보인다.
- **권한**: `STT_SERVICE`(normal)만 쓴다. `RECORD_AUDIO`는 필요 없다. 마이크는 Pleos 음성 서비스가 연다 ([permissions.md](../permissions.md)).

## 4. 설정

| 설정 | 기본 | 내용 |
|---|---|---|
| 음성 안내 | 켬 | 정차해서 용도 카드가 뜨면 음성으로 한 번 묻는다 |
| 음성으로 답하기 | 켬 (V8에서 인식 확인) | 질문 뒤 "업무 / 출퇴근 / 개인"이라고 말하면 버튼을 누른 것과 같다. 음성 안내가 켜져 있을 때만 쓴다 |

화면이 보일 때만 SDK를 연다 (`onStart` → `initialize`, `onStop` → `stop`·`removeListener`·`release`). 백그라운드 서비스에서는 음성을 쓰지 않는다.

## 5. 시험 결과 (디버그 빌드, 에뮬레이터)

| # | 시험 | 결과 | 근거 |
|---|---|---|---|
| V1 | 앱을 열 때 답하지 않은 주행이 있음 | 통과 | "용도 확인이 필요한 주행이 1건 있어요" 한 번. 사람이 듣고 확인 |
| V2 | 탭 이동, 다른 앱으로 갔다가 복귀, 런처에서 다시 열기 | 통과 | 다시 말하지 않음 (6절 첫 항목을 고친 뒤) |
| V3 | 주행 중 (D, 총 주행거리 증가) | 통과 | 음성 로그 없음 |
| V4 | 정차 (P) | 통과 | 종료 0.2~0.4초 뒤 포커스 `GRANTED` → 200 ms 뒤 `speak("이번 주행 일 점 육 킬로미터, 업무였나요?")` → `onDone`(3.1초) → 듣기. 사람이 "이번 주행 1km 업무였나요?"를 듣고 확인 |
| V5 | 말하는 중 D 또는 R | 통과 | "Voice: 중단 (주행 시작)" → 포커스 반환 → `TextToSpeech.stop -> onStop`. 사람이 D·R 모두 확인 |
| V6 | 기어 R인 채로 앱을 엶 | 통과 | 말하지 않음 (R은 주행 중으로 본다) |
| V7 | 듣기 (아무 말 없음) | 통과 | `request` → `onReady`(0.1~2.1초) → 7초 동안 입력 크기 콜백 69~70회 → 결과 없음 → `stop` → 서비스 녹음 종료(`close`, readCnt=70) → 포커스 반환. 처음에는 `stop`을 부르지 않았다 (6절) |
| V8 | "업무" / "출퇴근" / "개인" / "아니요, 개인이요"라고 말함 | 통과 | 차례로 #14 업무, #15 출퇴근, #16 개인, #17 개인("아니요 개인이요"). 말이 끝나고 약 1초 뒤 최종 결과. 컴퓨터 내장 마이크, 카페 소음 속 (2026-11-12) |
| V9 | 설정 › 음성 안내 끔 | 통과 | 주행 #18이 끝나도 TTS·STT 호출 없음. "음성으로 답하기"는 비활성 + "음성 안내가 켜져 있을 때만 씁니다" |
| V10 | TTS 초기화 실패 | 코드 확인 | 에뮬레이터에서 실패를 만들 수 없음. 실패하면 로그를 남기고 버튼만 보인다 |
| V11 | 통화 중 | 코드 확인 | 에뮬레이터에 통화 기능 없음. `AudioManager.mode`가 통화·벨이면 말하지 않는다 |
| V12 | release APK, Cold Boot·새 설치 | 통과 | 1.1.0과 1.1.1 모두. S1·S3·S4·S8과 정차 질문·대기 안내. 자세히는 [apk/README.md](../../apk/README.md) |
| V13 | 대기 주행이 있을 때 앱이 꺼진 동안 주행 → 앱 열기 | 통과 (1.1.1) | 화면 "확인 대기 3건"과 음성 "3건 있어요"가 같다. 1.1.0은 "2건"이라고 말했다 (6절) |

## 6. 시험하며 알게 된 것

- **런처에서 앱을 열 때마다 화면이 하나씩 쌓였다.** 같은 작업 안에 `MainActivity`가 새로 만들어져 ViewModel도 새로 생기고, 대기 안내가 열 때마다 반복됐다.
  `android:launchMode="singleTop"`으로 기존 화면을 다시 쓰게 했고(`wm_new_intent`), "이미 말한 주행" 기억도 프로세스 단위로 옮겼다.
- **말하다 끊긴 안내를 "말함"으로 치지 않는다.** 처음에는 말하기 전에 기억해서, 화면 전환으로 0.02초 만에 끊긴 안내가 다시 나오지 않았다.
- **듣기 시간은 마이크가 열린 뒤부터 센다.** `request()` → `onReady`가 최대 2.1초 걸려 그만큼 듣는 시간이 줄었었다.
- **듣기가 끝나도 마이크가 열려 있었다.** 시간이 다 되면 `stop()`을 부르지 않아, Pleos 음성 서비스가 다음 요청 때까지 녹음을 계속했다 (한 번은 약 6분, 서비스 로그 `readCnt=3724`).
  `request()`부터 `stop()`까지를 따로 표시해 듣기가 어떻게 끝나든 `stop()`을 부른다.
- **최종 결과는 `onEndedRecognition` 뒤에 온다.** 순서: `onStartedRecognition` → `onUpdated`(중간) → `onEndedRecognition` → 약 30 ms 뒤 `onUpdated(completed=true)`.
  끝 신호에서 바로 `stop()`하면 서비스가 최종 결과를 버린다 (`sendSttDataIfLatest ignore`). 끝 신호 뒤 1초 기다리고, 최종 결과가 없으면 마지막 중간 결과를 쓴다.
- **마이크 입력 확인**: 디버그 로그의 "SpeechToText: … RMS n회 최소 x 최대 y". 소리가 안 들어오면 1~4에 머물고, 들어오면 수백~수천이 된다.
- **블루투스 이어폰 마이크로는 인식되지 않았다.** 입력 크기는 올라갔지만(최대 910) 인식기가 말 시작도 감지하지 못했고, 컴퓨터 쪽 에뮬레이터 로그에 오디오 변환 오류(-50)가 있었다.
  컴퓨터 입력을 내장 마이크로 바꾸자 바로 인식됐다. 블루투스 이어폰을 쓰면 컴퓨터 스피커로 낸 소리도 이어폰 마이크에 들어가지 않는다.
- **말할 내용은 말하기 직전에 정한다 (1.1.1).** 앱을 열면 카드가 기존 주행으로 먼저 뜨고, 약 0.01초 뒤 누락 감지가 주행을 하나 더한다.
  1.1.0은 카드가 처음 뜬 순간의 목록으로 문장을 만들어, 화면은 "확인 대기 2건"인데 "1건 있어요"라고 했다. 정차 2분 안이면 새 카드를 띄워 둔 채 이전 주행을 묻기도 했다.
  이제 선행 묵음이 끝난 뒤 문장을 만들고, 그 전에 카드 목록이 바뀌면 화면에 보이는 목록으로 대기 안내를 한다 (로그 "Voice: 말하기 전에 카드 목록이 바뀜"). 시연 스크립트로 찾았다.
- **한 번 관찰 (원인 미확인)**: 1.1.1 시험 중 1.0 km 주행이 0.12 km와 0.88 km 두 개로 나뉘었다. 첫 거리 값 직후 앱이 기어 P 신호를 한 번 받아 `GEAR_PARK`로 끝냈다 (D는 그보다 앞서 보냈다).
  같은 순서로 세 번 다시 해도 나오지 않았고, 그때 로그는 시연 스크립트가 지워 남지 않았다. 기록 규칙은 바꾸지 않았다 (CLAUDE.md 5절 규칙 4).
  시연 스크립트는 이제 로그를 지우지 않고, D로 바꾼 뒤 1초 있다가 거리를 올린다. 다시 나오면 `adb logcat -s KiloBook:I`의 `onSelected`·`onChangeEvent(GEAR_SELECTION)` 줄을 본다.
- **콘솔에서 질문을 들으려면 총 주행거리를 올려야 한다.** 기어만 D → P로 바꾸면 주행이 생기지 않아 물을 것이 없다 (규칙 1).
- **에뮬레이터 마이크 설정**: Extended Controls › Microphone › **Enable Host Microphone Access** 켬, **Simulate Insert Headset** 끔 (가상 헤드셋 마이크로 바뀔 수 있다).
  에뮬레이터를 다시 켜면 꺼진다. 에뮬레이터 버전에 따라 이름이 "Virtual microphone uses host audio input"이다.
- **Back 키** (`singleTop` 뒤 다시 확인): 앱 안에서는 동작한다 (라이선스 화면이 닫힘). 첫 화면에서 누르면 시스템이 앱을 그대로 둔다 (일시정지·종료 기록 없음).

## 7. SDK 호출 결과

| API | 결과 | 비고 |
|---|---|---|
| `TextToSpeech(context, Mode.ON_DEVICE).initialize` | `onSuccess` | 화면이 보일 때마다 |
| `addEventListener` / `setLocale(ko_KR)` / `setAudioAttributes(NOTIFICATION, SPEECH)` | ok | |
| `AudioManager.requestAudioFocus(GAIN_TRANSIENT_MAY_DUCK)` | `GRANTED` | 매번 |
| `TextToSpeech.speak(text, …)` | `onStart` → `onDone` | 질문 약 3초, 대기 안내 약 3초 |
| `TextToSpeech.stop` | `onStop` | 말하는 중 D·R |
| `SpeechToText(context, Mode.ON_DEVICE).initialize` | `onSuccess` | |
| `addListener` / `setLocale(ko_KR)` | ok | |
| `SpeechToText.request` | `onReady` (0.1~2.1초) → `onStartedRecognition` → `onUpdated`(중간) → `onEndedRecognition` → `onUpdated(completed=true)` → `onUpdatedEpdData(0, 32000)` | 듣는 동안 `onUpdatedRms` 약 10회/초 |
| `SpeechToText.stop` | ok | 서비스가 녹음을 끝낸다 (`close` → `Session.stop`) |
| `AudioManager.abandonAudioFocusRequest` | `GRANTED` | 매번 |
| `removeEventListener`·`release` / `stop`·`removeListener`·`release` | ok | 화면 숨김 때 |

## 8. 새 Android 개념의 iOS 대응

| Android | iOS/SwiftUI에 가까운 것 |
|---|---|
| 오디오 포커스 (`AudioFocusRequest`, `GAIN_TRANSIENT_MAY_DUCK`) | `AVAudioSession` 카테고리 + `.duckOthers` 옵션으로 활성화하고, 끝나면 `setActive(false, .notifyOthersOnDeactivation)` |
| 포커스 상실 콜백 (`onAudioFocusChange`) | `AVAudioSession.interruptionNotification` |
| `suspendCancellableCoroutine` (콜백 SDK를 `suspend` 함수로) | `withCheckedContinuation` |
| `launchMode="singleTop"` (같은 화면을 새로 만들지 않고 `onNewIntent`로 전달) | 이미 떠 있는 Scene을 다시 쓰는 것 (새 창을 만들지 않고 `onOpenURL` 등으로 받음) |
| `LaunchedEffect(key)` / `DisposableEffect` (카드가 뜰 때·사라질 때) | `.task(id:)` / `.onDisappear` |
