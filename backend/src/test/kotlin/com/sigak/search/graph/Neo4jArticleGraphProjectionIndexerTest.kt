package com.sigak.search.graph

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.any
import org.mockito.Mockito.anyMap
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.neo4j.driver.Driver
import org.neo4j.driver.Record
import org.neo4j.driver.Result
import org.neo4j.driver.Session
import org.neo4j.driver.TransactionCallback
import org.neo4j.driver.TransactionContext
import org.neo4j.driver.Values

class Neo4jArticleGraphProjectionIndexerTest {

    @Test
    fun rebuildCreatesProjectionMetadataInWriteTransactionAfterEnsuringConstraints() {
        val driver = mock(Driver::class.java)
        val constraintSession = mock(Session::class.java)
        val rebuildSession = mock(Session::class.java)
        val transaction = mock(TransactionContext::class.java)
        `when`(driver.session()).thenReturn(constraintSession, rebuildSession)
        `when`(constraintSession.run(anyString())).thenReturn(mock(Result::class.java))
        `when`(transaction.run(anyString())).thenReturn(mock(Result::class.java))
        `when`(transaction.run(anyString(), anyMap())).thenReturn(mock(Result::class.java))
        `when`(rebuildSession.executeWrite(anyTransactionCallback<ArticleGraphProjectionIndexResult>()))
            .thenAnswer { invocation ->
                invocation.getArgument<TransactionCallback<ArticleGraphProjectionIndexResult>>(0).execute(transaction)
            }
        val indexer = Neo4jArticleGraphProjectionIndexer(driver)

        val result = indexer.rebuild(listOf(sourceDocument(), targetDocument()))

        assertEquals(2, result.articleNodeCount)
        assertEquals(2, result.topicNodeCount)
        assertEquals(3, result.hasTopicRelationshipCount)
        assertEquals(1, result.relatedToRelationshipCount)
        verify(constraintSession).run(
            """
            CREATE CONSTRAINT sigak_article_article_id IF NOT EXISTS
            FOR (article:Article)
            REQUIRE article.articleId IS UNIQUE
            """.trimIndent()
        )
        verify(constraintSession).run(
            """
            CREATE CONSTRAINT sigak_topic_name IF NOT EXISTS
            FOR (topic:Topic)
            REQUIRE topic.name IS UNIQUE
            """.trimIndent()
        )
        verify(transaction).run(
            """
            MATCH (node)
            WHERE node:Article OR node:Topic
            DETACH DELETE node
            """.trimIndent()
        )
        val cypherCaptor = ArgumentCaptor.forClass(String::class.java)
        verify(transaction, org.mockito.Mockito.atLeastOnce()).run(cypherCaptor.capture(), anyMap())
        val cyphers = cypherCaptor.allValues.joinToString("\n")
        assertTrue(cyphers.contains("CREATE (article:Article"))
        assertTrue(cyphers.contains("MERGE (topic:Topic {name: \$name})"))
        assertTrue(cyphers.contains("MERGE (article)-[hasTopic:HAS_TOPIC]->(topic)"))
        assertTrue(cyphers.contains("MERGE (source)-[relatedTo:RELATED_TO]->(target)"))
        verify(constraintSession).close()
        verify(rebuildSession).close()
    }

    @Test
    fun findContextReturnsNullWhenArticleNodeDoesNotExist() {
        val driver = mock(Driver::class.java)
        val session = mock(Session::class.java)
        val result = mock(Result::class.java)
        val record = mock(Record::class.java)
        `when`(driver.session()).thenReturn(session)
        `when`(session.run(anyString(), anyMap())).thenReturn(result)
        `when`(result.single()).thenReturn(record)
        `when`(record.get("articleExists")).thenReturn(Values.value(false))
        val indexer = Neo4jArticleGraphProjectionIndexer(driver)

        val context = indexer.findContext(99L)

        assertNull(context)
        verify(session).close()
    }

