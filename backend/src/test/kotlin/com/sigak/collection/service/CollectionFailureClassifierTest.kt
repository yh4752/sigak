package com.sigak.collection.service

import com.sigak.collection.domain.CollectionFailureKind
import com.sigak.collection.dto.CollectionFailureStage
import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException

class CollectionFailureClassifierTest {

    private val classifier = CollectionFailureClassifier()

    @Test
    fun classifyFetchTimeoutAsRetryableTransientFetch() {
        val result = classifier.classify(
            stage = CollectionFailureStage.FETCH_SOURCE,
            exception = ResourceAccessException("timeout", SocketTimeoutException("read timed out"))
        )

        assertEquals(CollectionFailureKind.TRANSIENT_FETCH, result.failureKind)
        assertEquals(true, result.retryable)
        assertEquals("ResourceAccessException: timeout", result.message)
    }

    @Test
    fun classifyFetchServerErrorAndRateLimitAsRetryableTransientFetch() {
        val serverError = classifier.classify(
            stage = CollectionFailureStage.FETCH_SOURCE,
            exception = HttpServerErrorException(HttpStatus.BAD_GATEWAY, "bad gateway")
        )
        val rateLimit = classifier.classify(
            stage = CollectionFailureStage.FETCH_SOURCE,
            exception = HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS, "rate limited")
        )

        assertEquals(CollectionFailureKind.TRANSIENT_FETCH, serverError.failureKind)
        assertEquals(true, serverError.retryable)
        assertEquals(CollectionFailureKind.TRANSIENT_FETCH, rateLimit.failureKind)
        assertEquals(true, rateLimit.retryable)
    }

    @Test
    fun classifyFetchNotFoundAsNonRetryableSourceFormat() {
        val result = classifier.classify(
            stage = CollectionFailureStage.FETCH_SOURCE,
            exception = HttpClientErrorException(HttpStatus.NOT_FOUND, "missing")
        )

        assertEquals(CollectionFailureKind.SOURCE_FORMAT, result.failureKind)
        assertEquals(false, result.retryable)
    }

    @Test
    fun classifyParseFailureAsNonRetryableSourceFormat() {
        val result = classifier.classify(
            stage = CollectionFailureStage.PARSE_SOURCE,
            exception = IllegalArgumentException("invalid xml")
        )

        assertEquals(CollectionFailureKind.SOURCE_FORMAT, result.failureKind)
        assertEquals(false, result.retryable)
    }

    @Test
    fun classifyPublishValidationFailureAsNonRetryableInvalidArticle() {
        val result = classifier.classify(
            stage = CollectionFailureStage.PUBLISH_ARTICLE,
            exception = IllegalArgumentException("title must not be blank")
        )

        assertEquals(CollectionFailureKind.INVALID_ARTICLE, result.failureKind)
        assertEquals(false, result.retryable)
    }

    @Test
    fun classifyPublishPersistenceFailureAsNonRetryablePersistence() {
        val result = classifier.classify(
            stage = CollectionFailureStage.PUBLISH_ARTICLE,
            exception = DataIntegrityViolationException("constraint")
        )

        assertEquals(CollectionFailureKind.PERSISTENCE, result.failureKind)
        assertEquals(false, result.retryable)
    }
}
