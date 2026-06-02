# Retrieval Benchmark System Comparison Design

## Summary

Sigak에는 첫 retrieval benchmark smoke runner가 생겼다.
현재 runner는 label JSON을 읽고 public article search API의 현재 검색 결과를 평가한다.
이 구조는 `qrels -> run -> metrics -> report` 흐름을 검증하는 데는 충분하지만, keyword, vector, hybrid 검색 품질을 공정하게 비교하기에는 부족하다.

이번 설계의 목표는 public article API를 흔들지 않으면서, 내부 실험용 경계에서 같은 query set에 대해 `KEYWORD`, `VECTOR`, strict `HYBRID` run을 생성하고 같은 metric으로 비교하는 것이다.
추가로 runner는 기존 public API 결과를 `PUBLIC` run으로 따로 기록해, 순수 retrieval system 품질과 사용자가 실제로 보게 되는 degrade 포함 경험을 분리해서 볼 수 있게 한다.

## Current State

- `GET /api/articles?query=...`는 public 사용자 API다.
- Public search는 Elasticsearch keyword candidates, Qdrant vector candidates, RRF hybrid search를 사용한다.
- Public search는 장애 상황에서 `KEYWORD_ONLY`, `VECTOR_ONLY`, `POSTGRES_FALLBACK`으로 degrade할 수 있다.
- `experiments/scripts/retrieval-benchmark.mjs`는 label JSON을 읽어 current public search result를 평가한다.
- 현재 smoke artifact는 3 reviewed query, 6 catalog articles 기준이다.
- PostgreSQL은 source of truth이고, Elasticsearch/Qdrant/Neo4j는 rebuildable projection store다.

## Problem

현재 runner가 public API만 호출하면 아래 문제가 생긴다.

1. Public API는 장애 시 degrade한다.
   - 예를 들어 Qdrant가 실패하면 public result는 `KEYWORD_ONLY`가 될 수 있다.
   - 그런데 report에는 `hybrid`로 기록되면 실제 hybrid 품질과 fallback 품질이 섞인다.
2. Keyword, vector, strict hybrid를 같은 query와 같은 limit으로 비교할 수 없다.
3. Public response shape에는 score, candidate count, system별 failure reason이 없다.
   - public API에 이 정보를 추가하면 사용자 API가 실험 요구에 끌려간다.
4. Runner가 backend 설정을 바꾸거나 재시작해서 mode를 비교하면 느리고 불안정하다.
5. 반대로 strict hybrid만 보면 실제 사용자가 보게 되는 degrade 포함 public experience를 놓친다.

따라서 strict retrieval system 비교용 run은 internal evaluation boundary에서 만들고, public experience run은 기존 public API를 별도 system으로 기록한다.

## Design Goal

목표:

- 같은 label JSON과 query set으로 `KEYWORD`, `VECTOR`, strict `HYBRID` run을 생성한다.
- 기존 public article search result를 `PUBLIC` run으로 별도 기록한다.
- 각 system의 ranked article IDs, stale candidate count, failure reason, latency breakdown을 저장한다.
- 기존 metric(`Top1 Strong Hit`, `Recall@k`, `MRR@k`, `LatencyMs`)을 system별로 계산한다.
- 실패율, degrade 비율, failure reason count를 quality metric과 분리해서 기록한다.
- system별 summary와 comparison report를 생성한다.
- public `GET /api/articles` response shape를 바꾸지 않는다.

비목표:

- public API에 `mode` query parameter를 추가하지 않는다.
- 검색 score를 public article response에 노출하지 않는다.
- nDCG, MAP, 통계 검정, p50/p95 반복 측정을 이번 단계에 추가하지 않는다.
- Neo4j graph-aware retrieval 평가는 이번 단계에 포함하지 않는다.
- DB-backed evaluation UI나 `/research` dashboard는 이번 단계에 포함하지 않는다.

## Approach Review

### Option A: Restart backend with different search mode

Flow:

```text
SIGAK_SEARCH_MODE=keyword ./gradlew bootRun -> benchmark
SIGAK_SEARCH_MODE=hybrid ./gradlew bootRun -> benchmark
```

장점:

- backend code change가 적다.
- public API 기준 결과를 그대로 볼 수 있다.

단점:

