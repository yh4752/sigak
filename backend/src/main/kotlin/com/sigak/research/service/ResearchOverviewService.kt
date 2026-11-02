package com.sigak.research.service

import com.fasterxml.jackson.databind.JsonNode
import com.sigak.research.dto.ExpandedCatalogResearchSectionResponse
import com.sigak.research.dto.GraphQueryEvidenceResponse
import com.sigak.research.dto.GraphResearchSectionResponse
import com.sigak.research.dto.ResearchOverviewResponse
import com.sigak.research.dto.RetrievalResearchSectionResponse
import com.sigak.research.dto.RetrievalSystemMetricResponse
import java.time.Instant
import org.springframework.stereotype.Service

private const val RETRIEVAL_COMPARISON_PATH = "results/retrieval/latest/metrics.comparison.json"
private const val GRAPH_SUMMARY_PATH = "results/graph/latest/graph-context.metrics.summary.json"
private const val GRAPH_BY_QUERY_PATH = "results/graph/latest/graph-context.metrics.by-query.json"
private const val EXPANDED_CATALOG_PATH = "datasets/raw/articles.catalog.api-ready-2026-06-05.json"

private data class GraphQueryEvidenceRead(
    val evidence: List<GraphQueryEvidenceResponse>,
    val warnings: List<String>
)

