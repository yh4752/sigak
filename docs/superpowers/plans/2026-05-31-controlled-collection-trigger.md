# Controlled Collection Trigger Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a controlled internal collection run trigger that can execute selected registered sources and report fetched, published, skipped, and failed counts without adding scheduling or persistent run history.

**Architecture:** Keep the trigger as an internal Spring Boot API under `/api/internal/collections/runs`. Put orchestration in `CollectionRunService`, keep one-source fetch/parse/publish in `SourceCollectionService`, and return a publish outcome from the persistence boundary so duplicate skips are counted honestly. Do not add background jobs, a database table for run history, or automatic search projection rebuilds in this slice.

**Tech Stack:** Kotlin, Spring Boot MVC, JUnit 5, MockMvc, Testcontainers PostgreSQL, Markdown documentation.

---

## Reference Spec

- `docs/superpowers/specs/2026-05-31-controlled-collection-trigger-design.md`

## File Structure

Create:

- `backend/src/main/kotlin/com/sigak/collection/service/CollectedArticlePublishResult.kt`
  - Internal publish outcome contract used by `CollectionPipelineService`, `CollectedArticlePublisher`, and `CollectedArticlePersistenceService`.
- `backend/src/main/kotlin/com/sigak/collection/dto/CollectionRunRequest.kt`
  - Internal collection run request DTO.
- `backend/src/main/kotlin/com/sigak/collection/service/CollectionRunService.kt`
  - Select sources, validate request, execute one-source collection sequentially, aggregate counts, and compute run status.
- `backend/src/main/kotlin/com/sigak/collection/controller/CollectionRunController.kt`
  - Expose `POST /api/internal/collections/runs`.
- `backend/src/test/kotlin/com/sigak/collection/service/CollectionRunServiceTest.kt`
  - Unit tests for source selection, validation, aggregation, and partial failure behavior.
- `backend/src/test/kotlin/com/sigak/collection/controller/CollectionRunControllerTest.kt`
  - MockMvc tests for request/response serialization and bad request handling.

Modify:

- `backend/src/main/kotlin/com/sigak/collection/service/CollectionPipelineService.kt`
  - Change `CollectedArticlePublisher.publish` and `CollectionPipelineService.publish` from `Long` to `CollectedArticlePublishResult`.
- `backend/src/main/kotlin/com/sigak/collection/service/CollectedArticlePersistenceService.kt`
  - Return `PUBLISHED` or `SKIPPED_DUPLICATE`.
- `backend/src/main/kotlin/com/sigak/collection/service/SourceCollectionService.kt`
  - Add `SourceCollector` interface, add `maxArticlesPerSource`, separate published/skipped IDs, cap article failure summaries.
- `backend/src/main/kotlin/com/sigak/collection/dto/CollectionRunResponse.kt`
  - Internal collection run response DTO, source result DTO, status enums, and failure summary DTO.
- `backend/src/test/kotlin/com/sigak/collection/service/CollectionPipelineServiceTest.kt`
  - Assert publish outcome propagation.
- `backend/src/test/kotlin/com/sigak/collection/service/CollectedArticlePersistenceServiceTest.kt`
  - Assert new saves return `PUBLISHED` and duplicates return `SKIPPED_DUPLICATE`.
- `backend/src/test/kotlin/com/sigak/collection/service/SourceCollectionServiceTest.kt`
  - Assert published/skipped separation and article limit behavior.
- `docs/API_SPEC.md`
  - Document the new internal collection run endpoint.
- `docs/STATUS.md`
  - Update collection trigger status after implementation verification.
- `docs/ROADMAP.md`
  - Mark the controlled trigger item as implemented or clarify that command runner remains deferred.
- `docs/ONBOARDING.ko.md`
  - Add the internal collection trigger to local operation commands.
- `docs/blog/topic-queue.md`
  - Add or update a topic candidate for collection run count semantics.
- `docs/blog/2026-05-31-dev-log.md`
  - Append verified implementation facts for this session.

## Invariants

- Do not add `@Scheduled`.
- Do not add a `collection_runs` persistence table.
- Do not add a frontend page.
- Do not rebuild Elasticsearch, Qdrant, or Neo4j automatically after collection.
- Do not switch enrichment to FastAPI HTTP mode.
- Keep duplicate detection order in `CollectedArticlePersistenceService`: URL, then source external ID, then source/title/published date.
- Keep source collection sequential for the MVP.
- Unknown source IDs must fail before any source is fetched.

## Tasks

### Task 1: Baseline Verification

**Files:**
- Read: `docs/superpowers/specs/2026-05-31-controlled-collection-trigger-design.md`
- Read: `backend/src/main/kotlin/com/sigak/collection/service/CollectionPipelineService.kt`
- Read: `backend/src/main/kotlin/com/sigak/collection/service/SourceCollectionService.kt`
- Read: `backend/src/main/kotlin/com/sigak/collection/service/CollectedArticlePersistenceService.kt`

- [ ] **Step 1: Verify branch state**

Run:

```bash
git status --short --branch
```

Expected: branch `codex/refactor-onboarding-search`. Only this plan file should be uncommitted before implementation starts.

- [ ] **Step 2: Run current collection tests**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.collection.service.CollectionPipelineServiceTest \
  --tests com.sigak.collection.service.SourceCollectionServiceTest \
  --tests com.sigak.collection.service.CollectedArticlePersistenceServiceTest \
  --tests com.sigak.collection.service.SourceRegistryTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Record implementation constraints**

Before editing code, restate these constraints in the working summary:

```txt
internal endpoint first
command runner deferred
duplicate attempts count as skipped
projection rebuild remains explicit
no persistent run history
```

### Task 2: Publish Outcome Contract

**Files:**
- Create: `backend/src/main/kotlin/com/sigak/collection/service/CollectedArticlePublishResult.kt`
- Modify: `backend/src/main/kotlin/com/sigak/collection/service/CollectionPipelineService.kt`
- Modify: `backend/src/main/kotlin/com/sigak/collection/service/CollectedArticlePersistenceService.kt`
- Test: `backend/src/test/kotlin/com/sigak/collection/service/CollectionPipelineServiceTest.kt`
- Test: `backend/src/test/kotlin/com/sigak/collection/service/CollectedArticlePersistenceServiceTest.kt`

- [ ] **Step 1: Write failing publish outcome tests**

Update `CollectionPipelineServiceTest.publishCollectedArticleEnrichesAndPersistsArticle` so it expects a publish result:

