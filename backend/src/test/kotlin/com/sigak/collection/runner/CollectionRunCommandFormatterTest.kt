package com.sigak.collection.runner

import com.sigak.collection.dto.CollectionFailureStage
import com.sigak.collection.dto.CollectionFailureSummary
import com.sigak.collection.dto.CollectionRunResponse
import com.sigak.collection.dto.CollectionRunStatus
import com.sigak.collection.dto.CollectionSourceRunResult
import kotlin.test.Test
import kotlin.test.assertContains

class CollectionRunCommandFormatterTest {

    private val formatter = CollectionRunCommandFormatter()

    @Test
    fun formatIncludesRunStatusCountsAndArticleIds() {
        val text = formatter.format(response())

        assertContains(text, "Collection run status: COMPLETED")
        assertContains(text, "sources selected=1 fetched=1 failed=0")
        assertContains(text, "articles discovered=3 published=2 skipped=1 failed=0")
        assertContains(text, "publishedArticleIds=101,102")
        assertContains(text, "skippedArticleIds=7")
    }

    @Test
    fun formatIncludesFailureSummariesWhenPresent() {
        val text = formatter.format(
            response(
                status = CollectionRunStatus.PARTIAL,
                sourceResults = listOf(
                    sourceResult(
                        status = CollectionRunStatus.PARTIAL,
                        failureSummaries = listOf(
                            CollectionFailureSummary(
                                stage = CollectionFailureStage.PUBLISH_ARTICLE,
                                message = "IllegalArgumentException: title must not be blank"
                            )
                        )
                    )
                )
            )
        )

        assertContains(text, "Failures:")
        assertContains(text, "github-blog PUBLISH_ARTICLE IllegalArgumentException: title must not be blank")
    }

    @Test
    fun formatErrorIncludesCollectionRunPrefix() {
        val text = formatter.formatError(IllegalArgumentException("missing source"))

        assertContains(text, "Collection run failed: missing source")
    }

    private fun response(
        status: CollectionRunStatus = CollectionRunStatus.COMPLETED,
        sourceResults: List<CollectionSourceRunResult> = listOf(sourceResult())
    ): CollectionRunResponse =
        CollectionRunResponse(
            status = status,
            requestedSourceIds = listOf("github-blog"),
            selectedSourceCount = 1,
            fetchedSourceCount = 1,
            failedSourceCount = 0,
            discoveredArticleCount = 3,
            publishedArticleCount = 2,
            skippedArticleCount = 1,
            failedArticleCount = 0,
            publishedArticleIds = listOf(101L, 102L),
            skippedArticleIds = listOf(7L),
            durationMs = 42,
            sourceResults = sourceResults
        )

    private fun sourceResult(
        status: CollectionRunStatus = CollectionRunStatus.COMPLETED,
        failureSummaries: List<CollectionFailureSummary> = emptyList()
    ): CollectionSourceRunResult =
        CollectionSourceRunResult(
            sourceId = "github-blog",
            status = status,
            fetched = true,
            discoveredArticleCount = 3,
            publishedArticleCount = 2,
            skippedArticleCount = 1,
            failedArticleCount = failureSummaries.size,
            publishedArticleIds = listOf(101L, 102L),
            skippedArticleIds = listOf(7L),
            failureSummaries = failureSummaries,
            durationMs = 40
        )
}
