# Graph-Aware Evaluation Design

날짜: 2026-06-03
개정: 2026-06-04

## 목표

Sigak은 이제 public article detail에서 Neo4j graph context를 이용해 related article reason을 보여줄 수 있다.
다음 목표는 이 graph-aware context가 단순한 related article ID 목록이나 검색 결과보다 어떤 설명 가치를 주는지 작게라도 재현 가능하게 평가하는 것이다.

이번 설계는 A안을 선택한다.

```txt
기존 label JSON
-> public search baseline run
-> public article detail + graph-context 수집
-> graph-aware context metric
-> report artifact
```

핵심은 "그래프를 붙였다"에서 멈추지 않고, 다음 질문에 답할 수 있는 artifact를 남기는 것이다.

- Graph context가 관련 article을 얼마나 잘 포함하는가?
- 관련 article이 있을 때 reason도 함께 제공되는가?
- shared topic이나 topic context가 실제 설명 근거로 남는가?
- 검색 단계에서 positive article을 놓친 경우와 graph context 자체의 한계를 구분할 수 있는가?
- Neo4j projection이 없거나 실패해도 public detail 경험이 깨지지 않는가?
- 현재 label/catalog 크기에서 어디까지 말할 수 있고, 어디부터는 아직 말하면 안 되는가?

## 비목표

- full GraphRAG chatbot을 만들지 않는다.
- Neo4j graph context로 public search ranking을 바꾸지 않는다.
- graph explorer UI를 만들지 않는다.
- 새 `/research` dashboard를 이번 범위에 만들지 않는다.
- LLM judge, RAGAS, nDCG, 통계 검정, 반복 latency p95 측정을 이번 범위에 넣지 않는다.
- 사람 라벨링 UI를 graph 평가용으로 크게 개편하지 않는다.
- graph relation 품질을 자동으로 정답 판정하지 않는다.
- 현재 3-query/6-article smoke label set으로 검색 품질이나 graph 품질 결론을 단정하지 않는다.

## 현재 상태

이미 준비된 것:

- `experiments/scripts/retrieval-benchmark.mjs`
- retrieval benchmark label JSON
- public search benchmark artifact
- keyword/vector/strict-hybrid/public system comparison runner
- `GET /api/articles?query=...`
- `GET /api/articles/{id}`
- `GET /api/articles/{id}/graph-context`
- `POST /api/internal/graph-projections/articles/rebuild`
- Neo4j article/topic/relation projection
- public graph-context fallback 정책

현재 label set:

- `experiments/datasets/labels/search-labels.api-ready-2026-06-02.2026-06-02.json`
- 3 reviewed queries
- 6 catalog articles
- 이 규모는 통계적 품질 주장을 위한 dataset이 아니라 runner, artifact, report 흐름을 확인하기 위한 smoke dataset이다.

현재 graph context 응답은 다음 정보를 가진다.

```json
{
  "articleId": 4,
  "relatedArticleReasons": [
    {
      "articleId": 1,
      "reason": "Graph RAG evaluation connects to agent and retrieval evaluation.",
      "sharedTopics": []
    }
  ],
  "topics": [
    {
      "name": "graph rag",
      "displayName": "Graph RAG",
      "relatedArticleIds": []
    }
  ]
}
```

Public graph-context endpoint는 timings, relation type, Neo4j 내부 오류를 노출하지 않는다.
따라서 graph-aware evaluation runner도 public-safe 응답만 사용한다.

## Dataset Scale And Interpretation Gates

현재 3-query/6-article label set은 graph-aware evaluation 구현과 smoke 검증에는 충분하지만, 품질 결론을 내리기에는 너무 작다.
따라서 report는 dataset 규모에 따라 해석 수준을 분리한다.

| 규모 | 해석 수준 | Report 표현 |
| --- | --- | --- |
| 10 reviewed queries 미만 또는 20 articles 미만 | Smoke only | "평가 pipeline이 동작한다"까지만 말한다. |
| 10-15 reviewed queries와 20+ articles | MVP evidence | 제한된 query set에서 baseline과 graph context 차이를 관찰한다. |
| 30+ reviewed queries와 50+ articles | Portfolio comparison | query type별 경향을 조심스럽게 설명할 수 있다. |

