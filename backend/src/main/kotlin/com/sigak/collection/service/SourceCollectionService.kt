package com.sigak.collection.service

import com.sigak.collection.collector.ArxivCollector
import com.sigak.collection.collector.RssAtomCollector
import com.sigak.collection.config.ArxivFetchProperties
import com.sigak.collection.domain.CollectedArticle
import com.sigak.collection.domain.NewsSource
import com.sigak.collection.domain.SourceType
import com.sigak.collection.dto.CollectionFailureStage
import com.sigak.collection.dto.CollectionFailureSummary
import java.net.URI
import java.time.Clock
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient

private const val MAX_FAILURE_SUMMARY_COUNT = 5
private const val ARXIV_EXPORT_HOST = "export.arxiv.org"
private const val RETRY_AFTER_HEADER = "Retry-After"

fun interface SourceContentFetcher {
    fun fetch(url: String): String
}

fun interface SourceFetchDelay {
    fun sleep(duration: Duration)
}

class ThreadSourceFetchDelay : SourceFetchDelay {
    override fun sleep(duration: Duration) {
        if (!duration.isPositive()) {
            return
        }

        try {
            Thread.sleep(duration.toMillis())
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("Interrupted while waiting to fetch source content.", exception)
        }
    }
}

fun interface SourceCollector {
    fun collect(source: NewsSource, maxArticlesPerSource: Int): SourceCollectionResult
}

@Component
class HttpSourceContentFetcher(
    @Qualifier("sourceContentRestClient")
    private val restClient: RestClient,
    private val arxivFetchProperties: ArxivFetchProperties,
    private val sourceFetchDelay: SourceFetchDelay,
    @Qualifier("collectionFetchClock")
    private val clock: Clock
) : SourceContentFetcher {
    private val arxivLock = Any()
    private var lastArxivRequestStartedAt: java.time.Instant? = null

    override fun fetch(url: String): String =
        if (isArxivExportUrl(url)) {
            fetchArxiv(url)
        } else {
            fetchOnce(url)
        }

    private fun fetchArxiv(url: String): String {
        synchronized(arxivLock) {
            val retryBudget = ArxivRetryBudget(
                remainingRateLimitRetries = arxivFetchProperties.maxRateLimitRetries,
                remainingTransientFetchRetries = arxivFetchProperties.maxTransientFetchRetries
            )

            while (true) {
                waitForNextArxivRequest()

                try {
                    return fetchOnce(url)
                } catch (exception: HttpClientErrorException) {
                    if (exception.statusCode != HttpStatus.TOO_MANY_REQUESTS || !retryBudget.useRateLimitRetry()) {
                        throw exception
                    }

                    sourceFetchDelay.sleep(retryDelayFor(exception))
                } catch (exception: ResourceAccessException) {
                    if (!retryBudget.useTransientFetchRetry()) {
                        throw exception
                    }

                    sourceFetchDelay.sleep(arxivFetchProperties.transientFetchRetryDelay)
                }
            }
        }
    }

    private fun waitForNextArxivRequest() {
        val now = clock.instant()
        val nextAllowedAt = lastArxivRequestStartedAt?.plus(arxivFetchProperties.minRequestInterval)
        val waitDuration = nextAllowedAt?.let { allowedAt -> Duration.between(now, allowedAt) }

        if (waitDuration != null && waitDuration.isPositive()) {
            sourceFetchDelay.sleep(waitDuration)
        }

        // arXiv export API는 관리 머신 합산 3초 1요청 정책이 있어 요청 시작 시각을 직렬화한다.
        lastArxivRequestStartedAt = clock.instant()
    }

    private fun fetchOnce(url: String): String =
        restClient
            .get()
            .uri(url)
            .retrieve()
            .body(String::class.java)
            ?: ""

    private fun retryDelayFor(exception: HttpClientErrorException): Duration {
        val retryAfter = exception.responseHeaders?.getFirst(RETRY_AFTER_HEADER)
        return retryAfterDuration(retryAfter) ?: arxivFetchProperties.rateLimitRetryDelay
    }

    private fun retryAfterDuration(value: String?): Duration? {
        val trimmed = value?.trim()?.takeIf { header -> header.isNotBlank() } ?: return null
        val retryAfterSeconds = trimmed.toLongOrNull()
        if (retryAfterSeconds != null) {
            return Duration.ofSeconds(retryAfterSeconds).takeIf { duration -> duration.isPositive() }
        }

        val retryAt = runCatching {
            ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
        }.getOrNull() ?: return null

        return Duration.between(clock.instant(), retryAt).takeIf { duration -> duration.isPositive() }
    }

    private fun isArxivExportUrl(url: String): Boolean =
        runCatching { URI.create(url).host.equals(ARXIV_EXPORT_HOST, ignoreCase = true) }
            .getOrDefault(false)
}

