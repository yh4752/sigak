# API Spec

[English](API_SPEC.md) | [한국어](API_SPEC.ko.md)

## Purpose
This document describes the current MVP API contract for Sigak.

The API should stay simple while keeping the response shape compatible with later search, enrichment, and Graph RAG features.

## Current Endpoints

```http
GET /api/articles
GET /api/articles?query={query}
GET /api/articles/{id}
GET /api/internal/search-metrics/articles
POST /api/internal/search-projections/article-vectors/rebuild
POST /api/internal/vector-search/articles
GET /api/internal/search-metrics/article-vectors
GET /v3/api-docs
GET /swagger-ui/index.html
```

Search results use the same response shape as `GET /api/articles`.

## Generated API Documentation

The backend uses `springdoc-openapi` to generate OpenAPI documentation from the Spring MVC controllers and DTO schemas.

Local documentation URLs:

```txt
http://localhost:8080/swagger-ui/index.html
http://localhost:8080/v3/api-docs
```

`docs/API_SPEC.md` remains the human-readable API design reference. Swagger UI and `/v3/api-docs` are the executable API documentation generated from backend code.

## Article Response

`GET /api/articles` returns a JSON array of article responses.

`GET /api/articles/{id}` returns one article response.

```json
{
  "id": 1,
  "title": "OpenAI Releases Agent Evaluation Toolkit",
  "source": "OpenAI",
  "url": "https://example.com/articles/openai-agent-evals",
  "publishedAt": "2026-05-01T09:00:00Z",
  "eventType": "OFFICIAL_ANNOUNCEMENT",
  "primaryCategory": "AI",
  "topics": ["LLM agents", "evaluation", "production AI"],
  "summary": "OpenAI introduced a toolkit for evaluating agent behavior in multi-step workflows.",
  "whyItMatters": "Agent evaluation is becoming a practical requirement as teams move from demos to production workflows.",
  "importanceScore": 88,
  "relatedArticleIds": [3, 5]
}
```

## Article Fields

| Field | Type | Description |
| --- | --- | --- |
| `id` | Long | Positive article identifier. Stable within the current dataset. |
| `title` | String | Non-empty display title. |
| `source` | String | Non-empty human-readable source name. |
| `url` | String | Non-empty original article URL. Unique in persisted article data. |
| `publishedAt` | String | ISO-8601 UTC timestamp, such as `2026-05-01T09:00:00Z`. |
| `eventType` | String enum | Type of technical event. |
| `primaryCategory` | String enum | Main technical category for navigation and filtering. |
| `topics` | String array | Detailed technical concepts. 1-8 items recommended. |
| `summary` | String | Short factual summary without hype. |
| `whyItMatters` | String | Beginner-friendly but technically meaningful explanation of importance. |
| `importanceScore` | Integer | Curated importance score from 0 to 100. |
| `relatedArticleIds` | Long array | Related article IDs. Can be empty. Should refer to existing articles when possible. |

## Event Types

Allowed `eventType` values:

- `NEWS`
- `OFFICIAL_ANNOUNCEMENT`
- `RESEARCH`
- `SECURITY`
- `RELEASE`

## Primary Categories

Allowed `primaryCategory` values:

- `AI`
- `SECURITY`
- `SOFTWARE_ENGINEERING`
- `BACKEND`
- `FRONTEND`
- `DATA`
- `INFRA_CLOUD`
- `DEVTOOLS`
- `CS_RESEARCH`

## Importance Score

`importanceScore` is a 0-100 integer.

For MVP seed data, the score is manually curated.

| Range | Level | Meaning |
| --- | --- | --- |
| `90-100` | Critical | Major security issues, major AI/CS changes, or very important official announcements that can affect the technical ecosystem. |
| `75-89` | High | Important changes many developers or teams should know about. |
| `50-74` | Medium | Meaningful changes for a specific technical audience. |
| `0-49` | Low | Recordable items that should not usually appear in the main important feed. |

Later AI enrichment may suggest scores, but the final stored score can be manually reviewed or adjusted by rules.

For the current MVP UI, `importanceScore` is used as a ranking and curation signal. The article detail page does not need to expose the raw numeric score; importance should be explained through `whyItMatters` unless a later product decision introduces a qualitative label.

## Search Behavior

Hybrid search:

```http
GET /api/articles?query=rag
```

For non-blank `query` values, the backend uses Elasticsearch for keyword candidates and Qdrant for vector candidates. The candidate lists are fused with reciprocal rank fusion (RRF), and Spring Boot reloads API-ready article responses from PostgreSQL. PostgreSQL remains the source of truth for public response data.

Behavior details:
- leading and trailing whitespace is ignored before search
- same response shape as `GET /api/articles`
- keyword candidates search the Elasticsearch projection:
  - `title`
  - `summary`
  - `topics`
  - `primaryCategory`
  - `whyItMatters`
- vector candidates search the Qdrant article vector projection built from title, summary, why-it-matters, category, topics, and event type
- if one projection path fails, the other path can serve `KEYWORD_ONLY` or `VECTOR_ONLY` results
- if both projection paths fail, Spring Boot falls back to PostgreSQL field filtering over:
  - `title`
  - `summary`
  - `primaryCategory`
  - `topics`
