package com.sigak.search.graph

import com.sigak.common.time.measureElapsed
import org.springframework.stereotype.Service

@Service
class ArticleGraphContextService(
    private val indexer: ArticleGraphProjectionIndexer
) {

    fun getContext(articleId: Long): ArticleGraphContextResponse {
        require(articleId > 0) { "articleId must be positive." }

        val contextMeasurement = measureElapsed {
            indexer.findContext(articleId)
                ?: throw ArticleGraphContextNotFoundException(articleId)
        }

        return contextMeasurement.value.toResponse(
            neo4jElapsedMs = contextMeasurement.elapsedMs,
            totalElapsedMs = contextMeasurement.elapsedMs
        )
    }

    private fun ArticleGraphContextProjection.toResponse(
        neo4jElapsedMs: Long,
        totalElapsedMs: Long
    ): ArticleGraphContextResponse =
        ArticleGraphContextResponse(
            articleId = articleId,
            topics = topics.map { topic ->
                ArticleGraphTopicContextResponse(
                    name = topic.name,
                    displayName = topic.displayName,
                    relatedArticleIds = topic.relatedArticleIds
                )
            },
            relatedArticles = relatedArticles.map { relatedArticle ->
                ArticleGraphRelatedArticleResponse(
                    articleId = relatedArticle.articleId,
                    title = relatedArticle.title,
                    relationType = relatedArticle.relationType,
                    reason = relatedArticle.reason,
                    sharedTopics = relatedArticle.sharedTopics
                )
            },
            timings = ArticleGraphContextTimingsResponse(
                neo4jElapsedMs = neo4jElapsedMs,
                totalElapsedMs = totalElapsedMs
            )
        )
}

class ArticleGraphContextNotFoundException(articleId: Long) :
    RuntimeException("Article graph context not found: articleId=$articleId")
