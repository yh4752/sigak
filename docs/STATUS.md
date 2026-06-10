# Sigak Status

[English](STATUS.md) | [한국어](STATUS.ko.md)

Last updated: 2026-06-10

> 2026-06-10: A codebase overview document (`docs/CODEBASE_OVERVIEW.md`) was added and behavior-preserving refactorings were applied across backend/frontend/ai (duplicate helpers consolidated, dead code removed). Frontend (test/lint/build) and AI server (pytest) were verified in-session; backend `./gradlew test`/`check` is 미검증 and must be run locally. See `docs/blog/2026-06-10-dev-log.md`.

This is a living status document. Update it whenever a roadmap phase is completed, a major risk changes, or verification results become outdated.

## 1. Summary

Sigak is an AI-powered technical news insight platform for important AI, software development, and computer science updates.

The project now has the core MVP foundation: documentation, a monorepo structure, Spring Boot backend APIs, PostgreSQL persistence, a React frontend, a FastAPI mock AI server, and a selected-source collection-to-persistence pipeline.

As of 2026-05-27, the MVP target has been sharpened into a three-week public portfolio release. Sigak v0.1 should demonstrate a complete AI search vertical slice: collection, PostgreSQL source-of-truth storage, Elasticsearch keyword search, Qdrant vector search, Neo4j graph projection, hybrid retrieval, graph-aware article detail, and reproducible local metrics.

| Area | Current state | Assessment |
| --- | --- | --- |
| Product direction | MVP scope and non-goals are documented | Good |
| Backend | Persisted article list/detail/search APIs are implemented; query search uses Elasticsearch keyword candidates and Qdrant vector candidates with PostgreSQL fallback; public graph-aware article detail, internal Qdrant vector diagnostics, Neo4j graph projection/context, and collection failure diagnostics APIs are implemented | Good; API-ready filtering, hybrid fallback search, AI client wiring, internal vector search, public graph context, graph projection context, and collection failure inspection are in place |
| Frontend | Home, search, detail, related article, and graph reason display flows are implemented | Good; stale related state was fixed and graph reason lookup degrades without breaking detail |
| AI server | FastAPI mock enrichment endpoint and configurable embedding providers are implemented; local FastEmbed multilingual mode is the preferred retrieval path | Initial AI/RAG boundary complete; Qdrant projection now consumes embedding vectors through Spring Boot |
| Data | PostgreSQL schema, seed data, graph-ready metadata, and collected article persistence exist | MVP foundation complete |
| Search infra | Elasticsearch readiness, keyword projection/search, Qdrant vector projection/search, Neo4j graph projection/context lookup, public hybrid search, fallback modes, search metrics, strict keyword/vector/hybrid retrieval benchmark runs, graph-aware evaluation artifacts, and an expanded API-ready search catalog are connected | Core keyword/vector/hybrid/graph projection and graph-aware evaluation slice is complete for the current phase; the catalog has expanded to 41 API-ready articles, but larger labels are still needed before quality claims |
| Infra | Docker Compose includes PostgreSQL, Elasticsearch, Qdrant, Neo4j, AI server, and SchemaSpy tooling | Good local foundation; portfolio packaging still needs expansion |
| Docs | README, API spec, roadmap, status, ADRs, research strategy, search labeling guide/tooling, experiments directory guide, smoke benchmark runner, system comparison runner, and graph-aware evaluation runner docs are organized | Good; retrieval benchmark labeling can start from a user-smoke-checked static HTML workflow, the first 3-query smoke label set exists against the local 6-article catalog, the 41-article expanded catalog is ready for a new label handoff, and the runner can generate public smoke, keyword/vector/strict-hybrid/public comparison, and graph-aware evaluation artifacts |

## 2. Sigak v0.1 Target

Target period: 2026-05-27 to 2026-06-16

Target positioning:

```txt
public AI news search MVP
-> hybrid retrieval
-> graph-aware article insight
-> reproducible local metrics
```

Target demo flow:

```txt
collect selected sources
-> persist articles in PostgreSQL
-> rebuild Elasticsearch, Qdrant, and Neo4j projections
-> run keyword, vector, and hybrid search
-> inspect graph-aware article detail
-> review indexing/search metrics and retrieval benchmark
```

Included in the v0.1 scope:

