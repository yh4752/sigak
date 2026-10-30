package com.sigak.collection.automation

import com.sigak.collection.config.CollectionAutomationProperties
import com.sigak.collection.service.CollectionAutomationService
import com.sigak.collection.dto.CollectionRunRequest
import com.sigak.collection.dto.CollectionRunResponse
import com.sigak.collection.dto.CollectionRunStatus
import com.sigak.collection.service.CollectionRunService
import com.sigak.search.graph.ArticleGraphProjectionRebuildResponse
import com.sigak.search.graph.ArticleGraphProjectionRebuildService
import com.sigak.search.projection.ArticleSearchProjectionRebuildResponse
import com.sigak.search.projection.ArticleSearchProjectionRebuildService
import com.sigak.search.vector.ArticleVectorProjectionRebuildResponse
import com.sigak.search.vector.ArticleVectorProjectionRebuildService
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.mockito.Mockito.*

class CollectionAutomationServiceTest {
    private val collection = mock(CollectionRunService::class.java)
    private val keyword = mock(ArticleSearchProjectionRebuildService::class.java)
    private val vector = mock(ArticleVectorProjectionRebuildService::class.java)
    private val graph = mock(ArticleGraphProjectionRebuildService::class.java)
    private val now = Instant.parse("2026-10-30T01:00:00Z")

    @Test
    fun disabledAutomationDoesNotCollectOrIndex() {
        val service = service(CollectionAutomationProperties())
        service.runCycle()
        assertEquals("DISABLED", service.status().state)
        verifyNoInteractions(collection, keyword, vector, graph)
    }

    @Test
    fun rejectsInvalidScheduleAndBatchLimits() {
        assertFailsWith<IllegalArgumentException> {
            CollectionAutomationProperties(enabled = true, fixedDelay = Duration.ofSeconds(59)).validate()
        }
        assertFailsWith<IllegalArgumentException> {
            CollectionAutomationProperties(maxArticlesPerSource = 21).validate()
        }
        assertFailsWith<IllegalArgumentException> {
            CollectionAutomationProperties(initialDelay = Duration.ofSeconds(-1)).validate()
        }
    }

    @Test
    fun collectsBeforeRebuildingAndRecordsSuccessfulCounts() {
        val response = response(2)
        `when`(collection.run(CollectionRunRequest(maxArticlesPerSource = 20))).thenReturn(response)
        projectionsSucceed()
        val service = service()
        service.runCycle()
        val status = service.status()
        assertEquals("COMPLETED", status.state)
        assertEquals(1, status.attemptedRunCount)
        assertEquals(2, status.lastRun?.publishedArticleCount)
        assertEquals(response.runId, status.lastRun?.runId)
        assertEquals(now, status.lastSuccessfulAt)
        assertEquals(now, status.lastPublishedAt)
        assertEquals(now.plusSeconds(600), status.nextEligibleAt)
        val order = inOrder(collection, keyword, vector, graph)
        order.verify(collection).run(CollectionRunRequest(maxArticlesPerSource = 20))
        order.verify(keyword).rebuild()
        order.verify(vector).rebuild()
        order.verify(graph).rebuild()
    }

    @Test
    fun repairsProjectionsOnStartupButSkipsUnchangedLaterCycles() {
        `when`(collection.run(CollectionRunRequest(maxArticlesPerSource = 20))).thenReturn(response(0))
        projectionsSucceed()
        val service = service()
        service.runCycle()
        service.runCycle()
        verify(keyword, times(1)).rebuild()
        verify(vector, times(1)).rebuild()
        verify(graph, times(1)).rebuild()
        assertEquals(2, service.status().attemptedRunCount)
        assertNull(service.status().lastPublishedAt)
    }

    @Test
    fun failedVectorDoesNotBlockGraphAndIsRetriedWithoutNewArticles() {
        `when`(collection.run(CollectionRunRequest(maxArticlesPerSource = 20))).thenReturn(response(0))
        projectionsSucceed()
        `when`(vector.rebuild()).thenThrow(IllegalStateException("do not expose this remote error"))
            .thenReturn(vectorResponse())
        val service = service()
        service.runCycle()
        assertEquals("PARTIAL", service.status().state)
        assertTrue(service.status().projectionRefreshPending)
        assertEquals("IllegalStateException", service.status().projectionResults[1].failureType)
        assertNull(service.status().lastSuccessfulAt)
        verify(graph).rebuild()
        service.runCycle()
        assertEquals("COMPLETED", service.status().state)
        assertEquals(false, service.status().projectionRefreshPending)
        verify(vector, times(2)).rebuild()
    }

    @Test
    fun recordsCollectionFailureAndAllowsNextCycle() {
        `when`(collection.run(CollectionRunRequest(maxArticlesPerSource = 20)))
            .thenThrow(IllegalStateException("private upstream details"))
            .thenReturn(response(1))
        projectionsSucceed()
        val service = service()
        service.runCycle()
        assertEquals("FAILED", service.status().state)
        assertEquals("IllegalStateException", service.status().failureType)
        service.runCycle()
        assertEquals("COMPLETED", service.status().state)
        assertEquals(2, service.status().attemptedRunCount)
        assertNull(service.status().failureType)
    }