```kotlin
private val pipelineService = CollectionPipelineService(
    articleNormalizer = normalizer,
    enrichmentClient = { request ->
        EnrichmentResponse(
            summary = "${request.title} summary",
            whyItMatters = "${request.source} insight",
            suggestedTopics = request.topics,
            suggestedPrimaryCategory = request.topics.first(),
            suggestedImportanceScore = 70
        )
    },
    collectedArticlePublisher = { _, _ ->
        publishedArticleIds.add(42L)
        CollectedArticlePublishResult(
            articleId = 42L,
            outcome = CollectedArticlePublishOutcome.PUBLISHED
        )
    }
)
```

Replace the assertion body with:

```kotlin
val result = pipelineService.publish(collectedArticle)

assertEquals(42L, result.articleId)
assertEquals(CollectedArticlePublishOutcome.PUBLISHED, result.outcome)
assertEquals(listOf(42L), publishedArticleIds)
```

Update `CollectedArticlePersistenceServiceTest.publishStoresCollectedArticleEnrichmentAndRawContentForPublicApi`:

```kotlin
val result = persistenceService.publish(collectedArticle, enrichment)
val publishedArticleId = result.articleId

assertEquals(CollectedArticlePublishOutcome.PUBLISHED, result.outcome)
```

Update `CollectedArticlePersistenceServiceTest.publishReturnsExistingArticleWhenCanonicalUrlAlreadyExists`:

```kotlin
val firstResult = persistenceService.publish(collectedArticle, enrichment)
val secondResult = persistenceService.publish(
    collectedArticle.copy(externalId = "http://arxiv.org/abs/2605.99992v2"),
    enrichment
)

assertEquals(CollectedArticlePublishOutcome.PUBLISHED, firstResult.outcome)
assertEquals(CollectedArticlePublishOutcome.SKIPPED_DUPLICATE, secondResult.outcome)
assertEquals(firstResult.articleId, secondResult.articleId)
```

- [ ] **Step 2: Run tests to verify red**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.collection.service.CollectionPipelineServiceTest \
  --tests com.sigak.collection.service.CollectedArticlePersistenceServiceTest
```

Expected: FAIL at Kotlin compilation with unresolved `CollectedArticlePublishResult` or type mismatch from `Long` to `CollectedArticlePublishResult`.

- [ ] **Step 3: Add publish outcome contract**

Create `backend/src/main/kotlin/com/sigak/collection/service/CollectedArticlePublishResult.kt`:

```kotlin
package com.sigak.collection.service

enum class CollectedArticlePublishOutcome {
    PUBLISHED,
    SKIPPED_DUPLICATE
}

data class CollectedArticlePublishResult(
    val articleId: Long,
    val outcome: CollectedArticlePublishOutcome
)
```

- [ ] **Step 4: Update pipeline publish signatures**

In `CollectionPipelineService.kt`, change the publisher interface and publish method:

```kotlin
fun interface CollectedArticlePublisher {
    fun publish(article: CollectedArticle, enrichment: EnrichmentResponse): CollectedArticlePublishResult
}
```

```kotlin
fun publish(article: CollectedArticle): CollectedArticlePublishResult {
    val enrichment = enrich(article)
    return collectedArticlePublisher.publish(article, enrichment)
}
```

- [ ] **Step 5: Return outcome from persistence service**

In `CollectedArticlePersistenceService.publish`, change the return type and duplicate/new save returns:

```kotlin
override fun publish(article: CollectedArticle, enrichment: EnrichmentResponse): CollectedArticlePublishResult {
    val source = findOrCreateSource(article)
    val identity = persistenceIdentityFor(article)

    val duplicate = findDuplicateArticle(source.sourceKey, article, identity)
    if (duplicate != null) {
        return CollectedArticlePublishResult(
            articleId = requireNotNull(duplicate.id),
            outcome = CollectedArticlePublishOutcome.SKIPPED_DUPLICATE
        )
    }

    val savedArticle = buildArticleEntity(source, article, enrichment, identity)
    attachRawContent(savedArticle, article)
    attachCurrentEnrichment(savedArticle, enrichment)
    attachTopics(savedArticle, enrichment)

    return CollectedArticlePublishResult(
        articleId = requireNotNull(articleRepository.save(savedArticle).id),
        outcome = CollectedArticlePublishOutcome.PUBLISHED
    )
}
```

- [ ] **Step 6: Run focused tests to verify green**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.collection.service.CollectionPipelineServiceTest \
  --tests com.sigak.collection.service.CollectedArticlePersistenceServiceTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit publish outcome contract**

Run:

```bash
git add backend/src/main/kotlin/com/sigak/collection/service/CollectedArticlePublishResult.kt \
  backend/src/main/kotlin/com/sigak/collection/service/CollectionPipelineService.kt \
  backend/src/main/kotlin/com/sigak/collection/service/CollectedArticlePersistenceService.kt \
  backend/src/test/kotlin/com/sigak/collection/service/CollectionPipelineServiceTest.kt \
  backend/src/test/kotlin/com/sigak/collection/service/CollectedArticlePersistenceServiceTest.kt
