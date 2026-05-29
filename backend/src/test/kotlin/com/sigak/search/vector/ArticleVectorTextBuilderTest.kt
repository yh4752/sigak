package com.sigak.search.vector

import com.sigak.article.dto.ArticleResponse
import kotlin.test.Test
import kotlin.test.assertEquals

class ArticleVectorTextBuilderTest {

    private val builder = ArticleVectorTextBuilder()

    @Test
    fun buildsStableEmbeddingInputFromSearchRelevantArticleFields() {
        val article = ArticleResponse(
            id = 3,
            title = "Critical Package Registry Attack Targets AI Toolchains",
            source = "Security Advisory Board",
            url = "https://example.com/articles/ai-toolchain-package-attack",
            publishedAt = "2026-05-03T15:45:00Z",
            eventType = "SECURITY",
            primaryCategory = "SECURITY",
            topics = listOf("supply chain security", "AI tooling"),
            summary = "A coordinated package registry attack targeted developer environments.",
            whyItMatters = "AI development stacks combine packages, credentials, and automation.",
            importanceScore = 93,
            relatedArticleIds = listOf(1, 5)
        )

        val text = builder.build(article)

        assertEquals(
            """
            Title: Critical Package Registry Attack Targets AI Toolchains
            Summary: A coordinated package registry attack targeted developer environments.
            Why it matters: AI development stacks combine packages, credentials, and automation.
            Category: SECURITY
            Topics: supply chain security, AI tooling
            Event type: SECURITY
            """.trimIndent(),
            text
        )
    }
}
