package com.sigak.search.metrics

import com.sigak.search.hybrid.ArticlePublicSearchMode
import org.springframework.stereotype.Service

@Service
class ArticleSearchMetricsRecorder {

    private val observations = ArrayDeque<ArticleSearchMetricObservation>()

    @Synchronized
    fun record(observation: ArticleSearchMetricObservation) {
        observations.addLast(observation)
        if (observations.size > MAX_RECENT_OBSERVATIONS) {
            observations.removeFirst()
        }
    }

    @Synchronized
    fun reset() {
        observations.clear()
    }

    @Synchronized
    fun summarize(): ArticleSearchMetricsResponse {
        if (observations.isEmpty()) {
            return ArticleSearchMetricsResponse(
                totalSearchCount = 0,
                hybridSearchCount = 0,
                keywordOnlySearchCount = 0,
                vectorOnlySearchCount = 0,
                postgresFallbackSearchCount = 0,
                fallbackRate = 0.0,
                averageTotalElapsedMs = 0.0,
                p50TotalElapsedMs = 0,
                p95TotalElapsedMs = 0,
                lastSearch = null
            )
        }

        val snapshot = observations.toList()
        val totalSearchCount = snapshot.size.toLong()
        val postgresFallbackSearchCount = snapshot.count { observation ->
            observation.mode == ArticlePublicSearchMode.POSTGRES_FALLBACK
        }.toLong()
        val totalLatencies = snapshot.map { observation -> observation.totalElapsedMs }.sorted()

        return ArticleSearchMetricsResponse(
            totalSearchCount = totalSearchCount,
            hybridSearchCount = snapshot.count { observation -> observation.mode == ArticlePublicSearchMode.HYBRID }.toLong(),
            keywordOnlySearchCount = snapshot.count { observation -> observation.mode == ArticlePublicSearchMode.KEYWORD_ONLY }.toLong(),
            vectorOnlySearchCount = snapshot.count { observation -> observation.mode == ArticlePublicSearchMode.VECTOR_ONLY }.toLong(),
            postgresFallbackSearchCount = postgresFallbackSearchCount,
            fallbackRate = postgresFallbackSearchCount.toDouble() / totalSearchCount,
            averageTotalElapsedMs = totalLatencies.average(),
            p50TotalElapsedMs = percentile(totalLatencies, 0.50),
            p95TotalElapsedMs = percentile(totalLatencies, 0.95),
            lastSearch = snapshot.last().toSnapshotResponse()
        )
    }

    private fun percentile(sortedValues: List<Long>, percentile: Double): Long {
        val index = kotlin.math.ceil(sortedValues.size * percentile).toInt().coerceAtLeast(1) - 1

        return sortedValues[index.coerceAtMost(sortedValues.lastIndex)]
    }

    private fun ArticleSearchMetricObservation.toSnapshotResponse(): ArticleSearchMetricSnapshotResponse =
        ArticleSearchMetricSnapshotResponse(
            queryLength = queryLength,
            resultCount = resultCount,
            mode = mode,
            keywordCandidateCount = keywordCandidateCount,
            vectorCandidateCount = vectorCandidateCount,
            fusedCandidateCount = fusedCandidateCount,
            staleCandidateCount = staleCandidateCount,
            keywordFailed = keywordFailed,
            vectorFailed = vectorFailed,
            fallbackReason = fallbackReason,
            keywordElapsedMs = keywordElapsedMs,
            embeddingElapsedMs = embeddingElapsedMs,
            vectorElapsedMs = vectorElapsedMs,
            fusionElapsedMs = fusionElapsedMs,
            articleReloadElapsedMs = articleReloadElapsedMs,
            totalElapsedMs = totalElapsedMs
        )

    private companion object {
        private const val MAX_RECENT_OBSERVATIONS = 100
    }
}
