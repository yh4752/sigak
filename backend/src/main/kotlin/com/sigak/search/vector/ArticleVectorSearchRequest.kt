package com.sigak.search.vector

data class ArticleVectorSearchRequest(
    val query: String,
    val limit: Int? = null
)