git commit -m "feat: return collection publish outcomes"
```

Expected: commit succeeds.

### Task 3: Source-Level Collection Outcomes

**Files:**
- Create: `backend/src/main/kotlin/com/sigak/collection/dto/CollectionRunResponse.kt`
- Modify: `backend/src/main/kotlin/com/sigak/collection/service/SourceCollectionService.kt`
- Test: `backend/src/test/kotlin/com/sigak/collection/service/SourceCollectionServiceTest.kt`

- [ ] **Step 1: Write failing source collection tests**

In `SourceCollectionServiceTest`, update the fake publisher to return publish results:

```kotlin
collectedArticlePublisher = { article, _ ->
    publishedTitles.add(article.title)
    CollectedArticlePublishResult(
        articleId = 101L,
        outcome = CollectedArticlePublishOutcome.PUBLISHED
    )
}
```

Update the existing test assertions:

```kotlin
assertEquals(1, result.discoveredCount)
assertEquals(listOf(101L), result.publishedArticleIds)
assertEquals(emptyList(), result.skippedArticleIds)
assertEquals(0, result.failedCount)
assertEquals(emptyList(), result.failureSummaries)
```

Add this test for duplicate skips:

```kotlin
@Test
fun collectSeparatesDuplicateSkippedArticlesFromPublishedArticles() {
    val service = SourceCollectionService(
        sourceContentFetcher = { rssXml() },
        rssAtomCollector = RssAtomCollector(),
        arxivCollector = ArxivCollector(),
        collectionPipelineService = CollectionPipelineService(
            articleNormalizer = ArticleNormalizer(),
            enrichmentClient = { request ->
                EnrichmentResponse(
                    summary = "${request.title} summary",
                    whyItMatters = "${request.source} insight",
                    suggestedTopics = request.topics,
                    suggestedPrimaryCategory = request.topics.first(),
                    suggestedImportanceScore = 70
                )
            },
            collectedArticlePublisher = { _, _ ->
                CollectedArticlePublishResult(
                    articleId = 77L,
                    outcome = CollectedArticlePublishOutcome.SKIPPED_DUPLICATE
                )
            }
        )
    )

    val result = service.collect(source(), maxArticlesPerSource = 10)

    assertEquals(emptyList(), result.publishedArticleIds)
    assertEquals(listOf(77L), result.skippedArticleIds)
    assertEquals(0, result.failedCount)
}
```

Add this test for max article limiting:

```kotlin
@Test
fun collectAppliesMaxArticlesPerSourceBeforePublishing() {
    val publishedTitles = mutableListOf<String>()
    val service = SourceCollectionService(
        sourceContentFetcher = { rssXmlWithTwoItems() },
        rssAtomCollector = RssAtomCollector(),
        arxivCollector = ArxivCollector(),
        collectionPipelineService = CollectionPipelineService(
            articleNormalizer = ArticleNormalizer(),
            enrichmentClient = { request ->
                EnrichmentResponse(
                    summary = "${request.title} summary",
                    whyItMatters = "${request.source} insight",
                    suggestedTopics = request.topics,
                    suggestedPrimaryCategory = request.topics.first(),
                    suggestedImportanceScore = 70
                )
            },
            collectedArticlePublisher = { article, _ ->
                publishedTitles.add(article.title)
                CollectedArticlePublishResult(
                    articleId = publishedTitles.size.toLong(),
                    outcome = CollectedArticlePublishOutcome.PUBLISHED
                )
            }
        )
    )

    val result = service.collect(source(), maxArticlesPerSource = 1)

    assertEquals(1, result.discoveredCount)
    assertEquals(listOf("Reliable Builds for AI Toolchains"), publishedTitles)
    assertEquals(listOf(1L), result.publishedArticleIds)
}
```

Add these helpers to the test:

```kotlin
private fun source(): NewsSource =
    NewsSource(
        id = "example-feed",
        name = "Example Engineering Blog",
        type = SourceType.RSS_ATOM,
        url = "https://example.com/feed.xml",
        categoryHint = "SOFTWARE_ENGINEERING"
    )
```

```kotlin
private fun rssXmlWithTwoItems(): String =
    """
    <rss version="2.0">
      <channel>
        <item>
          <guid>https://example.com/articles/reliable-builds</guid>
          <title>Reliable Builds for AI Toolchains</title>
          <link>https://example.com/articles/reliable-builds</link>
          <pubDate>Tue, 05 May 2026 09:00:00 GMT</pubDate>
          <description>Build systems need stronger provenance as AI coding tools grow.</description>
        </item>
        <item>
          <guid>https://example.com/articles/second-builds</guid>
          <title>Second Build Reliability Note</title>
          <link>https://example.com/articles/second-builds</link>
          <pubDate>Tue, 05 May 2026 10:00:00 GMT</pubDate>
          <description>Teams compare build signals.</description>
        </item>
      </channel>
    </rss>
    """.trimIndent()
```

- [ ] **Step 2: Run tests to verify red**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.collection.service.SourceCollectionServiceTest
```

Expected: FAIL because `SourceCollectionResult` has no `skippedArticleIds` or `failureSummaries`, `CollectionFailureSummary` does not exist, and `collect` does not accept `maxArticlesPerSource`.

- [ ] **Step 3: Add collection run response DTOs used by source failures**

Create `backend/src/main/kotlin/com/sigak/collection/dto/CollectionRunResponse.kt`:

```kotlin
package com.sigak.collection.dto

enum class CollectionRunStatus {
    COMPLETED,
    PARTIAL,
    FAILED
}

enum class CollectionFailureStage {
    FETCH_SOURCE,
    PARSE_SOURCE,
    PUBLISH_ARTICLE
}

data class CollectionFailureSummary(
    val stage: CollectionFailureStage,
    val message: String
)

data class CollectionRunResponse(
    val status: CollectionRunStatus,
    val requestedSourceIds: List<String>,
    val selectedSourceCount: Int,
    val fetchedSourceCount: Int,
    val failedSourceCount: Int,
    val discoveredArticleCount: Int,
    val publishedArticleCount: Int,
    val skippedArticleCount: Int,
    val failedArticleCount: Int,
    val publishedArticleIds: List<Long>,
    val skippedArticleIds: List<Long>,
    val durationMs: Long,
    val sourceResults: List<CollectionSourceRunResult>
)

data class CollectionSourceRunResult(
    val sourceId: String,
    val status: CollectionRunStatus,
    val fetched: Boolean,
    val discoveredArticleCount: Int,
    val publishedArticleCount: Int,
    val skippedArticleCount: Int,
    val failedArticleCount: Int,
    val publishedArticleIds: List<Long>,
    val skippedArticleIds: List<Long>,
    val failureSummaries: List<CollectionFailureSummary>,
    val durationMs: Long
)
```

- [ ] **Step 4: Add source collection model fields and collector interface**

In `SourceCollectionService.kt`, replace `SourceCollectionResult` and add `SourceCollector`:

```kotlin
fun interface SourceCollector {
    fun collect(source: NewsSource, maxArticlesPerSource: Int): SourceCollectionResult
}

data class SourceCollectionResult(
    val sourceId: String,
    val discoveredCount: Int,
    val publishedArticleIds: List<Long>,
    val skippedArticleIds: List<Long>,
    val failedCount: Int,
    val failureSummaries: List<CollectionFailureSummary>
)
```

Change the class declaration:

```kotlin
class SourceCollectionService(
    private val sourceContentFetcher: SourceContentFetcher,
    private val rssAtomCollector: RssAtomCollector,
    private val arxivCollector: ArxivCollector,
    private val collectionPipelineService: CollectionPipelineService
) : SourceCollector {
```

- [ ] **Step 5: Update one-source collection logic**

Change `collect` to:

