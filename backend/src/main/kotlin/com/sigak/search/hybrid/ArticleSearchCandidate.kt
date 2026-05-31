package com.sigak.search.hybrid

data class ArticleSearchCandidate(
    val articleId: Long,
    val rank: Int,
    val score: Double? = null
)
