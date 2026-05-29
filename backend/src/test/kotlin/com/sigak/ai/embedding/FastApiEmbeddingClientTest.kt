package com.sigak.ai.embedding

import com.sigak.ai.config.AiServerConfig
import com.sigak.ai.config.AiServerProperties
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import org.hamcrest.Matchers.containsString
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class FastApiEmbeddingClientTest {

    private val aiServerClientBuilder = RestClient.builder().baseUrl("http://ai-server:8000")
    private val aiServer = MockRestServiceServer.bindTo(aiServerClientBuilder).build()
    private val aiServerClient = aiServerClientBuilder.build()
    private val client = FastApiEmbeddingClient(aiServerClient)

    @Test
    fun embedsTextThroughFastApiEmbeddingEndpoint() {
        aiServer.expect(ExpectedCount.once(), requestTo("http://ai-server:8000/api/embeddings/text"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().string(containsString("Graph RAG failure modes")))
            .andRespond(
                withSuccess(
                    """
                    {
                      "provider": "local",
                      "modelName": "sigak-deterministic-hash-v1",
                      "dimension": 8,
                      "embedding": [0.1, -0.2, 0.3, -0.4, 0.5, -0.6, 0.7, -0.8]
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON
                )
            )

        val response = client.embedText("Graph RAG failure modes")

        assertEquals("local", response.provider)
        assertEquals("sigak-deterministic-hash-v1", response.modelName)
        assertEquals(8, response.dimension)
        assertEquals(listOf(0.1, -0.2, 0.3, -0.4, 0.5, -0.6, 0.7, -0.8), response.embedding)
        aiServer.verify()
    }

    @Test
    fun sendsJsonBodyWhenCallingRealHttpServer() {
        val capturedContentType = AtomicReference<String>()
        val capturedBody = AtomicReference<String>()
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/api/embeddings/text") { exchange ->
            capturedContentType.set(exchange.requestHeaders.getFirst("Content-Type"))
            capturedBody.set(exchange.requestBody.bufferedReader().use { it.readText() })

            val responseBody = """
                {
                  "provider": "local",
                  "modelName": "sigak-deterministic-hash-v1",
                  "dimension": 2,
                  "embedding": [0.1, -0.1]
                }
            """.trimIndent().toByteArray()

            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, responseBody.size.toLong())
            exchange.responseBody.use { it.write(responseBody) }
        }
        server.start()

        try {
            val client = FastApiEmbeddingClient(RestClient.builder().baseUrl("http://localhost:${server.address.port}").build())

            client.embedText("multilingual article text")

            assertEquals("application/json", capturedContentType.get())
            assertEquals("""{"text":"multilingual article text"}""", capturedBody.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun configuredAiServerClientDoesNotRequestHttp2Upgrade() {
        val capturedUpgrade = AtomicReference<String>()
        val capturedBody = AtomicReference<String>()
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/api/embeddings/text") { exchange ->
            capturedUpgrade.set(exchange.requestHeaders.getFirst("Upgrade"))
            capturedBody.set(exchange.requestBody.bufferedReader().use { it.readText() })

            val responseBody = """
                {
                  "provider": "local",
                  "modelName": "sigak-deterministic-hash-v1",
                  "dimension": 2,
                  "embedding": [0.1, -0.1]
                }
            """.trimIndent().toByteArray()

            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, responseBody.size.toLong())
            exchange.responseBody.use { it.write(responseBody) }
        }
        server.start()

        try {
            val restClient = AiServerConfig(AiServerProperties(url = "http://localhost:${server.address.port}"))
                .aiServerRestClient()
            val client = FastApiEmbeddingClient(restClient)

            client.embedText("FastAPI should receive this body")

            assertNull(capturedUpgrade.get())
            assertEquals("""{"text":"FastAPI should receive this body"}""", capturedBody.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun rejectsBlankTextBeforeCallingFastApi() {
        assertFailsWith<IllegalArgumentException> {
            client.embedText("   ")
        }

        aiServer.verify()
    }
}
