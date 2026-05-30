package com.sigak.search.vector

data class ArticleVectorSearchMetricObservation(
    val queryLength: Int,
    val resultCount: Int,
    val embeddingElapsedMs: Long,
    val qdrantElapsedMs: Long,
    val articleLoadElapsedMs: Long,
    val totalElapsedMs: Long
)
