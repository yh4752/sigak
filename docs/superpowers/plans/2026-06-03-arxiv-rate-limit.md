# arXiv Rate Limit Handling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Sigak collection respect arXiv export API request spacing and recover from transient 429 responses.

**Architecture:** Keep the behavior at the source content fetch boundary. `HttpSourceContentFetcher` detects `export.arxiv.org`, serializes those requests, applies a configurable minimum interval, and retries 429 responses with bounded backoff.

**Tech Stack:** Kotlin, Spring Boot `RestClient`, Spring `@ConfigurationProperties`, JUnit 5, `MockRestServiceServer`.

---

## Reference Spec

- `docs/superpowers/specs/2026-06-03-arxiv-rate-limit-design.md`

## File Structure

- Modify: `backend/src/main/kotlin/com/sigak/collection/service/SourceCollectionService.kt`
  - Add arXiv-specific throttle/retry behavior in `HttpSourceContentFetcher`.
  - Add fake-able delay boundary for tests.
- Modify: `backend/src/main/kotlin/com/sigak/collection/config/CollectionHttpConfig.kt`
  - Add arXiv fetch properties and production delay/clock beans.
- Modify: `backend/src/main/resources/application.yml`
  - Expose arXiv policy defaults.
- Modify: `backend/src/test/resources/application.yml`
  - Keep test configuration explicit.
- Modify: `.env.example`
  - Document tunable arXiv policy environment variables.
- Create: `backend/src/test/kotlin/com/sigak/collection/service/HttpSourceContentFetcherTest.kt`
  - Verify throttle, non-arXiv bypass, 429 retry, and retry limit.
- Modify: `docs/STATUS.md`
  - Record current stabilization state and verified commands.
- Create/modify: `docs/blog/2026-06-03-arxiv-rate-limit-dev-log.md`, `docs/blog/topic-queue.md`
  - Record session facts and blog topic candidate.

## Tasks

- [x] **Step 1: Confirm isolation and scope**

Run:

```bash
git status --short --branch
git worktree list --porcelain
```

Expected: work happens on `codex/arxiv-rate-limit`; unrelated Neo4j plan/spec files remain untouched.

- [x] **Step 2: Inspect current fetch flow**

Read:

```bash
rg -n "SourceCollectionService|HttpSourceContentFetcher|CollectionFailureClassifier|SourceRegistry|arxiv" backend docs -S
```

Expected: fetcher has direct `RestClient` call; classifier marks 429 retryable but no retry execution exists.

- [x] **Step 3: Write failing tests**

Create `HttpSourceContentFetcherTest` with:

- consecutive arXiv URLs wait 3 seconds;
- non-arXiv URLs do not wait;
- arXiv 429 uses `Retry-After`;
- retry limit throws after configured attempts.

Run:

```bash
cd backend
./gradlew test --tests com.sigak.collection.service.HttpSourceContentFetcherTest
```

Expected RED: compile failure for missing `ArxivFetchProperties`, `SourceFetchDelay`, and constructor arguments.

- [x] **Step 4: Implement fetch policy**

Add:

- `ArxivFetchProperties`;
- `SourceFetchDelay` and `ThreadSourceFetchDelay`;
- `Clock` and delay beans;
- `HttpSourceContentFetcher` arXiv host detection, lock, minimum interval, 429 retry/backoff.

Run:

```bash
cd backend
./gradlew test --tests com.sigak.collection.service.HttpSourceContentFetcherTest
```

Expected GREEN: `BUILD SUCCESSFUL`.

- [x] **Step 5: Run collection regression tests**

Run:

```bash
cd backend
./gradlew test \
  --tests com.sigak.collection.service.HttpSourceContentFetcherTest \
  --tests com.sigak.collection.service.SourceCollectionServiceTest \
  --tests com.sigak.collection.service.CollectionRunServiceTest \
  --tests com.sigak.collection.runner.CollectionRunCommandRunnerTest \
  --tests com.sigak.collection.controller.CollectionRunControllerTest
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 6: Run backend Done gate**

Run:

```bash
cd backend
./gradlew test
./gradlew check
```

Expected: both commands return `BUILD SUCCESSFUL`.

- [x] **Step 7: Record session**

Update:

- `docs/STATUS.md`
- `docs/blog/2026-06-03-arxiv-rate-limit-dev-log.md`
- `docs/blog/topic-queue.md`

Report verified and `미검증` items separately.
