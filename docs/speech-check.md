# TTS/STT 동작 확인과 계획

> **Phase 7에서 구현했다 (2026-11-10).** 구현과 시험 결과는 [docs/phase7/README.md](phase7/README.md).
> 이 문서와 달라진 점: `RECORD_AUDIO`는 필요 없다 (아래 2절), 쓰임은 "정차 후 한 번 묻기 + 음성 답"으로 정했다.

확인일: 2026-11-06 / 에뮬레이터: Pleos Connect v2.1.0 (Android 14), Pleos 음성 서비스 `ai.pleos.playground.caas` 4.2.10

질문: Pleos TTS/STT가 문서상 "준비 중"이라는데 실제로 동작하는가? 안 되면 Android 표준 TextToSpeech·SpeechRecognizer를 대안으로 쓴다 (구현은 Phase 5 이후).
운영사무국: 공모전 자료는 Pleos Playground 공개 문서가 전부이고, 공개 API 외에는 제공되지 않는다.

## 1. 결론

| API | 에뮬레이터 결과 | 판정 |
|---|---|---|
| Pleos TextToSpeech 3.3.1, `Mode.ON_DEVICE` | `initialize` → `onSuccess`, `speak` → `onReady` → `onStart` → `onDone`. 음성 서비스 로그: `ko_KR`, 화자 `seoyoon`, PCM 82,432바이트 재생 | **동작** |
| Pleos SpeechToText 3.3.1, `Mode.ON_DEVICE` | `initialize` → `onSuccess`, `request` → `onReady`. 엔진이 마이크 입력을 계속 처리한다 (에뮬레이터 마이크에 소리가 없어 인식 결과는 없음) | **엔진 동작.** 실제 인식은 마이크를 켜고 사람이 말해서 확인 필요 |
| Android `TextToSpeech` | `onInit` = `ERROR(-1)`, `getEngines()` 빈 목록. 엔진 패키지를 직접 지정해도 같음 | **동작 안 함** |
| Android `SpeechRecognizer` | `isRecognitionAvailable` = true, 시작 요청은 기본 인식 서비스 `ai.umos.ambientai`에 전달됨. 그러나 45초 동안 콜백 없음 (재부팅 뒤 다시 확인) | **동작 안 함** |

음성 기능을 넣는다면 **Pleos SDK `ON_DEVICE`**로 간다. Android 표준 API는 이 에뮬레이터에서 대안이 되지 못한다.

- Android `TextToSpeech` 실패 원인 (추정): 에뮬레이터의 TTS 서비스는 Pleos 음성 서비스 하나뿐인데,
  Android 표준 TTS 엔진으로 잡히지 않는다 (`pm query-services -a android.intent.action.TTS_SERVICE` 결과 `isDefault=false`, 앱의 `getEngines()`는 빈 목록).
- 실제 차량에서 Android 표준 API가 되는지는 확인하지 못했다.

## 2. 공식 문서 확인

- **"준비 중" 표시를 찾지 못했다.** 한국어·영문 TTS/STT 소개 페이지, SDK 목록, 사이드바에 "준비 중"이나 "Coming soon"이 없다.
  두 소개 페이지 모두 "SDK v3.3.1 기준"이라고 쓰여 있다. 다른 곳에서 "준비 중"을 봤다면 그 위치를 다시 확인해야 한다.
  - https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro
  - https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/intro
- **의존성**: `ai.pleos.playground:TextToSpeech:3.3.1`, `ai.pleos.playground:SpeechToText:3.3.1` (Vehicle SDK와 같은 Pleos 저장소).
- **Mode**: `HYBRID`(기본. 서버 우선, 네트워크 실패 시 기기 내), `SERVER`(네트워크 + Pleos Playground 앱 등록 필요), `ON_DEVICE`(네트워크·앱 등록 필요 없음).
  네트워크는 이 앱의 범위 밖이므로 `ON_DEVICE`만 쓴다.
