package com.sigak.search.vector

import com.sigak.ai.embedding.EmbeddingClient
import com.sigak.ai.embedding.EmbeddingResponse
import com.sigak.article.dto.ArticleResponse
import com.sigak.article.service.ArticleService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class ArticleVectorProjectionRebuildServiceTest {

    private val articleService = mock(ArticleService::class.java)
    private val embeddingClient = RecordingEmbeddingClient()
    private val articleVectorProjectionIndexer = RecordingArticleVectorProjectionIndexer()
    private val rebuildService = ArticleVectorProjectionRebuildService(
        articleService = articleService,
        articleVectorTextBuilder = ArticleVectorTextBuilder(),
        embeddingClient = embeddingClient,
        articleVectorProjectionIndexer = articleVectorProjectionIndexer
    )

    @Test
    fun rebuildEmbedsArticlesAndUpsertsVectorsIntoQdrant() {
        `when`(articleService.getArticles(null))
            .thenReturn(listOf(article(id = 3, title = "Critical Package Registry Attack Targets AI Toolchains")))
        embeddingClient.responses = ArrayDeque(
            listOf(
                EmbeddingResponse(
                    provider = "local",
                    modelName = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
                    dimension = 3,
                    embedding = listOf(0.1, 0.2, 0.3)
                )
            )
        )

        val response = rebuildService.rebuild()

        assertEquals("completed", response.status)
        assertEquals("sigak-article-vectors-v1", response.collectionName)
        assertEquals(1, response.indexedCount)
        assertEquals("local", response.embeddingProvider)
        assertEquals("sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2", response.embeddingModelName)
        assertEquals(3, response.embeddingDimension)
        assertNull(response.failedReason)
        assertEquals(3, articleVectorProjectionIndexer.recreatedDimensions.single())

        val document = articleVectorProjectionIndexer.documents.single()
        assertEquals(3, document.id)
        assertEquals(listOf(0.1, 0.2, 0.3), document.vector)
        assertEquals(3, document.payload.articleId)
        assertEquals("Critical Package Registry Attack Targets AI Toolchains", document.payload.title)
        assertEquals("Security Advisory Board", document.payload.source)
        assertEquals("https://example.com/articles/3", document.payload.url)
        assertEquals("2026-05-03T15:45:00Z", document.payload.publishedAt)
        assertEquals("SECURITY", document.payload.eventType)
        assertEquals("SECURITY", document.payload.primaryCategory)
        assertEquals(listOf("supply chain security", "AI tooling"), document.payload.topics)
        assertEquals(93, document.payload.importanceScore)
        assertEquals(listOf(1L, 5L), document.payload.relatedArticleIds)
        assertEquals("local", document.payload.embeddingProvider)
        assertEquals("sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2", document.payload.embeddingModelName)
        assertEquals(3, document.payload.embeddingDimension)
    }

    @Test
    fun rebuildFailsWhenEmbeddingMetadataChangesWithinSameCollection() {
        `when`(articleService.getArticles(null))
            .thenReturn(
                listOf(
                    article(id = 3, title = "First AI Infrastructure Report"),
                    article(id = 4, title = "Second AI Infrastructure Report")
                )
            )
        embeddingClient.responses = ArrayDeque(
            listOf(
                EmbeddingResponse(
                    provider = "local",
                    modelName = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
                    dimension = 3,
                    embedding = listOf(0.1, 0.2, 0.3)
                ),
                EmbeddingResponse(
                    provider = "local",
                    modelName = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
                    dimension = 4,
                    embedding = listOf(0.1, 0.2, 0.3, 0.4)
                )
            )
        )

        val response = rebuildService.rebuild()

        assertEquals("failed", response.status)
        assertEquals(0, response.indexedCount)
        assertTrue(response.failedReason.orEmpty().contains("Embedding metadata mismatch"))
        assertEquals(emptyList(), articleVectorProjectionIndexer.recreatedDimensions)
        assertEquals(0, articleVectorProjectionIndexer.deleteCount)
        assertEquals(emptyList(), articleVectorProjectionIndexer.documents)
    }

    @Test
    fun rebuildFailsBeforeRecreatingCollectionWhenEmbeddingVectorSizeDoesNotMatchDimension() {
        `when`(articleService.getArticles(null))
            .thenReturn(listOf(article(id = 3, title = "Critical Package Registry Attack Targets AI Toolchains")))
        embeddingClient.responses = ArrayDeque(
            listOf(
                EmbeddingResponse(
                    provider = "local",
                    modelName = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
                    dimension = 3,
                    embedding = listOf(0.1, 0.2)
                )
            )
        )

        val response = rebuildService.rebuild()

        assertEquals("failed", response.status)
        assertEquals(0, response.indexedCount)
        assertTrue(response.failedReason.orEmpty().contains("Embedding vector size mismatch"))
        assertEquals(emptyList(), articleVectorProjectionIndexer.recreatedDimensions)
        assertEquals(0, articleVectorProjectionIndexer.deleteCount)
        assertEquals(emptyList(), articleVectorProjectionIndexer.documents)
    }

    @Test
    fun rebuildDeletesCollectionWhenThereAreNoApiReadyArticles() {
        `when`(articleService.getArticles(null))
            .thenReturn(emptyList())

        val response = rebuildService.rebuild()

        assertEquals("completed", response.status)
        assertEquals("sigak-article-vectors-v1", response.collectionName)
        assertEquals(0, response.indexedCount)
        assertNull(response.embeddingProvider)
        assertNull(response.embeddingModelName)
        assertNull(response.embeddingDimension)
        assertNull(response.failedReason)
        assertEquals(1, articleVectorProjectionIndexer.deleteCount)
        assertEquals(emptyList(), articleVectorProjectionIndexer.recreatedDimensions)
        assertEquals(emptyList(), articleVectorProjectionIndexer.documents)
    }

    private fun article(id: Long, title: String): ArticleResponse =
        ArticleResponse(
            id = id,
            title = title,
            source = "Security Advisory Board",
            url = "https://example.com/articles/$id",
            publishedAt = "2026-05-03T15:45:00Z",
            eventType = "SECURITY",
            primaryCategory = "SECURITY",
            topics = listOf("supply chain security", "AI tooling"),
            summary = "A coordinated package registry attack targeted developer environments.",
            whyItMatters = "AI development stacks combine packages, credentials, and automation.",
            importanceScore = 93,
            relatedArticleIds = listOf(1, 5)
        )

    private class RecordingEmbeddingClient : EmbeddingClient {
        var responses: ArrayDeque<EmbeddingResponse> = ArrayDeque()

        override fun embedText(text: String): EmbeddingResponse =
            responses.removeFirst()
    }

    private class RecordingArticleVectorProjectionIndexer : ArticleVectorProjectionIndexer {
        var deleteCount = 0
        var recreatedDimensions: List<Int> = emptyList()
        var documents: List<ArticleVectorDocument> = emptyList()

        override fun collectionName(): String = "sigak-article-vectors-v1"

        override fun deleteCollectionIfExists() {
            deleteCount += 1
        }

        override fun recreateCollection(dimension: Int) {
            recreatedDimensions = recreatedDimensions + dimension
        }

        override fun upsertAll(documents: List<ArticleVectorDocument>) {
            this.documents = documents
        }

        override fun search(vector: List<Double>, limit: Int): List<ArticleVectorSearchHit> =
            emptyList()
    }
}
