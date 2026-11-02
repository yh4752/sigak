package com.sigak.common.http

import jakarta.annotation.PreDestroy
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutionException
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.jsoup.Jsoup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component


data class ExternalDocument(val text: String, val finalUrl: String, val contentType: String, val durationMs: Long)

fun interface PublicDocumentClient {
    fun fetch(url: String, allowedHosts: Set<String>): ExternalDocument
}

class ExternalFetchException(val code: String, val retryAfter: String? = null) : RuntimeException(code)

object PublicUrlPolicy {
    fun requireUrl(url: String, allowedHosts: Set<String>? = null): URI {
        val uri = runCatching { URI(url) }.getOrElse { throw ExternalFetchException("INVALID_URL") }
        val host = uri.host?.lowercase() ?: throw ExternalFetchException("INVALID_URL")
        if (uri.scheme != "https" || uri.rawUserInfo != null || uri.port !in setOf(-1, 443) ||
            uri.rawAuthority.lowercase() !in setOf(host, "$host:443") || host.endsWith('.') ||
            host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local") || host.endsWith(".internal")) {
            throw ExternalFetchException("INVALID_URL")
        }
        if (allowedHosts != null && host !in allowedHosts.map { it.lowercase() }.toSet()) {
            throw ExternalFetchException("HOST_NOT_ALLOWED")
        }
        val path = uri.rawPath.ifEmpty { "/" }
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        return URI("https://$host$path$query").normalize()
    }

    fun host(url: String): String = requireUrl(url).host
    fun canonical(url: String): String = requireUrl(url).toString()

    fun publicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress) return false
        val bytes = address.address.map { it.toInt() and 255 }
        if (address is Inet4Address) {
            val first = bytes[0]
            val second = bytes[1]
            return first !in setOf(0, 10, 127) && first < 224 &&
                !(first == 100 && second in 64..127) && !(first == 169 && second == 254) &&
                !(first == 172 && second in 16..31) && !(first == 192 && second == 168) &&
                !(first == 192 && second == 0 && bytes[2] in setOf(0, 2)) &&
                !(first == 192 && second == 88 && bytes[2] == 99) &&
                !(first == 198 && second in setOf(18, 19)) &&
                !(first == 198 && second == 51 && bytes[2] == 100) &&
                !(first == 203 && second == 0 && bytes[2] == 113)
        }
        // 공개 IPv6 unicast 중에서도 문서·터널·특수 목적 대역은 보수적으로 거절한다.
        return bytes.size == 16 && bytes[0] in 0x20..0x3f &&
            !(bytes[0] == 0x20 && bytes[1] == 0x01 && bytes[2] < 2) &&
            !(bytes.take(4) == listOf(0x20, 0x01, 0x0d, 0xb8)) &&
            !(bytes[0] == 0x20 && bytes[1] == 0x02) &&
            !(bytes[0] == 0x3f && bytes[1] == 0xff && bytes[2] < 0x10)
    }
}

