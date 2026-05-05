# Sigak

Sigak is an AI-powered technical news insight platform for AI, software development, and computer science.

The initial MVP focuses on helping users find important technical changes, understand why they matter, and explore related concepts through a simple, deployable architecture.

## Architecture
- `backend/`: Spring Boot main backend and REST API.
- `frontend/`: React + TypeScript + Vite frontend.
- `ai/`: FastAPI service for AI/RAG-related capabilities.
- `infra/`: Docker Compose and local development infrastructure.
- `docs/`: project context, roadmap, and architecture decisions.

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
The project has initial guidance docs, a minimal monorepo structure, mock backend article APIs, generated Swagger/OpenAPI documentation, and a first frontend home/search screen.

```http
GET /api/articles
GET /api/articles?query={query}
GET /api/articles/{id}
```

The current keyword search is backed by mock data. The frontend calls the backend through an Axios API client and validates article responses with Zod. Search engines, vector databases, persistence, article detail UI, and external AI APIs are planned but not implemented yet.

## Run Locally
Start the backend:

```bash
cd backend
./gradlew bootRun
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
│   └── decisions/
├── frontend/
├── infra/
│   └── docker-compose.yml
├── AGENTS.md
├── README.md
└── .env.example
```

## Documentation
- [Project Context](docs/PROJECT_CONTEXT.md)
- [Product Plan](docs/PRODUCT_PLAN.md)
- [API Spec](docs/API_SPEC.md)
- [Source Policy](docs/SOURCE_POLICY.md)
- [Roadmap](docs/ROADMAP.md)
- [Initial Architecture ADR](docs/decisions/0001-initial-architecture.md)
- [Product Scope and Graph RAG Strategy ADR](docs/decisions/0002-product-scope-and-graph-rag-strategy.md)
