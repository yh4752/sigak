package com.sigak.search.service

interface ArticleKeywordSearchService {
    fun searchArticleIds(query: String): List<Long>
}
