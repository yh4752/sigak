package com.sigak.collection.service

import com.sigak.collection.domain.CollectedArticle
import com.sigak.collection.domain.SourceType
import com.sigak.collection.dto.EnrichmentRequest
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
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
        val trimmed = publishedAt.trim()
        if (trimmed.isBlank()) {
            return trimmed
        }

        return parseInstant(trimmed)?.toString()
            ?: parseRfc1123(trimmed)?.toString()
            ?: trimmed
    }

    private fun parseInstant(value: String): Instant? =
        try {
            Instant.parse(value)
        } catch (_: DateTimeParseException) {
            null
        }

    private fun parseRfc1123(value: String): Instant? =
        try {
            ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
        } catch (_: DateTimeParseException) {
            null
        }

    private fun categoryFor(article: CollectedArticle): String =
        article.categoryHint?.trim()?.takeIf { it.isNotBlank() } ?: when (article.sourceType) {
            SourceType.ARXIV -> "CS_RESEARCH"
            SourceType.RSS_ATOM -> "SOFTWARE_ENGINEERING"
            SourceType.MANUAL -> "SOFTWARE_ENGINEERING"
        }
}
