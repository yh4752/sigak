package com.sigak.search.vector

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/internal/search-metrics/article-vectors")
@Tag(name = "Internal Vector Search Metrics", description = "Local vector search latency summaries for MVP development.")
class ArticleVectorSearchMetricsController(
    private val metricsRecorder: ArticleVectorSearchMetricsRecorder
) {

    @GetMapping
    @Operation(
        summary = "Get article vector search metrics",
        description = "Returns in-memory embedding, Qdrant, article reload, and total latency metrics collected since backend startup."
    )
    fun getMetrics(): ArticleVectorSearchMetricsResponse =
        metricsRecorder.summarize()
}
