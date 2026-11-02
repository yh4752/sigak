package com.sigak.common.http

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.security.cert.X509Certificate
import javax.net.ssl.SSLException
import org.apache.hc.client5.http.ssl.DefaultHostnameVerifier
import org.apache.hc.client5.http.socket.LayeredConnectionSocketFactory
import org.apache.hc.core5.http.HttpHost
import org.apache.hc.core5.http.protocol.HttpContext
import org.apache.hc.core5.util.TimeValue
import org.mockito.Mockito
import kotlin.test.*

class PinnedHttpsTransportTest {
    private val ip = InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8))

    @Test fun `actual Apache connection receives pinned IP and original TLS host without system lookup`() {
        val factory = RecordingSocketFactory("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: 4\r\nConnection: close\r\n\r\nbody")
        val transport = ApachePinnedHttpsTransport(factory)
        transport.open(PinnedRequest(URI("https://not-in-public-dns.invalid/document"), ip, 1000), FetchCancellation()).use { response ->
            assertEquals(200, response.status)
            assertEquals("body", response.body.readBytes().toString(Charsets.UTF_8))
        }
        assertEquals(ip, factory.remoteAddresses.single().address)
        assertEquals(443, factory.remoteAddresses.single().port)
        assertEquals("not-in-public-dns.invalid", factory.tlsHosts.single())
        assertTrue(factory.output.toString(Charsets.ISO_8859_1).contains("Host: not-in-public-dns.invalid"))
        assertTrue(factory.output.toString(Charsets.ISO_8859_1).contains("Accept-Encoding: identity"))
    }

    @Test fun `Apache does not retry when peer closes without any response`() {
        val factory = RecordingSocketFactory("")
        val transport = ApachePinnedHttpsTransport(factory)
        assertFails {
            transport.open(PinnedRequest(URI("https://not-in-public-dns.invalid/document"), ip, 1000), FetchCancellation())
        }
        assertEquals(1, factory.remoteAddresses.size)
    }

    @Test fun `pinned resolver never resolves another host or reverse DNS`() {
        val resolver = PinnedDnsResolver("public.example.test", ip)
        assertEquals(listOf(ip), resolver.resolve("public.example.test").toList())
        assertEquals("public.example.test", resolver.resolveCanonicalHostname("public.example.test"))
        assertFailsWith<java.net.UnknownHostException> { resolver.resolve("other.example.test") }
        assertFailsWith<java.net.UnknownHostException> { resolver.resolveCanonicalHostname("other.example.test") }
    }

    @Test fun `default TLS verifier validates original hostname rather than pinned IP`() {
        val certificate = Mockito.mock(X509Certificate::class.java)
        Mockito.`when`(certificate.subjectAlternativeNames).thenReturn(listOf(listOf(2, "public.example.test")))
        DefaultHostnameVerifier().verify("public.example.test", certificate)
        assertFailsWith<SSLException> { DefaultHostnameVerifier().verify("8.8.8.8", certificate) }
        assertFailsWith<SSLException> { DefaultHostnameVerifier().verify("other.example.test", certificate) }
    }

    @Test fun `HTTP429 preserves safe bounded Retry After delta or date for source cooldown`() {
        for ((header, expected) in listOf(" 120 " to "120", "Wed, 21 Oct 2026 07:28:00 GMT" to "Wed, 21 Oct 2026 07:28:00 GMT",
            "x".repeat(129) to null, "bad\u0001value" to null)) {
            val wire = "HTTP/1.1 429 Too Many Requests\r\nContent-Type: text/xml\r\nRetry-After: $header\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
            val client = SafePublicDocumentClient({ arrayOf(ip) }, ApachePinnedHttpsTransport(RecordingSocketFactory(wire)), 1000)
            try {
                val failure = assertFailsWith<ExternalFetchException> {
                    client.fetch("https://public.example.test/feed", setOf("public.example.test"))
                }
                assertEquals("HTTP_429", failure.code)
                assertEquals(expected, failure.retryAfter)
            } finally { client.close() }
        }
    }

    private class RecordingSocketFactory(private val response: String) : LayeredConnectionSocketFactory {
        val remoteAddresses = mutableListOf<InetSocketAddress>()
        val tlsHosts = mutableListOf<String>()
        val output = ByteArrayOutputStream()
        override fun createSocket(context: HttpContext?): Socket = memorySocket()
        override fun connectSocket(timeout: TimeValue?, socket: Socket?, host: HttpHost,
            remote: InetSocketAddress, local: InetSocketAddress?, context: HttpContext?): Socket {
            remoteAddresses.add(remote)
            tlsHosts.add(host.hostName)
            return memorySocket()
        }
        override fun createLayeredSocket(socket: Socket, target: String, port: Int, context: HttpContext?): Socket {
            tlsHosts.add(target)
            return socket
        }
        private fun memorySocket(): Socket = object : Socket() {
            private val input = ByteArrayInputStream(response.toByteArray(Charsets.ISO_8859_1))
            private var timeout = 0
            override fun getInputStream() = input
            override fun getOutputStream() = output
            override fun isConnected() = true
            override fun setSoTimeout(value: Int) { timeout = value }
            override fun getSoTimeout() = timeout
            override fun setTcpNoDelay(value: Boolean) = Unit
            override fun setKeepAlive(value: Boolean) = Unit
        }
    }
}
