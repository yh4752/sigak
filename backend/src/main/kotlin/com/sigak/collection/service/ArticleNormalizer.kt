package com.sigak.collection.service

import com.sigak.collection.domain.CollectedArticle
import com.sigak.collection.domain.SourceType
import com.sigak.collection.dto.EnrichmentRequest
import org.springframework.stereotype.Component

@Component
class ArticleNormalizer {

    fun toEnrichmentRequest(article: CollectedArticle): EnrichmentRequest {
        val rawContent = article.extractedText.trim()
            .ifBlank { article.rawContent.trim() }
            .ifBlank { throw IllegalArgumentException("Collected article content is required for enrichment") }

        return EnrichmentRequest(
            title = article.title.trim(),
            source = article.sourceName,
            url = article.canonicalUrl.ifBlank { article.url },
            publishedAt = normalizePublishedAt(article.publishedAt),
            topics = listOf(categoryFor(article)),
            rawContent = rawContent
        )
    }

    private fun normalizePublishedAt(publishedAt: String): String {
        return PublishedAtParser.normalizeToIsoString(publishedAt)
    }

    private fun categoryFor(article: CollectedArticle): String =
        article.categoryHint?.trim()?.takeIf { it.isNotBlank() } ?: when (article.sourceType) {
            SourceType.ARXIV -> "CS_RESEARCH"
            SourceType.RSS_ATOM -> "SOFTWARE_ENGINEERING"
            SourceType.MANUAL -> "SOFTWARE_ENGINEERING"
        }
}