- vector-only mode가 현재 설정 계약에 없다.
- backend 재시작과 projection 상태에 따라 실행 시간이 길어진다.
- fallback/degrade와 system comparison이 섞인다.
- 실험 자동화가 불편하다.

판단:

- 공정 비교용으로는 부적절하다.

### Option B: Internal evaluation run endpoint

Flow:

```text
label JSON
-> runner
-> POST /api/internal/search-evaluation/retrieval-runs for KEYWORD/VECTOR/HYBRID
-> GET /api/articles?query=... for PUBLIC
-> system별 run artifact
-> system별 metrics/report
```

장점:

- public API shape를 유지한다.
- 같은 query, 같은 projection state, 같은 limit에서 여러 system을 비교한다.
- system별 실패를 report에 명시할 수 있다.
- backend 내부 검색 경계를 재사용하므로 실제 서비스와 동떨어진 실험이 되지 않는다.
- 기존 public API run을 함께 저장하면 strict system 품질과 사용자 경험을 나란히 볼 수 있다.

단점:

- backend에 internal controller/service/DTO가 추가된다.
- `ArticlePublicSearchService`의 private candidate search 흐름을 일부 추출해야 한다.

판단:

- 현재 MVP에는 이 방법이 가장 적절하다.
- 기능은 internal-only로 제한하고, 제품 UI에는 노출하지 않는다.

### Option C: Kotlin command runner for full benchmark

Flow:

```text
cd backend
./gradlew bootRun --args='retrieval-benchmark ...'
```

장점:

- backend service를 직접 호출할 수 있다.
- Kotlin test로 business logic 검증이 쉽다.

단점:

- 실험 artifact 생성이 backend에 깊게 들어간다.
- label/report 파일 작업이 제품 backend 책임처럼 보일 수 있다.
- 현재 Node experiment runner와 역할이 겹친다.

판단:

- 나중에 benchmark가 커지면 검토할 수 있지만, 지금은 과하다.

## Recommended Design

Option B를 채택한다.
다만 구현 범위가 커지는 것을 막기 위해 public degrade 포함 run은 backend endpoint에 새 system으로 넣지 않고, runner가 기존 public API를 호출해 `PUBLIC` system artifact로 기록한다.

핵심 원칙:

- Public API는 사용자 경험을 위한 fallback/degrade를 유지한다.
- Internal evaluation API는 strict 검색 system 비교를 위한 run 생성만 담당한다.
- Evaluation의 `HYBRID`는 public fallback mode가 아니라 keyword와 vector가 모두 성공했을 때의 RRF fused result다.
- `PUBLIC`은 기존 public article API 결과를 그대로 기록하는 별도 system이다.
- strict system이 실패하면 다른 system 결과로 대체하지 않고 `FAILED`로 기록한다.
- stale projection candidate는 PostgreSQL API-ready reload 단계에서 제거하고 `staleCandidateCount`로 기록한다.

## Incremental Scope

To avoid overengineering, implement this in small stages.

### Stage 1: fair comparison run

Required:

- backend internal endpoint for strict `KEYWORD`, `VECTOR`, `HYBRID`
- runner support for `--systems=keyword,vector,hybrid,public`
- per-system run artifacts
- quality metrics plus failure/degrade metrics
- Markdown report

Not included:

- dashboard UI
- statistical significance
- repeated latency runs
- arbitrary RRF weight sweeps
- graph-aware retrieval

### Stage 2: larger labels

Required before making quality claims:

- expand from 3 reviewed queries to at least 10-15 reviewed queries
- keep catalog and label JSON in sync
- include query types that naturally favor keyword, vector, and hybrid retrieval

### Stage 3: research packaging

Only after Stage 1 and Stage 2:

- add p50/p95 repeated latency
- consider nDCG or Precision@k
- add `/research` or report-based portfolio summary

## Backend Design

### Security And Exposure Guard

This endpoint is internal evaluation tooling, not a product API.

Rules:

- keep it under `/api/internal/search-evaluation`
- do not call it from frontend code
- do not expose it in public demos or production-like deployments
- add a config flag such as `sigak.internal.search-evaluation.enabled`
  - local default can be enabled for MVP development
  - deployed environments should set it to disabled or block `/api/internal/**` at the gateway/reverse proxy
