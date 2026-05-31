# Collection Persistence Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve current collection persistence behavior while making the publish flow easier to extend for collection trigger observability.

**Architecture:** Keep the existing Spring Boot service boundary. Refactor inside `CollectedArticlePersistenceService` using private domain helpers and one private value object; do not introduce a new mapper, factory, or orchestration layer.

**Tech Stack:** Kotlin, Spring Boot, JUnit, Testcontainers PostgreSQL, Markdown documentation.

---

## Reference Spec

- `docs/superpowers/specs/2026-05-31-collection-persistence-refactor-design.md`

## File Structure

Modify:

- `backend/src/main/kotlin/com/sigak/collection/service/CollectedArticlePersistenceService.kt`
  - Clarify the publish sequence: source resolve, identity normalization,
    duplicate lookup, aggregate assembly, save.
- `docs/blog/topic-queue.md`
  - Add or update a concrete blog topic candidate if the refactor produces a
    useful lesson.
- `docs/blog/2026-05-31-dev-log.md`
  - Record the session using only verified commands and observed results.
- `docs/superpowers/plans/2026-05-31-collection-persistence-refactor.md`
  - Track task completion checkboxes as work progresses.

Read/Test:

- `backend/src/test/kotlin/com/sigak/collection/service/CollectedArticlePersistenceServiceTest.kt`
- `backend/src/test/kotlin/com/sigak/collection/service/CollectionPipelineServiceTest.kt`

## Invariants

- Keep `CollectedArticlePublisher.publish(article, enrichment): Long` unchanged.
- Keep duplicate lookup order unchanged: URL, then source/external ID, then
  source/title/published date.
- Keep topic trimming, deduplication, limit, and ordering unchanged.
- Keep mock enrichment metadata values unchanged.
- Do not add collection trigger, status transitions, run metrics, or retries.

## Tasks

### Task 1: Baseline Verification

**Files:**
- Read: `docs/superpowers/specs/2026-05-31-collection-persistence-refactor-design.md`
- Read: `backend/src/main/kotlin/com/sigak/collection/service/CollectedArticlePersistenceService.kt`
- Test: `backend/src/test/kotlin/com/sigak/collection/service/CollectedArticlePersistenceServiceTest.kt`

- [x] **Step 1: Verify branch state**

Run:

```bash
git status --short --branch
```

Expected: branch `codex/refactor-onboarding-search` with only this plan/spec work
before code edits.

- [x] **Step 2: Run focused baseline tests**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.collection.service.CollectedArticlePersistenceServiceTest \
  --tests com.sigak.collection.service.CollectionPipelineServiceTest
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 3: Record constraints**

Before editing production code, confirm the refactor will preserve duplicate
detection, public API-readability of persisted articles, and enrichment/topic
mapping behavior.

### Task 2: Refactor Publish Flow

**Files:**
- Modify: `backend/src/main/kotlin/com/sigak/collection/service/CollectedArticlePersistenceService.kt`

- [x] **Step 1: Add a private identity value object**

Add this private data class inside `CollectedArticlePersistenceService`:

```kotlin
private data class CollectedArticlePersistenceIdentity(
    val canonicalUrl: String,
    val url: String,
    val publishedAt: Instant
)
```

- [x] **Step 2: Extract identity normalization**

Add this helper and use it from `publish()`:

```kotlin
private fun persistenceIdentityFor(article: CollectedArticle): CollectedArticlePersistenceIdentity {
    val canonicalUrl = article.canonicalUrl.ifBlank { article.url }.trim()
    val url = article.url.ifBlank { canonicalUrl }.trim()
    return CollectedArticlePersistenceIdentity(
        canonicalUrl = canonicalUrl,
        url = url,
        publishedAt = parsePublishedAt(article.publishedAt)
    )
}
```

- [x] **Step 3: Pass identity to duplicate lookup**

Change the duplicate lookup signature to:

```kotlin
private fun findDuplicateArticle(
    sourceKey: String,
    article: CollectedArticle,
    identity: CollectedArticlePersistenceIdentity
): ArticleEntity?
```

Use `identity.canonicalUrl`, `identity.url`, and `identity.publishedAt` inside
the method. Keep lookup order unchanged.

- [x] **Step 4: Extract aggregate assembly helpers**

Extract private helpers with these responsibilities:

```kotlin
private fun buildArticleEntity(
    source: NewsSourceEntity,
    article: CollectedArticle,
    enrichment: EnrichmentResponse,
    identity: CollectedArticlePersistenceIdentity
): ArticleEntity

private fun attachRawContent(savedArticle: ArticleEntity, article: CollectedArticle)

private fun attachCurrentEnrichment(savedArticle: ArticleEntity, enrichment: EnrichmentResponse)

private fun attachTopics(savedArticle: ArticleEntity, enrichment: EnrichmentResponse)
```

Keep the same entity fields and timestamp behavior. Do not move logic into a new
production class.

- [x] **Step 5: Keep publish as orchestration**

After extraction, `publish()` should read in this order:

```txt
source resolve
identity normalize
duplicate lookup
article aggregate build
raw/enrichment/topics attach
save
```

### Task 3: Focused Verification

**Files:**
- Test: `backend/src/test/kotlin/com/sigak/collection/service/CollectedArticlePersistenceServiceTest.kt`
- Test: `backend/src/test/kotlin/com/sigak/collection/service/CollectionPipelineServiceTest.kt`

- [x] **Step 1: Run focused tests after refactor**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.collection.service.CollectedArticlePersistenceServiceTest \
  --tests com.sigak.collection.service.CollectionPipelineServiceTest
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 2: Inspect diff for behavior drift**

Run:

```bash
git diff -- backend/src/main/kotlin/com/sigak/collection/service/CollectedArticlePersistenceService.kt
```

Expected: method extraction only; no changed repository calls, no changed DTO/entity
field mapping, no changed topic normalization policy.

### Task 4: Record Refactor Lesson

**Files:**
- Modify: `docs/blog/topic-queue.md`
- Modify: `docs/superpowers/plans/2026-05-31-collection-persistence-refactor.md`

- [x] **Step 1: Update topic queue if criteria match**

Add a candidate if the refactor demonstrates at least two queue criteria. Use
this concrete title if appropriate:

```md
## [candidate] Collection trigger를 만들기 전에 persistence 흐름을 정리한 이유
```

The entry should mention the files changed and the actual verification commands.

- [x] **Step 2: Mark this plan's completed steps**

Update checkboxes in this plan as each task is completed.

### Task 5: Final Backend Verification And Commit

**Files:**
- Verify: backend
- Verify: repository diff

- [x] **Step 1: Run full backend tests**

Run:

```bash
cd backend
./gradlew test --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 2: Run backend check**

Run:

```bash
cd backend
./gradlew check
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 3: Run whitespace diff check**

Run:

```bash
git diff --check
```

Expected: no output and exit code `0`.

- [x] **Step 4: Review final status**

Run:

```bash
git status --short
```

Expected: only the planned files and the session dev-log are modified or added.

- [x] **Step 5: Commit**

Run:

```bash
git add docs/superpowers/specs/2026-05-31-collection-persistence-refactor-design.md \
  docs/superpowers/plans/2026-05-31-collection-persistence-refactor.md \
  backend/src/main/kotlin/com/sigak/collection/service/CollectedArticlePersistenceService.kt \
  docs/blog/topic-queue.md \
  docs/blog/2026-05-31-dev-log.md
git commit -m "refactor: clarify collection persistence flow"
```

Expected: commit succeeds on `codex/refactor-onboarding-search`.
