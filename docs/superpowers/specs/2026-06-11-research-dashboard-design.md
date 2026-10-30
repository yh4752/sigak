# Research Dashboard Design

날짜: 2026-06-11

## Summary

Sigak에 `/research` 페이지를 추가해 취업 포트폴리오에서 보여줄 수 있는 실험 근거를 제품 안에서 확인하게 만든다.
이 페이지는 장식용 차트가 아니라, 실제 `experiments/results` artifact를 읽어 retrieval, graph-aware insight, dataset scope, 남은 검증 과제를 한 화면에 정리하는 evidence dashboard다.

선택한 방향은 Spring Boot API + React dashboard다.

```txt
experiments/results/*.json
-> Spring Boot /api/research/overview
-> React /research
-> portfolio reviewer가 서비스 안에서 지표와 한계를 확인
```

대시보드는 예뻐야 하지만, 숫자를 크게 꾸며 과장하지 않는다.
Sigak의 연구 역량을 설득하는 방법은 "좋아 보이는 그래프"가 아니라 "실험 범위, 성능 지표, 실패/한계, 다음 과제"를 정직하고 세련되게 보여주는 것이다.

## Current State

이미 준비된 것:

- `experiments/results/retrieval/latest/metrics.comparison.json`
- `experiments/results/retrieval/latest/metrics.summary.json`
- `experiments/results/graph/latest/graph-context.metrics.summary.json`
- `experiments/results/graph/latest/graph-context.metrics.by-query.json`
- `experiments/datasets/raw/articles.catalog.api-ready-2026-06-05.json`
- React Router 기반 frontend routes: `/`, `/articles/:id`
- Spring Boot public API boundary
- frontend API client + Zod validation pattern

현재 artifact의 해석 범위:

- retrieval/graph smoke artifact는 3 reviewed queries와 6-article baseline catalog 기반이다.
- expanded API-ready catalog는 41 articles까지 늘었지만, reviewed labels가 아직 부족해 품질 주장용 benchmark로 쓰면 안 된다.
- graph-aware metric은 public API round-trip 기반이며 순수 Neo4j query latency가 아니다.
- graph reason은 projection에 저장된 reason이며 독립적인 factual verification 결과가 아니다.

## Problem

현재 Sigak은 backend, frontend, search projection, graph projection, benchmark artifact가 따로는 존재하지만, 포트폴리오 리뷰어가 한 화면에서 다음 질문에 답하기 어렵다.

1. 이 프로젝트가 단순 CRUD가 아니라 AI search/retrieval system임을 어디서 확인할 수 있는가?
2. keyword, vector, hybrid, public search가 어떤 지표로 비교되는가?
3. graph-aware context가 실제로 어떤 보조 근거를 제공하는가?
4. 현재 숫자가 smoke 수준인지, 포트폴리오 품질 주장 수준인지 어떻게 구분하는가?
5. 아직 라벨링이 부족한 상태에서 무엇을 보여주고 무엇은 말하지 말아야 하는가?

따라서 `/research`는 제품 화면 안에서 "검증된 것"과 "아직 검증 전인 것"을 함께 보여줘야 한다.

## Goals

- `/research` route를 추가한다.
- backend는 `GET /api/research/overview`로 연구 대시보드용 요약 데이터를 제공한다.
- frontend는 Spring Boot API만 호출하고 Zod로 응답을 검증한다.
- retrieval comparison 지표를 keyword/vector/hybrid/public 시스템별로 비교한다.
- graph-aware insight 지표와 query-level evidence를 함께 보여준다.
- expanded catalog 상태를 보여주되, reviewed labels 부족으로 benchmark claim은 제한한다.
- 포트폴리오용으로 보기 좋은 화면을 만든다: 간결한 레이아웃, 명확한 정보 위계, 읽기 쉬운 metric table, 제한 사항을 숨기지 않는 visual treatment.
- artifact가 없거나 일부만 있어도 페이지 전체가 깨지지 않고 unavailable state를 보여준다.

## Non-Goals

- 이번 범위에서 새 실험 runner를 만들지 않는다.
- 이번 범위에서 label set을 새로 만들거나 확장하지 않는다.
- 이번 범위에서 RAG evaluation, fine-tuning evaluation, cost tracking을 새로 계산하지 않는다.
- chart library를 새로 도입하지 않는다.
- full graph explorer나 query drill-down page를 만들지 않는다.
- frontend가 `experiments/` 파일을 직접 읽지 않는다.
- smoke artifact로 품질 우수성을 단정하지 않는다.

## Approach Comparison

### A안. Spring Boot API + React dashboard

흐름:

```txt
backend service reads committed experiment artifacts
-> maps them to explicit DTOs
-> frontend fetches /api/research/overview
-> /research renders metric dashboard
```

장점:

- Sigak 원칙인 "frontend는 Spring Boot만 바라본다"와 맞다.
- backend DTO, frontend Zod schema, UI까지 연결되어 portfolio full-stack evidence가 된다.
- artifact parsing과 missing-file fallback을 backend에서 책임질 수 있다.
- 후속 단계에서 RAG/failure/cost summary를 endpoint에 추가하기 쉽다.

