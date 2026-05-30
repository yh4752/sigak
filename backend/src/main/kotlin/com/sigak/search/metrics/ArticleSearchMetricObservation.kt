package com.sigak.search.metrics

data class ArticleSearchMetricObservation(
    val queryLength: Int,
    val resultCount: Int,
    val fallback: Boolean,
    val elapsedMs: Long
)
