package com.sigak.article.service

import com.sigak.article.domain.ArticleEnrichmentEntity
import com.sigak.article.domain.ArticleEntity
import com.sigak.article.domain.ProcessingStatus
import com.sigak.article.dto.ArticleResponse
import com.sigak.article.repository.ArticleRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ArticleService(
    private val articleRepository: ArticleRepository
) {

    @Transactional(readOnly = true)
    fun getArticles(query: String? = null): List<ArticleResponse> {
        val articles = articleRepository.findApiReadyArticles(ProcessingStatus.PUBLISHED)
        articleRepository.fetchArticleResponseGraph(articles)

        val responses = articles.map { article -> article.toResponse() }
        val normalizedQuery = query?.trim()

        if (normalizedQuery.isNullOrBlank()) {
            return responses
        }

        return responses.filter { article -> article.matches(normalizedQuery) }
    }

    @Transactional(readOnly = true)
    fun getArticle(id: Long): ArticleResponse? {
        val article = articleRepository.findApiReadyWithSourceById(id, ProcessingStatus.PUBLISHED) ?: return null
        articleRepository.fetchArticleResponseGraph(listOf(article))

        return article.toResponse()
    }

    private fun ArticleRepository.fetchArticleResponseGraph(articles: List<ArticleEntity>) {
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
}
