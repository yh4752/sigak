package com.sigak.collection.service

import com.sigak.SigakBackendApplication
import com.sigak.collection.domain.CollectionFailureEventEntity
import com.sigak.collection.domain.CollectionFailureKind
import com.sigak.collection.dto.CollectionFailureEventSearchRequest
import com.sigak.collection.dto.CollectionFailureStage
import com.sigak.collection.repository.CollectionFailureEventRepository
import com.sigak.support.PostgresIntegrationTest
import java.time.Instant
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(classes = [SigakBackendApplication::class])
class CollectionFailureEventQueryServiceTest : PostgresIntegrationTest() {

    @Autowired
    private lateinit var service: CollectionFailureEventQueryService

    @Autowired
    private lateinit var repository: CollectionFailureEventRepository

    @BeforeTest
    @AfterTest
    fun clearFailureEvents() {
        repository.deleteAll()
    }

    @Test
    fun searchFiltersFailureEventsAndReturnsLatestFirst() {
        val runId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
        val older = repository.save(
            failureEvent(
                runId = runId,
                sourceKey = "github-blog",
                retryable = true,
                occurredAt = Instant.parse("2026-05-31T10:00:00Z"),
                message = "SocketTimeoutException: timed out"
            )
        )
        repository.save(
            failureEvent(
                runId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                sourceKey = "github-blog",
                retryable = true,
                occurredAt = Instant.parse("2026-05-31T12:00:00Z"),
                message = "Connection reset"
            )
        )
        val newest = repository.save(
            failureEvent(
                runId = runId,
                sourceKey = "github-blog",
                retryable = true,
                occurredAt = Instant.parse("2026-05-31T11:00:00Z"),
                message = "HTTP 503"
            )
        )
        repository.save(
            failureEvent(
                runId = runId,
                sourceKey = "openai-blog",
                retryable = true,
                occurredAt = Instant.parse("2026-05-31T13:00:00Z"),
                message = "Other source"
            )
        )
        repository.save(
            failureEvent(
                runId = runId,
                sourceKey = "github-blog",
                retryable = false,
                occurredAt = Instant.parse("2026-05-31T14:00:00Z"),
                message = "Invalid article"
            )
        )

        val response = service.search(
            CollectionFailureEventSearchRequest(
                sourceId = " github-blog ",
                runId = runId,
                retryable = true,
                limit = 2
            )
        )

        assertEquals(2, response.returnedCount)
        assertEquals(listOf(newest.id, older.id), response.events.map { event -> event.id })
        assertEquals("github-blog", response.events.first().sourceId)
        assertEquals(CollectionFailureKind.TRANSIENT_FETCH, response.events.first().failureKind)
        assertEquals(CollectionFailureStage.FETCH_SOURCE, response.events.first().stage)
    }

    @Test
    fun searchRejectsLimitOutsideAllowedRange() {
        val exception = assertFailsWith<IllegalArgumentException> {
            service.search(CollectionFailureEventSearchRequest(limit = 0))
        }

        assertEquals("limit must be between 1 and 100", exception.message)
    }

    private fun failureEvent(
        runId: UUID,
        sourceKey: String,
        retryable: Boolean,
        occurredAt: Instant,
        message: String
    ): CollectionFailureEventEntity =
        CollectionFailureEventEntity(
            runId = runId,
            sourceKey = sourceKey,
            stage = CollectionFailureStage.FETCH_SOURCE,
            failureKind = CollectionFailureKind.TRANSIENT_FETCH,
            retryable = retryable,
            message = message,
            fingerprint = "$sourceKey:$message",
            occurredAt = occurredAt
        )
}
