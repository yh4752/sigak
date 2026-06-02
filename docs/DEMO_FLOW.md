# Sigak Local Demo Flow

[English](DEMO_FLOW.md) | [한국어](DEMO_FLOW.ko.md)

Last updated: 2026-05-31

This document is the reproducible local demo script for the current Sigak v0.1 backend slice. It proves that selected-source collection, failure diagnostics, Elasticsearch projection, Qdrant projection, and public hybrid search are connected while PostgreSQL remains the source of truth.

## What This Flow Proves

```txt
selected source collection
-> PostgreSQL article persistence
-> collection failure diagnostics lookup
-> forced failure event diagnostics sample
-> Elasticsearch keyword projection rebuild
-> Qdrant vector projection rebuild
-> public hybrid search
-> internal vector search metrics
-> search metrics inspection
```

Current limitation: Neo4j graph projection is not part of this flow yet.

## Prerequisites

Run commands from the repository root unless a step says otherwise.

Required services:

```bash
docker compose -f infra/docker-compose.yml up -d --pull never postgres elasticsearch qdrant ai
docker compose -f infra/docker-compose.yml ps postgres elasticsearch qdrant ai
```

Expected signal: PostgreSQL, Elasticsearch, Qdrant, and AI server are `healthy`.

Start the backend in another terminal:

```bash
cd backend
./gradlew bootRun
```

Expected signal: Spring Boot starts on `http://localhost:8080`.

## Step 1. Run Selected-Source Collection

```bash
curl -X POST http://localhost:8080/api/internal/collections/runs \
  -H 'Content-Type: application/json' \
  -d '{"sourceIds":["github-blog"],"maxArticlesPerSource":1}'
```

Expected signal:

- `status` is `COMPLETED` or `PARTIAL`.
- `runId` is returned.
- `publishedArticleCount`, `skippedArticleCount`, and `failedArticleCount` explain the run outcome.
- Duplicate articles are counted as skipped, not failed.

Observed on 2026-05-31:

```json
{
  "runId": "947c254a-3b79-4a48-836a-2b0e66884503",
  "status": "COMPLETED",
  "publishedArticleCount": 0,
  "skippedArticleCount": 1,
  "failedArticleCount": 0,
  "skippedArticleIds": [6]
}
```

## Step 2. Inspect Failure Diagnostics

Use the `runId` from step 1:

```bash
curl "http://localhost:8080/api/internal/collections/failure-events?sourceId=github-blog&runId=<RUN_ID>&limit=10"
```

Expected signal:

- If the run had failures, `events` contains persisted failure evidence with `failureKind`, `retryable`, `message`, and optional article hints.
- If the run succeeded or only skipped duplicates, `returnedCount` may be `0`.

Observed on 2026-05-31:

```json
{
  "returnedCount": 0,
  "events": []
}
```

### Optional: Force a Fetch Failure for Diagnostics

To prove that source-level collection failures are persisted and queryable, start the backend with a deliberately invalid local proxy. Use this only for the failure diagnostics smoke; restart the backend normally afterward.

```bash
cd backend
JAVA_TOOL_OPTIONS='-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=9 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=9' ./gradlew bootRun
```

Then run the same selected-source collection command:

```bash
curl -X POST http://localhost:8080/api/internal/collections/runs \
  -H 'Content-Type: application/json' \
  -d '{"sourceIds":["github-blog"],"maxArticlesPerSource":1}'
```

Observed on 2026-05-31:

```json
{
  "runId": "cd28c139-0275-465a-a04d-4ff5bea2597a",
  "status": "FAILED",
  "fetchedSourceCount": 0,
  "failedSourceCount": 1,
  "sourceResults": [
    {
      "sourceId": "github-blog",
      "status": "FAILED",
      "failure": {
        "stage": "FETCH_SOURCE",
        "failureKind": "TRANSIENT_FETCH",
        "retryable": true,
        "failureEventId": 1
      }
    }
  ]
}
```

Failure event lookup:

```bash
curl "http://localhost:8080/api/internal/collections/failure-events?sourceId=github-blog&runId=cd28c139-0275-465a-a04d-4ff5bea2597a&limit=10"
```

Observed on 2026-05-31:

```json
{
  "returnedCount": 1,
  "events": [
    {
      "id": 1,
      "runId": "cd28c139-0275-465a-a04d-4ff5bea2597a",
      "sourceId": "github-blog",
      "stage": "FETCH_SOURCE",
      "failureKind": "TRANSIENT_FETCH",
      "retryable": true,
      "fingerprint": "1666500cb084d40c6589c94b907d5808b1b4a8be9ddb4d533d230d1ca69ddcb5"
    }
  ]
}
```

## Step 3. Rebuild Elasticsearch Keyword Projection

```bash
curl -X POST http://localhost:8080/api/internal/search-projections/articles/rebuild
curl http://localhost:9200/sigak-articles-v1/_count
```

Expected signal:

- Rebuild response `status` is `completed`.
- `indexedCount` matches the number of API-ready PostgreSQL articles.
- Elasticsearch `_count` matches `indexedCount`.

Observed on 2026-05-31:

```json
{
  "status": "completed",
  "indexName": "sigak-articles-v1",
  "indexedCount": 6,
  "failedReason": null
}
```

Elasticsearch count:

```json
{
  "count": 6
}
```

## Step 4. Rebuild Qdrant Vector Projection

```bash
curl -X POST http://localhost:8080/api/internal/search-projections/article-vectors/rebuild
curl http://localhost:6333/collections/sigak-article-vectors-minilm-v1
```

Expected signal:

