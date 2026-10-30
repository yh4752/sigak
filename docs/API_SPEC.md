# API Spec

[English](API_SPEC.md) | [한국어](API_SPEC.ko.md)

## Purpose
This document describes the current MVP API contract for Sigak.

The API should stay simple while keeping the response shape compatible with later search, enrichment, and Graph RAG features.

## Current Endpoints

```http
GET /api/articles
GET /api/articles?query={query}
GET /api/articles?ids={id1},{id2}
GET /api/articles/{id}
GET /api/articles/{id}/graph-context
GET /api/research/overview
POST /api/internal/collections/runs
GET /api/internal/collections/failure-events
GET /api/internal/search-metrics/articles
POST /api/internal/search-evaluation/retrieval-runs
POST /api/internal/graph-projections/articles/rebuild
GET /api/internal/graph/articles/{id}/context
POST /api/internal/search-projections/article-vectors/rebuild
POST /api/internal/vector-search/articles
GET /api/internal/search-metrics/article-vectors
GET /v3/api-docs
GET /swagger-ui/index.html
```

Search and bulk-ID results use the same response shape as `GET /api/articles`.

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

`GET /api/articles?ids=1,2,3` returns API-ready articles matching the requested IDs, preserving the requested order after duplicate IDs and missing/non-public IDs are omitted.

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
| `summary` | String or null | Stored summary; null until requested and generated. Mock collection summaries are not exposed as real AI output. |
| `whyItMatters` | String or null | Stored importance explanation; null when not generated. |
| `importanceScore` | Integer | Curated importance score from 0 to 100. |
| `relatedArticleIds` | Long array | Related article IDs. Can be empty. Should refer to existing articles when possible. |
| `sourceExcerpt` | String | Plain-text collected source excerpt, bounded to 1200 characters. |
| `summaryStatus` | String | Article snapshot: READY or NOT_REQUESTED. Use the summary endpoint for pending/failure details. |
| `summaryProvider` | String or null | Stored model name or curated-seed provenance. |

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
- vector candidates search the Qdrant article vector projection built from title, source excerpt, category, topics, and event type
- if one projection path fails, the other path can serve `KEYWORD_ONLY` or `VECTOR_ONLY` results
- if both projection paths fail, Spring Boot falls back to PostgreSQL field filtering over:
  - `title`
  - `sourceExcerpt`
  - `primaryCategory`
  - `topics`
- fallback search uses case-insensitive keyword matching
- search metrics are recorded internally as mode, candidate counts, stale candidate count, failure flags, fallback reason, and latency breakdowns

Graph-aware retrieval is a later enhancement. The public search response shape should remain stable when the backend implementation evolves.

## Bulk Article Lookup

The frontend uses bulk lookup for related articles so article detail does not issue one HTTP request per related ID.

```http
GET /api/articles?ids=4,1,4,999
```

Behavior details:
- IDs may be provided as a comma-separated query parameter
- duplicate IDs are deduplicated while preserving first-seen request order
- unknown, draft, or otherwise non-public IDs are omitted
- if `query` and `ids` are both present, `ids` lookup takes precedence because it is an explicit article reload path
- response shape is the same article response array used by list and search

## Public Article Graph Context

The article detail page uses a companion graph-context endpoint to display why related articles are connected. This keeps the shared `ArticleResponse` shape stable for list, search, detail, and bulk article lookup.

```http
GET /api/articles/{id}/graph-context
```

Expected response:

```json
{
  "articleId": 4,
  "relatedArticleReasons": [
    {
      "articleId": 1,
      "reason": "Graph RAG evaluation connects to agent and retrieval evaluation.",
      "sharedTopics": ["evaluation"]
    }
  ],
  "topics": [
    {
      "name": "graph rag",
      "displayName": "Graph RAG",
      "relatedArticleIds": [1]
    }
  ]
}
```

Behavior details:
- PostgreSQL remains the source of truth for whether the article is public and API-ready.
- unknown, draft, or otherwise non-public article IDs return `404`.
- non-positive article IDs return `400`.
- Neo4j context is optional projection data; missing or unavailable graph context returns the same response shape with empty `relatedArticleReasons` and `topics`.
- public graph context responses do not include internal timings, Neo4j errors, stack traces, or internal relation type.
- the frontend joins `relatedArticleReasons` to already-loaded related articles by `articleId`.

## Research Dashboard Overview

The `/research` page uses a Spring Boot API boundary to display portfolio evidence from committed experiment artifacts. The frontend does not read files from `experiments/` directly.

