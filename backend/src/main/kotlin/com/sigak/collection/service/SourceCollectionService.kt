package com.sigak.collection.service

import com.sigak.collection.collector.ArxivCollector
import com.sigak.collection.collector.RssAtomCollector
import com.sigak.collection.domain.CollectedArticle
import com.sigak.collection.domain.NewsSource
import com.sigak.collection.domain.SourceType
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

fun interface SourceContentFetcher {
    fun fetch(url: String): String
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
    val failedCount: Int
)

@Service
class SourceCollectionService(
    private val sourceContentFetcher: SourceContentFetcher,
    private val rssAtomCollector: RssAtomCollector,
    private val arxivCollector: ArxivCollector,
    private val collectionPipelineService: CollectionPipelineService
) {

    fun collect(source: NewsSource): SourceCollectionResult {
        val xml = sourceContentFetcher.fetch(source.url)
        val articles = parse(source, xml)
        val publishedArticleIds = mutableListOf<Long>()
        var failedCount = 0

        articles.forEach { article ->
            runCatching { collectionPipelineService.publish(article) }
                .onSuccess { articleId -> publishedArticleIds.add(articleId) }
                // 일부 기사 저장 실패가 전체 소스 수집 실패로 번지지 않도록 실패 수만 기록한다.
                .onFailure { failedCount += 1 }
        }

        return SourceCollectionResult(
            sourceId = source.id,
            discoveredCount = articles.size,
            publishedArticleIds = publishedArticleIds,
            failedCount = failedCount
        )
    }

    private fun parse(source: NewsSource, xml: String): List<CollectedArticle> =
        when (source.type) {
            SourceType.RSS_ATOM -> rssAtomCollector.parse(source, xml)
            SourceType.ARXIV -> arxivCollector.parse(source, xml)
            SourceType.MANUAL -> emptyList()
        }
}
