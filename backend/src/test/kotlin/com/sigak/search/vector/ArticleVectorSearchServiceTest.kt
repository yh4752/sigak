package com.sigak.search.vector

import com.sigak.ai.embedding.EmbeddingClient
import com.sigak.ai.embedding.EmbeddingResponse
import com.sigak.article.dto.ArticleResponse
import com.sigak.article.service.ArticleService
import com.sigak.search.config.SearchInfrastructureProperties
import com.sigak.search.hybrid.ArticleVectorCandidateSearchService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verifyNoInteractions

class ArticleVectorSearchServiceTest {

    private val articleService = mock(ArticleService::class.java)
    private val embeddingClient = RecordingEmbeddingClient()
    private val articleVectorProjectionIndexer = RecordingArticleVectorProjectionIndexer()
    private val articleVectorCandidateSearchService = ArticleVectorCandidateSearchService(
        embeddingClient = embeddingClient,
        articleVectorProjectionIndexer = articleVectorProjectionIndexer
    )
    private val metricsRecorder = ArticleVectorSearchMetricsRecorder()
    private val properties = SearchInfrastructureProperties(
        qdrant = SearchInfrastructureProperties.Qdrant(
            articleCollectionName = "sigak-article-vectors-test",
            defaultLimit = 10,
            maxLimit = 50
        )
    )
    private val service = ArticleVectorSearchService(
        articleVectorCandidateSearchService = articleVectorCandidateSearchService,
        articleService = articleService,
        properties = properties,
        metricsRecorder = metricsRecorder
    )

    @Test
    fun searchesQdrantAndReloadsArticlesInVectorResultOrder() {
        embeddingClient.response = EmbeddingResponse(
            provider = "local",
            modelName = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
            dimension = 3,
            embedding = listOf(0.1, 0.2, 0.3)
        )
        articleVectorProjectionIndexer.hits = listOf(
            ArticleVectorSearchHit(articleId = 7, score = 0.91),
            ArticleVectorSearchHit(articleId = 3, score = 0.82)
        )
        `when`(articleService.getApiReadyArticlesByIds(listOf(7L, 3L)))
            .thenReturn(
                listOf(
                    article(id = 7, title = "AI Supply Chain Security Playbook"),
                    article(id = 3, title = "Build Systems Add Model Signing")
                )
            )

        val response = service.search(ArticleVectorSearchRequest(query = "  AI supply chain security  ", limit = 2))

        assertEquals("AI supply chain security", response.query)
        assertEquals("sigak-article-vectors-test", response.collectionName)
        assertEquals("local", response.embeddingProvider)
        assertEquals("sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2", response.embeddingModelName)
        assertEquals(3, response.embeddingDimension)
        assertEquals(listOf(7L, 3L), response.results.map { result -> result.article.id })
        assertEquals(listOf(0.91, 0.82), response.results.map { result -> result.score })
        assertEquals("AI supply chain security", embeddingClient.requestedText)
        assertEquals(listOf(0.1, 0.2, 0.3), articleVectorProjectionIndexer.requestedVector)
        assertEquals(2, articleVectorProjectionIndexer.requestedLimit)
        assertEquals(1, metricsRecorder.summarize().totalSearchCount)
    }

    @Test
    fun omitsStaleQdrantHitsWhenArticlesAreNoLongerApiReady() {
        embeddingClient.response = EmbeddingResponse(
            provider = "local",
            modelName = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
            dimension = 3,
            embedding = listOf(0.1, 0.2, 0.3)
        )
        articleVectorProjectionIndexer.hits = listOf(
            ArticleVectorSearchHit(articleId = 7, score = 0.91),
            ArticleVectorSearchHit(articleId = 999, score = 0.88),
            ArticleVectorSearchHit(articleId = 3, score = 0.82)
        )
        `when`(articleService.getApiReadyArticlesByIds(listOf(7L, 999L, 3L)))
            .thenReturn(
                listOf(
                    article(id = 7, title = "AI Supply Chain Security Playbook"),
                    article(id = 3, title = "Build Systems Add Model Signing")
                )
            )

        val response = service.search(ArticleVectorSearchRequest(query = "AI supply chain security", limit = 3))

        assertEquals(listOf(7L, 3L), response.results.map { result -> result.article.id })
        assertEquals(listOf(0.91, 0.82), response.results.map { result -> result.score })
        assertEquals(2, metricsRecorder.summarize().lastSearch?.resultCount)
    }

