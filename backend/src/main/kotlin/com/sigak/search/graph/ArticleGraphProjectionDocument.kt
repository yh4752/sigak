package com.sigak.search.graph

data class ArticleGraphProjectionDocument(
    val articleId: Long,
    val title: String,
    val source: String,
    val url: String,
    val publishedAt: String,
    val eventType: String,
    val primaryCategory: String,
    val importanceScore: Int,
    val topics: List<ArticleGraphTopicDocument>,
    val outgoingRelations: List<ArticleGraphRelationDocument>
)

data class ArticleGraphTopicDocument(
    val name: String,
    val displayName: String,
    val position: Int
)

data class ArticleGraphRelationDocument(
    val sourceArticleId: Long,
    val targetArticleId: Long,
    val relationType: String,
    val reason: String?
)
