package com.sigak.ai.embedding

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class FastApiEmbeddingClient(
    @Qualifier("aiServerRestClient")
    private val aiServerClient: RestClient
) : EmbeddingClient {

    override fun embedText(text: String): EmbeddingResponse {
        require(text.isNotBlank()) { "Embedding text must not be blank." }

        // FastAPI embedding 계약을 Spring 내부 타입으로 감싸 Qdrant projection 교체 비용을 낮춘다.
        return aiServerClient.post()
            .uri("/api/embeddings/text")
            .body(EmbeddingRequest(text = text))
            .retrieve()
            .body(EmbeddingResponse::class.java)
            ?: error("FastAPI embedding response body must not be empty.")
    }
}
