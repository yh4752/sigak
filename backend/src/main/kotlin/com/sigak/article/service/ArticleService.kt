package com.sigak.article.service

import com.sigak.article.dto.ArticleResponse
import org.springframework.stereotype.Service

@Service
class ArticleService {

    private val articles = listOf(
        ArticleResponse(
            id = 1L,
            title = "OpenAI Releases Agent Evaluation Toolkit",
            source = "OpenAI",
            url = "https://example.com/articles/openai-agent-evals",
            publishedAt = "2026-05-01T09:00:00Z",
            eventType = "OFFICIAL_ANNOUNCEMENT",
            primaryCategory = "AI",
            topics = listOf("LLM agents", "evaluation", "production AI"),
            summary = "OpenAI introduced a toolkit for evaluating agent behavior in multi-step workflows.",
            whyItMatters = "Agent evaluation is becoming a practical requirement as teams move from demos to production workflows.",
            importanceScore = 88,
            relatedArticleIds = listOf(3L, 5L)
        ),
        ArticleResponse(
            id = 2L,
            title = "PostgreSQL Adds Native Vector Index Improvements",
            source = "PostgreSQL Weekly",
            url = "https://example.com/articles/postgres-vector-indexes",
            publishedAt = "2026-05-02T11:30:00Z",
            eventType = "RELEASE",
            primaryCategory = "DATA",
            topics = listOf("PostgreSQL", "vector search", "database indexing"),
            summary = "A PostgreSQL release improved vector index performance for retrieval-heavy workloads.",
            whyItMatters = "Better vector indexing makes it easier to build search and RAG features without adding infrastructure too early.",
            importanceScore = 82,
            relatedArticleIds = listOf(1L, 5L)
        ),
        ArticleResponse(
            id = 3L,
            title = "Critical Package Registry Attack Targets AI Toolchains",
            source = "Security Advisory Board",
            url = "https://example.com/articles/ai-toolchain-package-attack",
            publishedAt = "2026-05-03T15:45:00Z",
            eventType = "SECURITY",
            primaryCategory = "SECURITY",
            topics = listOf("supply chain security", "package registry", "AI tooling"),
            summary = "A coordinated package registry attack targeted developer environments that install AI tooling.",
            whyItMatters = "AI development stacks often combine fast-moving packages, credentials, and automation, which raises the blast radius of supply chain attacks.",
            importanceScore = 93,
            relatedArticleIds = listOf(1L, 4L)
        ),
        ArticleResponse(
            id = 4L,
            title = "New Research Maps Failure Modes in Graph RAG Systems",
            source = "arXiv",
            url = "https://example.com/articles/graph-rag-failure-modes",
            publishedAt = "2026-05-04T08:20:00Z",
            eventType = "RESEARCH",
            primaryCategory = "CS_RESEARCH",
            topics = listOf("Graph RAG", "knowledge graphs", "retrieval quality"),
            summary = "Researchers categorized common failure modes in Graph RAG systems and proposed evaluation criteria.",
            whyItMatters = "Understanding graph retrieval failures helps teams design relationship-aware insight features with better evidence quality.",
            importanceScore = 86,
            relatedArticleIds = listOf(1L, 5L)
        ),
        ArticleResponse(
            id = 5L,
            title = "Kubernetes Project Updates Long-Term Support Policy",
            source = "Cloud Native Computing Foundation",
            url = "https://example.com/articles/kubernetes-lts-policy",
            publishedAt = "2026-05-05T10:10:00Z",
            eventType = "NEWS",
            primaryCategory = "INFRA_CLOUD",
            topics = listOf("Kubernetes", "release policy", "platform operations"),
            summary = "The Kubernetes project updated its support policy for production operators managing long-lived clusters.",
            whyItMatters = "Support windows shape upgrade planning, security posture, and operational cost for infrastructure teams.",
            importanceScore = 78,
            relatedArticleIds = listOf(2L, 3L)
        )
    )

    fun getArticles(query: String? = null): List<ArticleResponse> {
        val normalizedQuery = query?.trim()

        if (normalizedQuery.isNullOrBlank()) {
            return articles
        }

        return articles.filter { article -> article.matches(normalizedQuery) }
    }

    fun getArticle(id: Long): ArticleResponse? = articles.firstOrNull { it.id == id }

    private fun ArticleResponse.matches(query: String): Boolean =
        title.contains(query, ignoreCase = true) ||
            summary.contains(query, ignoreCase = true) ||
            primaryCategory.contains(query, ignoreCase = true) ||
            topics.any { topic -> topic.contains(query, ignoreCase = true) }
}
