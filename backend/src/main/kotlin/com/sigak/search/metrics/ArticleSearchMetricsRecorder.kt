package com.sigak.search.metrics

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
                elasticsearchSearchCount = 0,
                fallbackSearchCount = 0,
                fallbackRate = 0.0,
                averageElapsedMs = 0.0,
                p50ElapsedMs = 0,
                p95ElapsedMs = 0,
                lastSearch = null
            )
        }

        val snapshot = observations.toList()
        val totalSearchCount = snapshot.size.toLong()
        val fallbackSearchCount = snapshot.count { observation -> observation.fallback }.toLong()
        val latencies = snapshot.map { observation -> observation.elapsedMs }.sorted()

        return ArticleSearchMetricsResponse(
            totalSearchCount = totalSearchCount,
            elasticsearchSearchCount = totalSearchCount - fallbackSearchCount,
            fallbackSearchCount = fallbackSearchCount,
            fallbackRate = fallbackSearchCount.toDouble() / totalSearchCount,
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

    private fun ArticleSearchMetricObservation.toSnapshotResponse(): ArticleSearchMetricSnapshotResponse =
        ArticleSearchMetricSnapshotResponse(
            queryLength = queryLength,
            resultCount = resultCount,
            fallback = fallback,
            elapsedMs = elapsedMs
        )

    private companion object {
        private const val MAX_RECENT_OBSERVATIONS = 100
    }
}
