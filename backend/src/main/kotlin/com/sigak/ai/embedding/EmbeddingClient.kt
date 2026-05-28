package com.sigak.ai.embedding

fun interface EmbeddingClient {

    fun embedText(text: String): EmbeddingResponse
}
