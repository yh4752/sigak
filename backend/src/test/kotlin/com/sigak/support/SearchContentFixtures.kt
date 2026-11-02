package com.sigak.support

import com.sigak.article.dto.ArticleResponse
import com.sigak.article.service.ArticleSearchContent
import org.mockito.stubbing.OngoingStubbing

fun OngoingStubbing<List<ArticleSearchContent>>.thenReturnSearchArticles(
    articles: List<ArticleResponse>
): OngoingStubbing<List<ArticleSearchContent>> =
    thenReturn(articles.map { ArticleSearchContent(it, it.sourceExcerpt) })
