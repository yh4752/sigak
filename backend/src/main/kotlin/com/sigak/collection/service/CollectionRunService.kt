package com.sigak.collection.service

import com.sigak.common.time.measureElapsed
import com.sigak.collection.domain.NewsSource
import com.sigak.collection.dto.CollectionFailureStage
import com.sigak.collection.dto.CollectionFailureSummary
import com.sigak.collection.dto.CollectionRunRequest
import com.sigak.collection.dto.CollectionRunResponse
import com.sigak.collection.dto.CollectionRunStatus
import com.sigak.collection.dto.CollectionSourceRunResult
import java.util.UUID
import org.springframework.stereotype.Service

private const val DEFAULT_MAX_ARTICLES_PER_SOURCE = 10
private const val MAX_ARTICLES_PER_SOURCE_LIMIT = 20

@Service
class CollectionRunService(
    private val sourceRegistry: SourceRegistry,
    private val sourceCollector: SourceCollector,
    private val collectionFailureClassifier: CollectionFailureClassifier,
    private val collectionFailureRecorder: CollectionFailureRecorder
) {

    fun run(request: CollectionRunRequest = CollectionRunRequest()): CollectionRunResponse {
        val runId = UUID.randomUUID()
        val maxArticlesPerSource = maxArticlesPerSourceFor(request.maxArticlesPerSource)
        val selectedSources = selectedSourcesFor(request.sourceIds)
        val sourceResults = mutableListOf<CollectionSourceRunResult>()

        val runResult = measureElapsed {
            selectedSources.forEach { source ->
                sourceResults.add(collectSource(runId, source, maxArticlesPerSource))
            }
        }

        return responseFor(
            runId = runId,
            sourceIds = selectedSources.map { source -> source.id },
            sourceResults = sourceResults,
            durationMs = runResult.elapsedMs
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

    private fun collectSource(
        runId: UUID,
        source: NewsSource,
        maxArticlesPerSource: Int
    ): CollectionSourceRunResult {
        var sourceResult: SourceCollectionResult? = null
        var failure: Throwable? = null
        val sourceRunResult = measureElapsed {
            runCatching { sourceCollector.collect(source, maxArticlesPerSource) }
                .onSuccess { result -> sourceResult = result }
                .onFailure { exception -> failure = exception }
        }

        val exception = failure
        return if (exception == null) {
            completedSourceResult(runId, requireNotNull(sourceResult), sourceRunResult.elapsedMs)
        } else {
            failedSourceResult(runId, source, exception, sourceRunResult.elapsedMs)
        }
    }

    private fun completedSourceResult(
        runId: UUID,
        result: SourceCollectionResult,
        durationMs: Long
    ): CollectionSourceRunResult {
        val status = if (result.failedCount > 0) CollectionRunStatus.PARTIAL else CollectionRunStatus.COMPLETED
        val failureSummaries = result.failureSummaries
            .map { summary -> recordFailureSummary(runId, result.sourceId, summary) }
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
            failureSummaries = failureSummaries,
            durationMs = durationMs
        )
    }

    private fun failedSourceResult(
        runId: UUID,
        source: NewsSource,
        exception: Throwable,
        durationMs: Long
    ): CollectionSourceRunResult {
        val stage = (exception as? SourceCollectionException)?.stage ?: CollectionFailureStage.FETCH_SOURCE
        val classification = collectionFailureClassifier.classify(stage, exception)
        val failureSummary = recordFailureSummary(
            runId = runId,
            sourceId = source.id,
            summary = CollectionFailureSummary(
                stage = stage,
                message = classification.message,
                failureKind = classification.failureKind,
                retryable = classification.retryable
            )
        )
        return CollectionSourceRunResult(
            sourceId = source.id,
            status = CollectionRunStatus.FAILED,
            fetched = false,
            discoveredArticleCount = 0,
            publishedArticleCount = 0,
            skippedArticleCount = 0,
            failedArticleCount = 0,
            publishedArticleIds = emptyList(),
            skippedArticleIds = emptyList(),
            failureSummaries = listOf(failureSummary),
            durationMs = durationMs
        )
    }

    private fun recordFailureSummary(
        runId: UUID,
        sourceId: String,
        summary: CollectionFailureSummary
    ): CollectionFailureSummary {
        val event = collectionFailureRecorder.record(
            CollectionFailureEventRecordRequest(
                runId = runId,
                sourceId = sourceId,
                stage = summary.stage,
                failureKind = summary.failureKind,
                retryable = summary.retryable,
                message = summary.message,
                articleExternalId = summary.articleExternalId,
                articleUrl = summary.articleUrl,
                articleTitle = summary.articleTitle
            )
        )
        return summary.copy(failureEventId = event.id)
    }

    private fun responseFor(
        runId: UUID,
        sourceIds: List<String>,
        sourceResults: List<CollectionSourceRunResult>,
        durationMs: Long
    ): CollectionRunResponse {
        val fetchedSourceCount = sourceResults.count { result -> result.fetched }
        val failedSourceCount = sourceResults.count { result -> result.status == CollectionRunStatus.FAILED }
        val failedArticleCount = sourceResults.sumOf { result -> result.failedArticleCount }
        val status = statusFor(sourceResults)

        return CollectionRunResponse(
            runId = runId,
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