```kotlin
override fun collect(source: NewsSource, maxArticlesPerSource: Int): SourceCollectionResult {
    val xml = sourceContentFetcher.fetch(source.url)
    val articles = parse(source, xml).take(maxArticlesPerSource)
    val publishedArticleIds = mutableListOf<Long>()
    val skippedArticleIds = mutableListOf<Long>()
    val failureSummaries = mutableListOf<CollectionFailureSummary>()
    var failedCount = 0

    articles.forEach { article ->
        runCatching { collectionPipelineService.publish(article) }
            .onSuccess { result ->
                when (result.outcome) {
                    CollectedArticlePublishOutcome.PUBLISHED -> publishedArticleIds.add(result.articleId)
                    CollectedArticlePublishOutcome.SKIPPED_DUPLICATE -> skippedArticleIds.add(result.articleId)
                }
            }
            // 일부 기사 저장 실패가 전체 소스 수집 실패로 번지지 않도록 실패 수만 기록한다.
            .onFailure { exception ->
                failedCount += 1
                if (failureSummaries.size < MAX_FAILURE_SUMMARY_COUNT) {
                    failureSummaries.add(
                        CollectionFailureSummary(
                            stage = CollectionFailureStage.PUBLISH_ARTICLE,
                            message = failureMessage(exception)
                        )
                    )
                }
            }
    }

    return SourceCollectionResult(
        sourceId = source.id,
        discoveredCount = articles.size,
        publishedArticleIds = publishedArticleIds,
        skippedArticleIds = skippedArticleIds,
        failedCount = failedCount,
        failureSummaries = failureSummaries
    )
}
```

Add helpers in the same file:

```kotlin
private const val MAX_FAILURE_SUMMARY_COUNT = 5

internal fun failureMessage(exception: Throwable): String {
    val className = exception::class.simpleName ?: "Exception"
    val message = exception.message?.takeIf { value -> value.isNotBlank() }
    return if (message == null) className else "$className: $message"
}
```

Import `CollectionFailureStage` and `CollectionFailureSummary` from `com.sigak.collection.dto`.

- [ ] **Step 6: Run focused source collection tests**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.collection.service.SourceCollectionServiceTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Run collection contract tests together**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.collection.service.CollectionPipelineServiceTest \
  --tests com.sigak.collection.service.SourceCollectionServiceTest \
  --tests com.sigak.collection.service.CollectedArticlePersistenceServiceTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit source-level outcomes**

Run:

```bash
git add backend/src/main/kotlin/com/sigak/collection/service/SourceCollectionService.kt \
  backend/src/main/kotlin/com/sigak/collection/dto/CollectionRunResponse.kt \
  backend/src/test/kotlin/com/sigak/collection/service/SourceCollectionServiceTest.kt
git commit -m "feat: report source collection outcomes"
```

Expected: commit succeeds.

### Task 4: Collection Run Service

**Files:**
- Create: `backend/src/main/kotlin/com/sigak/collection/dto/CollectionRunRequest.kt`
- Read: `backend/src/main/kotlin/com/sigak/collection/dto/CollectionRunResponse.kt`
- Create: `backend/src/main/kotlin/com/sigak/collection/service/CollectionRunService.kt`
- Test: `backend/src/test/kotlin/com/sigak/collection/service/CollectionRunServiceTest.kt`

- [ ] **Step 1: Write failing run service tests**

Create `CollectionRunServiceTest.kt` with these tests:

