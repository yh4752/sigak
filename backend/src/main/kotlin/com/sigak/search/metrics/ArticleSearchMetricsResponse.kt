package com.sigak.search.metrics

data class ArticleSearchMetricsResponse(
    val totalSearchCount: Long,
    val elasticsearchSearchCount: Long,
    val fallbackSearchCount: Long,
    val fallbackRate: Double,
    val averageElapsedMs: Double,
    val p50ElapsedMs: Long,
    val p95ElapsedMs: Long,
    val lastSearch: ArticleSearchMetricSnapshotResponse?
)

data class ArticleSearchMetricSnapshotResponse(
    val queryLength: Int,
    val resultCount: Int,
    val fallback: Boolean,
    val elapsedMs: Long
)
