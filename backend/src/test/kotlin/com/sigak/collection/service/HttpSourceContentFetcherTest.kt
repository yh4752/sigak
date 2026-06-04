package com.sigak.collection.service

import com.sigak.collection.config.ArxivFetchProperties
import java.net.SocketTimeoutException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.io.TempDir
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient

class HttpSourceContentFetcherTest {

    private val restClientBuilder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(restClientBuilder).build()
    private val clock = MutableClock(Instant.parse("2026-06-03T00:00:00Z"))
    private val delay = RecordingSourceFetchDelay(clock)

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun delaysConsecutiveArxivExportRequestsByTheConfiguredMinimumInterval() {
        val fetcher = fetcher()
        val firstUrl = "https://export.arxiv.org/api/query?search_query=cat:cs.AI"
        val secondUrl = "https://export.arxiv.org/api/query?search_query=cat:cs.LG"
        server.expect(ExpectedCount.once(), requestTo(firstUrl))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("first feed", MediaType.TEXT_XML))
        server.expect(ExpectedCount.once(), requestTo(secondUrl))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("second feed", MediaType.TEXT_XML))

        assertEquals("first feed", fetcher.fetch(firstUrl))
        assertEquals("second feed", fetcher.fetch(secondUrl))

        assertEquals(listOf(Duration.ofSeconds(3)), delay.durations)
        server.verify()
    }

    @Test
    fun doesNotThrottleNonArxivSources() {
        val fetcher = fetcher()
        val firstUrl = "https://openai.com/news/rss.xml"
        val secondUrl = "https://github.blog/feed/"
        server.expect(ExpectedCount.once(), requestTo(firstUrl))
            .andRespond(withSuccess("openai feed", MediaType.TEXT_XML))
        server.expect(ExpectedCount.once(), requestTo(secondUrl))
            .andRespond(withSuccess("github feed", MediaType.TEXT_XML))

        assertEquals("openai feed", fetcher.fetch(firstUrl))
        assertEquals("github feed", fetcher.fetch(secondUrl))

        assertEquals(emptyList(), delay.durations)
        server.verify()
    }

    @Test
    fun retriesArxivRateLimitAfterRetryAfterHeader() {
        val fetcher = fetcher()
        val url = "https://export.arxiv.org/api/query?search_query=cat:cs.AI"
        server.expect(ExpectedCount.once(), requestTo(url))
            .andRespond(
                withStatus(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", "7")
                    .body("Rate exceeded")
            )
        server.expect(ExpectedCount.once(), requestTo(url))
            .andRespond(withSuccess("retried feed", MediaType.TEXT_XML))

        assertEquals("retried feed", fetcher.fetch(url))

        assertEquals(listOf(Duration.ofSeconds(7)), delay.durations)
        server.verify()
    }

    @Test
    fun stopsRetryingArxivRateLimitAfterConfiguredRetryLimit() {
        val fetcher = fetcher(
            properties = ArxivFetchProperties(
                minRequestInterval = Duration.ofSeconds(3),
                rateLimitRetryDelay = Duration.ofSeconds(30),
                transientFetchRetryDelay = Duration.ofSeconds(5),
                maxTransientFetchRetries = 1,
                maxRateLimitRetries = 1
            )
        )
        val url = "https://export.arxiv.org/api/query?search_query=cat:cs.AI"
        server.expect(ExpectedCount.twice(), requestTo(url))
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("Rate exceeded"))

        assertFailsWith<HttpClientErrorException.TooManyRequests> {
            fetcher.fetch(url)
        }

        assertEquals(listOf(Duration.ofSeconds(30)), delay.durations)
        server.verify()
    }

    @Test
    fun retriesArxivReadTimeoutWithConfiguredTransientDelay() {
        val fetcher = fetcher()
        val url = "https://export.arxiv.org/api/query?search_query=cat:cs.CL"
        server.expect(ExpectedCount.once(), requestTo(url))
            .andRespond(withException(SocketTimeoutException("read timed out")))
        server.expect(ExpectedCount.once(), requestTo(url))
            .andRespond(withSuccess("retried after timeout", MediaType.TEXT_XML))

        assertEquals("retried after timeout", fetcher.fetch(url))

        assertEquals(listOf(Duration.ofSeconds(5)), delay.durations)
        server.verify()
    }

    @Test
    fun sharesArxivCooldownAcrossFetcherInstancesUsingStateFile() {
        val properties = defaultArxivProperties()
        val cooldownStateFile = tempDir.resolve("arxiv-cooldown.txt")
        val firstFetcher = fetcher(properties = properties, cooldownStateFile = cooldownStateFile)
        val secondFetcher = fetcher(properties = properties, cooldownStateFile = cooldownStateFile)
        val firstUrl = "https://export.arxiv.org/api/query?search_query=cat:cs.AI"
        val secondUrl = "https://export.arxiv.org/api/query?search_query=cat:cs.LG"
        server.expect(ExpectedCount.once(), requestTo(firstUrl))
            .andRespond(withSuccess("first feed", MediaType.TEXT_XML))
        server.expect(ExpectedCount.once(), requestTo(secondUrl))
            .andRespond(withSuccess("second feed", MediaType.TEXT_XML))

        assertEquals("first feed", firstFetcher.fetch(firstUrl))
        assertEquals("second feed", secondFetcher.fetch(secondUrl))

        assertEquals(listOf(Duration.ofSeconds(3)), delay.durations)
        server.verify()
    }

    private fun fetcher(
        properties: ArxivFetchProperties = defaultArxivProperties(),
        cooldownStateFile: Path = tempDir.resolve("arxiv-cooldown.txt")
    ): HttpSourceContentFetcher {
        val arxivFetchGate = FileBackedArxivFetchGate(
            properties = properties,
            sourceFetchDelay = delay,
            clock = clock,
            cooldownStateFile = cooldownStateFile
        )

        return HttpSourceContentFetcher(
            restClient = restClientBuilder.build(),
            arxivFetchProperties = properties,
            sourceFetchDelay = delay,
            arxivFetchGate = arxivFetchGate,
            clock = clock
        )
    }

    private fun defaultArxivProperties(): ArxivFetchProperties =
        ArxivFetchProperties(
            minRequestInterval = Duration.ofSeconds(3),
            rateLimitRetryDelay = Duration.ofSeconds(30),
            transientFetchRetryDelay = Duration.ofSeconds(5),
            maxTransientFetchRetries = 1,
            maxRateLimitRetries = 2
        )
}

private class RecordingSourceFetchDelay(
    private val clock: MutableClock
) : SourceFetchDelay {
    val durations = mutableListOf<Duration>()

    override fun sleep(duration: Duration) {
        durations.add(duration)
        clock.advance(duration)
    }
}

private class MutableClock(
    private var current: Instant
) : Clock() {

    override fun getZone(): ZoneId = ZoneId.of("UTC")

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = current

    fun advance(duration: Duration) {
        current = current.plus(duration)
    }
}
