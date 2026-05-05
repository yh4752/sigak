package com.sigak.collection.service

import com.sigak.collection.domain.CollectedArticle
import com.sigak.collection.domain.SourceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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

    @Test
    fun normalizeUsesCategoryHintForOpenAiRssTopic() {
        val collectedArticle = collectedArticle(
            sourceName = "OpenAI Blog",
            sourceType = SourceType.RSS_ATOM,
            categoryHint = "AI"
        )

        val request = normalizer.toEnrichmentRequest(collectedArticle)

        assertEquals(listOf("AI"), request.topics)
    }

    @Test
    fun normalizeUsesCategoryHintForGithubRssTopic() {
        val collectedArticle = collectedArticle(
            sourceName = "GitHub Blog",
            sourceType = SourceType.RSS_ATOM,
            categoryHint = "DEVTOOLS"
        )

        val request = normalizer.toEnrichmentRequest(collectedArticle)

        assertEquals(listOf("DEVTOOLS"), request.topics)
    }

    @Test
    fun normalizeFallsBackToSourceTypeTopicForArxiv() {
        val collectedArticle = collectedArticle(
            sourceName = "arXiv cs.AI",
            sourceType = SourceType.ARXIV
        )

        val request = normalizer.toEnrichmentRequest(collectedArticle)

        assertEquals(listOf("CS_RESEARCH"), request.topics)
    }

    @Test
    fun normalizeRejectsBlankContent() {
        val collectedArticle = collectedArticle(
            rawContent = "   ",
            extractedText = "\n\t"
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            normalizer.toEnrichmentRequest(collectedArticle)
        }

        assertEquals("Collected article content is required for enrichment", exception.message)
    }

    @Test
    fun normalizeTrimsExtractedTextBeforeRawContent() {
        val collectedArticle = collectedArticle(
            rawContent = " Raw fallback. ",
            extractedText = " Extracted content. "
        )

        val request = normalizer.toEnrichmentRequest(collectedArticle)

        assertEquals("Extracted content.", request.rawContent)
    }

    @Test
    fun normalizeConvertsRssPublishedDateToIsoInstant() {
        val collectedArticle = collectedArticle(
            sourceType = SourceType.RSS_ATOM,
            rawContent = "Raw article content.",
            extractedText = "Extracted article content.",
            publishedAt = "Tue, 05 May 2026 09:00:00 GMT"
        )

        val request = normalizer.toEnrichmentRequest(collectedArticle)

        assertEquals("2026-05-05T09:00:00Z", request.publishedAt)
    }

    private fun collectedArticle(
        sourceName: String = "Example Source",
        sourceType: SourceType = SourceType.RSS_ATOM,
        categoryHint: String? = null,
        rawContent: String = "Raw article content.",
        extractedText: String = "Extracted article content.",
        publishedAt: String = "2026-05-05T00:00:00Z"
    ): CollectedArticle =
        CollectedArticle(
            sourceName = sourceName,
            sourceType = sourceType,
            externalId = "https://example.com/article",
            url = "https://example.com/article",
            canonicalUrl = "https://example.com/article",
            title = "Example Article",
            publishedAt = publishedAt,
            authorNames = emptyList(),
            rawContent = rawContent,
            extractedText = extractedText,
            categoryHint = categoryHint
        )
}