이번 구현의 성공 기준은 첫 번째 단계다.
즉, 작은 dataset warning을 정확히 표시하고, 결과를 과장하지 않는 artifact를 만드는 것이 목표다.
더 많은 query/article 확보는 graph-aware evaluation 구현 이후 별도 작업으로 이어간다.

## 문제

Article detail에 relation reason을 표시하는 것만으로는 다음을 알 수 없다.

1. Graph context가 사람이 라벨링한 관련 article을 실제로 포함하는지.
2. Related article ID baseline과 graph context가 어떻게 다른지.
3. Graph reason이 비어 있거나 projection이 stale인 경우가 얼마나 생기는지.
4. Graph context가 추가 호출로서 어느 정도 latency를 가지는지.
5. 검색 결과와 detail graph context를 연결해 설명 가능한 흐름을 만들 수 있는지.
6. Positive article이 public search top-k에서 빠졌기 때문에 graph context를 확인할 기회 자체가 줄어든 경우를 구분할 수 있는지.
7. Reason/topic이 seed/mock 기반 저장값인지, 실제 relation extraction에서 나온 값인지 report가 명확히 설명하는지.
8. Neo4j projection이나 relation extraction이 바뀌었을 때 이전 run과 현재 run을 비교할 수 있는 최소 metadata가 남는지.

반대로 너무 큰 평가 체계를 바로 만들면 다음 문제가 생긴다.

- label set이 작아 수치가 과장된다.
- graph relation extraction 품질이 아직 seed/mock 기반이라 자동 평가가 부정확해진다.
- 백엔드 internal endpoint, 새 DTO, dashboard까지 한 번에 늘어나 MVP 범위가 흔들린다.

따라서 이번 단계는 "작고 반복 가능한 graph-aware smoke evaluation"에 집중한다.

## 접근안 비교

### A안. 기존 Node benchmark runner 확장

흐름:

```txt
label JSON
-> public search run
-> top-k result article detail 조회
-> 각 article의 public graph-context 조회
-> graph context metric 계산
-> graph report artifact 저장
```

장점:

- public API shape를 바꾸지 않는다.
- 백엔드 코드 변경 없이 시작할 수 있다.
- 기존 label JSON, report writer, metric 구조를 재사용할 수 있다.
- graph context가 사용자에게 실제로 노출되는 public-safe 응답만 평가한다.
- 작은 catalog에서도 smoke artifact를 만들 수 있다.

단점:

- backend internal timing을 볼 수 없다.
- graph context의 relation type 같은 internal field를 비교하지 않는다.
- public API round-trip latency라서 순수 Neo4j query latency와는 다르다.

판단:

- 이번 작업의 최종 선택이다.
- MVP 단계에서 가장 작고 재현 가능한 방식이다.

### B안. Backend internal graph evaluation endpoint 추가

흐름:

```txt
POST /api/internal/graph-evaluation/context-runs
-> backend가 article/detail/context를 직접 조회
-> internal timings와 relation metadata 포함
```

장점:

- internal timing과 projection 상태를 더 정확히 볼 수 있다.
- Neo4j query 실패 원인을 세밀하게 기록할 수 있다.

단점:

- internal API, DTO, service가 추가된다.
- 현재 public graph-context endpoint가 이미 있으므로 중복 경계가 생긴다.
- 평가 목표보다 구현 범위가 커진다.

판단:

- 나중에 graph evaluation이 커지면 검토한다.
- 지금은 과하다.

### C안. 수동 dev-log/report만 작성

흐름:

```txt
수동으로 graph-context API 호출
-> 결과를 dev-log에 기록
```

장점:

- 가장 빠르다.
- 코드 변경이 없다.

단점:

- 반복 실행이 어렵다.
- artifact가 부족해서 포트폴리오나 기술 블로그에서 설득력이 약하다.
- baseline 비교가 사람 기억에 의존한다.

판단:

- 보조 기록으로는 좋지만 evaluation으로는 부족하다.

## 최종 결정

A안을 선택한다.

기존 retrieval benchmark runner를 확장해 graph-aware artifact를 만든다.
이때 graph evaluation은 search ranking을 새로 만드는 기능이 아니다.
검색 결과 또는 label의 positive article을 기준으로, article detail에서 얻을 수 있는 graph context가 얼마나 설명 가능한 보조 정보를 제공하는지 측정한다.