```kotlin
package com.sigak.collection.service

import com.sigak.collection.dto.CollectionRunRequest
import com.sigak.collection.dto.CollectionRunStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CollectionRunServiceTest {

    private val sourceRegistry = SourceRegistry()

    @Test
    fun runCollectsAllRegisteredSourcesWhenSourceIdsAreEmpty() {
        val collectedSourceIds = mutableListOf<String>()
        val service = CollectionRunService(
            sourceRegistry = sourceRegistry,
            sourceCollector = SourceCollector { source, _ ->
                collectedSourceIds.add(source.id)
                SourceCollectionResult(
                    sourceId = source.id,
                    discoveredCount = 0,
                    publishedArticleIds = emptyList(),
                    skippedArticleIds = emptyList(),
                    failedCount = 0,
                    failureSummaries = emptyList()
                )
            }
        )

        val response = service.run(CollectionRunRequest(sourceIds = emptyList(), maxArticlesPerSource = 10))

        val expectedIds = sourceRegistry.sources().map { source -> source.id }
        assertEquals(expectedIds, response.requestedSourceIds)
        assertEquals(expectedIds, collectedSourceIds)
        assertEquals(expectedIds.size, response.selectedSourceCount)
        assertEquals(CollectionRunStatus.COMPLETED, response.status)
    }

    @Test
    fun runCollectsSelectedSourcesInRequestOrderAndDeduplicatesIds() {
        val collectedSourceIds = mutableListOf<String>()
        val service = CollectionRunService(
            sourceRegistry = sourceRegistry,
            sourceCollector = SourceCollector { source, _ ->
                collectedSourceIds.add(source.id)
                SourceCollectionResult(
                    sourceId = source.id,
                    discoveredCount = 1,
                    publishedArticleIds = listOf(10L + collectedSourceIds.size),
                    skippedArticleIds = emptyList(),
                    failedCount = 0,
                    failureSummaries = emptyList()
                )
            }
        )

        val response = service.run(
            CollectionRunRequest(
                sourceIds = listOf(" arxiv-cs-ai ", "openai-blog", "arxiv-cs-ai", " "),
                maxArticlesPerSource = 3
            )
        )

        assertEquals(listOf("arxiv-cs-ai", "openai-blog"), response.requestedSourceIds)
        assertEquals(listOf("arxiv-cs-ai", "openai-blog"), collectedSourceIds)
        assertEquals(2, response.selectedSourceCount)
        assertEquals(2, response.discoveredArticleCount)
        assertEquals(2, response.publishedArticleCount)
        assertEquals(listOf(11L, 12L), response.publishedArticleIds)
    }

    @Test
    fun runRejectsUnknownSourceIdsBeforeCollectingAnySource() {
        val collectedSourceIds = mutableListOf<String>()
        val service = CollectionRunService(
            sourceRegistry = sourceRegistry,
            sourceCollector = SourceCollector { source, _ ->
                collectedSourceIds.add(source.id)
                SourceCollectionResult(
                    sourceId = source.id,
                    discoveredCount = 0,
                    publishedArticleIds = emptyList(),
                    skippedArticleIds = emptyList(),
                    failedCount = 0,
                    failureSummaries = emptyList()
                )
            }
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            service.run(CollectionRunRequest(sourceIds = listOf("openai-blog", "missing-source")))
        }

        assertEquals("Unknown collection source IDs: missing-source", exception.message)
        assertEquals(emptyList(), collectedSourceIds)
    }

    @Test
    fun runAggregatesPublishedSkippedAndFailedArticleCounts() {
        val service = CollectionRunService(
            sourceRegistry = sourceRegistry,
            sourceCollector = SourceCollector { source, _ ->
                SourceCollectionResult(
                    sourceId = source.id,
                    discoveredCount = 4,
                    publishedArticleIds = listOf(101L, 102L),
                    skippedArticleIds = listOf(1L),
                    failedCount = 1,
                    failureSummaries = listOf(
                        CollectionFailureSummary(
                            stage = CollectionFailureStage.PUBLISH_ARTICLE,
                            message = "IllegalArgumentException: title must not be blank"
                        )
                    )
                )
            }
        )

        val response = service.run(CollectionRunRequest(sourceIds = listOf("openai-blog")))

        assertEquals(CollectionRunStatus.PARTIAL, response.status)
        assertEquals(1, response.fetchedSourceCount)
        assertEquals(0, response.failedSourceCount)
        assertEquals(4, response.discoveredArticleCount)
        assertEquals(2, response.publishedArticleCount)
        assertEquals(1, response.skippedArticleCount)
        assertEquals(1, response.failedArticleCount)
        assertEquals(listOf(101L, 102L), response.publishedArticleIds)
        assertEquals(listOf(1L), response.skippedArticleIds)
        assertEquals(CollectionRunStatus.PARTIAL, response.sourceResults.single().status)
    }

    @Test
    fun runReturnsPartialWhenOneSourceFailsAndAnotherSucceeds() {
        val service = CollectionRunService(
            sourceRegistry = sourceRegistry,
            sourceCollector = SourceCollector { source, _ ->
                if (source.id == "openai-blog") {
                    throw IllegalStateException("feed unavailable")
                }
                SourceCollectionResult(
                    sourceId = source.id,
                    discoveredCount = 1,
                    publishedArticleIds = listOf(201L),
                    skippedArticleIds = emptyList(),
                    failedCount = 0,
                    failureSummaries = emptyList()
                )
            }
        )

        val response = service.run(CollectionRunRequest(sourceIds = listOf("openai-blog", "github-blog")))

        assertEquals(CollectionRunStatus.PARTIAL, response.status)
        assertEquals(1, response.fetchedSourceCount)
        assertEquals(1, response.failedSourceCount)
        assertEquals(1, response.publishedArticleCount)
        assertEquals(CollectionRunStatus.FAILED, response.sourceResults[0].status)
        assertEquals(false, response.sourceResults[0].fetched)
        assertEquals(CollectionFailureStage.FETCH_SOURCE, response.sourceResults[0].failureSummaries.single().stage)
        assertEquals(CollectionRunStatus.COMPLETED, response.sourceResults[1].status)
    }

    @Test
    fun runRejectsMaxArticlesPerSourceOutsideAllowedRange() {
        val service = CollectionRunService(
            sourceRegistry = sourceRegistry,
            sourceCollector = SourceCollector { source, _ ->
                SourceCollectionResult(
                    sourceId = source.id,
                    discoveredCount = 0,
                    publishedArticleIds = emptyList(),
                    skippedArticleIds = emptyList(),
                    failedCount = 0,
                    failureSummaries = emptyList()
                )
            }
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            service.run(CollectionRunRequest(sourceIds = listOf("openai-blog"), maxArticlesPerSource = 21))
        }

        assertEquals("maxArticlesPerSource must be between 1 and 20.", exception.message)
    }
}
```

- [ ] **Step 2: Run tests to verify red**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.collection.service.CollectionRunServiceTest
```

Expected: FAIL at Kotlin compilation because `CollectionRunService`, `CollectionRunRequest`, and response DTOs do not exist.

- [ ] **Step 3: Add request DTO**

Create `backend/src/main/kotlin/com/sigak/collection/dto/CollectionRunRequest.kt`:

```kotlin
package com.sigak.collection.dto

data class CollectionRunRequest(
    val sourceIds: List<String>? = null,
    val maxArticlesPerSource: Int? = null
)
```

- [ ] **Step 4: Add CollectionRunService**

Create `backend/src/main/kotlin/com/sigak/collection/service/CollectionRunService.kt`:

```kotlin
package com.sigak.collection.service

import com.sigak.collection.domain.NewsSource
import com.sigak.collection.dto.CollectionFailureStage
import com.sigak.collection.dto.CollectionFailureSummary
import com.sigak.collection.dto.CollectionRunRequest
import com.sigak.collection.dto.CollectionRunResponse
import com.sigak.collection.dto.CollectionRunStatus
import com.sigak.collection.dto.CollectionSourceRunResult
import kotlin.system.measureTimeMillis
import org.springframework.stereotype.Service

private const val DEFAULT_MAX_ARTICLES_PER_SOURCE = 10
private const val MAX_ARTICLES_PER_SOURCE_LIMIT = 20

