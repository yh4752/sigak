package com.sigak.collection.service

import com.sigak.article.domain.ArticleEnrichmentEntity
import com.sigak.article.domain.ArticleEntity
import com.sigak.article.domain.ArticleRawContentEntity
import com.sigak.article.domain.ArticleTopicEntity
import com.sigak.article.domain.EventType
import com.sigak.article.domain.PrimaryCategory
import com.sigak.article.domain.ProcessingStatus
import com.sigak.article.repository.ArticleRepository
import com.sigak.collection.domain.CollectedArticle
import com.sigak.collection.domain.SourceType
import com.sigak.collection.dto.EnrichmentResponse
import com.sigak.source.domain.NewsSourceEntity
import com.sigak.source.repository.NewsSourceRepository
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CollectedArticlePersistenceService(
    private val articleRepository: ArticleRepository,
    private val newsSourceRepository: NewsSourceRepository
) : CollectedArticlePublisher {

    @Transactional
    override fun publish(article: CollectedArticle, enrichment: EnrichmentResponse): Long {
        val source = findOrCreateSource(article)
        val publishedAt = parsePublishedAt(article.publishedAt)
        val canonicalUrl = article.canonicalUrl.ifBlank { article.url }.trim()
        val url = article.url.ifBlank { canonicalUrl }.trim()

        val duplicate = findDuplicateArticle(source.sourceKey, article, canonicalUrl, url, publishedAt)
        if (duplicate != null) {
            return requireNotNull(duplicate.id)
        }

        val savedArticle = ArticleEntity(
            source = source,
            externalId = article.externalId.ifBlank { null },
            title = article.title.trim(),
            url = url,
            canonicalUrl = canonicalUrl,
            publishedAt = publishedAt,
            eventType = eventTypeFor(article, enrichment),
            primaryCategory = primaryCategoryFor(article, enrichment),
            importanceScore = enrichment.suggestedImportanceScore.coerceIn(0, 100),
            processingStatus = ProcessingStatus.PUBLISHED,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        savedArticle.rawContent = ArticleRawContentEntity(
            article = savedArticle,
            rawContent = article.rawContent,
            extractedText = article.extractedText,
            collectedAt = Instant.now()
        )
        savedArticle.enrichments.add(
            ArticleEnrichmentEntity(
                article = savedArticle,
                summary = enrichment.summary,
                whyItMatters = enrichment.whyItMatters,
                suggestedPrimaryCategory = enrichment.suggestedPrimaryCategory,
                suggestedImportanceScore = enrichment.suggestedImportanceScore.coerceIn(0, 100),
                modelName = "mock-enrichment",
                promptVersion = "collection-v1",
                current = true,
                enrichedAt = Instant.now()
            )
        )
        enrichment.suggestedTopics
            .map { topic -> topic.trim() }
            .filter { topic -> topic.isNotBlank() }
            .distinct()
            .take(8)
            .forEachIndexed { index, topic ->
                savedArticle.topics.add(
                    ArticleTopicEntity(
                        article = savedArticle,
                        topic = topic,
                        position = index
                    )
                )
            }

        return requireNotNull(articleRepository.save(savedArticle).id)
    }

    private fun findOrCreateSource(article: CollectedArticle): NewsSourceEntity {
        val sourceKey = sourceKeyFor(article.sourceName)
        return newsSourceRepository.findBySourceKey(sourceKey)
            ?: newsSourceRepository.save(
                NewsSourceEntity(
                    sourceKey = sourceKey,
                    name = article.sourceName,
                    type = article.sourceType.name,
                    url = article.url,
                    categoryHint = article.categoryHint
                )
            )
    }

    private fun findDuplicateArticle(
        sourceKey: String,
        article: CollectedArticle,
        canonicalUrl: String,
        url: String,
        publishedAt: Instant
    ): ArticleEntity? =
        articleRepository.findFirstByCanonicalUrlOrUrlOrderByIdAsc(canonicalUrl, url)
            ?: article.externalId.trim()
                .takeIf { externalId -> externalId.isNotBlank() }
                ?.let { externalId ->
                    articleRepository.findFirstBySourceSourceKeyAndExternalIdOrderByIdAsc(sourceKey, externalId)
                }
            ?: articleRepository.findFirstBySourceSourceKeyAndTitleIgnoreCaseAndPublishedAtOrderByIdAsc(
                sourceKey = sourceKey,
                title = article.title.trim(),
                publishedAt = publishedAt
            )

    private fun sourceKeyFor(sourceName: String): String =
        sourceName
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "manual-source" }

    private fun parsePublishedAt(value: String): Instant {
        val trimmed = value.trim()
        return parseInstant(trimmed)
            ?: parseRfc1123(trimmed)
            ?: Instant.EPOCH
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

    private fun primaryCategoryFor(article: CollectedArticle, enrichment: EnrichmentResponse): PrimaryCategory =
        listOf(enrichment.suggestedPrimaryCategory, article.categoryHint)
            .firstNotNullOfOrNull { value -> value.toPrimaryCategoryOrNull() }
            ?: PrimaryCategory.SOFTWARE_ENGINEERING

    private fun eventTypeFor(article: CollectedArticle, enrichment: EnrichmentResponse): EventType {
        val category = primaryCategoryFor(article, enrichment)
        return when {
            article.sourceType == SourceType.ARXIV -> EventType.RESEARCH
            category == PrimaryCategory.CS_RESEARCH -> EventType.RESEARCH
            category == PrimaryCategory.SECURITY -> EventType.SECURITY
            else -> EventType.NEWS
        }
    }

    private fun String?.toPrimaryCategoryOrNull(): PrimaryCategory? =
        this
            ?.trim()
            ?.uppercase()
            ?.let { value -> runCatching { PrimaryCategory.valueOf(value) }.getOrNull() }
}
