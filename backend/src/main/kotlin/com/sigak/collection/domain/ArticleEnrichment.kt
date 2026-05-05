package com.sigak.collection.domain

data class ArticleEnrichment(
    val summary: String,
    val whyItMatters: String,
    val suggestedTopics: List<String>,
    val suggestedPrimaryCategory: String,
    val suggestedImportanceScore: Int,
    val modelName: String,
    val promptVersion: String
)
