package com.sigak.collection.service

import com.sigak.collection.domain.CollectedArticle
import com.sigak.collection.domain.SourceType
import com.sigak.collection.dto.EnrichmentRequest
import org.springframework.stereotype.Component

@Component
class ArticleNormalizer {

    fun toEnrichmentRequest(article: CollectedArticle): EnrichmentRequest =
        EnrichmentRequest(
            title = article.title.trim(),
            source = article.sourceName,
            url = article.canonicalUrl.ifBlank { article.url },
            publishedAt = article.publishedAt,
            topics = listOf(categoryFor(article)),
            rawContent = article.extractedText.ifBlank { article.rawContent }
        )

    private fun categoryFor(article: CollectedArticle): String =
        when (article.sourceType) {
            SourceType.ARXIV -> "CS_RESEARCH"
            SourceType.RSS_ATOM -> "SOFTWARE_ENGINEERING"
            SourceType.MANUAL -> "SOFTWARE_ENGINEERING"
        }
}
