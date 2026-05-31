# CLAUDE.md

This file provides Claude Code with tool-specific guidance (build/run/test commands and
file responsibilities) for this repository.

> **`AGENTS.md` is the single source of truth for development rules** — read order,
> architecture principles, the development lifecycle, the Definition of Done verification
> gate, coding rules, the session wrap-up loop, and git/security rules all live there.
> Read `AGENTS.md` first. This file only adds tool-specific detail; if the two ever
> conflict on a *rule*, `AGENTS.md` wins.

## Project Overview

Sigak is an AI-powered technical news insight platform targeting AI, software development, and computer science. The MVP (target: late June 2026) demonstrates:
- important technical news curation
- clear backend architecture with Spring Boot
- search and article exploration
- AI-assisted summarization via FastAPI
- Graph RAG-ready data modeling
- deployable local development with Docker Compose

See `docs/PRODUCT.md` for the product definition and `docs/ROADMAP.md` for the execution plan.

## Architecture

Three main services with clear boundaries:

1. **Spring Boot Backend** (Kotlin, REST API)
   - Main API boundary for frontend
   - Business rules, persistence, article orchestration
   - Calls FastAPI only for AI/enrichment tasks
   - OpenAPI documentation auto-generated and served at `/swagger-ui/index.html`

2. **React Frontend** (TypeScript + Vite)
   - Communicates with Spring Boot backend
   - Validates API responses with Zod
   - Uses Axios for HTTP calls
   - Keep UI simple for MVP (no complex state management yet)

3. **FastAPI Service** (Python)
   - AI/RAG-specific capabilities only
   - Summarization, enrichment, embeddings
   - Not called directly by frontend
   - Can use mocks if external APIs unavailable

Supporting infrastructure:
- **PostgreSQL** (source of truth, Flyway schema, seed data)
- **Elasticsearch** (rebuildable keyword-search projection)
- **Qdrant** (rebuildable vector-search projection)
- **Neo4j** (planned graph projection store)
- **Docker Compose** (local infrastructure setup)

See `docs/decisions/0001-initial-architecture.md` for architecture decisions.

## Build & Run Commands

### Backend
```bash
cd backend
./gradlew bootRun
```
Runs on `http://localhost:8080`
- API: `/api/articles` (list), `/api/articles?query={q}` (search), `/api/articles/{id}` (detail)
- Docs: `/swagger-ui/index.html`, `/v3/api-docs`

**Tests:**
```bash
./gradlew test
./gradlew test --tests ClassName
./gradlew test --tests ClassName.methodName
```

**Format/Check:**
```bash
./gradlew check
```

### Frontend
```bash
cd frontend
npm install
npm run dev
```
Runs on `http://localhost:5173`

**Tests:**
```bash
npm test
npm test -- ClassName
```

**Lint:**
```bash
npm run lint
```

**Build:**
```bash
npm run build
```

### Local Infrastructure (Docker Compose)
```bash
cd infra
docker compose up -d postgres elasticsearch qdrant neo4j ai
```
Starts local infrastructure services. Run the Spring Boot backend and Vite frontend
from their own directories unless a later compose profile explicitly adds app containers.

## Key Files & Responsibilities

