package com.sigak.search.graph

import com.sigak.common.time.measureElapsed
import java.time.Instant
import org.springframework.stereotype.Service

@Service
class ArticleGraphProjectionRebuildService(
    private val reader: ArticleGraphProjectionReaderPort,
    private val indexer: ArticleGraphProjectionIndexer
) {

    fun rebuild(): ArticleGraphProjectionRebuildResponse {
        var indexResult = emptyResult()
        var rebuiltAt: Instant? = null
        var failedReason: String? = null

        val rebuildMeasurement = measureElapsed {
            try {
                val documents = reader.readApiReadyGraphDocuments()
                indexResult = indexer.rebuild(documents)
                rebuiltAt = Instant.now()
            } catch (exception: Exception) {
                // Neo4j는 재생성 가능한 projection store이므로 실패 내용을 짧게 노출하고 원본 PostgreSQL 상태는 유지한다.
                indexResult = emptyResult()
                rebuiltAt = null
                failedReason = exception.toFailedReason()
            }
        }

        return ArticleGraphProjectionRebuildResponse(
            status = if (failedReason == null) COMPLETED else FAILED,
            rebuiltAt = rebuiltAt?.toString(),
            articleNodeCount = indexResult.articleNodeCount,
            topicNodeCount = indexResult.topicNodeCount,
            hasTopicRelationshipCount = indexResult.hasTopicRelationshipCount,
            relatedToRelationshipCount = indexResult.relatedToRelationshipCount,
            durationMs = rebuildMeasurement.elapsedMs,
            failedReason = failedReason
        )
    }

    private fun Exception.toFailedReason(): String {
        val message = message
            ?.lineSequence()
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { failedReason -> failedReason.isNotBlank() }

        return (message ?: this::class.simpleName ?: "Unknown failure")
            .take(MAX_FAILED_REASON_LENGTH)
    }

    private fun emptyResult(): ArticleGraphProjectionIndexResult =
        ArticleGraphProjectionIndexResult(
            articleNodeCount = 0,
            topicNodeCount = 0,
            hasTopicRelationshipCount = 0,
            relatedToRelationshipCount = 0
        )

    private companion object {
        const val COMPLETED = "completed"
        const val FAILED = "failed"
        const val MAX_FAILED_REASON_LENGTH = 160
    }
}
