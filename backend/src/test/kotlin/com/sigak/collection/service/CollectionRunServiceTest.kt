package com.sigak.collection.service

import com.sigak.collection.domain.CollectionFailureEventEntity
import com.sigak.collection.domain.CollectionFailureKind
import com.sigak.collection.dto.CollectionFailureStage
import com.sigak.collection.dto.CollectionFailureSummary
import com.sigak.collection.dto.CollectionRunRequest
import com.sigak.collection.dto.CollectionRunStatus
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class CollectionRunServiceTest {

    private val sourceRegistry = SourceRegistry()

    @Test
    fun runCollectsAllRegisteredSourcesWhenSourceIdsAreEmpty() {
        val collectedSourceIds = mutableListOf<String>()
        val service = collectionRunService(
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
        val service = collectionRunService(
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
        val service = collectionRunService(
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
        val service = collectionRunService(
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
        val service = collectionRunService(
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
        val service = collectionRunService(
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

    @Test
    fun runReturnsGeneratedRunId() {
        val recorder = RecordingFailureRecorder()
        val service = collectionRunService(
            sourceCollector = SourceCollector { source, _ ->
                SourceCollectionResult(
                    sourceId = source.id,
                    discoveredCount = 0,
                    publishedArticleIds = emptyList(),
                    skippedArticleIds = emptyList(),
                    failedCount = 0,
                    failureSummaries = emptyList()
                )
            },
            collectionFailureClassifier = CollectionFailureClassifier(),
            collectionFailureRecorder = recorder
        )

        val response = service.run(CollectionRunRequest(sourceIds = listOf("github-blog")))

        assertNotEquals(UUID(0, 0), response.runId)
        assertEquals(emptyList(), recorder.records)
    }

    @Test
    fun runRecordsSourceLevelFailureEventAndReturnsFailureEventId() {
        val recorder = RecordingFailureRecorder()
        val service = collectionRunService(
            sourceCollector = SourceCollector { _, _ ->
                throw SourceCollectionException(
                    stage = CollectionFailureStage.FETCH_SOURCE,
                    message = "IllegalStateException: feed unavailable",
                    cause = IllegalStateException("feed unavailable")
                )
            },
            collectionFailureClassifier = CollectionFailureClassifier(),
            collectionFailureRecorder = recorder
        )

        val response = service.run(CollectionRunRequest(sourceIds = listOf("github-blog")))

        val failure = response.sourceResults.single().failureSummaries.single()
        assertEquals(1L, failure.failureEventId)
        assertEquals(1, recorder.records.size)
        assertEquals(response.runId, recorder.records.single().runId)
        assertEquals("github-blog", recorder.records.single().sourceId)
        assertEquals(CollectionFailureStage.FETCH_SOURCE, recorder.records.single().stage)
    }

    @Test
    fun runRecordsArticleLevelFailureEventAndReturnsFailureEventId() {
        val recorder = RecordingFailureRecorder()
        val service = collectionRunService(
            sourceCollector = SourceCollector { source, _ ->
                SourceCollectionResult(
                    sourceId = source.id,
                    discoveredCount = 1,
                    publishedArticleIds = emptyList(),
                    skippedArticleIds = emptyList(),
                    failedCount = 1,
                    failureSummaries = listOf(
                        CollectionFailureSummary(
                            stage = CollectionFailureStage.PUBLISH_ARTICLE,
                            message = "IllegalArgumentException: title must not be blank",
                            failureKind = CollectionFailureKind.INVALID_ARTICLE,
                            retryable = false,
                            articleExternalId = "gh-1",
                            articleUrl = "https://github.blog/example",
                            articleTitle = "Broken article"
                        )
                    )
                )
            },
            collectionFailureClassifier = CollectionFailureClassifier(),
            collectionFailureRecorder = recorder
        )

        val response = service.run(CollectionRunRequest(sourceIds = listOf("github-blog")))

        val failure = response.sourceResults.single().failureSummaries.single()
        assertEquals(1L, failure.failureEventId)
        assertEquals(CollectionFailureKind.INVALID_ARTICLE, recorder.records.single().failureKind)
        assertEquals("gh-1", recorder.records.single().articleExternalId)
    }

    @Test
    fun runDoesNotRecordDuplicateSkipsAsFailureEvents() {
        val recorder = RecordingFailureRecorder()
        val service = collectionRunService(
            sourceCollector = SourceCollector { source, _ ->
                SourceCollectionResult(
                    sourceId = source.id,
                    discoveredCount = 1,
                    publishedArticleIds = emptyList(),
                    skippedArticleIds = listOf(77L),
                    failedCount = 0,
                    failureSummaries = emptyList()
                )
            },
            collectionFailureClassifier = CollectionFailureClassifier(),
            collectionFailureRecorder = recorder
        )

        val response = service.run(CollectionRunRequest(sourceIds = listOf("github-blog")))

        assertEquals(1, response.skippedArticleCount)
        assertEquals(emptyList(), recorder.records)
    }

    private fun collectionRunService(
        sourceCollector: SourceCollector,
        collectionFailureClassifier: CollectionFailureClassifier = CollectionFailureClassifier(),
        collectionFailureRecorder: CollectionFailureRecorder = NoOpCollectionFailureRecorder
    ): CollectionRunService =
        CollectionRunService(
            sourceRegistry = sourceRegistry,
            sourceCollector = sourceCollector,
            collectionFailureClassifier = collectionFailureClassifier,
            collectionFailureRecorder = collectionFailureRecorder
        )

    private class RecordingFailureRecorder : CollectionFailureRecorder {
        val records = mutableListOf<CollectionFailureEventRecordRequest>()

        override fun record(request: CollectionFailureEventRecordRequest): CollectionFailureEventEntity {
            records.add(request)
            return CollectionFailureEventEntity(
                id = records.size.toLong(),
                runId = request.runId,
                sourceKey = request.sourceId,
                stage = request.stage,
                failureKind = request.failureKind,
                retryable = request.retryable,
                message = request.message,
                fingerprint = "fingerprint-${records.size}",
                articleExternalId = request.articleExternalId,
                articleUrl = request.articleUrl,
                articleTitle = request.articleTitle
            )
        }
    }
}
