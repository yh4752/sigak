package com.sigak.article.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Technical news article response used by list, search, and detail APIs.")
data class ArticleResponse(
    @field:Schema(description = "Positive article identifier.", example = "1")
    val id: Long,
    @field:Schema(description = "Article display title.", example = "OpenAI Releases Agent Evaluation Toolkit")
    val title: String,
    @field:Schema(description = "Human-readable source name.", example = "OpenAI")
    val source: String,
    @field:Schema(description = "Original article URL.", example = "https://example.com/articles/openai-agent-evals")
    val url: String,
    @field:Schema(description = "UTC publication timestamp in ISO-8601 format.", example = "2026-05-01T09:00:00Z")
    val publishedAt: String,
    @field:Schema(description = "Technical event type.", example = "OFFICIAL_ANNOUNCEMENT")
    val eventType: String,
    @field:Schema(description = "Main technical category.", example = "AI")
    val primaryCategory: String,
    @field:Schema(description = "Detailed technical concepts connected to the article.", example = "[\"LLM agents\", \"evaluation\", \"production AI\"]")
    val topics: List<String>,
    @field:Schema(description = "Short factual article summary.")
    val summary: String,
    @field:Schema(description = "Explanation of why this article matters for technical readers.")
    val whyItMatters: String,
    @field:Schema(description = "Curated importance score from 0 to 100.", example = "88", minimum = "0", maximum = "100")
    val importanceScore: Int,
    @field:Schema(description = "IDs of related articles.", example = "[3, 5]")
    val relatedArticleIds: List<Long>
)
