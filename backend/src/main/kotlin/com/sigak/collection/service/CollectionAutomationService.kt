package com.sigak.collection.service

import com.sigak.collection.config.CollectionAutomationProperties
import com.sigak.collection.dto.AutomationCollectionSummary
import com.sigak.collection.dto.AutomationProjectionResult
import com.sigak.collection.dto.CollectionAutomationStatus
import com.sigak.collection.dto.CollectionRunRequest
import com.sigak.collection.dto.CollectionRunResponse
import com.sigak.collection.dto.CollectionRunStatus
import com.sigak.search.graph.ArticleGraphProjectionRebuildService
import com.sigak.search.projection.ArticleSearchProjectionRebuildService
import com.sigak.search.vector.ArticleVectorProjectionRebuildService
import java.time.Clock
import java.util.concurrent.atomic.AtomicBoolean
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

@Service
class CollectionAutomationService(
    private val properties: CollectionAutomationProperties,
    private val collection: CollectionRunService,
    private val keyword: ArticleSearchProjectionRebuildService,
    private val vector: ArticleVectorProjectionRebuildService,
    private val graph: ArticleGraphProjectionRebuildService,
    @Qualifier("collectionAutomationClock") private val clock: Clock
) {
    private val running = AtomicBoolean(false)
    private val logger = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var snapshot = CollectionAutomationStatus(
        enabled = properties.enabled,
        state = if (properties.enabled) "IDLE" else "DISABLED",
        fixedDelaySeconds = properties.fixedDelay.seconds,
        nextEligibleAt = if (properties.enabled) clock.instant().plus(properties.initialDelay) else null
    )

    init {
        properties.validate()
    }

    fun status(): CollectionAutomationStatus = snapshot

    fun runCycle() {
        if (!properties.enabled || !running.compareAndSet(false, true)) return
        val previouslyPending = snapshot.projectionRefreshPending
        snapshot = snapshot.copy(
            state = "RUNNING", attemptedRunCount = snapshot.attemptedRunCount + 1,
            lastStartedAt = clock.instant(), nextEligibleAt = null,
            lastRun = null, projectionResults = emptyList(), failureType = null,
            // 수집 도중 예외가 나도 일부 기사는 저장됐을 수 있으므로 다음 주기에 검색을 복구한다.
            projectionRefreshPending = true
        )
        try {
            val response = collection.run(CollectionRunRequest(
                sourceIds = properties.sourceIds.takeIf { it.isNotEmpty() },
                maxArticlesPerSource = properties.maxArticlesPerSource
            ))
            snapshot = snapshot.copy(projectionRefreshPending = previouslyPending || response.publishedArticleCount > 0)
            if (response.publishedArticleCount > 0) {
                // 저장 뒤 indexing 도중 프로세스가 중단돼도 다음 실행에서 전체 projection을 복구한다.
                snapshot = snapshot.copy(lastPublishedAt = clock.instant(), projectionRefreshPending = true)
            }
            val results = if (snapshot.projectionRefreshPending) refreshProjections() else emptyList()
            val pending = results.any { it.status != "completed" }
            val state = when {
                response.status == CollectionRunStatus.FAILED -> "FAILED"
                response.status == CollectionRunStatus.PARTIAL || pending -> "PARTIAL"
                else -> "COMPLETED"
            }
            snapshot = snapshot.copy(
                state = state, lastRun = response.toSummary(), projectionResults = results,
                projectionRefreshPending = pending,
                lastSuccessfulAt = if (state == "COMPLETED") clock.instant() else snapshot.lastSuccessfulAt
            )
        } catch (exception: Exception) {
            // 예외의 원문에는 외부 주소나 자격 증명이 포함될 수 있으므로 타입만 진단에 남긴다.
            snapshot = snapshot.copy(state = "FAILED", failureType = exception.javaClass.simpleName)
        } finally {
            val finished = clock.instant()
            snapshot = snapshot.copy(lastFinishedAt = finished, nextEligibleAt = finished.plus(properties.fixedDelay))
            running.set(false)
        }
        val result = snapshot
        logger.info("collection_automation state={} runId={} published={} skipped={} failedSources={} pending={} failureType={}",
            result.state, result.lastRun?.runId, result.lastRun?.publishedArticleCount,
            result.lastRun?.skippedArticleCount, result.lastRun?.failedSourceCount,
            result.projectionRefreshPending, result.failureType)
    }

    private fun refreshProjections(): List<AutomationProjectionResult> = listOf(
        refresh("elasticsearch") { keyword.rebuild().let { it.status to it.indexedCount } },
        refresh("qdrant") { vector.rebuild().let { it.status to it.indexedCount } },
        refresh("neo4j") { graph.rebuild().let { it.status to it.articleNodeCount } }
    )

    private fun refresh(store: String, operation: () -> Pair<String, Int>): AutomationProjectionResult =
        try {
            val (status, count) = operation()
            AutomationProjectionResult(store, status, count, clock.instant(),
                failureType = if (status == "completed") null else "PROJECTION_REFRESH_FAILED")
        } catch (exception: Exception) {
            // 한 projection 장애가 나머지 저장소의 갱신을 막지 않도록 store별로 격리한다.
            AutomationProjectionResult(store, "failed", 0, clock.instant(), exception.javaClass.simpleName)
        }

    private fun CollectionRunResponse.toSummary() = AutomationCollectionSummary(
        runId, status, selectedSourceCount, fetchedSourceCount, failedSourceCount,
        discoveredArticleCount, publishedArticleCount, skippedArticleCount, failedArticleCount
    )
}