## 평가 질문

이번 평가가 답해야 하는 질문은 여섯 가지다.

### Q1. Graph context가 관련 article을 포함하는가?

정답 label에서 `strong` 또는 `acceptable`로 표시된 article들을 positive article로 본다.
각 query의 public search top-k 결과에 대해 article detail과 graph context를 조회하고, graph context의 `relatedArticleReasons.articleId`가 positive article을 얼마나 포함하는지 계산한다.
여러 source article에서 같은 positive article이 반복될 수 있으므로 coverage는 distinct article ID 기준으로 계산한다.

단, source article 자기 자신은 graph context related hit에서 제외한다.

Coverage는 두 층으로 나눈다.

- `graphContextCoverageAtK`: public search top-k에서 출발한 end-to-end 사용자 흐름 기준 coverage다.
- `graphContextCoverageAmongSearchHits`: public search top-k에 포함된 positive article만 놓고 본 graph context 보조 설명 coverage다.

두 값을 분리하는 이유는 graph context가 나빠서 설명하지 못한 경우와, 검색 단계에서 relevant article을 먼저 놓쳐서 상세 화면으로 진입할 기회가 줄어든 경우를 구분하기 위해서다.

### Q2. Graph context가 reason을 제공하는가?

Related article이 graph context에 포함되어도 reason이 비어 있으면 설명 가치는 낮다.
따라서 related hit 중 `reason`이 non-blank인 비율을 별도 metric으로 계산한다.

### Q3. Topic context가 설명 근거를 남기는가?

Graph context가 직접 related article을 맞추지 못하더라도, `topics` 또는 `sharedTopics`가 있으면 사용자가 관련 개념을 이해하는 데 도움이 될 수 있다.
따라서 topic context가 있는 article 비율을 별도 metric으로 기록한다.

### Q4. Graph context가 실패해도 public 경험이 유지되는가?

Neo4j projection이 비어 있거나 Neo4j가 꺼져도 public endpoint는 empty context로 degrade한다.
Runner는 graph context 호출 실패와 empty context를 구분해서 기록한다.

- HTTP 실패: `status = FAILED`
- 200 empty context: `status = COMPLETED`, `emptyContext = true`

### Q5. Reason/topic의 출처와 품질 한계가 드러나는가?

현재 graph context의 reason/topic은 Neo4j가 새로 추론한 결과가 아니다.
PostgreSQL에 저장된 article relation과 topic metadata를 Neo4j projection에 투영한 뒤 public-safe하게 읽은 값이다.
현재 seed/mock 기반 데이터에서는 reason이 "검증된 관계 설명"이라기보다 "저장된 relation reason"에 가깝다.

따라서 report에는 reason/topic 품질을 자동으로 사실 판정하지 않는다고 명시한다.
추후 real relation extraction이 들어오면 run metadata에 extraction mode, prompt/model version, accepted/rejected 예시를 남기는 방식으로 확장한다.

### Q6. Projection 변경과 실험 history를 추적할 수 있는가?

Neo4j projection schema, relation extraction 방식, seed/catalog가 바뀌면 graph-aware evaluation 결과도 달라진다.
Runner는 최소한 다음 metadata를 artifact에 남긴다.

- `generatedAt`
- `labelsPath`
- `labelsSha256`
- `catalogId`
- `catalogArticleCount`
- `baseUrl`
- `k`
- `sourceArticleSelection`
- `graphContextEndpoint`
- `reasonSource`

이 metadata는 완전한 experiment tracking system은 아니지만, smoke run이 어떤 입력과 전제에서 만들어졌는지 설명하는 최소 근거다.

## Metric Design

### Query-level metrics

각 query마다 다음 값을 계산한다.

```json
{
  "query": "graph rag failure",
  "evaluatedArticleCount": 5,
  "positiveArticleIds": [4, 1],
  "searchRankedArticleIds": [4, 2, 1],
  "searchPositiveHitIds": [4, 1],
  "searchMissedPositiveIds": [],
  "relatedBaselineHitCount": 1,
  "graphRelatedHitCount": 1,
  "graphSearchHitRelatedHitCount": 1,
  "graphReasonedHitCount": 1,
  "searchPositiveCoverageAtK": 1,
  "relatedBaselineCoverageAtK": 0.5,
  "graphContextCoverageAtK": 0.5,
  "graphContextCoverageAmongSearchHits": 0.5,
  "graphReasonedCoverageAtK": 0.5,
  "reasonCoverage": 1,
  "topicContextCoverage": 1,
  "emptyContextCount": 0,
  "failedContextCount": 0,
  "averageGraphLatencyMs": 18
}
```

