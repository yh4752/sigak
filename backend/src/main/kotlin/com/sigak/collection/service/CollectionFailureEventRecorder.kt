package com.sigak.collection.service

import com.sigak.collection.domain.CollectionFailureEventEntity
import com.sigak.collection.domain.CollectionFailureKind
import com.sigak.collection.dto.CollectionFailureStage
import com.sigak.collection.repository.CollectionFailureEventRepository
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class CollectionFailureEventRecordRequest(
    val runId: UUID,
    val sourceId: String,
    val stage: CollectionFailureStage,
    val failureKind: CollectionFailureKind,
    val retryable: Boolean,
    val message: String,
    val articleExternalId: String? = null,
    val articleUrl: String? = null,
    val articleTitle: String? = null
)

fun interface CollectionFailureRecorder {
    fun record(request: CollectionFailureEventRecordRequest): CollectionFailureEventEntity
}

object NoOpCollectionFailureRecorder : CollectionFailureRecorder {
    override fun record(request: CollectionFailureEventRecordRequest): CollectionFailureEventEntity =
        CollectionFailureEventEntity(
            runId = request.runId,
            sourceKey = request.sourceId,
            stage = request.stage,
            failureKind = request.failureKind,
            retryable = request.retryable,
            message = request.message,
            fingerprint = "",
            articleExternalId = request.articleExternalId,
            articleUrl = request.articleUrl,
            articleTitle = request.articleTitle,
            occurredAt = Instant.now()
        )
}

@Service
class CollectionFailureEventRecorder(
    private val repository: CollectionFailureEventRepository
) : CollectionFailureRecorder {

    @Transactional
    override fun record(request: CollectionFailureEventRecordRequest): CollectionFailureEventEntity =
        repository.save(
            CollectionFailureEventEntity(
                runId = request.runId,
                sourceKey = request.sourceId,
                stage = request.stage,
                failureKind = request.failureKind,
                retryable = request.retryable,
                message = request.message,
                fingerprint = fingerprintFor(request),
                articleExternalId = request.articleExternalId,
                articleUrl = request.articleUrl,
                articleTitle = request.articleTitle,
                occurredAt = Instant.now()
            )
        )

    private fun fingerprintFor(request: CollectionFailureEventRecordRequest): String {
        val source = listOf(
            request.sourceId,
            request.stage.name,
            request.failureKind.name,
            request.message,
            request.articleExternalId.orEmpty(),
            request.articleUrl.orEmpty(),
            request.articleTitle.orEmpty()
        ).joinToString("|")

        val digest = MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }.take(64)
    }
}
