package com.sigak.common.http

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.InetAddress
import java.nio.charset.Charset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class PublicDocumentClientTest {
    private val ip = InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8))
    private val hosts = setOf("public.example.test")
    private val start = "https://public.example.test/feed"

    @Test fun `validated DNS address is pinned and original hostname is preserved`() {
        val calls = mutableListOf<PinnedRequest>()
        val resolutions = AtomicInteger()
        val client = client(resolver = {
            resolutions.incrementAndGet()
            arrayOf(ip)
        }) { request, _ ->
            calls.add(request)
            response("<rss>closing evidence</rss>", "application/rss+xml; charset=UTF-8")
        }
        try {
            val result = client.fetch(start, hosts)
            assertEquals("<rss>closing evidence</rss>", result.text)
            assertEquals(start, result.finalUrl)
            assertEquals(ip, calls.single().address)
            assertEquals("public.example.test", calls.single().uri.host)
            assertEquals(1, resolutions.get())
            assertTrue(result.durationMs >= 0)
        } finally { client.close() }
    }

    @Test fun `host suffix credentials nonstandard port and HTTP never resolve`() {
        val resolutions = AtomicInteger()
        val client = client(resolver = { resolutions.incrementAndGet(); arrayOf(ip) }) { _, _ -> error("must not transport") }
        try {
            for (url in listOf("https://public.example.test.evil.test/feed", "https://user@public.example.test/feed",
                "https://public.example.test:8443/feed", "http://public.example.test/feed", "https://localhost/feed")) {
                assertFailsWith<ExternalFetchException> { client.fetch(url, hosts) }
            }
            assertEquals(0, resolutions.get())
        } finally { client.close() }
    }

    @Test fun `all DNS answers must be public`() {
        val client = client(resolver = { arrayOf(ip, InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))) }) { _, _ -> error("must not transport") }
        try { assertEquals("PRIVATE_ADDRESS", assertFailsWith<ExternalFetchException> { client.fetch(start, hosts) }.code) }
        finally { client.close() }
    }

    @Test fun `same host redirects resolve and validate again but cannot cross hosts`() {
        val calls = mutableListOf<PinnedRequest>()
        val resolutions = AtomicInteger()
        val client = client(resolver = { resolutions.incrementAndGet(); arrayOf(ip) }) { request, _ ->
            calls.add(request)
            if (calls.size == 1) response(status = 302, location = "/final") else response("<html>final</html>")
        }
        try {
            assertEquals("https://public.example.test/final", client.fetch(start, hosts).finalUrl)
            assertEquals(2, resolutions.get())
        } finally { client.close() }
        val rejected = client { _, _ -> response(status = 302, location = "https://other.example.test/feed") }
        try { assertEquals("REDIRECT_REJECTED", assertFailsWith<ExternalFetchException> { rejected.fetch(start, hosts + "other.example.test") }.code) }
        finally { rejected.close() }
    }

    @Test fun `DNS rebind after redirect is rejected before second transport`() {
        val resolutions = AtomicInteger()
        val transports = AtomicInteger()
        val client = client(resolver = {
            if (resolutions.incrementAndGet() == 1) arrayOf(ip) else arrayOf(InetAddress.getByAddress(byteArrayOf(10, 0, 0, 1)))
        }) { _, _ -> transports.incrementAndGet(); response(status = 302, location = "/again") }
        try {
            assertEquals("PRIVATE_ADDRESS", assertFailsWith<ExternalFetchException> { client.fetch(start, hosts) }.code)
            assertEquals(1, transports.get())
        } finally { client.close() }
    }

    @Test fun `fourth redirect fails without a fifth request`() {
        val calls = AtomicInteger()
        val client = client { _, _ -> calls.incrementAndGet(); response(status = 307, location = "/again") }
        try {
            assertEquals("REDIRECT_REJECTED", assertFailsWith<ExternalFetchException> { client.fetch(start, hosts) }.code)
            assertEquals(4, calls.get())
        } finally { client.close() }
    }

    @Test fun `size limit uses actual streamed bytes and never returns a prefix`() {
        val client = client { _, _ -> response("x".repeat(2 * 1024 * 1024 + 1), length = -1) }
        try { assertEquals("RESPONSE_TOO_LARGE", assertFailsWith<ExternalFetchException> { client.fetch(start, hosts) }.code) }
        finally { client.close() }
        val exact = client { _, _ -> response("x".repeat(2 * 1024 * 1024), length = -1) }
        try { assertEquals(2 * 1024 * 1024, exact.fetch(start, hosts).text.length) }
        finally { exact.close() }
    }

    @Test fun `advertised oversize and compression reject before reading stream`() {
        for ((length, encoding, expected) in listOf(Triple(2097153L, null, "RESPONSE_TOO_LARGE"), Triple(-1L, "gzip", "COMPRESSED_RESPONSE"))) {
            val client = client { _, _ -> PinnedResponse(200, "text/html", encoding, null, length,
                object : InputStream() { override fun read(): Int = error("must not read") }) }
            try { assertEquals(expected, assertFailsWith<ExternalFetchException> { client.fetch(start, hosts) }.code) }
            finally { client.close() }
        }
    }

    @Test fun `safe HTTP status codes survive without response body details or retries`() {
        for (status in listOf(401, 403, 404, 429, 500)) {
            val calls = AtomicInteger()
            val client = client { _, _ -> calls.incrementAndGet(); response("private diagnostic body", status = status) }
            try {
                val failure = assertFailsWith<ExternalFetchException> { client.fetch(start, hosts) }
                assertEquals("HTTP_$status", failure.code)
                assertFalse(failure.message.orEmpty().contains("private"))
                assertEquals(1, calls.get())
            } finally { client.close() }
        }
    }

    @Test fun `XML declaration and HTTP charset decode full text correctly`() {
        val xml = "<?xml version='1.0' encoding='EUC-KR'?><feed>마지막 근거</feed>"
        val xmlClient = client { _, _ -> PinnedResponse(200, "application/atom+xml", null, null, -1,
            ByteArrayInputStream(xml.toByteArray(Charset.forName("EUC-KR")))) }
        try { assertTrue(xmlClient.fetch(start, hosts).text.contains("마지막 근거")) }
        finally { xmlClient.close() }
        val html = "<article>끝 문단</article>"
        val htmlClient = client { _, _ -> PinnedResponse(200, "text/html; charset=EUC-KR", null, null, -1,
            ByteArrayInputStream(html.toByteArray(Charset.forName("EUC-KR")))) }
        try { assertEquals(html, htmlClient.fetch(start, hosts).text) }
        finally { htmlClient.close() }
    }

    @Test fun `total deadline includes DNS and cancels in flight request`() {
        val entered = CountDownLatch(1)
        val client = client(timeout = 50, resolver = { entered.countDown(); Thread.sleep(1000); arrayOf(ip) }) { _, _ -> error("must not connect") }
        val started = System.nanoTime()
        try {
            assertEquals("FETCH_TIMEOUT", assertFailsWith<ExternalFetchException> { client.fetch(start, hosts) }.code)
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 500)
        } finally { client.close() }
        val cancelled = CountDownLatch(1)
        val transportClient = client(timeout = 50) { _, cancellation ->
            cancellation.register { cancelled.countDown() }
            Thread.sleep(1000)
            response()
        }
        try {
            assertEquals("FETCH_TIMEOUT", assertFailsWith<ExternalFetchException> { transportClient.fetch(start, hosts) }.code)
            assertTrue(cancelled.await(1, TimeUnit.SECONDS))
        } finally { transportClient.close() }
    }

    @Test fun `transport failure is sanitized and shutdown rejects new work`() {
        val calls = AtomicInteger()
        val client = client { _, _ -> calls.incrementAndGet(); throw java.io.IOException("private address and response details") }
        try {
            assertEquals("FETCH_FAILED", assertFailsWith<ExternalFetchException> { client.fetch(start, hosts) }.code)
            assertEquals(1, calls.get())
        } finally { client.close() }
        assertEquals("FETCH_CLOSED", assertFailsWith<ExternalFetchException> { client.fetch(start, hosts) }.code)
    }

    @Test fun `public WordPress IP is allowed while local metadata and reserved ranges are blocked`() {
        assertTrue(PublicUrlPolicy.publicAddress(InetAddress.getByName("192.0.66.2")))
        for (address in listOf("0.0.0.0", "127.0.0.1", "10.0.0.1", "100.64.1.1", "169.254.169.254", "172.16.0.1",
            "192.168.1.1", "192.0.0.1", "192.0.2.1", "192.88.99.1", "198.18.1.1", "198.51.100.1", "203.0.113.1",
            "224.0.0.1", "240.1.2.3", "::1", "fc00::1", "fe80::1", "2001:db8::1", "2002::1", "3fff::1")) {
            assertFalse(PublicUrlPolicy.publicAddress(InetAddress.getByName(address)), address)
        }
        assertEquals("https://public.example.test/feed?a=1", PublicUrlPolicy.canonical("https://PUBLIC.EXAMPLE.TEST:443/feed?a=1#part"))
    }

    @Test fun `HTML meta charset and UTF16 XML BOM are honored without changing original markup`() {
        val html = "<html><head><meta charset='EUC-KR'></head><body><p>마지막 문단</p></body></html>"
        val htmlClient = client { _, _ -> PinnedResponse(200, "text/html", null, null, -1,
            ByteArrayInputStream(html.toByteArray(Charset.forName("EUC-KR")))) }
        try { assertEquals(html, htmlClient.fetch(start, hosts).text) }
        finally { htmlClient.close() }
        val xml = "<?xml version='1.0' encoding='UTF-16'?><feed>마지막 문단</feed>"
        val xmlClient = client { _, _ -> PinnedResponse(200, "application/atom+xml", null, null, -1,
            ByteArrayInputStream(xml.toByteArray(Charsets.UTF_16))) }
        try { assertEquals(xml, xmlClient.fetch(start, hosts).text) }
        finally { xmlClient.close() }
    }

    @Test fun `body deadline is total and closes the response instead of returning partial text`() {
        val aborted = CountDownLatch(1)
        val client = client(timeout = 100) { _, cancellation ->
            cancellation.register { aborted.countDown() }
            PinnedResponse(200, "text/html", null, null, -1, object : InputStream() {
                override fun read(): Int { Thread.sleep(30); return 'x'.code }
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    buffer[offset] = read().toByte()
                    return 1
                }
            })
        }
        try {
            assertEquals("FETCH_TIMEOUT", assertFailsWith<ExternalFetchException> { client.fetch(start, hosts) }.code)
            assertTrue(aborted.await(1, TimeUnit.SECONDS))
        } finally { client.close() }
    }

    @Test fun `rejected response closes transport before stream cleanup can drain bytes`() {
        var closed = false
        var streamSawClosed = false
        val response = PinnedResponse(200, "text/html", null, null, 2097153,
            object : InputStream() {
                override fun read() = error("must not drain")
                override fun close() { streamSawClosed = closed }
            }, { closed = true })
        val client = client { _, _ -> response }
        try {
            assertEquals("RESPONSE_TOO_LARGE", assertFailsWith<ExternalFetchException> { client.fetch(start, hosts) }.code)
            assertTrue(closed)
            assertTrue(streamSawClosed)
        } finally { client.close() }
    }

    @Test fun `shutdown cancels active transport and interrupts its worker`() {
        val entered = CountDownLatch(1)
        val aborted = CountDownLatch(1)
        val caller = java.util.concurrent.Executors.newSingleThreadExecutor()
        val client = client(timeout = 1000) { _, cancellation ->
            cancellation.register { aborted.countDown() }
            entered.countDown()
            Thread.sleep(1000)
            response()
        }
        try {
            val future = caller.submit { runCatching { client.fetch(start, hosts) } }
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            client.close()
            assertTrue(aborted.await(1, TimeUnit.SECONDS))
            future.get(1, TimeUnit.SECONDS)
        } finally { client.close(); caller.shutdownNow() }
    }

    private fun client(timeout: Long = 1000, resolver: (String) -> Array<InetAddress> = { arrayOf(ip) },
        transport: (PinnedRequest, FetchCancellation) -> PinnedResponse) =
        SafePublicDocumentClient(resolver, PinnedDocumentTransport(transport), timeout)

    private fun response(text: String = "<html>document</html>", type: String = "text/html", status: Int = 200,
        location: String? = null, length: Long = -1) =
        PinnedResponse(status, type, null, location, length, ByteArrayInputStream(text.toByteArray()))
}
