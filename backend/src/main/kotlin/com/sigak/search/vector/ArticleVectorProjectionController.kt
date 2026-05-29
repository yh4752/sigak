package com.sigak.search.vector

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/internal/search-projections/article-vectors")
@Tag(name = "Internal Vector Projections", description = "Local rebuild triggers for vector projection stores.")
class ArticleVectorProjectionController(
    private val rebuildService: ArticleVectorProjectionRebuildService
) {

    @PostMapping("/rebuild")
    @Operation(
        summary = "Rebuild article vector projection",
        description = "Embeds API-ready articles from PostgreSQL and rebuilds the Qdrant article vector projection."
    )
    fun rebuild(): ArticleVectorProjectionRebuildResponse =
        rebuildService.rebuild()
}
