# Sigak Status

[English](STATUS.md) | [한국어](STATUS.ko.md)

Last updated: 2026-05-31

This is a living status document. Update it whenever a roadmap phase is completed, a major risk changes, or verification results become outdated.

## 1. Summary

Sigak is an AI-powered technical news insight platform for important AI, software development, and computer science updates.

The project now has the core MVP foundation: documentation, a monorepo structure, Spring Boot backend APIs, PostgreSQL persistence, a React frontend, a FastAPI mock AI server, and a selected-source collection-to-persistence pipeline.

As of 2026-05-27, the MVP target has been sharpened into a three-week public portfolio release. Sigak v0.1 should demonstrate a complete AI search vertical slice: collection, PostgreSQL source-of-truth storage, Elasticsearch keyword search, Qdrant vector search, Neo4j graph projection, hybrid retrieval, graph-aware article detail, and reproducible local metrics.

| Area | Current state | Assessment |
| --- | --- | --- |
| Product direction | MVP scope and non-goals are documented | Good |
| Backend | Persisted article list/detail/search APIs are implemented; query search uses Elasticsearch keyword candidates and Qdrant vector candidates with PostgreSQL fallback; internal Qdrant vector diagnostics APIs are implemented | Good; API-ready filtering, hybrid fallback search, AI client wiring, and internal vector search are in place |
| Frontend | Home, search, detail, and related article flows are implemented | Good; stale related state was fixed |
| AI server | FastAPI mock enrichment endpoint and configurable embedding providers are implemented; local FastEmbed multilingual mode is the preferred retrieval path | Initial AI/RAG boundary complete; Qdrant projection now consumes embedding vectors through Spring Boot |
| Data | PostgreSQL schema, seed data, graph-ready metadata, and collected article persistence exist | MVP foundation complete |
| Search infra | Elasticsearch readiness, keyword projection/search, Qdrant vector projection/search, public hybrid search, fallback modes, and search metrics are connected; Neo4j remains pending | Core keyword/vector/hybrid slice is complete for the current phase |
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
- Collector, normalizer, pipeline, and persistence service tests

Strengths:

- The pipeline starts from selected sources instead of broad crawling.
- Raw content and extracted text are separated for future reprocessing.
- Community aggregators such as Hacker News are intentionally excluded as initial original sources.
- Collected articles can be published into the same persisted article model used by public APIs.

Needs work:

- Scheduled collection is not implemented yet.
- Internal controlled collection trigger exists for local runs, but persistent run history and command runner wrapper remain pending.
- Retry, persistent failure status, and observability beyond response counts are still missing.
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

- Neo4j application-level projection flow is still pending.
- Search metrics need to move from internal in-memory endpoints toward a reproducible benchmark artifact.

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

## 5. Verification

Recent verification:

| Area | Command | Result |
| --- | --- | --- |
| Backend | `./gradlew test` | Passed |
| Backend search slice | `./gradlew test --tests com.sigak.search.hybrid.ArticlePublicSearchServiceTest --tests com.sigak.article.service.ArticleServiceTest` | Passed |
| Backend article API | `./gradlew test --tests com.sigak.article.controller.ArticleControllerTest` | Passed |
| Local hybrid search smoke | `compose up postgres/elasticsearch/qdrant/ai -> bootRun -> rebuild ES/Qdrant projections -> query graph/security/vector -> stop qdrant -> stop elasticsearch -> stop both -> metrics` | Passed; both projections indexed 5 articles, `HYBRID`, `KEYWORD_ONLY`, `VECTOR_ONLY`, and `POSTGRES_FALLBACK` modes were observed |
| Backend search metrics | `./gradlew test --tests com.sigak.search.metrics.ArticleSearchMetricsRecorderTest --tests com.sigak.search.metrics.ArticleSearchMetricsControllerTest --tests com.sigak.article.service.ArticleServiceTest` | Passed |
| Backend Qdrant vector search slice | `./gradlew test --tests 'com.sigak.search.vector.*'` | Passed |
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

1. Harden controlled collection operations:
   - command runner wrapper for the existing internal endpoint service
   - persistent run history or failure evidence
   - retry/failure status rules for bad feeds and invalid articles

2. Add Neo4j graph projection:
   - project articles and topics from PostgreSQL
   - store or project article-topic relationships
   - expose relation reasons or related concepts on article detail

3. Add retrieval benchmark and portfolio metrics:
   - indexing duration/count metrics
   - search latency p50/p95 metrics
   - Recall@5 and MRR@5 benchmark
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

Status: partially done.

Completed:

- Execute selected-source collection intentionally and inspect the result.
- Internal endpoint can trigger selected-source collection.
- Result includes fetched/published/skipped/failed counts and failure summaries.

Still pending:

- Command runner wrapper remains deferred.
- Persistent failure recording and retry rules remain deferred.

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
- Collection execution/automation: persistence pipeline and internal trigger are connected; command runner and persistent run history remain pending
- Local deployability: medium-high; multi-service compose exists, while run docs and deployment packaging still need polish
- Portfolio documentation: high

Sigak is now more than a planning document or a CRUD/search demo. Backend persistence, API docs, source policy, AI boundaries, and Elasticsearch/Qdrant-backed hybrid search are connected. The next step is to make the remaining graph and benchmark flow explicit and observable:

```txt
source trigger -> collect -> persist -> index projections -> hybrid search -> graph-aware detail -> metrics
```

When this flow can be triggered and inspected, Sigak will function as a public, reviewable version of the AI search and Graph RAG experience described in the resume.

## 9. Conclusion

The project direction remains aligned with the MVP goals. Spring Boot is the stable API boundary, FastAPI is reserved for AI/RAG work, PostgreSQL remains the source of truth, and Elasticsearch plus Qdrant are already used as rebuildable projection stores. Neo4j should follow the same rule when graph projection is added.

The next development focus should be the remaining v0.1 sequence: collection operations hardening, Neo4j graph projection, graph-aware article detail, retrieval benchmark artifacts, and portfolio packaging.