@Service
class CollectionRunService(
    private val sourceRegistry: SourceRegistry,
    private val sourceCollector: SourceCollector
) {

    fun run(request: CollectionRunRequest = CollectionRunRequest()): CollectionRunResponse {
        val maxArticlesPerSource = maxArticlesPerSourceFor(request.maxArticlesPerSource)
        val selectedSources = selectedSourcesFor(request.sourceIds)
        val sourceResults = mutableListOf<CollectionSourceRunResult>()

        val durationMs = measureTimeMillis {
            selectedSources.forEach { source ->
                sourceResults.add(collectSource(source, maxArticlesPerSource))
            }
        }

        return responseFor(
            sourceIds = selectedSources.map { source -> source.id },
            sourceResults = sourceResults,
            durationMs = durationMs
        )
    }

    private fun selectedSourcesFor(sourceIds: List<String>?): List<NewsSource> {
        val sources = sourceRegistry.sources()
        val sourcesById = sources.associateBy { source -> source.id }
        val requestedIds = sourceIds
            .orEmpty()
            .map { sourceId -> sourceId.trim() }
            .filter { sourceId -> sourceId.isNotBlank() }
            .distinct()

        if (requestedIds.isEmpty()) {
            return sources
        }

        val unknownIds = requestedIds.filterNot { sourceId -> sourcesById.containsKey(sourceId) }
        require(unknownIds.isEmpty()) {
            "Unknown collection source IDs: ${unknownIds.joinToString(", ")}"
        }

        return requestedIds.map { sourceId -> requireNotNull(sourcesById[sourceId]) }
    }

    private fun maxArticlesPerSourceFor(value: Int?): Int {
        val maxArticlesPerSource = value ?: DEFAULT_MAX_ARTICLES_PER_SOURCE
        require(maxArticlesPerSource in 1..MAX_ARTICLES_PER_SOURCE_LIMIT) {
            "maxArticlesPerSource must be between 1 and 20."
        }
        return maxArticlesPerSource
    }

    private fun collectSource(source: NewsSource, maxArticlesPerSource: Int): CollectionSourceRunResult {
        var sourceResult: SourceCollectionResult? = null
        var failure: Throwable? = null
        val durationMs = measureTimeMillis {
            runCatching { sourceCollector.collect(source, maxArticlesPerSource) }
                .onSuccess { result -> sourceResult = result }
                .onFailure { exception -> failure = exception }
        }

        val exception = failure
        return if (exception == null) {
            completedSourceResult(requireNotNull(sourceResult), durationMs)
        } else {
            failedSourceResult(source, exception, durationMs)
        }
    }

    private fun completedSourceResult(
        result: SourceCollectionResult,
        durationMs: Long
    ): CollectionSourceRunResult {
        val status = if (result.failedCount > 0) CollectionRunStatus.PARTIAL else CollectionRunStatus.COMPLETED
        return CollectionSourceRunResult(
            sourceId = result.sourceId,
            status = status,
            fetched = true,
            discoveredArticleCount = result.discoveredCount,
            publishedArticleCount = result.publishedArticleIds.size,
            skippedArticleCount = result.skippedArticleIds.size,
            failedArticleCount = result.failedCount,
            publishedArticleIds = result.publishedArticleIds,
            skippedArticleIds = result.skippedArticleIds,
            failureSummaries = result.failureSummaries,
            durationMs = durationMs
        )
    }

    private fun failedSourceResult(
        source: NewsSource,
        exception: Throwable,
        durationMs: Long
    ): CollectionSourceRunResult =
        CollectionSourceRunResult(
            sourceId = source.id,
            status = CollectionRunStatus.FAILED,
            fetched = false,
            discoveredArticleCount = 0,
            publishedArticleCount = 0,
            skippedArticleCount = 0,
            failedArticleCount = 0,
            publishedArticleIds = emptyList(),
            skippedArticleIds = emptyList(),
            failureSummaries = listOf(
                CollectionFailureSummary(
                    stage = CollectionFailureStage.FETCH_SOURCE,
                    message = failureMessage(exception)
                )
            ),
            durationMs = durationMs
        )

    private fun responseFor(
        sourceIds: List<String>,
        sourceResults: List<CollectionSourceRunResult>,
        durationMs: Long
    ): CollectionRunResponse {
        val fetchedSourceCount = sourceResults.count { result -> result.fetched }
        val failedSourceCount = sourceResults.count { result -> result.status == CollectionRunStatus.FAILED }
        val failedArticleCount = sourceResults.sumOf { result -> result.failedArticleCount }
        val status = statusFor(sourceResults)

        return CollectionRunResponse(
            status = status,
            requestedSourceIds = sourceIds,
            selectedSourceCount = sourceIds.size,
            fetchedSourceCount = fetchedSourceCount,
            failedSourceCount = failedSourceCount,
            discoveredArticleCount = sourceResults.sumOf { result -> result.discoveredArticleCount },
            publishedArticleCount = sourceResults.sumOf { result -> result.publishedArticleCount },
            skippedArticleCount = sourceResults.sumOf { result -> result.skippedArticleCount },
            failedArticleCount = failedArticleCount,
            publishedArticleIds = sourceResults.flatMap { result -> result.publishedArticleIds },
            skippedArticleIds = sourceResults.flatMap { result -> result.skippedArticleIds },
            durationMs = durationMs,
            sourceResults = sourceResults
        )
    }

    private fun statusFor(sourceResults: List<CollectionSourceRunResult>): CollectionRunStatus =
        when {
            sourceResults.all { result -> result.status == CollectionRunStatus.FAILED } -> CollectionRunStatus.FAILED
            sourceResults.any { result -> result.status != CollectionRunStatus.COMPLETED } -> CollectionRunStatus.PARTIAL
            else -> CollectionRunStatus.COMPLETED
        }
}
```

- [ ] **Step 5: Run run service tests**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.collection.service.CollectionRunServiceTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Run collection service tests together**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.collection.service.CollectionRunServiceTest \
  --tests com.sigak.collection.service.SourceCollectionServiceTest \
  --tests com.sigak.collection.service.CollectionPipelineServiceTest \
  --tests com.sigak.collection.service.CollectedArticlePersistenceServiceTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit run service**

Run:

```bash
git add backend/src/main/kotlin/com/sigak/collection/dto/CollectionRunRequest.kt \
  backend/src/main/kotlin/com/sigak/collection/service/CollectionRunService.kt \
  backend/src/test/kotlin/com/sigak/collection/service/CollectionRunServiceTest.kt
git commit -m "feat: aggregate controlled collection runs"
```

Expected: commit succeeds.

### Task 5: Internal Collection Run Controller

**Files:**
- Create: `backend/src/main/kotlin/com/sigak/collection/controller/CollectionRunController.kt`
- Test: `backend/src/test/kotlin/com/sigak/collection/controller/CollectionRunControllerTest.kt`

- [ ] **Step 1: Write failing controller tests**

Create `CollectionRunControllerTest.kt`:

```kotlin
package com.sigak.collection.controller

