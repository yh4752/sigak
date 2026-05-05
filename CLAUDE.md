# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Sigak is an AI-powered technical news insight platform targeting AI, software development, and computer science. The MVP (target: late June 2026) demonstrates:
- important technical news curation
- clear backend architecture with Spring Boot
- search and article exploration
- AI-assisted summarization via FastAPI
- Graph RAG-ready data modeling
- deployable local development with Docker Compose

See `docs/PROJECT_CONTEXT.md` and `docs/PRODUCT_PLAN.md` for full product definition and strategy.

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
- **Elasticsearch** (keyword search, MVP ready)
- **Qdrant** (vector search, Graph RAG ready)
- **PostgreSQL/MySQL** (persistence, seeds with mock data)
- **Docker Compose** (local dev setup, ease of onboarding)

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

### Full Stack (Docker Compose)
```bash
cd infra
docker-compose up
```
Starts backend, frontend, database, and search services locally.

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
- `docs/PROJECT_CONTEXT.md`: Product vision and priorities
- `docs/PRODUCT_PLAN.md`: Feature scope, categories, event types, importance scoring
- `docs/API_SPEC.md`: Current API contract (authoritative)
- `docs/ROADMAP.md`: Feature pipeline
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
Current search is keyword-based. The response shape is stable for future semantic/graph expansion:
- Backend: Filter by title, summary, category, topics (case-insensitive)
- Frontend: Pass `?query={q}` to `/api/articles`
- Semantic search and graph-aware retrieval are later enhancements

## Important Development Principles

**From AGENTS.md:**
- Make small, reviewable changes (PR-sized commits)
- Avoid rewriting whole project; target MVP first
- Keep business logic in services, not controllers
- Do not expose entities directly through APIs; use DTOs
- Add tests for core business logic and important endpoints
- Update documentation for major features

**Tech Choices:**
- Kotlin for backend (concise, null-safe, Spring-friendly)
- TypeScript for frontend (type safety)
- Zod for API validation (runtime guarantees)
- Axios for HTTP (simple, reliable)
- No heavy state management for MVP (Context API sufficient)
- No Next.js unless explicitly requested

**Git Commit Style:**
- `feat: add new feature`
- `fix: fix bug`
- `docs: update documentation`
- `refactor: improve structure without behavior change`
- `chore: project setup or maintenance`
- `test: add or update tests`

Never commit: API keys, tokens, `.env`, build artifacts, personal data.

## Testing Expectations

- **Backend**: JUnit tests for service logic and API endpoints (minimum: service-level tests for core features)
- **Frontend**: Vitest for components and utilities
- **Integration**: Test API contracts match Swagger documentation

Run all tests before pushing:
```bash
cd backend && ./gradlew test
cd ../frontend && npm test
```

## Data Model Notes

Article data is designed for reprocessing and Graph RAG:
- Store raw `contentText` for future semantic/embedding operations
- Keep `topics` and `relatedArticleIds` for Graph RAG relationships
- `importanceScore` is manually curated for MVP (0-100, see `PRODUCT_PLAN.md`)
- `processingStatus` field planned for pipeline stages (COLLECTED → EXTRACTED → ENRICHED → INDEXED)

Future Graph RAG fields (not in MVP):
- ConceptNode (id, name, type)
- ArticleConcept (articleId, conceptId, relationType, confidence)

See `docs/decisions/0002-product-scope-and-graph-rag-strategy.md` for Graph RAG direction.

## Common Issues & Decisions

**Why separate FastAPI from Spring Boot?**
Spring Boot owns user-facing APIs and orchestration. FastAPI handles AI/enrichment only. This keeps service boundaries clear and avoids overengineering the main backend.

**Why no semantic search yet?**
MVP prioritizes a working product. Search response shape is stable; backend can evolve from keyword → semantic → graph-aware without frontend changes.

**Why mock data initially?**
Proves product structure without external dependencies. RSS/API collection comes after MVP is stable.

**Why Zod validation on frontend?**
Runtime validation ensures components receive expected shapes, even if backend changes drift from documentation.
