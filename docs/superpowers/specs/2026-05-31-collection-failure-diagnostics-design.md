# Collection Failure Diagnostics Design

## Goal

Expose a small internal diagnostics endpoint that lets a developer inspect persisted collection failure events after an HTTP or command-runner collection run.

## Design Decision

The endpoint is read-only and internal:

```http
GET /api/internal/collections/failure-events
```

It queries `collection_failure_events`, which is already the persistent evidence table for source and article-level collection failures. This keeps the feature useful without adding a run-history table, retry queue, scheduler, or admin UI before v0.1.

## Scope

Included:

- List recent failure events.
- Filter by `sourceId`.
- Filter by `runId`.
- Filter by `retryable`.
- Limit result count with `limit`, default `20`, allowed range `1..100`.
- Return latest events first using `occurredAt desc, id desc`.

Deferred:

- Automatic retry.
- Failure deletion or acknowledgement.
- Persistent collection run history.
- Pagination cursor.
- Public frontend exposure.
- Aggregated dashboards.

## API Contract

Query parameters:

| Name | Type | Required | Meaning |
| --- | --- | --- | --- |
| `sourceId` | string | no | Source registry ID such as `github-blog` |
| `runId` | UUID | no | One collection run UUID |
| `retryable` | boolean | no | Whether manual retry may help |
| `limit` | integer | no | Default `20`, maximum `100` |

Response:

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
      "fingerprint": "github-blog:PUBLISH_ARTICLE:INVALID_ARTICLE:...",
      "articleExternalId": "gh-1",
      "articleUrl": "https://github.blog/example",
      "articleTitle": "Broken article",
      "occurredAt": "2026-05-31T12:00:00Z"
    }
  ]
}
```

Invalid `limit` returns `400 Bad Request` with:

```json
{
  "message": "limit must be between 1 and 100"
}
```

## Service Boundaries

- `CollectionFailureEventController`
  - Owns HTTP query parameter mapping and bad request mapping.
- `CollectionFailureEventQueryService`
  - Normalizes blank `sourceId`, validates `limit`, calls the repository, and maps entities to DTOs.
- `CollectionFailureEventRepository`
  - Adds a filtered latest-first query using `Pageable`.
- DTOs under `collection/dto`
  - Keep JPA entities out of API responses.

## Testing Strategy

- Controller test:
  - endpoint maps query parameters to the service and returns event JSON.
  - invalid limit from the service becomes `400`.
- Service/JPA integration test:
  - filters by source, run, and retryable.
  - returns latest events first.
  - enforces the limit range.

## Non-Goals

This endpoint is not a public product API. It exists to support local MVP operations, debugging, and portfolio evidence while collection operations are still intentionally lightweight.
