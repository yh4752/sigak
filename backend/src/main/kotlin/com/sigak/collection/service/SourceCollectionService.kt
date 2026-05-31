package com.sigak.collection.service

import com.sigak.collection.collector.ArxivCollector
import com.sigak.collection.collector.RssAtomCollector
import com.sigak.collection.domain.CollectedArticle
import com.sigak.collection.domain.NewsSource
import com.sigak.collection.domain.SourceType
import com.sigak.collection.dto.CollectionFailureStage
import com.sigak.collection.dto.CollectionFailureSummary
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

private const val MAX_FAILURE_SUMMARY_COUNT = 5

fun interface SourceContentFetcher {
    fun fetch(url: String): String
}

fun interface SourceCollector {
    fun collect(source: NewsSource, maxArticlesPerSource: Int): SourceCollectionResult
}

@Component
class HttpSourceContentFetcher : SourceContentFetcher {
    private val restClient = RestClient.create()

    override fun fetch(url: String): String =
        restClient
            .get()
            .uri(url)
            .retrieve()
            .body(String::class.java)
            ?: ""
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
    private val collectionPipelineService: CollectionPipelineService
) : SourceCollector {

    override fun collect(source: NewsSource, maxArticlesPerSource: Int): SourceCollectionResult {
        val xml = sourceContentFetcher.fetch(source.url)
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
                        failureSummaries.add(
                            CollectionFailureSummary(
                                stage = CollectionFailureStage.PUBLISH_ARTICLE,
                                message = failureMessage(exception)
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

    private fun parse(source: NewsSource, xml: String): List<CollectedArticle> =
        when (source.type) {
            SourceType.RSS_ATOM -> rssAtomCollector.parse(source, xml)
            SourceType.ARXIV -> arxivCollector.parse(source, xml)
            SourceType.MANUAL -> emptyList()
        }
}

internal fun failureMessage(exception: Throwable): String {
    val className = exception::class.simpleName ?: "Exception"
    val message = exception.message?.takeIf { value -> value.isNotBlank() }
    return if (message == null) className else "$className: $message"
}
