# Pleos 문의 목록

기술 검증 앱(킬로북, Pleos Connect v2.1.0 에뮬레이터, Vehicle SDK 2.3.2)을 만들며 문서로 확인하지 못한 것.
각 항목: 지금 한 것 → 궁금한 것.

## 1. DO(Distraction Optimization) 선언 배포 허용

- 한 것: `MainActivity`에 `<meta-data android:name="distractionOptimized" android:value="true"/>` (Android 표준 방식).
  adb로 설치한 디버그 빌드는 기어 D(주행 규제 0x50)에서도 화면이 유지됐다. Pleos 샘플 앱(미선언)은 같은 조건에서 가려졌다.
- Pleos 정책 문서: "DO 미선언 앱은 Parking에서만 사용 가능". 선언 방법이나 허용 기준은 문서에 없다.
- 궁금한 것:
  - 공모전 제출·스토어 배포 앱이 이 선언을 쓸 수 있는가? 별도 심사나 허용 목록(allowlist)이 필요한가?
  - 허용된다면 주행 중 화면에서 지켜야 할 기준 (글자 크기, 터치 횟수, `activeRestrictions`별 제한)은?
  - 이 앱은 주행 중에 확인 카드를 띄우지 않는다. 주행 중 표시 내용이 실시간 패널뿐이라면 DO 없이 가는 것이 권장되는가?

## 2. Foreground Service `specialUse` 유형 허용

- 한 것: 주행 기록 서비스를 `android:foregroundServiceType="specialUse"` + `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`
  ("Records driving trips from the vehicle odometer and gear while the app is not on screen")로 선언.
  표준 유형 중 "차량 신호 관찰"에 맞는 것이 없어 골랐다 (`location`·`dataSync`는 하는 일과 다름).
- 궁금한 것:
  - Pleos Connect에서 `specialUse` 유형이 허용되는가? 다른 유형을 권장하는가?
  - Google Play처럼 subtype 설명을 심사하는가? 심사 기준은?

## 3. 상주 서비스 심사 기준

- Pleos "앱 구조 및 동작 모델": 백그라운드 작업은 실행 조건과 중단·재개 정책을 정의하고, 상주·타이머·폴링을 최소화하라고 한다.
- 이 앱의 정책: 차량 리스너만 사용 (폴링 없음), 타이머는 대체 규칙의 1회성 무변화 판정 하나. 설정에서 백그라운드 기록을 끌 수 있다.
- 궁금한 것:
  - 상주 서비스의 허용 조건이나 한도 (CPU, 메모리, 실행 시간)가 있는가?
  - 사용자에게 켜고 끄는 설정을 반드시 제공해야 하는가?
  - 시동을 끄거나 차량이 저전력 상태로 들어갈 때 앱이 해야 할 일 (서비스 종료, 저장 시점)이 정해져 있는가?

## 4. 서비스 알림 표시 의무

- 한 것: `POST_NOTIFICATIONS` 권한을 요청하지 않는다. 확인 결과 서비스는 포그라운드로 동작하지만 (`isForeground=true`)
  `dumpsys notification`에 이 앱의 알림 기록이 없어 알림이 표시되지 않는다.
- Android 문서: Foreground Service는 상태 표시줄 알림으로 사용자에게 알린다.
- 궁금한 것:
  - Pleos Connect에서 Foreground Service 알림을 반드시 보여야 하는가? 보인다면 어디에 (알림 센터, 상태 표시줄)?
  - `POST_NOTIFICATIONS` 권한을 요청해야 하는가? 요청 화면을 차량에서 어떻게 띄우는 것이 권장되는가?

## 5. 부팅 시 자동 시작 허용

- 한 것: `RECEIVE_BOOT_COMPLETED` 권한 + `BOOT_COMPLETED` 수신기에서 서비스 시작 (설정에서 백그라운드 기록이 켜져 있을 때만).
- 에뮬레이터 결과: Cold Boot 뒤 앱을 열지 않아도 user 10에서 `BOOT_COMPLETED`를 받아 `specialUse` 서비스가 시작됐고
  (부팅 완료 후 약 12초, 예외 없음), 앱을 연 적 없이 주행 1건이 기록됐다. 자세한 내용은 [Phase 4 문서](spike/phase4-scenarios.md)의 S8.
- Android 문서 확인 결과:
  - `BOOT_COMPLETED` 수신은 Android 12+ 백그라운드 시작 제한의 예외다.
  - `BOOT_COMPLETED` 수신기에서 시작할 수 없는 유형은 dataSync, camera, mediaPlayback, phoneCall, mediaProjection, microphone이며
    `specialUse`는 목록에 없다 (Android 15 동작 변경, microphone은 Android 14부터).
- AAOS 전원 문서: 차량은 Suspend-to-RAM에서 깨어날 수 있고, 전원 상태 API(`CarPowerManager`)는 `@SystemApi`라 일반 앱이 쓸 수 없다.
- 궁금한 것:
  - Pleos Connect가 일반 앱의 부팅 시 자동 시작을 허용하는가?
  - 실제 차량은 시동을 걸 때마다 부팅하는가, Suspend-to-RAM에서 깨어나는가? 깨어나는 경우 `BOOT_COMPLETED`가 오지 않을 텐데,
    일반 앱이 "차량이 깨어남"을 알 수 있는 신호가 있는가?

## 추가로 물어볼 만한 것 (선택)

- 트립 API (`getTripCurrentDrivingDistance`, `getTripCurrentDrivingTime`)가 에뮬레이터에서 `ERROR_NOT_SUPPORTED_PROPERTY`다. 실제 차량에서는 지원되는가?
- 앱을 강제 종료한 뒤 다시 실행하면 `registerDistanceDriven` 첫 콜백이 종료 전 값을 준다 (현재 값은 약 1초 뒤). 의도된 동작인가?
- Virtual Console에 주차 브레이크(`PARKING_BRAKE_ON`) 조작이 없다. 추가 계획이 있는가?