단점:

- frontend-only 구현보다 파일이 조금 더 늘어난다.
- backend 실행 위치에 따라 artifact path를 안정적으로 잡아야 한다.

판단:

- 최종 선택이다.
- 포트폴리오 설득력과 아키텍처 일관성을 가장 잘 만족한다.

### B안. Frontend static data dashboard

흐름:

```txt
artifact values copied into frontend/src/data
-> /research renders local constants
```

장점:

- 빠르게 만들 수 있다.
- backend 변경이 없다.

단점:

- artifact와 UI가 drift될 수 있다.
- frontend가 보여주는 숫자가 실제 report artifact에서 왔다는 연결이 약하다.
- API/Zod 경계가 없어서 Sigak의 기존 frontend 패턴과 덜 맞다.

판단:

- 시간 압박이 극단적일 때만 fallback으로 쓴다.

### C안. Docs/report link page

흐름:

```txt
/research shows links to reports and experiment files
```

장점:

- 가장 작다.
- 구현 위험이 낮다.

단점:

- "대시보드"가 아니라 안내 페이지에 가깝다.
- 취업 포트폴리오에서 제품 완성도가 약하게 보인다.

판단:

- 이번 목표에는 부족하다.

## Selected Design

### Backend Boundary

새 API:

```txt
GET /api/research/overview
```

역할:

- `experiments/results/retrieval/latest/metrics.comparison.json`을 읽어 retrieval system comparison을 만든다.
- `experiments/results/graph/latest/graph-context.metrics.summary.json`을 읽어 graph-aware summary를 만든다.
- `experiments/results/graph/latest/graph-context.metrics.by-query.json`을 읽어 query-level graph evidence를 만든다.
- `experiments/datasets/raw/articles.catalog.api-ready-2026-06-05.json`을 읽어 expanded catalog 상태를 만든다.
- artifact가 없거나 JSON parsing이 실패하면 해당 section만 `available=false`로 내려준다.

응답 형태는 implementation에서 Kotlin DTO로 명시한다.
개념적 shape는 다음과 같다.

```json
{
  "generatedAt": "2026-06-11T00:00:00Z",
  "retrieval": {
    "available": true,
    "catalogId": "api-ready-2026-06-02",
    "catalogArticleCount": 6,
    "evaluatedQueryCount": 3,
    "k": 5,
    "generatedAt": "2026-06-02T15:06:29.546Z",
    "systems": [
      {
        "name": "hybrid",
        "macroTop1StrongHit": 0.6667,
        "macroRecallAtK": 0.8333,
        "macroMrrAtK": 0.8333,
        "averageLatencyMs": 15.0,
        "failedQueryCount": 0,
        "degradedQueryCount": 0
      }
    ],
    "warnings": []
  },
  "graph": {
    "available": true,
    "catalogId": "api-ready-2026-06-02",
    "evaluatedQueryCount": 3,
    "macroGraphContextCoverageAtK": 0.3333,
    "macroGraphReasonedCoverageAtK": 0.3333,
    "graphContextFailureRate": 0.0,
    "emptyContextRate": 0.0,
    "averageGraphLatencyMs": 33.53,
    "queryEvidence": []
  },
  "expandedCatalog": {
    "available": true,
    "catalogId": "api-ready-2026-06-05",
    "articleCount": 41,
    "labelStatus": "draft_only",
    "benchmarkStatus": "blocked_until_reviewed_labels"
  },
  "limitations": []
}
```

Backend component boundaries:

- `ResearchOverviewController`: HTTP endpoint만 담당한다.
- `ResearchOverviewService`: artifact를 읽고 dashboard DTO로 매핑한다.
- `ResearchArtifactProperties`: artifact root path를 설정한다.
- `ResearchArtifactReader`: 파일 읽기와 JSON parsing 실패를 section-level result로 바꾼다.
- DTO package: public API response shape를 명시한다.

기본 artifact root는 repository layout 기준 `../experiments`가 아니라 application working directory 차이를 고려해 설정값으로 둔다.
local default는 project root에서 backend를 실행하는 일반 흐름과 `cd backend` 실행 흐름을 모두 고려해야 한다.
implementation에서는 테스트 가능한 path resolution을 먼저 고정한다.

### Frontend Route

새 route:

```txt
/research
```

Frontend component boundaries:

- `src/api/research.ts`: Axios 호출, Zod schema, 타입 export
- `src/pages/ResearchPage.tsx`: page-level loading/error/empty state와 section composition
- `src/pages/ResearchPage.css`: dashboard layout and visual styling
- `NavBar.tsx`: `/research` link 추가

화면 section:

1. Overview band
   - "Research Evidence" title
   - compact subtitle: "Hybrid retrieval, graph context, and reproducible smoke metrics"
   - artifact scope chips: catalog, query count, generated date

