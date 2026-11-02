package com.sigak.collection.service

import com.sigak.collection.domain.CollectedArticle
import com.sigak.collection.dto.EnrichmentRequest
import com.sigak.collection.dto.EnrichmentResponse
import org.springframework.stereotype.Service

fun interface EnrichmentClient {
    fun enrich(request: EnrichmentRequest): EnrichmentResponse
}

fun interface CollectedArticlePublisher {
    fun publish(article: CollectedArticle, enrichment: EnrichmentResponse?): CollectedArticlePublishResult
}

@Service
class CollectionPipelineService(
    private val articleNormalizer: ArticleNormalizer,
    private val enrichmentClient: EnrichmentClient,
    private val collectedArticlePublisher: CollectedArticlePublisher
) {

    fun enrich(article: CollectedArticle): EnrichmentResponse {
        val request = articleNormalizer.toEnrichmentRequest(article)
        return enrichmentClient.enrich(request)
    }

    fun publish(article: CollectedArticle): CollectedArticlePublishResult {
        // 수집은 AI 호출 없이 공개하고 실제 요약은 상세 방문 때 별도 작업으로 생성한다.
        return collectedArticlePublisher.publish(article, null)
    }
}
