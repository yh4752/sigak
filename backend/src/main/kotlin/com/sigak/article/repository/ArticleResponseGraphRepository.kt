package com.sigak.article.repository

import com.sigak.article.domain.ArticleEntity

interface ArticleResponseGraphRepository {
    fun fetchArticleResponseGraph(articles: List<ArticleEntity>)
}
