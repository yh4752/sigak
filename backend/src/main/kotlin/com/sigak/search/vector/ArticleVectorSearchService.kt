package com.sigak.search.vector

import com.sigak.article.dto.ArticleResponse
import com.sigak.article.service.ArticleService
import com.sigak.common.time.elapsedMillis
import com.sigak.common.time.measureElapsed
import com.sigak.search.config.SearchInfrastructureProperties
import com.sigak.search.hybrid.ArticleSearchCandidate
import com.sigak.search.hybrid.ArticleVectorCandidateSearchService
import org.springframework.stereotype.Service

@Service
class ArticleVectorSearchService(
    private val articleVectorCandidateSearchService: ArticleVectorCandidateSearchService,
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

        val candidateResult = articleVectorCandidateSearchService.search(
            query = normalizedQuery,
            limit = limit
        )
        val articleLoadResult = measureElapsed {
            articleService.getApiReadyArticlesByIds(candidateResult.candidates.map { candidate -> candidate.articleId })
        }
        val timings = ArticleVectorSearchTimings(
            embeddingElapsedMs = candidateResult.embeddingElapsedMs,
            qdrantElapsedMs = candidateResult.vectorElapsedMs,
            articleLoadElapsedMs = articleLoadResult.elapsedMs,
            totalElapsedMs = elapsedMillis(totalStartedAt)
        )
        val results = articleLoadResult.value.toResults(candidateResult.candidates)

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
            collectionName = articleVectorCandidateSearchService.collectionName(),
            embeddingProvider = candidateResult.embeddingProvider,
            embeddingModelName = candidateResult.embeddingModelName,
            embeddingDimension = candidateResult.embeddingDimension,
            results = results,
            timings = timings
        )
    }

    private fun List<ArticleResponse>.toResults(candidates: List<ArticleSearchCandidate>): List<ArticleVectorSearchResult> {
        val scoresByArticleId = candidates.mapNotNull { candidate ->
            candidate.score?.let { score -> candidate.articleId to score }
        }.toMap()

        return mapNotNull { article ->
            val score = scoresByArticleId[article.id] ?: return@mapNotNull null

            ArticleVectorSearchResult(
                article = article,
                score = score
            )
        }
    }

}