- fallback search uses case-insensitive keyword matching
- search metrics are recorded internally as mode, candidate counts, stale candidate count, failure flags, fallback reason, and latency breakdowns

Graph-aware retrieval is a later enhancement. The public search response shape should remain stable when the backend implementation evolves.

## Internal Article Search Metrics Contract

Public article responses do not include performance metadata. Local search metrics are exposed through an internal endpoint so development and smoke tests can inspect search latency and fallback behavior without changing the user-facing API contract.

```http
GET /api/internal/search-metrics/articles
```

Expected response:

```json
{
  "totalSearchCount": 2,
  "hybridSearchCount": 1,
  "keywordOnlySearchCount": 0,
  "vectorOnlySearchCount": 0,
  "postgresFallbackSearchCount": 1,
  "fallbackRate": 0.5,
  "averageTotalElapsedMs": 30.0,
  "p50TotalElapsedMs": 20,
  "p95TotalElapsedMs": 40,
  "lastSearch": {
    "queryLength": 6,
    "resultCount": 2,
    "mode": "POSTGRES_FALLBACK",
    "keywordCandidateCount": 0,
    "vectorCandidateCount": 0,
    "fusedCandidateCount": 0,
    "staleCandidateCount": 0,
    "keywordFailed": true,
    "vectorFailed": true,
    "fallbackReason": "KEYWORD_SEARCH_FAILED; QDRANT_SEARCH_FAILED",
    "keywordElapsedMs": 2,
    "embeddingElapsedMs": 0,
    "vectorElapsedMs": 2,
    "fusionElapsedMs": 0,
    "articleReloadElapsedMs": 4,
    "totalElapsedMs": 40
  }
}
```

Notes:
- metrics are in-memory and reset when the backend process restarts
- raw query text is not stored; only query length is recorded
- this is a local MVP metric boundary, not a production observability stack

## Internal Search Projection Contract

The public article API remains stable while search projection stores are rebuilt from PostgreSQL.

```http
POST /api/internal/search-projections/articles/rebuild
```

Expected response:

```json
{
  "status": "completed",
  "indexName": "sigak-articles-v1",
  "indexedCount": 5,
  "durationMs": 42,
  "failedReason": null
}
```

The rebuild operation reads API-ready articles from PostgreSQL and indexes them into Elasticsearch. PostgreSQL remains the source of truth; Elasticsearch is a rebuildable projection store.

Local smoke test:

```bash
docker compose -f infra/docker-compose.yml up -d postgres elasticsearch
cd backend
./gradlew bootRun
curl -X POST http://localhost:8080/api/internal/search-projections/articles/rebuild
curl http://localhost:9200/sigak-articles-v1/_count
curl "http://localhost:8080/api/articles?query=graph"
curl http://localhost:8080/api/internal/search-metrics/articles
```

Fallback smoke test:

```bash
docker compose -f infra/docker-compose.yml stop elasticsearch
curl "http://localhost:8080/api/articles?query=graph"
docker compose -f infra/docker-compose.yml up -d elasticsearch
```

The fallback request should still return matching articles, and the backend log should include `fallback=true`.

## Internal Article Vector Projection Contract

Qdrant stores article embeddings as a rebuildable projection. PostgreSQL remains the source of truth for article response data, and FastAPI remains the embedding provider boundary.

```http
POST /api/internal/search-projections/article-vectors/rebuild
```

Expected response:

```json
{
  "status": "completed",
  "collectionName": "sigak-article-vectors-minilm-v1",
  "indexedCount": 5,
  "embeddingProvider": "local",
  "embeddingModelName": "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
  "embeddingDimension": 384,
  "durationMs": 1420,
  "failedReason": null
}
```

Behavior details:
- reads API-ready articles from PostgreSQL
- builds embedding input from title, summary, why-it-matters, category, topics, and event type
- calls FastAPI `POST /api/embeddings/text`
- recreates the configured Qdrant article vector collection
- stores article ID, vector, and debugging payload metadata
- fails the rebuild if embedding provider, model name, or dimension changes within one run
- fails before recreating the collection if an embedding vector length does not match its declared dimension

## Internal Article Vector Search Contract

Internal vector search embeds a query, searches Qdrant, and reloads final article responses from PostgreSQL. Public `GET /api/articles?query=...` now uses the same lower-level vector candidate boundary as part of hybrid search, while this endpoint remains a diagnostics API with scores and timing details.

```http
POST /api/internal/vector-search/articles
```

Request:

```json
{
  "query": "AI supply chain security risk",
  "limit": 10
}
```

Expected response:

