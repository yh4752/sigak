package com.sigak.search.projection

data class ArticleSearchProjectionDocument(
    val id: Long,
    val title: String,
    val source: String,
    val url: String,
    val publishedAt: String,
    val eventType: String,
    val primaryCategory: String,
    val topics: List<String>,
    val summary: String,
    val whyItMatters: String,
    val importanceScore: Int,
    val relatedArticleIds: List<Long>
)
