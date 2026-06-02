package com.sigak.search.evaluation.retrieval

import java.time.Instant

enum class ArticleRetrievalEvaluationSystem {
    KEYWORD,
    VECTOR,
    HYBRID
}

enum class ArticleRetrievalRunStatus {
    COMPLETED,
    FAILED
}

data class ArticleRetrievalRunRequest(
    val queries: List<String> = emptyList(),
    val systems: List<ArticleRetrievalEvaluationSystem>? = null,
    val limit: Int? = null
)

data class ArticleRetrievalRunResponse(
    val generatedAt: Instant,
    val limit: Int,
    val runs: List<ArticleRetrievalRunItemResponse>
)

data class ArticleRetrievalRunItemResponse(
    val query: String,
    val system: ArticleRetrievalEvaluationSystem,
    val status: ArticleRetrievalRunStatus,
    val rankedArticleIds: List<Long>,
    val candidateCount: Int,
    val staleCandidateCount: Int,
    val failureReason: String?,
    val degraded: Boolean,
    val resolvedMode: String?,
    val timings: ArticleRetrievalRunTimingsResponse,
    val metadata: ArticleRetrievalRunMetadataResponse?
)

data class ArticleRetrievalRunTimingsResponse(
    val keywordElapsedMs: Long,
    val embeddingElapsedMs: Long,
    val vectorElapsedMs: Long,
    val fusionElapsedMs: Long,
    val articleReloadElapsedMs: Long,
    val totalElapsedMs: Long
)

data class ArticleRetrievalRunMetadataResponse(
    val embeddingProvider: String?,
    val embeddingModelName: String?,
    val embeddingDimension: Int?
)
