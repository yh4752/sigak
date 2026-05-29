package com.sigak.search.vector

import com.sigak.search.config.SearchInfrastructureProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import org.hamcrest.Matchers.containsString
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class QdrantArticleVectorProjectionIndexerTest {

    private val qdrantClientBuilder = RestClient.builder().baseUrl("http://qdrant:6333")
    private val qdrantServer = MockRestServiceServer.bindTo(qdrantClientBuilder).build()
    private val qdrantClient = qdrantClientBuilder.build()
    private val properties = SearchInfrastructureProperties(
        qdrant = SearchInfrastructureProperties.Qdrant(
            url = "http://qdrant:6333",
            articleCollectionName = "test-article-vectors",
            distance = "Cosine"
        )
    )
    private val indexer = QdrantArticleVectorProjectionIndexer(
        qdrantClient = qdrantClient,
        properties = properties
    )

    @Test
    fun recreatesCollectionAndUpsertsArticleVectorPoints() {
        qdrantServer.expect(ExpectedCount.once(), requestTo("http://qdrant:6333/collections/test-article-vectors"))
            .andExpect(method(HttpMethod.DELETE))
            .andRespond(withSuccess("""{"result":true}""", MediaType.APPLICATION_JSON))
        qdrantServer.expect(ExpectedCount.once(), requestTo("http://qdrant:6333/collections/test-article-vectors"))
            .andExpect(method(HttpMethod.PUT))
            .andExpect(content().string(containsString(""""size":3""")))
            .andExpect(content().string(containsString(""""distance":"Cosine"""")))
            .andRespond(withSuccess("""{"result":true}""", MediaType.APPLICATION_JSON))
        qdrantServer.expect(
            ExpectedCount.once(),
            requestTo("http://qdrant:6333/collections/test-article-vectors/points?wait=true")
        )
            .andExpect(method(HttpMethod.PUT))
            .andExpect(content().string(containsString(""""id":17""")))
            .andExpect(content().string(containsString(""""articleId":17""")))
            .andExpect(content().string(containsString(""""embeddingModelName":"all-MiniLM-L6-v2"""")))
            .andRespond(withSuccess("""{"result":{"operation_id":1,"status":"completed"}}""", MediaType.APPLICATION_JSON))

        indexer.recreateCollection(dimension = 3)
        indexer.upsertAll(
            listOf(
                ArticleVectorDocument(
                    id = 17,
                    vector = listOf(0.1, 0.2, 0.3),
                    payload = ArticleVectorPayload(
                        articleId = 17,
                        title = "Graph RAG Indexing Reaches MVP",
                        source = "Sigak Research",
                        url = "https://example.com/articles/graph-rag-indexing",
                        publishedAt = "2026-05-12T09:00:00Z",
                        eventType = "PRODUCT",
                        primaryCategory = "AI",
                        topics = listOf("graph rag", "vector search"),
                        importanceScore = 87,
                        relatedArticleIds = listOf(3, 5),
                        embeddingProvider = "mock",
                        embeddingModelName = "all-MiniLM-L6-v2",
                        embeddingDimension = 3
                    )
                )
            )
        )

        qdrantServer.verify()
    }

    @Test
    fun ignoresMissingCollectionWhenRecreatingCollection() {
        qdrantServer.expect(ExpectedCount.once(), requestTo("http://qdrant:6333/collections/test-article-vectors"))
            .andExpect(method(HttpMethod.DELETE))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))
        qdrantServer.expect(ExpectedCount.once(), requestTo("http://qdrant:6333/collections/test-article-vectors"))
            .andExpect(method(HttpMethod.PUT))
            .andExpect(content().string(containsString(""""size":3""")))
            .andExpect(content().string(containsString(""""distance":"Cosine"""")))
            .andRespond(withSuccess("""{"result":true}""", MediaType.APPLICATION_JSON))

        indexer.recreateCollection(dimension = 3)

        qdrantServer.verify()
    }

    @Test
    fun skipsUpsertRequestWhenDocumentsAreEmpty() {
        indexer.upsertAll(emptyList())

        qdrantServer.verify()
    }

    @Test
    fun searchesArticleVectorPoints() {
        qdrantServer.expect(
            ExpectedCount.once(),
            requestTo("http://qdrant:6333/collections/test-article-vectors/points/search")
        )
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().string(containsString(""""vector":[0.1,0.2,0.3]""")))
            .andExpect(content().string(containsString(""""limit":2""")))
            .andExpect(content().string(containsString(""""with_payload":true""")))
            .andExpect(content().string(containsString(""""with_vector":false""")))
            .andRespond(
                withSuccess(
                    """
                    {
                      "status": "ok",
                      "time": 0.001,
                      "result": [
                        {
                          "id": 21,
                          "score": 0.97,
                          "version": 3,
                          "payload": {
                            "articleId": 7
                          }
                        },
                        {
                          "id": 8,
                          "score": 0.83,
                          "version": 4,
                          "payload": {}
                        }
                      ]
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON
                )
            )

        val hits = indexer.search(vector = listOf(0.1, 0.2, 0.3), limit = 2)

        assertEquals(
            listOf(
                ArticleVectorSearchHit(articleId = 7, score = 0.97),
                ArticleVectorSearchHit(articleId = 8, score = 0.83)
            ),
            hits
        )
        qdrantServer.verify()
    }
}
