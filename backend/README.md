# Backend

[English](README.md) | [한국어](README.ko.md)

Spring Boot with Kotlin is the main application backend for Sigak.

## Responsibilities
- Public REST APIs for the frontend
- Business logic
- Persistence for article data
- Calling the AI server for AI-specific features

## Current Status
The backend exposes PostgreSQL-backed news article APIs:

```http
GET /api/articles
GET /api/articles?query={query}
GET /api/articles/{id}
```

Article responses include product-planning fields such as `eventType`, `primaryCategory`, `topics`, `summary`, `whyItMatters`, `importanceScore`, and `relatedArticleIds`.

Keyword search currently runs through the backend service over persisted article fields. Elasticsearch indexing, vector search, and external AI integration are not implemented yet.

The backend now has a lightweight readiness boundary for local search infrastructure:

```http
GET /api/internal/search-infrastructure/health
```

This endpoint checks whether Elasticsearch, Qdrant, and Neo4j are reachable. It is an internal smoke-check endpoint for local development before article indexing and hybrid search are connected.

The local Vite frontend origins `http://localhost:5173` and `http://127.0.0.1:5173` are allowed for `/api/**` CORS requests.

## Run Locally
Requirements:
- Java 17
- Docker
- PostgreSQL from `infra/docker-compose.yml`
- Optional search infrastructure from `infra/docker-compose.yml`

From this directory, start the database:

```bash
docker compose -f ../infra/docker-compose.yml up -d postgres
```

To check all local search projection stores, start the search infrastructure too:

```bash
docker compose -f ../infra/docker-compose.yml up -d elasticsearch qdrant neo4j
```

Then start the backend:

```bash
./gradlew bootRun
```

Then open:

```txt
http://localhost:8080/api/articles
http://localhost:8080/api/articles?query=rag
http://localhost:8080/api/internal/search-infrastructure/health
```

Search infrastructure environment values:

```txt
SIGAK_ELASTICSEARCH_URL=http://localhost:9200
SIGAK_QDRANT_URL=http://localhost:6333
SIGAK_NEO4J_URI=bolt://localhost:7687
SIGAK_NEO4J_USERNAME=neo4j
SIGAK_NEO4J_PASSWORD=sigak-neo4j-password
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

## Collection Pipeline

The backend owns source registry, source fetch execution, collector parsing, normalization, mock enrichment, and persistence.

Current collection capabilities:
- RSS/Atom source registry entries for selected official technical sources
- arXiv API source registry entries for selected research categories
- parser tests using local XML fixtures
- local mock enrichment client
- source execution service that fetches XML, parses articles, enriches them, and publishes persisted article records
- duplicate detection by canonical URL, source external ID, and source/title/published date
- separate persistence for raw collected content and current enrichment output

The default local path still uses mock enrichment, so backend development does not require paid AI API keys. The public article API remains stable while collected articles can be published into the same persisted article model used by `/api/articles`.
