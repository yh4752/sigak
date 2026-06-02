package com.sigak.collection.dto

data class EnrichmentResponse(
    val summary: String,
    val whyItMatters: String,
    val suggestedTopics: List<String>,
    val suggestedPrimaryCategory: String,
    val suggestedImportanceScore: Int,
    val modelName: String = "unknown-enrichment"
)