    @Test
    fun findContextMapsTopicsAndRelatedArticlesUsingPositionAliasCypher() {
        val driver = mock(Driver::class.java)
        val session = mock(Session::class.java)
        val result = mock(Result::class.java)
        val record = mock(Record::class.java)
        `when`(driver.session()).thenReturn(session)
        `when`(session.run(anyString(), anyMap())).thenReturn(result)
        `when`(result.single()).thenReturn(record)
        `when`(record.get("articleExists")).thenReturn(Values.value(true))
        `when`(record.get("topics")).thenReturn(
            Values.value(
                listOf(
                    mapOf(
                        "name" to "graph rag",
                        "displayName" to "Graph RAG",
                        "relatedArticleIds" to listOf(2L, 3L)
                    )
                )
            )
        )
        `when`(record.get("relatedArticles")).thenReturn(
            Values.value(
                listOf(
                    mapOf(
                        "articleId" to 2L,
                        "title" to "Target article",
                        "relationType" to "RELATED",
                        "reason" to "Both explain graph-aware retrieval.",
                        "sharedTopics" to listOf("Graph RAG")
                    )
                )
            )
        )
        val indexer = Neo4jArticleGraphProjectionIndexer(driver)

        val context = indexer.findContext(1L)

        assertNotNull(context)
        assertEquals(1L, context.articleId)
        assertEquals(
            listOf(ArticleGraphTopicContextProjection("graph rag", "Graph RAG", listOf(2L, 3L))),
            context.topics
        )
        assertEquals(
            listOf(
                ArticleGraphRelatedArticleProjection(
                    articleId = 2L,
                    title = "Target article",
                    relationType = "RELATED",
                    reason = "Both explain graph-aware retrieval.",
                    sharedTopics = listOf("Graph RAG")
                )
            ),
            context.relatedArticles
        )
        val cypherCaptor = ArgumentCaptor.forClass(String::class.java)
        verify(session).run(cypherCaptor.capture(), anyMap())
        assertTrue(cypherCaptor.value.contains("articleTopic.position AS position"))
        assertTrue(cypherCaptor.value.contains("ORDER BY position ASC, displayName ASC"))
        verify(session).close()
    }

    private fun sourceDocument(): ArticleGraphProjectionDocument =
        ArticleGraphProjectionDocument(
            articleId = 1L,
            title = "Source article",
            source = "OpenAI",
            url = "https://example.com/articles/1",
            publishedAt = "2026-06-03T00:00:00Z",
            eventType = "OFFICIAL_ANNOUNCEMENT",
            primaryCategory = "AI",
            importanceScore = 80,
            topics = listOf(
                ArticleGraphTopicDocument(name = "graph rag", displayName = "Graph RAG", position = 0),
                ArticleGraphTopicDocument(name = "graph rag", displayName = "Graph RAG", position = 0),
                ArticleGraphTopicDocument(name = "knowledge graph", displayName = "Knowledge Graph", position = 1)
            ),
            outgoingRelations = listOf(
                ArticleGraphRelationDocument(
                    sourceArticleId = 1L,
                    targetArticleId = 2L,
                    relationType = "RELATED",
                    reason = "Both explain graph-aware retrieval."
                ),
                ArticleGraphRelationDocument(
                    sourceArticleId = 1L,
                    targetArticleId = 2L,
                    relationType = "RELATED",
                    reason = "Both explain graph-aware retrieval."
                )
            )
        )

    private fun targetDocument(): ArticleGraphProjectionDocument =
        ArticleGraphProjectionDocument(
            articleId = 2L,
            title = "Target article",
            source = "OpenAI",
            url = "https://example.com/articles/2",
            publishedAt = "2026-06-03T00:00:00Z",
            eventType = "OFFICIAL_ANNOUNCEMENT",
            primaryCategory = "AI",
            importanceScore = 75,
            topics = listOf(
                ArticleGraphTopicDocument(name = "graph rag", displayName = "Graph RAG", position = 0)
            ),
            outgoingRelations = emptyList()
        )

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyTransactionCallback(): TransactionCallback<T> {
        any(TransactionCallback::class.java)
        return uninitialized()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> uninitialized(): T = null as T
}
