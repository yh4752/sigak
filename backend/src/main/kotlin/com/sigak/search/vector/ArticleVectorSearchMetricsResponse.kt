package com.sigak.search.vector

data class ArticleVectorSearchMetricsResponse(
    val totalSearchCount: Long,
    val averageTotalElapsedMs: Double,
    val p50TotalElapsedMs: Long,
    val p95TotalElapsedMs: Long,
    val averageEmbeddingElapsedMs: Double,
    val averageQdrantElapsedMs: Double,
    val averageArticleLoadElapsedMs: Double,
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
