package com.sigak.search.hybrid

import com.sigak.ai.embedding.EmbeddingClient
import com.sigak.common.time.elapsedMillis
import com.sigak.common.time.runCatchingMeasured
import com.sigak.search.vector.ArticleVectorProjectionIndexer
import org.springframework.stereotype.Service

interface ArticleVectorCandidateSearcher {
    fun collectionName(): String

    fun search(query: String, limit: Int): ArticleVectorCandidateSearchResult
}

@Service
class ArticleVectorCandidateSearchService(
    private val embeddingClient: EmbeddingClient,
    private val articleVectorProjectionIndexer: ArticleVectorProjectionIndexer
) : ArticleVectorCandidateSearcher {

    override fun collectionName(): String =
        articleVectorProjectionIndexer.collectionName()

    override fun search(query: String, limit: Int): ArticleVectorCandidateSearchResult {
        val totalStartedAt = System.nanoTime()
        val normalizedQuery = query.trim()
        require(normalizedQuery.isNotBlank()) { "Vector search query must not be blank." }

        val embeddingResult = runCatchingMeasured { embeddingClient.embedText(normalizedQuery) }
        val embedding = embeddingResult.value
            ?: throw ArticleVectorCandidateSearchException(
                reasonCode = "EMBEDDING_FAILED",
                embeddingElapsedMs = embeddingResult.elapsedMs,
                vectorElapsedMs = 0,
                cause = embeddingResult.exception
            )
        val vectorResult = runCatchingMeasured {
            articleVectorProjectionIndexer.search(
                vector = embedding.embedding,
                limit = limit
            )
        }
        val hits = vectorResult.value
            ?: throw ArticleVectorCandidateSearchException(
                reasonCode = "QDRANT_SEARCH_FAILED",
                embeddingElapsedMs = embeddingResult.elapsedMs,
                vectorElapsedMs = vectorResult.elapsedMs,
                cause = vectorResult.exception
            )
        val candidates = hits
            .distinctBy { hit -> hit.articleId }
            .mapIndexed { index, hit ->
                ArticleSearchCandidate(
                    articleId = hit.articleId,
                    rank = index + 1,
                    score = hit.score
                )
            }

        return ArticleVectorCandidateSearchResult(
            query = normalizedQuery,
            candidates = candidates,
            embeddingProvider = embedding.provider,
            embeddingModelName = embedding.modelName,
            embeddingDimension = embedding.dimension,
            embeddingElapsedMs = embeddingResult.elapsedMs,
            vectorElapsedMs = vectorResult.elapsedMs,
            totalElapsedMs = elapsedMillis(totalStartedAt)
        )
    }
}

data class ArticleVectorCandidateSearchResult(
    val query: String,
    val candidates: List<ArticleSearchCandidate>,
    val embeddingProvider: String,
    val embeddingModelName: String,
    val embeddingDimension: Int,
    val embeddingElapsedMs: Long,
    val vectorElapsedMs: Long,
    val totalElapsedMs: Long
)

class ArticleVectorCandidateSearchException(
    val reasonCode: String,
    val embeddingElapsedMs: Long,
    val vectorElapsedMs: Long,
    cause: Throwable?
) : RuntimeException(reasonCode, cause)
