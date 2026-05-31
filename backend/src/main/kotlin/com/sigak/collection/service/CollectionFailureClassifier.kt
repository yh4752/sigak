package com.sigak.collection.service

import com.sigak.collection.domain.CollectionFailureKind
import com.sigak.collection.dto.CollectionFailureStage
import java.net.SocketTimeoutException
import org.springframework.dao.DataAccessException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException

data class CollectionFailureClassification(
    val failureKind: CollectionFailureKind,
    val retryable: Boolean,
    val message: String
)

@Component
class CollectionFailureClassifier {

    fun classify(
        stage: CollectionFailureStage,
        exception: Throwable
    ): CollectionFailureClassification {
        val root = (exception as? SourceCollectionException)?.cause ?: exception
        val kind = failureKindFor(stage, root)
        return CollectionFailureClassification(
            failureKind = kind,
            retryable = retryableFor(kind),
            message = failureMessage(root)
        )
    }

    private fun failureKindFor(
        stage: CollectionFailureStage,
        exception: Throwable
    ): CollectionFailureKind =
        when (stage) {
            CollectionFailureStage.FETCH_SOURCE -> fetchFailureKindFor(exception)
            CollectionFailureStage.PARSE_SOURCE -> CollectionFailureKind.SOURCE_FORMAT
            CollectionFailureStage.PUBLISH_ARTICLE -> publishFailureKindFor(exception)
        }

    private fun fetchFailureKindFor(exception: Throwable): CollectionFailureKind =
        when {
            exception is ResourceAccessException && exception.containsCause<SocketTimeoutException>() ->
                CollectionFailureKind.TRANSIENT_FETCH
            exception is ResourceAccessException ->
                CollectionFailureKind.TRANSIENT_FETCH
            exception is HttpServerErrorException ->
                CollectionFailureKind.TRANSIENT_FETCH
            exception is HttpClientErrorException && exception.statusCode == HttpStatus.TOO_MANY_REQUESTS ->
                CollectionFailureKind.TRANSIENT_FETCH
            exception is HttpClientErrorException ->
                CollectionFailureKind.SOURCE_FORMAT
            else ->
                CollectionFailureKind.UNKNOWN
        }

    private fun publishFailureKindFor(exception: Throwable): CollectionFailureKind =
        when (exception) {
            is IllegalArgumentException -> CollectionFailureKind.INVALID_ARTICLE
            is DataAccessException -> CollectionFailureKind.PERSISTENCE
            else -> CollectionFailureKind.UNKNOWN
        }

    private fun retryableFor(kind: CollectionFailureKind): Boolean =
        kind == CollectionFailureKind.TRANSIENT_FETCH

    private inline fun <reified T : Throwable> Throwable.containsCause(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is T) {
                return true
            }
            current = current.cause
        }
        return false
    }
}
