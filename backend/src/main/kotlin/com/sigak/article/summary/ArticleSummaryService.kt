package com.sigak.article.summary

import com.sigak.article.domain.PrimaryCategory
import org.springframework.stereotype.Service
import org.springframework.context.ApplicationEventPublisher
import org.springframework.web.client.HttpStatusCodeException

@Service
class ArticleSummaryService(
    private val store: SummaryJobStore,
    private val provider: SummaryProviderClient,
    private val properties: SummaryProperties,
    private val events: ApplicationEventPublisher
) {
    fun get(articleId: Long): ArticleSummaryResponse = store.status(articleId)
    fun request(articleId: Long): ArticleSummaryResponse {
        val result = store.request(articleId)
        if (result.status == "PENDING") events.publishEvent(SummaryWorkAvailable())
        return result
    }

    fun runOne(): Boolean {
        if (!properties.enabled) return false
        val job = store.claim() ?: return false
        try {
            val output = provider.enrich(job.input)
            validate(output, job.input)
            store.complete(job, output)
        } catch (exception: Exception) {
            // 응답 유실은 미과금의 증거가 아니므로 비용을 유지하고 자동 재호출하지 않는다.
            val stop = exception is IllegalArgumentException ||
                (exception is HttpStatusCodeException && exception.statusCode.value() == 503)
            store.fail(job, stop)
        }
        return true
    }

    private fun validate(output: SummaryOutput, input: SummaryInput) {
        require(output.modelName == SUMMARY_MODEL || output.modelName.startsWith("$SUMMARY_MODEL-"))
        require(output.promptVersion == SUMMARY_PROMPT && output.inputScope == input.inputScope)
        require(output.summary.isNotBlank() && output.whyItMatters.isNotBlank())
        require(output.summary.length <= 4000 && output.whyItMatters.length <= 3000)
        require(output.suggestedImportanceScore in 0..100 && output.suggestedTopics.size <= 8)
        require(PrimaryCategory.entries.any { it.name == output.suggestedPrimaryCategory })
        require(output.inputTokens in 1..70000 && output.outputTokens in 1..2048)
        require(output.providerResponseId.isNotBlank())
        require(output.cacheWriteTokens == 0 && output.cachedInputTokens in 0..output.inputTokens)
        require(input.inputScope == "FULL_TEXT" && !output.truncated)
        require(output.evidenceQuotes.size in 1..3 && output.evidenceQuotes.all { it.isNotBlank() && input.rawContent.contains(it) })
    }
}