@Component
class SafePublicDocumentClient internal constructor(
    private val resolver: (String) -> Array<InetAddress>,
    private val transport: PinnedDocumentTransport,
    private val timeoutMillis: Long
) : PublicDocumentClient, AutoCloseable {
    @Autowired
    constructor() : this({ InetAddress.getAllByName(it) }, ApachePinnedHttpsTransport(), 15000)

    private val active = ConcurrentHashMap.newKeySet<FetchCancellation>()
    private val executor = ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(8),
        { task -> Thread(task, "public-document-fetch").apply { isDaemon = true } }, ThreadPoolExecutor.AbortPolicy())

    override fun fetch(url: String, allowedHosts: Set<String>): ExternalDocument {
        val uri = PublicUrlPolicy.requireUrl(url, allowedHosts)
        if (executor.isShutdown) throw ExternalFetchException("FETCH_CLOSED")
        val started = System.nanoTime()
        val deadline = started + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        val cancellation = FetchCancellation()
        active.add(cancellation)
        val task = try {
            executor.submit<ExternalDocument> { fetchWithinDeadline(uri, allowedHosts, started, deadline, cancellation) }
        } catch (_: RejectedExecutionException) {
            active.remove(cancellation)
            throw ExternalFetchException(if (executor.isShutdown) "FETCH_CLOSED" else "FETCH_BUSY")
        }
        try {
            return task.get(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            cancellation.cancel()
            task.cancel(true)
            executor.remove(task as Runnable)
            throw ExternalFetchException("FETCH_TIMEOUT")
        } catch (_: InterruptedException) {
            cancellation.cancel()
            task.cancel(true)
            executor.remove(task as Runnable)
            Thread.currentThread().interrupt()
            throw ExternalFetchException("FETCH_TIMEOUT")
        } catch (exception: ExecutionException) {
            throw when (val cause = exception.cause) {
                is ExternalFetchException -> cause
                is SocketTimeoutException, is TimeoutException -> ExternalFetchException("FETCH_TIMEOUT")
                is UnknownHostException -> ExternalFetchException("DNS_FAILED")
                else -> ExternalFetchException("FETCH_FAILED")
            }
        } finally {
            cancellation.cancel()
            active.remove(cancellation)
        }
    }

    private fun fetchWithinDeadline(initial: URI, hosts: Set<String>, started: Long, deadline: Long,
        cancellation: FetchCancellation): ExternalDocument {
        var uri = initial
        for (redirects in 0..3) {
            remainingMillis(deadline)
            val addresses = resolver(uri.host)
            if (addresses.isEmpty() || addresses.any { !PublicUrlPolicy.publicAddress(it) }) {
                throw ExternalFetchException("PRIVATE_ADDRESS")
            }
            // 실제 전송기도 이 IP 하나만 사용한다. 같은 호스트의 다음 redirect도 다시 검증한다.
            val request = PinnedRequest(uri, addresses.first(), remainingMillis(deadline))
            transport.open(request, cancellation).use { response ->
                if (response.status in setOf(301, 302, 303, 307, 308)) {
                    if (redirects == 3) throw ExternalFetchException("REDIRECT_REJECTED")
                    val target = response.location?.let { runCatching { uri.resolve(it).toString() }.getOrNull() }
                        ?: throw ExternalFetchException("REDIRECT_REJECTED")
                    val next = try { PublicUrlPolicy.requireUrl(target, hosts) }
                    catch (_: ExternalFetchException) { throw ExternalFetchException("REDIRECT_REJECTED") }
                    if (next.host != initial.host) throw ExternalFetchException("REDIRECT_REJECTED")
                    uri = next
                    return@use
                }
                if (response.status != 200) {
                    val retryAfter = if (response.status == 429) response.retryAfter?.trim()?.takeIf { value ->
                        value.length in 1..128 && value.all { it.code in 32..126 }
                    } else null
                    throw ExternalFetchException("HTTP_${response.status}", retryAfter)
                }
                if (!response.contentEncoding.isNullOrBlank() && !response.contentEncoding.equals("identity", true)) {
                    throw ExternalFetchException("COMPRESSED_RESPONSE")
                }
                if (response.contentLength > MAX_BYTES) throw ExternalFetchException("RESPONSE_TOO_LARGE")
                val contentType = response.contentType.orEmpty()
                val mime = contentType.substringBefore(';').trim().lowercase()
                if (mime !in setOf("text/html", "application/xhtml+xml", "text/xml", "application/xml", "application/rss+xml", "application/atom+xml")) {
                    throw ExternalFetchException("UNSUPPORTED_CONTENT_TYPE")
                }
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    remainingMillis(deadline)
                    val count = response.body.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > MAX_BYTES) throw ExternalFetchException("RESPONSE_TOO_LARGE")
                    output.write(buffer, 0, count)
                }
                val text = decode(output.toByteArray(), contentType, mime)
                remainingMillis(deadline)
                return ExternalDocument(text, uri.toString(), contentType,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
            }
        }
        throw ExternalFetchException("REDIRECT_REJECTED")
    }

    private fun decode(bytes: ByteArray, contentType: String, mime: String): String {
        val headerCharset = Regex("charset\\s*=\\s*[\"']?([^\\s;\"']+)", RegexOption.IGNORE_CASE)
            .find(contentType)?.groupValues?.get(1)
        val prefix = bytes.take(4).map { it.toInt() and 255 }
        val bomCharset = when {
            prefix == listOf(0x00, 0x00, 0xfe, 0xff) -> "UTF-32BE"
            prefix == listOf(0xff, 0xfe, 0x00, 0x00) -> "UTF-32LE"
            prefix.take(2) == listOf(0xfe, 0xff) -> "UTF-16BE"
            prefix.take(2) == listOf(0xff, 0xfe) -> "UTF-16LE"
            else -> null
        }
        val xmlPrefix = bytes.take(1024).toByteArray().toString(Charsets.ISO_8859_1)
        val xmlCharset = if (mime.endsWith("xml")) {
            Regex("^\\s*<\\?xml[^>]*encoding\\s*=\\s*['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE)
                .find(xmlPrefix)?.groupValues?.get(1)
        } else null
        val htmlCharset = if (mime in setOf("text/html", "application/xhtml+xml")) {
            val metadata = Jsoup.parse(bytes.take(8192).toByteArray().toString(Charsets.ISO_8859_1))
            metadata.selectFirst("meta[charset]")?.attr("charset")?.takeIf { it.isNotBlank() }
                ?: metadata.select("meta[http-equiv=content-type]").firstNotNullOfOrNull { meta ->
                    Regex("charset\\s*=\\s*([^\\s;]+)", RegexOption.IGNORE_CASE)
                        .find(meta.attr("content"))?.groupValues?.get(1)
                }
        } else null
        val charset = runCatching { Charset.forName(headerCharset ?: bomCharset ?: xmlCharset ?: htmlCharset ?: "UTF-8") }
            .getOrElse { throw ExternalFetchException("INVALID_ENCODING") }
        return try {
            charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
        } catch (_: java.nio.charset.CharacterCodingException) {
            throw ExternalFetchException("INVALID_ENCODING")
        }
    }

    private fun remainingMillis(deadline: Long): Int {
        val remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())
        if (remaining <= 0 || Thread.currentThread().isInterrupted) throw ExternalFetchException("FETCH_TIMEOUT")
        return remaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    @PreDestroy
    override fun close() {
        executor.shutdownNow()
        active.forEach { it.cancel() }
    }

    companion object { private const val MAX_BYTES = 2 * 1024 * 1024 }
}