    @Test
    fun keepsFirstScoreWhenQdrantReturnsDuplicateArticleHits() {
        embeddingClient.response = EmbeddingResponse(
            provider = "local",
            modelName = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
            dimension = 3,
            embedding = listOf(0.1, 0.2, 0.3)
        )
        articleVectorProjectionIndexer.hits = listOf(
            ArticleVectorSearchHit(articleId = 7, score = 0.91),
            ArticleVectorSearchHit(articleId = 3, score = 0.82),
            ArticleVectorSearchHit(articleId = 7, score = 0.40)
        )
        `when`(articleService.getApiReadyArticlesByIds(listOf(7L, 3L)))
            .thenReturn(
                listOf(
                    article(id = 7, title = "AI Supply Chain Security Playbook"),
                    article(id = 3, title = "Build Systems Add Model Signing")
                )
            )

        val response = service.search(ArticleVectorSearchRequest(query = "AI supply chain security", limit = 3))

        assertEquals(listOf(7L, 3L), response.results.map { result -> result.article.id })
        assertEquals(listOf(0.91, 0.82), response.results.map { result -> result.score })
    }

    @Test
    fun rejectsBlankQuery() {
        val exception = assertFailsWith<IllegalArgumentException> {
            service.search(ArticleVectorSearchRequest(query = "   "))
        }

        assertEquals("Vector search query must not be blank.", exception.message)
        assertEquals(null, embeddingClient.requestedText)
        assertEquals(null, articleVectorProjectionIndexer.requestedVector)
        verifyNoInteractions(articleService)
    }

    @Test
    fun rejectsLimitGreaterThanConfiguredMaximum() {
        val exception = assertFailsWith<IllegalArgumentException> {
            service.search(ArticleVectorSearchRequest(query = "graph rag", limit = 51))
        }

        assertEquals("Vector search limit must be between 1 and 50.", exception.message)
        assertEquals(null, embeddingClient.requestedText)
        assertEquals(null, articleVectorProjectionIndexer.requestedVector)
        verifyNoInteractions(articleService)
    }

    private fun article(id: Long, title: String): ArticleResponse =
        ArticleResponse(
            id = id,
            title = title,
            source = "Sigak Research",
            url = "https://example.com/articles/$id",
            publishedAt = "2026-05-29T09:00:00Z",
            eventType = "SECURITY",
            primaryCategory = "AI",
            topics = listOf("AI", "supply chain security"),
            summary = "A report explains supply chain controls for AI systems.",
            whyItMatters = "AI delivery paths combine models, packages, and deployment automation.",
            importanceScore = 91,
            relatedArticleIds = listOf(1, 2)
        )

    private class RecordingEmbeddingClient : EmbeddingClient {
        lateinit var response: EmbeddingResponse
        var requestedText: String? = null

        override fun embedText(text: String): EmbeddingResponse {
            requestedText = text

            return response
        }
    }

    private class RecordingArticleVectorProjectionIndexer : ArticleVectorProjectionIndexer {
        var hits: List<ArticleVectorSearchHit> = emptyList()
        var requestedVector: List<Double>? = null
        var requestedLimit: Int? = null

        override fun collectionName(): String = "sigak-article-vectors-test"

        override fun deleteCollectionIfExists() = Unit

        override fun recreateCollection(dimension: Int) = Unit

        override fun upsertAll(documents: List<ArticleVectorDocument>) = Unit

        override fun search(vector: List<Double>, limit: Int): List<ArticleVectorSearchHit> {
            requestedVector = vector
            requestedLimit = limit

            return hits
        }
    }
}
