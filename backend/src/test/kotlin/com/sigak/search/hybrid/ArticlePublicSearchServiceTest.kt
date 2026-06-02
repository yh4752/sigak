package com.sigak.search.hybrid

import com.sigak.search.config.SearchInfrastructureProperties
import com.sigak.search.service.ArticleKeywordSearchService
import kotlin.test.Test
import kotlin.test.assertEquals

class ArticlePublicSearchServiceTest {

    private val keywordSearchService = RecordingKeywordSearchService()
    private val vectorCandidateSearcher = RecordingVectorCandidateSearcher()
    private val fusion = ReciprocalRankFusion()
    private val properties = SearchInfrastructureProperties(
        hybrid = SearchInfrastructureProperties.Hybrid(
            rrfK = 60,
            keywordWeight = 1.0,
            vectorWeight = 1.0,
            keywordCandidateLimit = 20,
            vectorCandidateLimit = 20,
            resultLimit = 20
        )
    )
    private val candidateService = ArticleRetrievalCandidateService(
        articleKeywordSearchService = keywordSearchService,
        articleVectorCandidateSearcher = vectorCandidateSearcher,
        properties = properties
    )
    private val service = ArticlePublicSearchService(
        articleRetrievalCandidateService = candidateService,
        reciprocalRankFusion = fusion,
        properties = properties
    )

    @Test
    fun returnsHybridCandidatesWhenKeywordAndVectorSucceed() {
        keywordSearchService.articleIds = listOf(10L, 20L)
        vectorCandidateSearcher.result = vectorResult(
            ArticleSearchCandidate(articleId = 30, rank = 1, score = 0.91),
            ArticleSearchCandidate(articleId = 10, rank = 2, score = 0.82)
        )

        val result = service.search(" graph ")

        assertEquals(listOf(10L, 30L, 20L), result.articleIds)
        assertEquals(ArticlePublicSearchMode.HYBRID, result.mode)
        assertEquals(2, result.keywordCandidateCount)
        assertEquals(2, result.vectorCandidateCount)
        assertEquals(3, result.fusedCandidateCount)
        assertEquals("graph", keywordSearchService.requestedQuery)
        assertEquals(20, keywordSearchService.requestedLimit)
        assertEquals("graph", vectorCandidateSearcher.requestedQuery)
        assertEquals(20, vectorCandidateSearcher.requestedLimit)
    }

    @Test
    fun returnsKeywordOnlyWhenVectorFails() {
        keywordSearchService.articleIds = listOf(10L, 20L)
        vectorCandidateSearcher.exception = RuntimeException("vector search unavailable")

        val result = service.search("graph")

        assertEquals(listOf(10L, 20L), result.articleIds)
        assertEquals(ArticlePublicSearchMode.KEYWORD_ONLY, result.mode)
        assertEquals(0, result.fusedCandidateCount)
        assertEquals(true, result.vectorFailed)
        assertEquals(false, result.keywordFailed)
        assertEquals("VECTOR_SEARCH_FAILED", result.fallbackReason)
    }

    @Test
    fun returnsVectorOnlyWhenKeywordFails() {
        keywordSearchService.exception = RuntimeException("keyword search unavailable")
        vectorCandidateSearcher.result = vectorResult(
            ArticleSearchCandidate(articleId = 30, rank = 1, score = 0.91),
            ArticleSearchCandidate(articleId = 10, rank = 2, score = 0.82)
        )

        val result = service.search("graph")

        assertEquals(listOf(30L, 10L), result.articleIds)
        assertEquals(ArticlePublicSearchMode.VECTOR_ONLY, result.mode)
        assertEquals(0, result.fusedCandidateCount)
        assertEquals(true, result.keywordFailed)
        assertEquals(false, result.vectorFailed)
        assertEquals("KEYWORD_SEARCH_FAILED", result.fallbackReason)
    }

    @Test
    fun returnsPostgresFallbackMarkerWhenBothProjectionPathsFail() {
        keywordSearchService.exception = RuntimeException("keyword search unavailable")
        vectorCandidateSearcher.exception = RuntimeException("vector search unavailable")

        val result = service.search("graph")

        assertEquals(emptyList(), result.articleIds)
        assertEquals(ArticlePublicSearchMode.POSTGRES_FALLBACK, result.mode)
        assertEquals(true, result.keywordFailed)
        assertEquals(true, result.vectorFailed)
        assertEquals("KEYWORD_SEARCH_FAILED; VECTOR_SEARCH_FAILED", result.fallbackReason)
    }