- response metadata may include embedding provider, model name, and dimension, but must never include API keys, request headers, raw credentials, or environment variable values
- update deployment/AWS docs when this endpoint is implemented so internal paths are explicitly blocked or disabled

This does not replace real authentication.
It is a local MVP guardrail to reduce accidental exposure until the project has a broader internal API security model.

### New Endpoint

```http
POST /api/internal/search-evaluation/retrieval-runs
```

Request:

```json
{
  "queries": ["agent evaluation", "graph rag failure"],
  "systems": ["KEYWORD", "VECTOR", "HYBRID"],
  "limit": 20
}
```

Rules:

- `queries` must contain 1-50 non-blank strings.
- `systems` is optional; default is `["KEYWORD", "VECTOR", "HYBRID"]`.
- allowed systems are `KEYWORD`, `VECTOR`, `HYBRID`.
- `PUBLIC` is not accepted by this endpoint; runner creates `PUBLIC` run by calling the existing public API.
- `limit` is optional; default is `sigak.search.hybrid.result-limit`.
- `limit` must be between 1 and `sigak.search.hybrid.result-limit`.

Response:

```json
{
  "generatedAt": "2026-06-02T12:00:00Z",
  "limit": 20,
  "runs": [
    {
      "query": "graph rag failure",
      "system": "HYBRID",
      "status": "COMPLETED",
      "rankedArticleIds": [4, 1, 2],
      "candidateCount": 3,
      "staleCandidateCount": 0,
      "failureReason": null,
      "degraded": false,
      "resolvedMode": "HYBRID",
      "timings": {
        "keywordElapsedMs": 7,
        "embeddingElapsedMs": 12,
        "vectorElapsedMs": 9,
        "fusionElapsedMs": 0,
        "articleReloadElapsedMs": 4,
        "totalElapsedMs": 26
      },
      "metadata": {
        "embeddingProvider": "local",
        "embeddingModelName": "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
        "embeddingDimension": 384
      }
    }
  ]
}
```

### New Backend Types

Create under `backend/src/main/kotlin/com/sigak/search/evaluation/`:

- `ArticleRetrievalEvaluationController`
  - thin internal controller
  - validates request body through DTO constraints or service preconditions
- `ArticleRetrievalEvaluationService`
  - orchestrates query/system runs
  - reloads final API-ready article IDs from PostgreSQL
  - records stale candidate count
- `ArticleRetrievalEvaluationSystem`
  - enum: `KEYWORD`, `VECTOR`, `HYBRID`
- `ArticleRetrievalRunStatus`
  - enum: `COMPLETED`, `FAILED`
- DTOs:
  - `ArticleRetrievalRunRequest`
  - `ArticleRetrievalRunResponse`
  - `ArticleRetrievalRunItemResponse`
  - `ArticleRetrievalRunTimingsResponse`
  - `ArticleRetrievalRunMetadataResponse`

### Candidate Search Extraction

Current `ArticlePublicSearchService` owns keyword/vector candidate search as private helpers.
To avoid duplicate logic, extract a small shared component:

```text
ArticleRetrievalCandidateService
```

Responsibilities:

- normalize query
- call `ArticleKeywordSearchService.searchArticleIds(query, limit)`
- call `ArticleVectorCandidateSearcher.search(query, limit)`
- measure keyword, embedding, vector elapsed time
- return keyword attempt and vector attempt separately
- preserve failure reason without falling back

`ArticlePublicSearchService` then keeps its public behavior:

```text
candidate service
-> resolve public mode
-> HYBRID / KEYWORD_ONLY / VECTOR_ONLY / POSTGRES_FALLBACK
```

`ArticleRetrievalEvaluationService` uses the same candidate result:

```text
candidate service
-> KEYWORD run
-> VECTOR run
-> HYBRID run
```

This is a targeted extraction, not a broad search refactor.

### System Semantics

`KEYWORD`:

- uses Elasticsearch keyword candidates only
- fails if keyword candidate search fails
- does not call vector search just to produce a keyword run

`VECTOR`:

- uses Qdrant vector candidates only
- includes embedding metadata
- fails if embedding or Qdrant search fails

`HYBRID`:

