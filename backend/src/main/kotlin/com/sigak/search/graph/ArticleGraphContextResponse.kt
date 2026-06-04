package com.sigak.search.graph

data class ArticleGraphContextResponse(
    val articleId: Long,
    val topics: List<ArticleGraphTopicContextResponse>,
    val relatedArticles: List<ArticleGraphRelatedArticleResponse>,
    val timings: ArticleGraphContextTimingsResponse
)

data class ArticleGraphTopicContextResponse(
    val name: String,
    val displayName: String,
    val relatedArticleIds: List<Long>
)

data class ArticleGraphRelatedArticleResponse(
    val articleId: Long,
    val title: String,
    val relationType: String,
    val reason: String?,
    val sharedTopics: List<String>
)

data class ArticleGraphContextTimingsResponse(
    val neo4jElapsedMs: Long,
    val totalElapsedMs: Long
)
