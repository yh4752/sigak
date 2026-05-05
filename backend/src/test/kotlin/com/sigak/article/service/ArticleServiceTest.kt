package com.sigak.article.service

import kotlin.test.Test
import kotlin.test.assertEquals

class ArticleServiceTest {

    private val articleService = ArticleService()

    @Test
    fun getArticlesReturnsAllArticlesWhenQueryIsBlank() {
        val articles = articleService.getArticles("   ")

        assertEquals(5, articles.size)
    }

    @Test
    fun getArticlesFiltersByKeywordIgnoringCase() {
        val articles = articleService.getArticles("VECTOR")

        assertEquals(listOf(2L), articles.map { it.id })
    }

    @Test
    fun getArticlesReturnsEmptyListWhenKeywordDoesNotMatch() {
        val articles = articleService.getArticles("nonexistent")

        assertEquals(emptyList(), articles)
    }
}
