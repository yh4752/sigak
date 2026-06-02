package com.sigak.search.evaluation.catalog

import com.sigak.article.dto.ArticleResponse
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SearchCatalogFactoryTest {

    private val factory = SearchCatalogFactory()

    @Test
    fun buildMapsArticleResponsesToLabelingCatalogSchema() {
        val catalog = factory.build(
            articles = listOf(
                article(
                    id = 7,
                    title = "Graph RAG Evaluation",
                    source = "Research Blog",
                    primaryCategory = "CS_RESEARCH",
                    topics = listOf("Graph RAG", "retrieval quality")
                )
            ),
            catalogId = "api-ready-test",
            generatedAt = Instant.parse("2026-06-02T00:00:00Z"),
            limit = 50
        )

        assertEquals(1, catalog.version)
        assertEquals("api-ready-test", catalog.catalogId)
        assertEquals("2026-06-02T00:00:00Z", catalog.generatedAt)
        assertEquals("postgres-api-ready", catalog.source)
        assertEquals(1, catalog.articles.size)

        val article = catalog.articles.single()
        assertEquals(7L, article.id)
        assertEquals("Graph RAG Evaluation", article.title)
        assertEquals("CS_RESEARCH", article.category)
        assertEquals(listOf("Graph RAG", "retrieval quality"), article.topics)
        assertEquals("summary for 7", article.summaryKo)
        assertEquals("why it matters for 7", article.whyItMattersKo)
        assertEquals("2026-05-07T00:00:00Z", article.publishedAt)
        assertEquals("Research Blog", article.source)
        assertEquals("https://example.com/articles/7", article.url)
    }

    @Test
    fun buildAppliesLimitBeforeMappingArticles() {
        val catalog = factory.build(
            articles = listOf(article(1), article(2), article(3)),
            catalogId = "api-ready-test",
            generatedAt = Instant.parse("2026-06-02T00:00:00Z"),
            limit = 2
        )

        assertEquals(listOf(1L, 2L), catalog.articles.map { it.id })
    }

    @Test
    fun buildRejectsEmptyApiReadyArticles() {
        val exception = assertFailsWith<IllegalStateException> {
            factory.build(
                articles = emptyList(),
                catalogId = "api-ready-test",
                generatedAt = Instant.parse("2026-06-02T00:00:00Z"),
                limit = 50
            )
        }

        assertEquals("No API-ready articles are available for search catalog export.", exception.message)
    }

    private fun article(
        id: Long,
        title: String = "Article $id",
        source: String = "Source $id",
        primaryCategory: String = "AI",
        topics: List<String> = listOf("topic-$id")
    ): ArticleResponse =
        ArticleResponse(
            id = id,
            title = title,
            source = source,
            url = "https://example.com/articles/$id",
            publishedAt = "2026-05-07T00:00:00Z",
            eventType = "NEWS",
            primaryCategory = primaryCategory,
            topics = topics,
            summary = "summary for $id",
            whyItMatters = "why it matters for $id",
            importanceScore = 70,
            relatedArticleIds = emptyList()
        )
}
