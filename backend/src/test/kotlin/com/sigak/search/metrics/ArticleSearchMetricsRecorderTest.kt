package com.sigak.search.metrics

import com.sigak.search.hybrid.ArticlePublicSearchMode
import kotlin.test.Test
import kotlin.test.assertEquals

class ArticleSearchMetricsRecorderTest {

    @Test
    fun summarizesModeAwareSearchMetrics() {
        val recorder = ArticleSearchMetricsRecorder()

        recorder.record(
            observation(
                mode = ArticlePublicSearchMode.HYBRID,
                resultCount = 2,
                totalElapsedMs = 20
            )
        )
        recorder.record(
            observation(
                mode = ArticlePublicSearchMode.POSTGRES_FALLBACK,
                resultCount = 1,
                keywordFailed = true,
                vectorFailed = true,
                fallbackReason = "KEYWORD_SEARCH_FAILED; QDRANT_SEARCH_FAILED",
                totalElapsedMs = 40
            )
        )

        val summary = recorder.summarize()

        assertEquals(2, summary.totalSearchCount)
        assertEquals(1, summary.hybridSearchCount)
        assertEquals(0, summary.keywordOnlySearchCount)
        assertEquals(0, summary.vectorOnlySearchCount)
        assertEquals(1, summary.postgresFallbackSearchCount)
        assertEquals(0.5, summary.fallbackRate)
        assertEquals(30.0, summary.averageTotalElapsedMs)
        assertEquals(20, summary.p50TotalElapsedMs)
        assertEquals(40, summary.p95TotalElapsedMs)
        assertEquals(ArticlePublicSearchMode.POSTGRES_FALLBACK, summary.lastSearch?.mode)
        assertEquals("KEYWORD_SEARCH_FAILED; QDRANT_SEARCH_FAILED", summary.lastSearch?.fallbackReason)
    }

    @Test
    fun returnsEmptySummaryBeforeAnySearchIsRecorded() {
        val recorder = ArticleSearchMetricsRecorder()

        val summary = recorder.summarize()

        assertEquals(0, summary.totalSearchCount)
        assertEquals(0, summary.hybridSearchCount)
        assertEquals(0, summary.keywordOnlySearchCount)
        assertEquals(0, summary.vectorOnlySearchCount)
        assertEquals(0, summary.postgresFallbackSearchCount)
        assertEquals(0.0, summary.fallbackRate)
        assertEquals(0.0, summary.averageTotalElapsedMs)
        assertEquals(0, summary.p50TotalElapsedMs)
        assertEquals(0, summary.p95TotalElapsedMs)
        assertEquals(null, summary.lastSearch)
    }

    private fun observation(
        mode: ArticlePublicSearchMode,
        resultCount: Int,
        keywordFailed: Boolean = false,
        vectorFailed: Boolean = false,
        fallbackReason: String? = null,
        totalElapsedMs: Long
    ): ArticleSearchMetricObservation =
        ArticleSearchMetricObservation(
            queryLength = 5,
            resultCount = resultCount,
            mode = mode,
            keywordCandidateCount = 2,
            vectorCandidateCount = 2,
            fusedCandidateCount = 3,
            staleCandidateCount = 0,
            keywordFailed = keywordFailed,
            vectorFailed = vectorFailed,
            fallbackReason = fallbackReason,
            keywordElapsedMs = 3,
            embeddingElapsedMs = 4,
            vectorElapsedMs = 5,
            fusionElapsedMs = 1,
            articleReloadElapsedMs = 2,
            totalElapsedMs = totalElapsedMs
        )
}
