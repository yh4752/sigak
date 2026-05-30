package com.sigak.article.service

import com.sigak.SigakBackendApplication
import com.sigak.search.metrics.ArticleSearchMetricsRecorder
import com.sigak.search.service.ArticleKeywordSearchService
import com.sigak.support.PostgresIntegrationTest
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito

@SpringBootTest(classes = [SigakBackendApplication::class])
class ArticleServiceTest : PostgresIntegrationTest() {

    @Autowired
    private lateinit var articleService: ArticleService

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @MockBean
    private lateinit var articleKeywordSearchService: ArticleKeywordSearchService

    @Autowired
    private lateinit var articleSearchMetricsRecorder: ArticleSearchMetricsRecorder

    @BeforeEach
    fun resetArticleKeywordSearchService() {
        Mockito.reset(articleKeywordSearchService)
        articleSearchMetricsRecorder.reset()
        Mockito.doThrow(RuntimeException("keyword search unavailable in fallback tests"))
            .`when`(articleKeywordSearchService)
            .searchArticleIds(anyString())
    }

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
    fun getArticlesUsesElasticsearchIdsWhenKeywordSearchSucceeds() {
        Mockito.doReturn(listOf(4L, 1L))
            .`when`(articleKeywordSearchService)
            .searchArticleIds("graph")

        val articles = articleService.getArticles(" graph ")

        assertEquals(listOf(4L, 1L), articles.map { it.id })
        Mockito.verify(articleKeywordSearchService).searchArticleIds("graph")

        val summary = articleSearchMetricsRecorder.summarize()
        assertEquals(1, summary.totalSearchCount)
        assertEquals(1, summary.elasticsearchSearchCount)
        assertEquals(0, summary.fallbackSearchCount)
        assertEquals(false, summary.lastSearch?.fallback)
        assertEquals(5, summary.lastSearch?.queryLength)
        assertEquals(2, summary.lastSearch?.resultCount)
    }

    @Test
    fun getArticlesFallsBackToPostgresFilteringWhenKeywordSearchFails() {
        Mockito.doThrow(RuntimeException("elasticsearch down"))
            .`when`(articleKeywordSearchService)
            .searchArticleIds("VECTOR")

        val articles = articleService.getArticles("VECTOR")

        assertEquals(listOf(2L), articles.map { it.id })

        val summary = articleSearchMetricsRecorder.summarize()
        assertEquals(1, summary.totalSearchCount)
        assertEquals(0, summary.elasticsearchSearchCount)
        assertEquals(1, summary.fallbackSearchCount)
        assertEquals(true, summary.lastSearch?.fallback)
        assertEquals(6, summary.lastSearch?.queryLength)
        assertEquals(1, summary.lastSearch?.resultCount)
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
    fun getApiReadyArticlesByIdsReturnsArticlesInRequestedOrder() {
        val articles = articleService.getApiReadyArticlesByIds(listOf(4L, 1L, 4L, 999L))

        assertEquals(listOf(4L, 1L), articles.map { it.id })
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
