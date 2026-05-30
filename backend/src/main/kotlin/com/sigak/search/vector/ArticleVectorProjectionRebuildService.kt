package com.sigak.search.vector

import com.sigak.ai.embedding.EmbeddingClient
import com.sigak.ai.embedding.EmbeddingResponse
import com.sigak.article.dto.ArticleResponse
import com.sigak.article.service.ArticleService
import kotlin.system.measureTimeMillis
import org.springframework.stereotype.Service

@Service
class ArticleVectorProjectionRebuildService(
    private val articleService: ArticleService,
    private val articleVectorTextBuilder: ArticleVectorTextBuilder,
    private val embeddingClient: EmbeddingClient,
    private val articleVectorProjectionIndexer: ArticleVectorProjectionIndexer
) {

    fun rebuild(): ArticleVectorProjectionRebuildResponse {
        val collectionName = articleVectorProjectionIndexer.collectionName()
        var indexedCount = 0
        var embeddingMetadata: EmbeddingMetadata? = null
        var failedReason: String? = null
        val durationMs = measureTimeMillis {
            try {
                val articles = articleService.getArticles(null)
                if (articles.isEmpty()) {
                    articleVectorProjectionIndexer.deleteCollectionIfExists()
                    return@measureTimeMillis
                }

                val documents = articles.map { article ->
                    val embedding = embeddingClient.embedText(articleVectorTextBuilder.build(article))
                    embedding.validateVectorSize()
                    val currentMetadata = embedding.toMetadata()
                    val previousMetadata = embeddingMetadata

                    if (previousMetadata == null) {
                        embeddingMetadata = currentMetadata
                    } else if (previousMetadata != currentMetadata) {
                        throw EmbeddingMetadataMismatchException(previousMetadata, currentMetadata)
                    }

                    article.toVectorDocument(embedding)
                }

                val dimension = requireNotNull(embeddingMetadata).dimension
                articleVectorProjectionIndexer.recreateCollection(dimension)
                articleVectorProjectionIndexer.upsertAll(documents)
                indexedCount = documents.size
            } catch (exception: Exception) {
                // Projection rebuild는 원본 PostgreSQL을 바꾸지 않으므로 실패를 응답으로 노출하고 재시도 가능하게 둔다.
                failedReason = exception.message ?: exception::class.simpleName
                indexedCount = 0
                embeddingMetadata = null
            }
        }

        return ArticleVectorProjectionRebuildResponse(
            status = if (failedReason == null) COMPLETED else FAILED,
            collectionName = collectionName,
            indexedCount = indexedCount,
            embeddingProvider = embeddingMetadata?.provider,
            embeddingModelName = embeddingMetadata?.modelName,
            embeddingDimension = embeddingMetadata?.dimension,
            durationMs = durationMs,
            failedReason = failedReason
        )
    }

    private fun ArticleResponse.toVectorDocument(embedding: EmbeddingResponse): ArticleVectorDocument =
        ArticleVectorDocument(
            id = id,
            vector = embedding.embedding,
            payload = ArticleVectorPayload(
                articleId = id,
                title = title,
                source = source,
                url = url,
                publishedAt = publishedAt,
                eventType = eventType,
                primaryCategory = primaryCategory,
                topics = topics,
                importanceScore = importanceScore,
                relatedArticleIds = relatedArticleIds,
                embeddingProvider = embedding.provider,
                embeddingModelName = embedding.modelName,
                embeddingDimension = embedding.dimension
            )
        )

    private fun EmbeddingResponse.toMetadata(): EmbeddingMetadata =
        EmbeddingMetadata(
            provider = provider,
            modelName = modelName,
            dimension = dimension
        )

    private fun EmbeddingResponse.validateVectorSize() {
        require(embedding.size == dimension) {
            "Embedding vector size mismatch: dimension=$dimension, vectorSize=${embedding.size}"
        }
    }

    private data class EmbeddingMetadata(
        val provider: String,
        val modelName: String,
        val dimension: Int
    )

    private class EmbeddingMetadataMismatchException(
        expected: EmbeddingMetadata,
        actual: EmbeddingMetadata
    ) : RuntimeException(
        "Embedding metadata mismatch: expected provider=${expected.provider}, " +
            "modelName=${expected.modelName}, dimension=${expected.dimension}; " +
            "actual provider=${actual.provider}, modelName=${actual.modelName}, dimension=${actual.dimension}"
    )

    private companion object {
        const val COMPLETED = "completed"
        const val FAILED = "failed"
    }
}
