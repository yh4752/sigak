package com.sigak.search.graph

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/internal/graph-projections/articles")
@Tag(name = "Internal Graph Projection", description = "Internal Neo4j graph projection APIs.")
class ArticleGraphProjectionController(
    private val rebuildService: ArticleGraphProjectionRebuildService
) {

    @PostMapping("/rebuild")
    @Operation(
        summary = "Rebuild article graph projection",
        description = "Recreates Neo4j Article, Topic, HAS_TOPIC, and RELATED_TO projection data from PostgreSQL."
    )
    fun rebuild(): ArticleGraphProjectionRebuildResponse =
        rebuildService.rebuild()
}
