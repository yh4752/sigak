# Controlled Collection Trigger Design

## Summary

Sigak already has selected-source collection building blocks:

```txt
SourceRegistry
-> SourceCollectionService
-> CollectionPipelineService
-> CollectedArticlePersistenceService
```

The missing MVP slice is a controlled way to run that pipeline from Spring Boot
and see what happened. This design adds an internal collection run boundary that
can be called locally with `curl` or Swagger UI. It deliberately does not add
scheduling, background jobs, persistent run history, retries, or a production
admin console.

The chosen first step is:

```txt
POST /api/internal/collections/runs
```

The endpoint will delegate to a `CollectionRunService`. That service will be the
single orchestration boundary so a later command runner can reuse the same logic
without duplicating source selection, validation, or count aggregation.

## Current State

Completed collection pieces:

- `SourceRegistry` exposes six controlled sources:
  - `openai-blog`
  - `google-ai-blog`
  - `github-blog`
  - `arxiv-cs-ai`
  - `arxiv-cs-lg`
  - `arxiv-cs-cl`
- `SourceCollectionService.collect(source)` fetches one source, parses it, and
  publishes parsed articles through `CollectionPipelineService`.
- `CollectionPipelineService.publish(article)` normalizes and enriches one
  collected article before calling `CollectedArticlePublisher`.
- `CollectedArticlePersistenceService` persists collected articles into the
  same PostgreSQL article model used by the public API.
- Duplicate detection already exists by canonical URL, source external ID, and
  source/title/published date.

Current gap:

- There is no internal/admin trigger or command runner.
- A caller cannot run selected sources and get an aggregated run result.
- `CollectedArticlePublisher.publish()` returns only an article ID, so the
  caller cannot distinguish a newly persisted article from a duplicate that
  returned an existing ID.
- Per-run failure/debug information exists only as local control flow, not as a
  response contract.

## Design Decision

Use an internal HTTP trigger first, backed by a command-runner-ready service:

```txt
CollectionRunController
-> CollectionRunService
-> SourceRegistry
-> SourceCollectionService
-> CollectionPipelineService
-> CollectedArticlePersistenceService
```

Why this order:

- Existing internal operations already use `/api/internal/...` endpoints for
  search projection rebuilds and diagnostics.
- The collection trigger needs to be demoable with local `curl` and visible in
  generated OpenAPI docs.
- A direct command runner is useful later, but implementing it first would make
  local API smoke testing and documentation weaker.
- The service boundary keeps the future command runner cheap: it can call
  `CollectionRunService.run(request)` instead of reimplementing orchestration.

## Non-Goals

- Do not add `@Scheduled` collection.
- Do not add a persistent `collection_runs` table yet.
- Do not add a production admin UI.
- Do not add authentication in this slice. The endpoint remains under
  `/api/internal/...`; deployment/network protection is a later deployment task.
- Do not implement FastAPI HTTP enrichment mode in this slice.
- Do not add retries, backoff, or dead-letter storage.
- Do not rebuild Elasticsearch/Qdrant/Neo4j automatically after collection in
  this slice. Projection rebuild remains an explicit separate operation.

## API Contract

Endpoint:

```http
POST /api/internal/collections/runs
```

Request body is optional. Missing body or empty `sourceIds` means "run all
registered sources".

```json
{
  "sourceIds": ["openai-blog", "arxiv-cs-ai"],
  "maxArticlesPerSource": 10
}
```

Request rules:

- `sourceIds` is optional.
- Blank source IDs are ignored after trimming.
- Duplicate source IDs are deduplicated while preserving request order.
- Unknown source IDs are rejected before any source is collected.
- `maxArticlesPerSource` is optional.
- Default `maxArticlesPerSource` is `10`.
- Minimum `maxArticlesPerSource` is `1`.
- Maximum `maxArticlesPerSource` is `20`.

Successful response:

```json
{
  "status": "COMPLETED",
  "requestedSourceIds": ["openai-blog", "arxiv-cs-ai"],
  "selectedSourceCount": 2,
  "fetchedSourceCount": 2,
  "failedSourceCount": 0,
  "discoveredArticleCount": 12,
  "publishedArticleCount": 3,
  "skippedArticleCount": 9,
  "failedArticleCount": 0,
  "publishedArticleIds": [101, 102, 103],
  "skippedArticleIds": [1, 2, 3, 4, 5, 6, 7, 8, 9],
  "durationMs": 4200,
  "sourceResults": [
    {
      "sourceId": "openai-blog",
      "status": "COMPLETED",
      "fetched": true,
      "discoveredArticleCount": 7,
      "publishedArticleCount": 2,
      "skippedArticleCount": 5,
      "failedArticleCount": 0,
      "publishedArticleIds": [101, 102],
      "skippedArticleIds": [1, 2, 3, 4, 5],
      "failureSummaries": [],
      "durationMs": 1200
    }
  ]
}
```