- controlled collection trigger
- indexing rebuild trigger
- Elasticsearch keyword search
- FastAPI embedding boundary
- Qdrant vector search
- RRF-based hybrid search
- Neo4j article/topic/relation projection
- article detail relationship reasons or related concepts
- indexing/search latency metrics
- small retrieval benchmark with 10-15 labeled queries
- portfolio README, ADR, demo script, and release notes

Explicitly deferred:

- full GraphRAG chatbot
- Airflow orchestration
- user accounts and saved articles
- full graph explorer
- large-scale benchmark suite
- production observability stack

## 3. Completed Work

### 3.1 Product and Documentation

Completed:

- Root `README.md` describes purpose, architecture, and local run commands.
- `docs/PRODUCT.md` defines the product, users, MVP scope, and product decisions.
- `docs/API_SPEC.md` documents the article API and internal enrichment contract.
- `docs/ROADMAP.md` combines service and research execution tracks.
- `docs/SOURCE_POLICY.md` defines initial source quality rules.
- `docs/decisions/` records major architecture decisions.
- Korean companion documents are available through `.ko.md` language links.
- `docs/search-evaluation/labeling.html` provides a static browser tool for creating retrieval benchmark relevance labels and exporting label JSON.
- `experiments/README.md` documents the raw/labels/processed/results directories, the API-ready article catalog export command, benchmark runner command, prerequisites, and smoke result interpretation.
- `docs/API_SPEC.md` documents the internal retrieval evaluation endpoint and the boundary between strict `HYBRID` experiment runs and `PUBLIC` user-visible search behavior.
- The expanded API-ready search catalog artifact `experiments/datasets/raw/articles.catalog.api-ready-2026-06-05.json` was generated with `catalogId=api-ready-2026-06-05` and article count `41`; the 6-article `api-ready-2026-06-02` smoke catalog remains preserved as the baseline.

### 3.2 Backend

Completed:

- Kotlin + Spring Boot backend
- Layered architecture: controller, service, repository, entity/domain, DTO, config
- Article list API
- Article detail API
- Keyword search
- Swagger/OpenAPI generation
- Explicit CORS for local frontend origins
- PostgreSQL + Flyway persistence schema
- JPA entities and repositories
- Curated seed article data
- Service, controller, and integration tests

Article API responses include:

- title
- source
- url
- publishedAt
- eventType
- primaryCategory
- topics
- summary
- whyItMatters
- importanceScore
- relatedArticleIds

Strengths:

- Controllers stay thin and APIs expose DTOs instead of entities.
- Raw article content and enrichment output are separated, which supports reprocessing and future Graph RAG work.
- Seed data includes event type, category, topic, and relation metadata.

Current search behavior:

- Hybrid search is now connected to `/api/articles?query=...` through Elasticsearch keyword candidates, Qdrant vector candidates, and reciprocal rank fusion.
- PostgreSQL filtering remains as the fallback path when both projection paths are unavailable.

### 3.3 Frontend

Completed:

- React + TypeScript + Vite setup
- React Router routes:
  - `/`
  - `/articles/:id`
- Axios API client
- Zod response validation at the API boundary
- Home screen with search and article sections
- Article detail page with summary, why-it-matters, topics, source link, and related articles
- Loading, empty, and error states
- API client, page, and component tests

Strengths:

- API calls are centralized in client modules.
- Zod catches backend response drift at runtime.
- The MVP UI focuses on search -> detail -> related article exploration without heavy state management.

Needs work:

- Future category/topic filters should keep search state synchronized with URL query parameters.

### 3.4 AI Server

Completed:

- FastAPI app
- Health endpoint
- Mock enrichment endpoint:
  - `POST /api/enrichment/article`
- Configurable embedding endpoint:
  - `POST /api/embeddings/text`
- Enrichment request/response schemas
- Pytest smoke tests

Strengths:

- Local development does not require paid API keys.
- Spring Boot and FastAPI responsibilities are clearly separated.
- The internal enrichment contract is documented in `docs/API_SPEC.md`.
- The embedding boundary can use local model mode for semantic retrieval and deterministic mode for fast wiring smoke tests.

Needs work:

- Spring Boot can call the FastAPI embedding endpoint over HTTP, but enrichment still uses the local mock client.
- Real enrichment remains pending; vector embedding is connected for search projection work.

