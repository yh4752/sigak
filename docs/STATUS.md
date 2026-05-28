# Sigak Status

[English](STATUS.md) | [한국어](STATUS.ko.md)

Last updated: 2026-05-28

This is a living status document. Update it whenever a roadmap phase is completed, a major risk changes, or verification results become outdated.

## 1. Summary

Sigak is an AI-powered technical news insight platform for important AI, software development, and computer science updates.

The project now has the core MVP foundation: documentation, a monorepo structure, Spring Boot backend APIs, PostgreSQL persistence, a React frontend, a FastAPI mock AI server, and a selected-source collection-to-persistence pipeline.

As of 2026-05-27, the MVP target has been sharpened into a three-week public portfolio release. Sigak v0.1 should demonstrate a complete AI search vertical slice: collection, PostgreSQL source-of-truth storage, Elasticsearch keyword search, Qdrant vector search, Neo4j graph projection, hybrid retrieval, graph-aware article detail, and reproducible local metrics.

| Area | Current state | Assessment |
| --- | --- | --- |
| Product direction | MVP scope and non-goals are documented | Good |
| Backend | Persisted article list/detail/search APIs are implemented; query search uses Elasticsearch first with PostgreSQL fallback; FastAPI embedding client boundary exists | Good; API-ready filtering, fallback search, and AI client wiring are in place |
| Frontend | Home, search, detail, and related article flows are implemented | Good; stale related state was fixed |
| AI server | FastAPI mock enrichment and deterministic embedding endpoints are implemented; real embedding mode is the preferred next retrieval path | Initial AI/RAG boundary complete; semantic retrieval quality still needs a real model |
| Data | PostgreSQL schema, seed data, graph-ready metadata, and collected article persistence exist | MVP foundation complete |
| Search infra | Elasticsearch readiness, article projection rebuild, and keyword search path are connected; Qdrant and Neo4j remain pending | Core keyword slice is underway |
| Infra | Docker Compose includes PostgreSQL, Elasticsearch, Qdrant, Neo4j, AI server, and SchemaSpy tooling | Good local foundation; application-level projection flows still need expansion |
| Docs | README, API spec, roadmap, status, ADRs, and research strategy are organized | Good |

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

Needs work:

- Elasticsearch keyword search is now connected to `/api/articles?query=...`, but ranking tuning, user-facing search metrics, and hybrid search are still pending.
- PostgreSQL filtering remains as the fallback path when Elasticsearch is unavailable.

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
- Deterministic embedding endpoint:
  - `POST /api/embeddings/text`
- Enrichment request/response schemas
- Pytest smoke tests

Strengths:

- Local development does not require paid API keys.
- Spring Boot and FastAPI responsibilities are clearly separated.
- The internal enrichment contract is documented in `docs/API_SPEC.md`.
- The embedding boundary can be used for Qdrant indexing smoke tests before real embedding quality work begins.

Needs work:

- Spring Boot can call the FastAPI embedding endpoint over HTTP, but enrichment still uses the local mock client and Qdrant projection wiring is still pending.
- The current embedding output is deterministic test data. A real embedding model mode should be added before using vector search quality in portfolio claims.

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
- Collector, normalizer, pipeline, and persistence service tests

Strengths:

- The pipeline starts from selected sources instead of broad crawling.
- Raw content and extracted text are separated for future reprocessing.
- Community aggregators such as Hacker News are intentionally excluded as initial original sources.
- Collected articles can be published into the same persisted article model used by public APIs.

Needs work:

- Scheduled collection is not implemented yet.
- There is no admin/internal trigger endpoint or command runner yet.
- Retry, failure status, and observability are still missing.
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

- Qdrant and Neo4j application-level projection flows are still pending.
- Search metrics need to move from basic logs toward a reproducible benchmark artifact.

## 4. Stabilization Fixes

### 4.1 API-ready article filtering

Public article list/search/detail APIs now query only articles with `processingStatus = PUBLISHED` and current enrichment. This prevents unfinished collected articles from breaking public response mapping.

### 4.2 Article detail stale related articles

The frontend clears related article state when the selected article changes and guards against late related article fetch results overwriting the new page state.

### 4.3 AI enrichment input validation

FastAPI enrichment schemas reject whitespace-only required text and constrain `suggestedImportanceScore` to the `0-100` range.

### 4.4 Elasticsearch keyword search with PostgreSQL fallback

The public article search flow now uses Elasticsearch as the primary keyword candidate source for non-blank `query` values. Elasticsearch returns article IDs, and Spring Boot reloads API-ready article responses from PostgreSQL so the search index does not become the source of truth.

