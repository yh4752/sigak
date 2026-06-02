# Sigak

[English](README.md) | [한국어](README.ko.md)

Sigak is an AI-powered technical news insight platform for AI, software development, and computer science.

The initial MVP focuses on helping users find important technical changes, understand why they matter, and explore related concepts through a simple, deployable architecture.

## Architecture
- `backend/`: Spring Boot main backend and REST API.
- `frontend/`: React + TypeScript + Vite frontend.
- `ai/`: FastAPI service for AI/RAG-related capabilities.
- `infra/`: Docker Compose and local development infrastructure.
- `docs/`: product, roadmap, API, research strategy, status, and architecture decisions.

Spring Boot is the primary API boundary. The frontend should call Spring Boot first, and Spring Boot may call FastAPI for AI-specific features.

## MVP Scope
- News article list
- News article detail
- Keyword search
- AI summary for a selected article
- Importance and why-it-matters insight
- Graph RAG-ready article metadata
- Simple frontend UI
- Docker Compose local setup

## Current Status
The project has initial guidance docs, a minimal monorepo structure, PostgreSQL-backed backend article APIs, generated Swagger/OpenAPI documentation, and a first frontend home/search/detail flow.

The selected-source collection pipeline now parses RSS/Atom and arXiv feeds, applies mock enrichment, deduplicates collected items, and persists API-ready articles with raw content and current enrichment records. Public article APIs expose only published articles that have current enrichment.

```http
GET /api/articles
GET /api/articles?query={query}
GET /api/articles/{id}
```

Public search now uses Elasticsearch keyword candidates and Qdrant vector candidates for non-blank queries, fuses them with reciprocal rank fusion, and reloads final responses from PostgreSQL. If both projection paths fail, the API falls back to PostgreSQL field filtering. Internal collection runs, command-runner collection, and failure-event diagnostics are available for local MVP operations. The frontend calls the backend through an Axios API client and validates article responses with Zod. Article detail pages show the core insight fields without exposing the raw importance score; the score is currently used for ranking. FastAPI provides mock enrichment and configurable embedding providers. Scheduled collection, graph-backed retrieval, FastAPI HTTP enrichment mode, and external AI APIs remain planned later enhancements.

## Run Locally
From the repository root, start PostgreSQL, search infrastructure, and the AI server:

```bash
docker compose -f infra/docker-compose.yml up -d postgres elasticsearch qdrant ai
```

Start the backend:

```bash
cd backend
./gradlew bootRun
```

After the backend starts, rebuild the article search projection from another terminal:

```bash
curl -X POST http://localhost:8080/api/internal/search-projections/articles/rebuild
```

For internal Qdrant vector search, rebuild the vector projection and run a semantic query:

```bash
curl -X POST http://localhost:8080/api/internal/search-projections/article-vectors/rebuild
curl -X POST http://localhost:8080/api/internal/vector-search/articles \
  -H 'Content-Type: application/json' \
  -d '{"query":"AI supply chain security risk","limit":10}'
curl http://localhost:8080/api/internal/search-metrics/article-vectors
```

Start the frontend in another terminal:

```bash
cd frontend
npm install
npm run dev
```

Local URLs:

```txt
http://localhost:5173
http://localhost:8080/swagger-ui/index.html
http://localhost:8080/api/articles
```

## Project Structure
```txt
sigak/
├── ai/
├── backend/
├── docs/
│   ├── PRODUCT.md
│   ├── ROADMAP.md
│   ├── STATUS.md
│   ├── RESEARCH_STRATEGY.md
│   ├── ko/
│   └── decisions/
├── frontend/
├── infra/
│   └── docker-compose.yml
├── AGENTS.md
├── README.md
└── .env.example
```

## Documentation
- [Documentation Index](docs/README.md)
- [Product](docs/PRODUCT.md)
- [Roadmap](docs/ROADMAP.md)
- [Status](docs/STATUS.md)
- [Local Demo Flow](docs/DEMO_FLOW.md)
- [Research Strategy](docs/RESEARCH_STRATEGY.md)
- [API Spec](docs/API_SPEC.md)
- [Source Policy](docs/SOURCE_POLICY.md)
- [Korean Guide](docs/ko/GUIDE.md)
- [Initial Architecture ADR](docs/decisions/0001-initial-architecture.md)
- [Product Scope and Graph RAG Strategy ADR](docs/decisions/0002-product-scope-and-graph-rag-strategy.md)
