package com.sigak.collection.domain

data class CollectedArticle(
    val sourceName: String,
    val sourceType: SourceType,
    val externalId: String,
    val url: String,
    val canonicalUrl: String,
    val title: String,
    val publishedAt: String,
    val authorNames: List<String>,
    val rawContent: String,
    val extractedText: String,
    val categoryHint: String? = null,
    val status: CollectionStatus = CollectionStatus.EXTRACTED
)
