package com.sigak.collection.service

import com.sigak.collection.domain.CollectedArticle
import com.sigak.collection.domain.SourceType
import kotlin.test.Test
import kotlin.test.assertEquals

class ArticleNormalizerTest {

    private val normalizer = ArticleNormalizer()

    @Test
    fun normalizeCollectedArticleIntoEnrichmentRequest() {
        val collectedArticle = CollectedArticle(
            sourceName = "arXiv cs.AI",
            sourceType = SourceType.ARXIV,
            externalId = "http://arxiv.org/abs/2605.00001v1",
            url = "http://arxiv.org/abs/2605.00001v1",
            canonicalUrl = "http://arxiv.org/abs/2605.00001v1",
            title = "Evaluating Retrieval Agents",
            publishedAt = "2026-05-05T00:00:00Z",
            authorNames = listOf("Ada Lovelace"),
            rawContent = "We study retrieval agents.",
            extractedText = "We study retrieval agents."
        )

        val request = normalizer.toEnrichmentRequest(collectedArticle)

        assertEquals("Evaluating Retrieval Agents", request.title)
        assertEquals("arXiv cs.AI", request.source)
        assertEquals("http://arxiv.org/abs/2605.00001v1", request.url)
        assertEquals("2026-05-05T00:00:00Z", request.publishedAt)
        assertEquals(listOf("CS_RESEARCH"), request.topics)
        assertEquals("We study retrieval agents.", request.rawContent)
    }
}
