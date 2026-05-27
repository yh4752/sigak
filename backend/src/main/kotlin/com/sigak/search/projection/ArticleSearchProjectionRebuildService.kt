package com.sigak.search.projection

import com.sigak.article.dto.ArticleResponse
import com.sigak.article.service.ArticleService
import kotlin.system.measureTimeMillis
import org.springframework.stereotype.Service

@Service
class ArticleSearchProjectionRebuildService(
    private val articleService: ArticleService,
    private val articleSearchProjectionIndexer: ArticleSearchProjectionIndexer
) {

    fun rebuild(): ArticleSearchProjectionRebuildResponse {
        val indexName = articleSearchProjectionIndexer.indexName()
        var indexedCount = 0
        var failedReason: String? = null
        val durationMs = measureTimeMillis {
            try {
                val documents = articleService.getArticles(null)
                    .map { article -> article.toProjectionDocument() }
                articleSearchProjectionIndexer.replaceAll(documents)
                indexedCount = documents.size
            } catch (exception: Exception) {
                // Projection store는 재생성 가능하므로 실패를 응답에 담고 source of truth인 PostgreSQL은 변경하지 않는다.
                failedReason = exception.message ?: exception::class.simpleName
            }
        }

        return ArticleSearchProjectionRebuildResponse(
            status = if (failedReason == null) COMPLETED else FAILED,
            indexName = indexName,
            indexedCount = indexedCount,
            durationMs = durationMs,
            failedReason = failedReason
        )
    }

    private fun ArticleResponse.toProjectionDocument(): ArticleSearchProjectionDocument =
        ArticleSearchProjectionDocument(
            id = id,
            title = title,
            source = source,
            url = url,
            publishedAt = publishedAt,
            eventType = eventType,
            primaryCategory = primaryCategory,
            topics = topics,
            summary = summary,
            whyItMatters = whyItMatters,
            importanceScore = importanceScore,
            relatedArticleIds = relatedArticleIds
        )

    private companion object {
        const val COMPLETED = "completed"
        const val FAILED = "failed"
    }
}
