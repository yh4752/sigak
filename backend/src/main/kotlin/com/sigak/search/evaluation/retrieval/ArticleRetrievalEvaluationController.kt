package com.sigak.search.evaluation.retrieval

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/internal/search-evaluation/retrieval-runs")
@Tag(name = "Internal Search Evaluation", description = "Internal retrieval benchmark APIs for local experiment runs.")
@ConditionalOnProperty(
    prefix = "sigak.internal.search-evaluation",
    name = ["enabled"],
    havingValue = "true"
)
class ArticleRetrievalEvaluationController(
    private val service: ArticleRetrievalEvaluationService
) {

    @PostMapping
    @Operation(
        summary = "Create retrieval benchmark runs",
        description = "Creates strict keyword, vector, and hybrid retrieval runs for local evaluation artifacts."
    )
    fun createRuns(@RequestBody request: ArticleRetrievalRunRequest): ArticleRetrievalRunResponse =
        service.createRuns(request)

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleIllegalArgumentException(exception: IllegalArgumentException): Map<String, String> =
        mapOf("message" to exception.message.orEmpty())

    @ExceptionHandler(IllegalStateException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleIllegalStateException(exception: IllegalStateException): Map<String, String> =
        mapOf("message" to exception.message.orEmpty())
}
