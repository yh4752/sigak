package com.sigak.search.evaluation.catalog

import com.sigak.article.dto.ArticleResponse
import com.sigak.article.service.ArticleService
import org.springframework.stereotype.Component

fun interface SearchCatalogArticleReader {
    fun readApiReadyArticles(): List<ArticleResponse>
}

@Component
class ArticleServiceSearchCatalogArticleReader(
    private val articleService: ArticleService
) : SearchCatalogArticleReader {

    override fun readApiReadyArticles(): List<ArticleResponse> =
        // catalog export 기준이 public API-ready article과 갈라지지 않도록 ArticleService 경계를 재사용한다.
        articleService.getArticles(null)
}
