package com.sigak.search.evaluation.retrieval

import com.sigak.search.config.SearchInfrastructureProperties
import com.sigak.search.hybrid.ArticleRetrievalCandidateAttempt
import com.sigak.search.hybrid.ArticleRetrievalCandidateProvider
import com.sigak.search.hybrid.ArticleRetrievalCandidateSearchResult
import com.sigak.search.hybrid.ArticleSearchCandidate
import com.sigak.search.hybrid.ArticleVectorCandidateSearchResult
import com.sigak.search.hybrid.ReciprocalRankFusion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ArticleRetrievalEvaluationServiceTest {

    private val candidateService = RecordingCandidateService()
    private val articleReloader = RecordingApiReadyArticleReloader(visibleIds = setOf(1L, 4L))
    private val service = ArticleRetrievalEvaluationService(
        candidateProvider = candidateService,
        reciprocalRankFusion = ReciprocalRankFusion(),
        articleReloader = articleReloader,
        searchProperties = SearchInfrastructureProperties(),
        evaluationProperties = ArticleRetrievalEvaluationProperties(enabled = true)
    )

    @Test
    fun createRunsReturnsKeywordVectorAndHybridRunsWhenDependenciesSucceed() {
        candidateService.keyword = ArticleRetrievalCandidateAttempt(
            value = listOf(ArticleSearchCandidate(articleId = 4L, rank = 1)),
            exception = null,
            failureReason = null,
            elapsedMs = 2,
            embeddingElapsedMs = 0,
            vectorElapsedMs = 0
        )
        candidateService.vector = ArticleRetrievalCandidateAttempt(
            value = vectorResult(ArticleSearchCandidate(articleId = 1L, rank = 2, score = 0.9)),
            exception = null,
            failureReason = null,
            elapsedMs = 5,
            embeddingElapsedMs = 3,
            vectorElapsedMs = 2
        )

        val response = service.createRuns(
            ArticleRetrievalRunRequest(
                queries = listOf(" graph "),
                systems = listOf(
                    ArticleRetrievalEvaluationSystem.KEYWORD,
                    ArticleRetrievalEvaluationSystem.VECTOR,
                    ArticleRetrievalEvaluationSystem.HYBRID
                ),
                limit = 20
            )
        )

        val keyword = response.runs.first { run -> run.system == ArticleRetrievalEvaluationSystem.KEYWORD }
        val vector = response.runs.first { run -> run.system == ArticleRetrievalEvaluationSystem.VECTOR }
        val hybrid = response.runs.first { run -> run.system == ArticleRetrievalEvaluationSystem.HYBRID }

        assertEquals(ArticleRetrievalRunStatus.COMPLETED, keyword.status)
        assertEquals(listOf(4L), keyword.rankedArticleIds)
        assertEquals(ArticleRetrievalRunStatus.COMPLETED, vector.status)
        assertEquals(listOf(1L), vector.rankedArticleIds)
        assertEquals(ArticleRetrievalRunStatus.COMPLETED, hybrid.status)
        assertEquals(listOf(4L, 1L), hybrid.rankedArticleIds)
        assertEquals(false, hybrid.degraded)
        assertEquals("HYBRID", hybrid.resolvedMode)
        assertEquals(0, hybrid.staleCandidateCount)
        assertEquals("local", hybrid.metadata?.embeddingProvider)
    }

    @Test
    fun createRunsFailsHybridInsteadOfDegradingWhenVectorFails() {
        candidateService.keyword = ArticleRetrievalCandidateAttempt(
            value = listOf(ArticleSearchCandidate(articleId = 4L, rank = 1)),
            exception = null,
            failureReason = null,
            elapsedMs = 2,
            embeddingElapsedMs = 0,
            vectorElapsedMs = 0
        )
        candidateService.vector = ArticleRetrievalCandidateAttempt(
            value = null,
            exception = RuntimeException("qdrant unavailable"),
            failureReason = "QDRANT_SEARCH_FAILED",
            elapsedMs = 5,
            embeddingElapsedMs = 3,
            vectorElapsedMs = 2
        )

        val response = service.createRuns(
            ArticleRetrievalRunRequest(
                queries = listOf("graph"),
                systems = listOf(ArticleRetrievalEvaluationSystem.HYBRID),
                limit = 20
            )
        )

        val hybrid = response.runs.single()

        assertEquals(ArticleRetrievalRunStatus.FAILED, hybrid.status)
        assertEquals("HYBRID_DEPENDENCY_FAILED: QDRANT_SEARCH_FAILED", hybrid.failureReason)
        assertEquals(emptyList(), hybrid.rankedArticleIds)
        assertEquals(false, hybrid.degraded)
        assertEquals(null, hybrid.resolvedMode)
        assertEquals(2, hybrid.timings.keywordElapsedMs)
        assertEquals(3, hybrid.timings.embeddingElapsedMs)
        assertEquals(2, hybrid.timings.vectorElapsedMs)
    }

    @Test
    fun createRunsOmitsStaleCandidateIdsAfterApiReadyReload() {
        val staleReloader = RecordingApiReadyArticleReloader(visibleIds = setOf(1L, 4L))
        val staleService = ArticleRetrievalEvaluationService(
            candidateProvider = candidateService,
            reciprocalRankFusion = ReciprocalRankFusion(),
            articleReloader = staleReloader,
            searchProperties = SearchInfrastructureProperties(),
            evaluationProperties = ArticleRetrievalEvaluationProperties(enabled = true)
        )
        candidateService.keyword = ArticleRetrievalCandidateAttempt(
            value = listOf(
                ArticleSearchCandidate(articleId = 4L, rank = 1),
                ArticleSearchCandidate(articleId = 999L, rank = 2),
                ArticleSearchCandidate(articleId = 1L, rank = 3)
            ),
            exception = null,
            failureReason = null,
            elapsedMs = 2,
            embeddingElapsedMs = 0,
            vectorElapsedMs = 0
        )

        val response = staleService.createRuns(
            ArticleRetrievalRunRequest(
                queries = listOf("graph"),
                systems = listOf(ArticleRetrievalEvaluationSystem.KEYWORD),
                limit = 20
            )
        )

        val keyword = response.runs.single()

        assertEquals(listOf(4L, 1L), keyword.rankedArticleIds)
        assertEquals(1, keyword.staleCandidateCount)
        assertEquals(3, keyword.candidateCount)
    }

    @Test
    fun createRunsRejectsDisabledEndpoint() {
        val disabledService = ArticleRetrievalEvaluationService(
            candidateProvider = candidateService,
            reciprocalRankFusion = ReciprocalRankFusion(),
            articleReloader = articleReloader,
            searchProperties = SearchInfrastructureProperties(),
            evaluationProperties = ArticleRetrievalEvaluationProperties(enabled = false)
        )

        val exception = assertFailsWith<IllegalStateException> {
            disabledService.createRuns(
                ArticleRetrievalRunRequest(
                    queries = listOf("graph"),
                    systems = listOf(ArticleRetrievalEvaluationSystem.KEYWORD),
                    limit = 20
                )
            )
        }

        assertEquals("Internal search evaluation endpoint is disabled.", exception.message)
    }

    @Test
    fun createRunsRejectsExplicitEmptySystems() {
        val exception = assertFailsWith<IllegalArgumentException> {
            service.createRuns(
                ArticleRetrievalRunRequest(
                    queries = listOf("graph"),
                    systems = emptyList(),
                    limit = 20
                )
            )
        }

        assertEquals("systems must contain at least one system", exception.message)
    }

    @Test
    fun createRunsOnlyCallsRequestedSystemDependencies() {
        service.createRuns(
            ArticleRetrievalRunRequest(
                queries = listOf("graph"),
                systems = listOf(ArticleRetrievalEvaluationSystem.KEYWORD),
                limit = 20
            )
        )

        assertEquals(1, candidateService.keywordCallCount)
        assertEquals(0, candidateService.vectorCallCount)
    }

    private class RecordingCandidateService : ArticleRetrievalCandidateProvider {
        var keyword = ArticleRetrievalCandidateAttempt(
            value = listOf(ArticleSearchCandidate(articleId = 4L, rank = 1)),
            exception = null,
            failureReason = null,
            elapsedMs = 2,
            embeddingElapsedMs = 0,
            vectorElapsedMs = 0
        )
        var vector = ArticleRetrievalCandidateAttempt(
            value = vectorResult(ArticleSearchCandidate(articleId = 1L, rank = 1, score = 0.9)),
            exception = null,
            failureReason = null,
            elapsedMs = 5,
            embeddingElapsedMs = 3,
            vectorElapsedMs = 2
        )
        var keywordCallCount = 0
        var vectorCallCount = 0

        override fun search(query: String): ArticleRetrievalCandidateSearchResult =
            ArticleRetrievalCandidateSearchResult(
                query = query.trim(),
                keyword = searchKeyword(query),
                vector = searchVector(query)
            )

        override fun searchKeyword(query: String): ArticleRetrievalCandidateAttempt<List<ArticleSearchCandidate>> {
            keywordCallCount += 1

            return keyword
        }

        override fun searchVector(query: String): ArticleRetrievalCandidateAttempt<ArticleVectorCandidateSearchResult> {
            vectorCallCount += 1

            return vector
        }
    }

    private class RecordingApiReadyArticleReloader(
        private val visibleIds: Set<Long>
    ) : ApiReadyArticleReloader {
        override fun reloadApiReadyArticleIds(articleIds: List<Long>): List<Long> =
            articleIds.distinct().filter { articleId -> visibleIds.contains(articleId) }
    }

    private companion object {
        fun vectorResult(vararg candidates: ArticleSearchCandidate): ArticleVectorCandidateSearchResult =
            ArticleVectorCandidateSearchResult(
                query = "graph",
                candidates = candidates.toList(),
                embeddingProvider = "local",
                embeddingModelName = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
                embeddingDimension = 384,
                embeddingElapsedMs = 3,
                vectorElapsedMs = 2,
                totalElapsedMs = 5
            )
    }
}
