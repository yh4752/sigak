package com.sigak.article.service

import com.sigak.SigakBackendApplication
import com.sigak.search.hybrid.ArticlePublicSearchMode
import com.sigak.search.hybrid.ArticlePublicSearchResult
import com.sigak.search.hybrid.ArticlePublicSearchService
import com.sigak.search.metrics.ArticleSearchMetricsRecorder
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
    private lateinit var articlePublicSearchService: ArticlePublicSearchService

    @Autowired
    private lateinit var articleSearchMetricsRecorder: ArticleSearchMetricsRecorder

    @BeforeEach
    fun resetArticlePublicSearchService() {
        Mockito.reset(articlePublicSearchService)
        articleSearchMetricsRecorder.reset()
        Mockito.doReturn(postgresFallbackResult())
            .`when`(articlePublicSearchService)
            .search(anyString())
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
    fun getArticlesUsesHybridRankedIdsWhenPublicSearchSucceeds() {
        Mockito.doReturn(
            ArticlePublicSearchResult(
                articleIds = listOf(4L, 1L),
                mode = ArticlePublicSearchMode.HYBRID,
                keywordCandidateCount = 2,
                vectorCandidateCount = 2,
                fusedCandidateCount = 2,
                keywordFailed = false,
                vectorFailed = false,
                fallbackReason = null,
                keywordElapsedMs = 3,
                embeddingElapsedMs = 4,
                vectorElapsedMs = 5,
                fusionElapsedMs = 1,
                totalElapsedMs = 13
            )
        ).`when`(articlePublicSearchService).search("graph")

        val articles = articleService.getArticles(" graph ")

        assertEquals(listOf(4L, 1L), articles.map { it.id })
        Mockito.verify(articlePublicSearchService).search("graph")

        val summary = articleSearchMetricsRecorder.summarize()
        assertEquals(1, summary.totalSearchCount)
        assertEquals(1, summary.hybridSearchCount)
        assertEquals(0, summary.postgresFallbackSearchCount)
        assertEquals(ArticlePublicSearchMode.HYBRID, summary.lastSearch?.mode)
        assertEquals(5, summary.lastSearch?.queryLength)
        assertEquals(2, summary.lastSearch?.resultCount)
    }

    @Test
    fun getArticlesFallsBackToPostgresFilteringWhenProjectionSearchFails() {
        Mockito.doReturn(
            ArticlePublicSearchResult(
                articleIds = emptyList(),
                mode = ArticlePublicSearchMode.POSTGRES_FALLBACK,
                keywordCandidateCount = 0,
                vectorCandidateCount = 0,
                fusedCandidateCount = 0,
                keywordFailed = true,
                vectorFailed = true,
                fallbackReason = "KEYWORD_SEARCH_FAILED; QDRANT_SEARCH_FAILED",
                keywordElapsedMs = 2,
                embeddingElapsedMs = 0,
                vectorElapsedMs = 2,
                fusionElapsedMs = 0,
                totalElapsedMs = 4
            )
        ).`when`(articlePublicSearchService).search("VECTOR")

        val articles = articleService.getArticles("VECTOR")

        assertEquals(listOf(2L), articles.map { it.id })

        val summary = articleSearchMetricsRecorder.summarize()
        assertEquals(1, summary.totalSearchCount)
        assertEquals(0, summary.hybridSearchCount)
        assertEquals(1, summary.postgresFallbackSearchCount)
        assertEquals(ArticlePublicSearchMode.POSTGRES_FALLBACK, summary.lastSearch?.mode)
        assertEquals("KEYWORD_SEARCH_FAILED; QDRANT_SEARCH_FAILED", summary.lastSearch?.fallbackReason)
        assertEquals(6, summary.lastSearch?.queryLength)
        assertEquals(1, summary.lastSearch?.resultCount)
    }

    @Test
    fun getArticlesOmitsStaleHybridCandidatesAfterPostgresReload() {
        Mockito.doReturn(
            ArticlePublicSearchResult(
                articleIds = listOf(4L, 999L, 1L),
                mode = ArticlePublicSearchMode.HYBRID,
                keywordCandidateCount = 2,
                vectorCandidateCount = 2,
                fusedCandidateCount = 3,
                keywordFailed = false,
                vectorFailed = false,
                fallbackReason = null,
                keywordElapsedMs = 3,
                embeddingElapsedMs = 4,
                vectorElapsedMs = 5,
                fusionElapsedMs = 1,
                totalElapsedMs = 13
            )
        ).`when`(articlePublicSearchService).search("graph")

        val articles = articleService.getArticles("graph")

        assertEquals(listOf(4L, 1L), articles.map { it.id })

        val summary = articleSearchMetricsRecorder.summarize()
        assertEquals(1, summary.lastSearch?.staleCandidateCount)
        assertEquals(ArticlePublicSearchMode.HYBRID, summary.lastSearch?.mode)
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

    private fun postgresFallbackResult(): ArticlePublicSearchResult =
        ArticlePublicSearchResult(
            articleIds = emptyList(),
            mode = ArticlePublicSearchMode.POSTGRES_FALLBACK,
            keywordCandidateCount = 0,
            vectorCandidateCount = 0,
            fusedCandidateCount = 0,
            keywordFailed = true,
            vectorFailed = true,
            fallbackReason = "KEYWORD_SEARCH_FAILED; VECTOR_SEARCH_FAILED",
            keywordElapsedMs = 0,
            embeddingElapsedMs = 0,
            vectorElapsedMs = 0,
            fusionElapsedMs = 0,
            totalElapsedMs = 0
        )
}
