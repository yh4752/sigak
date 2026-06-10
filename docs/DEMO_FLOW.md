# Sigak Local Demo Flow

[English](DEMO_FLOW.md) | [한국어](DEMO_FLOW.ko.md)

Last updated: 2026-06-08

This document is the reproducible local demo script for the current Sigak v0.1 backend slice. It proves that selected-source collection, failure diagnostics, Elasticsearch projection, Qdrant projection, Neo4j graph projection, public hybrid search, graph-aware article detail, and smoke evaluation artifact inspection are connected while PostgreSQL remains the source of truth.

## What This Flow Proves

```txt
selected source collection
-> PostgreSQL article persistence
-> collection failure diagnostics lookup
-> forced failure event diagnostics sample
-> Elasticsearch keyword projection rebuild
-> Qdrant vector projection rebuild
-> Neo4j graph projection rebuild
-> public hybrid search
-> public graph-aware article detail
-> internal vector search metrics
-> search metrics inspection
-> retrieval and graph-aware smoke artifact inspection
```

## Prerequisites

Run commands from the repository root unless a step says otherwise.

Required services:

```bash
docker compose -f infra/docker-compose.yml up -d --pull never postgres elasticsearch qdrant neo4j ai
docker compose -f infra/docker-compose.yml ps postgres elasticsearch qdrant neo4j ai
```

Expected signal: PostgreSQL, Elasticsearch, Qdrant, Neo4j, and AI server are `healthy`.

Start the backend in another terminal:

```bash
cd backend
./gradlew bootRun
```

Expected signal: Spring Boot starts on `http://localhost:8080`.

Observed counts in this guide come from date-specific smoke runs. Your local counts may differ if PostgreSQL already contains collected articles or if the database volume was reset.

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

## Step 5. Rebuild Neo4j Graph Projection

```bash
curl -X POST http://localhost:8080/api/internal/graph-projections/articles/rebuild
```

Expected signal:

- Rebuild response `status` is `completed`.
- `articleNodeCount` matches the API-ready article count used for the projection smoke.
- `topicNodeCount`, `hasTopicRelationshipCount`, and `relatedToRelationshipCount` are present.
- Neo4j remains a rebuildable projection store, not the source of truth.

Observed on 2026-06-03:

```json
{
  "status": "completed",
  "articleNodeCount": 26,
  "topicNodeCount": 18,
  "hasTopicRelationshipCount": 36,
  "relatedToRelationshipCount": 10,
  "durationMs": 1037,
  "failedReason": null
}
```

## Step 6. Run Public Hybrid Search And Graph Context

```bash
curl "http://localhost:8080/api/articles?query=graph"
curl http://localhost:8080/api/articles/4/graph-context
curl http://localhost:8080/api/internal/search-metrics/articles
```

Expected signal:

- Public search returns article responses, not projection-store payloads.
- Search metrics show `lastSearch.mode` as `HYBRID` when both Elasticsearch and Qdrant are available.
- `staleCandidateCount` should be `0` in a fresh rebuild smoke.
- Public graph context returns `relatedArticleReasons` and `topics` for article detail when Neo4j is available.
- If Neo4j is unavailable, `GET /api/articles/{id}/graph-context` degrades to an empty graph context instead of breaking article detail.

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

Public graph context response shape:

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

Neo4j unavailable fallback observed on 2026-06-03:

```json
{
  "articleId": 4,
  "relatedArticleReasons": [],
  "topics": []
}
```

## Step 7. Run Internal Vector Search Metrics

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

## Step 8. Inspect Retrieval Smoke Artifacts

This step does not create new benchmark claims. It reads the already committed smoke comparison artifact.

```bash
node -e "const s=require('./experiments/results/retrieval/latest/metrics.comparison.json'); console.log(JSON.stringify({catalogId:s.catalogId,evaluatedQueryCount:s.evaluatedQueryCount,systems:s.systems.map((system)=>system.system),warnings:s.warnings}, null, 2))"
```

Expected signal:

- `catalogId` is `api-ready-2026-06-02`.
- `evaluatedQueryCount` is `3`.
- systems include `keyword`, `vector`, `hybrid`, and `public`.
- warnings say the label set is smaller than 10 reviewed queries and the catalog is below 20 articles.

Observed on 2026-06-02:

```json
{
  "catalogId": "api-ready-2026-06-02",
  "evaluatedQueryCount": 3,
  "systems": ["keyword", "vector", "hybrid", "public"],
  "warnings": [
    "label set is smaller than 10 reviewed queries, so this is a smoke benchmark",
    "catalog article count is below 20, so ranking difficulty is still low"
  ]
}
```

## Step 9. Inspect Graph-Aware Smoke Artifacts

This step reads the graph-aware evaluation artifact from the current smoke baseline. It does not replace the still-pending `api-ready-2026-06-05` expanded benchmark.

```bash
node -e "const s=require('./experiments/results/graph/latest/graph-context.metrics.summary.json'); console.log(JSON.stringify({catalogId:s.catalogId,evaluatedQueryCount:s.evaluatedQueryCount,macroGraphContextCoverageAtK:s.macroGraphContextCoverageAtK,graphContextFailureRate:s.graphContextFailureRate,emptyContextRate:s.emptyContextRate,warnings:s.warnings}, null, 2))"
```

Expected signal:

- `catalogId` is `api-ready-2026-06-02`.
- `evaluatedQueryCount` is `3`.
- graph failure and empty-context rates are present.
- warnings state that this is smoke-only and that graph reasons are stored projection reasons.

Observed on 2026-06-04:

```json
{
  "catalogId": "api-ready-2026-06-02",
  "evaluatedQueryCount": 3,
  "macroGraphContextCoverageAtK": 0.3333333333333333,
  "graphContextFailureRate": 0,
  "emptyContextRate": 0,
  "warnings": [
    "label set is smaller than 10 reviewed queries, so this is a smoke graph evaluation",
    "catalog is smaller than 20 articles, so graph density is too small for quality claims",
    "graph latency is measured as public API round-trip, not pure Neo4j query latency",
    "graph reasons are stored projection reasons, not independently verified factual explanations"
  ]
}
```

## Step 10. Verify Frontend Build and API Contract

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

- If Elasticsearch, Qdrant, or Neo4j rebuild fails, inspect the relevant Docker service health and logs.
- If Qdrant rebuild fails while calling the AI server, confirm `http://localhost:8000/health` is reachable.
- If public graph context is empty, confirm Neo4j is healthy and rerun `POST /api/internal/graph-projections/articles/rebuild` before treating it as a data issue.
- Projection rebuilds are manual. Collection does not automatically rebuild Elasticsearch, Qdrant, or Neo4j.
