package com.sigak.research.controller

import com.sigak.research.dto.ExpandedCatalogResearchSectionResponse
import com.sigak.research.dto.GraphResearchSectionResponse
import com.sigak.research.dto.ResearchOverviewResponse
import com.sigak.research.dto.RetrievalResearchSectionResponse
import com.sigak.research.dto.RetrievalSystemMetricResponse
import com.sigak.research.service.ResearchOverviewService
import java.time.Instant
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(ResearchOverviewController::class)
class ResearchOverviewControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var overviewService: ResearchOverviewService

    @Test
    fun returnsResearchOverview() {
        `when`(overviewService.getOverview())
            .thenReturn(
                ResearchOverviewResponse(
                    generatedAt = Instant.parse("2026-06-12T00:00:00Z"),
                    retrieval = RetrievalResearchSectionResponse(
                        available = true,
                        unavailableReason = null,
                        catalogId = "api-ready-2026-06-02",
                        catalogArticleCount = 6,
                        evaluatedQueryCount = 3,
                        k = 5,
                        generatedAt = Instant.parse("2026-06-02T15:06:29.546Z"),
                        systems = listOf(
                            RetrievalSystemMetricResponse(
                                name = "hybrid",
                                attemptedQueryCount = 3,
                                completedQueryCount = 3,
                                failedQueryCount = 0,
                                degradedQueryCount = 0,
                                macroTop1StrongHit = 0.6667,
                                macroRecallAtK = 0.8333,
                                macroMrrAtK = 0.8333,
                                averageLatencyMs = 15.0
                            )
                        ),
                        warnings = listOf("smoke benchmark")
                    ),
                    graph = GraphResearchSectionResponse(
                        available = false,
                        unavailableReason = "Artifact not found: results/graph/latest/graph-context.metrics.summary.json",
                        catalogId = null,
                        catalogArticleCount = null,
                        evaluatedQueryCount = null,
                        k = null,
                        generatedAt = null,
                        macroSearchPositiveCoverageAtK = null,
                        macroRelatedBaselineCoverageAtK = null,
                        macroGraphContextCoverageAtK = null,
                        macroGraphReasonedCoverageAtK = null,
                        graphContextFailureRate = null,
                        emptyContextRate = null,
                        averageGraphLatencyMs = null,
                        queryEvidence = emptyList(),
                        warnings = emptyList()
                    ),
                    expandedCatalog = ExpandedCatalogResearchSectionResponse(
                        available = true,
                        unavailableReason = null,
                        catalogId = "api-ready-2026-06-05",
                        generatedAt = Instant.parse("2026-06-04T15:45:30.641155Z"),
                        articleCount = 41,
                        labelStatus = "draft_only",
                        benchmarkStatus = "blocked_until_reviewed_labels",
                        warnings = listOf("Reviewed labels are required before quality claims use the expanded catalog.")
                    ),
                    limitations = listOf("Current retrieval and graph metrics are smoke evidence, not broad quality claims.")
                )
            )

        mockMvc.perform(get("/api/research/overview"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.retrieval.available").value(true))
            .andExpect(jsonPath("$.retrieval.systems[0].name").value("hybrid"))
            .andExpect(jsonPath("$.graph.available").value(false))
            .andExpect(jsonPath("$.expandedCatalog.articleCount").value(41))
            .andExpect(
                jsonPath("$.limitations[0]")
                    .value("Current retrieval and graph metrics are smoke evidence, not broad quality claims.")
            )
    }
}
