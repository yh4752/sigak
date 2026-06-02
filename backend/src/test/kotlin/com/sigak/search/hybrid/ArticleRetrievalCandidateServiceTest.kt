package com.sigak.search.hybrid

import com.sigak.search.config.SearchInfrastructureProperties
import com.sigak.search.service.ArticleKeywordSearchService
import kotlin.test.Test
import kotlin.test.assertEquals

class ArticleRetrievalCandidateServiceTest {

    private val keywordSearchService = RecordingKeywordSearchService()
    private val vectorCandidateSearcher = RecordingVectorCandidateSearcher()
    private val service = ArticleRetrievalCandidateService(
        articleKeywordSearchService = keywordSearchService,
        articleVectorCandidateSearcher = vectorCandidateSearcher,
        properties = SearchInfrastructureProperties()
    )

    @Test
    fun searchReturnsKeywordAndVectorAttemptsWhenBothSucceed() {
        keywordSearchService.articleIds = listOf(10L, 20L)
        vectorCandidateSearcher.result = vectorResult(
            ArticleSearchCandidate(articleId = 30L, rank = 1, score = 0.91)
        )

        val result = service.search(" graph ")

        assertEquals("graph", result.query)
        assertEquals(listOf(10L, 20L), result.keyword.value?.map { candidate -> candidate.articleId })
        assertEquals(listOf(30L), result.vector.value?.candidates?.map { candidate -> candidate.articleId })
        assertEquals(null, result.keyword.failureReason)
        assertEquals(null, result.vector.failureReason)
        assertEquals("graph", keywordSearchService.requestedQuery)
        assertEquals(20, keywordSearchService.requestedLimit)
        assertEquals("graph", vectorCandidateSearcher.requestedQuery)
        assertEquals(20, vectorCandidateSearcher.requestedLimit)
    }

    @Test
    fun searchPreservesStableKeywordFailureReason() {
        keywordSearchService.exception = RuntimeException("elasticsearch unavailable")
        vectorCandidateSearcher.result = vectorResult()

        val result = service.search("graph")

        assertEquals("KEYWORD_SEARCH_FAILED", result.keyword.failureReason)
        assertEquals(true, result.keyword.failed)
        assertEquals(false, result.vector.failed)
    }

    @Test
    fun searchPreservesVectorSpecificFailureReasonAndTimings() {
        keywordSearchService.articleIds = listOf(10L)
        vectorCandidateSearcher.exception = ArticleVectorCandidateSearchException(
            reasonCode = "QDRANT_SEARCH_FAILED",
            embeddingElapsedMs = 3,
            vectorElapsedMs = 9,
            cause = RuntimeException("qdrant unavailable")
        )

        val result = service.search("graph")

        assertEquals("QDRANT_SEARCH_FAILED", result.vector.failureReason)
        assertEquals(3, result.vector.embeddingElapsedMs)
        assertEquals(9, result.vector.vectorElapsedMs)
    }

    private companion object {
        fun vectorResult(vararg candidates: ArticleSearchCandidate): ArticleVectorCandidateSearchResult =
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
    }

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
        var result = vectorResult()
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
