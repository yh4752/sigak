package com.sigak.search.vector

import com.sigak.ai.embedding.EmbeddingClient
import com.sigak.article.dto.ArticleResponse
import com.sigak.article.service.ArticleService
import com.sigak.search.config.SearchInfrastructureProperties
import org.springframework.stereotype.Service

@Service
class ArticleVectorSearchService(
    private val embeddingClient: EmbeddingClient,
    private val articleVectorProjectionIndexer: ArticleVectorProjectionIndexer,
    private val articleService: ArticleService,
    private val properties: SearchInfrastructureProperties,
    private val metricsRecorder: ArticleVectorSearchMetricsRecorder
) {

    fun search(request: ArticleVectorSearchRequest): ArticleVectorSearchResponse {
        val totalStartedAt = System.nanoTime()
        val normalizedQuery = request.query.trim()
        require(normalizedQuery.isNotBlank()) { "Vector search query must not be blank." }

        val limit = request.limit ?: properties.qdrant.defaultLimit
        require(limit in 1..properties.qdrant.maxLimit) {
            "Vector search limit must be between 1 and ${properties.qdrant.maxLimit}."
        }

        val embeddingResult = measureElapsed { embeddingClient.embedText(normalizedQuery) }
        val vectorSearchResult = measureElapsed {
            articleVectorProjectionIndexer.search(
                vector = embeddingResult.value.embedding,
                limit = limit
            )
        }
        val articleLoadResult = measureElapsed {
            articleService.getApiReadyArticlesByIds(vectorSearchResult.value.map { hit -> hit.articleId })
        }
        val timings = ArticleVectorSearchTimings(
            embeddingElapsedMs = embeddingResult.elapsedMs,
            qdrantElapsedMs = vectorSearchResult.elapsedMs,
            articleLoadElapsedMs = articleLoadResult.elapsedMs,
            totalElapsedMs = elapsedMillis(totalStartedAt)
        )
        val results = articleLoadResult.value.toResults(vectorSearchResult.value)

        metricsRecorder.record(
            ArticleVectorSearchMetricObservation(
                queryLength = normalizedQuery.length,
                resultCount = results.size,
                embeddingElapsedMs = timings.embeddingElapsedMs,
                qdrantElapsedMs = timings.qdrantElapsedMs,
                articleLoadElapsedMs = timings.articleLoadElapsedMs,
                totalElapsedMs = timings.totalElapsedMs
            )
        )

        return ArticleVectorSearchResponse(
            query = normalizedQuery,
            collectionName = articleVectorProjectionIndexer.collectionName(),
            embeddingProvider = embeddingResult.value.provider,
            embeddingModelName = embeddingResult.value.modelName,
            embeddingDimension = embeddingResult.value.dimension,
            results = results,
            timings = timings
        )
    }

    private fun List<ArticleResponse>.toResults(hits: List<ArticleVectorSearchHit>): List<ArticleVectorSearchResult> {
        val scoresByArticleId = hits.associate { hit -> hit.articleId to hit.score }

        return mapNotNull { article ->
            val score = scoresByArticleId[article.id] ?: return@mapNotNull null

            ArticleVectorSearchResult(
                article = article,
                score = score
            )
        }
    }

    private fun <T> measureElapsed(block: () -> T): Measured<T> {
        val startedAt = System.nanoTime()
        val value = block()

        return Measured(
            value = value,
            elapsedMs = elapsedMillis(startedAt)
        )
    }

    private fun elapsedMillis(startedAt: Long): Long =
        (System.nanoTime() - startedAt) / 1_000_000

    private data class Measured<T>(
        val value: T,
        val elapsedMs: Long
    )
}
