package com.sigak.article.service

import com.sigak.article.dto.ArticleResponse

// 공개 소개글과 내부 검색 입력을 분리해 본문이 API 응답에 노출되지 않게 한다.
data class ArticleSearchContent(
    val article: ArticleResponse,
    val sourceText: String
)
