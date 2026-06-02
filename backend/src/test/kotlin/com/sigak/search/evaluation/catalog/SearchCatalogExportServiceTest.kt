package com.sigak.search.evaluation.catalog

import com.sigak.article.dto.ArticleResponse
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchCatalogExportServiceTest {

    @Test
    fun exportBuildsCatalogFromApiReadyArticleReader() {
        val service = SearchCatalogExportService(
            articleReader = StaticArticleReader(listOf(article(1), article(2))),
            catalogFactory = SearchCatalogFactory()
        )

        val catalog = service.export(
            SearchCatalogExportCommand(
                output = Path.of("unused.json"),
                limit = 1,
                catalogId = "api-ready-test"
            )
        )

        assertEquals("api-ready-test", catalog.catalogId)
        assertEquals(listOf(1L), catalog.articles.map { article -> article.id })
        assertTrue(catalog.generatedAt.isNotBlank())
    }

    @Test
    fun exportUsesDateBasedCatalogIdWhenCatalogIdIsMissing() {
        val service = SearchCatalogExportService(
            articleReader = StaticArticleReader(listOf(article(1))),
            catalogFactory = SearchCatalogFactory()
        )

        val catalog = service.export(
            SearchCatalogExportCommand(
                output = Path.of("unused.json")
            )
        )

        assertTrue(Regex("api-ready-\\d{4}-\\d{2}-\\d{2}").matches(catalog.catalogId))
    }

    private class StaticArticleReader(
        private val articles: List<ArticleResponse>
    ) : SearchCatalogArticleReader {
        override fun readApiReadyArticles(): List<ArticleResponse> = articles
    }

    private fun article(id: Long): ArticleResponse =
        ArticleResponse(
            id = id,
            title = "Article $id",
            source = "Source $id",
            url = "https://example.com/articles/$id",
            publishedAt = "2026-05-07T00:00:00Z",
            eventType = "NEWS",
            primaryCategory = "AI",
            topics = listOf("topic-$id"),
            summary = "summary for $id",
            whyItMatters = "why it matters for $id",
            importanceScore = 70,
            relatedArticleIds = emptyList()
        )
}
