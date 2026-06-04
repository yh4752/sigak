package com.sigak.search.graph

interface ArticleGraphProjectionIndexer {
    fun rebuild(documents: List<ArticleGraphProjectionDocument>): ArticleGraphProjectionIndexResult

    fun findContext(articleId: Long): ArticleGraphContextProjection?
}

data class ArticleGraphProjectionIndexResult(
    val articleNodeCount: Int,
    val topicNodeCount: Int,
    val hasTopicRelationshipCount: Int,
    val relatedToRelationshipCount: Int
)

data class ArticleGraphContextProjection(
    val articleId: Long,
    val topics: List<ArticleGraphTopicContextProjection>,
    val relatedArticles: List<ArticleGraphRelatedArticleProjection>
)

data class ArticleGraphTopicContextProjection(
    val name: String,
    val displayName: String,
    val relatedArticleIds: List<Long>
)

data class ArticleGraphRelatedArticleProjection(
    val articleId: Long,
    val title: String,
    val relationType: String,
    val reason: String?,
    val sharedTopics: List<String>
)