### 3.5 Collection and Enrichment Foundation

Completed:

- Source registry
- RSS/Atom collector boundary
- arXiv collector boundary
- Collected article model
- Article normalizer
- Mock enrichment client
- Collected article persistence writer
- Duplicate detection by canonical URL, source external ID, and source/title/published date
- Separate persistence for raw content, current enrichment, and topics
- Internal controlled collection run endpoint with source/article count response
- Command runner wrapper for controlled collection runs
- Persistent collection failure events with `runId`, failure kind, retry hint, and article hints
- Internal read-only diagnostics endpoint for querying collection failure events
- Collector, normalizer, pipeline, and persistence service tests

Strengths:

- The pipeline starts from selected sources instead of broad crawling.
- Raw content and extracted text are separated for future reprocessing.
- Community aggregators such as Hacker News are intentionally excluded as initial original sources.
- Collected articles can be published into the same persisted article model used by public APIs.

Needs work:

- Scheduled collection is not implemented yet.
- Internal controlled collection trigger exists for local HTTP and command-line runs, but full run history remains deferred.
- Automatic retry and broader observability beyond failure events, diagnostics lookup, and response counts are still missing.
- FastAPI HTTP enrichment mode is still pending.

### 3.6 Infrastructure and Local Development

Completed:

- Root `.env.example`
- PostgreSQL, Elasticsearch, Qdrant, Neo4j, AI server, and SchemaSpy Docker Compose setup
- Backend/frontend/AI local run docs
- Testcontainers-based PostgreSQL integration tests
- Search infrastructure health endpoint
- Article search projection rebuild endpoint
- SchemaSpy one-off DB visualization workflow

Needs work:

- Graph-aware evaluation runner is implemented and smoke-verified; a 41-article expanded catalog now exists, but larger labels are still needed before quality claims.
- Search metrics now have both internal in-memory endpoints and a reproducible smoke benchmark artifact path.
- The frozen catalog export command for API-ready PostgreSQL articles is implemented and smoke-verified with a 6-article local artifact plus a separate 41-article expanded artifact.
- The retrieval benchmark runner is implemented and smoke-verified with 3 reviewed queries.
- The system comparison runner can now generate keyword, vector, strict hybrid, and public artifacts from the same label set. The first 3-query/6-article comparison smoke completed with no failed or degraded runs, but meaningful quality claims still require more labeled examples.
- The graph-aware evaluation runner can generate graph context run, by-query metric, summary, and appended markdown report artifacts from public search and public graph-context endpoints.

## 4. Stabilization Fixes

### 4.1 API-ready article filtering

Public article list/search/detail APIs now query only articles with `processingStatus = PUBLISHED` and current enrichment. This prevents unfinished collected articles from breaking public response mapping.

### 4.2 Article detail stale related articles

The frontend clears related article state when the selected article changes and guards against late related article fetch results overwriting the new page state.

### 4.3 AI enrichment input validation

FastAPI enrichment schemas reject whitespace-only required text and constrain `suggestedImportanceScore` to the `0-100` range.

### 4.4 Hybrid public search with PostgreSQL fallback

The public article search flow now uses Elasticsearch as the keyword candidate source and Qdrant as the vector candidate source for non-blank `query` values. Candidate IDs are fused with reciprocal rank fusion, and Spring Boot reloads API-ready article responses from PostgreSQL so projection stores do not become the source of truth.

If one projection path fails, the service degrades to `KEYWORD_ONLY` or `VECTOR_ONLY`. If both paths fail, it falls back to the previous PostgreSQL field filtering path. Internal metrics now record search mode, candidate counts, stale candidate count, failure flags, fallback reason, and latency breakdowns. This keeps the MVP usable during local infrastructure failures while preserving observable signals for later benchmark work.

### 4.5 Qdrant internal vector search

The backend can now rebuild a Qdrant article vector projection from API-ready PostgreSQL articles. The rebuild flow builds article embedding input text, calls the FastAPI embedding endpoint, validates embedding provider/model/dimension consistency, recreates the configured Qdrant collection, and stores vectors with article metadata payloads.

An internal vector search endpoint embeds a query, searches Qdrant for article IDs and scores, reloads API-ready article responses from PostgreSQL, and returns a timing breakdown for embedding, Qdrant search, article reload, and total elapsed time. Public `/api/articles` search now reuses the lower-level vector candidate boundary while keeping the diagnostics endpoint separate.

