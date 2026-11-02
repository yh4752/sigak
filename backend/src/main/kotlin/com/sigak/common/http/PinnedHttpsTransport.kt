package com.sigak.common.http

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.apache.hc.client5.http.DnsResolver
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.config.ConnectionConfig
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder
import org.apache.hc.client5.http.socket.LayeredConnectionSocketFactory
import org.apache.hc.client5.http.ssl.DefaultHostnameVerifier
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory
import org.apache.hc.core5.io.CloseMode
import org.apache.hc.core5.ssl.SSLContexts
import org.apache.hc.core5.util.Timeout

internal data class PinnedRequest(val uri: URI, val address: InetAddress, val timeoutMillis: Int)

internal class PinnedResponse(val status: Int, val contentType: String?, val contentEncoding: String?,
    val location: String?, val contentLength: Long, val body: InputStream,
    private val closeAction: () -> Unit = {}, val retryAfter: String? = null) : AutoCloseable {
    // 거절한 큰 응답을 close 중에 끝까지 drain하지 않도록 연결부터 즉시 닫는다.
    override fun close() { runCatching { closeAction() }; runCatching { body.close() } }
}

internal fun interface PinnedDocumentTransport {
    fun open(request: PinnedRequest, cancellation: FetchCancellation): PinnedResponse
}

internal class FetchCancellation {
    private val cancelled = AtomicBoolean(false)
    private val action = AtomicReference<(() -> Unit)?>(null)
    fun register(onCancel: () -> Unit) {
        action.set(onCancel)
        if (cancelled.get()) runCatching { onCancel() }
    }
    fun cancel() {
        cancelled.set(true)
        action.get()?.let { runCatching { it() } }
    }
}

internal class PinnedDnsResolver(private val host: String, private val address: InetAddress) : DnsResolver {
    override fun resolve(name: String): Array<InetAddress> {
        if (!name.equals(host, true)) throw UnknownHostException("Unapproved connection host")
        return arrayOf(address)
    }
    override fun resolveCanonicalHostname(name: String): String {
        if (!name.equals(host, true)) throw UnknownHostException("Unapproved connection host")
        return host
    }
}

internal class ApachePinnedHttpsTransport(
    private val socketFactory: LayeredConnectionSocketFactory =
        SSLConnectionSocketFactory(SSLContexts.createSystemDefault(), DefaultHostnameVerifier())
) : PinnedDocumentTransport {
    override fun open(request: PinnedRequest, cancellation: FetchCancellation): PinnedResponse {
        val timeout = Timeout.ofMilliseconds(request.timeoutMillis.toLong())
        val manager = PoolingHttpClientConnectionManagerBuilder.create()
            .setDnsResolver(PinnedDnsResolver(request.uri.host, request.address))
            .setSSLSocketFactory(socketFactory)
            .setMaxConnTotal(1).setMaxConnPerRoute(1)
            .setDefaultConnectionConfig(ConnectionConfig.custom().setConnectTimeout(timeout).setSocketTimeout(timeout).build())
            .build()
        val client = HttpClients.custom().setConnectionManager(manager)
            .disableAutomaticRetries().disableRedirectHandling().disableContentCompression().disableCookieManagement()
            .setDefaultRequestConfig(RequestConfig.custom().setResponseTimeout(timeout).setConnectionRequestTimeout(timeout)
                .setAuthenticationEnabled(false).setHardCancellationEnabled(true).build())
            .build()
        // URL과 인증 호스트는 유지하고 연결 주소만 고정해야 TLS hostname 검증을 약화시키지 않는다.
        val get = HttpGet(request.uri)
        get.setHeader("Accept", "text/html,application/xhtml+xml,application/rss+xml,application/atom+xml,application/xml,text/xml")
        get.setHeader("Accept-Encoding", "identity")
        get.setHeader("User-Agent", "Sigak/0.1 (technical-news source reader)")
        cancellation.register { get.cancel(); client.close(CloseMode.IMMEDIATE) }
        try {
            val response = client.executeOpen(null, get, null)
            val entity = response.entity
            return PinnedResponse(response.code, response.getFirstHeader("Content-Type")?.value,
                response.getHeaders("Content-Encoding").map { it.value }.takeIf { it.isNotEmpty() }?.joinToString(","), response.getFirstHeader("Location")?.value,
                entity?.contentLength ?: -1, entity?.content ?: ByteArrayInputStream(ByteArray(0)), closeAction = {
                    client.close(CloseMode.IMMEDIATE)
                    response.close()
                }, retryAfter = response.getFirstHeader("Retry-After")?.value)
        } catch (exception: Exception) {
            client.close(CloseMode.IMMEDIATE)
            throw exception
        }
    }
}