    @Test
    fun successfulEmptyCandidatesDoNotTriggerPostgresFallback() {
        keywordSearchService.articleIds = emptyList()
        vectorCandidateSearcher.result = vectorResult()

        val result = service.search("graph")

        assertEquals(emptyList(), result.articleIds)
        assertEquals(ArticlePublicSearchMode.HYBRID, result.mode)
        assertEquals(false, result.keywordFailed)
        assertEquals(false, result.vectorFailed)
    }

    @Test
    fun appliesResultLimitToDegradedModes() {
        val limitedProperties = SearchInfrastructureProperties(
            hybrid = SearchInfrastructureProperties.Hybrid(
                keywordCandidateLimit = 5,
                vectorCandidateLimit = 5,
                resultLimit = 1
            )
        )
        val limitedCandidateService = ArticleRetrievalCandidateService(
            articleKeywordSearchService = keywordSearchService,
            articleVectorCandidateSearcher = vectorCandidateSearcher,
            properties = limitedProperties
        )
        val limitedService = ArticlePublicSearchService(
            articleRetrievalCandidateService = limitedCandidateService,
            reciprocalRankFusion = fusion,
            properties = limitedProperties
        )

        keywordSearchService.articleIds = listOf(10L, 20L)
        vectorCandidateSearcher.exception = RuntimeException("vector search unavailable")

        val keywordOnlyResult = limitedService.search("graph")

        assertEquals(listOf(10L), keywordOnlyResult.articleIds)
        assertEquals(2, keywordOnlyResult.keywordCandidateCount)

        keywordSearchService.exception = RuntimeException("keyword search unavailable")
        vectorCandidateSearcher.exception = null
        vectorCandidateSearcher.result = vectorResult(
            ArticleSearchCandidate(articleId = 30, rank = 1, score = 0.91),
            ArticleSearchCandidate(articleId = 10, rank = 2, score = 0.82)
        )

        val vectorOnlyResult = limitedService.search("graph")

        assertEquals(listOf(30L), vectorOnlyResult.articleIds)
    }

    @Test
    fun preservesVectorFailureTimingsFromCandidateException() {
        keywordSearchService.articleIds = listOf(10L, 20L)
        vectorCandidateSearcher.exception = ArticleVectorCandidateSearchException(
            reasonCode = "QDRANT_SEARCH_FAILED",
            embeddingElapsedMs = 3,
            vectorElapsedMs = 9,
            cause = RuntimeException("request body echoed graph")
        )

        val result = service.search("graph")

        assertEquals(ArticlePublicSearchMode.KEYWORD_ONLY, result.mode)
        assertEquals("QDRANT_SEARCH_FAILED", result.fallbackReason)
        assertEquals(3, result.embeddingElapsedMs)
        assertEquals(9, result.vectorElapsedMs)
    }

    private fun vectorResult(vararg candidates: ArticleSearchCandidate): ArticleVectorCandidateSearchResult =
        ArticleVectorCandidateSearchResult(
            query = "graph",
            candidates = candidates.toList(),
            embeddingProvider = "local",
            embeddingModelName = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
            embeddingDimension = 384,
            embeddingElapsedMs = 0,
            vectorElapsedMs = 0,
            totalElapsedMs = 0
        )

    private class RecordingKeywordSearchService : ArticleKeywordSearchService {
        var articleIds: List<Long> = emptyList()
        var exception: RuntimeException? = null
        var requestedQuery: String? = null
        var requestedLimit: Int? = null

        override fun searchArticleIds(query: String, limit: Int): List<Long> {
            requestedQuery = query
            requestedLimit = limit
            exception?.let { throw it }

            return articleIds
        }
    }

    private class RecordingVectorCandidateSearcher : ArticleVectorCandidateSearcher {
        var result = ArticleVectorCandidateSearchResult(
            query = "graph",
            candidates = emptyList(),
            embeddingProvider = "local",
            embeddingModelName = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
            embeddingDimension = 384,
            embeddingElapsedMs = 0,
            vectorElapsedMs = 0,
            totalElapsedMs = 0
        )
        var exception: RuntimeException? = null
        var requestedQuery: String? = null
        var requestedLimit: Int? = null

        override fun collectionName(): String = "sigak-article-vectors-test"

        override fun search(query: String, limit: Int): ArticleVectorCandidateSearchResult {
            requestedQuery = query
            requestedLimit = limit
            exception?.let { throw it }

            return result
        }
    }
}