If Elasticsearch is unavailable, the service falls back to the previous PostgreSQL field filtering path and records query length, result count, fallback status, and elapsed time in an internal in-memory metrics endpoint. This keeps the MVP usable during local infrastructure failures while preserving an observable signal for later benchmark work.

## 5. Verification

Recent verification:

| Area | Command | Result |
| --- | --- | --- |
| Backend | `./gradlew test` | Passed |
| Backend search slice | `./gradlew test --tests com.sigak.search.service.ElasticsearchArticleKeywordSearchServiceTest --tests com.sigak.article.service.ArticleServiceTest` | Passed |
| Backend article API | `./gradlew test --tests com.sigak.article.controller.ArticleControllerTest` | Passed |
| Local Elasticsearch search smoke | `rebuild -> _count -> /api/articles?query=graph -> metrics -> stop Elasticsearch -> fallback query -> metrics` | Passed; indexed 5 articles, fallback returned article 4, and metrics showed `totalSearchCount=2`, `fallbackSearchCount=1` |
| Backend search metrics | `./gradlew test --tests com.sigak.search.metrics.ArticleSearchMetricsRecorderTest --tests com.sigak.search.metrics.ArticleSearchMetricsControllerTest --tests com.sigak.article.service.ArticleServiceTest` | Passed |
| Backend FastAPI embedding client | `./gradlew test --tests com.sigak.ai.embedding.FastApiEmbeddingClientTest` | Passed |
| AI embedding endpoint | `.venv/bin/python -m pytest tests/test_embedding_router.py` | Passed |
| Frontend tests | `npm test` | Passed |
| Frontend build | `npm run build` | Passed |
| Frontend lint | `npm run lint` | Passed |
| AI tests | `.venv/bin/python -m pytest` | Passed |

Notes:

- AI tests should use `ai/.venv` Python when the virtual environment exists.
- Backend tests use Testcontainers with PostgreSQL.

## 6. Next Work

### 6.1 Three-week priorities

1. Add local search infrastructure:
   - expand Docker Compose for Elasticsearch, Qdrant, Neo4j, and AI server
   - define health checks and environment variables
   - keep PostgreSQL as the source of truth

2. Add a controlled collection trigger:
   - internal/admin endpoint or command runner
   - source-level execution result
   - fetched/published/skipped/failed counts

3. Add indexing and search:
   - indexing rebuild trigger
   - Elasticsearch keyword indexing and search
   - FastAPI embedding boundary
   - Qdrant vector indexing and search
   - RRF-based hybrid search

4. Add graph-aware insight:
   - Neo4j article/topic/relation projection
   - relation reasons or related concepts on article detail
   - keep the UI small and readable

5. Add metrics and portfolio packaging:
   - indexing duration/count metrics
   - search latency p50/p95 metrics
   - Recall@5 and MRR@5 benchmark
   - README, ADR, demo script, and release notes

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

### Step 3. Add collection trigger and run observability

Next goal:

- Execute selected-source collection intentionally and inspect the result.

Completion criteria:

- Internal/admin endpoint or command runner can trigger collection.
- Result includes fetched/published/skipped/failed counts and failure reasons.
- Failure recording or retry rules are documented.

### Step 4. Add search projection stores

Next goal:

- Rebuild search projections from PostgreSQL and compare keyword, vector, and hybrid search.

Completion criteria:

- Elasticsearch stores searchable article text and metadata.
- Qdrant stores article vectors from the FastAPI embedding boundary.
- Hybrid search merges keyword and vector results with RRF.
- Projection rebuild is reproducible from a local command or internal endpoint.

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
- AI/RAG practical usage: early but now explicitly targeted for the v0.1 search infrastructure slice
- Collection execution/automation: persistence pipeline complete, trigger still early
- Local deployability: medium; multi-service compose is the next infrastructure risk
- Portfolio documentation: high

Sigak is now more than a planning document or a CRUD/search demo. The next step is to make the public AI search flow explicit and observable:

```txt
source trigger -> collect -> persist -> index projections -> hybrid search -> graph-aware detail -> metrics
```

When this flow can be triggered and inspected, Sigak will function as a public, reviewable version of the AI search and Graph RAG experience described in the resume.

## 9. Conclusion

The project direction remains aligned with the MVP goals. Spring Boot is the stable API boundary, FastAPI is reserved for AI/RAG work, PostgreSQL remains the source of truth, and Elasticsearch, Qdrant, and Neo4j should be added as rebuildable projection stores.

The next development focus should be the three-week v0.1 sequence: compose expansion, collection trigger, projection rebuild, hybrid search, graph-aware detail, local metrics, and portfolio packaging.
