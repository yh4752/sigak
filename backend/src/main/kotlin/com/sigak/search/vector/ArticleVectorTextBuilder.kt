package com.sigak.search.vector

import com.sigak.article.dto.ArticleResponse
import org.springframework.stereotype.Component

@Component
class ArticleVectorTextBuilder {

    fun build(article: ArticleResponse, sourceText: String = article.sourceExcerpt): String =
        listOf(
            "Title: ${article.title}",
            "Excerpt: $sourceText",
            "Category: ${article.primaryCategory}",
            "Topics: ${article.topics.joinToString(", ")}",
            "Event type: ${article.eventType}"
        ).joinToString(separator = "\n")
}