```http
GET /api/research/overview
```

Abridged response example:

```json
{
  "generatedAt": "2026-07-12T08:00:00Z",
  "retrieval": {
    "available": true,
    "unavailableReason": null,
    "catalogId": "api-ready-2026-06-02",
    "catalogArticleCount": 6,
    "evaluatedQueryCount": 3,
    "k": 5,
    "systems": [
      {
        "name": "hybrid",
        "attemptedQueryCount": 3,
        "completedQueryCount": 3,
        "failedQueryCount": 0,
        "degradedQueryCount": 0,
        "macroTop1StrongHit": 0.6667,
        "macroRecallAtK": 0.8333,
        "macroMrrAtK": 0.8333,
        "averageLatencyMs": 15.0
      }
    ],
    "warnings": []
  },
  "graph": {
    "available": true,
    "queryEvidence": []
  },
  "expandedCatalog": {
    "available": true,
    "catalogId": "api-ready-2026-06-05",
    "articleCount": 41,
    "labelStatus": "draft_only",
    "benchmarkStatus": "blocked_until_reviewed_labels"
  },
  "limitations": [
    "Current retrieval and graph metrics are smoke evidence, not broad quality claims."
  ]
}
```

Behavior details:
- `retrieval` reads the latest retrieval system comparison artifact.
- `graph` reads graph-aware summary and query-level smoke artifacts.
- `expandedCatalog` reads the 41-article API-ready catalog artifact and marks labels as draft-only until reviewed labels exist.
- Missing or invalid artifacts degrade at section level with `available=false` and `unavailableReason`; the endpoint should still return the remaining available sections.
- `reasonCoverage` and `topicContextCoverage` in graph query evidence may be `null` when the artifact marks the metric as not applicable.
- Local absolute filesystem paths, stack traces, and internal exception details are not included in the public response.

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

## Internal Retrieval Benchmark Run Contract

The retrieval benchmark runner compares strict retrieval systems through an internal evaluation endpoint.
This endpoint is for local experiment artifacts, not for frontend or public product calls.

```http
POST /api/internal/search-evaluation/retrieval-runs
```

Request:

```json
{
  "queries": ["agent evaluation", "graph rag failure"],
  "systems": ["KEYWORD", "VECTOR", "HYBRID"],
  "limit": 20
}
```

Rules:
- allowed systems are `KEYWORD`, `VECTOR`, and strict `HYBRID`
- `PUBLIC` is rejected by this endpoint
- `PUBLIC` benchmark runs are created only by calling the existing `GET /api/articles?query=...` API
- strict `HYBRID` fails if keyword or vector retrieval fails; it does not degrade
- public search may degrade to `KEYWORD_ONLY`, `VECTOR_ONLY`, or `POSTGRES_FALLBACK`
- public article response shape remains unchanged
- endpoint exposure is controlled by `sigak.internal.search-evaluation.enabled`; the default is disabled and local comparison runs should opt in with `SIGAK_INTERNAL_SEARCH_EVALUATION_ENABLED=true`
- response metadata may include embedding provider, model name, and dimension only; it must not expose API keys, request headers, environment variables, service URLs, raw vectors, credentials, or stack traces

Expected response:

```json
{
  "generatedAt": "2026-06-02T00:00:00Z",
  "limit": 20,
  "runs": [
    {
      "query": "graph rag failure",
      "system": "HYBRID",
      "status": "COMPLETED",
      "rankedArticleIds": [4, 1],
      "candidateCount": 2,
      "staleCandidateCount": 0,
      "failureReason": null,
      "degraded": false,
      "resolvedMode": "HYBRID",
      "timings": {
        "keywordElapsedMs": 2,
        "embeddingElapsedMs": 3,
        "vectorElapsedMs": 2,
        "fusionElapsedMs": 1,
        "articleReloadElapsedMs": 4,
        "totalElapsedMs": 12
      },
      "metadata": {
        "embeddingProvider": "local",
        "embeddingModelName": "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
        "embeddingDimension": 384
      }
    }
  ]
}
```

The distinction between strict `HYBRID` and `PUBLIC` is intentional:
strict `HYBRID` measures the retrieval system under experiment conditions, while `PUBLIC` measures the user-visible API behavior with fallback/degrade included.

## Internal Collection Automation Status

```http
GET /api/internal/collections/automation
```

Read-only process diagnostics for the optional single-instance fixed-delay collection job. Development defaults disabled; deployment defaults enabled with `PT30S` initial delay and `PT10M` delay after completion. Interval must be at least 60 seconds and each source batch is limited to 1–20. Empty source IDs select all registered sources; unknown IDs fail enabled-job startup.