정의:

- `evaluatedArticleCount`: graph context를 조회한 source article 수
- `positiveArticleIds`: label 기준 `strong`, `acceptable` article IDs
- `searchRankedArticleIds`: public search top-k article IDs
- `searchPositiveHitIds`: `searchRankedArticleIds`에 포함된 positive article IDs
- `searchMissedPositiveIds`: public search top-k에 포함되지 않은 positive article IDs
- `relatedBaselineHitCount`: source article들의 `relatedArticleIds` 중 positive article에 해당하는 distinct 개수
- `graphRelatedHitCount`: source article들의 graph related IDs 중 positive article에 해당하는 distinct 개수
- `graphSearchHitRelatedHitCount`: graph related hit 중 `searchPositiveHitIds`에도 포함되는 distinct 개수
- `graphReasonedHitCount`: graph related hit 중 non-blank reason을 가진 distinct positive article 개수
- `searchPositiveCoverageAtK`: `searchPositiveHitIds.length / positiveArticleIds.length`
- `relatedBaselineCoverageAtK`: `relatedBaselineHitCount / positiveArticleIds.length`
- `graphContextCoverageAtK`: `graphRelatedHitCount / positiveArticleIds.length`
- `graphContextCoverageAmongSearchHits`: `graphSearchHitRelatedHitCount / searchPositiveHitIds.length`
- `graphReasonedCoverageAtK`: `graphReasonedHitCount / positiveArticleIds.length`
- `reasonCoverage`: graph related hit 중 non-blank reason을 가진 비율
- `topicContextCoverage`: 조회한 graph context 중 topics 또는 sharedTopics가 하나 이상 있는 비율
- `emptyContextCount`: 200 응답이지만 related reason과 topic이 모두 없는 context 수
- `failedContextCount`: HTTP error, schema error, network error로 context 조회가 실패한 수
- `averageGraphLatencyMs`: public graph-context API round-trip 평균 latency

주의:

- coverage denominator가 0인 경우 metric은 `null`로 둔다.
- `graphContextCoverageAmongSearchHits`에서 `searchPositiveHitIds.length`가 0이면 `null`로 둔다.
- `searchMissedPositiveIds`가 있으면 report에서 "검색 단계에서 놓친 positive article은 detail graph context로 설명할 기회가 제한된다"는 원인 해석을 추가한다.

### Latency metrics

이번 범위의 latency는 public graph-context API round-trip이다.
즉, 다음을 포함한다.

- HTTP client round-trip
- Spring Boot request handling
- PostgreSQL public article existence check
- Neo4j context lookup
- DTO mapping

따라서 `averageGraphLatencyMs`는 Neo4j query latency와 같지 않다.
Neo4j query만의 latency가 필요해지는 시점에는 internal graph endpoint 또는 backend-side timing artifact를 별도 설계한다.

### Summary metrics

전체 report에는 query 평균과 실패 비율을 분리해서 기록한다.

```json
{
  "evaluatedQueryCount": 3,
  "catalogArticleCount": 6,
  "k": 5,
  "macroSearchPositiveCoverageAtK": 0.83,
  "macroRelatedBaselineCoverageAtK": 0.5,
  "macroGraphContextCoverageAtK": 0.5,
  "macroGraphContextCoverageAmongSearchHits": 0.6,
  "macroGraphReasonedCoverageAtK": 0.5,
  "macroReasonCoverage": 1,
  "macroTopicContextCoverage": 0.83,
  "graphContextFailureRate": 0,
  "emptyContextRate": 0.2,
  "averageGraphLatencyMs": 21,
  "warnings": [
    "label set is smaller than 10 reviewed queries, so this is a smoke graph evaluation",
    "catalog is smaller than 20 articles, so graph density is too small for quality claims",
    "graph latency is measured as public API round-trip, not pure Neo4j query latency",
    "graph reasons are stored projection reasons, not independently verified factual explanations"
  ]
}
```

