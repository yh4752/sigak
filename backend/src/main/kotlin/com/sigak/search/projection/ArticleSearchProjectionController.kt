package com.sigak.search.projection

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/internal/search-projections/articles")
@Tag(name = "Internal Search Projections", description = "Local rebuild triggers for search projection stores.")
class ArticleSearchProjectionController(
    private val rebuildService: ArticleSearchProjectionRebuildService
) {

    @PostMapping("/rebuild")
    @Operation(
        summary = "Rebuild article search projection",
        description = "Indexes API-ready articles from PostgreSQL into the Elasticsearch article projection."
    )
    fun rebuild(): ArticleSearchProjectionRebuildResponse =
        rebuildService.rebuild()
}