- Rebuild response `status` is `completed`.
- `embeddingProvider` is `local` for the main semantic retrieval path.
- `embeddingDimension` is `384` for `sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2`.
- Qdrant collection `points_count` matches `indexedCount`.

Observed on 2026-05-31:

```json
{
  "status": "completed",
  "collectionName": "sigak-article-vectors-minilm-v1",
  "indexedCount": 6,
  "embeddingProvider": "local",
  "embeddingModelName": "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
  "embeddingDimension": 384,
  "failedReason": null
}
```

Qdrant collection signal:

```json
{
  "status": "green",
  "points_count": 6
}
```

## Step 5. Run Public Hybrid Search

```bash
curl "http://localhost:8080/api/articles?query=graph"
curl http://localhost:8080/api/internal/search-metrics/articles
```

Expected signal:

- Public search returns article responses, not projection-store payloads.
- Search metrics show `lastSearch.mode` as `HYBRID` when both Elasticsearch and Qdrant are available.
- `staleCandidateCount` should be `0` in a fresh rebuild smoke.

Observed on 2026-05-31:

```json
{
  "totalSearchCount": 1,
  "hybridSearchCount": 1,
  "postgresFallbackSearchCount": 0,
  "lastSearch": {
    "queryLength": 5,
    "resultCount": 6,
    "mode": "HYBRID",
    "keywordCandidateCount": 1,
    "vectorCandidateCount": 6,
    "fusedCandidateCount": 6,
    "staleCandidateCount": 0,
    "fallbackReason": null
  }
}
```

The first public result for `query=graph` was article `4`, `New Research Maps Failure Modes in Graph RAG Systems`.

## Step 6. Run Internal Vector Search Metrics

```bash
curl -X POST http://localhost:8080/api/internal/vector-search/articles \
  -H 'Content-Type: application/json' \
  -d '{"query":"graph rag","limit":3}'

curl http://localhost:8080/api/internal/search-metrics/article-vectors
```

Expected signal:

- The vector endpoint returns PostgreSQL article responses with Qdrant scores.
- The top result for `graph rag` should be graph/RAG-related when the local embedding model and Qdrant projection are healthy.
- Vector metrics include embedding, Qdrant, article reload, and total elapsed time.

Observed on 2026-05-31:

```json
{
  "query": "graph rag",
  "collectionName": "sigak-article-vectors-minilm-v1",
  "embeddingProvider": "local",
  "embeddingModelName": "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
  "embeddingDimension": 384,
  "topResult": {
    "articleId": 4,
    "title": "New Research Maps Failure Modes in Graph RAG Systems",
    "score": 0.4870288
  },
  "timings": {
    "embeddingElapsedMs": 19,
    "qdrantElapsedMs": 10,
    "articleLoadElapsedMs": 14,
    "totalElapsedMs": 45
  }
}
```

Vector metrics:

```json
{
  "totalSearchCount": 1,
  "averageTotalElapsedMs": 45.0,
  "lastSearch": {
    "queryLength": 9,
    "resultCount": 3,
    "embeddingElapsedMs": 19,
    "qdrantElapsedMs": 10,
    "articleLoadElapsedMs": 14,
    "totalElapsedMs": 45
  }
}
```

## Step 7. Verify Frontend Build and API Contract

The in-app browser automation was blocked by the local URL security policy in the 2026-05-31 session, so the frontend evidence for this run uses tests, lint, build, Vite HTML fetch, and backend API responses instead of a browser screenshot.

```bash
cd frontend
npm test
npm run lint
npm run build
curl http://127.0.0.1:5173/
curl http://localhost:8080/api/articles/4
```

Observed on 2026-05-31:

- `npm test`: 6 test files and 26 tests passed.
- `npm run lint`: passed.
- `npm run build`: passed.
- Vite dev server returned the Sigak HTML shell from `http://127.0.0.1:5173/`.
- `GET /api/articles/4` returned the Graph RAG article detail with summary, why-it-matters, topics, and related article IDs `[1, 5]`.

## Cleanup

Stop the backend with `Ctrl+C`.

Stop local infrastructure when the demo is finished:

```bash
docker compose -f infra/docker-compose.yml down
```

Use volume reset only when you intentionally want to delete local data:

```bash
docker compose -f infra/docker-compose.yml down -v
```

## Troubleshooting

- If collection returns duplicate skips, that is still a valid persistence signal. The article already exists in PostgreSQL.
- If `events` is empty, check `failedArticleCount` and `failedSourceCount` in the collection run response first.
- For a fetch failure with `failureKind=TRANSIENT_FETCH` and `retryable=true`, restart the backend without the forced bad proxy or wait for the upstream source/network to recover, then rerun the same source through the internal endpoint or command runner.
- Use this decision table before rerunning a failed source:

| Failure kind | Retry? | What to check first |
| --- | --- | --- |
| `TRANSIENT_FETCH` | Yes | Remove forced bad proxy/test failure, check network/Docker health, or wait for upstream feed recovery. |
| `SOURCE_FORMAT` | Not first | Inspect the feed response, parser assumptions, and source registry settings. |
| `INVALID_ARTICLE` | Not first | Inspect article hints and normalization/enrichment validation rules. |
| `PERSISTENCE` | Not first | Check PostgreSQL health, Flyway state, constraints, and persistence mapping. |
| `UNKNOWN` | Not first | Inspect event message, stage, fingerprint, and backend logs before repeated retries. |

- If Elasticsearch or Qdrant rebuild fails, inspect the relevant Docker service health and logs.
- If Qdrant rebuild fails while calling the AI server, confirm `http://localhost:8000/health` is reachable.
- Projection rebuilds are manual. Collection does not automatically rebuild Elasticsearch, Qdrant, or Neo4j.