### 4.6 Neo4j internal graph projection and context lookup

The backend can now rebuild a Neo4j article graph projection from API-ready PostgreSQL articles. The projection stores article metadata, normalized topics, `HAS_TOPIC` relationships with positions, and `RELATED_TO` relationships with relation type and stored reason. PostgreSQL remains the source of truth; Neo4j does not store raw content, summaries, why-it-matters text, embedding vectors, or secrets.

Internal graph context lookup returns an article's projected topics, topic-neighbor article IDs, outgoing related articles, relation reasons, shared topics, and Neo4j timing metadata. Public article responses and the frontend are unchanged in this step.

### 4.7 Public graph-aware article detail

Public article detail can now request `GET /api/articles/{id}/graph-context` as a companion endpoint. The shared `ArticleResponse` shape remains unchanged for list, search, detail, and bulk related article lookup. The public graph context response exposes related article reasons, shared topics, and topic context, but hides internal Neo4j timings and relation type.

The frontend joins `relatedArticleReasons` to already-loaded related articles by `articleId` and displays the reason below each related article title when available. If Neo4j context is missing or unavailable, the public endpoint degrades to an empty graph context while preserving the article detail page.

### 4.8 Related article bulk lookup and refactor cleanup

Public `GET /api/articles?ids=...` can now reload API-ready articles by ID in request order, which lets the frontend fetch related articles with one bulk request instead of one request per related ID. The response shape stays the same as list/search responses.

The review cleanup also moved article response graph prefetching into a repository fragment, extracted shared elapsed-time measurement and published-date parsing helpers, added enrichment `modelName` metadata to the internal enrichment response, made collection failure dependencies explicit constructor injections, and gave source HTTP fetches configurable connect/read timeouts.

## 5. Verification

Recent verification:

