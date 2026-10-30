package com.sigak.article.fulltext

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class ArticleBodyFetcherTest {
    private val html = "<article><p>" + "Opening evidence. ".repeat(20) + "</p><p>Final evidence in the closing paragraph.</p></article>"
    private val publicIp = InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8))

    @Test fun `valid source fetch returns complete text and final url`() {
        val connection = StubConnection("https://openai.com/index/example", body = html)
        val fetcher = fetcher { connection }
        try {
            val result = fetcher.fetch(connection.url.toString())
            assertTrue(result.text.contains("Opening evidence"))
            assertTrue(result.text.contains("Final evidence"))
            assertEquals(connection.url.toString(), result.finalUrl)
            assertTrue(result.durationMs >= 0)
            assertFalse(connection.instanceFollowRedirects)
            assertTrue(connection.disconnected)
        } finally { fetcher.close() }
    }

    @Test fun `unapproved url never resolves or opens a connection`() {
        val calls = AtomicInteger()
        val fetcher = HttpArticleBodyFetcher(ArticleBodyExtractor(), {
            calls.incrementAndGet(); arrayOf(publicIp)
        }, { calls.incrementAndGet(); error("must not connect") }, 1000)
        try {
            assertEquals("SOURCE_NOT_SUPPORTED", assertFailsWith<FullTextException> {
                fetcher.fetch("https://evil.test/article")
            }.code)
            assertEquals(0, calls.get())
        } finally { fetcher.close() }
    }

    @Test fun `mixed public and private DNS answers never open a connection`() {
        val fetcher = HttpArticleBodyFetcher(ArticleBodyExtractor(), {
            arrayOf(publicIp, InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))
        }, { error("must not connect") }, 1000)
        try {
            assertEquals("PRIVATE_ADDRESS", assertFailsWith<FullTextException> {
                fetcher.fetch("https://openai.com/index/example")
            }.code)
        } finally { fetcher.close() }
    }

    @Test fun `redirects preserve source host and revalidate DNS every hop`() {
        val calls = mutableListOf<String>()
        val resolved = AtomicInteger()
        val fetcher = HttpArticleBodyFetcher(ArticleBodyExtractor(), {
            resolved.incrementAndGet(); arrayOf(publicIp)
        }, { url ->
            calls.add(url.toString())
            if (url.path == "/start") StubConnection(url.toString(), 302, headers = mapOf("location" to "/finish"))
            else StubConnection(url.toString(), body = html)
        }, 1000)
        try {
            val result = fetcher.fetch("https://openai.com/start")
            assertEquals("https://openai.com/finish", result.finalUrl)
            assertEquals(2, calls.size)
            assertEquals(2, resolved.get())
        } finally { fetcher.close() }
    }

    @Test fun `cross source redirects are rejected before opening target`() {
        val calls = AtomicInteger()
        val fetcher = fetcher {
            calls.incrementAndGet()
            StubConnection(it.toString(), 302, headers = mapOf("location" to "https://blog.google/article"))
        }
        try {
            assertEquals("REDIRECT_REJECTED", assertFailsWith<FullTextException> {
                fetcher.fetch("https://openai.com/start")
            }.code)
            assertEquals(1, calls.get())
        } finally { fetcher.close() }
    }

    @Test fun `more than three redirects fail without a fifth request`() {
        val calls = AtomicInteger()
        val fetcher = fetcher {
            calls.incrementAndGet()
            StubConnection(it.toString(), 302, headers = mapOf("location" to "/again"))
        }
        try {
            assertEquals("REDIRECT_REJECTED", assertFailsWith<FullTextException> {
                fetcher.fetch("https://openai.com/start")
            }.code)
            assertEquals(4, calls.get())
        } finally { fetcher.close() }
    }

    @Test fun `oversized content length rejects before reading a prefix`() {
        val connection = StubConnection("https://openai.com/example", body = html,
            headers = mapOf("content-length" to "2097153"))
        val fetcher = fetcher { connection }
        try {
            assertEquals("HTML_TOO_LARGE", assertFailsWith<FullTextException> { fetcher.fetch(connection.url.toString()) }.code)
            assertFalse(connection.readStarted)
        } finally { fetcher.close() }
    }

    @Test fun `stream exceeding limit rejects instead of extracting a prefix`() {
        val connection = StubConnection("https://openai.com/example", body = html + "x".repeat(2097152))
        val fetcher = fetcher { connection }
        try {
            assertEquals("HTML_TOO_LARGE", assertFailsWith<FullTextException> { fetcher.fetch(connection.url.toString()) }.code)
        } finally { fetcher.close() }
    }

    @Test fun `total deadline includes DNS and never returns a partial result`() {
        val fetcher = HttpArticleBodyFetcher(ArticleBodyExtractor(), {
            Thread.sleep(1000); arrayOf(publicIp)
        }, { StubConnection(it.toString(), body = html) }, 30)
        val started = System.nanoTime()
        try {
            assertEquals("FETCH_TIMEOUT", assertFailsWith<FullTextException> { fetcher.fetch("https://openai.com/example") }.code)
            assertTrue((System.nanoTime() - started) / 1000000 < 500)
        } finally { fetcher.close() }
    }

    @Test fun `provider error details are not exposed`() {
        val fetcher = fetcher { throw java.io.IOException("private network details") }
        try {
            val failure = assertFailsWith<FullTextException> { fetcher.fetch("https://openai.com/example") }
            assertEquals("FETCH_FAILED", failure.code)
            assertFalse(failure.message.orEmpty().contains("private"))
        } finally { fetcher.close() }
    }

    @Test fun `nonpublic address ranges are blocked`() {
        for (ip in listOf("0.1.2.3", "10.0.0.1", "100.64.0.1", "127.0.0.1", "169.254.169.254",
            "172.16.0.1", "192.168.0.1", "198.18.0.1", "224.0.0.1", "::1", "fc00::1", "fe80::1", "2001:db8::1")) {
            assertFalse(ArticleBodyPolicy.publicAddress(InetAddress.getByName(ip)), ip)
        }
        assertTrue(ArticleBodyPolicy.publicAddress(publicIp))
        assertTrue(ArticleBodyPolicy.publicAddress(InetAddress.getByName("2606:4700::1111")))
    }

    private fun fetcher(factory: (URL) -> HttpURLConnection) =
        HttpArticleBodyFetcher(ArticleBodyExtractor(), { arrayOf(publicIp) }, factory, 1000)

    private class StubConnection(
        address: String,
        private val status: Int = 200,
        private val body: String = "",
        private val headers: Map<String, String> = emptyMap()
    ) : HttpURLConnection(URL(address)) {
        var disconnected = false
        var readStarted = false
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getHeaderField(name: String): String? = headers[name.lowercase()]
            ?: if (name.equals("content-type", true)) "text/html; charset=UTF-8" else null
        override fun getInputStream(): InputStream {
            readStarted = true
            return ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
        }
    }
}