- uses RRF over keyword and vector candidates
- completes only when both keyword and vector candidate search succeed
- fails if either dependency fails
- does not degrade to keyword-only or vector-only inside evaluation
- response sets `resolvedMode = HYBRID`, `degraded = false`

`PUBLIC`:

- is not a backend evaluation endpoint system
- runner calls `GET /api/articles?query=...`
- represents what a user would actually see
- may include degraded behavior such as keyword-only, vector-only, or PostgreSQL fallback
- stores `resolvedMode` when runner can read `/api/internal/search-metrics/articles` immediately after the public request
- stores client round-trip latency, not backend-only `totalElapsedMs`

This differs intentionally from public search.
Public search protects user experience; evaluation preserves experiment honesty.
The benchmark report shows both when requested, but does not treat strict `HYBRID` and `PUBLIC` as the same system.

### Stale Candidate Handling

Each completed run reloads ranked candidate IDs through PostgreSQL/API-ready article boundary.

Example:

```text
candidate IDs: [4, 999, 1]
API-ready IDs after reload: [4, 1]
staleCandidateCount: 1
```

The response stores only API-ready `rankedArticleIds`.
Stale candidates are not scored as relevant or irrelevant in benchmark metrics because they are outside the frozen catalog/user-visible article set.

## Runner Design

The existing runner evolves from single public run to system comparison.

New command:

```bash
node experiments/scripts/retrieval-benchmark.mjs \
  --labels=experiments/datasets/labels/search-labels.api-ready-2026-06-02.2026-06-02.json \
  --base-url=http://localhost:8080 \
  --output-dir=experiments/results/retrieval/latest \
  --systems=keyword,vector,hybrid,public \
  --k=5 \
  --limit=20
```

Rules:

- If `--systems` is omitted, the runner keeps the existing public API smoke behavior.
- If `--systems` is provided and contains `keyword`, `vector`, or `hybrid`, the runner uses the internal evaluation endpoint for those systems.
- If `--systems` contains `public`, the runner uses the existing public article API for that system.
- allowed values: `keyword`, `vector`, `hybrid`, `public`.
- `--limit` is optional; default is `20`.
- runner rejects `limit < k`.
- system comparison mode can include one or more systems, including `--systems=hybrid` or `--systems=public`.
- internal `hybrid` means pure RRF fused result when keyword and vector both succeed, not public degraded behavior.
- `public` means user-visible public API behavior and may degrade.

### Output Artifacts

For multiple systems:

```text
experiments/results/retrieval/latest/
├── runs.keyword.json
├── runs.vector.json
├── runs.hybrid.json
├── runs.public.json
├── metrics.by-query.json
├── metrics.by-system.json
├── metrics.comparison.json
└── report.md
```

`runs.<system>.json`:

- labelsPath
- baseUrl
- system
- k
- limit
- query-level rankedArticleIds
- query-level status/failureReason
- query-level resolvedMode/degraded when available
- query-level latency/timings

`metrics.by-query.json`:

- flat rows keyed by `query + system`
- status
- resolvedMode
- degraded
- top1StrongHit
- recallAtK
- mrrAtK
- latencyMs
- failureReason

`metrics.by-system.json`:

- system
- attemptedQueryCount
- completedQueryCount
- failedQueryCount
- failureRate
- degradedQueryCount
- degradedRate
- failureReasonCounts
- resolvedModeCounts
- macroTop1StrongHit
- macroRecallAtK
- macroMrrAtK
- effectiveTop1StrongHit
- effectiveRecallAtK
- effectiveMrrAtK
- averageLatencyMs
- totalStaleCandidateCount

`metrics.comparison.json`:

- best observed system by metric
- per-system metric table
- reliability table
- warnings
  - failed systems
  - degraded public runs
  - too few queries
  - deterministic embedding mode
  - label set smaller than target

`report.md`:

- experiment conditions
- system comparison table
- query-level result table
- failure/stale candidate table
- limitations and next steps

### Dataset Interpretation Guard

The report must separate "measurement was produced" from "quality claim is justified".

Rules:

- if reviewed query count is below 10, report title or warning must say `smoke benchmark`
- if catalog article count is below 20, report must warn that ranking difficulty is still low
- `best system` should be written as `best observed system in this smoke run`
- report must not claim hybrid is generally better until the label set reaches the roadmap target of 10-15 reviewed queries at minimum
- for the current 3-query/6-article set, the valid conclusion is only that comparison artifacts are reproducible

