# 세 출처 수집과 홈 노출 정리

작성: 2026-11-02
상태: 사용자가 기존 수집 중지·토스/우아한형제들/D2만 수집·기존243건 보관/홈 숨김을 승인했다.

## 결정과 범위

기존 OpenAI/Google/GitHub/arXiv6개는 운영 disable 명령으로 중지한다. 세 기술 블로그만
활성으로 유지한다. 기존238개 기사·데모5개·요약 job6개는 삭제하지 않는다. 신규3개에는50개가 있다.
유료 이력·UNKNOWN·누적 예산은 보존하고 이번 작업은 실제 AI 생성을 하지 않는다.

홈은 활성 출처의 실제 글만 최신순으로 보여 준다. source 이름이나 host를 프론트엔드에
고정하지 않고 DTO sourceEnabled를 registry의 collection_enabled에서 읽는다. 신규 URL을
검증·활성화하면 동일 정책으로 반영된다. 데모는 curated-seed provenance도 검사해 제외한다.
검색은 이전 실제 기사의 관련도 순서를 유지해 보관 자료를 찾을 수 있게 하며 데모는 표시하지 않는다.
API/직접 링크·검색 projection/실험 fixture는 삭제하거나 줄이지 않는다.

## 구현 경계

- NewsSourceEntity에 기존 collection_enabled 열을 매핑한다. 새 DB migration은 필요 없다.
- ArticleResponse.sourceEnabled와 ArticleService mapping을 추가한다.
- Zod는 optional boolean으로 확장해 구버전 payload와 호환하되 홈에는 true로 확인한 출처만 표시한다.
- HomePage는 데모를 제외한 배열에서 active 최신 목록을 만들고 검색 관련도는 보존한다.
- API 전체 목록·직접 상세 조회는 그대로 유지한다. 비활성은 읽기 권한 차단이나 영구 삭제가 아니다.

## 검증과 기록

백엔드 service/controller에서 sourceEnabled true/false와 보관 기사 조회를 검증한다.
UI에서 비활성/데모 제외, 전부 제외된 빈 상태, 보관 검색·관련도·캐시를 검증한다.
backend test/check/bootJar와 frontend test/lint/build를 실행해 배포한다.
실제 DB에는 활성3개만 남고 옛6개 수집 요청은 selected0/excluded6이어야 한다.
새3개 소량/자동 수집, 홈50개/데모0/옛출처0, 기존293개/요약/예산 보존을 실제 확인한다.
이 수치는 관측 시점이며 다음 수집에 따라 달라질 수 있다.