Fields: `enabled`, `state` (`DISABLED`, `IDLE`, `RUNNING`, `COMPLETED`, `PARTIAL`, `FAILED`), `fixedDelaySeconds`, `attemptedRunCount`, `lastStartedAt`, `lastFinishedAt`, `lastSuccessfulAt`, `lastPublishedAt`, `nextEligibleAt`, `lastRun`, `projectionRefreshPending`, `projectionResults`, `failureType`. Timestamps are ISO-8601 instants or null. `lastRun` contains run ID/status and source/discovered/published/skipped/failed counts; each projection result contains store/status/articleCount/refreshedAt and a sanitized failure type.

`lastSuccessfulAt` advances only on a fully successful cycle. `lastPublishedAt` advances only when new articles are persisted. `nextEligibleAt` is an earliest expected start, not a completion promise. When a successful duplicate-only cycle needs no rebuild, `projectionResults` is empty. Snapshot diagnostics reset on restart; articles and existing source failure events persist. Pending projection repair is conservative on restart/collection errors and retried even with no new articles. Partial failure does not roll back persisted articles or stop later cycles.

Caddy blocks this internal path. It does not change public article responses or expose a public scheduler trigger. The supported deployment runs one enabled backend; full history/distributed locking and concurrent manual operations are outside this contract. See [deployment operations](../infra/deploy/README.md).

## Internal Collection Run Contract

Controlled collection runs are internal MVP operations. They execute selected registered sources through Spring Boot and return source/article counts without changing the public article API response shape.

```http
POST /api/internal/collections/runs
```

Request body is optional. Missing body or empty `sourceIds` runs all registered sources.

```json
{
  "sourceIds": ["openai-blog", "arxiv-cs-ai"],
  "maxArticlesPerSource": 10
}
```

Expected response:

```json
{
  "runId": "33333333-3333-3333-3333-333333333333",
  "status": "COMPLETED",
  "requestedSourceIds": ["openai-blog"],
  "selectedSourceCount": 1,
  "fetchedSourceCount": 1,
  "failedSourceCount": 0,
  "discoveredArticleCount": 3,
  "publishedArticleCount": 2,
  "skippedArticleCount": 1,
  "failedArticleCount": 0,
  "publishedArticleIds": [101, 102],
  "skippedArticleIds": [1],
  "durationMs": 42,
  "sourceResults": [
    {
      "sourceId": "openai-blog",
      "status": "COMPLETED",
      "fetched": true,
      "discoveredArticleCount": 3,
      "publishedArticleCount": 2,
      "skippedArticleCount": 1,
      "failedArticleCount": 0,
      "publishedArticleIds": [101, 102],
      "skippedArticleIds": [1],
      "failureSummaries": [],
      "durationMs": 40
    }
  ]
}
```

Failure summary example:

```json
{
  "stage": "PUBLISH_ARTICLE",
  "message": "IllegalArgumentException: title must not be blank",
  "failureKind": "INVALID_ARTICLE",
  "retryable": false,
  "failureEventId": 9,
  "articleExternalId": "gh-1",
  "articleUrl": "https://github.blog/example",
  "articleTitle": "Broken article"
}
```

Notes:
- `sourceIds` values are trimmed, blank IDs are ignored, and duplicate IDs are deduplicated in request order
- unknown source IDs return `400 Bad Request` before any source is collected
- `maxArticlesPerSource` defaults to `10` and must be between `1` and `20`
- duplicate article attempts are counted as `skipped`, not `published`
- `runId` groups persisted failure evidence from one HTTP or command-runner collection run
- failure summaries include `failureEventId` only after the failure has been persisted to PostgreSQL
- `retryable=true` means manual source re-run may help; automatic retry is not implemented in the MVP
- collection does not automatically rebuild Elasticsearch, Qdrant, or Neo4j projections

Manual retry decision table:

| Failure kind | Retry? | Operator action | Notes |
| --- | --- | --- | --- |
| `TRANSIENT_FETCH` | Yes | Re-run the same source after removing forced test failures, checking network/Docker health, or waiting for the upstream feed to recover. | Covers timeout, connection failure, 5xx, and 429. Re-running is safe because duplicate articles are counted as skipped. |
| `SOURCE_FORMAT` | No, not first | Inspect the source response, RSS/Atom/arXiv parsing assumptions, and source registry configuration before re-running. | Re-running the same unchanged malformed source is likely to fail again. |
| `INVALID_ARTICLE` | No, not first | Inspect article hints (`articleExternalId`, `articleUrl`, `articleTitle`) and normalization/enrichment validation rules before re-running. | Usually means the collected item cannot become an API-ready article without data or mapping changes. |
| `PERSISTENCE` | No, not first | Check database health, Flyway state, constraints, and persistence mapping. Re-run only after the storage problem is fixed. | Treat this as an infrastructure or schema issue, not a source freshness issue. |
| `UNKNOWN` | No, not first | Inspect the event message, stage, fingerprint, and backend logs, then classify or add tests before repeated retries. | Conservative default to avoid hiding a new failure mode. |

For manual re-run, use either the internal endpoint or the command runner with the same source ID. The diagnostics endpoint is read-only and never starts a retry by itself.

## Internal Collection Failure Event Diagnostics Contract

Collection failure events are internal diagnostics for local MVP operations. They are read-only and do not trigger retry, deletion, acknowledgement, or projection rebuild work.

```http
GET /api/internal/collections/failure-events
```

Optional query parameters:

| Parameter | Type | Default | Description |
| --- | --- | --- | --- |
| `sourceId` | string | none | Filters by collection source ID, such as `github-blog`. Blank values are ignored. |
| `runId` | UUID | none | Filters by one collection run UUID. |
| `retryable` | boolean | none | Filters failures where manual retry may or may not help. |
| `limit` | integer | `20` | Maximum returned events. Must be between `1` and `100`. |

Events are returned latest first by `occurredAt desc, id desc`.

Example:

```http
GET /api/internal/collections/failure-events?sourceId=github-blog&retryable=false&limit=10
```

Expected response:

```json
{
  "returnedCount": 1,
  "events": [
    {
      "id": 9,
      "runId": "33333333-3333-3333-3333-333333333333",
      "sourceId": "github-blog",
      "stage": "PUBLISH_ARTICLE",
      "failureKind": "INVALID_ARTICLE",
      "retryable": false,
      "message": "IllegalArgumentException: title must not be blank",
      "fingerprint": "github-blog:PUBLISH_ARTICLE:INVALID_ARTICLE:title",
      "articleExternalId": "gh-1",
      "articleUrl": "https://github.blog/example",
      "articleTitle": "Broken article",
      "occurredAt": "2026-05-31T12:00:00Z"
    }
  ]
}
```

Invalid `limit` returns `400 Bad Request`:

```json
{
  "message": "limit must be between 1 and 100"
}
```

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

## Internal Graph Projection Contract

Neo4j stores article/topic/relation metadata as a rebuildable projection. PostgreSQL remains the source of truth for article response data, and public article responses are unchanged in this implementation.

```http
POST /api/internal/graph-projections/articles/rebuild
```

Expected response:

```json
{
  "status": "completed",
  "rebuiltAt": "2026-06-03T09:00:00Z",
  "articleNodeCount": 5,
  "topicNodeCount": 15,
  "hasTopicRelationshipCount": 15,
  "relatedToRelationshipCount": 10,
  "durationMs": 120,
  "failedReason": null
}
```

Failure responses use the same shape with `status="failed"`, zero count fields, `rebuiltAt=null`, and a short `failedReason`.

Behavior details:
- reads API-ready articles, topics, and outgoing relation metadata from PostgreSQL
- creates Neo4j `Article` and `Topic` nodes
- creates `HAS_TOPIC` relationships with topic position
- creates `RELATED_TO` relationships with `relationType` and `reason`
- stores projection metadata only; it does not store raw content, summaries, why-it-matters text, embedding vectors, secrets, request headers, or environment values
- keeps rebuild manual for MVP; collection does not automatically rebuild Neo4j

```http
GET /api/internal/graph/articles/{id}/context
```

Expected response:

```json
{
  "articleId": 4,
  "topics": [
    {
      "name": "graph rag",
      "displayName": "Graph RAG",
      "relatedArticleIds": [1]
    }
  ],
  "relatedArticles": [
    {
      "articleId": 1,
      "title": "OpenAI Releases Agent Evaluation Toolkit",
      "relationType": "RELATED",
      "reason": "Graph RAG evaluation connects to agent and retrieval evaluation.",
      "sharedTopics": []
    }
  ],
  "timings": {
    "neo4jElapsedMs": 8,
    "totalElapsedMs": 8
  }
}
```

This endpoint is internal. It exists to validate graph projection context before public article detail or frontend graph-aware UI changes are added.

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
- builds embedding input from title, source excerpt, category, topics, and event type
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

