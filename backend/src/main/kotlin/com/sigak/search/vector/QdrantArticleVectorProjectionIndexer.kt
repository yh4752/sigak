package com.sigak.search.vector

import com.fasterxml.jackson.annotation.JsonProperty
import com.sigak.search.config.SearchInfrastructureProperties
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

@Component
class QdrantArticleVectorProjectionIndexer(
    @Qualifier("qdrantRestClient")
    private val qdrantClient: RestClient,
    private val properties: SearchInfrastructureProperties
) : ArticleVectorProjectionIndexer {

    override fun collectionName(): String =
        properties.qdrant.articleCollectionName

    override fun deleteCollectionIfExists() {
        try {
            qdrantClient.delete()
                .uri("/collections/{collectionName}", collectionName())
                .retrieve()
                .toBodilessEntity()
        } catch (exception: RestClientResponseException) {
            if (exception.statusCode != HttpStatus.NOT_FOUND) {
                throw exception
            }
        }
    }

    override fun recreateCollection(dimension: Int) {
        deleteCollectionIfExists()

        // Qdrant collection은 하나의 unnamed vector에 대해 dimension과 metric이 고정된다.
        qdrantClient.put()
            .uri("/collections/{collectionName}", collectionName())
            .body(
                QdrantCollectionRequest(
                    vectors = QdrantVectorConfig(
                        size = dimension,
                        distance = properties.qdrant.distance
                    )
                )
            )
            .retrieve()
            .toBodilessEntity()
    }

    override fun upsertAll(documents: List<ArticleVectorDocument>) {
        if (documents.isEmpty()) {
            return
        }

        qdrantClient.put()
            .uri("/collections/{collectionName}/points?wait=true", collectionName())
            .body(QdrantUpsertRequest(points = documents.map { document -> document.toPoint() }))
            .retrieve()
            .toBodilessEntity()
    }

    override fun search(vector: List<Double>, limit: Int): List<ArticleVectorSearchHit> {
        val response = qdrantClient.post()
            .uri("/collections/{collectionName}/points/search", collectionName())
            .body(
                QdrantSearchRequest(
                    vector = vector,
                    limit = limit,
                    withPayload = true,
                    withVector = false
                )
            )
            .retrieve()
            .body(QdrantSearchResponse::class.java)

        return response?.result.orEmpty()
            .map { point ->
                ArticleVectorSearchHit(
                    articleId = point.payload?.articleId ?: point.id.toLong(),
                    score = point.score
                )
            }
    }

    private fun ArticleVectorDocument.toPoint(): QdrantPoint =
        QdrantPoint(
            id = id,
            vector = vector,
            payload = payload
        )

    private data class QdrantCollectionRequest(
        val vectors: QdrantVectorConfig
    )

    private data class QdrantVectorConfig(
        val size: Int,
        val distance: String
    )

    private data class QdrantUpsertRequest(
        val points: List<QdrantPoint>
    )

    private data class QdrantPoint(
        val id: Long,
        val vector: List<Double>,
        val payload: ArticleVectorPayload
    )

    private data class QdrantSearchRequest(
        val vector: List<Double>,
        val limit: Int,
        @JsonProperty("with_payload")
        val withPayload: Boolean,
        @JsonProperty("with_vector")
        val withVector: Boolean
    )

    private data class QdrantSearchResponse(
        val result: List<QdrantSearchResultPoint> = emptyList()
    )

    private data class QdrantSearchResultPoint(
        val id: Long,
        val score: Double,
        val payload: QdrantSearchPayload? = null
    )

    private data class QdrantSearchPayload(
        val articleId: Long? = null
    )
}