### Backend
- **Domain Model**: Article entity, event types, categories (see `API_SPEC.md`)
- **Controllers**: Thin layers calling services
- **Services**: Business logic, search, filtering
- **Repositories**: Data access via JPA
- **DTOs**: API request/response objects (don't expose entities directly)

Article response shape (all endpoints return same structure):
```json
{
  "id": 1,
  "title": "...",
  "source": "...",
  "url": "...",
  "publishedAt": "2026-05-01T09:00:00Z",
  "eventType": "NEWS|OFFICIAL_ANNOUNCEMENT|RESEARCH|SECURITY|RELEASE",
  "primaryCategory": "AI|SECURITY|SOFTWARE_ENGINEERING|BACKEND|FRONTEND|DATA|INFRA_CLOUD|DEVTOOLS|CS_RESEARCH",
  "topics": ["..."],
  "summary": "...",
  "whyItMatters": "...",
  "importanceScore": 0-100,
  "relatedArticleIds": [...]
}
```

### Frontend
- **src/api/**: Axios client, Zod validation schemas
- **src/components/**: UI components (keep simple)
- **src/pages/**: Route-level components
- **src/types/**: TypeScript interfaces matching API contracts

Validation: All backend responses validated at API client boundary with Zod before reaching components.

### Documentation
- `docs/README.md`: Documentation index and reading order
- `docs/PRODUCT.md`: Product vision, MVP scope, categories, event types, and importance scoring
- `docs/API_SPEC.md`: Current API contract (authoritative)
- `docs/ROADMAP.md`: Integrated service and research roadmap
- `docs/STATUS.md`: Current implementation status and known risks
- `docs/RESEARCH_STRATEGY.md`: LLM/NLP research portfolio strategy
- `docs/SOURCE_POLICY.md`: Content selection criteria
- `docs/decisions/`: Architecture decision records (ADRs)

## Development Workflows

### Adding an Article Field
1. Update domain entity and JPA mappings
2. Add to DTO and API response
3. Update `docs/API_SPEC.md` with field description
4. Test API with Swagger UI
5. Update frontend Zod schema and components

### Adding Backend Endpoints
1. Create DTO for request/response
2. Add to service layer (business logic)
3. Create controller method
4. Add JUnit tests (service-level tests preferred)
5. Verify with Swagger UI at `/swagger-ui/index.html`

### Adding Frontend Pages/Components
1. Add React component under `src/components/`
2. Create Zod schema for any API calls
3. Import API client from `src/api/`
4. Validate responses before rendering
5. Test with `npm test`

### Implementing Search
Current public article search uses a hybrid retrieval path:
- Frontend: Pass `?query={q}` to `/api/articles`
- Backend: Generate keyword candidates from Elasticsearch and vector candidates from Qdrant
- Fusion: Merge candidates with reciprocal rank fusion, then reload API-ready article responses from PostgreSQL
- Degraded modes: If one projection path fails, use `KEYWORD_ONLY` or `VECTOR_ONLY`; if both fail, fall back to PostgreSQL field filtering

Search projection stores are rebuildable. PostgreSQL remains the source of truth for the final API response.

## Development Principles, Verification & Git

These rules are **defined in `AGENTS.md`** — see it for the full set:
development principles, the Definition of Done verification gate (exact build/test/lint
commands per service), the session wrap-up loop, tech choices, git commit style, and
secret-handling rules. Do not maintain a second copy here.

## Data Model Notes

Article data is designed for reprocessing and Graph RAG:
- Store raw `contentText` for embedding, enrichment, and future graph-aware processing
- Keep `topics` and `relatedArticleIds` for Graph RAG relationships
- `importanceScore` is manually curated for MVP (0-100, see `docs/PRODUCT.md`)
- `processingStatus` tracks collection/enrichment readiness (`DISCOVERED`, `FETCHED`, `EXTRACTED`, `NORMALIZED`, `ENRICHED`, `PUBLISHED`, `FAILED`)

Future Graph RAG fields (not in MVP):
- ConceptNode (id, name, type)
- ArticleConcept (articleId, conceptId, relationType, confidence)

See `docs/decisions/0002-product-scope-and-graph-rag-strategy.md` for Graph RAG direction.

## Common Issues & Decisions

**Why separate FastAPI from Spring Boot?**
Spring Boot owns user-facing APIs and orchestration. FastAPI handles AI/enrichment only. This keeps service boundaries clear and avoids overengineering the main backend.

**Why keep PostgreSQL as the source of truth for search responses?**
Elasticsearch and Qdrant are optimized for candidate retrieval, but they are projection stores. Reloading final article responses from PostgreSQL keeps public API behavior stable and makes projection rebuilds safe.

**Why mock data initially?**
Proves product structure without external dependencies. RSS/API collection comes after MVP is stable.

**Why Zod validation on frontend?**
Runtime validation ensures components receive expected shapes, even if backend changes drift from documentation.
