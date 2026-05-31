package com.sigak.collection.dto

data class CollectionRunRequest(
    val sourceIds: List<String>? = null,
    val maxArticlesPerSource: Int? = null
)
