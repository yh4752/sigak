package com.sigak.search.hybrid

import com.sigak.ai.embedding.EmbeddingClient
import com.sigak.ai.embedding.EmbeddingResponse
import com.sigak.search.vector.ArticleVectorDocument
import com.sigak.search.vector.ArticleVectorProjectionIndexer
import com.sigak.search.vector.ArticleVectorSearchHit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ArticleVectorCandidateSearchServiceTest {

    private val embeddingClient = RecordingEmbeddingClient()
    private val indexer = RecordingArticleVectorProjectionIndexer()
    private val service = ArticleVectorCandidateSearchService(
        embeddingClient = embeddingClient,
        articleVectorProjectionIndexer = indexer
    )

    @Test
    fun searchesVectorCandidatesInQdrantRankOrder() {
        embeddingClient.response = EmbeddingResponse(
            provider = "local",
            modelName = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
            dimension = 3,
            embedding = listOf(0.1, 0.2, 0.3)
        )
        indexer.hits = listOf(
            ArticleVectorSearchHit(articleId = 7, score = 0.91),
            ArticleVectorSearchHit(articleId = 3, score = 0.82),
            ArticleVectorSearchHit(articleId = 7, score = 0.40)
        )

        val result = service.search(query = "  AI security  ", limit = 3)

        assertEquals("AI security", result.query)
        assertEquals(listOf(7L, 3L), result.candidates.map { candidate -> candidate.articleId })
        assertEquals(listOf(1, 2), result.candidates.map { candidate -> candidate.rank })
        assertEquals(listOf(0.91, 0.82), result.candidates.map { candidate -> candidate.score })
        assertEquals("AI security", embeddingClient.requestedText)
        assertEquals(listOf(0.1, 0.2, 0.3), indexer.requestedVector)
        assertEquals(3, indexer.requestedLimit)
        assertEquals("local", result.embeddingProvider)
        assertEquals("sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2", result.embeddingModelName)
        assertEquals(3, result.embeddingDimension)
    }

    @Test
    fun rejectsBlankVectorCandidateQuery() {
        val exception = assertFailsWith<IllegalArgumentException> {
            service.search(query = "   ", limit = 3)
        }

        assertEquals("Vector search query must not be blank.", exception.message)
    }

    @Test
    fun wrapsEmbeddingFailureWithBoundedReasonCode() {
        embeddingClient.exception = RuntimeException("request body echoed graph")

        val exception = assertFailsWith<ArticleVectorCandidateSearchException> {
            service.search(query = "graph", limit = 3)
        }

        assertEquals("EMBEDDING_FAILED", exception.reasonCode)
        assertTrue(exception.embeddingElapsedMs >= 0)
        assertEquals(0, exception.vectorElapsedMs)
    }

    @Test
    fun wrapsQdrantFailureWithBoundedReasonCodeAndTimings() {
        embeddingClient.response = EmbeddingResponse(
            provider = "local",
            modelName = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
            dimension = 3,
            embedding = listOf(0.1, 0.2, 0.3)
        )
        indexer.exception = RuntimeException("request body echoed graph")

        val exception = assertFailsWith<ArticleVectorCandidateSearchException> {
            service.search(query = "graph", limit = 3)
        }

        assertEquals("QDRANT_SEARCH_FAILED", exception.reasonCode)
        assertTrue(exception.embeddingElapsedMs >= 0)
        assertTrue(exception.vectorElapsedMs >= 0)
    }

    private class RecordingEmbeddingClient : EmbeddingClient {
        lateinit var response: EmbeddingResponse
        var exception: RuntimeException? = null
        var requestedText: String? = null

        override fun embedText(text: String): EmbeddingResponse {
            requestedText = text
            exception?.let { throw it }

            return response
        }
    }

    private class RecordingArticleVectorProjectionIndexer : ArticleVectorProjectionIndexer {
        var hits: List<ArticleVectorSearchHit> = emptyList()
        var exception: RuntimeException? = null
        var requestedVector: List<Double>? = null
        var requestedLimit: Int? = null

        override fun collectionName(): String = "sigak-article-vectors-test"

        override fun deleteCollectionIfExists() = Unit

        override fun recreateCollection(dimension: Int) = Unit

        override fun upsertAll(documents: List<ArticleVectorDocument>) = Unit

        override fun search(vector: List<Double>, limit: Int): List<ArticleVectorSearchHit> {
            requestedVector = vector
            requestedLimit = limit
            exception?.let { throw it }

            return hits
        }
    }
}
