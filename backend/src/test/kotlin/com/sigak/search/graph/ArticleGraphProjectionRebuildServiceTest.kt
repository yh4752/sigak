package com.sigak.search.graph

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArticleGraphProjectionRebuildServiceTest {

    @Test
    fun rebuildReturnsCompletedCountsAndRebuiltAt() {
        val documents = listOf(articleDocument(articleId = 1L), articleDocument(articleId = 2L))
        val indexer = RecordingArticleGraphProjectionIndexer(
            result = ArticleGraphProjectionIndexResult(
                articleNodeCount = 2,
                topicNodeCount = 3,
                hasTopicRelationshipCount = 4,
                relatedToRelationshipCount = 1
            )
        )
        val service = ArticleGraphProjectionRebuildService(
            reader = StubArticleGraphProjectionReader(documents),
            indexer = indexer
        )

        val response = service.rebuild()

        assertEquals("completed", response.status)
        assertNotNull(response.rebuiltAt)
        assertEquals(2, response.articleNodeCount)
        assertEquals(3, response.topicNodeCount)
        assertEquals(4, response.hasTopicRelationshipCount)
        assertEquals(1, response.relatedToRelationshipCount)
        assertTrue(response.durationMs >= 0)
        assertNull(response.failedReason)
        assertEquals(documents, indexer.rebuiltDocuments)
    }

    @Test
    fun rebuildReturnsCompletedWhenNoArticlesExist() {
        val indexer = RecordingArticleGraphProjectionIndexer(
            result = ArticleGraphProjectionIndexResult(
                articleNodeCount = 0,
                topicNodeCount = 0,
                hasTopicRelationshipCount = 0,
                relatedToRelationshipCount = 0
            )
        )
        val service = ArticleGraphProjectionRebuildService(
            reader = StubArticleGraphProjectionReader(emptyList()),
            indexer = indexer
        )

        val response = service.rebuild()

        assertEquals("completed", response.status)
        assertNotNull(response.rebuiltAt)
        assertEquals(0, response.articleNodeCount)
        assertEquals(0, response.topicNodeCount)
        assertEquals(0, response.hasTopicRelationshipCount)
        assertEquals(0, response.relatedToRelationshipCount)
        assertNull(response.failedReason)
        assertEquals(emptyList(), indexer.rebuiltDocuments)
    }

    @Test
    fun rebuildReturnsFailedResponseWhenIndexerFails() {
        val service = ArticleGraphProjectionRebuildService(
            reader = StubArticleGraphProjectionReader(listOf(articleDocument(articleId = 1L))),
            indexer = FailingArticleGraphProjectionIndexer("Neo4j is unavailable\nstack trace line")
        )

        val response = service.rebuild()

        assertEquals("failed", response.status)
        assertNull(response.rebuiltAt)
        assertEquals(0, response.articleNodeCount)
        assertEquals(0, response.topicNodeCount)
        assertEquals(0, response.hasTopicRelationshipCount)
        assertEquals(0, response.relatedToRelationshipCount)
        assertTrue(response.durationMs >= 0)
        assertEquals("Neo4j is unavailable", response.failedReason)
    }

    @Test
    fun rebuildReturnsFailedResponseWhenReaderFails() {
        val service = ArticleGraphProjectionRebuildService(
            reader = FailingArticleGraphProjectionReader("PostgreSQL graph read failed\nstack trace line"),
            indexer = RecordingArticleGraphProjectionIndexer(
                result = ArticleGraphProjectionIndexResult(
                    articleNodeCount = 1,
                    topicNodeCount = 1,
                    hasTopicRelationshipCount = 1,
                    relatedToRelationshipCount = 1
                )
            )
        )

        val response = service.rebuild()

        assertEquals("failed", response.status)
        assertNull(response.rebuiltAt)
        assertEquals(0, response.articleNodeCount)
        assertEquals(0, response.topicNodeCount)
        assertEquals(0, response.hasTopicRelationshipCount)
        assertEquals(0, response.relatedToRelationshipCount)
        assertTrue(response.durationMs >= 0)
        assertEquals("PostgreSQL graph read failed", response.failedReason)
    }

    private fun articleDocument(articleId: Long): ArticleGraphProjectionDocument =
        ArticleGraphProjectionDocument(
            articleId = articleId,
            title = "Article $articleId",
            source = "OpenAI",
            url = "https://example.com/articles/$articleId",
            publishedAt = "2026-06-03T00:00:00Z",
            eventType = "OFFICIAL_ANNOUNCEMENT",
            primaryCategory = "AI",
            importanceScore = 80,
            topics = listOf(
                ArticleGraphTopicDocument(
                    name = "graph rag",
                    displayName = "Graph RAG",
                    position = 0
                )
            ),
            outgoingRelations = emptyList()
        )

    private class StubArticleGraphProjectionReader(
        private val documents: List<ArticleGraphProjectionDocument>
    ) : ArticleGraphProjectionReaderPort {

        override fun readApiReadyGraphDocuments(): List<ArticleGraphProjectionDocument> = documents
    }

    private class FailingArticleGraphProjectionReader(
        private val failureMessage: String
    ) : ArticleGraphProjectionReaderPort {

        override fun readApiReadyGraphDocuments(): List<ArticleGraphProjectionDocument> {
            throw RuntimeException(failureMessage)
        }
    }

    private class RecordingArticleGraphProjectionIndexer(
        private val result: ArticleGraphProjectionIndexResult
    ) : ArticleGraphProjectionIndexer {
        var rebuiltDocuments: List<ArticleGraphProjectionDocument>? = null

        override fun rebuild(documents: List<ArticleGraphProjectionDocument>): ArticleGraphProjectionIndexResult {
            rebuiltDocuments = documents
            return result
        }

        override fun findContext(articleId: Long): ArticleGraphContextProjection? = null
    }

    private class FailingArticleGraphProjectionIndexer(
        private val failureMessage: String
    ) : ArticleGraphProjectionIndexer {

        override fun rebuild(documents: List<ArticleGraphProjectionDocument>): ArticleGraphProjectionIndexResult {
            throw RuntimeException(failureMessage)
        }

        override fun findContext(articleId: Long): ArticleGraphContextProjection? = null
    }
}
