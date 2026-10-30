package com.sigak.article.summary

import org.springframework.boot.context.properties.ConfigurationProperties

const val SUMMARY_MODEL = "gpt-5.6-luna"
const val SUMMARY_PROMPT = "article-summary-v2-fulltext"
const val ATTEMPT_MICROS = 20000L
const val MAX_BUDGET_MICROS = 4000000L

@ConfigurationProperties("sigak.summary")
data class SummaryProperties(
    val enabled: Boolean = false,
    val pilotAttemptLimit: Int = 10,
    val dailyAttemptLimit: Int = 20,
    val rollout: String = "pilot"
) {
    init {
        require(pilotAttemptLimit in 1..10)
        require(rollout in setOf("pilot", "active"))
        require(dailyAttemptLimit in 1..20)
    }
}

data class SummaryInput(
    val title: String, val source: String, val url: String, val publishedAt: String,
    val topics: List<String>, val rawContent: String, val inputScope: String
)

data class SummaryOutput(
    val summary: String, val whyItMatters: String, val suggestedTopics: List<String>,
    val suggestedPrimaryCategory: String, val suggestedImportanceScore: Int,
    val modelName: String, val promptVersion: String, val inputScope: String,
    val truncated: Boolean, val evidenceQuotes: List<String>, val inputTokens: Int,
    val outputTokens: Int, val providerResponseId: String,
    val cachedInputTokens: Int = 0, val cacheWriteTokens: Int = 0
)

data class ArticleSummaryResponse(
    val articleId: Long, val status: String, val summary: String? = null,
    val whyItMatters: String? = null, val evidenceQuotes: List<String> = emptyList(),
    val modelName: String? = null, val inputScope: String? = null,
    val truncated: Boolean = false, val errorCode: String? = null, val bodyStatus: String? = null
)

data class SummaryBudgetResponse(
    val chargedMicros: Long, val actualCostMicros: Long, val attemptCount: Long,
    val dailyCount: Long, val unknownCount: Long, val stoppedReason: String?,
    val maximumMicros: Long = MAX_BUDGET_MICROS
)

data class ClaimedSummaryJob(val id: Long, val articleId: Long, val inputHash: String, val input: SummaryInput)

fun interface SummaryProviderClient {
    fun enrich(input: SummaryInput): SummaryOutput
}
