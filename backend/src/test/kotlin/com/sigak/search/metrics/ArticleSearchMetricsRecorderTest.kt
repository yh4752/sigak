package com.sigak.search.metrics

import kotlin.test.Test
import kotlin.test.assertEquals

class ArticleSearchMetricsRecorderTest {

    @Test
    fun summarizesSearchLatencyAndFallbackCounts() {
        val recorder = ArticleSearchMetricsRecorder()

        recorder.record(
            ArticleSearchMetricObservation(
                queryLength = 5,
                resultCount = 1,
                fallback = false,
                elapsedMs = 10
            )
        )
        recorder.record(
            ArticleSearchMetricObservation(
                queryLength = 6,
                resultCount = 2,
                fallback = true,
                elapsedMs = 30
            )
        )

        val summary = recorder.summarize()

        assertEquals(2, summary.totalSearchCount)
        assertEquals(1, summary.elasticsearchSearchCount)
        assertEquals(1, summary.fallbackSearchCount)
        assertEquals(0.5, summary.fallbackRate)
        assertEquals(20.0, summary.averageElapsedMs)
        assertEquals(10, summary.p50ElapsedMs)
        assertEquals(30, summary.p95ElapsedMs)
        assertEquals(true, summary.lastSearch?.fallback)
        assertEquals(30, summary.lastSearch?.elapsedMs)
    }

    @Test
    fun returnsEmptySummaryBeforeAnySearchIsRecorded() {
        val recorder = ArticleSearchMetricsRecorder()

        val summary = recorder.summarize()

        assertEquals(0, summary.totalSearchCount)
        assertEquals(0, summary.elasticsearchSearchCount)
        assertEquals(0, summary.fallbackSearchCount)
        assertEquals(0.0, summary.fallbackRate)
        assertEquals(0.0, summary.averageElapsedMs)
        assertEquals(0, summary.p50ElapsedMs)
        assertEquals(0, summary.p95ElapsedMs)
        assertEquals(null, summary.lastSearch)
    }
}
