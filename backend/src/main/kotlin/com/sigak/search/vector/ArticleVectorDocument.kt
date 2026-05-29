package com.sigak.search.vector

data class ArticleVectorDocument(
    val id: Long,
    val vector: List<Double>,
    val payload: ArticleVectorPayload
)

data class ArticleVectorPayload(
    val articleId: Long,
    val title: String,
    val source: String,
    val url: String,
    val publishedAt: String,
    val eventType: String,
    val primaryCategory: String,
    val topics: List<String>,
    val importanceScore: Int,
    val relatedArticleIds: List<Long>,
    val embeddingProvider: String,
    val embeddingModelName: String,
    val embeddingDimension: Int
)

data class ArticleVectorSearchHit(
    val articleId: Long,
    val score: Double
)
