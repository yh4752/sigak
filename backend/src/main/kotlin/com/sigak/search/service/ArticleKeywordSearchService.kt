package com.sigak.search.service

interface ArticleKeywordSearchService {
    fun searchArticleIds(query: String): List<Long> =
        searchArticleIds(query = query, limit = 20)

    fun searchArticleIds(query: String, limit: Int): List<Long>
}
