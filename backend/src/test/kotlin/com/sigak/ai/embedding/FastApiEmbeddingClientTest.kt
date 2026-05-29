package com.sigak.ai.embedding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun rejectsBlankTextBeforeCallingFastApi() {
        assertFailsWith<IllegalArgumentException> {
            client.embedText("   ")
        }

        aiServer.verify()
    }
}
