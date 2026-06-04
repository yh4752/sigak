package com.sigak.search.graph

data class ArticleGraphProjectionRebuildResponse(
    val status: String,
    val rebuiltAt: String?,
    val articleNodeCount: Int,
    val topicNodeCount: Int,
    val hasTopicRelationshipCount: Int,
    val relatedToRelationshipCount: Int,
    val durationMs: Long,
    val failedReason: String?
)
