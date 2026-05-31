package com.sigak.search.metrics

import com.sigak.search.hybrid.ArticlePublicSearchMode

data class ArticleSearchMetricsResponse(
    val totalSearchCount: Long,
    val hybridSearchCount: Long,
    val keywordOnlySearchCount: Long,
    val vectorOnlySearchCount: Long,
    val postgresFallbackSearchCount: Long,
    val fallbackRate: Double,
    val averageTotalElapsedMs: Double,
    val p50TotalElapsedMs: Long,
    val p95TotalElapsedMs: Long,
    val lastSearch: ArticleSearchMetricSnapshotResponse?
)

data class ArticleSearchMetricSnapshotResponse(
    val queryLength: Int,
    val resultCount: Int,
    val mode: ArticlePublicSearchMode,
    val keywordCandidateCount: Int,
    val vectorCandidateCount: Int,
    val fusedCandidateCount: Int,
    val staleCandidateCount: Int,
    val keywordFailed: Boolean,
    val vectorFailed: Boolean,
    val fallbackReason: String?,
    val keywordElapsedMs: Long,
    val embeddingElapsedMs: Long,
    val vectorElapsedMs: Long,
    val fusionElapsedMs: Long,
    val articleReloadElapsedMs: Long,
    val totalElapsedMs: Long
)
