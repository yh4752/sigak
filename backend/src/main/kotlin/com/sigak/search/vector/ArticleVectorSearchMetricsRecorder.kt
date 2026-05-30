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
                averageTotalElapsedMs = 0.0,
                p50TotalElapsedMs = 0,
                p95TotalElapsedMs = 0,
                averageEmbeddingElapsedMs = 0.0,
                averageQdrantElapsedMs = 0.0,
                averageArticleLoadElapsedMs = 0.0,
                lastSearch = null
            )
        }

        val snapshot = observations.toList()
        val totalLatencies = snapshot.map { observation -> observation.totalElapsedMs }.sorted()

        return ArticleVectorSearchMetricsResponse(
            totalSearchCount = snapshot.size.toLong(),
            averageTotalElapsedMs = totalLatencies.average(),
            p50TotalElapsedMs = percentile(totalLatencies, 0.50),
            p95TotalElapsedMs = percentile(totalLatencies, 0.95),
            averageEmbeddingElapsedMs = snapshot.map { observation -> observation.embeddingElapsedMs }.average(),
            averageQdrantElapsedMs = snapshot.map { observation -> observation.qdrantElapsedMs }.average(),
            averageArticleLoadElapsedMs = snapshot.map { observation -> observation.articleLoadElapsedMs }.average(),
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
