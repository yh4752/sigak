package com.sigak.search.evaluation.retrieval

import com.sigak.article.service.ArticleService
import com.sigak.common.time.elapsedMillis
import com.sigak.common.time.measureElapsed
import com.sigak.search.config.SearchInfrastructureProperties
import com.sigak.search.hybrid.ArticleRetrievalCandidateAttempt
import com.sigak.search.hybrid.ArticleRetrievalCandidateProvider
import com.sigak.search.hybrid.ArticleSearchCandidate
import com.sigak.search.hybrid.ArticleVectorCandidateSearchResult
import com.sigak.search.hybrid.ReciprocalRankFusion
import java.time.Instant
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service

@Service
class ArticleRetrievalEvaluationService(
    private val candidateProvider: ArticleRetrievalCandidateProvider,
    private val reciprocalRankFusion: ReciprocalRankFusion,
    private val articleReloader: ApiReadyArticleReloader,
    private val searchProperties: SearchInfrastructureProperties,
    private val evaluationProperties: ArticleRetrievalEvaluationProperties
) {

    fun createRuns(request: ArticleRetrievalRunRequest): ArticleRetrievalRunResponse {
        if (!evaluationProperties.enabled) {
            throw IllegalStateException("Internal search evaluation endpoint is disabled.")
        }

        val queries = normalizedQueries(request.queries)
        val systems = normalizedSystems(request.systems)
        val limit = request.limit ?: searchProperties.hybrid.resultLimit
        require(limit in 1..searchProperties.hybrid.resultLimit) {
            "limit must be between 1 and ${searchProperties.hybrid.resultLimit}"
        }

        val runs = queries.flatMap { query ->
            val candidates = QueryCandidateAttempts(
                query = query,
                candidateProvider = candidateProvider
            )
            systems.map { system ->
                runSystem(query = query, system = system, candidates = candidates, limit = limit)
            }
        }

        return ArticleRetrievalRunResponse(
            generatedAt = Instant.now(),
            limit = limit,
            runs = runs
        )
    }

    private fun normalizedQueries(queries: List<String>): List<String> {
        require(queries.isNotEmpty()) { "queries must contain at least one query" }
        require(queries.size <= 50) { "queries must contain at most 50 queries" }

        return queries.map { query ->
            query.trim().also { normalizedQuery ->
                require(normalizedQuery.isNotBlank()) { "queries must not contain blank values" }
            }
        }
    }

    private fun normalizedSystems(systems: List<ArticleRetrievalEvaluationSystem>?): List<ArticleRetrievalEvaluationSystem> {
        if (systems == null) {
            return ArticleRetrievalEvaluationSystem.values().toList()
        }

        require(systems.isNotEmpty()) { "systems must contain at least one system" }

        return systems
    }

    private fun runSystem(
        query: String,
        system: ArticleRetrievalEvaluationSystem,
        candidates: QueryCandidateAttempts,
        limit: Int
    ): ArticleRetrievalRunItemResponse {
        val totalStartedAt = System.nanoTime()

        return when (system) {
            ArticleRetrievalEvaluationSystem.KEYWORD -> runKeyword(query, candidates, limit, totalStartedAt)
            ArticleRetrievalEvaluationSystem.VECTOR -> runVector(query, candidates, limit, totalStartedAt)
            ArticleRetrievalEvaluationSystem.HYBRID -> runHybrid(query, candidates, limit, totalStartedAt)
        }
    }

    private fun runKeyword(
        query: String,
        candidates: QueryCandidateAttempts,
        limit: Int,
        totalStartedAt: Long
    ): ArticleRetrievalRunItemResponse {
        val keyword = candidates.keyword
        if (keyword.failed) {
            return failedRun(
                query = query,
                system = ArticleRetrievalEvaluationSystem.KEYWORD,
                failureReason = keyword.failureReason,
                timings = timingsFrom(keyword),
                totalStartedAt = totalStartedAt
            )
        }

        return completedRun(
            query = query,
            system = ArticleRetrievalEvaluationSystem.KEYWORD,
            candidates = keyword.value.orEmpty().take(limit),
            metadata = null,
            timings = ArticleRetrievalRunTimingsResponse(
                keywordElapsedMs = keyword.elapsedMs,
                embeddingElapsedMs = 0,
                vectorElapsedMs = 0,
                fusionElapsedMs = 0,
                articleReloadElapsedMs = 0,
                totalElapsedMs = 0
            ),
            totalStartedAt = totalStartedAt,
            resolvedMode = "KEYWORD",
            limit = limit
        )
    }

    private fun runVector(
        query: String,
        candidates: QueryCandidateAttempts,
        limit: Int,
        totalStartedAt: Long
    ): ArticleRetrievalRunItemResponse {
        val vector = candidates.vector
        if (vector.failed) {
            return failedRun(
                query = query,
                system = ArticleRetrievalEvaluationSystem.VECTOR,
                failureReason = vector.failureReason,
                timings = timingsFrom(vector),
                totalStartedAt = totalStartedAt
            )
        }

        val vectorResult = requireNotNull(vector.value)
        return completedRun(
            query = query,
            system = ArticleRetrievalEvaluationSystem.VECTOR,
            candidates = vectorResult.candidates.take(limit),
            metadata = vectorResult.toMetadata(),
            timings = ArticleRetrievalRunTimingsResponse(
                keywordElapsedMs = 0,
                embeddingElapsedMs = vectorResult.embeddingElapsedMs,
                vectorElapsedMs = vectorResult.vectorElapsedMs,
                fusionElapsedMs = 0,
                articleReloadElapsedMs = 0,
                totalElapsedMs = 0
            ),
            totalStartedAt = totalStartedAt,
            resolvedMode = "VECTOR",
            limit = limit
        )
    }

    private fun runHybrid(
        query: String,
        candidates: QueryCandidateAttempts,
        limit: Int,
        totalStartedAt: Long
    ): ArticleRetrievalRunItemResponse {
        val keyword = candidates.keyword
        val vector = candidates.vector
        if (keyword.failed || vector.failed) {
            return failedRun(
                query = query,
                system = ArticleRetrievalEvaluationSystem.HYBRID,
                failureReason = hybridFailureReason(keyword, vector),
                timings = ArticleRetrievalRunTimingsResponse(
                    keywordElapsedMs = keyword.elapsedMs,
                    embeddingElapsedMs = vector.embeddingElapsedMs,
                    vectorElapsedMs = vector.vectorElapsedMs,
                    fusionElapsedMs = 0,
                    articleReloadElapsedMs = 0,
                    totalElapsedMs = 0
                ),
                totalStartedAt = totalStartedAt
            )
        }

        val vectorResult = requireNotNull(vector.value)
        val fusion = measureElapsed {
            reciprocalRankFusion.fuse(
                keywordCandidates = keyword.value.orEmpty(),
                vectorCandidates = vectorResult.candidates,
                rrfK = searchProperties.hybrid.rrfK,
                keywordWeight = searchProperties.hybrid.keywordWeight,
                vectorWeight = searchProperties.hybrid.vectorWeight,
                limit = limit
            )
        }

        return completedRun(
            query = query,
            system = ArticleRetrievalEvaluationSystem.HYBRID,
            candidates = fusion.value,
            metadata = vectorResult.toMetadata(),
            timings = ArticleRetrievalRunTimingsResponse(
                keywordElapsedMs = keyword.elapsedMs,
                embeddingElapsedMs = vectorResult.embeddingElapsedMs,
                vectorElapsedMs = vectorResult.vectorElapsedMs,
                fusionElapsedMs = fusion.elapsedMs,
                articleReloadElapsedMs = 0,
                totalElapsedMs = 0
            ),
            totalStartedAt = totalStartedAt,
            resolvedMode = "HYBRID",
            limit = limit
        )
    }

    private fun completedRun(
        query: String,
        system: ArticleRetrievalEvaluationSystem,
        candidates: List<ArticleSearchCandidate>,
        metadata: ArticleRetrievalRunMetadataResponse?,
        timings: ArticleRetrievalRunTimingsResponse,
        totalStartedAt: Long,
        resolvedMode: String,
        limit: Int
    ): ArticleRetrievalRunItemResponse {
        val candidateIds = candidates.map { candidate -> candidate.articleId }.take(limit)
        val uniqueCandidateIds = candidateIds.distinct()
        val reload = measureElapsed { articleReloader.reloadApiReadyArticleIds(candidateIds) }
        val staleCandidateCount = uniqueCandidateIds.size - reload.value.size

        return ArticleRetrievalRunItemResponse(
            query = query,
            system = system,
            status = ArticleRetrievalRunStatus.COMPLETED,
            rankedArticleIds = reload.value,
            candidateCount = uniqueCandidateIds.size,
            staleCandidateCount = staleCandidateCount,
            failureReason = null,
            degraded = false,
            resolvedMode = resolvedMode,
            timings = timings.copy(
                articleReloadElapsedMs = reload.elapsedMs,
                totalElapsedMs = elapsedMillis(totalStartedAt)
            ),
            metadata = metadata
        )
    }

    private fun failedRun(
        query: String,
        system: ArticleRetrievalEvaluationSystem,
        failureReason: String?,
        timings: ArticleRetrievalRunTimingsResponse,
        totalStartedAt: Long
    ): ArticleRetrievalRunItemResponse =
        ArticleRetrievalRunItemResponse(
            query = query,
            system = system,
            status = ArticleRetrievalRunStatus.FAILED,
            rankedArticleIds = emptyList(),
            candidateCount = 0,
            staleCandidateCount = 0,
            failureReason = failureReason,
            degraded = false,
            resolvedMode = null,
            timings = timings.copy(
                totalElapsedMs = elapsedMillis(totalStartedAt)
            ),
            metadata = null
        )

    private fun <T> timingsFrom(attempt: ArticleRetrievalCandidateAttempt<T>): ArticleRetrievalRunTimingsResponse =
        ArticleRetrievalRunTimingsResponse(
            keywordElapsedMs = attempt.elapsedMs.takeIf { attempt.embeddingElapsedMs == 0L && attempt.vectorElapsedMs == 0L } ?: 0,
            embeddingElapsedMs = attempt.embeddingElapsedMs,
            vectorElapsedMs = attempt.vectorElapsedMs,
            fusionElapsedMs = 0,
            articleReloadElapsedMs = 0,
            totalElapsedMs = 0
        )

    private fun hybridFailureReason(
        keyword: ArticleRetrievalCandidateAttempt<List<ArticleSearchCandidate>>,
        vector: ArticleRetrievalCandidateAttempt<ArticleVectorCandidateSearchResult>
    ): String {
        val dependencyReasons = listOfNotNull(keyword.failureReason, vector.failureReason)

        return if (dependencyReasons.isEmpty()) {
            "HYBRID_DEPENDENCY_FAILED"
        } else {
            "HYBRID_DEPENDENCY_FAILED: ${dependencyReasons.joinToString("; ")}"
        }
    }

    private fun ArticleVectorCandidateSearchResult.toMetadata(): ArticleRetrievalRunMetadataResponse =
        ArticleRetrievalRunMetadataResponse(
            embeddingProvider = embeddingProvider,
            embeddingModelName = embeddingModelName,
            embeddingDimension = embeddingDimension
        )
}

private class QueryCandidateAttempts(
    private val query: String,
    private val candidateProvider: ArticleRetrievalCandidateProvider
) {
    val keyword: ArticleRetrievalCandidateAttempt<List<ArticleSearchCandidate>> by lazy {
        candidateProvider.searchKeyword(query)
    }

    val vector: ArticleRetrievalCandidateAttempt<ArticleVectorCandidateSearchResult> by lazy {
        candidateProvider.searchVector(query)
    }
}

interface ApiReadyArticleReloader {
    fun reloadApiReadyArticleIds(articleIds: List<Long>): List<Long>
}

@Component
class ArticleServiceApiReadyArticleReloader(
    private val articleService: ArticleService
) : ApiReadyArticleReloader {
    override fun reloadApiReadyArticleIds(articleIds: List<Long>): List<Long> =
        articleService.getApiReadyArticlesByIds(articleIds).map { article -> article.id }
}
