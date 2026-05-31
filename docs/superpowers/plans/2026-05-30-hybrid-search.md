# Hybrid Article Search Implementation Plan

> **For agentic workers:** This plan has already been executed on
> `codex/hybrid-search`. Keep it as the reviewed implementation record for the
> 2026-05-30 hybrid search slice. For follow-up work, create a new plan instead
> of reusing this one.

**Goal:** Public `GET /api/articles?query=...` uses Elasticsearch keyword
candidates and Qdrant vector candidates, fuses them with reciprocal rank fusion,
and keeps the existing `List<ArticleResponse>` response shape.

**Architecture:** PostgreSQL remains the source of truth. Elasticsearch and
Qdrant are rebuildable projection stores used only for candidate retrieval.
Spring Boot reloads final API-ready article responses from PostgreSQL after
candidate generation. The public search path degrades to keyword-only,
vector-only, or PostgreSQL fallback when projection stores are unavailable.

**Tech Stack:** Kotlin, Spring Boot, JPA, PostgreSQL/Testcontainers,
Elasticsearch REST client, Qdrant REST client, FastAPI embedding boundary,
JUnit, Mockito.

---

## Reference Spec

- `docs/superpowers/specs/2026-05-30-hybrid-search-design.md`

## Final File Structure

Created:

- `backend/src/main/kotlin/com/sigak/search/hybrid/ArticleSearchMode.kt`
- `backend/src/main/kotlin/com/sigak/search/hybrid/ArticleSearchCandidate.kt`
- `backend/src/main/kotlin/com/sigak/search/hybrid/ArticleVectorCandidateSearchService.kt`
- `backend/src/main/kotlin/com/sigak/search/hybrid/ReciprocalRankFusion.kt`
- `backend/src/main/kotlin/com/sigak/search/hybrid/ArticlePublicSearchService.kt`
- `backend/src/main/kotlin/com/sigak/search/hybrid/ArticlePublicSearchResult.kt`
- `backend/src/test/kotlin/com/sigak/search/hybrid/ReciprocalRankFusionTest.kt`
- `backend/src/test/kotlin/com/sigak/search/hybrid/ArticleVectorCandidateSearchServiceTest.kt`
- `backend/src/test/kotlin/com/sigak/search/hybrid/ArticlePublicSearchServiceTest.kt`

Modified:

- `backend/src/main/kotlin/com/sigak/search/config/SearchInfrastructureProperties.kt`
- `backend/src/main/resources/application.yml`
- `backend/src/test/resources/application.yml`
- `backend/src/main/kotlin/com/sigak/search/service/ArticleKeywordSearchService.kt`
- `backend/src/main/kotlin/com/sigak/search/service/ElasticsearchArticleKeywordSearchService.kt`
- `backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorSearchService.kt`
- `backend/src/main/kotlin/com/sigak/article/service/ArticleService.kt`
- `backend/src/main/kotlin/com/sigak/article/controller/ArticleController.kt`
- `backend/src/main/kotlin/com/sigak/search/metrics/ArticleSearchMetricObservation.kt`
- `backend/src/main/kotlin/com/sigak/search/metrics/ArticleSearchMetricsRecorder.kt`
- `backend/src/main/kotlin/com/sigak/search/metrics/ArticleSearchMetricsResponse.kt`
- Relevant backend tests, API docs, README/status/roadmap docs, blog topic queue,
  and search evaluation query notes.

## Executed Tasks

### Task 1: Hybrid Configuration And Candidate Model

- [x] Added `ArticleSearchMode` and `ArticleSearchCandidate`.
- [x] Added `sigak.search.mode` and `sigak.search.hybrid.*` properties.
- [x] Added property binding coverage.

### Task 2: Reciprocal Rank Fusion

- [x] Added pure RRF fusion logic.
- [x] Covered ranking, deduplication, weight, limit, and deterministic tie-break
  behavior in tests.

### Task 3: Limit-Aware Keyword Candidate Search

- [x] Added limit-aware keyword search while preserving the default method.
- [x] Updated Elasticsearch request generation to use the requested candidate
  limit.

### Task 4: Vector Candidate Search Boundary

- [x] Added `ArticleVectorCandidateSearcher` and
  `ArticleVectorCandidateSearchService`.
- [x] Reused the candidate boundary from the internal vector diagnostics service.
- [x] Preserved embedding and Qdrant elapsed-time diagnostics.
- [x] Wrapped embedding and Qdrant failures with bounded reason codes:
  `EMBEDDING_FAILED` and `QDRANT_SEARCH_FAILED`.

### Task 5: Public Hybrid Search Orchestration

- [x] Added `ArticlePublicSearchService`.
- [x] Implemented modes:
  - `HYBRID`
  - `KEYWORD_ONLY`
  - `VECTOR_ONLY`
  - `POSTGRES_FALLBACK`
- [x] Kept empty candidate lists as successful search results rather than
  fallback failures.
- [x] Applied `resultLimit` to hybrid and degraded modes.
- [x] Used bounded `fallbackReason` values instead of raw exception messages.

### Task 6: ArticleService Integration

- [x] Routed non-blank public queries through `ArticlePublicSearchService`.
- [x] Reloaded non-fallback candidate IDs from PostgreSQL.
- [x] Preserved PostgreSQL field filtering as the fallback path when both
  projection paths fail.
- [x] Recorded stale candidate count after PostgreSQL reload.

### Task 7: Mode-Aware Search Metrics

- [x] Replaced keyword-only search metrics with mode-aware search observations.
- [x] Recorded candidate counts, stale candidate count, failure flags, bounded
  fallback reason, and latency breakdowns.
- [x] Updated metrics controller tests and API docs.

### Task 8: Documentation And Smoke Evidence

- [x] Updated README, backend README, API specs, status, roadmap, and topic queue.
- [x] Added `docs/search-evaluation/queries.md` as the initial retrieval
  evaluation query note.
- [x] Wrote `docs/blog/2026-05-30-dev-log.md`.
- [x] Ran local smoke verification for:
  - `HYBRID`
  - `KEYWORD_ONLY`
  - `VECTOR_ONLY`
  - `POSTGRES_FALLBACK`

## Verification Record

Fresh verification should always be rerun before committing or pushing. The
implementation session verified the slice with:

- `./gradlew test`
- `./gradlew test --rerun-tasks`
- `./gradlew check`
- `git diff --check`
- Local Docker smoke:
  - start PostgreSQL, Elasticsearch, Qdrant, and AI server
  - rebuild Elasticsearch article projections
  - rebuild Qdrant article vector projections
  - query public `/api/articles?query=...`
  - stop Qdrant and Elasticsearch in turn to observe degraded/fallback modes

## Follow-Up Work

- Keep search ranking tuning and benchmark scoring out of this implementation
  slice until the retrieval benchmark artifact exists.
- Add Neo4j projection and graph-aware article detail through a separate spec and
  plan.
- Add collection trigger observability through a separate spec and plan.
