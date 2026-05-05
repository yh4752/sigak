# API Spec

## Purpose
This document describes the current MVP API contract for Sigak.

The API should stay simple while keeping the response shape compatible with later search, enrichment, and Graph RAG features.

## Current Endpoints

```http
GET /api/articles
GET /api/articles?query={query}
GET /api/articles/{id}
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
| `url` | String | Non-empty original article URL. Should be unique when persistence is added. |
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

Keyword search:

```http
GET /api/articles?query=rag
```

Current search behavior:
- case-insensitive keyword matching
- leading and trailing whitespace is ignored
- same response shape as `GET /api/articles`
- search targets:
  - `title`
  - `summary`
  - `primaryCategory`
  - `topics`

Semantic search and graph-aware retrieval are later enhancements. The search response shape should remain stable when the backend implementation evolves.

## Error Behavior

Unknown article IDs return `404 Not Found`.

```http
GET /api/articles/999
```

```http
HTTP/1.1 404 Not Found
```

The exact error response body is not part of the current MVP contract.
