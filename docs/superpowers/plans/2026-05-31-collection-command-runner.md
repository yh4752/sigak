# Collection Command Runner Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a command runner wrapper that executes `CollectionRunService` from a terminal command and prints verifiable collection counts.

**Architecture:** Keep collection business logic in `CollectionRunService`. Add a small `collection/runner` package for argument parsing, summary formatting, and Spring Boot command execution. Do not add scheduling, persistent run history, projection rebuild chaining, or a second collection path.

**Tech Stack:** Kotlin, Spring Boot `ApplicationRunner`, JUnit 5, Gradle bootRun, Markdown documentation.

---

## Reference Spec

- `docs/superpowers/specs/2026-05-31-collection-command-runner-design.md`

## Tasks

### Task 1: Baseline

**Files:**
- Read: `backend/src/main/kotlin/com/sigak/collection/service/CollectionRunService.kt`
- Read: `backend/src/main/kotlin/com/sigak/collection/dto/CollectionRunRequest.kt`
- Read: `backend/src/main/kotlin/com/sigak/collection/dto/CollectionRunResponse.kt`

- [ ] Run focused collection tests:

```bash
cd backend
./gradlew test --tests com.sigak.collection.service.CollectionRunServiceTest --tests com.sigak.collection.controller.CollectionRunControllerTest
```

Expected: `BUILD SUCCESSFUL`.

### Task 2: Parser And Formatter

**Files:**
- Create: `backend/src/main/kotlin/com/sigak/collection/runner/CollectionRunCommand.kt`
- Create: `backend/src/main/kotlin/com/sigak/collection/runner/CollectionRunCommandParser.kt`
- Create: `backend/src/main/kotlin/com/sigak/collection/runner/CollectionRunCommandFormatter.kt`
- Create: `backend/src/test/kotlin/com/sigak/collection/runner/CollectionRunCommandParserTest.kt`
- Create: `backend/src/test/kotlin/com/sigak/collection/runner/CollectionRunCommandFormatterTest.kt`

- [ ] Write failing parser tests for non-command args, sources parsing, max parsing, unknown options, and invalid max.
- [ ] Run parser tests and verify RED.
- [ ] Implement parser model and parser.
- [ ] Run parser tests and verify GREEN.
- [ ] Write failing formatter tests for successful count output and failure summary output.
- [ ] Run formatter tests and verify RED.
- [ ] Implement formatter.
- [ ] Run parser/formatter tests and verify GREEN.
- [ ] Commit parser and formatter.

### Task 3: Spring Boot Runner

**Files:**
- Create: `backend/src/main/kotlin/com/sigak/collection/runner/ApplicationExit.kt`
- Create: `backend/src/main/kotlin/com/sigak/collection/runner/CollectionRunCommandRunner.kt`
- Create: `backend/src/test/kotlin/com/sigak/collection/runner/CollectionRunCommandRunnerTest.kt`

- [ ] Write failing runner tests showing non-command args do nothing, command args call `CollectionRunService`, successful command exits `0`, invalid command exits `1`.
- [ ] Run runner tests and verify RED.
- [ ] Implement runner and exit boundary.
- [ ] Run runner tests and verify GREEN.
- [ ] Run focused collection runner/service/controller tests.
- [ ] Commit runner.

### Task 4: Docs And Runtime Smoke

**Files:**
- Modify: `docs/ONBOARDING.ko.md`
- Modify: `docs/STATUS.md`
- Modify: `docs/ROADMAP.md`
- Modify: `docs/blog/2026-05-31-dev-log.md`
- Modify: `docs/blog/topic-queue.md`

- [ ] Document the command runner usage.
- [ ] Update status and roadmap to mark command runner wrapper complete.
- [ ] Run backend full test and check.
- [ ] Run command runner smoke with PostgreSQL compose and `github-blog --max=1`.
- [ ] Record actual observed counts only.
- [ ] Commit docs.

### Task 5: Final Verification

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

- [ ] Report verified and unverified items.
