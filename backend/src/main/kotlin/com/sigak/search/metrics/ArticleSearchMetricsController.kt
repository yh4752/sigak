package com.sigak.search.metrics

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/internal/search-metrics/articles")
@Tag(name = "Internal Search Metrics", description = "Local article search metric summaries for MVP development.")
class ArticleSearchMetricsController(
    private val metricsRecorder: ArticleSearchMetricsRecorder
) {

    @GetMapping
    @Operation(
        summary = "Get article search metrics",
        description = "Returns in-memory keyword search latency and fallback metrics collected since the backend process started."
    )
    fun getMetrics(): ArticleSearchMetricsResponse =
        metricsRecorder.summarize()
}
