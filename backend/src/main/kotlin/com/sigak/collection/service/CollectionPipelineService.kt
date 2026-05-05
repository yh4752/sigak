package com.sigak.collection.service

import com.sigak.collection.domain.CollectedArticle
import com.sigak.collection.dto.EnrichmentRequest
import com.sigak.collection.dto.EnrichmentResponse
import org.springframework.stereotype.Service

fun interface EnrichmentClient {
    fun enrich(request: EnrichmentRequest): EnrichmentResponse
}

@Service
class CollectionPipelineService(
    private val articleNormalizer: ArticleNormalizer,
    private val enrichmentClient: EnrichmentClient
) {

    fun enrich(article: CollectedArticle): EnrichmentResponse {
        val request = articleNormalizer.toEnrichmentRequest(article)
        return enrichmentClient.enrich(request)
    }
}
