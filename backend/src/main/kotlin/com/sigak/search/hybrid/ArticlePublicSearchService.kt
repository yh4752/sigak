package com.sigak.search.hybrid

import com.sigak.search.config.SearchInfrastructureProperties
import com.sigak.search.service.ArticleKeywordSearchService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ArticlePublicSearchService(
    private val articleKeywordSearchService: ArticleKeywordSearchService,
    private val articleVectorCandidateSearcher: ArticleVectorCandidateSearcher,
    private val reciprocalRankFusion: ReciprocalRankFusion,
    private val properties: SearchInfrastructureProperties
) {

    fun search(query: String): ArticlePublicSearchResult {
        val totalStartedAt = System.nanoTime()
        val normalizedQuery = query.trim()

        if (properties.mode == ArticleSearchMode.KEYWORD) {
            return searchKeywordOnly(normalizedQuery, totalStartedAt)
        }

        val keywordResult = runCatchingMeasured {
            articleKeywordSearchService.searchArticleIds(
                query = normalizedQuery,
                limit = properties.hybrid.keywordCandidateLimit
            ).mapIndexed { index, articleId ->
                ArticleSearchCandidate(articleId = articleId, rank = index + 1)
            }
        }
        val vectorResult = runCatchingMeasured {
            articleVectorCandidateSearcher.search(
                query = normalizedQuery,
                limit = properties.hybrid.vectorCandidateLimit
            )
        }

        val keywordCandidates = keywordResult.valueOrNull().orEmpty()
        val vectorCandidates = vectorResult.valueOrNull()?.candidates.orEmpty()
        val fusionResult = measureElapsed {
            reciprocalRankFusion.fuse(
                keywordCandidates = keywordCandidates,
                vectorCandidates = vectorCandidates,
                rrfK = properties.hybrid.rrfK,
                keywordWeight = properties.hybrid.keywordWeight,
                vectorWeight = properties.hybrid.vectorWeight,
                limit = properties.hybrid.resultLimit
            )
        }

        val mode = resolveMode(keywordResult, vectorResult)
        val articleIds = when (mode) {
            ArticlePublicSearchMode.HYBRID -> fusionResult.value.map { candidate -> candidate.articleId }
            ArticlePublicSearchMode.KEYWORD_ONLY -> keywordCandidates.map { candidate -> candidate.articleId }
                .take(properties.hybrid.resultLimit)
            ArticlePublicSearchMode.VECTOR_ONLY -> vectorCandidates.map { candidate -> candidate.articleId }
                .take(properties.hybrid.resultLimit)
            ArticlePublicSearchMode.POSTGRES_FALLBACK -> emptyList()
        }
        val vectorFailure = vectorResult.exception as? ArticleVectorCandidateSearchException

        return ArticlePublicSearchResult(
            articleIds = articleIds,
            mode = mode,
            keywordCandidateCount = keywordCandidates.size,
            vectorCandidateCount = vectorCandidates.size,
            fusedCandidateCount = if (mode == ArticlePublicSearchMode.HYBRID) fusionResult.value.size else 0,
            keywordFailed = keywordResult.exception != null,
            vectorFailed = vectorResult.exception != null,
            fallbackReason = fallbackReason(keywordResult.exception, vectorResult.exception),
            keywordElapsedMs = keywordResult.elapsedMs,
            embeddingElapsedMs = vectorResult.valueOrNull()?.embeddingElapsedMs ?: vectorFailure?.embeddingElapsedMs ?: 0,
            vectorElapsedMs = vectorResult.valueOrNull()?.vectorElapsedMs ?: vectorFailure?.vectorElapsedMs ?: 0,
            fusionElapsedMs = fusionResult.elapsedMs,
            totalElapsedMs = elapsedMillis(totalStartedAt)
        )
    }

    private fun searchKeywordOnly(query: String, totalStartedAt: Long): ArticlePublicSearchResult {
        val keywordResult = runCatchingMeasured {
            articleKeywordSearchService.searchArticleIds(
                query = query,
                limit = properties.hybrid.keywordCandidateLimit
            )
        }

        if (keywordResult.exception != null) {
            logger.warn("Keyword-only article search failed queryLength={} reason={}", query.length, keywordResult.exception.message)

            return ArticlePublicSearchResult(
                articleIds = emptyList(),
                mode = ArticlePublicSearchMode.POSTGRES_FALLBACK,
                keywordCandidateCount = 0,
                vectorCandidateCount = 0,
                fusedCandidateCount = 0,
                keywordFailed = true,
                vectorFailed = false,
                fallbackReason = fallbackReason(keywordResult.exception, null),
                keywordElapsedMs = keywordResult.elapsedMs,
                embeddingElapsedMs = 0,
                vectorElapsedMs = 0,
                fusionElapsedMs = 0,
                totalElapsedMs = elapsedMillis(totalStartedAt)
            )
        }

        val articleIds = keywordResult.valueOrNull().orEmpty()
            .take(properties.hybrid.resultLimit)
        return ArticlePublicSearchResult(
            articleIds = articleIds,
            mode = ArticlePublicSearchMode.KEYWORD_ONLY,
            keywordCandidateCount = articleIds.size,
            vectorCandidateCount = 0,
            fusedCandidateCount = 0,
            keywordFailed = false,
            vectorFailed = false,
            fallbackReason = null,
            keywordElapsedMs = keywordResult.elapsedMs,
            embeddingElapsedMs = 0,
            vectorElapsedMs = 0,
            fusionElapsedMs = 0,
            totalElapsedMs = elapsedMillis(totalStartedAt)
        )
    }

    private fun resolveMode(
        keywordResult: SearchAttempt<List<ArticleSearchCandidate>>,
        vectorResult: SearchAttempt<ArticleVectorCandidateSearchResult>
    ): ArticlePublicSearchMode =
        when {
            keywordResult.exception == null && vectorResult.exception == null -> ArticlePublicSearchMode.HYBRID
            keywordResult.exception == null -> ArticlePublicSearchMode.KEYWORD_ONLY
            vectorResult.exception == null -> ArticlePublicSearchMode.VECTOR_ONLY
            else -> ArticlePublicSearchMode.POSTGRES_FALLBACK
        }

    private fun fallbackReason(keywordException: Exception?, vectorException: Exception?): String? =
        listOfNotNull(
            keywordException?.let { "KEYWORD_SEARCH_FAILED" },
            vectorException?.toVectorFailureReason()
        )
            .takeIf { reasons -> reasons.isNotEmpty() }
            ?.joinToString("; ")

    private fun Exception.toVectorFailureReason(): String =
        when (this) {
            is ArticleVectorCandidateSearchException -> reasonCode
            else -> "VECTOR_SEARCH_FAILED"
        }

    private fun <T> runCatchingMeasured(block: () -> T): SearchAttempt<T> {
        val startedAt = System.nanoTime()

        return try {
            SearchAttempt(value = block(), exception = null, elapsedMs = elapsedMillis(startedAt))
        } catch (exception: Exception) {
            // 외부 검색 경계에서는 client별 예외 타입이 달라질 수 있어 Exception까지 fallback 대상으로 본다.
            SearchAttempt(value = null, exception = exception, elapsedMs = elapsedMillis(startedAt))
        }
    }

    private fun <T> measureElapsed(block: () -> T): Measured<T> {
        val startedAt = System.nanoTime()
        val value = block()

        return Measured(value = value, elapsedMs = elapsedMillis(startedAt))
    }

    private fun elapsedMillis(startedAt: Long): Long =
        (System.nanoTime() - startedAt) / 1_000_000

    private data class SearchAttempt<T>(
        val value: T?,
        val exception: Exception?,
        val elapsedMs: Long
    ) {
        fun valueOrNull(): T? = value
    }

    private data class Measured<T>(
        val value: T,
        val elapsedMs: Long
    )

    private companion object {
        private val logger = LoggerFactory.getLogger(ArticlePublicSearchService::class.java)
    }
}