private class ArxivRetryBudget(
    private var remainingRateLimitRetries: Int,
    private var remainingTransientFetchRetries: Int
) {
    fun useRateLimitRetry(): Boolean {
        if (remainingRateLimitRetries <= 0) {
            return false
        }

        remainingRateLimitRetries -= 1
        return true
    }

    fun useTransientFetchRetry(): Boolean {
        if (remainingTransientFetchRetries <= 0) {
            return false
        }

        remainingTransientFetchRetries -= 1
        return true
    }
}

data class SourceCollectionResult(
    val sourceId: String,
    val discoveredCount: Int,
    val publishedArticleIds: List<Long>,
    val skippedArticleIds: List<Long>,
    val failedCount: Int,
    val failureSummaries: List<CollectionFailureSummary>
)

@Service
class SourceCollectionService(
    private val sourceContentFetcher: SourceContentFetcher,
    private val rssAtomCollector: RssAtomCollector,
    private val arxivCollector: ArxivCollector,
    private val collectionFailureClassifier: CollectionFailureClassifier,
    private val collectionPipelineService: CollectionPipelineService
) : SourceCollector {

    override fun collect(source: NewsSource, maxArticlesPerSource: Int): SourceCollectionResult {
        val xml = fetch(source)
        val articles = parse(source, xml).take(maxArticlesPerSource)
        val publishedArticleIds = mutableListOf<Long>()
        val skippedArticleIds = mutableListOf<Long>()
        val failureSummaries = mutableListOf<CollectionFailureSummary>()
        var failedCount = 0

        articles.forEach { article ->
            runCatching { collectionPipelineService.publish(article) }
                .onSuccess { result ->
                    when (result.outcome) {
                        CollectedArticlePublishOutcome.PUBLISHED -> publishedArticleIds.add(result.articleId)
                        CollectedArticlePublishOutcome.SKIPPED_DUPLICATE -> skippedArticleIds.add(result.articleId)
                    }
                }
                // 일부 기사 저장 실패가 전체 소스 수집 실패로 번지지 않도록 실패 수만 기록한다.
                .onFailure { exception ->
                    failedCount += 1
                    if (failureSummaries.size < MAX_FAILURE_SUMMARY_COUNT) {
                        val classification = collectionFailureClassifier.classify(
                            stage = CollectionFailureStage.PUBLISH_ARTICLE,
                            exception = exception
                        )
                        failureSummaries.add(
                            CollectionFailureSummary(
                                stage = CollectionFailureStage.PUBLISH_ARTICLE,
                                message = classification.message,
                                failureKind = classification.failureKind,
                                retryable = classification.retryable,
                                articleExternalId = article.externalId,
                                articleUrl = article.url,
                                articleTitle = article.title
                            )
                        )
                    }
                }
        }

        return SourceCollectionResult(
            sourceId = source.id,
            discoveredCount = articles.size,
            publishedArticleIds = publishedArticleIds,
            skippedArticleIds = skippedArticleIds,
            failedCount = failedCount,
            failureSummaries = failureSummaries
        )
    }

    private fun fetch(source: NewsSource): String =
        runCatching { sourceContentFetcher.fetch(source.url) }
            .getOrElse { exception ->
                throw SourceCollectionException(
                    stage = CollectionFailureStage.FETCH_SOURCE,
                    message = failureMessage(exception),
                    cause = exception
                )
            }

    private fun parse(source: NewsSource, xml: String): List<CollectedArticle> =
        runCatching {
            when (source.type) {
                SourceType.RSS_ATOM -> rssAtomCollector.parse(source, xml)
                SourceType.ARXIV -> arxivCollector.parse(source, xml)
                SourceType.MANUAL -> emptyList()
            }
        }.getOrElse { exception ->
            throw SourceCollectionException(
                stage = CollectionFailureStage.PARSE_SOURCE,
                message = failureMessage(exception),
                cause = exception
            )
        }
}

internal fun failureMessage(exception: Throwable): String {
    val className = exception::class.simpleName ?: "Exception"
    val message = exception.message?.takeIf { value -> value.isNotBlank() }
    return if (message == null) className else "$className: $message"
}

private fun Duration.isPositive(): Boolean =
    !isZero && !isNegative
