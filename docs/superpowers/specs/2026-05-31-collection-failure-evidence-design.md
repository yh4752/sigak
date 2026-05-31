# Collection Failure Evidence Design

## Goal

Persist enough collection failure evidence to debug bad feeds and invalid collected articles across runs, without building a scheduler, retry queue, or full collection run history system before v0.1.

## Second Review Conclusion

The right MVP design is **persistent failure events, not persistent run history**.

Current collection responses already show `fetched`, `published`, `skipped`, and `failed` counts. The missing piece is that failure evidence disappears after the HTTP request or command runner exits. Persisting every collection run would add more schema, status transitions, and lifecycle questions than the MVP needs. Persisting only failure events gives useful debugging evidence while keeping the design small.

## Scope

Included:

- Add `collection_failure_events` table.
- Add a `runId` UUID to each collection run response and persisted failure event.
- Persist source-level failures such as fetch and parse failures.
- Persist article-level publish failures with article hints when available.
- Classify failures into a small set of stable kinds.
- Mark whether a failure is retryable.
- Return persisted `failureEventId`, `failureKind`, and `retryable` in internal response summaries.
- Keep duplicate skips out of failure evidence.

Deferred:

- `collection_runs` table.
- Retry queue.
- Automatic retry execution.
- Scheduler/background worker.
- Alerting or production observability stack.
- Projection rebuild chaining after collection.

## Data Model

Create `collection_failure_events`:

| Column | Type | Meaning |
| --- | --- | --- |
| `id` | `bigserial primary key` | Failure event ID returned in summaries |
| `run_id` | `uuid not null` | Groups events from one HTTP/CLI collection run |
| `source_key` | `varchar(120) not null` | Source registry ID such as `github-blog` |
| `stage` | `varchar(60) not null` | `FETCH_SOURCE`, `PARSE_SOURCE`, or `PUBLISH_ARTICLE` |
| `failure_kind` | `varchar(80) not null` | Stable classifier for filtering and blog/ops analysis |
| `retryable` | `boolean not null` | Whether a manual retry is likely meaningful |
| `message` | `text not null` | Sanitized exception summary |
| `fingerprint` | `varchar(160) not null` | Deterministic grouping key for repeated failures |
| `article_external_id` | `varchar(255)` | Optional article hint |
| `article_url` | `text` | Optional article hint |
| `article_title` | `varchar(500)` | Optional article hint |
| `occurred_at` | `timestamptz not null` | Event timestamp |

Indexes:

- `(source_key, occurred_at desc)`
- `(run_id)`
- `(fingerprint, occurred_at desc)`
- `(retryable, occurred_at desc)`

No unique constraint is added in the MVP. Repeated failures are useful evidence, and deduplication can be handled later by grouping `fingerprint`.

## Failure Kinds

```kotlin
enum class CollectionFailureKind {
    TRANSIENT_FETCH,
    SOURCE_FORMAT,
    INVALID_ARTICLE,
    PERSISTENCE,
    UNKNOWN
}
```

Classification rules:

- `FETCH_SOURCE`
  - timeout, connection failure, 5xx, 429 -> `TRANSIENT_FETCH`, `retryable=true`
  - other 4xx -> `SOURCE_FORMAT`, `retryable=false`
  - unknown fetch exception -> `UNKNOWN`, `retryable=false`
- `PARSE_SOURCE`
  - parser/XML/feed shape failures -> `SOURCE_FORMAT`, `retryable=false`
  - unknown parse exception -> `UNKNOWN`, `retryable=false`
- `PUBLISH_ARTICLE`
  - validation/normalization failure -> `INVALID_ARTICLE`, `retryable=false`
  - persistence/data access failure -> `PERSISTENCE`, `retryable=false`
  - unknown publish exception -> `UNKNOWN`, `retryable=false`

This is intentionally conservative. The system records whether retry might help, but does not automatically retry.

## Response Contract

Add `runId` to `CollectionRunResponse`.

Extend `CollectionFailureSummary` with defaulted fields so older test setup remains lightweight:

```kotlin
data class CollectionFailureSummary(
    val stage: CollectionFailureStage,
    val message: String,
    val failureKind: CollectionFailureKind = CollectionFailureKind.UNKNOWN,
    val retryable: Boolean = false,
    val failureEventId: Long? = null,
    val articleExternalId: String? = null,
    val articleUrl: String? = null,
    val articleTitle: String? = null
)
```

## Service Boundaries

- `CollectionFailureClassifier`
  - Pure service that turns `(stage, Throwable)` into kind, retryability, and message.
- `SourceCollectionException`
  - Wraps source-level fetch/parse failures with the correct `CollectionFailureStage`.
- `CollectionFailureEventEntity`
  - JPA entity for `collection_failure_events`.
- `CollectionFailureEventRepository`
  - Internal repository for persistence and tests.
- `CollectionFailureEventRecorder`
  - Persists summaries with `runId` and `sourceId`, then returns the summary with `failureEventId`.

`CollectionRunService` remains the run orchestrator. It generates `runId`, collects source results, records failure summaries, and returns the response. `SourceCollectionService` still handles one-source fetch/parse/publish and creates article-level summaries when publish fails.

## Retry Rule

No automatic retry is implemented in this slice.

Manual retry guidance:

- `retryable=true`: safe to run the same source again with the command runner or internal endpoint.
- `retryable=false`: inspect source format, article data, or persistence mapping before retrying.

Duplicate skips are not failures and are not recorded.

## Testing Strategy

- Classifier unit tests for retryable fetch and non-retryable validation/persistence failures.
- Source collection tests for article-level failure summaries with article hints.
- Collection run service tests for:
  - generated `runId`
  - persisted source-level failure event
  - persisted article-level failure event
  - summary includes `failureEventId`
  - duplicate skip does not create a failure event
- Repository/JPA test for migration and basic query persistence.
- Existing controller and command runner tests should continue to pass after response DTO changes.

## Non-Goals

Do not add a public API for failure events in this slice. If inspection becomes necessary, use database queries or tests first. An internal listing endpoint can be designed later if the evidence table proves useful.
