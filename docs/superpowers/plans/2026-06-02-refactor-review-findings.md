# Refactor Review Findings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce review-identified duplication and related article HTTP N+1 without changing the public article response shape.

**Architecture:** Keep controllers thin and delegate article loading to `ArticleService`. Add small common helpers only where duplication already crosses service boundaries. Keep Spring local mock enrichment as the current collection default and move provider metadata into the enrichment response.

**Tech Stack:** Kotlin Spring Boot, React TypeScript Vite, FastAPI/Pydantic, Gradle, Vitest, pytest.

---

## Reference Spec

- `docs/superpowers/specs/2026-06-02-refactor-review-findings-design.md`

## File Structure

- Create `backend/src/main/kotlin/com/sigak/common/time/ElapsedMeasurement.kt`: elapsed measurement helper.
- Create `backend/src/main/kotlin/com/sigak/collection/service/PublishedAtParser.kt`: collection published date parser.
- Modify `backend/src/main/kotlin/com/sigak/article/controller/ArticleController.kt`: support optional `ids` query parameter.
- Modify `backend/src/main/kotlin/com/sigak/article/service/ArticleService.kt`: use repository graph helper and common timing helper.
- Modify `backend/src/main/kotlin/com/sigak/article/repository/ArticleRepository.kt`: move article response graph prefetch responsibility into repository.
- Modify collection services and DTO/schema files for parser and `modelName`.
- Modify frontend article API and detail page to fetch related articles through bulk API.
- Update API/docs/status/dev-log after verified implementation.

## Tasks

### Task 1: Backend Article Bulk ID API

- [x] Add failing controller test for `GET /api/articles?ids=4,1,4,999`.
- [x] Run `cd backend && ./gradlew test --tests com.sigak.article.controller.ArticleControllerTest`.
- [x] Add `ids` request parameter and call `ArticleService.getApiReadyArticlesByIds`.
- [x] Run the focused controller test until it passes.

### Task 2: Frontend Related Article Bulk Fetch

- [x] Add failing API client test for `fetchArticlesByIds([4, 1, 4])`.
- [x] Add failing detail page test that related articles use one bulk call instead of per-ID calls.
- [x] Run `cd frontend && npm test -- articles.test.ts ArticleDetailPage.test.tsx`.
- [x] Implement `fetchArticlesByIds` and update `ArticleDetailPage`.
- [x] Run the focused frontend tests until they pass.

### Task 3: Shared Date Parser

- [x] Add failing tests for ISO, RFC1123, blank passthrough, and epoch fallback.
- [x] Run `cd backend && ./gradlew test --tests com.sigak.collection.service.PublishedAtParserTest`.
- [x] Implement `PublishedAtParser` and replace duplicated parser code in normalizer and persistence service.
- [x] Run focused collection tests.

### Task 4: Timing Helper and Repository Graph Loading

- [x] Add the common elapsed measurement helper.
- [x] Move article response graph prefetch from service extension to repository method.
- [x] Replace local `Measured`, `measureElapsed`, and `elapsedMillis` duplicates in touched services.
- [x] Run focused article/search tests.

### Task 5: Enrichment Metadata, DI Cleanup, and HTTP Timeout

- [x] Add `modelName` to Kotlin and Python enrichment response models and tests.
- [x] Persist enrichment `modelName` from response instead of a hardcoded persistence string.
- [x] Remove `CollectionRunService` concrete default constructor values and update tests.
- [x] Add collection HTTP client timeout properties and wire `HttpSourceContentFetcher` through a configured `RestClient`.
- [x] Run focused backend and AI tests.

### Task 6: Verification and Session Record

- [x] Run backend Definition of Done commands.
- [x] Run frontend Definition of Done commands.
- [x] Run AI pytest if AI schema changed.
- [x] Update `docs/API_SPEC.md`, `docs/API_SPEC.ko.md`, `docs/STATUS.md`, `docs/STATUS.ko.md`, and roadmap only if scope changed.
- [x] Write or update `docs/blog/2026-06-02-dev-log.md`.
- [x] Run topic-queue routine and update `docs/blog/topic-queue.md`.
