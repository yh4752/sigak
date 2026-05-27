package com.sigak.search.service

import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.AfterEach
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.mock
import org.neo4j.driver.Driver
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class SearchInfrastructureHealthServiceTest {

    private val elasticsearchClientBuilder = RestClient.builder().baseUrl("http://elasticsearch:9200")
    private val qdrantClientBuilder = RestClient.builder().baseUrl("http://qdrant:6333")
    private val elasticsearchServer = MockRestServiceServer.bindTo(elasticsearchClientBuilder).build()
    private val qdrantServer = MockRestServiceServer.bindTo(qdrantClientBuilder).build()
    private val elasticsearchClient = elasticsearchClientBuilder.build()
    private val qdrantClient = qdrantClientBuilder.build()
    private val neo4jDriver = mock(Driver::class.java)

    @AfterEach
    fun tearDown() {
        elasticsearchServer.verify()
        qdrantServer.verify()
    }

    @Test
    fun reportsAllSearchStoresAsReadyWhenHealthChecksSucceed() {
        elasticsearchServer.expect(ExpectedCount.once(), requestTo("http://elasticsearch:9200/_cluster/health"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""{"status":"green"}""", MediaType.APPLICATION_JSON))
        qdrantServer.expect(ExpectedCount.once(), requestTo("http://qdrant:6333/healthz"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""{"title":"qdrant"}""", MediaType.APPLICATION_JSON))
        doNothing().`when`(neo4jDriver).verifyConnectivity()

        val service = SearchInfrastructureHealthService(
            elasticsearchClient = elasticsearchClient,
            qdrantClient = qdrantClient,
            neo4jDriver = neo4jDriver
        )

        val response = service.checkHealth()

        assertEquals("ready", response.overallStatus)
        assertEquals("ready", response.elasticsearch.status)
        assertEquals("ready", response.qdrant.status)
        assertEquals("ready", response.neo4j.status)
    }
}