- **권한**: TTS `pleos.car.permission.TTS_SERVICE`, STT `pleos.car.permission.STT_SERVICE`. 둘 다 normal (에뮬레이터 확인).
  ~~STT는 마이크를 쓰므로 `android.permission.RECORD_AUDIO`도 필요하다.~~ → **필요 없다 (Phase 7 확인).** 마이크는 Pleos 음성 서비스가 열고,
  STT 문서(Declaring permissions)와 SDK 매니페스트가 요구하는 권한은 `STT_SERVICE`뿐이다. 이 확인용 앱은 `RECORD_AUDIO`를 부여한 채로 시험했었다.
- **SDK가 더하는 것**: 매니페스트 병합으로 `INTERNET`(normal)이 들어온다 (SDK 주석 "CCG Validation 체크를 위한 권한").
  Pleos 음성 서비스 최소 버전 4.1.29 (SDK 매니페스트), 에뮬레이터는 4.2.10.
- **문서 호출 순서**: TTS `initialize` → `addEventListener` → (`setLocale`) → (앱 등록: 서버 모드만) → `speak` → `stop` → `removeEventListener` → `release`.
  STT `initialize` → `addListener` → (`setLocale`) → (앱 등록: 서버 모드만) → `request` → `stop` → `removeListener` → `release`.

## 3. 확인하면서 알게 된 주의사항

- `initialize()`보다 리스너 등록을 먼저 하면 SDK 안에서 `UninitializedPropertyAccessException`이 난다. 문서 순서대로 `initialize()`를 먼저 부른다.
- 확인용 앱을 강제 종료하고 삭제해도 Pleos 음성 서비스가 그 앱의 STT 인식 작업을 계속했다. 에뮬레이터를 다시 켜서야 멈췄다.
  구현할 때는 화면을 떠날 때 `stop()`·`release()`를 반드시 부른다 (Pleos 문의 후보).
- 에뮬레이터 마이크 입력은 Pleos 문서의 에뮬레이터 설정 중 "마이크 접근 설정" 단계를 따라 켠다.

## 4. 계획 (Phase 5 이후, 구현은 승인 후)

| # | 작업 | 내용 | 예상 |
|---|---|---|---|
| S-1 | 쓰임 확정 | 예: 정차 후 "방금 주행 X km, 용도를 골라 주세요" 음성 안내(TTS), "업무 / 출퇴근 / 개인" 음성 답(STT). 주행 중에는 말하지 않는다 | 사용자 결정 |
| S-2 | 구조 | `speech/` 패키지: `SpeechOutput`·`SpeechInput` 인터페이스 + Pleos `ON_DEVICE` 구현. 인식 결과는 화면 버튼과 같은 "용도 선택" 경로로 넣는다. 음성이 실패하면 버튼만 쓴다 | 1.5시간 |
| S-3 | 권한 | `TTS_SERVICE`, `STT_SERVICE`(normal) 선언. (Phase 7: `RECORD_AUDIO`는 필요 없어 넣지 않음) | 0.5시간 |
| S-4 | 수명 | 화면 `onStop`에서 `stop()`·`release()`. 백그라운드 서비스에서는 음성을 쓰지 않는다 | 0.5시간 |
| S-5 | 검증 | 에뮬레이터 마이크를 켜고 "업무"·"출퇴근"·"개인" 인식률 확인, 오류 결과(`ErrorType`)를 화면·보고에 그대로 남김 | 1시간 |
| S-6 | 문서 | 오픈소스 라이선스 목록 갱신 (TTS/STT SDK, `universal-access`) | 0.2시간 |

Android 표준 `TextToSpeech`·`SpeechRecognizer` 구현은 만들지 않는다. 실제 차량에서 동작이 확인되면 그때 `SpeechOutput`·`SpeechInput`의 다른 구현으로 추가한다.

## 5. 재현 방법

확인용 앱 소스는 `reference/speech-probe/`에 있다 (git 제외, 이 저장소에 포함되지 않음). 단계별로 실행한다:

```bash
adb install -r reference/speech-probe/app/build/outputs/apk/debug/app-debug.apk   # 먼저 그 폴더에서 ./gradlew assembleDebug
adb shell pm grant --user 10 com.kilobook.speechprobe android.permission.RECORD_AUDIO
adb shell am start -n com.kilobook.speechprobe/.ProbeActivity --es step pleos_tts --es mode ON_DEVICE   # pleos_stt, android_tts, android_stt
adb logcat -s SpeechProbe:I
```