import com.sigak.collection.dto.CollectionRunRequest
import com.sigak.collection.dto.CollectionRunResponse
import com.sigak.collection.dto.CollectionRunStatus
import com.sigak.collection.dto.CollectionSourceRunResult
import com.sigak.collection.service.CollectionRunService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(CollectionRunController::class)
class CollectionRunControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var service: CollectionRunService

    @Test
    fun runCollectionReturnsAggregatedRunResponse() {
        `when`(service.run(CollectionRunRequest(sourceIds = listOf("openai-blog"), maxArticlesPerSource = 3)))
            .thenReturn(
                CollectionRunResponse(
                    status = CollectionRunStatus.COMPLETED,
                    requestedSourceIds = listOf("openai-blog"),
                    selectedSourceCount = 1,
                    fetchedSourceCount = 1,
                    failedSourceCount = 0,
                    discoveredArticleCount = 3,
                    publishedArticleCount = 2,
                    skippedArticleCount = 1,
                    failedArticleCount = 0,
                    publishedArticleIds = listOf(101L, 102L),
                    skippedArticleIds = listOf(1L),
                    durationMs = 42,
                    sourceResults = listOf(
                        CollectionSourceRunResult(
                            sourceId = "openai-blog",
                            status = CollectionRunStatus.COMPLETED,
                            fetched = true,
                            discoveredArticleCount = 3,
                            publishedArticleCount = 2,
                            skippedArticleCount = 1,
                            failedArticleCount = 0,
                            publishedArticleIds = listOf(101L, 102L),
                            skippedArticleIds = listOf(1L),
                            failureSummaries = emptyList(),
                            durationMs = 40
                        )
                    )
                )
            )

        mockMvc.perform(
            post("/api/internal/collections/runs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"sourceIds":["openai-blog"],"maxArticlesPerSource":3}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.requestedSourceIds[0]").value("openai-blog"))
            .andExpect(jsonPath("$.selectedSourceCount").value(1))
            .andExpect(jsonPath("$.fetchedSourceCount").value(1))
            .andExpect(jsonPath("$.publishedArticleCount").value(2))
            .andExpect(jsonPath("$.skippedArticleCount").value(1))
            .andExpect(jsonPath("$.publishedArticleIds[0]").value(101))
            .andExpect(jsonPath("$.skippedArticleIds[0]").value(1))
            .andExpect(jsonPath("$.durationMs").value(42))
            .andExpect(jsonPath("$.sourceResults[0].sourceId").value("openai-blog"))
            .andExpect(jsonPath("$.sourceResults[0].fetched").value(true))
    }

    @Test
    fun runCollectionAcceptsMissingRequestBodyAsDefaultRequest() {
        `when`(service.run(CollectionRunRequest()))
            .thenReturn(
                CollectionRunResponse(
                    status = CollectionRunStatus.COMPLETED,
                    requestedSourceIds = listOf("openai-blog"),
                    selectedSourceCount = 1,
                    fetchedSourceCount = 1,
                    failedSourceCount = 0,
                    discoveredArticleCount = 0,
                    publishedArticleCount = 0,
                    skippedArticleCount = 0,
                    failedArticleCount = 0,
                    publishedArticleIds = emptyList(),
                    skippedArticleIds = emptyList(),
                    durationMs = 1,
                    sourceResults = emptyList()
                )
            )

        mockMvc.perform(post("/api/internal/collections/runs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.selectedSourceCount").value(1))
    }

    @Test
    fun runCollectionReturnsBadRequestWhenServiceRejectsRequest() {
        `when`(service.run(CollectionRunRequest(sourceIds = listOf("missing-source"), maxArticlesPerSource = null)))
            .thenThrow(IllegalArgumentException("Unknown collection source IDs: missing-source"))

        mockMvc.perform(
            post("/api/internal/collections/runs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"sourceIds":["missing-source"]}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Unknown collection source IDs: missing-source"))
    }
}
```

- [ ] **Step 2: Run tests to verify red**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.collection.controller.CollectionRunControllerTest
```

Expected: FAIL at Kotlin compilation because `CollectionRunController` does not exist.

- [ ] **Step 3: Add internal controller**

Create `backend/src/main/kotlin/com/sigak/collection/controller/CollectionRunController.kt`:

```kotlin
package com.sigak.collection.controller

import com.sigak.collection.dto.CollectionRunRequest
import com.sigak.collection.dto.CollectionRunResponse
import com.sigak.collection.service.CollectionRunService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/internal/collections/runs")
@Tag(name = "Internal Collections", description = "Controlled local triggers for selected-source collection runs.")
class CollectionRunController(
    private val service: CollectionRunService
) {

    @PostMapping
    @Operation(
        summary = "Run selected-source collection",
        description = "Runs registered collection sources sequentially and returns source/article counts for local MVP operations."
    )
    fun runCollection(
        @RequestBody(required = false)
        request: CollectionRunRequest?
    ): CollectionRunResponse =
        service.run(request ?: CollectionRunRequest())

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleIllegalArgumentException(exception: IllegalArgumentException): Map<String, String> =
        mapOf("message" to exception.message.orEmpty())
}
```

- [ ] **Step 4: Run controller tests**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.collection.controller.CollectionRunControllerTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Run collection trigger focused tests**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.collection.controller.CollectionRunControllerTest \
  --tests com.sigak.collection.service.CollectionRunServiceTest \
  --tests com.sigak.collection.service.SourceCollectionServiceTest \
  --tests com.sigak.collection.service.CollectionPipelineServiceTest \
  --tests com.sigak.collection.service.CollectedArticlePersistenceServiceTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit internal controller**

Run:

```bash
git add backend/src/main/kotlin/com/sigak/collection/controller/CollectionRunController.kt \
  backend/src/test/kotlin/com/sigak/collection/controller/CollectionRunControllerTest.kt
git commit -m "feat: expose internal collection run trigger"
```

Expected: commit succeeds.

### Task 6: Documentation And Blog Queue

**Files:**
- Modify: `docs/API_SPEC.md`
- Modify: `docs/STATUS.md`
- Modify: `docs/ROADMAP.md`
- Modify: `docs/ONBOARDING.ko.md`
- Modify: `docs/blog/topic-queue.md`
- Modify: `docs/blog/2026-05-31-dev-log.md`

- [ ] **Step 1: Update API spec**

In `docs/API_SPEC.md`, add `POST /api/internal/collections/runs` to the current endpoint list.

Add a new section before `## Internal Search Projection Contract`:

````md
## Internal Collection Run Contract

Controlled collection runs are internal MVP operations. They execute selected registered sources through Spring Boot and return source/article counts without changing the public article API response shape.

```http
POST /api/internal/collections/runs
```

Request body is optional. Missing body or empty `sourceIds` runs all registered sources.

```json
{
  "sourceIds": ["openai-blog", "arxiv-cs-ai"],
  "maxArticlesPerSource": 10
}
```

Expected response:

