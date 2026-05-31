package com.sigak.collection.dto

import com.sigak.collection.domain.CollectionFailureKind
import java.time.Instant
import java.util.UUID

data class CollectionFailureEventSearchRequest(
    val sourceId: String? = null,
    val runId: UUID? = null,
    val retryable: Boolean? = null,
    val limit: Int = 20
)

data class CollectionFailureEventSearchResponse(
    val returnedCount: Int,
    val events: List<CollectionFailureEventItemResponse>
)

data class CollectionFailureEventItemResponse(
    val id: Long,
    val runId: UUID,
    val sourceId: String,
    val stage: CollectionFailureStage,
    val failureKind: CollectionFailureKind,
    val retryable: Boolean,
    val message: String,
    val fingerprint: String,
    val articleExternalId: String?,
    val articleUrl: String?,
    val articleTitle: String?,
    val occurredAt: Instant
)
