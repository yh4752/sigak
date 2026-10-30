package com.sigak.search.vector

import com.sigak.article.dto.ArticleResponse
import org.springframework.stereotype.Component

@Component
class ArticleVectorTextBuilder {

    fun build(article: ArticleResponse): String =
        listOf(
            "Title: ${article.title}",
            "Excerpt: ${article.sourceExcerpt}",
            "Category: ${article.primaryCategory}",
            "Topics: ${article.topics.joinToString(", ")}",
            "Event type: ${article.eventType}"
        ).joinToString(separator = "\n")
}
