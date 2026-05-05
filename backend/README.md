# Backend

Spring Boot with Kotlin is the main application backend for Sigak.

## Responsibilities
- Public REST APIs for the frontend
- Business logic
- Persistence when the database is added
- Calling the AI server for AI-specific features

## Current Status
The backend exposes mock news article APIs:

```http
GET /api/articles
GET /api/articles?query={query}
GET /api/articles/{id}
```

Mock article responses include product-planning fields such as `eventType`, `primaryCategory`, `topics`, `summary`, `whyItMatters`, `importanceScore`, and `relatedArticleIds`.

Keyword search is currently backed by the in-memory mock article list. Database, Elasticsearch, vector search, and AI integration are not implemented yet.

The local Vite frontend origins `http://localhost:5173` and `http://127.0.0.1:5173` are allowed for `/api/**` CORS requests.

## Run Locally
Requirements:
- Java 17

From this directory:

```bash
./gradlew bootRun
```

Then open:

```txt
http://localhost:8080/api/articles
http://localhost:8080/api/articles?query=rag
```

## API Documentation
Swagger UI is available locally after starting the backend:

```txt
http://localhost:8080/swagger-ui/index.html
```

The generated OpenAPI JSON is available at:

```txt
http://localhost:8080/v3/api-docs
```

## Test
From this directory:

```bash
./gradlew test
```

## Collection Foundation

The backend owns source registry, collector parsing, normalization, and collection orchestration.

Current Phase 5 collection boundaries:
- RSS/Atom source registry entries for selected official technical sources
- arXiv API source registry entries for selected research categories
- parser tests using local XML fixtures
- local mock enrichment client

The public article API remains backed by curated mock data until persistence is added.