## Failure Handling

Backend:

- If one system fails for one query, return a `FAILED` run item for that system/query.
- Do not fail the whole request unless request validation fails or the service cannot produce any response.
- Failure reasons use stable strings:
  - `KEYWORD_SEARCH_FAILED`
  - `EMBEDDING_FAILED`
  - `QDRANT_SEARCH_FAILED`
  - `HYBRID_DEPENDENCY_FAILED`
  - `ARTICLE_RELOAD_FAILED`

Runner:

- failed query/system pairs do not contribute to macro quality metrics.
- failed query/system pairs count toward `failedQueryCount`.
- failed query/system pairs count as `0` only in `effective*` metrics.
- `failureRate = failedQueryCount / attemptedQueryCount`.
- `failureReasonCounts` groups failures by stable reason code.
- `degradedRate` is tracked separately from failure rate.
- report must show failures explicitly.
- if a system has zero completed queries, its metrics are `null` rather than `0`.

Rationale:

- `macro*` metrics answer "when this system completed, how good was the ranking?"
- `effective*` metrics answer "if failed runs count as user-visible misses, how good was the system overall?"
- `failureRate` and `failureReasonCounts` answer "how often and why did this system fail?"
- `degradedRate` answers "how often did user-visible public behavior rely on fallback/degrade?"
- `0` in macro metrics means the system ran and retrieved nothing useful.
- `null` in macro metrics means there was no valid completed measurement.

Example:

```json
{
  "system": "HYBRID",
  "attemptedQueryCount": 3,
  "completedQueryCount": 2,
  "failedQueryCount": 1,
  "failureRate": 0.3333333333333333,
  "macroRecallAtK": 0.75,
  "effectiveRecallAtK": 0.5,
  "failureReasonCounts": {
    "QDRANT_SEARCH_FAILED": 1
  }
}
```

## Testing Strategy

Backend tests:

- controller test:
  - accepts valid systems and queries
  - rejects blank queries
  - rejects unknown systems
  - rejects `PUBLIC` as backend endpoint system
  - rejects invalid limit
  - returns not-found or disabled response when internal search evaluation config is disabled
- service test:
  - keyword run uses keyword candidates only
  - vector run uses vector candidates only
  - hybrid run uses RRF only when both paths succeed
  - hybrid run fails instead of degrading when one dependency fails
  - stale candidate IDs are omitted after API-ready reload and counted
  - result order is preserved after reload

Runner tests:

- CLI parser accepts `--systems` and `--limit`
- CLI parser rejects unknown systems
- CLI parser rejects `limit < k`
- internal evaluation client maps backend runs by system
- public system client calls existing public article API
- public system records degraded/resolved mode when internal metrics lookup is available
- metric aggregation ignores failed rows and records failed counts
- metric aggregation computes `effective*` metrics with failed rows counted as zero
- metric aggregation computes failure reason counts and degraded rate
- report writer includes comparison, failures, and limitations
- report writer warns when query count or catalog size is too small

Smoke verification:

```bash
docker compose -f infra/docker-compose.yml up -d --pull never postgres elasticsearch qdrant
cd ai
SIGAK_EMBEDDING_PROVIDER=deterministic .venv/bin/python -m uvicorn app.main:app --host 127.0.0.1 --port 8000
cd ../backend
SIGAK_AI_SERVER_URL=http://localhost:8000 ./gradlew bootRun
curl -X POST 'http://localhost:8080/api/internal/search-projections/articles/rebuild'
curl -X POST 'http://localhost:8080/api/internal/search-projections/article-vectors/rebuild'
node experiments/scripts/retrieval-benchmark.mjs \
  --labels=experiments/datasets/labels/search-labels.api-ready-2026-06-02.2026-06-02.json \
  --base-url=http://localhost:8080 \
  --output-dir=experiments/results/retrieval/latest \
  --systems=keyword,vector,hybrid,public \
  --k=5 \
  --limit=20
```

Record:

- indexedCount for Elasticsearch and Qdrant
- completed/failed query count per system
- degraded query count for `PUBLIC`
- Top1 Strong Hit, Recall@5, MRR@5, average latency per system
- effective Top1 Strong Hit, effective Recall@5, effective MRR@5
- failure reason counts per system
- stale candidate count per system

## Documentation Updates

Implementation should update:

- `docs/API_SPEC.md`
  - internal evaluation endpoint contract
- `docs/search-evaluation/queries.md`
  - system comparison command and interpretation
- `experiments/README.md`
  - artifact layout for multi-system benchmark
- `docs/STATUS.md`, `docs/STATUS.ko.md`
  - after implementation and smoke verification
- `docs/ROADMAP.md`, `docs/ROADMAP.ko.md`
  - mark fair comparison runner progress after implementation
- `docs/blog/2026-06-02-dev-log.md`
  - only if implementation occurs in the same session
- `docs/blog/topic-queue.md`
  - strengthen qrels/run/metrics/report topic with system comparison evidence

## Blog Notes

This feature is a strong technical blog candidate.

Important points:

- Public API fallback and benchmark comparison have different goals.
- `HYBRID` as a product behavior is not the same as `HYBRID` as an experiment system.
- Strict `HYBRID` and user-visible `PUBLIC` should be reported separately.
- Failed measurements should be shown as failures, not converted to zero scores.
- Effective metrics can count failures as zero, but only alongside failure rate and reason counts.
- Projection stores are candidate generators; PostgreSQL remains the source of truth for final article IDs.
- A small benchmark can be honest if it explains label count, system failures, and deterministic embedding limitations.

## Risks And Mitigations

| Risk | Mitigation |
| --- | --- |
| Internal endpoint grows into a second product API | Keep it under `/api/internal/search-evaluation`, return IDs/metrics only, do not use it from frontend, add an enable/disable config flag |
| Internal metadata leaks in deployed environment | Do not return secrets, disable the endpoint or block `/api/internal/**` in deployed environments, document the deployment guard |
| Evaluation duplicates public search logic | Extract only candidate search into a small shared service; keep `PUBLIC` run in runner through the existing public API |
| Hybrid comparison hides dependency failure | Mark HYBRID failed when keyword or vector path fails |
| Strict HYBRID ignores user-visible degrade behavior | Add a separate `PUBLIC` system that records public API result and degrade/resolved mode when available |
| Failure metrics make systems hard to compare | Report both macro quality metrics over completed runs and effective metrics with failed runs counted as zero |
| Stale projection IDs distort metrics | Reload API-ready IDs from PostgreSQL and count stale candidates |
| Code change scope becomes too large | Limit Stage 1 to internal strict endpoint, runner system support, JSON artifacts, and Markdown report |
| Small label set leads to exaggerated claims | Report warnings, use `best observed system in this smoke run`, and keep quality claims limited until at least 10-15 reviewed queries |
| Deterministic embedding is mistaken for semantic quality | Record embedding provider/model and warn in report |

## Implementation Defaults

- Keep the existing public smoke path when `--systems` is omitted.
- Use internal evaluation runs for `keyword`, `vector`, and `hybrid` whenever `--systems` is provided.
- Use the existing public article API for `public`.
- Use flat `query + system` rows in `metrics.by-query.json`.
- Use backend `totalElapsedMs` as `latencyMs` for internal system comparison.
- Use client round-trip latency for `public` and mark its latency source as `client_round_trip`.
- Use `backend_total` as the latency source for strict internal systems.
- Add `failureRate`, `failureReasonCounts`, `degradedRate`, and `resolvedModeCounts` to system summaries.

## Definition Of Done

The design is implemented when:

- Backend internal evaluation endpoint returns `KEYWORD`, `VECTOR`, and `HYBRID` run items.
- Internal evaluation endpoint can be disabled or blocked by configuration for deployed environments.
- Public article API response shape is unchanged.
- Runner can generate `keyword`, `vector`, `hybrid`, and `public` run artifacts.
- Failed systems, failure reason counts, effective metrics, degraded public runs, and stale candidates are visible in JSON and Markdown report.
- Node runner tests pass.
- Relevant backend tests pass.
- Local smoke creates a comparison report from the first 3-query label set.
- Documentation records verified facts, deployment/internal endpoint cautions, label-set size warnings, and limitations.
