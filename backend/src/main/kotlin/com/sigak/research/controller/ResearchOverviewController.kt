package com.sigak.research.controller

import com.sigak.research.dto.ResearchOverviewResponse
import com.sigak.research.service.ResearchOverviewService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/research")
@Tag(name = "Research Dashboard", description = "Portfolio research metrics generated from local experiment artifacts.")
class ResearchOverviewController(
    private val overviewService: ResearchOverviewService
) {

    @GetMapping("/overview")
    @Operation(
        summary = "Get research dashboard overview",
        description = "Returns retrieval, graph-aware context, expanded catalog, and limitation evidence for the portfolio research dashboard."
    )
    fun getOverview(): ResearchOverviewResponse =
        overviewService.getOverview()
}