    @Test
    fun retriesIndexingWhenCollectionThrowsAfterAnEarlierSuccessfulCycle() {
        `when`(collection.run(CollectionRunRequest(maxArticlesPerSource = 20)))
            .thenReturn(response(0))
            .thenThrow(IllegalStateException("may have persisted before failing"))
            .thenReturn(response(0))
        projectionsSucceed()
        val service = service()
        service.runCycle()
        service.runCycle()
        assertTrue(service.status().projectionRefreshPending)
        service.runCycle()
        verify(keyword, times(2)).rebuild()
        verify(vector, times(2)).rebuild()
    }

    @Test
    fun returnedProjectionFailureAndPartialCollectionAreNotReportedAsCompleted() {
        `when`(collection.run(CollectionRunRequest(maxArticlesPerSource = 20)))
            .thenReturn(response(1).copy(status = CollectionRunStatus.PARTIAL, failedSourceCount = 1))
        projectionsSucceed()
        `when`(keyword.rebuild()).thenReturn(ArticleSearchProjectionRebuildResponse("failed", "articles", 0, 1, "private details"))
        val service = service()
        service.runCycle()
        assertEquals("PARTIAL", service.status().state)
        assertEquals(1, service.status().lastRun?.failedSourceCount)
        assertEquals("PROJECTION_REFRESH_FAILED", service.status().projectionResults[0].failureType)
        assertNull(service.status().lastSuccessfulAt)
        assertEquals(now, service.status().lastPublishedAt)
    }

    @Test
    fun allFailedSourcesRemainFailedEvenIfProjectionRepairSucceeded() {
        `when`(collection.run(CollectionRunRequest(maxArticlesPerSource = 20)))
            .thenReturn(response(0).copy(status = CollectionRunStatus.FAILED, failedSourceCount = 1))
        projectionsSucceed()
        val service = service()
        service.runCycle()
        assertEquals("FAILED", service.status().state)
        assertNull(service.status().lastSuccessfulAt)
    }

    @Test
    fun overlappingCycleDoesNotCallCollectorTwice() {
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        `when`(collection.run(CollectionRunRequest(maxArticlesPerSource = 20))).thenAnswer {
            entered.countDown()
            check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
            response(0)
        }
        projectionsSucceed()
        val service = service()
        val thread = Thread { service.runCycle() }
        thread.start()
        try {
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            service.runCycle()
            assertEquals("RUNNING", service.status().state)
            assertEquals(1, service.status().attemptedRunCount)
        } finally {
            release.countDown()
            thread.join(5000)
        }
        assertEquals("COMPLETED", service.status().state)
        verify(collection, times(1)).run(CollectionRunRequest(maxArticlesPerSource = 20))
    }

    @Test
    fun partialCycleDoesNotAdvanceLastFullSuccessOrLastPublication() {
        class MutableClock(var time: Instant) : Clock() {
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId): Clock = this
            override fun instant() = time
        }
        val clock = MutableClock(now)
        `when`(collection.run(CollectionRunRequest(maxArticlesPerSource = 20)))
            .thenReturn(response(1))
            .thenReturn(response(0).copy(status = CollectionRunStatus.PARTIAL, failedSourceCount = 1))
        projectionsSucceed()
        val service = CollectionAutomationService(CollectionAutomationProperties(enabled = true),
            collection, keyword, vector, graph, clock)
        service.runCycle()
        clock.time = now.plusSeconds(600)
        service.runCycle()
        assertEquals("PARTIAL", service.status().state)
        assertEquals(now, service.status().lastSuccessfulAt)
        assertEquals(now, service.status().lastPublishedAt)
        assertEquals(clock.time, service.status().lastFinishedAt)
        assertEquals(clock.time.plusSeconds(600), service.status().nextEligibleAt)
    }

    private fun service(properties: CollectionAutomationProperties = CollectionAutomationProperties(enabled = true)) =
        CollectionAutomationService(properties, collection, keyword, vector, graph,
            Clock.fixed(now, ZoneOffset.UTC))

    private fun projectionsSucceed() {
        `when`(keyword.rebuild()).thenReturn(ArticleSearchProjectionRebuildResponse("completed", "articles", 2, 1, null))
        `when`(vector.rebuild()).thenReturn(vectorResponse())
        `when`(graph.rebuild()).thenReturn(ArticleGraphProjectionRebuildResponse("completed", now.toString(), 2, 1, 2, 0, 1, null))
    }

    private fun vectorResponse() = ArticleVectorProjectionRebuildResponse("completed", "vectors", 2, "local", "fixture", 3, 1, null)

    private fun response(published: Int) = CollectionRunResponse(
        runId = UUID.randomUUID(), status = CollectionRunStatus.COMPLETED, requestedSourceIds = listOf("openai-blog"),
        selectedSourceCount = 1, fetchedSourceCount = 1, failedSourceCount = 0,
        discoveredArticleCount = 2, publishedArticleCount = published, skippedArticleCount = 2 - published,
        failedArticleCount = 0, publishedArticleIds = if (published > 0) listOf(11L, 12L).take(published) else emptyList(),
        skippedArticleIds = emptyList(), durationMs = 1, sourceResults = emptyList()
    )
}
