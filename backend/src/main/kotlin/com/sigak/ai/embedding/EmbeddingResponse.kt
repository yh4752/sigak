package com.sigak.ai.embedding

data class EmbeddingResponse(
    val provider: String,
    val modelName: String,
    val dimension: Int,
    val embedding: List<Double>
)
