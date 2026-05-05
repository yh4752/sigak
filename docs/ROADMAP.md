# Roadmap

## Phase 0: Project Guidance
- Create project guidance documents.
- Record the initial architecture decision.
- Define MVP scope and non-goals.

## Phase 1: Minimal Repository Structure
- Add `backend/`, `frontend/`, `ai/`, and `infra/`.
- Add basic README files for each area.
- Add `.env.example`, `.gitignore`, and a Docker Compose placeholder.

## Phase 2: Backend MVP Foundation

### Completed
- Create the Kotlin Spring Boot backend structure.
- Add mock news article list API.
- Expand article response fields toward the product model:
  - event type
  - primary category
  - topics
  - summary
  - why it matters
  - importance score or level
  - related article IDs
- Add article detail API.
- Add curated seed data that covers the initial event types and categories.
- Add tests for important API and service behavior.
- Add keyword search support on the existing article list API.
- Add generated OpenAPI documentation with Swagger UI.
- Keep `docs/API_SPEC.md` aligned with backend API changes.
- Keep mock data small, curated, and easy to replace with persistence later.

## Phase 3: Search MVP
- Keep keyword search API behavior stable.
- Search over title, summary, primary category, and topics.
- Keep the search API stable so it can later use semantic and graph-aware retrieval.
- Treat Elasticsearch as an implementation candidate for a later search stage, not a prerequisite for the first search API.

## Phase 4: Frontend MVP Foundation

### Completed
- Create React + TypeScript + Vite frontend.
- Use Axios for backend HTTP calls.
- Use Zod to validate backend API responses at the API client boundary.
- Add a minimal home screen with centered search, today's important news, and popular news.
- Add article list/search result cards.
- Keep API calls in client modules.
- Add explicit local CORS support for the Vite frontend.

### Next
- Add article detail screen with summary, importance, why it matters, topics, and related items.
- Improve loading, empty, and error states as frontend behavior grows.
- Keep UI simple and portfolio-ready.

## Phase 5: AI Summary MVP
- Create FastAPI AI service.
- Add mock summary and insight endpoints first.
- Connect Spring Boot to FastAPI for article summary or insight requests.
- Keep local development usable without paid API keys.

## Phase 6: Persistence MVP
- Add a relational database when mock data no longer fits the workflow.
- Preserve raw article text and source metadata for future reprocessing.
- Keep enriched fields separate enough to regenerate later.
- Continue using Docker Compose for local development.

## Phase 7: Collection and Enrichment Pipeline
- Add selected RSS/API collection after the curated seed dataset proves the product shape.
- Use a processing flow that can evolve toward:
  - `COLLECTED`
  - `EXTRACTED`
  - `ENRICHED`
  - `INDEXED`
- Add source selection rules before broad automated collection.
- Avoid re-scraping saved articles when adding semantic search or Graph RAG.

## Phase 8: Limited Graph RAG Insight
- Add graph-ready concept and relationship data.
- Add relationship-aware article insights and related concepts.
- Add limited graph-backed retrieval or explanation.
- Treat Qdrant as an implementation candidate for vector retrieval when this phase needs embeddings.
- Defer Obsidian-style full graph exploration until after 1.0.0.

## Phase 9: Local Development and Polish
- Expand Docker Compose for local services.
- Improve README run instructions.
- Add `.env.example` values for each service.
- Document remaining architecture decisions.

## Later Enhancements
- Elasticsearch-backed keyword search
- Qdrant-backed vector search
- Hybrid search
- Full Obsidian-style graph explorer
- Broader RAG over multiple articles
- Personalized recommendations
- User accounts and saved articles
