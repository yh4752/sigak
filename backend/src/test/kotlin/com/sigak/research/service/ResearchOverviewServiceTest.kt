package com.sigak.research.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sigak.research.config.ResearchArtifactProperties
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class ResearchOverviewServiceTest {

    @TempDir
    lateinit var tempDir: Path

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun mapsResearchArtifactsToOverviewResponse() {
        writeArtifact(
            "results/retrieval/latest/metrics.comparison.json",
            """
            {
              "catalogId": "api-ready-2026-06-02",
              "catalogArticleCount": 6,
              "evaluatedQueryCount": 3,
              "k": 5,
              "generatedAt": "2026-06-02T15:06:29.546Z",
              "systems": [
                {
                  "system": "keyword",
                  "attemptedQueryCount": 3,
                  "completedQueryCount": 3,
                  "failedQueryCount": 0,
                  "degradedQueryCount": 0,
                  "macroTop1StrongHit": 1.0,
                  "macroRecallAtK": 0.6666666667,
                  "macroMrrAtK": 1.0,
                  "averageLatencyMs": 21.6666666667
                },
                {
                  "system": "hybrid",
                  "attemptedQueryCount": 3,
                  "completedQueryCount": 3,
                  "failedQueryCount": 0,
                  "degradedQueryCount": 0,
                  "macroTop1StrongHit": 0.6666666667,
                  "macroRecallAtK": 0.8333333333,
                  "macroMrrAtK": 0.8333333333,
                  "averageLatencyMs": 15.0
                }
              ],
              "warnings": ["label set is smaller than 10 reviewed queries"]
            }
            """.trimIndent()
        )
        writeArtifact(
            "results/graph/latest/graph-context.metrics.summary.json",
            """
            {
              "catalogId": "api-ready-2026-06-02",
              "catalogArticleCount": 6,
              "evaluatedQueryCount": 3,
              "k": 5,
              "generatedAt": "2026-06-04T03:21:51.802Z",
              "macroSearchPositiveCoverageAtK": 0.6666666667,
              "macroRelatedBaselineCoverageAtK": 0.3333333333,
              "macroGraphContextCoverageAtK": 0.3333333333,
              "macroGraphReasonedCoverageAtK": 0.3333333333,
              "graphContextFailureRate": 0.0,
              "emptyContextRate": 0.0,
              "averageGraphLatencyMs": 33.5333333333,
              "warnings": ["graph latency is measured as public API round-trip"]
            }
            """.trimIndent()
        )
        writeArtifact(
            "results/graph/latest/graph-context.metrics.by-query.json",
            """
            [
              {
                "query": "graph rag failure",
                "intent": "Find graph RAG failure analysis.",
                "searchPositiveCoverageAtK": 0.5,
                "relatedBaselineCoverageAtK": 0.0,
                "graphContextCoverageAtK": 0.5,
                "graphReasonedCoverageAtK": 0.5,
                "reasonCoverage": 1.0,
                "topicContextCoverage": 1.0,
                "averageGraphLatencyMs": 8.8,
                "searchMissedPositiveIds": [1]
              }
            ]
            """.trimIndent()
        )
        writeArtifact(
            "datasets/raw/articles.catalog.api-ready-2026-06-05.json",
            """
            {
              "catalogId": "api-ready-2026-06-05",
              "generatedAt": "2026-06-04T15:45:30.641155Z",
              "articles": [
                {"id": 1, "title": "A"},
                {"id": 2, "title": "B"}
              ]
            }
            """.trimIndent()
        )
        val service = newService()

        val response = service.getOverview()

        assertTrue(response.retrieval.available)
        assertEquals("api-ready-2026-06-02", response.retrieval.catalogId)
        assertEquals(2, response.retrieval.systems.size)
        assertEquals("hybrid", response.retrieval.systems[1].name)
        assertTrue(response.graph.available)
        assertEquals(0.3333333333, response.graph.macroGraphContextCoverageAtK)
        assertEquals("graph rag failure", response.graph.queryEvidence.single().query)
        assertTrue(response.expandedCatalog.available)
        assertEquals(2, response.expandedCatalog.articleCount)
        assertEquals("draft_only", response.expandedCatalog.labelStatus)
        assertEquals("blocked_until_reviewed_labels", response.expandedCatalog.benchmarkStatus)
        assertTrue(response.limitations.isNotEmpty())
        assertNotNull(response.generatedAt)
    }

    @Test
    fun preservesNullQueryCoverageForCommittedGraphArtifacts() {
        writeArtifact(
            "results/graph/latest/graph-context.metrics.summary.json",
            """
            {
              "catalogId": "api-ready-2026-06-02",
              "catalogArticleCount": 6,
              "evaluatedQueryCount": 3,
              "k": 5,
              "generatedAt": "2026-06-04T03:21:51.802Z"
            }
            """.trimIndent()
        )
        writeArtifact(
            "results/graph/latest/graph-context.metrics.by-query.json",
            """
            [
              {
                "query": "postgres vector search",
                "intent": "Find PostgreSQL vector search improvements.",
                "searchPositiveCoverageAtK": 0.5,
                "relatedBaselineCoverageAtK": 0.0,
                "graphContextCoverageAtK": 0.0,
                "graphReasonedCoverageAtK": 0.0,
                "reasonCoverage": null,
                "topicContextCoverage": 1.0,
                "averageGraphLatencyMs": 11.2,
                "searchMissedPositiveIds": [4]
              }
            ]
            """.trimIndent()
        )
        val service = newService()

        val response = service.getOverview()

        assertTrue(response.graph.available)
        assertEquals(null, response.graph.queryEvidence.single().reasonCoverage)
    }

    @Test
    fun keepsGraphSectionAvailableWhenByQueryArtifactShapeIsInvalid() {
        writeArtifact(
            "results/graph/latest/graph-context.metrics.summary.json",
            """
            {
              "catalogId": "api-ready-2026-06-02",
              "catalogArticleCount": 6,
              "evaluatedQueryCount": 3,
              "k": 5,
              "generatedAt": "2026-06-04T03:21:51.802Z"
            }
            """.trimIndent()
        )
        writeArtifact(
            "results/graph/latest/graph-context.metrics.by-query.json",
            """
            [
              {
                "query": "graph rag failure"
              }
            ]
            """.trimIndent()
        )
        val service = newService()

        val response = service.getOverview()

        assertTrue(response.graph.available)
        assertEquals(emptyList(), response.graph.queryEvidence)
        assertEquals(
            listOf("Artifact could not be mapped: results/graph/latest/graph-context.metrics.by-query.json"),
            response.graph.warnings
        )
    }

    @Test
    fun degradesMissingArtifactsBySection() {
        val service = newService()

        val response = service.getOverview()

        assertEquals(false, response.retrieval.available)
        assertEquals(
            "Artifact not found: results/retrieval/latest/metrics.comparison.json",
            response.retrieval.unavailableReason
        )
        assertEquals(emptyList(), response.retrieval.systems)
        assertEquals(false, response.graph.available)
        assertEquals(emptyList(), response.graph.queryEvidence)
        assertEquals(false, response.expandedCatalog.available)
    }

    private fun writeArtifact(relativePath: String, content: String) {
        val artifactFile = tempDir.resolve(relativePath)
        artifactFile.parent.createDirectories()
        artifactFile.writeText(content)
    }

    private fun newService(): ResearchOverviewService =
        ResearchOverviewService(
            artifactReader = ResearchArtifactReader(
                properties = ResearchArtifactProperties(artifactRoot = tempDir.toString()),
                objectMapper = objectMapper
            )
        )
}
