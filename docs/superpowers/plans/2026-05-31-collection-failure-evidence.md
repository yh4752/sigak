# Collection Failure Evidence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist collection failure evidence with stable classification and retry hints while keeping collection run history and automatic retry deferred.

**Architecture:** Add a small failure evidence boundary around the existing collection run flow. `CollectionRunService` remains the orchestrator, `SourceCollectionService` still handles one-source collection, and `CollectionFailureEventRecorder` persists failure summaries with a generated `runId`. No scheduler, retry queue, or `collection_runs` table is added.

**Tech Stack:** Kotlin, Spring Boot, Spring Data JPA, Flyway, PostgreSQL, JUnit 5, Testcontainers.

---

## Reference Spec

- `docs/superpowers/specs/2026-05-31-collection-failure-evidence-design.md`

## Invariants

- Duplicate skips are not failures.
- Do not add automatic retry.
- Do not add a `collection_runs` table.
- Do not expose failure evidence through public APIs.
- Keep PostgreSQL as source of truth.
- Keep the existing collection HTTP endpoint and command runner entrypoints.

## File Structure

Create:

- `backend/src/main/resources/db/migration/V3__collection_failure_events.sql`
- `backend/src/main/kotlin/com/sigak/collection/domain/CollectionFailureKind.kt`
- `backend/src/main/kotlin/com/sigak/collection/domain/CollectionFailureEventEntity.kt`
- `backend/src/main/kotlin/com/sigak/collection/repository/CollectionFailureEventRepository.kt`
- `backend/src/main/kotlin/com/sigak/collection/service/CollectionFailureClassifier.kt`
- `backend/src/main/kotlin/com/sigak/collection/service/CollectionFailureEventRecorder.kt`
- `backend/src/main/kotlin/com/sigak/collection/service/SourceCollectionException.kt`
- `backend/src/test/kotlin/com/sigak/collection/service/CollectionFailureClassifierTest.kt`
- `backend/src/test/kotlin/com/sigak/collection/service/CollectionFailureEventRecorderTest.kt`

Modify:

- `backend/src/main/kotlin/com/sigak/collection/dto/CollectionRunResponse.kt`
- `backend/src/main/kotlin/com/sigak/collection/service/SourceCollectionService.kt`
- `backend/src/main/kotlin/com/sigak/collection/service/CollectionRunService.kt`
- `backend/src/test/kotlin/com/sigak/collection/service/SourceCollectionServiceTest.kt`
- `backend/src/test/kotlin/com/sigak/collection/service/CollectionRunServiceTest.kt`
- `backend/src/test/kotlin/com/sigak/collection/controller/CollectionRunControllerTest.kt`
- `backend/src/test/kotlin/com/sigak/collection/runner/CollectionRunCommandFormatterTest.kt`
- `docs/API_SPEC.md`
- `docs/STATUS.md`
- `docs/ROADMAP.md`
- `docs/blog/2026-05-31-dev-log.md`
- `docs/blog/topic-queue.md`

## Tasks

### Task 1: Baseline Verification

- [ ] Run:

```bash
cd backend
./gradlew test --tests com.sigak.collection.service.CollectionRunServiceTest \
  --tests com.sigak.collection.service.SourceCollectionServiceTest \
  --tests com.sigak.collection.controller.CollectionRunControllerTest \
  --tests 'com.sigak.collection.runner.*'
```

Expected: `BUILD SUCCESSFUL`.

### Task 2: Migration And Domain Model

- [ ] Write failing repository test in `CollectionFailureEventRecorderTest` that saves a failure event and reads it back.
- [ ] Run the test and verify RED because the entity/repository/table do not exist.
- [ ] Add `V3__collection_failure_events.sql`:

```sql
create table collection_failure_events (
    id bigserial primary key,
    run_id uuid not null,
    source_key varchar(120) not null,
    stage varchar(60) not null,
    failure_kind varchar(80) not null,
    retryable boolean not null,
    message text not null,
    fingerprint varchar(160) not null,
    article_external_id varchar(255),
    article_url text,
    article_title varchar(500),
    occurred_at timestamptz not null
);

create index ix_collection_failure_events_source_occurred
    on collection_failure_events(source_key, occurred_at desc);

create index ix_collection_failure_events_run_id
    on collection_failure_events(run_id);

create index ix_collection_failure_events_fingerprint_occurred
    on collection_failure_events(fingerprint, occurred_at desc);

create index ix_collection_failure_events_retryable_occurred
    on collection_failure_events(retryable, occurred_at desc);
```

