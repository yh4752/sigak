package com.sigak.search.evaluation.catalog

import com.sigak.article.dto.ArticleResponse
import java.time.Instant
import org.springframework.stereotype.Component

private const val SEARCH_CATALOG_SOURCE = "postgres-api-ready"

@Component
class SearchCatalogFactory {

    fun build(
        articles: List<ArticleResponse>,
        catalogId: String,
        generatedAt: Instant,
        limit: Int
    ): SearchCatalog {
        val selectedArticles = articles.take(limit)
        if (selectedArticles.isEmpty()) {
            throw IllegalStateException("No API-ready articles are available for search catalog export.")
        }

        return SearchCatalog(
            catalogId = catalogId,
            generatedAt = generatedAt.toString(),
            source = SEARCH_CATALOG_SOURCE,
            articles = selectedArticles.map { article -> article.toCatalogArticle() }
        )
    }

    private fun ArticleResponse.toCatalogArticle(): SearchCatalogArticle =
        // 라벨링 도구의 기존 JSON 계약을 유지하기 위해 summary/whyItMatters를 *Ko 필드로 노출한다.
        SearchCatalogArticle(
            id = id,
            title = title,
            category = primaryCategory,
            topics = topics,
            summaryKo = summary,
            whyItMattersKo = whyItMatters,
            publishedAt = publishedAt,
            source = source,
            url = url
        )
}
