package com.sigak.search.projection

data class ArticleSearchProjectionRebuildResponse(
    val status: String,
    val indexName: String,
    val indexedCount: Int,
    val durationMs: Long,
    val failedReason: String?
)