해석 원칙:

- coverage 수치는 graph 품질 결론이 아니라 smoke evidence다.
- failure/empty context 비율은 quality metric과 분리한다.
- latency는 public API round-trip 기준이다.
- related baseline은 article ID 연결만 평가하고, graph context는 reason이 있는 연결을 별도 평가한다.
- label set이 10 reviewed queries 미만이면 report에 warning을 넣는다.
- catalog article 수가 20개 미만이면 graph density 부족 warning을 넣는다.
- search miss가 있으면 graph context 품질과 검색 단계 실패를 분리해서 해석한다.
- reason/topic은 현재 저장된 projection metadata의 품질을 반영한다. 사람 또는 모델이 별도 검증한 factual explanation으로 해석하지 않는다.

## Baseline Comparison

Graph-aware evaluation은 단독 점수보다 baseline 비교가 중요하다.

이번 범위의 baseline:

1. **Public search baseline**
   - query에 대한 public search top-k result
   - 기존 `Top1 Strong Hit`, `Recall@5`, `MRR@5`, latency 사용
2. **Related article baseline**
   - `GET /api/articles/{id}`의 `relatedArticleIds`
   - reason 없이 article ID만 제공하는 baseline
3. **Graph context**
   - `GET /api/articles/{id}/graph-context`
   - related article reason, shared topics, topics 제공

중요한 구분:

- public search는 query-to-article retrieval 평가다.
- related article baseline과 graph context는 article-to-article explanation 평가다.
- 따라서 graph-aware evaluation report는 "검색 성능을 올렸다"가 아니라 "상세 화면에서 설명 가능한 관련 맥락을 얼마나 제공했다"로 해석한다.

## Runner Design

기존 runner에 graph option을 추가한다.

예상 명령:

```bash
node experiments/scripts/retrieval-benchmark.mjs \
  --labels=experiments/datasets/labels/search-labels.api-ready-2026-06-02.2026-06-02.json \
  --base-url=http://localhost:8080 \
  --output-dir=experiments/results/graph/latest \
  --systems=public \
  --include-graph-context \
  --k=5 \
  --limit=20
```

설계 원칙:

- `--include-graph-context`가 없으면 기존 retrieval benchmark 동작을 바꾸지 않는다.
- graph evaluation은 public API 결과 위에 붙는 optional artifact다.
- `PUBLIC`은 여전히 기존 `/api/articles?query=...`를 호출한다.
- graph context는 public endpoint `/api/articles/{id}/graph-context`만 호출한다.
- internal graph endpoint는 runner에서 호출하지 않는다.
- CLI option 이름은 `--include-graph-context`로 고정한다.

## Artifact Design

Graph-aware run은 별도 output directory를 권장한다.

```txt
experiments/results/graph/latest/
├── graph-context.runs.json
├── graph-context.metrics.by-query.json
├── graph-context.metrics.summary.json
└── report.md
```

### `graph-context.runs.json`

```json
{
  "labelsPath": "experiments/datasets/labels/search-labels.api-ready-2026-06-02.2026-06-02.json",
  "labelsSha256": "sha256-of-label-file",
  "catalogId": "api-ready-2026-06-02",
  "catalogArticleCount": 6,
  "baseUrl": "http://localhost:8080",
  "k": 5,
  "generatedAt": "2026-06-03T12:00:00Z",
  "sourceArticleSelection": "public_search_top_k",
  "graphContextEndpoint": "/api/articles/{id}/graph-context",
  "reasonSource": "stored_projection_reason",
  "queries": [
    {
      "query": "graph rag failure",
      "positiveArticleIds": [4, 1],
      "searchRankedArticleIds": [4, 2, 1],
      "searchPositiveHitIds": [4, 1],
      "searchMissedPositiveIds": [],
      "sourceArticleIds": [4, 1, 2],
      "contexts": [
        {
          "articleId": 4,
          "status": "COMPLETED",
          "latencyMs": 18,
          "relatedArticleReasons": [
            {
              "articleId": 1,
              "reason": "Graph RAG evaluation connects to agent and retrieval evaluation.",
              "sharedTopics": []
            }
          ],
          "topics": ["Graph RAG", "retrieval quality"]
        }
      ]
    }
  ]
}
```

