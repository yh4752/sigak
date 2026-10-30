package com.sigak.article.summary

import com.sigak.ai.config.AiServerProperties
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class SummaryHttpClientTest {
    @Test fun `lost response never repeats the paid post`() {
        val calls=AtomicInteger()
        val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        server.createContext("/api/enrichment/article") { exchange ->
            exchange.requestBody.readAllBytes()
            calls.incrementAndGet()
            exchange.close()
        }
        server.start()
        try {
            val client=SummaryConfig().summaryProviderClient(AiServerProperties("http://127.0.0.1:${server.address.port}"))
            assertFails { client.enrich(SummaryInput("Title","Source","https://example.org","2026-10-30T00:00:00Z",emptyList(),"Text","FEED_EXCERPT")) }
            assertEquals(1,calls.get())
        } finally { server.stop(0) }
    }
}
