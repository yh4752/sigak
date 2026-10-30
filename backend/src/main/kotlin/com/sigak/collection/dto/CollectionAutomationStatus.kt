package com.sigak.collection.dto

import java.time.Instant
import java.util.UUID

data class CollectionAutomationStatus(
    val enabled: Boolean,
    val state: String,
    val fixedDelaySeconds: Long,
    val attemptedRunCount: Int = 0,
    val lastStartedAt: Instant? = null,
    val lastFinishedAt: Instant? = null,
    val lastSuccessfulAt: Instant? = null,
    val lastPublishedAt: Instant? = null,
    val nextEligibleAt: Instant? = null,
    val lastRun: AutomationCollectionSummary? = null,
    val projectionRefreshPending: Boolean = true,
    val projectionResults: List<AutomationProjectionResult> = emptyList(),
    val failureType: String? = null
)

data class AutomationCollectionSummary(
    val runId: UUID,
    val status: CollectionRunStatus,
    val selectedSourceCount: Int,
    val fetchedSourceCount: Int,
    val failedSourceCount: Int,
    val discoveredArticleCount: Int,
    val publishedArticleCount: Int,
    val skippedArticleCount: Int,
    val failedArticleCount: Int
)

data class AutomationProjectionResult(
    val store: String,
    val status: String,
    val articleCount: Int,
    val refreshedAt: Instant,
    val failureType: String? = null
)
