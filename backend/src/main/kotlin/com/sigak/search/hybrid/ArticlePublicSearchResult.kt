package com.sigak.search.hybrid

enum class ArticlePublicSearchMode {
    HYBRID,
    KEYWORD_ONLY,
    VECTOR_ONLY,
    POSTGRES_FALLBACK
}

data class ArticlePublicSearchResult(
    val articleIds: List<Long>,
    val mode: ArticlePublicSearchMode,
    val keywordCandidateCount: Int,
    val vectorCandidateCount: Int,
    val fusedCandidateCount: Int,
    val keywordFailed: Boolean,
    val vectorFailed: Boolean,
    val fallbackReason: String?,
    val keywordElapsedMs: Long,
    val embeddingElapsedMs: Long,
    val vectorElapsedMs: Long,
    val fusionElapsedMs: Long,
    val totalElapsedMs: Long
)
