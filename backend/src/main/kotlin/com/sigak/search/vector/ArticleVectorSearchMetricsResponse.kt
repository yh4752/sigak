package com.sigak.search.vector

data class ArticleVectorSearchMetricsResponse(
    val totalSearchCount: Long,
    val averageElapsedMs: Double,
    val p50ElapsedMs: Long,
    val p95ElapsedMs: Long,
    val lastSearch: ArticleVectorSearchMetricSnapshotResponse?
)

data class ArticleVectorSearchMetricSnapshotResponse(
    val queryLength: Int,
    val resultCount: Int,
    val embeddingElapsedMs: Long,
    val qdrantElapsedMs: Long,
    val articleLoadElapsedMs: Long,
    val totalElapsedMs: Long
)