| Area | Command | Result |
| --- | --- | --- |
| Backend review findings refactor | `./gradlew test` -> `./gradlew check` | Passed; both commands returned `BUILD SUCCESSFUL` after the bulk article API, parser, timing, repository prefetch, timeout, and enrichment metadata changes |
| Frontend related bulk lookup | `npm test` -> `npm run lint` -> `npm run build` | Passed; Vitest reported 6 test files and 29 tests passed, ESLint returned no errors, and Vite built successfully |
| AI enrichment metadata | `.venv/bin/python -m pytest` | Passed; 8 tests passed with 20 warnings |
| Backend | `./gradlew test` | Passed |
| Backend search slice | `./gradlew test --tests com.sigak.search.hybrid.ArticlePublicSearchServiceTest --tests com.sigak.article.service.ArticleServiceTest` | Passed |
| Backend article API | `./gradlew test --tests com.sigak.article.controller.ArticleControllerTest` | Passed |
| Controlled collection runtime smoke | `docker compose -f infra/docker-compose.yml up -d postgres -> SIGAK_SEARCH_MODE=KEYWORD ./gradlew bootRun -> POST /api/internal/collections/runs for github-blog twice -> GET /api/articles/6 -> stop services` | Passed; first run published 1 article with ID 6, second run skipped duplicate ID 6, article detail returned through public API |
| Controlled collection command runner smoke | `docker compose -f infra/docker-compose.yml up -d --pull never postgres -> SIGAK_SEARCH_MODE=KEYWORD ./gradlew bootRun --args='collection-run --sources=github-blog --max=1'` | Passed; command exited successfully with `COMPLETED`, `published=0`, `skipped=1`, `skippedArticleIds=6` |
| Controlled collection failure evidence focused group | `./gradlew test --tests com.sigak.collection.controller.CollectionRunControllerTest --tests 'com.sigak.collection.runner.*' --tests com.sigak.collection.service.CollectionRunServiceTest --tests com.sigak.collection.service.SourceCollectionServiceTest --tests com.sigak.collection.service.CollectionFailureClassifierTest --tests com.sigak.collection.service.CollectionFailureEventRecorderTest` | Passed |
| Collection-to-projection demo smoke | `compose up postgres/elasticsearch/qdrant/ai -> bootRun -> POST /api/internal/collections/runs -> GET /api/internal/collections/failure-events -> rebuild ES/Qdrant projections -> GET /api/articles?query=graph -> GET /api/internal/search-metrics/articles -> compose down` | Passed; collection run `COMPLETED`, duplicate `skippedArticleIds=[6]`, diagnostics `returnedCount=0`, ES/Qdrant indexed 6 articles, public search mode `HYBRID` |
| Collection failure diagnostics runtime smoke | `bootRun` with an intentionally invalid local proxy -> `POST /api/internal/collections/runs` for `github-blog` -> `GET /api/internal/collections/failure-events` | Passed; run `cd28c139-0275-465a-a04d-4ff5bea2597a` failed at `FETCH_SOURCE`, persisted `failureEventId=1`, `failureKind=TRANSIENT_FETCH`, `retryable=true`, diagnostics `returnedCount=1` |
| Internal vector search metrics smoke | `POST /api/internal/vector-search/articles` with `{"query":"graph rag","limit":3}` -> `GET /api/internal/search-metrics/article-vectors` | Passed; top result article `4`, embedding provider `local`, model `sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2`, total elapsed `45ms` |
| Frontend/API local smoke | `npm test` -> `npm run lint` -> `npm run build` -> `curl http://127.0.0.1:5173/` -> `GET /api/articles/4` | Passed for tests/lint/build/API; in-app browser automation was blocked by local URL security policy, so no browser screenshot was captured |
| Backend full test after failure evidence | `./gradlew test --rerun-tasks` | Passed |
| Backend check after failure evidence | `./gradlew check` | Passed |
| Local hybrid search smoke | `compose up postgres/elasticsearch/qdrant/ai -> bootRun -> rebuild ES/Qdrant projections -> query graph/security/vector -> stop qdrant -> stop elasticsearch -> stop both -> metrics` | Passed; both projections indexed 5 articles, `HYBRID`, `KEYWORD_ONLY`, `VECTOR_ONLY`, and `POSTGRES_FALLBACK` modes were observed |
| Backend search metrics | `./gradlew test --tests com.sigak.search.metrics.ArticleSearchMetricsRecorderTest --tests com.sigak.search.metrics.ArticleSearchMetricsControllerTest --tests com.sigak.article.service.ArticleServiceTest` | Passed |
| Backend Qdrant vector search slice | `./gradlew test --tests 'com.sigak.search.vector.*'` | Passed |
| Backend FastAPI embedding client | `./gradlew test --tests com.sigak.ai.embedding.FastApiEmbeddingClientTest` | Passed |
| AI embedding endpoint | `.venv/bin/python -m pytest tests/test_embedding_router.py` | Passed |
| Frontend tests | `npm test` | Passed |
| Frontend build | `npm run build` | Passed |
| Frontend lint | `npm run lint` | Passed |
| AI tests | `.venv/bin/python -m pytest` | Passed |
| Search labeling static tooling | `node` embedded JSON/script syntax check -> `git diff --check` -> external resource scan with `rg` | Passed; embedded JSON and browser script syntax were valid, whitespace check passed, and no external script/link/http resource references were found |
| Search labeling manual browser smoke | User opened `file:///Users/yonghyun/my-projects/sigak/docs/search-evaluation/labeling.html` in a browser and tested it manually | Passed by user report; Codex in-app browser automation for local `file://` screenshots/clicks/download parsing remains blocked by policy |
| Search catalog export focused package tests | `./gradlew test --tests 'com.sigak.search.evaluation.catalog.*'` | Passed |
| Backend full test after search catalog export | `./gradlew test` | Passed |
| Backend check after search catalog export | `./gradlew check` | Passed |
| Search catalog export smoke | `docker compose -f infra/docker-compose.yml up -d --pull never postgres -> pg_isready -> ./gradlew bootRun --args='search-catalog-export --output=../experiments/datasets/raw/articles.catalog.json --limit=50 --catalog-id=api-ready-2026-06-02'` | Passed; `articleCount=6`, output `experiments/datasets/raw/articles.catalog.json` |
| Search catalog JSON parse | `node -e` schema check for `experiments/datasets/raw/articles.catalog.json` | Passed; `catalogId=api-ready-2026-06-02`, article count `6` |
| Expanded search catalog export focused package tests | `./gradlew test --tests 'com.sigak.search.evaluation.catalog.*'` | Passed; `BUILD SUCCESSFUL` |
| Expanded search catalog export smoke | `docker compose -f infra/docker-compose.yml up -d --pull never postgres -> pg_isready -> ./gradlew bootRun --args='search-catalog-export --output=../experiments/datasets/raw/articles.catalog.api-ready-2026-06-05.json --limit=50 --catalog-id=api-ready-2026-06-05'` | Passed; `articleCount=41`, output `experiments/datasets/raw/articles.catalog.api-ready-2026-06-05.json` |
| Expanded search catalog JSON parse | `node -e` schema and duplicate-ID check for `experiments/datasets/raw/articles.catalog.api-ready-2026-06-05.json` | Passed; `catalogId=api-ready-2026-06-05`, article count `41`, first exported ID `3`, last exported ID `16` |
| Smoke baseline content preservation | `node -e` old catalog and old label content checks | Passed; old catalog `catalogId=api-ready-2026-06-02`, article count `6`; old label `catalogId=api-ready-2026-06-02`, `catalogArticleCount=6`, reviewed query count `3`, explicit label count `12` |
| Smoke latest result preservation | `git status --short experiments/results/retrieval/latest experiments/results/graph/latest` and `git diff --name-only -- experiments/results/retrieval/latest experiments/results/graph/latest` | Passed; no changed result paths |
| Search labeling sort/static check | `node` embedded JSON/script syntax check for `docs/search-evaluation/labeling.html` | Passed; article sort control markers and script syntax are valid |
| Search label JSON validation | `node` schema/catalog consistency check for `experiments/datasets/labels/search-labels.api-ready-2026-06-02.2026-06-02.json` | Passed; 3 reviewed queries, 12 explicit labels, no invalid article IDs or relevance values |
| Retrieval benchmark runner tests | `node --test experiments/scripts/retrieval-benchmark/*.test.mjs` | Passed; 81 tests, 81 passed, 0 failed |
| Retrieval comparison smoke | `compose up postgres/elasticsearch/qdrant -> deterministic AI server -> SIGAK_INTERNAL_SEARCH_EVALUATION_ENABLED=true backend bootRun -> rebuild ES/Qdrant projections -> node retrieval-benchmark --systems=keyword,vector,hybrid,public` | Passed; ES indexed 6 articles, Qdrant indexed 6 vectors, evaluated 3 queries. Completed/failed/degraded counts were `3/0/0` for keyword, vector, strict hybrid, and public. Macro Recall@5: keyword `0.6666666666666666`, vector/hybrid/public `0.8333333333333334`; warnings correctly marked the label set as smaller than 10 queries and catalog below 20 articles |
| Backend Neo4j graph focused tests | `./gradlew test --tests 'com.sigak.search.graph.*'` | Passed |
| Backend OpenAPI graph docs test | `./gradlew test --tests com.sigak.docs.OpenApiDocumentationTest` | Passed |
| Backend full graph gate | `./gradlew test` -> `./gradlew check` | Passed; both commands returned `BUILD SUCCESSFUL` |
| Neo4j graph projection smoke | `compose up neo4j -> bootRun -> POST /api/internal/graph-projections/articles/rebuild -> GET /api/internal/graph/articles/4/context -> cypher count checks` | Passed; rebuild returned `articleNodeCount=26`, `topicNodeCount=18`, `hasTopicRelationshipCount=36`, `relatedToRelationshipCount=10`, `durationMs=1037`; article `4` context returned `Graph RAG` topic and related articles `1`, `5` with stored reasons; Cypher counts matched the rebuild response |
| Backend public graph context focused tests | `./gradlew test --tests com.sigak.article.service.ArticlePublicGraphContextServiceTest --tests com.sigak.article.controller.ArticleControllerTest --tests com.sigak.docs.OpenApiDocumentationTest` | Passed |
| Frontend public graph context focused tests | `npm test -- articles.test.ts ArticleDetailPage.test.tsx` | Passed; 2 files, 20 tests passed |
| Backend full public graph detail gate | `./gradlew test` -> `./gradlew check` | Passed; both commands returned `BUILD SUCCESSFUL` |
| Frontend full public graph detail gate | `npm test` -> `npm run lint` -> `npm run build` | Passed; `npm test` reported 6 files and 33 tests passed |
| Public graph context smoke | `compose up neo4j -> bootRun -> POST /api/internal/graph-projections/articles/rebuild -> GET /api/articles/4/graph-context -> stop neo4j -> GET /api/articles/4/graph-context` | Passed; rebuild returned `articleNodeCount=26`, `topicNodeCount=18`, `hasTopicRelationshipCount=36`, `relatedToRelationshipCount=10`, `durationMs=1535`; article `4` public graph context returned related article reasons for article `1` and `5` without `timings`; `GET /api/articles/999/graph-context` returned `404`, `GET /api/articles/0/graph-context` returned `400`, and Neo4j unavailable fallback returned `{"articleId":4,"relatedArticleReasons":[],"topics":[]}` |
| Graph-aware evaluation design self-check | `rg -n "TBD\|TODO\|FIXME\|미정\|나중에 구현\|적절히\|필요하면" docs/superpowers/specs/2026-06-03-graph-aware-evaluation-design.md \|\| true` | Passed; no placeholder output |
| Graph-aware evaluation runner tests | `node --test experiments/scripts/retrieval-benchmark/*.test.mjs` | Passed; 81 tests, 81 passed, 0 failed |
| Graph-aware evaluation smoke | `compose up neo4j -> SIGAK_INTERNAL_SEARCH_EVALUATION_ENABLED=true backend bootRun -> rebuild ES/Qdrant/Neo4j projections -> node retrieval-benchmark --systems=public --include-graph-context` | Passed; ES indexed `26` articles, Qdrant indexed `26` vectors with `sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2`, Neo4j rebuild returned `articleNodeCount=26`, `topicNodeCount=18`, `hasTopicRelationshipCount=36`, `relatedToRelationshipCount=10`; evaluated `3` queries, Graph Context Coverage@5 `0.3333333333333333`, Graph Reasoned Coverage@5 `0.3333333333333333`, graph context failure rate `0`, empty context rate `0`, average graph latency `33.53333333333333ms`; warnings correctly marked the label set as smoke-only, graph density as too small for quality claims, latency as public API round-trip, and reasons as stored projection reasons |

