package com.sigak.article.service

import com.sigak.article.domain.ArticleEntity
import com.sigak.article.domain.ProcessingStatus
import com.sigak.article.repository.ArticleRepository
import com.sigak.search.graph.ArticleGraphContextNotFoundException
import com.sigak.search.graph.ArticleGraphContextResponse
import com.sigak.search.graph.ArticleGraphContextService
import com.sigak.search.graph.ArticleGraphContextTimingsResponse
import com.sigak.search.graph.ArticleGraphRelatedArticleResponse
import com.sigak.search.graph.ArticleGraphTopicContextResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class ArticlePublicGraphContextServiceTest {

    private val articleRepository = mock<ArticleRepository>()
    private val graphContextService = mock<ArticleGraphContextService>()
    private val service = ArticlePublicGraphContextService(articleRepository, graphContextService)

    @Test
    fun getContextReturnsPublicSafeGraphContext() {
        `when`(articleRepository.findApiReadyWithSourceById(4L, ProcessingStatus.PUBLISHED))
            .thenReturn(mock<ArticleEntity>())
        `when`(graphContextService.getContext(4L)).thenReturn(
            ArticleGraphContextResponse(
                articleId = 4L,
                topics = listOf(
                    ArticleGraphTopicContextResponse(
                        name = "graph rag",
                        displayName = "Graph RAG",
                        relatedArticleIds = listOf(1L)
                    )
                ),
                relatedArticles = listOf(
                    ArticleGraphRelatedArticleResponse(
                        articleId = 1L,
                        title = "OpenAI Releases Agent Evaluation Toolkit",
                        relationType = "RELATED",
                        reason = "Graph RAG evaluation connects to agent and retrieval evaluation.",
                        sharedTopics = listOf("evaluation")
                    )
                ),
                timings = ArticleGraphContextTimingsResponse(
                    neo4jElapsedMs = 8,
                    totalElapsedMs = 8
                )
            )
        )

        val response = service.getContext(4L)

        assertEquals(4L, response.articleId)
        assertEquals("graph rag", response.topics.single().name)
        assertEquals("Graph RAG", response.topics.single().displayName)
        assertEquals(listOf(1L), response.topics.single().relatedArticleIds)
        assertEquals(1L, response.relatedArticleReasons.single().articleId)
        assertEquals(
            "Graph RAG evaluation connects to agent and retrieval evaluation.",
            response.relatedArticleReasons.single().reason
        )
        assertEquals(listOf("evaluation"), response.relatedArticleReasons.single().sharedTopics)
    }

    @Test
    fun getContextReturnsEmptyContextWhenNeo4jProjectionIsMissing() {
        `when`(articleRepository.findApiReadyWithSourceById(4L, ProcessingStatus.PUBLISHED))
            .thenReturn(mock<ArticleEntity>())
        `when`(graphContextService.getContext(4L)).thenThrow(ArticleGraphContextNotFoundException(4L))

        val response = service.getContext(4L)

        assertEquals(4L, response.articleId)
        assertEquals(emptyList(), response.relatedArticleReasons)
        assertEquals(emptyList(), response.topics)
    }

    @Test
    fun getContextReturnsEmptyContextWhenNeo4jLookupFails() {
        `when`(articleRepository.findApiReadyWithSourceById(4L, ProcessingStatus.PUBLISHED))
            .thenReturn(mock<ArticleEntity>())
        `when`(graphContextService.getContext(4L)).thenThrow(RuntimeException("neo4j unavailable"))

        val response = service.getContext(4L)

        assertEquals(4L, response.articleId)
        assertEquals(emptyList(), response.relatedArticleReasons)
        assertEquals(emptyList(), response.topics)
    }

    @Test
    fun getContextThrowsNotFoundWhenArticleIsNotPublicApiReady() {
        `when`(articleRepository.findApiReadyWithSourceById(999L, ProcessingStatus.PUBLISHED))
            .thenReturn(null)

        val exception = assertFailsWith<ArticlePublicGraphContextNotFoundException> {
            service.getContext(999L)
        }

        assertEquals("Article not found: articleId=999", exception.message)
    }

    @Test
    fun getContextRejectsNonPositiveArticleId() {
        val exception = assertFailsWith<IllegalArgumentException> {
            service.getContext(0L)
        }

        assertEquals("articleId must be positive.", exception.message)
    }
}
