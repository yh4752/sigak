package com.sigak.research.dto

import java.time.Instant

data class ResearchOverviewResponse(
    val generatedAt: Instant,
    val retrieval: RetrievalResearchSectionResponse,
    val graph: GraphResearchSectionResponse,
    val expandedCatalog: ExpandedCatalogResearchSectionResponse,
    val limitations: List<String>
)

data class RetrievalResearchSectionResponse(
    val available: Boolean,
    val unavailableReason: String?,
    val catalogId: String?,
    val catalogArticleCount: Int?,
    val evaluatedQueryCount: Int?,
    val k: Int?,
    val generatedAt: Instant?,
    val systems: List<RetrievalSystemMetricResponse>,
    val warnings: List<String>
)

data class RetrievalSystemMetricResponse(
    val name: String,
    val attemptedQueryCount: Int,
    val completedQueryCount: Int,
    val failedQueryCount: Int,
    val degradedQueryCount: Int,
    val macroTop1StrongHit: Double,
    val macroRecallAtK: Double,
    val macroMrrAtK: Double,
    val averageLatencyMs: Double
)

data class GraphResearchSectionResponse(
    val available: Boolean,
    val unavailableReason: String?,
    val catalogId: String?,
    val catalogArticleCount: Int?,
    val evaluatedQueryCount: Int?,
    val k: Int?,
    val generatedAt: Instant?,
    val macroSearchPositiveCoverageAtK: Double?,
    val macroRelatedBaselineCoverageAtK: Double?,
    val macroGraphContextCoverageAtK: Double?,
    val macroGraphReasonedCoverageAtK: Double?,
    val graphContextFailureRate: Double?,
    val emptyContextRate: Double?,
    val averageGraphLatencyMs: Double?,
    val queryEvidence: List<GraphQueryEvidenceResponse>,
    val warnings: List<String>
)

data class GraphQueryEvidenceResponse(
    val query: String,
    val intent: String?,
    val searchPositiveCoverageAtK: Double,
    val relatedBaselineCoverageAtK: Double,
    val graphContextCoverageAtK: Double,
    val graphReasonedCoverageAtK: Double,
    val reasonCoverage: Double?,
    val topicContextCoverage: Double?,
    val averageGraphLatencyMs: Double,
    val searchMissedPositiveIds: List<Long>
)

data class ExpandedCatalogResearchSectionResponse(
    val available: Boolean,
    val unavailableReason: String?,
    val catalogId: String?,
    val generatedAt: Instant?,
    val articleCount: Int?,
    val labelStatus: String,
    val benchmarkStatus: String,
    val warnings: List<String>
)
