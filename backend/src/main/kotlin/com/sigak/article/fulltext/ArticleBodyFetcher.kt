package com.sigak.article.fulltext

import jakarta.annotation.PreDestroy
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.nio.charset.Charset
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component


data class FullTextResult(val text: String, val finalUrl: String, val durationMs: Long)

fun interface ArticleBodyFetcher {
    fun fetch(url: String): FullTextResult
}

class FullTextException(val code: String) : RuntimeException(code)

object ArticleBodyPolicy {
    private val hosts = setOf("openai.com", "blog.google", "github.blog")

    fun supported(url: String): Boolean = runCatching {
        val uri = URI(url)
        val host = uri.host?.lowercase() ?: return@runCatching false
        uri.scheme == "https" && host in hosts && uri.rawUserInfo == null &&
            uri.port in setOf(-1, 443) && uri.rawAuthority.lowercase() in setOf(host, "$host:443")
    }.getOrDefault(false)

    fun publicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress) return false
        val bytes = address.address.map { it.toInt() and 255 }
        if (address is Inet4Address) {
            val first = bytes[0]
            val second = bytes[1]
            return first !in setOf(0, 10, 127) && first < 224 &&
                !(first == 100 && second in 64..127) &&
                !(first == 169 && second == 254) &&
                !(first == 172 && second in 16..31) &&
                // 192.0.66.2는 GitHub Blog의 공개 주소이므로 예약된 /24만 제외한다.
                !(first == 192 && second == 168) &&
                !(first == 192 && second == 0 && bytes[2] in setOf(0, 2)) &&
                !(first == 198 && second in setOf(18, 19)) &&
                !(first == 198 && second == 51 && bytes[2] == 100) &&
                !(first == 203 && second == 0 && bytes[2] == 113)
        }
        // IPv6는 전역 unicast만 허용하고 문서·터널용 주소는 수집 대상에서 제외한다.
        return bytes.size == 16 && bytes[0] in 0x20..0x3f &&
            !(bytes[0] == 0x20 && bytes[1] == 0x02) &&
            !(bytes.take(4) == listOf(0x20, 0x01, 0x0d, 0xb8)) &&
            !(bytes.take(4) == listOf(0x20, 0x01, 0x00, 0x00))
    }
}

@Component
class HttpArticleBodyFetcher internal constructor(
    private val extractor: ArticleBodyExtractor,
    private val resolver: (String) -> Array<InetAddress>,
    private val connectionFactory: (URL) -> HttpURLConnection,
    private val timeoutMillis: Long
) : ArticleBodyFetcher {
    @Autowired
    constructor(extractor: ArticleBodyExtractor) : this(
        extractor,
        { InetAddress.getAllByName(it) },
        { it.openConnection() as HttpURLConnection },
        15000
    )

    private val executor = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "article-body-fetch").apply { isDaemon = true }
    }

    override fun fetch(url: String): FullTextResult {
        if (!ArticleBodyPolicy.supported(url)) throw FullTextException("SOURCE_NOT_SUPPORTED")
        val started = System.nanoTime()
        val deadline = started + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        // DNS 대기·리다이렉트·느린 연속 응답을 합친 전체 작업에 같은 시간 제한을 적용한다.
        val task = executor.submit<FullTextResult> { fetchWithinDeadline(url, started, deadline) }
        try {
            return task.get(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            task.cancel(true)
            throw FullTextException("FETCH_TIMEOUT")
        } catch (_: InterruptedException) {
            task.cancel(true)
            Thread.currentThread().interrupt()
            throw FullTextException("FETCH_TIMEOUT")
        } catch (exception: ExecutionException) {
            throw (exception.cause as? FullTextException ?: FullTextException("FETCH_FAILED"))
        }
    }

    private fun fetchWithinDeadline(initialUrl: String, started: Long, deadline: Long): FullTextResult {
        var current = URI(initialUrl)
        val host = current.host.lowercase()
        for (redirects in 0..3) {
            remainingMillis(deadline)
            if (!ArticleBodyPolicy.supported(current.toString()) || current.host.lowercase() != host) {
                throw FullTextException("REDIRECT_REJECTED")
            }
            val addresses = resolver(host)
            if (addresses.isEmpty() || addresses.any { !ArticleBodyPolicy.publicAddress(it) }) {
                throw FullTextException("PRIVATE_ADDRESS")
            }
            val connection = connectionFactory(current.toURL())
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = remainingMillis(deadline)
                connection.readTimeout = remainingMillis(deadline)
                connection.setRequestProperty("User-Agent", "Sigak/0.1 (technical-news article reader)")
                connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
                connection.setRequestProperty("Accept-Encoding", "identity")
                connection.useCaches = false
                val status = connection.responseCode
                if (status in setOf(301, 302, 303, 307, 308)) {
                    if (redirects == 3) throw FullTextException("REDIRECT_REJECTED")
                    val location = connection.getHeaderField("Location") ?: throw FullTextException("REDIRECT_REJECTED")
                    current = runCatching { current.resolve(location) }.getOrElse { throw FullTextException("REDIRECT_REJECTED") }
                    continue
                }
                if (status != 200) throw FullTextException("FETCH_FAILED")
                val contentType = connection.contentType.orEmpty()
                if (!contentType.startsWith("text/html", true) && !contentType.startsWith("application/xhtml+xml", true)) {
                    throw FullTextException("FETCH_FAILED")
                }
                val encoding = connection.contentEncoding
                if (encoding != null && !encoding.equals("identity", true)) throw FullTextException("FETCH_FAILED")
                if (connection.contentLengthLong > MAX_HTML_BYTES) throw FullTextException("HTML_TOO_LARGE")
                val html = readBounded(connection, deadline)
                val charset = Regex("charset\\s*=\\s*[\"']?([a-zA-Z0-9_-]+)", RegexOption.IGNORE_CASE)
                    .find(contentType)?.groupValues?.get(1)?.let { runCatching { Charset.forName(it) }.getOrNull() }
                    ?: Charsets.UTF_8
                val text = extractor.extract(current.toString(), html.toString(charset))
                remainingMillis(deadline)
                return FullTextResult(text, current.toString(), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
            } finally {
                connection.disconnect()
            }
        }
        throw FullTextException("REDIRECT_REJECTED")
    }

    private fun readBounded(connection: HttpURLConnection, deadline: Long): ByteArray {
        val output = ByteArrayOutputStream()
        connection.inputStream.use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                connection.readTimeout = remainingMillis(deadline)
                val size = input.read(buffer)
                if (size < 0) break
                if (output.size() + size > MAX_HTML_BYTES) throw FullTextException("HTML_TOO_LARGE")
                output.write(buffer, 0, size)
            }
        }
        return output.toByteArray()
    }

    private fun remainingMillis(deadline: Long): Int {
        val remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())
        if (remaining <= 0 || Thread.currentThread().isInterrupted) throw FullTextException("FETCH_TIMEOUT")
        return remaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    @PreDestroy
    fun close() { executor.shutdownNow() }

    companion object {
        private const val MAX_HTML_BYTES = 2 * 1024 * 1024
    }
}
