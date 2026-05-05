package com.sigak.collection.domain

data class NewsSource(
    val id: String,
    val name: String,
    val type: SourceType,
    val url: String,
    val categoryHint: String? = null
)
