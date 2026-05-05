# Roadmap

This roadmap tracks the current MVP implementation status.

## Status Legend
- [x] Implemented
- [ ] Not implemented yet

## Phase 0: Project Guidance
- [x] Create project guidance documents.
- [x] Record the initial architecture decision.
- [x] Define MVP scope and non-goals.
- [x] Record the product scope and Graph RAG strategy decision.

## Phase 1: Minimal Repository Structure
- [x] Add `backend/`, `frontend/`, `ai/`, and `infra/`.
- [x] Add basic README files for each area.
- [x] Add `.env.example`.
- [x] Add `.gitignore`.
- [x] Add a Docker Compose placeholder.

## Phase 2: Backend MVP Foundation
- [x] Create the Kotlin Spring Boot backend structure.
- [x] Add mock news article list API.
- [x] Expand article response fields toward the product model:
  - [x] event type
  - [x] primary category
  - [x] topics
  - [x] summary
  - [x] why it matters
  - [x] importance score
  - [x] related article IDs
- [x] Add article detail API.
- [x] Add curated seed data that covers the initial event types and categories.
- [x] Add tests for important API and service behavior.
- [x] Add keyword search support on the existing article list API.
- [x] Add generated OpenAPI documentation with Swagger UI.
- [x] Keep `docs/API_SPEC.md` aligned with backend API changes.
- [x] Keep mock data small, curated, and easy to replace with persistence later.

## Phase 3: Search MVP
- [x] Keep keyword search API behavior stable.
- [x] Search over title, summary, primary category, and topics.
- [x] Keep the search API response shape stable so it can later use semantic and graph-aware retrieval.
- [x] Treat Elasticsearch as an implementation candidate for a later search stage, not a prerequisite for the first search API.
- [ ] Add Elasticsearch-backed keyword search when the MVP needs real indexing.

## Phase 4: Frontend MVP Foundation
- [x] Create React + TypeScript + Vite frontend.
- [x] Add React Router routes for home and article detail pages.
- [x] Use Axios for backend HTTP calls.
- [x] Use Zod to validate backend API responses at the API client boundary.
- [x] Add a minimal home screen with centered search, today's important news, and popular news.
- [x] Add article list/search result cards.
- [x] Keep API calls in client modules.
- [x] Add explicit local CORS support for the Vite frontend.
- [x] Add article detail screen with summary, why it matters, topics, source link, and related items.
- [x] Add frontend tests for the API client, home UI, navigation, article cards, and article detail page.
- [x] Add basic loading, empty, and error states for the current article list/search/detail flows.
- [x] Intentionally keep the raw importance score out of the article detail UI and use it for ranking.
- [x] Keep the current UI simple and portfolio-ready for the Phase 4 MVP foundation.
- [x] Improve loading, empty, and error states for the current frontend MVP flows.

## Phase 5: AI Summary MVP
- [ ] Create FastAPI AI service structure.
- [ ] Add mock summary and insight endpoints first.
- [ ] Add simple smoke tests for AI server endpoints.
- [ ] Connect Spring Boot to FastAPI for article summary or insight requests.
- [ ] Keep local development usable without paid API keys.
- [ ] Add AI server environment variables to `.env.example` when needed.

## Phase 6: Persistence MVP
- [ ] Add a relational database when mock data no longer fits the workflow.
- [ ] Add JPA entities, repositories, and service logic for persisted articles.
- [ ] Preserve raw article text and source metadata for future reprocessing.
- [ ] Keep enriched fields separate enough to regenerate later.
- [ ] Continue using Docker Compose for local development.

## Phase 7: Collection and Enrichment Pipeline
- [ ] Add selected RSS/API collection after the curated seed dataset proves the product shape.
- [ ] Use a processing flow that can evolve toward:
  - [ ] `COLLECTED`
  - [ ] `EXTRACTED`
  - [ ] `ENRICHED`
  - [ ] `INDEXED`
- [ ] Add source selection rules before broad automated collection.
- [ ] Avoid re-scraping saved articles when adding semantic search or Graph RAG.

## Phase 8: Limited Graph RAG Insight
- [x] Add graph-ready article metadata fields to the MVP article response.
- [x] Add basic related-article ID metadata to curated articles.
- [ ] Add explicit concept and relationship data.
- [ ] Add relationship-aware article insights and related concepts.
- [ ] Add limited graph-backed retrieval or explanation.
- [ ] Treat Qdrant as an implementation candidate for vector retrieval when this phase needs embeddings.
- [ ] Defer Obsidian-style full graph exploration until after 1.0.0.

## Phase 9: Local Development and Polish
- [x] Add root-level local environment example values.
- [x] Add backend and frontend local run instructions.
- [ ] Expand Docker Compose for runnable local services.
- [x] Improve root `README.md` current-status notes so they match the implemented article detail UI.
- [x] Improve `frontend/README.md` current-status notes so they mention the article detail UI.
- [ ] Add `.env.example` values for each service as service needs become concrete.
- [ ] Document remaining architecture decisions.

## Later Enhancements
- [ ] Elasticsearch-backed keyword search
- [ ] Qdrant-backed vector search
- [ ] Hybrid search
- [ ] Full Obsidian-style graph explorer
- [ ] Broader RAG over multiple articles
- [ ] Personalized recommendations
- [ ] User accounts and saved articles