```json
{
  "query": "AI supply chain security risk",
  "collectionName": "sigak-article-vectors-minilm-v1",
  "embeddingProvider": "local",
  "embeddingModelName": "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
  "embeddingDimension": 384,
  "results": [
    {
      "score": 0.91,
      "article": {
        "id": 3,
        "title": "Critical Package Registry Attack Targets AI Toolchains",
        "source": "Security Advisory Board",
        "url": "https://example.com/articles/ai-toolchain-package-attack",
        "publishedAt": "2026-05-03T15:45:00Z",
        "eventType": "SECURITY",
        "primaryCategory": "SECURITY",
        "topics": ["supply chain security", "AI tooling"],
        "summary": "A coordinated package registry attack targeted developer environments.",
        "whyItMatters": "AI development stacks combine packages, credentials, and automation.",
        "importanceScore": 93,
        "relatedArticleIds": [1, 5]
      }
    }
  ],
  "timings": {
    "embeddingElapsedMs": 24,
    "qdrantElapsedMs": 8,
    "articleLoadElapsedMs": 5,
    "totalElapsedMs": 37
  }
}
```

Behavior details:
- `query` is trimmed and blank queries are rejected
- `limit` defaults to the configured Qdrant default limit and is capped by the configured max limit
- Qdrant returns article IDs and scores, and Spring Boot reloads final article responses from PostgreSQL
- stale Qdrant hits are omitted when PostgreSQL no longer exposes the article as API-ready
- duplicate Qdrant article hits keep the first score in ranked order

## Internal Article Vector Search Metrics Contract

```http
GET /api/internal/search-metrics/article-vectors
```

Expected response:

```json
{
  "totalSearchCount": 2,
  "averageTotalElapsedMs": 15.0,
  "p50TotalElapsedMs": 10,
  "p95TotalElapsedMs": 20,
  "averageEmbeddingElapsedMs": 6.0,
  "averageQdrantElapsedMs": 5.0,
  "averageArticleLoadElapsedMs": 4.0,
  "lastSearch": {
    "queryLength": 12,
    "resultCount": 3,
    "embeddingElapsedMs": 8,
    "qdrantElapsedMs": 7,
    "articleLoadElapsedMs": 5,
    "totalElapsedMs": 20
  }
}
```

Notes:
- metrics are in-memory and reset when the backend process restarts
- only successful vector searches are recorded
- raw query text is not stored; only query length is recorded

## Error Behavior

Unknown article IDs return `404 Not Found`.

```http
GET /api/articles/999
```

```http
HTTP/1.1 404 Not Found
```

The exact error response body is not part of the current MVP contract.

## Internal Embedding Contract

The AI server exposes an embedding endpoint for vector projection development. The implementation supports deterministic test mode and local multilingual FastEmbed mode so Spring Boot -> FastAPI -> Qdrant wiring can be tested without paid API keys while still supporting a real semantic retrieval path.

The target v0.1 retrieval path should use a real multilingual embedding model because article sources may include Korean, English, and other languages. Deterministic embedding remains useful as fallback/test mode, but it should not be used as evidence of semantic search quality. The first real model path uses FastEmbed because it provides local ONNX Runtime-based embeddings without pulling heavy PyTorch/CUDA dependencies.

```http
POST /api/embeddings/text
```

Request:

```json
{
  "text": "Graph RAG improves relationship-aware retrieval."
}
```

Deterministic response example:

```json
{
  "provider": "deterministic",
  "modelName": "sigak-deterministic-hash-v1",
  "dimension": 8,
  "embedding": [0.123456, -0.234567, 0.345678, -0.456789, 0.567891, -0.678912, 0.789123, -0.891234]
}
```

Local model response example:

```json
{
  "provider": "local",
  "modelName": "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
  "dimension": 384,
  "embedding": [0.012345, -0.023456, 0.034567]
}
```

Behavior details:
- the same text always returns the same vector
- whitespace-only text is rejected
- deterministic mode uses a small 8-dimensional vector for MVP local smoke tests
- local model mode uses the configured model dimension
- Spring Boot calls this endpoint through an internal embedding client boundary
- Qdrant projection code should treat this as a replaceable embedding boundary
- real embedding mode should record the model/provider name and vector dimension with indexed projection metadata

Spring Boot configuration:

```txt
SIGAK_AI_SERVER_URL=http://localhost:8000
SIGAK_EMBEDDING_PROVIDER=local
SIGAK_EMBEDDING_MODEL_NAME=sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2
```

## Internal AI Enrichment Contract

The public article API remains stable. Internally, collected articles are normalized before AI enrichment.

The current backend uses the same request/response shape for local mock enrichment. When Spring Boot is wired to FastAPI over HTTP, this is the intended internal contract:

```http
POST /api/enrichment/article
```

```json
{
  "title": "Evaluating Retrieval Agents",
  "source": "arXiv cs.AI",
  "url": "http://arxiv.org/abs/2605.00001v1",
  "publishedAt": "2026-05-05T00:00:00Z",
  "topics": ["CS_RESEARCH"],
  "rawContent": "We study retrieval agents in technical knowledge workflows."
}
```

The AI service returns enrichment candidates:

```json
{
  "summary": "Evaluating Retrieval Agents discusses We study retrieval agents in technical knowledge workflows.",
  "whyItMatters": "This matters because arXiv cs.AI is connected to CS_RESEARCH and may affect how technical teams understand the topic.",
  "suggestedTopics": ["CS_RESEARCH"],
  "suggestedPrimaryCategory": "CS_RESEARCH",
  "suggestedImportanceScore": 70
}
```
