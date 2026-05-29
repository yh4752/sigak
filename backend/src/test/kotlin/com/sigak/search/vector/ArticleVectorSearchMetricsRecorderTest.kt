package com.sigak.search.vector

import kotlin.test.Test
import kotlin.test.assertEquals

class ArticleVectorSearchMetricsRecorderTest {

    private val recorder = ArticleVectorSearchMetricsRecorder()

    @Test
    fun summarizesVectorSearchTimings() {
        recorder.record(observation(total = 10, embedding = 4, qdrant = 3, articleLoad = 3))
        recorder.record(observation(total = 20, embedding = 8, qdrant = 7, articleLoad = 5))

        val response = recorder.summarize()

        assertEquals(2, response.totalSearchCount)
        assertEquals(15.0, response.averageTotalElapsedMs)
        assertEquals(10, response.p50TotalElapsedMs)
        assertEquals(20, response.p95TotalElapsedMs)
        assertEquals(6.0, response.averageEmbeddingElapsedMs)
        assertEquals(5.0, response.averageQdrantElapsedMs)
        assertEquals(4.0, response.averageArticleLoadElapsedMs)
        assertEquals(20, response.lastSearch!!.totalElapsedMs)
    }

    @Test
    fun returnsEmptySummaryWhenNoSearchesRecorded() {
        val response = recorder.summarize()

        assertEquals(0, response.totalSearchCount)
        assertEquals(0.0, response.averageTotalElapsedMs)
        assertEquals(0, response.p50TotalElapsedMs)
        assertEquals(0, response.p95TotalElapsedMs)
        assertEquals(null, response.lastSearch)
    }

    private fun observation(
        total: Long,
        embedding: Long,
        qdrant: Long,
        articleLoad: Long
    ): ArticleVectorSearchMetricObservation =
        ArticleVectorSearchMetricObservation(
            queryLength = 12,
            resultCount = 3,
            embeddingElapsedMs = embedding,
            qdrantElapsedMs = qdrant,
            articleLoadElapsedMs = articleLoad,
            totalElapsedMs = total
        )
}
