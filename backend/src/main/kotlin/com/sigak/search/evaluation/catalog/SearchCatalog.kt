package com.sigak.search.evaluation.catalog

data class SearchCatalog(
    val version: Int = 1,
    val catalogId: String,
    val generatedAt: String,
    val source: String,
    val articles: List<SearchCatalogArticle>
)

data class SearchCatalogArticle(
    val id: Long,
    val title: String,
    val category: String,
    val topics: List<String>,
    val summaryKo: String,
    val whyItMattersKo: String?,
    val publishedAt: String?,
    val source: String?,
    val url: String?
)
