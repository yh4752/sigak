package com.sigak.article.service

import com.sigak.article.domain.ArticleEnrichmentEntity
import com.sigak.article.domain.ArticleEntity
import com.sigak.article.domain.ProcessingStatus
import com.sigak.article.dto.ArticleResponse
import com.sigak.article.repository.ArticleRepository
import com.sigak.search.metrics.ArticleSearchMetricObservation
import com.sigak.search.metrics.ArticleSearchMetricsRecorder
import com.sigak.search.service.ArticleKeywordSearchService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ArticleService(
    private val articleRepository: ArticleRepository,
    private val articleKeywordSearchService: ArticleKeywordSearchService,
    private val articleSearchMetricsRecorder: ArticleSearchMetricsRecorder
) {

    @Transactional(readOnly = true)
    fun getArticles(query: String? = null): List<ArticleResponse> {
        val normalizedQuery = query?.trim()

        if (normalizedQuery.isNullOrBlank()) {
            return getApiReadyArticleResponses()
        }

        return searchWithElasticsearchOrFallback(normalizedQuery)
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

    private fun searchWithElasticsearchOrFallback(query: String): List<ArticleResponse> {
        val startedAt = System.nanoTime()

        return try {
            val articleIds = articleKeywordSearchService.searchArticleIds(query)
            val responses = findApiReadyArticleResponsesByIds(articleIds)
            logger.info(
                "Article keyword search completed queryLength={} resultCount={} fallback=false elapsedMs={}",
                query.length,
                responses.size,
                elapsedMillis(startedAt)
            )
            articleSearchMetricsRecorder.record(
                ArticleSearchMetricObservation(
                    queryLength = query.length,
                    resultCount = responses.size,
                    fallback = false,
                    elapsedMs = elapsedMillis(startedAt)
                )
            )
            responses
        } catch (exception: RuntimeException) {
            // 검색 인프라는 projection store이므로 장애가 API 전체 장애로 번지지 않게 PostgreSQL 검색으로 후퇴한다.
            val responses = searchWithPostgresFallback(query)
            logger.warn(
                "Article keyword search failed queryLength={} fallback=true elapsedMs={} reason={}",
                query.length,
                elapsedMillis(startedAt),
                exception.message
            )
            articleSearchMetricsRecorder.record(
                ArticleSearchMetricObservation(
                    queryLength = query.length,
                    resultCount = responses.size,
                    fallback = true,
                    elapsedMs = elapsedMillis(startedAt)
                )
            )
            responses
        }
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

    private fun ArticleRepository.fetchArticleResponseGraph(articles: List<ArticleEntity>) {
        // 응답 변환 시 lazy relation 접근으로 N+1 쿼리가 발생하지 않도록 필요한 그래프를 먼저 로드한다.
        val ids = articles.map { article -> requireNotNull(article.id) }
        if (ids.isEmpty()) {
            return
        }

        fetchEnrichmentsByArticleIdIn(ids)
        fetchTopicsByArticleIdIn(ids)
        fetchOutgoingRelationsByArticleIdIn(ids)
    }

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

    private fun elapsedMillis(startedAt: Long): Long =
        (System.nanoTime() - startedAt) / 1_000_000

    private companion object {
        private val logger = LoggerFactory.getLogger(ArticleService::class.java)
    }
}
