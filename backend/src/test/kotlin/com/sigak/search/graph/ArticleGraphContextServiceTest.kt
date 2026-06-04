package com.sigak.search.graph

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ArticleGraphContextServiceTest {

    private val indexer = RecordingArticleGraphProjectionIndexer()
    private val service = ArticleGraphContextService(indexer)

    @Test
    fun getContextReturnsRelatedArticlesTopicsAndTimings() {
        indexer.context = ArticleGraphContextProjection(
            articleId = 4L,
            topics = listOf(
                ArticleGraphTopicContextProjection(
                    name = "graph rag",
                    displayName = "Graph RAG",
                    relatedArticleIds = listOf(1L)
                )
            ),
            relatedArticles = listOf(
                ArticleGraphRelatedArticleProjection(
                    articleId = 1L,
                    title = "OpenAI Releases Agent Evaluation Toolkit",
                    relationType = "RELATED",
                    reason = "Graph RAG evaluation connects to agent and retrieval evaluation.",
                    sharedTopics = listOf("evaluation")
                )
            )
        )

        val response = service.getContext(4L)

        assertEquals(4L, response.articleId)
        assertEquals("graph rag", response.topics.single().name)
        assertEquals("Graph RAG", response.topics.single().displayName)
        assertEquals(listOf(1L), response.topics.single().relatedArticleIds)
        assertEquals(1L, response.relatedArticles.single().articleId)
        assertEquals("OpenAI Releases Agent Evaluation Toolkit", response.relatedArticles.single().title)
        assertEquals("RELATED", response.relatedArticles.single().relationType)
        assertEquals(
            "Graph RAG evaluation connects to agent and retrieval evaluation.",
            response.relatedArticles.single().reason
        )
        assertEquals(listOf("evaluation"), response.relatedArticles.single().sharedTopics)
        assertTrue(response.timings.neo4jElapsedMs >= 0)
        assertTrue(response.timings.totalElapsedMs >= 0)
        assertEquals(4L, indexer.requestedArticleId)
    }

    @Test
    fun getContextRejectsNonPositiveArticleIds() {
        val exception = assertFailsWith<IllegalArgumentException> {
            service.getContext(0L)
        }

        assertEquals("articleId must be positive.", exception.message)
    }

    @Test
    fun getContextThrowsNotFoundWhenProjectionArticleIsMissing() {
        indexer.context = null

        val exception = assertFailsWith<ArticleGraphContextNotFoundException> {
            service.getContext(99L)
        }

        assertEquals("Article graph context not found: articleId=99", exception.message)
    }

    private class RecordingArticleGraphProjectionIndexer : ArticleGraphProjectionIndexer {
        var context: ArticleGraphContextProjection? = null
        var requestedArticleId: Long? = null

        override fun rebuild(documents: List<ArticleGraphProjectionDocument>): ArticleGraphProjectionIndexResult =
            ArticleGraphProjectionIndexResult(0, 0, 0, 0)

        override fun findContext(articleId: Long): ArticleGraphContextProjection? {
            requestedArticleId = articleId
            return context
        }
    }
}
