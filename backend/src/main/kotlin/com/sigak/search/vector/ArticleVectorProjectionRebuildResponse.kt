package com.sigak.search.vector

data class ArticleVectorProjectionRebuildResponse(
    val status: String,
    val collectionName: String,
    val indexedCount: Int,
    val embeddingProvider: String?,
    val embeddingModelName: String?,
    val embeddingDimension: Int?,
    val durationMs: Long,
    val failedReason: String?
)
