package com.sigak.collection.dto

data class EnrichmentRequest(
    val title: String,
    val source: String,
    val url: String,
    val publishedAt: String,
    val topics: List<String>,
    val rawContent: String
)
