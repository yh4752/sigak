package com.sigak.article.service

import com.sigak.SigakBackendApplication
import com.sigak.support.PostgresIntegrationTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(classes = [SigakBackendApplication::class])
class ArticleServiceTest : PostgresIntegrationTest() {

    @Autowired
    private lateinit var articleService: ArticleService

    @Test
    fun getArticlesReturnsAllArticlesWhenQueryIsBlank() {
        val articles = articleService.getArticles("   ")

        assertEquals(5, articles.size)
        assertEquals(listOf(3L, 1L, 4L, 2L, 5L), articles.map { it.id })
    }

    @Test
    fun getArticlesFiltersByTitleIgnoringCase() {
        val articles = articleService.getArticles("VECTOR")

        assertEquals(listOf(2L), articles.map { it.id })
    }

    @Test
    fun getArticlesFiltersBySummaryIgnoringCase() {
        val articles = articleService.getArticles("multi-step")

        assertEquals(listOf(1L), articles.map { it.id })
    }

    @Test
    fun getArticlesFiltersByPrimaryCategoryIgnoringCase() {
        val articles = articleService.getArticles("cs_research")

        assertEquals(listOf(4L), articles.map { it.id })
    }

    @Test
    fun getArticlesFiltersByTopicIgnoringCase() {
        val articles = articleService.getArticles("supply chain")

        assertEquals(listOf(3L), articles.map { it.id })
    }

    @Test
    fun getArticlesReturnsEmptyListWhenKeywordDoesNotMatch() {
        val articles = articleService.getArticles("nonexistent")

        assertEquals(emptyList(), articles)
    }

    @Test
    fun getArticleReturnsArticleById() {
        val article = articleService.getArticle(3L)

        assertNotNull(article)
        assertEquals("Critical Package Registry Attack Targets AI Toolchains", article.title)
        assertEquals("SECURITY", article.eventType)
        assertEquals("SECURITY", article.primaryCategory)
        assertEquals(listOf("supply chain security", "package registry", "AI tooling"), article.topics)
        assertEquals(listOf(1L, 4L), article.relatedArticleIds)
    }

    @Test
    fun getArticleReturnsNullForUnknownId() {
        val article = articleService.getArticle(999L)

        assertEquals(null, article)
    }
}
