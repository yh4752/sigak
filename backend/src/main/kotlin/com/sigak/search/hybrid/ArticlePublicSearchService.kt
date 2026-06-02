package com.sigak.search.hybrid

import com.sigak.common.time.Measured
import com.sigak.common.time.elapsedMillis
import com.sigak.common.time.measureElapsed
import com.sigak.search.config.SearchInfrastructureProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ArticlePublicSearchService(
    private val articleRetrievalCandidateService: ArticleRetrievalCandidateService,
    private val reciprocalRankFusion: ReciprocalRankFusion,
    private val properties: SearchInfrastructureProperties
) {

    fun search(query: String): ArticlePublicSearchResult {
        val totalStartedAt = System.nanoTime()
        val normalizedQuery = query.trim()

        if (properties.mode == ArticleSearchMode.KEYWORD) {
            return searchKeywordOnly(normalizedQuery, totalStartedAt)
        }

        val candidateResult = articleRetrievalCandidateService.search(normalizedQuery)
        val keywordResult = candidateResult.keyword
        val vectorResult = candidateResult.vector
        val keywordCandidates = keywordResult.value.orEmpty()
        val vectorCandidates = vectorResult.value?.candidates.orEmpty()
        val fusionResult = fuseCandidates(keywordCandidates, vectorCandidates)
        val mode = resolveMode(keywordResult, vectorResult)

        return ArticlePublicSearchResult(
            articleIds = selectArticleIds(
                mode = mode,
                keywordCandidates = keywordCandidates,
                vectorCandidates = vectorCandidates,
                fusedCandidates = fusionResult.value
            ),
            mode = mode,
            keywordCandidateCount = keywordCandidates.size,
            vectorCandidateCount = vectorCandidates.size,
            fusedCandidateCount = if (mode == ArticlePublicSearchMode.HYBRID) fusionResult.value.size else 0,
            keywordFailed = keywordResult.failed,
            vectorFailed = vectorResult.failed,
            fallbackReason = fallbackReason(keywordResult, vectorResult),
            keywordElapsedMs = keywordResult.elapsedMs,
            embeddingElapsedMs = vectorResult.embeddingElapsedMs,
            vectorElapsedMs = vectorResult.vectorElapsedMs,
            fusionElapsedMs = fusionResult.elapsedMs,
            totalElapsedMs = elapsedMillis(totalStartedAt)
        )
    }

    private fun fuseCandidates(
        keywordCandidates: List<ArticleSearchCandidate>,
        vectorCandidates: List<ArticleSearchCandidate>
    ): Measured<List<ArticleSearchCandidate>> =
        measureElapsed {
            reciprocalRankFusion.fuse(
                keywordCandidates = keywordCandidates,
                vectorCandidates = vectorCandidates,
                rrfK = properties.hybrid.rrfK,
                keywordWeight = properties.hybrid.keywordWeight,
                vectorWeight = properties.hybrid.vectorWeight,
                limit = properties.hybrid.resultLimit
            )
        }

    private fun selectArticleIds(
        mode: ArticlePublicSearchMode,
        keywordCandidates: List<ArticleSearchCandidate>,
        vectorCandidates: List<ArticleSearchCandidate>,
        fusedCandidates: List<ArticleSearchCandidate>
    ): List<Long> =
        when (mode) {
            ArticlePublicSearchMode.HYBRID -> fusedCandidates.map { candidate -> candidate.articleId }
            ArticlePublicSearchMode.KEYWORD_ONLY -> keywordCandidates.map { candidate -> candidate.articleId }
                .take(properties.hybrid.resultLimit)
            ArticlePublicSearchMode.VECTOR_ONLY -> vectorCandidates.map { candidate -> candidate.articleId }
                .take(properties.hybrid.resultLimit)
            ArticlePublicSearchMode.POSTGRES_FALLBACK -> emptyList()
    }

    private fun searchKeywordOnly(query: String, totalStartedAt: Long): ArticlePublicSearchResult {
        val keywordResult = articleRetrievalCandidateService.searchKeyword(query)
        if (keywordResult.failed) {
            logger.warn("Keyword-only article search failed queryLength={} reason={}", query.length, keywordResult.failureReason)

            return ArticlePublicSearchResult(
                articleIds = emptyList(),
                mode = ArticlePublicSearchMode.POSTGRES_FALLBACK,
                keywordCandidateCount = 0,
                vectorCandidateCount = 0,
                fusedCandidateCount = 0,
                keywordFailed = true,
                vectorFailed = false,
                fallbackReason = keywordResult.failureReason,
                keywordElapsedMs = keywordResult.elapsedMs,
                embeddingElapsedMs = 0,
                vectorElapsedMs = 0,
                fusionElapsedMs = 0,
                totalElapsedMs = elapsedMillis(totalStartedAt)
            )
        }

        val keywordArticleIds = keywordResult.value.orEmpty()
            .map { candidate -> candidate.articleId }
        val articleIds = keywordArticleIds
            .take(properties.hybrid.resultLimit)
        return ArticlePublicSearchResult(
            articleIds = articleIds,
            mode = ArticlePublicSearchMode.KEYWORD_ONLY,
            keywordCandidateCount = keywordArticleIds.size,
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
        keywordResult: ArticleRetrievalCandidateAttempt<List<ArticleSearchCandidate>>,
        vectorResult: ArticleRetrievalCandidateAttempt<ArticleVectorCandidateSearchResult>
    ): ArticlePublicSearchMode =
        when {
            !keywordResult.failed && !vectorResult.failed -> ArticlePublicSearchMode.HYBRID
            !keywordResult.failed -> ArticlePublicSearchMode.KEYWORD_ONLY
            !vectorResult.failed -> ArticlePublicSearchMode.VECTOR_ONLY
            else -> ArticlePublicSearchMode.POSTGRES_FALLBACK
        }

    private fun fallbackReason(
        keywordResult: ArticleRetrievalCandidateAttempt<List<ArticleSearchCandidate>>,
        vectorResult: ArticleRetrievalCandidateAttempt<ArticleVectorCandidateSearchResult>
    ): String? =
        listOfNotNull(keywordResult.failureReason, vectorResult.failureReason)
            .takeIf { reasons -> reasons.isNotEmpty() }
            ?.joinToString("; ")

    private companion object {
        private val logger = LoggerFactory.getLogger(ArticlePublicSearchService::class.java)
    }
}
