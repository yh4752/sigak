package com.sigak.collection.service

import com.sigak.collection.dto.EnrichmentRequest
import com.sigak.collection.dto.EnrichmentResponse
import org.springframework.stereotype.Component

@Component
class MockEnrichmentClient : EnrichmentClient {

    override fun enrich(request: EnrichmentRequest): EnrichmentResponse {
        val primaryTopic = request.topics.firstOrNull() ?: "SOFTWARE_ENGINEERING"
        val firstSentence = request.rawContent.trim().substringBefore(".").trim()

        // 외부 LLM 비용 없이 로컬 수집 파이프라인을 검증할 수 있도록 결정론적인 mock 응답을 만든다.
        return EnrichmentResponse(
            summary = "${request.title} discusses $firstSentence.",
            whyItMatters = "This matters because ${request.source} is connected to $primaryTopic and may affect how technical teams understand the topic.",
            suggestedTopics = request.topics.ifEmpty { listOf(primaryTopic) },
            suggestedPrimaryCategory = primaryTopic,
            suggestedImportanceScore = 70
        )
    }
}
