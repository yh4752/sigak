package com.sigak.search.graph

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/internal/graph/articles")
@Tag(name = "Internal Article Graph", description = "Internal Neo4j article graph context APIs.")
class ArticleGraphContextController(
    private val service: ArticleGraphContextService
) {

    @GetMapping("/{id}/context")
    @Operation(
        summary = "Get article graph context",
        description = "Returns internal graph context for an article from the Neo4j projection."
    )
    fun getContext(@PathVariable id: Long): ArticleGraphContextResponse =
        service.getContext(id)

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleIllegalArgumentException(exception: IllegalArgumentException): Map<String, String> =
        mapOf("message" to exception.message.orEmpty())

    @ExceptionHandler(ArticleGraphContextNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleNotFound(exception: ArticleGraphContextNotFoundException): Map<String, String> =
        mapOf("message" to exception.message.orEmpty())
}
