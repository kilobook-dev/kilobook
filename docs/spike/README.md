# 기술 검증(스파이크) 기록 — Phase 1~4

"에뮬레이터에서 차량 주행거리로 운행기록을 남길 수 있는가"를 판정한 기록. 결과는 git 태그 `spike-v1`(Phase 4 커밋)에 해당한다.
이 문서들은 확인 당시 기록이라 이름이 바뀌어도 고치지 않는다 (당시 앱 이름 `오도로그 스파이크`, 패키지 `com.odolog.spike`, 로그 태그 `OdologSpike`).

| 문서 | 내용 |
|---|---|
| [Phase 1 — 샘플 앱 연결 확인](phase1-sample-app.md) | 공식 샘플 빌드, Odometer SDK 결과, 문서와 다른 점 |
| [Phase 2 — 실시간 값 표시](phase2-live-panel.md) | 총 주행거리·트립·기어·주차 브레이크·주행 규제 표시와 Capability 결과 |
| [Phase 3 — 주행 기록과 백그라운드 기록](phase3-trip-recording.md) | 기록 규칙 1~6, 재시작 정리, Foreground Service, 테스트 결과 |
| [Phase 4 — 디버그 로그와 검증 시나리오 S1~S8](phase4-scenarios.md) | 시나리오 순서와 결과, 부팅 자동 시작, 시간대 설정. 회귀 테스트 안내로 계속 쓴다 |

Pleos에 물어볼 것: [../pleos-questions.md](../pleos-questions.md)
