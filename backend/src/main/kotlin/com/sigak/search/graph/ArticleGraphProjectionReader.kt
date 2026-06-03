package com.sigak.search.graph

import com.sigak.article.domain.ArticleEntity
import com.sigak.article.domain.ProcessingStatus
import com.sigak.article.repository.ArticleRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ArticleGraphProjectionReader(
    private val articleRepository: ArticleRepository
) : ArticleGraphProjectionReaderPort {

    @Transactional(readOnly = true)
    override fun readApiReadyGraphDocuments(): List<ArticleGraphProjectionDocument> {
        val articles = articleRepository.findApiReadyArticles(ProcessingStatus.PUBLISHED)
        articleRepository.fetchArticleResponseGraph(articles)

        val apiReadyArticleIds = articles.map { article -> requireNotNull(article.id) }.toSet()

        return articles.map { article -> article.toGraphProjectionDocument(apiReadyArticleIds) }
    }

    private fun ArticleEntity.toGraphProjectionDocument(apiReadyArticleIds: Set<Long>): ArticleGraphProjectionDocument {
        val articleId = requireNotNull(id)

        return ArticleGraphProjectionDocument(
            articleId = articleId,
            title = title,
            source = source.name,
            url = url,
            publishedAt = publishedAt.toString(),
            eventType = eventType.name,
            primaryCategory = primaryCategory.name,
            importanceScore = importanceScore,
            topics = topics
                .sortedBy { topic -> topic.position }
                .map { topic ->
                    ArticleGraphTopicDocument(
                        name = topic.topic.toGraphTopicName(),
                        displayName = topic.topic.trim(),
                        position = topic.position
                    )
                },
            outgoingRelations = outgoingRelations
                .sortedWith(compareBy(nullsLast()) { relation -> relation.id })
                .mapNotNull { relation ->
                    val targetArticleId = requireNotNull(relation.targetArticle.id)
                    // Neo4j projection은 공개 가능한 article graph만 담아야 하므로 비공개 target relation은 버린다.
                    if (targetArticleId !in apiReadyArticleIds) {
                        null
                    } else {
                        ArticleGraphRelationDocument(
                            sourceArticleId = articleId,
                            targetArticleId = targetArticleId,
                            relationType = relation.relationType.name,
                            reason = relation.reason
                        )
                    }
                }
        )
    }

    private fun String.toGraphTopicName(): String =
        trim().lowercase()
}

interface ArticleGraphProjectionReaderPort {
    fun readApiReadyGraphDocuments(): List<ArticleGraphProjectionDocument>
}