```json
{
  "status": "COMPLETED",
  "requestedSourceIds": ["openai-blog"],
  "selectedSourceCount": 1,
  "fetchedSourceCount": 1,
  "failedSourceCount": 0,
  "discoveredArticleCount": 3,
  "publishedArticleCount": 2,
  "skippedArticleCount": 1,
  "failedArticleCount": 0,
  "publishedArticleIds": [101, 102],
  "skippedArticleIds": [1],
  "durationMs": 42,
  "sourceResults": [
    {
      "sourceId": "openai-blog",
      "status": "COMPLETED",
      "fetched": true,
      "discoveredArticleCount": 3,
      "publishedArticleCount": 2,
      "skippedArticleCount": 1,
      "failedArticleCount": 0,
      "publishedArticleIds": [101, 102],
      "skippedArticleIds": [1],
      "failureSummaries": [],
      "durationMs": 40
    }
  ]
}
```

Notes:
- `sourceIds` values are trimmed, blank IDs are ignored, and duplicate IDs are deduplicated in request order
- unknown source IDs return `400 Bad Request` before any source is collected
- `maxArticlesPerSource` defaults to `10` and must be between `1` and `20`
- duplicate article attempts are counted as `skipped`, not `published`
- collection does not automatically rebuild Elasticsearch, Qdrant, or Neo4j projections
````

- [ ] **Step 2: Update status and roadmap**

In `docs/STATUS.md`, update Collection and Enrichment Foundation "Needs work":

```md
- Scheduled collection is not implemented yet.
- Internal controlled collection trigger exists for local runs, but persistent run history and command runner wrapper remain pending.
- Retry, persistent failure status, and observability beyond response counts are still missing.
- FastAPI HTTP enrichment mode is still pending.
```

In `docs/ROADMAP.md`, update Phase S2:

```md
- [x] Add an internal/admin trigger or command runner for source collection.
```

In Phase S3, keep command runner/operations hardening visible:

```md
- [ ] Add a command runner wrapper for controlled collection runs.
- [x] Return fetched, published, skipped, and failed counts for each run.
- [ ] Record enough persistent failure information to debug bad feeds or invalid collected articles across runs.
```

- [ ] **Step 3: Update onboarding**

In `docs/ONBOARDING.ko.md`, add a local operation command under the internal operation or local run section:

```md
- Collection run: `curl -X POST http://localhost:8080/api/internal/collections/runs -H 'Content-Type: application/json' -d '{"sourceIds":["openai-blog"],"maxArticlesPerSource":3}'`
```

- [ ] **Step 4: Update topic queue**

Add this candidate to `docs/blog/topic-queue.md` if it does not already exist:

```md
## [candidate] Collection run에서 published와 skipped count를 분리한 이유

- 날짜: 2026-05-31
- 관련 작업: internal collection trigger, publish outcome contract, source/run count aggregation
- 관련 파일:
  - `backend/src/main/kotlin/com/sigak/collection/service/CollectedArticlePublishResult.kt`
  - `backend/src/main/kotlin/com/sigak/collection/service/CollectionRunService.kt`
  - `backend/src/main/kotlin/com/sigak/collection/controller/CollectionRunController.kt`
  - `docs/API_SPEC.md`
- 감지 이유:
  - 기존 `publish()`는 article ID만 반환해 신규 저장과 중복 skip을 구분할 수 없었다.
  - 운영용 count가 거짓말하지 않도록 persistence boundary에서 publish outcome을 반환하게 했다.
  - internal endpoint와 command-runner-ready service boundary를 분리했다.
- 글의 핵심 질문:
  - collection run에서 published와 skipped를 섞으면 어떤 운영 문제가 생기는가?
  - duplicate detection은 왜 persistence boundary에 남겨야 하는가?
  - internal trigger를 만들면서 persistent run history를 미룬 이유는 무엇인가?
- 검증 근거:
  - 구현 세션에서 실행한 focused test, full backend test, `./gradlew check` 결과를 dev-log 작성 시 추가한다.
- 추천 글 유형: 회사 기술 블로그 / 운영성 설계 회고
- 상태: candidate
```

- [ ] **Step 5: Update dev-log with verified facts**

Append a section to `docs/blog/2026-05-31-dev-log.md` after implementation verification. Include only commands actually run during implementation:

```md
## 추가 진행: controlled collection trigger 구현

- `POST /api/internal/collections/runs` internal endpoint를 추가했다.
- `CollectionRunService`로 source 선택, unknown source 검증, sequential run, count aggregation을 분리했다.
- `CollectedArticlePublishResult`로 신규 저장과 duplicate skip을 구분했다.
- Projection rebuild는 자동화하지 않고 explicit internal operation으로 유지했다.

검증:
- `./gradlew test --tests com.sigak.collection.controller.CollectionRunControllerTest --tests com.sigak.collection.service.CollectionRunServiceTest --tests com.sigak.collection.service.SourceCollectionServiceTest --tests com.sigak.collection.service.CollectionPipelineServiceTest --tests com.sigak.collection.service.CollectedArticlePersistenceServiceTest`
- `./gradlew test --rerun-tasks`
- `./gradlew check`
```

- [ ] **Step 6: Commit docs**

Run:

```bash
git add docs/API_SPEC.md docs/STATUS.md docs/ROADMAP.md docs/ONBOARDING.ko.md \
  docs/blog/topic-queue.md docs/blog/2026-05-31-dev-log.md
git commit -m "docs: document controlled collection trigger"
```

Expected: commit succeeds.

### Task 7: Final Verification And Review

**Files:**
- Verify: backend
- Verify: repository diff

- [ ] **Step 1: Run full backend tests**

Run:

```bash
cd backend
./gradlew test --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run backend check**

Run:

```bash
cd backend
./gradlew check
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run whitespace diff check**

Run:

```bash
git diff --check
```

Expected: no output and exit code `0`.

- [ ] **Step 4: Inspect final diff**

Run:

```bash
git status --short
git log --oneline --decorate -5
```

Expected:

```txt
git status --short
```

shows no unstaged implementation changes, or only intentionally uncommitted final notes.

```txt
git log --oneline --decorate -5
```

shows the feature commits created by this plan on `codex/refactor-onboarding-search`.

- [ ] **Step 5: Report verified and unverified items**

Report to the user:

```txt
Verified:
- focused collection trigger tests
- full backend test
- backend check
- git diff --check

Not verified:
- Docker Compose runtime smoke unless it was actually run
- real external feed behavior unless the internal endpoint was called against live sources
```

Do not claim Docker smoke or live feed behavior unless those commands were actually run.