### `graph-context.metrics.by-query.json`

Query-level metrics를 배열로 저장한다.

### `graph-context.metrics.summary.json`

Summary metrics와 warning을 저장한다.

### `report.md`

사람이 읽는 report는 다음을 포함한다.

- label/catalog 크기
- labels checksum과 catalog ID
- 어떤 API를 호출했는지
- graph context coverage summary
- search miss가 graph context 평가에 미치는 영향
- reason/topic coverage
- reason/topic 품질 한계
- failure/empty context rate
- public round-trip latency와 Neo4j query latency의 차이
- 작은 sample에 대한 warning
- 검색 metric과 graph context metric의 해석 차이
- 개선 후보

## Data Flow

```txt
labels JSON
-> load reviewed queries
-> run public search for each query
-> choose source articles from public top-k
-> fetch each article detail for relatedArticleIds baseline
-> fetch each article public graph-context
-> calculate graph context metrics
-> write graph artifacts
-> write markdown report
```

Source article 선택:

- 기본값은 public search top-k article이다.
- label positive article이 public search top-k에 없으면 graph context를 조회할 source article 후보에서 빠질 수 있다.
- 이 경우 report에 "검색 단계에서 놓친 positive article은 detail graph context로 설명할 기회가 없었다"는 해석을 남긴다.
- 단, positive article이 source article로 선택되지 않아도 다른 source article의 graph context에서 related article로 발견될 수 있다. 이 경우 `graphRelatedHitCount`에는 포함하지만 `searchPositiveHitIds`에는 포함하지 않는다.

이 선택은 사용자 흐름과 맞다.
사용자는 검색 결과에서 article detail로 들어가기 때문에, graph-aware detail은 public search가 노출한 article을 기준으로 평가해야 한다.

## Error And Fallback Policy

### Public search 실패

해당 query는 graph evaluation도 실행하지 않는다.
report에는 `searchFailedQueryCount`를 기록한다.

### Article detail 실패

해당 source article의 related article baseline은 실패로 기록한다.
Graph context 조회는 article detail이 public resource로 확인되지 않았으므로 실행하지 않는다.

### Graph context HTTP 실패

Context row를 다음처럼 기록한다.

```json
{
  "articleId": 4,
  "status": "FAILED",
  "failureReason": "Graph context API failed with status 500.",
  "latencyMs": 12,
  "relatedArticleReasons": [],
  "topics": []
}
```

### Graph context empty fallback

Public endpoint가 200으로 empty context를 반환하면 실패가 아니다.

```json
{
  "articleId": 4,
  "status": "COMPLETED",
  "emptyContext": true,
  "relatedArticleReasons": [],
  "topics": []
}
```

이 경우 사용자는 상세 본문을 볼 수 있지만, graph-aware explanation은 제공되지 않은 것으로 해석한다.

## Security And Exposure

- Runner는 public graph-context endpoint만 호출한다.
- Internal graph endpoint의 timings, relation type, Neo4j diagnostics는 artifact에 저장하지 않는다.
- Artifact에는 API key, request header, environment variable 값을 저장하지 않는다.
- `baseUrl`, labels path, article IDs, public-safe reasons/topics만 저장한다.
- Public graph-context endpoint는 이미 PostgreSQL source-of-truth 기준 public article 존재를 확인한다.

## Run History And Change Tracking

Graph-aware evaluation 결과는 projection과 입력 dataset에 민감하다.
따라서 report는 이전 run과 비교 가능한 최소 metadata를 남겨야 한다.

저장할 metadata:

- label file checksum
- catalog ID와 article count
- source article selection policy
- graph context endpoint path
- reason source
- generated timestamp
- runner version 또는 현재 git commit을 읽을 수 있으면 git commit

주의:

- 현재 단계에서는 별도 experiment database를 만들지 않는다.
- `experiments/results/graph/latest`는 가장 최근 smoke run 용도다.
- 중요한 run을 보존하려면 `experiments/results/graph/YYYY-MM-DD-<short-note>` 같은 directory를 사용한다.
- Neo4j projection rebuild 시점은 public graph-context endpoint에서 직접 알 수 없으므로, smoke 절차와 dev-log에 rebuild command와 observed count를 함께 기록한다.