Notes:

- AI tests should use `ai/.venv` Python when the virtual environment exists.
- Backend tests use Testcontainers with PostgreSQL.

## 6. Next Work

### 6.1 Three-week priorities

1. Harden controlled collection operations:
   - keep the manual retry decision table current as failure kinds evolve
   - keep failure inspection examples tied to real runtime samples

2. Expand graph-aware evaluation evidence:
   - create labels for the `api-ready-2026-06-05` 41-article expanded catalog
   - increase the label set beyond the current 3-query/6-article smoke dataset
   - compare public graph context against simpler related article and retrieval baselines on the expanded catalog
   - document where graph reasons improve article detail and where they add little value without over-claiming from smoke data

3. Expand retrieval benchmark and portfolio metrics:
   - use the current 6-article frozen catalog and 3-query smoke set as a reproducibility baseline
   - use the 41-article expanded catalog for the next 10-15 reviewed query label set before running expanded benchmarks
   - indexing duration/count metrics
   - search latency p50/p95 metrics
   - expand Recall@5 and MRR@5 benchmark labels beyond the first 3-query smoke set
   - keep keyword/vector/strict-hybrid/public comparison artifacts reproducible as the label set grows
   - README, ADR, demo script, and release notes

4. Keep hybrid search stable while expanding graph work:
   - preserve the public article response shape
   - keep PostgreSQL as the source of truth
   - treat Elasticsearch, Qdrant, and Neo4j as rebuildable projections

