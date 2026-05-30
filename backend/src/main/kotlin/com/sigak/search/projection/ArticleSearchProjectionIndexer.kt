package com.sigak.search.projection

interface ArticleSearchProjectionIndexer {
    fun indexName(): String

    fun replaceAll(documents: List<ArticleSearchProjectionDocument>)
}
