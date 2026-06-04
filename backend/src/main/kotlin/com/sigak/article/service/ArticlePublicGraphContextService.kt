package com.sigak.article.service

import com.sigak.article.domain.ProcessingStatus
import com.sigak.article.dto.ArticlePublicGraphContextResponse
import com.sigak.article.dto.ArticlePublicGraphTopicResponse
import com.sigak.article.dto.ArticlePublicRelatedArticleReasonResponse
import com.sigak.article.repository.ArticleRepository
import com.sigak.search.graph.ArticleGraphContextNotFoundException
import com.sigak.search.graph.ArticleGraphContextResponse
import com.sigak.search.graph.ArticleGraphContextService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ArticlePublicGraphContextService(
    private val articleRepository: ArticleRepository,
    private val graphContextService: ArticleGraphContextService
) {

    @Transactional(readOnly = true)
    fun getContext(articleId: Long): ArticlePublicGraphContextResponse {
        require(articleId > 0) { "articleId must be positive." }

        articleRepository.findApiReadyWithSourceById(articleId, ProcessingStatus.PUBLISHED)
            ?: throw ArticlePublicGraphContextNotFoundException(articleId)

        val internalContext = try {
            graphContextService.getContext(articleId)
        } catch (exception: ArticleGraphContextNotFoundException) {
            null
        } catch (exception: RuntimeException) {
            // Graph context는 보조 정보이므로 Neo4j 장애를 public 상세 본문 장애로 전파하지 않는다.
            null
        }

        return internalContext?.toPublicResponse(articleId) ?: ArticlePublicGraphContextResponse(
            articleId = articleId,
            relatedArticleReasons = emptyList(),
            topics = emptyList()
        )
    }

    private fun ArticleGraphContextResponse.toPublicResponse(requestedArticleId: Long): ArticlePublicGraphContextResponse =
        ArticlePublicGraphContextResponse(
            articleId = requestedArticleId,
            relatedArticleReasons = relatedArticles.map { relatedArticle ->
                ArticlePublicRelatedArticleReasonResponse(
                    articleId = relatedArticle.articleId,
                    reason = relatedArticle.reason,
                    sharedTopics = relatedArticle.sharedTopics
                )
            },
            topics = topics.map { topic ->
                ArticlePublicGraphTopicResponse(
                    name = topic.name,
                    displayName = topic.displayName,
                    relatedArticleIds = topic.relatedArticleIds
                )
            }
        )
}

class ArticlePublicGraphContextNotFoundException(articleId: Long) :
    RuntimeException("Article not found: articleId=$articleId")