### 6.2 Milestones

| Date | Milestone | Completion signal |
| --- | --- | --- |
| 2026-06-02 | Search infrastructure slice | Articles can be indexed into Elasticsearch and Qdrant, then searched through keyword, vector, and hybrid modes. |
| 2026-06-09 | Graph and metrics slice | Neo4j projection, graph-aware detail, indexing metrics, latency metrics, and retrieval benchmark artifacts are reproducible. |
| 2026-06-16 | Sigak v0.1 portfolio MVP | README, ADR, demo script, tests, and release notes are ready for portfolio review. |

### 6.3 Risk controls

- Use a real embedding model for the main vector retrieval path; keep deterministic embeddings only as fallback/test mode if model setup slows local smoke testing.
- Treat Elasticsearch, Qdrant, and Neo4j as projection stores, not primary data stores.
- Do not build a full graph explorer in v0.1.
- Keep benchmark labels small enough to review manually.
- Prefer clear local reproducibility over broad feature coverage.

## 7. Recommended Development Order

### Step 1. Fix code review findings

Status: done.

Completed:

- Public article APIs expose only published/current-enrichment articles.
- Article detail no longer keeps stale related articles during navigation.
- AI endpoint rejects whitespace-only input.
- Related tests were added or updated.

### Step 2. Connect persisted collection pipeline

