package com.sigak.search.vector

import com.sigak.article.dto.ArticleResponse

data class ArticleVectorSearchResponse(
    val query: String,
    val collectionName: String,
    val embeddingProvider: String,
    val embeddingModelName: String,
    val embeddingDimension: Int,
    val results: List<ArticleVectorSearchResult>,
    val timings: ArticleVectorSearchTimings
)

data class ArticleVectorSearchResult(
    val article: ArticleResponse,
    val score: Double
)

data class ArticleVectorSearchTimings(
    val embeddingElapsedMs: Long,
    val qdrantElapsedMs: Long,
    val articleLoadElapsedMs: Long,
    val totalElapsedMs: Long
)
