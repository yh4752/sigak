package com.sigak.article.service

import com.sigak.article.domain.ArticleEnrichmentEntity
import com.sigak.article.domain.ArticleEntity
import com.sigak.article.domain.ProcessingStatus
import com.sigak.article.dto.ArticleResponse
import com.sigak.article.repository.ArticleRepository
import com.sigak.common.time.Measured
import com.sigak.common.time.elapsedMillis
import com.sigak.common.time.measureElapsed
import com.sigak.search.hybrid.ArticlePublicSearchMode
import com.sigak.search.hybrid.ArticlePublicSearchResult
import com.sigak.search.hybrid.ArticlePublicSearchService
import com.sigak.search.metrics.ArticleSearchMetricObservation
import com.sigak.search.metrics.ArticleSearchMetricsRecorder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ArticleService(
    private val articleRepository: ArticleRepository,
    private val articlePublicSearchService: ArticlePublicSearchService,
    private val articleSearchMetricsRecorder: ArticleSearchMetricsRecorder
) {

    @Transactional(readOnly = true)
    fun getArticles(query: String? = null): List<ArticleResponse> {
        val normalizedQuery = query?.trim()

        if (normalizedQuery.isNullOrBlank()) {
            return getApiReadyArticleResponses()
        }

        return searchWithPublicSearch(normalizedQuery)
    }

    @Transactional(readOnly = true)
    fun getArticle(id: Long): ArticleResponse? {
        val article = articleRepository.findApiReadyWithSourceById(id, ProcessingStatus.PUBLISHED) ?: return null
        articleRepository.fetchArticleResponseGraph(listOf(article))

        return article.toResponse()
    }

    @Transactional(readOnly = true)
    fun getApiReadyArticlesByIds(articleIds: List<Long>): List<ArticleResponse> =
        findApiReadyArticleResponsesByIds(articleIds)

    private fun getApiReadyArticleResponses(): List<ArticleResponse> {
        val articles = articleRepository.findApiReadyArticles(ProcessingStatus.PUBLISHED)
        articleRepository.fetchArticleResponseGraph(articles)

        return articles.map { article -> article.toResponse() }
    }

    private fun searchWithPublicSearch(query: String): List<ArticleResponse> {
        val totalStartedAt = System.nanoTime()
        val searchResult = articlePublicSearchService.search(query)
        val articleLoadResult = loadResponsesForSearchResult(query, searchResult)
        val responses = articleLoadResult.value

        recordSearchObservation(
            query = query,
            totalStartedAt = totalStartedAt,
            searchResult = searchResult,
            articleLoadResult = articleLoadResult
        )

        return responses
    }

    private fun loadResponsesForSearchResult(
        query: String,
        searchResult: ArticlePublicSearchResult
    ): Measured<List<ArticleResponse>> =
        measureElapsed {
            when (searchResult.mode) {
                ArticlePublicSearchMode.POSTGRES_FALLBACK -> searchWithPostgresFallback(query)
                else -> findApiReadyArticleResponsesByIds(searchResult.articleIds)
            }
        }

    private fun recordSearchObservation(
        query: String,
        totalStartedAt: Long,
        searchResult: ArticlePublicSearchResult,
        articleLoadResult: Measured<List<ArticleResponse>>
    ) {
        val responses = articleLoadResult.value

        articleSearchMetricsRecorder.record(
            ArticleSearchMetricObservation(
                queryLength = query.length,
                resultCount = responses.size,
                mode = searchResult.mode,
                keywordCandidateCount = searchResult.keywordCandidateCount,
                vectorCandidateCount = searchResult.vectorCandidateCount,
                fusedCandidateCount = searchResult.fusedCandidateCount,
                staleCandidateCount = staleCandidateCount(searchResult, responses),
                keywordFailed = searchResult.keywordFailed,
                vectorFailed = searchResult.vectorFailed,
                fallbackReason = searchResult.fallbackReason,
                keywordElapsedMs = searchResult.keywordElapsedMs,
                embeddingElapsedMs = searchResult.embeddingElapsedMs,
                vectorElapsedMs = searchResult.vectorElapsedMs,
                fusionElapsedMs = searchResult.fusionElapsedMs,
                articleReloadElapsedMs = articleLoadResult.elapsedMs,
                totalElapsedMs = elapsedMillis(totalStartedAt)
            )
        )
    }

    private fun staleCandidateCount(
        searchResult: ArticlePublicSearchResult,
        responses: List<ArticleResponse>
    ): Int =
        when (searchResult.mode) {
            ArticlePublicSearchMode.POSTGRES_FALLBACK -> 0
            else -> searchResult.articleIds.size - responses.size
        }

    private fun findApiReadyArticleResponsesByIds(articleIds: List<Long>): List<ArticleResponse> {
        val uniqueArticleIds = articleIds.distinct()
        if (uniqueArticleIds.isEmpty()) {
            return emptyList()
        }

        val articles = articleRepository.findApiReadyArticlesByIdIn(
            ids = uniqueArticleIds,
            status = ProcessingStatus.PUBLISHED
        )
        articleRepository.fetchArticleResponseGraph(articles)

        val articlesById = articles.associateBy { article -> requireNotNull(article.id) }

        return uniqueArticleIds.mapNotNull { articleId -> articlesById[articleId]?.toResponse() }
    }

    private fun searchWithPostgresFallback(query: String): List<ArticleResponse> =
        getApiReadyArticleResponses()
            .filter { article -> article.matches(query) }

    private fun ArticleEntity.toResponse(): ArticleResponse {
        val currentEnrichment = currentEnrichment()

        return ArticleResponse(
            id = requireNotNull(id),
            title = title,
            source = source.name,
            url = url,
            publishedAt = publishedAt.toString(),
            eventType = eventType.name,
            primaryCategory = primaryCategory.name,
            topics = topics
                .sortedBy { topic -> topic.position }
                .map { topic -> topic.topic },
            summary = currentEnrichment.summary,
            whyItMatters = currentEnrichment.whyItMatters,
            importanceScore = importanceScore,
            relatedArticleIds = outgoingRelations
                .sortedBy { relation -> relation.id ?: Long.MAX_VALUE }
                .map { relation -> requireNotNull(relation.targetArticle.id) }
        )
    }

    private fun ArticleEntity.currentEnrichment(): ArticleEnrichmentEntity =
        enrichments.firstOrNull { enrichment -> enrichment.current }
            ?: error("Article $id has no current enrichment")

    private fun ArticleResponse.matches(query: String): Boolean =
        title.contains(query, ignoreCase = true) ||
            summary.contains(query, ignoreCase = true) ||
            primaryCategory.contains(query, ignoreCase = true) ||
            topics.any { topic -> topic.contains(query, ignoreCase = true) }
}
