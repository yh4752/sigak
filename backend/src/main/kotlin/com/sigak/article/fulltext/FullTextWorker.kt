package com.sigak.article.fulltext

import com.sigak.article.summary.ArticleSummaryService
import com.sigak.article.summary.CoalescingWorkLoop
import com.sigak.article.summary.SummaryWorkAvailable
import com.sigak.article.summary.SUMMARY_PROMPT
import jakarta.annotation.PreDestroy
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.event.EventListener
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Configuration
@EnableConfigurationProperties(FullTextProperties::class)
class FullTextConfig

@Component
@ConditionalOnProperty(prefix="sigak.fulltext", name=["worker-enabled"], havingValue="true")
class FullTextWorker(private val store: FullTextStore, private val fetcher: ArticleBodyFetcher,
    private val summaries: ArticleSummaryService) {
    private val loop = CoalescingWorkLoop("article-body") { runOne() }
    fun runOne(): Boolean {
        // 본문 저장 직후 프로세스가 종료돼도 유료 작업 예약이 사라지지 않게 복구한다.
        store.awaitingSummary(SUMMARY_PROMPT)?.let { if (summaries.request(it).status == "PENDING") return true }
        val job = store.claim() ?: return false
        try {
            val requested = store.complete(job, fetcher.fetch(job.url))
            if (requested) summaries.request(job.articleId)
        } catch (exception: Exception) {
            store.fail(job.articleId, (exception as? FullTextException)?.code?.let { if (it.startsWith("BODY_")) it else "BODY_$it" } ?: "BODY_FETCH_FAILED")
        }
        return true
    }
    @EventListener fun wake(event: SummaryWorkAvailable) { loop.wake() }
    @Scheduled(fixedDelayString="PT2S", initialDelayString="PT5S") fun tick() { loop.wake() }
    @PreDestroy fun close() { loop.close() }
}