- [ ] Add `CollectionFailureKind`, entity, repository, and recorder.
- [ ] Run recorder test and verify GREEN.
- [ ] Commit migration/domain slice.

### Task 3: Failure Classification

- [ ] Write failing classifier tests for:
  - fetch timeout/connection failure -> `TRANSIENT_FETCH`, `retryable=true`
  - fetch 500 or 429 -> `TRANSIENT_FETCH`, `retryable=true`
  - fetch 404 -> `SOURCE_FORMAT`, `retryable=false`
  - parse failure -> `SOURCE_FORMAT`, `retryable=false`
  - publish `IllegalArgumentException` -> `INVALID_ARTICLE`, `retryable=false`
  - publish persistence exception -> `PERSISTENCE`, `retryable=false`
- [ ] Run tests and verify RED.
- [ ] Implement `CollectionFailureClassifier`.
- [ ] Run tests and verify GREEN.
- [ ] Commit classifier slice.

### Task 4: Source-Level Failure Summaries

- [ ] Extend `CollectionFailureSummary` with:

```kotlin
val failureKind: CollectionFailureKind = CollectionFailureKind.UNKNOWN
val retryable: Boolean = false
val failureEventId: Long? = null
val articleExternalId: String? = null
val articleUrl: String? = null
val articleTitle: String? = null
```

- [ ] Add `runId: UUID` to `CollectionRunResponse`.
- [ ] Add failing `SourceCollectionServiceTest` cases for:
  - publish failure summary includes article hints
  - fetch failure is wrapped as `SourceCollectionException(FETCH_SOURCE)`
  - parse failure is wrapped as `SourceCollectionException(PARSE_SOURCE)`
- [ ] Run tests and verify RED.
- [ ] Inject `CollectionFailureClassifier` into `SourceCollectionService`.
- [ ] Wrap fetch/parse exceptions in `SourceCollectionException`.
- [ ] Add article hint fields to publish failure summaries.
- [ ] Run source collection tests and verify GREEN.
- [ ] Commit source summary slice.

### Task 5: Record Failure Events During Runs

- [ ] Add failing `CollectionRunServiceTest` cases for:
  - response contains non-blank `runId`
  - source-level failure is persisted and summary has `failureEventId`
  - article-level failure is persisted and summary has `failureEventId`
  - duplicate skips do not record failure events
- [ ] Run tests and verify RED.
- [ ] Inject `CollectionFailureClassifier` and `CollectionFailureEventRecorder` into `CollectionRunService`.
- [ ] Generate one `UUID` per `run()`.
- [ ] Persist source-level and article-level failure summaries.
- [ ] Return summaries with `failureEventId`.
- [ ] Run collection run service tests and verify GREEN.
- [ ] Commit run recording slice.

### Task 6: API, Command Runner, And Docs Compatibility

- [ ] Update controller and command runner formatter tests for `runId` and enriched failure summaries.
- [ ] Run focused tests and verify GREEN:

```bash
cd backend
./gradlew test --tests com.sigak.collection.controller.CollectionRunControllerTest \
  --tests 'com.sigak.collection.runner.*' \
  --tests com.sigak.collection.service.CollectionRunServiceTest \
  --tests com.sigak.collection.service.SourceCollectionServiceTest \
  --tests com.sigak.collection.service.CollectionFailureClassifierTest \
  --tests com.sigak.collection.service.CollectionFailureEventRecorderTest
```

- [ ] Update `docs/API_SPEC.md`, `docs/STATUS.md`, `docs/ROADMAP.md`, dev-log, and topic queue.
- [ ] Commit API/docs compatibility slice.

### Task 7: Final Verification

- [ ] Run:

```bash
cd backend
./gradlew test --rerun-tasks
./gradlew check
```

- [ ] Run:

```bash
git diff --check
git status --short --branch
```

- [ ] Optional runtime smoke if Docker is available:

```bash
docker compose -f infra/docker-compose.yml up -d --pull never postgres
cd backend
SIGAK_SEARCH_MODE=KEYWORD ./gradlew bootRun --args='collection-run --sources=missing-source --max=1'
```

Expected: invalid source should fail before collection. Do not claim runtime persistence unless a failure event is actually produced and queried.

- [ ] Report verified and unverified items.
