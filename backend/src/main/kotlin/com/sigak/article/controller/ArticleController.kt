package com.sigak.article.controller

import com.sigak.article.dto.ArticleResponse
import com.sigak.article.service.ArticleService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/articles")
@Tag(name = "Articles", description = "Technical news article search and detail APIs.")
class ArticleController(
    private val articleService: ArticleService
) {

    @GetMapping
    @Operation(
        summary = "List or search articles",
        description = "Returns persisted curated articles. When query is provided, filters articles by title, summary, primary category, and topics while keeping the public response shape stable for a later Elasticsearch-backed search implementation."
    )
    @ApiResponse(responseCode = "200", description = "Article list returned.")
    fun getArticles(
        @Parameter(description = "Optional case-insensitive keyword query.")
        @RequestParam(required = false)
        query: String?
    ): List<ArticleResponse> =
        articleService.getArticles(query)

    @GetMapping("/{id}")
    @Operation(
        summary = "Get article detail",
        description = "Returns one article by ID, including summary, importance, why-it-matters insight, topics, and related article IDs."
    )
    @ApiResponse(responseCode = "200", description = "Article found.")
    @ApiResponse(responseCode = "404", description = "Article not found.")
    fun getArticle(
        @Parameter(description = "Article ID.")
        @PathVariable
        id: Long
    ): ArticleResponse =
        articleService.getArticle(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Article not found")
}
