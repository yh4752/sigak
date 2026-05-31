# Collection Persistence Refactor Design

## Summary

This refactor prepares the collection operations work without adding the
controlled trigger, status transitions, retries, or run metrics yet. The smallest
useful slice is `CollectedArticlePersistenceService`, because future collection
run observability will need to distinguish these steps clearly:

```txt
source resolve
-> published timestamp and URL normalization
-> duplicate lookup
-> article aggregate assembly
-> save and return article ID
```

This is a behavior-preserving refactor. It should make the persistence flow
easier to read and safer to extend, but it must not change duplicate detection,
public article response behavior, enrichment mapping, topic trimming/deduping, or
the current mock enrichment metadata.

## Current State

`CollectedArticlePersistenceService.publish()` currently handles several
responsibilities in one method:

- source lookup or creation
- published date parsing
- canonical URL and URL fallback normalization
- duplicate lookup by URL, external ID, and source/title/published date
- `ArticleEntity` construction
- raw content, enrichment, and topic attachment
- persistence and ID return

The behavior is covered by integration tests that verify a collected article can
be published into the public article API and that duplicate canonical URLs return
the existing article ID.

The method is still small enough to avoid new production abstractions, but the
sequence is now important enough to make explicit before adding collection
trigger counts such as fetched, published, skipped, and failed.

## Design Goals

- Keep the public `CollectedArticlePublisher.publish()` contract unchanged.
- Preserve duplicate detection order and returned ID semantics.
- Keep PostgreSQL persistence as the source-of-truth write path.
- Make aggregate assembly readable without introducing a generic factory,
  mapper framework, or orchestration layer.
- Keep helper methods private and domain-specific.
- Leave collection trigger/run metric implementation for a later plan.

## Non-Goals

- Do not add an admin/internal collection trigger.
- Do not add collection run result DTOs or status transitions.
- Do not add retries or failure storage.
- Do not switch enrichment from mock/local behavior to FastAPI HTTP mode.
- Do not split this into new files unless the service remains hard to read after
  private helper extraction.

## Behavior Contracts To Preserve

- `publish(article, enrichment)` returns the saved article ID.
- If a duplicate exists, `publish()` returns the existing article ID and does not
  create a new article row.
- Duplicate lookup order stays:
  1. canonical URL or URL
  2. source key + external ID
  3. source key + title + published date
- Blank `canonicalUrl` falls back to `url`; blank `url` falls back to normalized
  `canonicalUrl`.
- Invalid published dates are preserved as `Instant.EPOCH` for MVP sorting
  stability.
- Enrichment importance score remains clamped to `0..100`.
- Topics are trimmed, blank values removed, deduplicated, limited to eight, and
  stored with stable positions.
- The public article API can still read the persisted article with raw content,
  current enrichment, and topics.

## Proposed Refactoring Boundary

Keep `CollectedArticlePersistenceService` as the persistence writer, but make the
publish flow read like the domain sequence:

```kotlin
val source = findOrCreateSource(article)
val identity = persistenceIdentityFor(article)
val duplicate = findDuplicateArticle(source.sourceKey, article, identity)
if (duplicate != null) return requireNotNull(duplicate.id)

val savedArticle = buildArticleEntity(source, article, enrichment, identity)
attachRawContent(savedArticle, article)
attachCurrentEnrichment(savedArticle, enrichment)
attachTopics(savedArticle, enrichment)

return requireNotNull(articleRepository.save(savedArticle).id)
```

The only new type should be a small private value object if it reduces repeated
parameters:

```kotlin
private data class CollectedArticlePersistenceIdentity(
    val canonicalUrl: String,
    val url: String,
    val publishedAt: Instant
)
```

This avoids a broader mapper abstraction while removing the most error-prone
parameter passing.

## Blog Topic Queue Rule

Update `docs/blog/topic-queue.md` if the work reveals at least two of these:

- The collection persistence responsibility boundary became clearer.
- A larger abstraction was intentionally avoided.
- A test gap or risky coupling was found.
- A future collection trigger metric became easier to explain.
- A new developer handoff lesson emerged.

## Verification Strategy

- Run focused collection persistence tests before editing.
- Run focused collection persistence tests after editing.
- Run full backend verification before completion:
  - `cd backend && ./gradlew test --rerun-tasks`
  - `cd backend && ./gradlew check`
- Run `git diff --check`.

No Docker smoke check is required because this refactor does not change Docker
services, external APIs, or runtime endpoint contracts.
