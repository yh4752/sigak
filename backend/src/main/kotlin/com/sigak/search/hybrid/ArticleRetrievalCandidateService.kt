package com.sigak.search.hybrid

import com.sigak.common.time.MeasuredAttempt
import com.sigak.common.time.runCatchingMeasured
import com.sigak.search.config.SearchInfrastructureProperties
import com.sigak.search.service.ArticleKeywordSearchService
import org.springframework.stereotype.Service

@Service
class ArticleRetrievalCandidateService(
    private val articleKeywordSearchService: ArticleKeywordSearchService,
    private val articleVectorCandidateSearcher: ArticleVectorCandidateSearcher,
    private val properties: SearchInfrastructureProperties
) : ArticleRetrievalCandidateProvider {

    override fun search(query: String): ArticleRetrievalCandidateSearchResult {
        val normalizedQuery = query.trim()

        return ArticleRetrievalCandidateSearchResult(
            query = normalizedQuery,
            keyword = searchKeyword(normalizedQuery),
            vector = searchVector(normalizedQuery)
        )
    }

    override fun searchKeyword(query: String): ArticleRetrievalCandidateAttempt<List<ArticleSearchCandidate>> {
        val normalizedQuery = query.trim()

        return searchKeywordCandidates(normalizedQuery)
    }

    override fun searchVector(query: String): ArticleRetrievalCandidateAttempt<ArticleVectorCandidateSearchResult> {
        val normalizedQuery = query.trim()

        return searchVectorCandidates(normalizedQuery)
    }

    private fun searchKeywordCandidates(query: String): ArticleRetrievalCandidateAttempt<List<ArticleSearchCandidate>> =
        runCatchingMeasured {
            articleKeywordSearchService.searchArticleIds(
                query = query,
                limit = properties.hybrid.keywordCandidateLimit
            ).mapIndexed { index, articleId ->
                ArticleSearchCandidate(articleId = articleId, rank = index + 1)
            }
        }.toKeywordAttempt()

    private fun searchVectorCandidates(query: String):
        ArticleRetrievalCandidateAttempt<ArticleVectorCandidateSearchResult> =
        runCatchingMeasured {
            articleVectorCandidateSearcher.search(
                query = query,
                limit = properties.hybrid.vectorCandidateLimit
            )
        }.toVectorAttempt()

    private fun <T> MeasuredAttempt<T>.toKeywordAttempt(): ArticleRetrievalCandidateAttempt<T> =
        ArticleRetrievalCandidateAttempt(
            value = value,
            exception = exception,
            failureReason = exception?.let { "KEYWORD_SEARCH_FAILED" },
            elapsedMs = elapsedMs,
            embeddingElapsedMs = 0,
            vectorElapsedMs = 0
        )

    private fun MeasuredAttempt<ArticleVectorCandidateSearchResult>.toVectorAttempt():
        ArticleRetrievalCandidateAttempt<ArticleVectorCandidateSearchResult> {
        val vectorFailure = exception as? ArticleVectorCandidateSearchException

        return ArticleRetrievalCandidateAttempt(
            value = value,
            exception = exception,
            failureReason = exception?.toVectorFailureReason(),
            elapsedMs = elapsedMs,
            embeddingElapsedMs = value?.embeddingElapsedMs ?: vectorFailure?.embeddingElapsedMs ?: 0,
            vectorElapsedMs = value?.vectorElapsedMs ?: vectorFailure?.vectorElapsedMs ?: 0
        )
    }

    private fun Exception.toVectorFailureReason(): String =
        when (this) {
            is ArticleVectorCandidateSearchException -> reasonCode
            else -> "VECTOR_SEARCH_FAILED"
        }
}

interface ArticleRetrievalCandidateProvider {
    fun search(query: String): ArticleRetrievalCandidateSearchResult

    fun searchKeyword(query: String): ArticleRetrievalCandidateAttempt<List<ArticleSearchCandidate>>

    fun searchVector(query: String): ArticleRetrievalCandidateAttempt<ArticleVectorCandidateSearchResult>
}

data class ArticleRetrievalCandidateSearchResult(
    val query: String,
    val keyword: ArticleRetrievalCandidateAttempt<List<ArticleSearchCandidate>>,
    val vector: ArticleRetrievalCandidateAttempt<ArticleVectorCandidateSearchResult>
)

data class ArticleRetrievalCandidateAttempt<T>(
    val value: T?,
    val exception: Exception?,
    val failureReason: String?,
    val elapsedMs: Long,
    val embeddingElapsedMs: Long,
    val vectorElapsedMs: Long
) {
    val failed: Boolean
        get() = failureReason != null
}
