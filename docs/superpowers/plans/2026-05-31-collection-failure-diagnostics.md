# Collection Failure Diagnostics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a read-only internal endpoint for querying persisted collection failure events.

**Architecture:** Keep the controller thin and expose DTOs only. Add a service that validates query input and maps repository results, while the repository owns the filtered latest-first database query.

**Tech Stack:** Kotlin, Spring Boot MVC, Spring Data JPA, PostgreSQL/Testcontainers, JUnit 5, MockMvc.

---

## Reference Spec

- `docs/superpowers/specs/2026-05-31-collection-failure-diagnostics-design.md`

## Files

Create:

- `backend/src/main/kotlin/com/sigak/collection/controller/CollectionFailureEventController.kt`
- `backend/src/main/kotlin/com/sigak/collection/dto/CollectionFailureEventQuery.kt`
- `backend/src/main/kotlin/com/sigak/collection/service/CollectionFailureEventQueryService.kt`
- `backend/src/test/kotlin/com/sigak/collection/controller/CollectionFailureEventControllerTest.kt`
- `backend/src/test/kotlin/com/sigak/collection/service/CollectionFailureEventQueryServiceTest.kt`

Modify:

- `backend/src/main/kotlin/com/sigak/collection/repository/CollectionFailureEventRepository.kt`
- `docs/API_SPEC.md`
- `docs/STATUS.md`
- `docs/ROADMAP.md`
- `docs/blog/2026-05-31-dev-log.md`
- `docs/blog/topic-queue.md`

## Tasks

### Task 1: Baseline

- [x] Run focused existing tests:

```bash
cd backend
./gradlew test --tests com.sigak.collection.service.CollectionFailureEventRecorderTest \
  --tests com.sigak.collection.controller.CollectionRunControllerTest
```

Expected: `BUILD SUCCESSFUL`.

### Task 2: Controller Contract

- [x] Write failing `CollectionFailureEventControllerTest` for:
  - `GET /api/internal/collections/failure-events` returns event JSON.
  - service `IllegalArgumentException` becomes `400`.
- [x] Run the controller test and verify RED because the controller/service DTOs do not exist.
- [x] Add DTOs, controller, and a minimal service interface/class to pass the controller test.
- [x] Run the controller test and verify GREEN.

### Task 3: Repository And Service Query

- [x] Write failing `CollectionFailureEventQueryServiceTest` using persisted events.
- [x] Run it and verify RED because the repository search method is missing.
- [x] Add repository filtered latest-first query and service mapping.
- [x] Run the service test and verify GREEN.

### Task 4: Docs And Session Record

- [x] Document the endpoint in `docs/API_SPEC.md`.
- [x] Update `docs/STATUS.md` and `docs/ROADMAP.md`.
- [x] Update the 2026-05-31 dev-log and topic queue using only verified facts.

### Task 5: Final Verification

- [x] Run:

```bash
cd backend
./gradlew test --rerun-tasks
./gradlew check
```

- [x] Run:

```bash
git diff --check
git status --short --branch
```

- [ ] Commit and push the branch.
