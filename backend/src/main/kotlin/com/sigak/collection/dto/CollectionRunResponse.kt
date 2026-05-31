package com.sigak.collection.dto

import com.sigak.collection.domain.CollectionFailureKind
import java.util.UUID

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
    val message: String,
    val failureKind: CollectionFailureKind = CollectionFailureKind.UNKNOWN,
    val retryable: Boolean = false,
    val failureEventId: Long? = null,
    val articleExternalId: String? = null,
    val articleUrl: String? = null,
    val articleTitle: String? = null
)

data class CollectionRunResponse(
    val runId: UUID = UUID(0, 0),
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