Run status values:

- `COMPLETED`: all selected sources finished without source-level or
  article-level failures.
- `PARTIAL`: at least one source or article failed, and at least one source was
  fetched or one article was published/skipped.
- `FAILED`: every selected source failed before producing publishable article
  outcomes.

Source status values:

- `COMPLETED`: source fetched and article attempts had no failures.
- `PARTIAL`: source fetched, but at least one article failed during enrichment or
  persistence.
- `FAILED`: source fetch or parse failed before article-level processing could
  finish.

Invalid source response:

```http
HTTP/1.1 400 Bad Request
```

The error body is not part of the stable MVP contract yet, but the controller
should not expose stack traces.

## Count Semantics

This design separates source-level and article-level counts:

| Field | Meaning |
| --- | --- |
| `selectedSourceCount` | Number of valid sources selected for this run. |
| `fetchedSourceCount` | Number of sources whose source document was fetched and parsed far enough to attempt article publishing. |
| `failedSourceCount` | Number of sources that failed at source fetch or parse level. |
| `discoveredArticleCount` | Number of parsed articles considered after applying `maxArticlesPerSource`. |
| `publishedArticleCount` | Number of newly inserted published articles. |
| `skippedArticleCount` | Number of duplicate article attempts that returned an existing article ID. |
| `failedArticleCount` | Number of parsed articles that failed during enrichment or persistence. |

The important internal contract change is that article publishing must return an
outcome, not only an ID:

```kotlin
enum class CollectedArticlePublishOutcome {
    PUBLISHED,
    SKIPPED_DUPLICATE
}

data class CollectedArticlePublishResult(
    val articleId: Long,
    val outcome: CollectedArticlePublishOutcome
)
```

`CollectedArticlePublisher.publish(article, enrichment)` should return
`CollectedArticlePublishResult`. `CollectedArticlePersistenceService` can set:

- `PUBLISHED` when it saves a new `ArticleEntity`.
- `SKIPPED_DUPLICATE` when duplicate detection returns an existing article ID.

This is a small internal contract change, but it is necessary for honest
`published` vs `skipped` metrics.

## Component Responsibilities

### `CollectionRunController`

Package:

```txt
backend/src/main/kotlin/com/sigak/collection/controller/
```

Responsibilities:

- Expose `POST /api/internal/collections/runs`.
- Keep request validation and orchestration out of the controller.
- Return the run response DTO from `CollectionRunService`.
- Add OpenAPI summary/description consistent with other internal controllers.

### `CollectionRunService`

Package:

```txt
backend/src/main/kotlin/com/sigak/collection/service/
```

Responsibilities:

- Normalize requested source IDs.
- Resolve selected sources from `SourceRegistry`.
- Reject unknown source IDs before side effects.
- Apply `maxArticlesPerSource` policy.
- Run each selected source sequentially.
- Catch source-level failures so one broken feed does not stop every source.
- Aggregate top-level counts and IDs.
- Compute run/source status from observed outcomes.

Sequential execution is intentional for the MVP. It keeps source pressure low,
keeps logs readable, and avoids introducing async job state before persistent run
history exists.

### `SourceCollectionService`

Responsibilities after this change:

- Continue owning one-source fetch/parse/publish flow.
- Accept a per-source article limit.
- Return source-level article outcomes:
  - published IDs
  - skipped duplicate IDs
  - article failure count
  - failure summaries

It should still catch article-level failures so one bad article does not fail the
whole source. Source-level fetch/parse failures may still bubble up to
`CollectionRunService`, which converts them into a failed source result.

### `CollectionPipelineService`

Responsibilities after this change:

- Continue owning normalization and enrichment for one article.
- Return `CollectedArticlePublishResult` from `publish(article)`.
- Avoid knowing about source run aggregation.

### `CollectedArticlePersistenceService`

Responsibilities after this change:

- Preserve duplicate detection order.
- Return `PUBLISHED` for new saves.
- Return `SKIPPED_DUPLICATE` for existing duplicate IDs.
- Keep PostgreSQL as the source-of-truth write path.

