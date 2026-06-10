package com.sigak.search.vector

import com.sigak.common.metrics.RecentObservationWindow
import com.sigak.common.metrics.percentileOf
import org.springframework.stereotype.Service

@Service
class ArticleVectorSearchMetricsRecorder {

    private val observations = RecentObservationWindow<ArticleVectorSearchMetricObservation>(MAX_RECENT_OBSERVATIONS)

    fun record(observation: ArticleVectorSearchMetricObservation) {
        observations.record(observation)
    }

    fun reset() {
        observations.reset()
    }

    fun summarize(): ArticleVectorSearchMetricsResponse {
        val snapshot = observations.snapshot()
        if (snapshot.isEmpty()) {
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

        val totalLatencies = snapshot.map { observation -> observation.totalElapsedMs }.sorted()

        return ArticleVectorSearchMetricsResponse(
            totalSearchCount = snapshot.size.toLong(),
            averageTotalElapsedMs = totalLatencies.average(),
            p50TotalElapsedMs = percentileOf(totalLatencies, 0.50),
            p95TotalElapsedMs = percentileOf(totalLatencies, 0.95),
            averageEmbeddingElapsedMs = snapshot.map { observation -> observation.embeddingElapsedMs }.average(),
            averageQdrantElapsedMs = snapshot.map { observation -> observation.qdrantElapsedMs }.average(),
            averageArticleLoadElapsedMs = snapshot.map { observation -> observation.articleLoadElapsedMs }.average(),
            lastSearch = snapshot.last().toSnapshotResponse()
        )
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
