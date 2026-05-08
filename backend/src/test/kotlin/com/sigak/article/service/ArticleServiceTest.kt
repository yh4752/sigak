package com.sigak.article.service

import com.sigak.SigakBackendApplication
import com.sigak.support.PostgresIntegrationTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

@SpringBootTest(classes = [SigakBackendApplication::class])
class ArticleServiceTest : PostgresIntegrationTest() {

    @Autowired
    private lateinit var articleService: ArticleService

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

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
    fun getArticlesExcludesArticlesThatAreNotReadyForPublicApi() {
        insertArticle(id = 9001, externalId = "draft-without-enrichment", title = "Draft Article Without Enrichment", status = "DISCOVERED")

        try {
            val articles = articleService.getArticles("draft")

            assertEquals(emptyList(), articles)
        } finally {
            jdbcTemplate.update("delete from articles where id = 9001")
        }
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

    @Test
    fun getArticleReturnsNullWhenArticleIsNotReadyForPublicApi() {
        insertArticle(
            id = 9002,
            externalId = "published-without-current-enrichment",
            title = "Published Article Without Current Enrichment",
            status = "PUBLISHED"
        )

        try {
            val article = articleService.getArticle(9002L)

            assertEquals(null, article)
        } finally {
            jdbcTemplate.update("delete from articles where id = 9002")
        }
    }

    private fun insertArticle(id: Long, externalId: String, title: String, status: String) {
        jdbcTemplate.update(
            """
            insert into articles (
                id, source_id, external_id, title, url, canonical_url, published_at,
                event_type, primary_category, importance_score, processing_status,
                created_at, updated_at
            ) values (
                ?, 1, ?, ?,
                ?, ?,
                '2026-05-08T00:00:00Z', 'NEWS', 'AI', 50, ?,
                '2026-05-08T00:00:00Z', '2026-05-08T00:00:00Z'
            )
            """.trimIndent(),
            id,
            externalId,
            title,
            "https://example.com/articles/$externalId",
            "https://example.com/articles/$externalId",
            status
        )
    }
}
