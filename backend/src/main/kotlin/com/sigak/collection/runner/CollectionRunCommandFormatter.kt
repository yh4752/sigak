package com.sigak.collection.runner

import com.sigak.collection.dto.CollectionFailureSummary
import com.sigak.collection.dto.CollectionRunResponse
import org.springframework.stereotype.Component

@Component
class CollectionRunCommandFormatter {

    fun format(response: CollectionRunResponse): String =
        buildString {
            appendLine("Collection run status: ${response.status}")
            appendLine("runId=${response.runId}")
            appendLine(
                "sources selected=${response.selectedSourceCount} " +
                    "fetched=${response.fetchedSourceCount} " +
                    "failed=${response.failedSourceCount}"
            )
            appendLine(
                "articles discovered=${response.discoveredArticleCount} " +
                    "published=${response.publishedArticleCount} " +
                    "skipped=${response.skippedArticleCount} " +
                    "failed=${response.failedArticleCount}"
            )
            appendLine("durationMs=${response.durationMs}")
            appendLine("publishedArticleIds=${response.publishedArticleIds.joinToString(",")}")
            appendLine("skippedArticleIds=${response.skippedArticleIds.joinToString(",")}")
            appendFailures(response)
        }.trimEnd()

    fun formatError(exception: Throwable): String =
        "Collection run failed: ${exception.message ?: exception::class.simpleName.orEmpty()}"

    private fun StringBuilder.appendFailures(response: CollectionRunResponse) {
        val failures = response.sourceResults.flatMap { sourceResult ->
            sourceResult.failureSummaries.map { failure ->
                SourceFailure(sourceId = sourceResult.sourceId, failure = failure)
            }
        }
        if (failures.isEmpty()) {
            return
        }

        appendLine("Failures:")
        failures.forEach { failure ->
            appendLine(
                "${failure.sourceId} ${failure.failure.stage} ${failure.failure.failureKind} " +
                    "retryable=${failure.failure.retryable} " +
                    "eventId=${failure.failure.failureEventId ?: "-"} ${failure.failure.message}"
            )
            failure.failure.articleTitle?.takeIf { title -> title.isNotBlank() }?.let { title ->
                appendLine("article=$title <${failure.failure.articleUrl.orEmpty()}>")
            }
        }
    }

    private data class SourceFailure(
        val sourceId: String,
        val failure: CollectionFailureSummary
    )
}