## Testing Strategy

Node test 중심으로 검증한다.

필요한 테스트:

- CLI가 `--include-graph-context` option을 parsing한다.
- Graph client가 `/api/articles/{id}`와 `/api/articles/{id}/graph-context`를 호출한다.
- Invalid graph context response는 실패 row로 기록된다.
- Empty graph context는 failure가 아니라 `emptyContext = true`로 기록된다.
- Query-level graph metrics가 positive article, reason, topic coverage를 계산한다.
- Search top-k에서 빠진 positive article이 `searchMissedPositiveIds`로 기록된다.
- Related article baseline coverage와 graph reasoned coverage가 distinct positive article 기준으로 계산된다.
- `graphContextCoverageAmongSearchHits`는 search hit denominator가 0이면 `null`이 된다.
- Summary metrics가 작은 label/catalog warning을 만든다.
- Summary/report가 reason source와 public round-trip latency 한계를 명시한다.
- Report writer가 graph metric과 search metric의 해석 차이를 문서화한다.
- 기존 retrieval benchmark tests가 graph option 없이 그대로 통과한다.

Smoke 검증:

```txt
compose up postgres/elasticsearch/qdrant/neo4j/ai
-> backend bootRun
-> rebuild Elasticsearch/Qdrant/Neo4j projections
-> run graph-aware evaluation runner
-> inspect graph-context report
```

실제 observed 값은 dev-log와 `docs/STATUS.md`에 기록한다.

## Documentation Plan

구현 후 업데이트할 문서:

- `experiments/README.md`
  - graph-aware evaluation command
  - artifact 설명
  - smoke result 해석
  - dataset 규모별 해석 기준
- `docs/STATUS.md` / `docs/STATUS.ko.md`
  - S5 graph-aware evaluation 진행 상태
  - 실제 검증 값
- `docs/ROADMAP.md` / `docs/ROADMAP.ko.md`
  - S5 마지막 항목 완료 여부
- `docs/blog/YYYY-MM-DD-dev-log.md`
  - graph context를 baseline과 비교하려 한 이유
  - 작은 label set의 한계
  - search miss와 graph explanation miss를 분리한 이유
- `docs/blog/topic-queue.md`
  - "Graph-aware detail은 검색 성능이 아니라 설명 가능성 평가로 봐야 한다" 주제 후보

## 성공 기준

이번 구현 단위가 끝나면 다음이 가능해야 한다.

- 같은 label JSON으로 public search와 graph context artifact를 생성한다.
- Report가 graph context coverage, reason coverage, topic coverage, failure/empty context rate를 보여준다.
- Report가 search miss와 graph context 한계를 분리해서 보여준다.
- Report가 reason/topic 출처와 latency 측정 한계를 명시한다.
- Artifact가 labels checksum, catalog ID, reason source 같은 최소 history metadata를 저장한다.
- 작은 label/catalog warning이 자동으로 표시된다.
- Neo4j가 꺼진 상황에서는 empty context 또는 failure가 metric으로 분리된다.
- 기존 retrieval benchmark runner 동작은 graph option 없이 유지된다.

## 후속 작업

이번 graph-aware smoke evaluation이 끝난 뒤에만 다음을 검토한다.

- label query를 10-15개로 확장
- catalog article을 20개 이상으로 확장
- graph context에 relation quality score 추가
- internal graph evaluation endpoint 추가
- backend-side Neo4j query latency 측정
- `/research` dashboard에서 graph artifact 읽기
- graph-aware retrieval 또는 reranking 실험

## 설계 자기 검토

- Placeholder: 문서에 미완성 표시나 확정되지 않은 요구사항을 남기지 않았다.
- 일관성: public graph-context endpoint만 사용하며, internal endpoint를 runner에서 호출하지 않는 원칙을 유지했다. Latency도 public round-trip으로 해석한다고 명시했다.
- 범위: 백엔드 API 확장, dashboard, LLM judge, full GraphRAG를 제외해 단일 implementation plan으로 실행 가능한 크기로 제한했다.
- 모호성: graph metric은 검색 품질 metric이 아니라 article detail explanation metric으로 해석한다고 명시했다. Search miss, reason/topic 품질, history metadata를 별도 항목으로 분리했다.