2. Portfolio signal strip
   - `Hybrid Retrieval`
   - `Vector Search`
   - `Graph Context`
   - `Reproducible Metrics`
   - 각 signal은 claim이 아니라 현재 증명 가능한 system capability를 짧게 표시한다.

3. Retrieval comparison
   - system별 table
   - Recall@K, MRR@K, Top-1 Strong Hit, Avg latency, failure/degraded count
   - best observed metric은 subtle accent로 강조한다.

4. Graph-aware insight
   - coverage/failure/latency metric row
   - query-level table: query, search coverage, graph context coverage, reason coverage
   - graph가 도움을 준 경우와 아직 놓친 경우를 둘 다 보이게 한다.

5. Expanded catalog and limitation panel
   - 41-article expanded catalog 준비 상태
   - reviewed labels 부족으로 benchmark claim이 막혀 있음을 명확히 표시
   - next evidence step: reviewed labels 없이도 가능한 작업과 labels 이후 가능한 작업을 분리한다.

### Visual Direction

대시보드는 article reading UI보다 조금 더 analytical하게 보이되, 과장된 SaaS landing page처럼 보이면 안 된다.

시각 기준:

- 배경은 밝은 neutral 계열을 유지하고, section은 얇은 border와 subtle surface로 구분한다.
- dominant purple/blue gradient나 큰 hero illustration은 쓰지 않는다.
- 차트 라이브러리 없이도 metric hierarchy가 보이도록 숫자, label, table spacing을 정교하게 잡는다.
- status/warning은 숨기지 않고 amber/neutral tone으로 표시한다.
- cards는 반복 metric item에만 쓰고, page section 전체를 카드 안에 중첩하지 않는다.
- 모바일에서는 table이 깨지지 않도록 compact stacked rows 또는 horizontal overflow를 명시한다.
- headline은 포트폴리오 자신감이 느껴지게 쓰되, "best", "production-grade", "high quality" 같은 과장 표현은 피한다.

좋은 첫인상:

```txt
This project measures its AI retrieval behavior.
It knows what is verified.
It knows what is still smoke-only.
```

## Error Handling

Backend:

- artifact 파일이 없으면 해당 section을 unavailable로 반환한다.
- JSON schema가 예상과 다르면 해당 section warning에 parsing issue를 포함한다.
- 전체 API는 가능한 한 200을 반환하고 section-level availability로 degrade한다.
- 예외 stack trace나 local absolute path는 public response에 노출하지 않는다.

Frontend:

- API 호출 자체가 실패하면 dashboard-level error state와 retry button을 보여준다.
- section이 unavailable이면 그 section만 "Artifact unavailable" 상태로 보여준다.
- warning과 limitation은 숨기지 않는다.
- 숫자는 percent/decimal formatting helper를 통해 일관되게 표시한다.

## Testing

Backend:

- service test: retrieval comparison artifact를 DTO로 매핑한다.
- service test: graph summary/by-query artifact를 DTO로 매핑한다.
- service test: expanded catalog article count를 `articles.length`로 계산한다.
- service test: missing artifact는 section unavailable로 degrade한다.
- controller test: `GET /api/research/overview` response shape를 확인한다.

Frontend:

- API client test: valid overview response를 Zod로 통과시킨다.
- API client test: invalid response를 거부한다.
- page test: retrieval systems와 graph limitations를 렌더링한다.
- page test: unavailable section을 깨지지 않고 렌더링한다.
- routing/nav test: NavBar에서 Research link를 통해 route가 노출된다.

Verification gate:

```bash
cd backend && ./gradlew test && ./gradlew check
cd frontend && npm test && npm run lint && npm run build
```

## Acceptance Criteria

- `/research`가 frontend route로 접근 가능하다.
- `/api/research/overview`가 실제 committed experiment artifact에서 값을 읽는다.
- retrieval comparison table에 keyword/vector/hybrid/public 시스템이 보인다.
- graph-aware section에 summary metric과 query-level evidence가 보인다.
- expanded catalog가 41 articles로 표시되며, reviewed labels 전에는 benchmark claim이 제한된다는 문구가 보인다.
- artifact missing/invalid 상태에서도 page 전체가 깨지지 않는다.
- UI는 article reading flow와 구분되는 research dashboard 톤을 가진다.
- 테스트와 build/lint/check가 통과한다.
- `docs/STATUS.md`, `docs/ROADMAP.md`, dev-log, topic queue가 구현 완료 후 업데이트된다.

## Open Constraints Fixed By This Spec

- 데이터 출처는 frontend static constant가 아니라 Spring Boot API다.
- 이번 작업은 dashboard를 만드는 것이며 새 benchmark runner를 만들지 않는다.
- "예쁜 화면"은 포트폴리오 신뢰도를 높이는 방식으로 적용한다: 정직한 지표, 좋은 타이포그래피, 명확한 위계, 과장 없는 강조.
- smoke artifact의 limitation은 UI에서 숨기지 않는다.
