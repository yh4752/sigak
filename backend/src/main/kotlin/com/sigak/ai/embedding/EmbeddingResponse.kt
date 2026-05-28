package com.sigak.ai.embedding

data class EmbeddingResponse(
    val modelName: String,
    val dimension: Int,
    val embedding: List<Double>
)