Status: done.

Completed:

- Collection pipeline and persistence schema are connected.
- Collected articles can be stored in the database.
- Raw content and enrichment output are stored separately.
- Duplicate articles are blocked by canonical URL, source external ID, and source/title/published date.
- Mock enrichment results are stored as current enrichment.

### Step 3. Stabilize hybrid search evidence

Status: done for the current search slice.

Completed:

- Elasticsearch and Qdrant projection rebuild flows are connected.
- Public `/api/articles?query=...` uses hybrid search with RRF.
- Search metrics record mode, fallback reason, candidate counts, stale candidate count, and latency breakdown.
- Local smoke verified `HYBRID`, `KEYWORD_ONLY`, `VECTOR_ONLY`, and `POSTGRES_FALLBACK` modes.

### Step 4. Add collection trigger and run observability

Status: done for the current MVP operations slice.

Completed:

- Execute selected-source collection intentionally through the internal endpoint or command runner.
- Internal endpoint can trigger selected-source collection.
- Command runner can execute the same `CollectionRunService` path.
- Result includes fetched/published/skipped/failed counts and failure summaries.
- Persistent failure events are recorded with `runId`, failure kind, retry hint, and optional article hints.
- Internal diagnostics endpoint can list failure events by source, run, retryable flag, and limit.
- Runtime smoke includes both a duplicate-skip success sample and a forced `TRANSIENT_FETCH` failure event sample.
- Manual retry guidance now maps each failure kind to an operator action without adding an automatic retry queue.

Still pending:

- Full `collection_runs` lifecycle history remains deferred.
- Automatic retry queue/scheduler remains deferred.
- Retry guidance should be kept in sync when new failure kinds or collector behavior are added.

### Step 5. Add graph-aware insight

Next goal:

- Show Sigak's relationship-based differentiation inside article detail using a small Neo4j projection.

Completion criteria:

- Related article reasons or related concepts are visible.
- The experience explains what the article is connected to without requiring a full graph UI.

### Step 6. Add metrics, benchmark, and portfolio packaging

Next goal:

- Make the project reviewable as a public AI search portfolio project.

Completion criteria:

- Indexing and search latency metrics are generated.
- A small retrieval benchmark compares keyword, vector, and hybrid modes.
- README, ADR, demo script, and release notes explain the architecture and trade-offs.

## 8. MVP Completeness Assessment

Current assessment:

- Product direction: high
- Backend structure: high
- Frontend core flow: medium-high
- AI/RAG practical usage: vector and hybrid search are connected through embeddings; real enrichment remains pending
- Collection execution/automation: persistence pipeline, internal trigger, command runner, and persistent failure events are connected; full run history and automatic retry remain pending
- Local deployability: medium-high; multi-service compose exists, while run docs and deployment packaging still need polish
- Portfolio documentation: high

Sigak is now more than a planning document or a CRUD/search demo. Backend persistence, API docs, source policy, AI boundaries, Elasticsearch/Qdrant-backed hybrid search, Neo4j graph projection, and public graph-aware article detail are connected. The next step is to compare graph-aware detail against simpler baselines and make the remaining benchmark flow explicit and observable:

```txt
source trigger -> collect -> persist -> index projections -> hybrid search -> graph-aware detail -> metrics
```

When this flow can be triggered and inspected, Sigak will function as a public, reviewable version of the AI search and Graph RAG experience described in the resume.

## 9. Conclusion

The project direction remains aligned with the MVP goals. Spring Boot is the stable API boundary, FastAPI is reserved for AI/RAG work, PostgreSQL remains the source of truth, and Elasticsearch, Qdrant, and Neo4j are used as rebuildable projection stores.

The next development focus should be the remaining v0.1 sequence: graph-aware evaluation, collection operations hardening, retrieval benchmark artifacts, and portfolio packaging.
