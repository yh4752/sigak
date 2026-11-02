package com.sigak.search.projection

import com.sigak.article.dto.ArticleResponse
import com.sigak.article.service.ArticleService
import com.sigak.article.service.ArticleSearchContent
import com.sigak.support.thenReturnSearchArticles
import kotlin.test.Test
import kotlin.test.assertEquals
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class ArticleSearchProjectionRebuildServiceTest {

    private val articleService = mock(ArticleService::class.java)
    private val articleSearchProjectionIndexer = RecordingArticleSearchProjectionIndexer()
    private val rebuildService = ArticleSearchProjectionRebuildService(
        articleService = articleService,
        articleSearchProjectionIndexer = articleSearchProjectionIndexer
    )

    @Test
    fun rebuildIndexesApiReadyArticlesIntoSearchProjection() {
        `when`(articleService.getSearchArticles())
            .thenReturnSearchArticles(
                listOf(
                    ArticleResponse(
                        id = 3,
                        title = "Critical Package Registry Attack Targets AI Toolchains",
                        source = "Security Advisory Board",
                        url = "https://example.com/articles/ai-toolchain-package-attack",
                        publishedAt = "2026-05-03T15:45:00Z",
                        eventType = "SECURITY",
                        primaryCategory = "SECURITY",
                        topics = listOf("supply chain security", "AI tooling"),
                        summary = "A coordinated package registry attack targeted developer environments.",
                        whyItMatters = "AI development stacks combine packages, credentials, and automation.",
                        importanceScore = 93,
                        relatedArticleIds = listOf(1, 5)
                    )
                )
            )
        val response = rebuildService.rebuild()

        assertEquals("completed", response.status)
        assertEquals("sigak-articles-v1", response.indexName)
        assertEquals(1, response.indexedCount)
        assertEquals(null, response.failedReason)

        val indexedDocument = articleSearchProjectionIndexer.documents.first()
        assertEquals(3, indexedDocument.id)
        assertEquals("Critical Package Registry Attack Targets AI Toolchains", indexedDocument.title)
        assertEquals(listOf("supply chain security", "AI tooling"), indexedDocument.topics)
        assertEquals(listOf(1L, 5L), indexedDocument.relatedArticleIds)
    }

    @Test
    fun `keyword projection uses internal source text when public excerpt is empty`() {
        val metadata = ArticleResponse(
            id = 99,
            title = "Source title",
            source = "Source",
            url = "https://example.test/a",
            publishedAt = "2026-11-02T00:00:00Z",
            eventType = "NEWS",
            primaryCategory = "BACKEND",
            topics = emptyList(),
            summary = null,
            whyItMatters = null,
            importanceScore = 50,
            relatedArticleIds = emptyList(),
            sourceExcerpt = ""
        )
        `when`(articleService.getSearchArticles())
            .thenReturn(listOf(ArticleSearchContent(metadata, "Preserved internal source evidence")))

        assertEquals("completed", rebuildService.rebuild().status)
        assertEquals("Preserved internal source evidence", articleSearchProjectionIndexer.documents.single().summary)
        assertEquals("", metadata.sourceExcerpt)
    }

    private class RecordingArticleSearchProjectionIndexer : ArticleSearchProjectionIndexer {
        var documents: List<ArticleSearchProjectionDocument> = emptyList()

        override fun indexName(): String = "sigak-articles-v1"

        override fun replaceAll(documents: List<ArticleSearchProjectionDocument>) {
            this.documents = documents
        }
    }
}