@Service
class ResearchOverviewService(
    private val artifactReader: ResearchArtifactReader
) {

    fun getOverview(): ResearchOverviewResponse =
        ResearchOverviewResponse(
            generatedAt = Instant.now(),
            retrieval = readRetrievalSection(),
            graph = readGraphSection(),
            expandedCatalog = readExpandedCatalogSection(),
            limitations = listOf(
                "Current retrieval and graph metrics are smoke evidence, not broad quality claims.",
                "The expanded catalog needs reviewed labels before it can support benchmark claims."
            )
        )

    private fun readRetrievalSection(): RetrievalResearchSectionResponse =
        when (val result = artifactReader.readJson(RETRIEVAL_COMPARISON_PATH)) {
            is ResearchArtifactReadResult.Available ->
                mapOrUnavailable(
                    fallback = { unavailableRetrieval("Artifact could not be mapped: $RETRIEVAL_COMPARISON_PATH") }
                ) {
                    val json = result.json
                    RetrievalResearchSectionResponse(
                        available = true,
                        unavailableReason = null,
                        catalogId = json.textOrNull("catalogId"),
                        catalogArticleCount = json.intOrNull("catalogArticleCount"),
                        evaluatedQueryCount = json.intOrNull("evaluatedQueryCount"),
                        k = json.intOrNull("k"),
                        generatedAt = json.instantOrNull("generatedAt"),
                        systems = json.arrayOrEmpty("systems").map { system ->
                            RetrievalSystemMetricResponse(
                                name = system.requiredText("system"),
                                attemptedQueryCount = system.requiredInt("attemptedQueryCount"),
                                completedQueryCount = system.requiredInt("completedQueryCount"),
                                failedQueryCount = system.requiredInt("failedQueryCount"),
                                degradedQueryCount = system.requiredInt("degradedQueryCount"),
                                macroTop1StrongHit = system.requiredDouble("macroTop1StrongHit"),
                                macroRecallAtK = system.requiredDouble("macroRecallAtK"),
                                macroMrrAtK = system.requiredDouble("macroMrrAtK"),
                                averageLatencyMs = system.requiredDouble("averageLatencyMs")
                            )
                        },
                        warnings = json.stringArray("warnings")
                    )
                }
            is ResearchArtifactReadResult.Unavailable -> unavailableRetrieval(result.reason)
        }

    private fun readGraphSection(): GraphResearchSectionResponse =
        when (val summaryResult = artifactReader.readJson(GRAPH_SUMMARY_PATH)) {
            is ResearchArtifactReadResult.Available ->
                mapOrUnavailable(
                    fallback = { unavailableGraph("Artifact could not be mapped: $GRAPH_SUMMARY_PATH") }
                ) {
                    val summary = summaryResult.json
                    val byQueryResult = artifactReader.readJson(GRAPH_BY_QUERY_PATH)
                    // Graph summary만 있어도 대시보드는 보여줘야 하므로 by-query artifact 실패는 section warning으로 낮춘다.
                    val queryEvidenceRead = when (byQueryResult) {
                        is ResearchArtifactReadResult.Available ->
                            readGraphQueryEvidence(byQueryResult.json)
                        is ResearchArtifactReadResult.Unavailable ->
                            GraphQueryEvidenceRead(
                                evidence = emptyList(),
                                warnings = listOf(byQueryResult.reason)
                            )
                    }

                    GraphResearchSectionResponse(
                        available = true,
                        unavailableReason = null,
                        catalogId = summary.textOrNull("catalogId"),
                        catalogArticleCount = summary.intOrNull("catalogArticleCount"),
                        evaluatedQueryCount = summary.intOrNull("evaluatedQueryCount"),
                        k = summary.intOrNull("k"),
                        generatedAt = summary.instantOrNull("generatedAt"),
                        macroSearchPositiveCoverageAtK = summary.doubleOrNull("macroSearchPositiveCoverageAtK"),
                        macroRelatedBaselineCoverageAtK = summary.doubleOrNull("macroRelatedBaselineCoverageAtK"),
                        macroGraphContextCoverageAtK = summary.doubleOrNull("macroGraphContextCoverageAtK"),
                        macroGraphReasonedCoverageAtK = summary.doubleOrNull("macroGraphReasonedCoverageAtK"),
                        graphContextFailureRate = summary.doubleOrNull("graphContextFailureRate"),
                        emptyContextRate = summary.doubleOrNull("emptyContextRate"),
                        averageGraphLatencyMs = summary.doubleOrNull("averageGraphLatencyMs"),
                        queryEvidence = queryEvidenceRead.evidence,
                        warnings = summary.stringArray("warnings") + queryEvidenceRead.warnings
                    )
                }
            is ResearchArtifactReadResult.Unavailable -> unavailableGraph(summaryResult.reason)
        }

    private fun readGraphQueryEvidence(json: JsonNode): GraphQueryEvidenceRead =
        try {
            GraphQueryEvidenceRead(
                evidence = json.map { query ->
                    GraphQueryEvidenceResponse(
                        query = query.requiredText("query"),
                        intent = query.textOrNull("intent"),
                        searchPositiveCoverageAtK = query.requiredDouble("searchPositiveCoverageAtK"),
                        relatedBaselineCoverageAtK = query.requiredDouble("relatedBaselineCoverageAtK"),
                        graphContextCoverageAtK = query.requiredDouble("graphContextCoverageAtK"),
                        graphReasonedCoverageAtK = query.requiredDouble("graphReasonedCoverageAtK"),
                        // null은 측정 실패가 아니라 적용 불가 신호이므로 UI에서 n/a로 보여준다.
                        reasonCoverage = query.doubleOrNull("reasonCoverage"),
                        topicContextCoverage = query.doubleOrNull("topicContextCoverage"),
                        averageGraphLatencyMs = query.requiredDouble("averageGraphLatencyMs"),
                        searchMissedPositiveIds = query.longArray("searchMissedPositiveIds")
                    )
                },
                warnings = emptyList()
            )
        } catch (exception: RuntimeException) {
            GraphQueryEvidenceRead(
                evidence = emptyList(),
                warnings = listOf("Artifact could not be mapped: $GRAPH_BY_QUERY_PATH")
            )
        }

    private fun readExpandedCatalogSection(): ExpandedCatalogResearchSectionResponse =
        when (val result = artifactReader.readJson(EXPANDED_CATALOG_PATH)) {
            is ResearchArtifactReadResult.Available ->
                mapOrUnavailable(
                    fallback = { unavailableExpandedCatalog("Artifact could not be mapped: $EXPANDED_CATALOG_PATH") }
                ) {
                    val json = result.json
                    ExpandedCatalogResearchSectionResponse(
                        available = true,
                        unavailableReason = null,
                        catalogId = json.textOrNull("catalogId"),
                        generatedAt = json.instantOrNull("generatedAt"),
                        articleCount = json.arrayOrEmpty("articles").size,
                        labelStatus = "draft_only",
                        benchmarkStatus = "blocked_until_reviewed_labels",
                        warnings = listOf("Reviewed labels are required before quality claims use the expanded catalog.")
                    )
                }
            is ResearchArtifactReadResult.Unavailable -> unavailableExpandedCatalog(result.reason)
        }

    private fun unavailableRetrieval(reason: String): RetrievalResearchSectionResponse =
        RetrievalResearchSectionResponse(
            available = false,
            unavailableReason = reason,
            catalogId = null,
            catalogArticleCount = null,
            evaluatedQueryCount = null,
            k = null,
            generatedAt = null,
            systems = emptyList(),
            warnings = emptyList()
        )

    private fun unavailableGraph(reason: String): GraphResearchSectionResponse =
        GraphResearchSectionResponse(
            available = false,
            unavailableReason = reason,
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
        )

    private fun unavailableExpandedCatalog(reason: String): ExpandedCatalogResearchSectionResponse =
        ExpandedCatalogResearchSectionResponse(
            available = false,
            unavailableReason = reason,
            catalogId = null,
            generatedAt = null,
            articleCount = null,
            labelStatus = "draft_only",
            benchmarkStatus = "blocked_until_reviewed_labels",
            warnings = emptyList()
        )

    private fun <T> mapOrUnavailable(fallback: () -> T, mapper: () -> T): T =
        try {
            mapper()
        } catch (exception: RuntimeException) {
            fallback()
        }

    private fun JsonNode.textOrNull(field: String): String? =
        get(field)?.takeUnless { it.isNull }?.asText()

    private fun JsonNode.intOrNull(field: String): Int? =
        get(field)?.takeUnless { it.isNull || !it.isNumber }?.asInt()

    private fun JsonNode.doubleOrNull(field: String): Double? =
        get(field)?.takeUnless { it.isNull || !it.isNumber }?.asDouble()

    private fun JsonNode.instantOrNull(field: String): Instant? =
        textOrNull(field)?.let { Instant.parse(it) }

    private fun JsonNode.requiredText(field: String): String =
        textOrNull(field) ?: throw IllegalArgumentException("Missing required text field: $field")

    private fun JsonNode.requiredInt(field: String): Int =
        intOrNull(field) ?: throw IllegalArgumentException("Missing required integer field: $field")

    private fun JsonNode.requiredDouble(field: String): Double =
        doubleOrNull(field) ?: throw IllegalArgumentException("Missing required numeric field: $field")

    private fun JsonNode.arrayOrEmpty(field: String): List<JsonNode> {
        val node = get(field)
        if (node == null || !node.isArray) {
            return emptyList()
        }
        return node.toList()
    }

    private fun JsonNode.stringArray(field: String): List<String> =
        arrayOrEmpty(field).map { it.asText() }

    private fun JsonNode.longArray(field: String): List<Long> =
        arrayOrEmpty(field).mapNotNull { value ->
            value.takeIf { it.isNumber }?.asLong()
        }
}