## Failure Handling

Failure information should be useful but not noisy:

- Do not expose stack traces.
- Include exception class simple name and a short message when available.
- Cap failure summaries per source to avoid huge response bodies. The first
  implementation should keep up to five article-level failure summaries per
  source.
- Source-level failures should produce one source result with `status = FAILED`,
  `fetched = false`, and a failure summary.
- Article-level failures should produce `status = PARTIAL` for that source.

Example failure summary:

```json
{
  "stage": "PUBLISH_ARTICLE",
  "message": "IllegalArgumentException: title must not be blank"
}
```

Failure stage values:

- `FETCH_SOURCE`
- `PARSE_SOURCE`
- `PUBLISH_ARTICLE`

The first implementation may group fetch and parse exceptions at the run service
boundary if the exact source stage cannot be separated without widening
`SourceCollectionService` too much.

## Local Smoke Flow

After implementation, the expected local smoke flow is:

```bash
docker compose -f infra/docker-compose.yml up -d postgres
cd backend
./gradlew bootRun
curl -X POST http://localhost:8080/api/internal/collections/runs \
  -H 'Content-Type: application/json' \
  -d '{"sourceIds":["openai-blog"],"maxArticlesPerSource":3}'
curl http://localhost:8080/api/articles
```

Search projection rebuild remains explicit:

```bash
curl -X POST http://localhost:8080/api/internal/search-projections/articles/rebuild
curl -X POST http://localhost:8080/api/internal/search-projections/article-vectors/rebuild
```

## Documentation Updates

Implementation should update:

- `docs/API_SPEC.md`
  - Add the internal collection run endpoint contract.
- `docs/STATUS.md`
  - Move "internal/admin trigger or command runner" from missing to partially
    implemented once endpoint tests pass.
- `docs/ROADMAP.md`
  - Check or clarify the controlled trigger item after implementation.
- `docs/ONBOARDING.ko.md`
  - Add the collection trigger to the local internal operation map if useful.
- `docs/blog/topic-queue.md`
  - Add or update a topic if trigger count semantics or internal API boundary
    produce a useful writing candidate.

## Testing Strategy

Focused backend tests:

- `CollectionRunServiceTest`
  - runs all sources when request source IDs are empty
  - runs only selected source IDs in request order
  - rejects unknown source IDs before collecting any source
  - aggregates published, skipped, failed article counts
  - returns `PARTIAL` when one source fails and another succeeds
- `CollectionRunControllerTest`
  - exposes `POST /api/internal/collections/runs`
  - accepts optional request body
  - serializes top-level and source-level counts
- `SourceCollectionServiceTest`
  - separates published and duplicate-skipped outcomes
  - caps parsed articles using `maxArticlesPerSource`
- `CollectionPipelineServiceTest`
  - returns publish outcome from the persistence boundary
- `CollectedArticlePersistenceServiceTest`
  - returns `PUBLISHED` for a new article
  - returns `SKIPPED_DUPLICATE` for a duplicate canonical URL

Required final backend verification:

```bash
cd backend
./gradlew test --rerun-tasks
./gradlew check
```

Run `git diff --check` before committing.

## Trade-Offs

### Chosen: Internal HTTP Trigger First

Benefits:

- Easy local demo with `curl` and Swagger UI.
- Consistent with existing internal search projection endpoints.
- Keeps frontend unchanged.

Costs:

- A later command runner still needs a small wrapper.
- Endpoint is not production-secured yet and must remain internal-only.

### Deferred: Persistent Run History

Benefits:

- Avoids schema and retention policy work before the trigger behavior is stable.
- Keeps the first implementation small and testable.

Costs:

- Run results disappear after the HTTP response unless the caller saves them.
- Historical metrics and dashboard visualization remain future work.

### Chosen: Publish Outcome Contract

Benefits:

- Makes `published` and `skipped` counts truthful.
- Keeps duplicate detection inside persistence where the source-of-truth check
  belongs.

Costs:

- Updates several collection service tests and internal function signatures.

## Open Decisions Resolved

- **Endpoint vs command runner:** endpoint first, service reusable by command
  runner later.
- **All sources vs selected sources:** support both; empty request means all
  sources.
- **Duplicate handling:** duplicates count as `skipped`, not `published`.
- **Projection rebuild coupling:** do not rebuild search/vector/graph projections
  automatically after collection.
- **Storage:** do not persist run history in this slice.
