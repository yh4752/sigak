package com.sigak.search.controller

import com.sigak.search.dto.SearchInfrastructureHealthResponse
import com.sigak.search.service.SearchInfrastructureHealthService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/internal/search-infrastructure")
@Tag(name = "Internal Search Infrastructure", description = "Local readiness checks for search projection stores.")
class SearchInfrastructureHealthController(
    private val searchInfrastructureHealthService: SearchInfrastructureHealthService
) {

    @GetMapping("/health")
    @Operation(
        summary = "Check search infrastructure readiness",
        description = "Returns local readiness status for Elasticsearch, Qdrant, and Neo4j before indexing and hybrid search are connected."
    )
    fun getHealth(): SearchInfrastructureHealthResponse =
        searchInfrastructureHealthService.checkHealth()
}
