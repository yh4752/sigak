# Sigak Status

[English](STATUS.md) | [한국어](STATUS.ko.md)

Last updated: 2026-05-09

This is a living status document. Update it whenever a roadmap phase is completed, a major risk changes, or verification results become outdated.

## 1. Summary

Sigak is an AI-powered technical news insight platform for important AI, software development, and computer science updates.

The project now has the core MVP foundation: documentation, a monorepo structure, Spring Boot backend APIs, PostgreSQL persistence, a React frontend, a FastAPI mock AI server, and a selected-source collection-to-persistence pipeline.

Remaining MVP work includes a controlled collection trigger, Spring Boot to FastAPI HTTP enrichment mode, search hardening, limited Graph RAG insight, and local development polish.

| Area | Current state | Assessment |
| --- | --- | --- |
| Product direction | MVP scope and non-goals are documented | Good |
| Backend | Persisted article list/detail/search APIs are implemented | Good; API-ready filtering is in place |
| Frontend | Home, search, detail, and related article flows are implemented | Good; stale related state was fixed |
| AI server | FastAPI mock enrichment endpoint is implemented | Initial foundation complete |
| Data | PostgreSQL schema, seed data, graph-ready metadata, and collected article persistence exist | MVP foundation complete |
| Infra | PostgreSQL Docker Compose setup exists | Partial; full service compose is still pending |
| Docs | README, API spec, roadmap, status, ADRs, and research strategy are organized | Good |

## 2. Completed Work

### 2.1 Product and Documentation

Completed:

- Root `README.md` describes purpose, architecture, and local run commands.
- `docs/PRODUCT.md` defines the product, users, MVP scope, and product decisions.
- `docs/API_SPEC.md` documents the article API and internal enrichment contract.
- `docs/ROADMAP.md` combines service and research execution tracks.
- `docs/SOURCE_POLICY.md` defines initial source quality rules.
- `docs/decisions/` records major architecture decisions.
- Korean companion documents are available through `.ko.md` language links.

### 2.2 Backend

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

- Current keyword search still runs through Spring Boot service logic over persisted fields. It should move to database query search before Elasticsearch is introduced.

### 2.3 Frontend

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

### 2.4 AI Server

Completed:

- FastAPI app
- Health endpoint
- Mock enrichment endpoint:
  - `POST /api/enrichment/article`
- Enrichment request/response schemas
- Pytest smoke tests

Strengths:

- Local development does not require paid API keys.
- Spring Boot and FastAPI responsibilities are clearly separated.
- The internal enrichment contract is documented in `docs/API_SPEC.md`.

Needs work:

- Spring Boot does not yet call FastAPI over HTTP. The backend currently uses the mock enrichment boundary.

### 2.5 Collection and Enrichment Foundation

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

### 2.6 Infrastructure and Local Development

Completed:

- Root `.env.example`
- PostgreSQL Docker Compose setup
- Backend/frontend/AI local run docs
- Testcontainers-based PostgreSQL integration tests

Needs work:

- Docker Compose currently runs PostgreSQL only.
- Full local compose for backend, frontend, AI server, and database is not complete.
- Elasticsearch and Qdrant are intentionally deferred until the MVP search/retrieval needs justify them.

## 3. Stabilization Fixes

### 3.1 API-ready article filtering

Public article list/search/detail APIs now query only articles with `processingStatus = PUBLISHED` and current enrichment. This prevents unfinished collected articles from breaking public response mapping.

### 3.2 Article detail stale related articles

The frontend clears related article state when the selected article changes and guards against late related article fetch results overwriting the new page state.

### 3.3 AI enrichment input validation

FastAPI enrichment schemas reject whitespace-only required text and constrain `suggestedImportanceScore` to the `0-100` range.

## 4. Verification

Recent verification:

| Area | Command | Result |
| --- | --- | --- |
| Backend | `./gradlew test` | Passed |
| Frontend tests | `npm test` | Passed |
| Frontend build | `npm run build` | Passed |
| Frontend lint | `npm run lint` | Passed |
| AI tests | `.venv/bin/python -m pytest` | Passed |

Notes:

- AI tests should use `ai/.venv` Python when the virtual environment exists.
- Backend tests use Testcontainers with PostgreSQL.

## 5. Next Work

### 5.1 Short-term priorities

1. Add a controlled collection trigger:
   - internal/admin endpoint or command runner
   - source-level execution result
   - fetched/published/skipped/failed counts

2. Strengthen collection status handling:
   - discovered
   - fetched
   - extracted
   - enriched
   - published
   - failed

3. Document local collection execution:
   - collection trigger command
   - AI server mode
   - backend/frontend/PostgreSQL flow

### 5.2 MVP stabilization

Next implementation areas:

- Spring Boot HTTP client for FastAPI enrichment
- `mock` vs HTTP enrichment mode selection
- collection failure recording or retry rules
- Docker Compose scope decision for backend, AI server, and frontend
- simpler local run commands

The goal is to make collected articles executable, observable, enriched, stored, and visible from the frontend.

### 5.3 Graph RAG-ready expansion

Remaining work:

- explicit concept entity or topic/concept normalization
- article-concept relationship storage
- relation reason exposure decision
- related concepts UI or small related graph
- Qdrant embedding storage decision
- minimum graph-backed retrieval scope

For the MVP, article detail relationship explanation is more valuable than a full graph explorer.

### 5.4 Search expansion

Recommended order:

1. Move keyword search to PostgreSQL queries.
2. Add Elasticsearch when data size and quality needs justify it.
3. Add Qdrant and embeddings when semantic search is needed.
4. Consider hybrid search last.

Adding Elasticsearch and Qdrant before MVP stability would be premature.

## 6. Recommended Development Order

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

### Step 4. Local development polish

Next goal:

- Make the project easier to run for a new reviewer.

Completion criteria:

- README is enough to run backend/frontend/AI/PostgreSQL.
- `.env.example` covers required values.
- Docker Compose scope is clear.

### Step 5. Limited relationship insight

Next goal:

- Show Sigak's relationship-based differentiation inside article detail.

Completion criteria:

- Related article reasons or related concepts are visible.
- The experience explains what the article is connected to without requiring a full graph UI.

## 7. MVP Completeness Assessment

Current assessment:

- Product direction: high
- Backend structure: high
- Frontend core flow: medium-high
- AI/RAG practical usage: early
- Collection execution/automation: persistence pipeline complete, trigger still early
- Local deployability: medium
- Portfolio documentation: high

Sigak is now more than a planning document or a CRUD/search demo. The next step is to make collection execution explicit and observable:

```txt
source trigger -> collect -> normalize -> enrich -> persist -> search/list/detail
```

When this flow can be triggered and inspected, Sigak will feel much more like a real MVP than a static demo.

## 8. Conclusion

The project direction remains aligned with the MVP goals. Spring Boot is the stable API boundary, FastAPI is reserved for AI/RAG work, and React keeps the user flow simple.

The next development focus should be operationalizing the collection pipeline: controlled trigger, result visibility, mock/http enrichment mode, and failure records.
