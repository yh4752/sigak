package com.sigak.article.controller

import com.sigak.article.dto.ArticleResponse
import com.sigak.article.dto.ArticlePublicGraphContextResponse
import com.sigak.article.service.ArticlePublicGraphContextNotFoundException
import com.sigak.article.service.ArticlePublicGraphContextService
import com.sigak.article.service.ArticleService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/articles")
@Tag(name = "Articles", description = "Technical news article search and detail APIs.")
class ArticleController(
    private val articleService: ArticleService,
    private val articlePublicGraphContextService: ArticlePublicGraphContextService
) {

    @GetMapping
    @Operation(
        summary = "List or search articles",
        description = "Returns persisted curated articles. When query is provided, Elasticsearch keyword candidates and Qdrant vector candidates are fused with reciprocal rank fusion by default, then PostgreSQL reloads the final public responses. If both projection paths fail, PostgreSQL field filtering is used as the fallback while keeping the public response shape stable."
    )
    @ApiResponse(responseCode = "200", description = "Article list returned.")
    fun getArticles(
        @Parameter(description = "Optional case-insensitive keyword query.")
        @RequestParam(required = false)
        query: String?,
        @Parameter(description = "Optional comma-separated article IDs for bulk lookup.")
        @RequestParam(required = false)
        ids: List<Long>?
    ): List<ArticleResponse> =
        if (ids.isNullOrEmpty()) {
            articleService.getArticles(query)
        } else {
            articleService.getApiReadyArticlesByIds(ids)
        }

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

    @GetMapping("/{id}/graph-context")
    @Operation(
        summary = "Get article graph context",
        description = "Returns public-safe graph context for an article detail page. Neo4j projection context is optional and does not change the article detail response shape."
    )
    @ApiResponse(responseCode = "200", description = "Article graph context returned.")
    @ApiResponse(responseCode = "400", description = "Invalid article ID.")
    @ApiResponse(responseCode = "404", description = "Article not found.")
    fun getArticleGraphContext(
        @Parameter(description = "Article ID.")
        @PathVariable
        id: Long
    ): ArticlePublicGraphContextResponse =
        articlePublicGraphContextService.getContext(id)

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleIllegalArgumentException(exception: IllegalArgumentException): Map<String, String> =
        mapOf("message" to exception.message.orEmpty())

    @ExceptionHandler(ArticlePublicGraphContextNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleGraphContextNotFound(exception: ArticlePublicGraphContextNotFoundException): Map<String, String> =
        mapOf("message" to exception.message.orEmpty())
}
