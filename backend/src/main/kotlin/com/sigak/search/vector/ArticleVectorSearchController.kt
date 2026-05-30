package com.sigak.search.vector

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/internal/vector-search/articles")
@Tag(name = "Internal Vector Search", description = "Internal article vector search APIs backed by Qdrant.")
class ArticleVectorSearchController(
    private val service: ArticleVectorSearchService
) {

    @PostMapping
    @Operation(
        summary = "Search articles with vector similarity",
        description = "Embeds a query, searches the article vector collection, and reloads API-ready articles."
    )
    fun searchArticles(@RequestBody request: ArticleVectorSearchRequest): ArticleVectorSearchResponse =
        service.search(request)

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleIllegalArgumentException(exception: IllegalArgumentException): Map<String, String> =
        mapOf("message" to exception.message.orEmpty())
}
