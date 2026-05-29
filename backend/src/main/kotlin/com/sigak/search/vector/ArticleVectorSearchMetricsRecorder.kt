package com.sigak.search.vector

import org.springframework.stereotype.Service

@Service
class ArticleVectorSearchMetricsRecorder {

    private val observations = ArrayDeque<ArticleVectorSearchMetricObservation>()

    @Synchronized
    fun record(observation: ArticleVectorSearchMetricObservation) {
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
    fun summarize(): ArticleVectorSearchMetricsResponse {
        if (observations.isEmpty()) {
            return ArticleVectorSearchMetricsResponse(
                totalSearchCount = 0,
                averageElapsedMs = 0.0,
                p50ElapsedMs = 0,
                p95ElapsedMs = 0,
                lastSearch = null
            )
        }

        val snapshot = observations.toList()
        val latencies = snapshot.map { observation -> observation.totalElapsedMs }.sorted()

        return ArticleVectorSearchMetricsResponse(
            totalSearchCount = snapshot.size.toLong(),
            averageElapsedMs = latencies.average(),
            p50ElapsedMs = percentile(latencies, 0.50),
            p95ElapsedMs = percentile(latencies, 0.95),
            lastSearch = snapshot.last().toSnapshotResponse()
        )
    }

    private fun percentile(sortedValues: List<Long>, percentile: Double): Long {
        val index = kotlin.math.ceil(sortedValues.size * percentile).toInt().coerceAtLeast(1) - 1

        return sortedValues[index.coerceAtMost(sortedValues.lastIndex)]
    }

    private fun ArticleVectorSearchMetricObservation.toSnapshotResponse(): ArticleVectorSearchMetricSnapshotResponse =
        ArticleVectorSearchMetricSnapshotResponse(
            queryLength = queryLength,
            resultCount = resultCount,
            embeddingElapsedMs = embeddingElapsedMs,
            qdrantElapsedMs = qdrantElapsedMs,
            articleLoadElapsedMs = articleLoadElapsedMs,
            totalElapsedMs = totalElapsedMs
        )

    private companion object {
        private const val MAX_RECENT_OBSERVATIONS = 100
    }
}
