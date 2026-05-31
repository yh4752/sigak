package com.sigak.collection.service

import com.sigak.collection.domain.CollectionFailureEventEntity
import com.sigak.collection.dto.CollectionFailureEventItemResponse
import com.sigak.collection.dto.CollectionFailureEventSearchRequest
import com.sigak.collection.dto.CollectionFailureEventSearchResponse
import com.sigak.collection.repository.CollectionFailureEventRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

@Service
class CollectionFailureEventQueryService(
    private val repository: CollectionFailureEventRepository
) {

    fun search(request: CollectionFailureEventSearchRequest): CollectionFailureEventSearchResponse {
        require(request.limit in MIN_LIMIT..MAX_LIMIT) {
            "limit must be between $MIN_LIMIT and $MAX_LIMIT"
        }

        // 내부 진단 API라도 실수로 너무 큰 이벤트 목록을 읽지 않도록 상한을 둔다.
        val pageable = PageRequest.of(0, request.limit)
        val sourceId = request.sourceId?.trim()?.takeIf { it.isNotEmpty() }
        val events = repository.searchLatest(
            sourceKey = sourceId,
            runId = request.runId,
            retryable = request.retryable,
            pageable = pageable
        )

        return CollectionFailureEventSearchResponse(
            returnedCount = events.size,
            events = events.map { event -> event.toResponse() }
        )
    }

    private fun CollectionFailureEventEntity.toResponse(): CollectionFailureEventItemResponse =
        CollectionFailureEventItemResponse(
            id = requireNotNull(id),
            runId = runId,
            sourceId = sourceKey,
            stage = stage,
            failureKind = failureKind,
            retryable = retryable,
            message = message,
            fingerprint = fingerprint,
            articleExternalId = articleExternalId,
            articleUrl = articleUrl,
            articleTitle = articleTitle,
            occurredAt = occurredAt
        )

    private companion object {
        const val MIN_LIMIT = 1
        const val MAX_LIMIT = 100
    }
}
