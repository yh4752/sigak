package com.sigak.article.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Public graph context for an article detail page.")
data class ArticlePublicGraphContextResponse(
    @field:Schema(description = "Article ID this graph context belongs to.", example = "4")
    val articleId: Long,
    @field:Schema(description = "Display-safe relation reasons keyed by related article ID.")
    val relatedArticleReasons: List<ArticlePublicRelatedArticleReasonResponse>,
    @field:Schema(description = "Display-safe topic context from the graph projection.")
    val topics: List<ArticlePublicGraphTopicResponse>
)

data class ArticlePublicRelatedArticleReasonResponse(
    @field:Schema(description = "Related article ID.", example = "1")
    val articleId: Long,
    @field:Schema(description = "Stored relation reason, when available.")
    val reason: String?,
    @field:Schema(description = "Topic names shared by the current and related article.")
    val sharedTopics: List<String>
)

data class ArticlePublicGraphTopicResponse(
    @field:Schema(description = "Normalized topic name.", example = "graph rag")
    val name: String,
    @field:Schema(description = "Display topic name.", example = "Graph RAG")
    val displayName: String,
    @field:Schema(description = "Other article IDs connected through this topic.")
    val relatedArticleIds: List<Long>
)