Articles are public without enrichment. Summary/why-it-matters are nullable; real generation is independent of collection.

Spring calls private FastAPI from the on-demand worker. Default mock mode remains available for development; real mode uses OPENAI_API_KEY and SIGAK_ENRICHMENT_PROVIDER=openai. The internal contract is:

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
  "suggestedImportanceScore": 70,
  "modelName": "mock-enrichment"
}
```


## On-demand Article Summaries (2026-10-30)

- `GET /api/articles/{id}/summary`: read-only current summary/job status; never queues or spends.
- `POST /api/articles/{id}/summary`: request a shared asynchronous job for a public real article.
  Returns HTTP 200 with READY cached content or PENDING; duplicate visitors share one job.
  Unsupported/no-text/disabled requests return UNAVAILABLE, not fake AI content. Missing/private
  articles return 404. Cross-origin requests return 403; a bounded peer-address window returns 429.
- Detail entry initiates POST, then bounded GET polling. List/search/related/preload requests do not.
- Response: articleId, status, nullable summary/whyItMatters/modelName/inputScope/errorCode,
  evidenceQuotes array, truncated boolean and nullable bodyStatus. States: NOT_REQUESTED, PENDING, READY, FAILED,
  UNKNOWN, BUDGET_EXHAUSTED, UNAVAILABLE. DISPATCHED is private and exposed as PENDING.
- New paid summaries use FULL_TEXT only for supported ordinary OpenAI/Google/GitHub articles.
  Body preparation is free and persisted; missing old bodies are fetched on the first request,
  while new supported publications prefetch bodies without generating summaries. No historical
  bulk fetch/generation occurs. PENDING includes bodyStatus PENDING/FETCHING when fetching.
  Unsupported sources (including arXiv full papers), failed extraction and oversized complete
  input return UNAVAILABLE with BODY_* error codes before a paid job. Failures stay cached.
  No fallback to a feed excerpt or truncated article is labeled as full text.
  Article DTO summaryScope distinguishes FULL_TEXT from legacy FEED_EXCERPT. Old summaries
  remain in enrichment history until a successful visited-article upgrade; GET never upgrades.
  Evidence quotes are source substrings, not a guarantee of factual entailment. Curated seed results are reusable examples with no paid call.
- Raw-first published articles remain searchable without summaries. Elasticsearch's existing
  summary projection field contains the source excerpt; generated why-it-matters is not indexed.
  Local vector input also uses source excerpts. Reading popularity does not alter the base index.
- `GET /api/internal/summaries/budget` exposes chargedMicros, actualCostMicros, attemptCount,
  dailyCount, unknownCount, stoppedReason and maximumMicros to operators only.
- Monetary fields use USD micros (1 USD = 1,000,000). Each dispatch permanently charges 20,000
  conservative micros; this differs from actualCostMicros estimated from observed token usage.
  App maximum is 4,000,000 micros, leaving $1 of the user's approved $5 total as headroom.
  Limits persist across restarts; unknown calls retain their reservation and never auto-retry.
- Default rollout is pilot (at most ten total attempts); active rollout is explicitly configured
  after actual pilot verification and still stops at 200 lifetime attempts /20 daily attempts/$4.
  Authentication, model, billing or usage violations persist a circuit stop. Secret values and
  provider response bodies are never public errors. Caddy allows only the exact summary POST;
  all other public writes and internal paths stay denied. Behind a shared proxy, the request
  window currently acts as a shared gateway limit; global cost limits are authoritative.

Real private responses add promptVersion, inputScope, truncated, evidenceQuotes, inputTokens,
outputTokens and providerResponseId. The fixed provider/model is OpenAI/gpt-5.6-luna;
max output is 2048 tokens and full serialized payload is at most64KiB. Spring requires its
nested input envelope to fit56KiB, reserving8KiB for fixed instructions/schema, before charging.
FULL_TEXT is never truncated. Usage ceiling is70000 input tokens; 70000*$0.20/1M +
2048*$1.20/1M=$0.0164576 remains below the permanent$0.02 charge. Cache writes are disabled;
cachedInputTokens/cacheWriteTokens are validated in private real responses. No tools/retries.
AI HTTP connections are reused. Committed requests immediately wake a coalesced worker;
2-second scheduled recovery remains. The browser polls every500ms (at most120 polls).

External HTTPS behind an HTTP proxy must configure one exact `SIGAK_PUBLIC_ORIGIN`;
CORS and summary intent checks permit that origin only. Local Vite origins remain explicit.
