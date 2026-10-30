package com.sigak.article.summary

import com.sigak.SigakBackendApplication
import com.sigak.support.PostgresIntegrationTest
import java.util.concurrent.Executors
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.jdbc.core.JdbcTemplate
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito

@SpringBootTest(classes = [SigakBackendApplication::class], properties = ["sigak.summary.enabled=true"])
class ArticleSummaryServiceTest : PostgresIntegrationTest() {
    @Autowired lateinit var service: ArticleSummaryService
    @Autowired lateinit var store: SummaryJobStore
    @Autowired lateinit var jdbc: JdbcTemplate
    @MockBean lateinit var provider: SummaryProviderClient

    @BeforeEach
    fun prepare() {
        jdbc.update("delete from article_summary_jobs")
        jdbc.update("update summary_budget set charged_micros=0, attempt_count=0, daily_count=0, dispatch_day=current_date, stopped_reason=null")
        jdbc.update("delete from articles where id=9901")
        jdbc.update("""insert into articles(id,source_id,title,url,canonical_url,published_at,event_type,primary_category,importance_score,processing_status,created_at,updated_at)
            values(9901,1,'Real article','https://openai.com/news/summary-fixture','https://openai.com/news/summary-fixture',now(),'NEWS','AI',50,'PUBLISHED',now(),now())""")
        jdbc.update("insert into article_raw_contents(article_id,raw_content,extracted_text,collected_at) values(9901,'Evidence sentence.','Evidence sentence.',now())")
        Mockito.reset(provider)
    }

    @AfterEach fun cleanupFixture() {
        jdbc.update("delete from articles where id=9901")
        jdbc.update("update summary_budget set charged_micros=0, attempt_count=0, daily_count=0, stopped_reason=null")
    }

    @Test fun `pilot and daily limits stop before dispatch`() {
        jdbc.update("update summary_budget set attempt_count=10")
        service.request(9901)
        assertFalse(service.runOne())
        assertEquals("BUDGET_EXHAUSTED",service.get(9901).status)
        Mockito.verifyNoInteractions(provider)
        jdbc.update("delete from article_summary_jobs")
        jdbc.update("update summary_budget set attempt_count=0,daily_count=20,dispatch_day=current_date")
        service.request(9901)
        assertFalse(service.runOne())
        assertEquals(0L,store.budget().attemptCount)
    }

    @Test fun `read never creates a job or charges budget`() {
        assertEquals("NOT_REQUESTED", service.get(9901).status)
        assertEquals(0L, store.budget().attemptCount)
        Mockito.verifyNoInteractions(provider)
    }

    @Test fun `simultaneous requests share one durable job`() {
        val pool=Executors.newFixedThreadPool(6)
        try {
            val results=(1..12).map { pool.submit<String> { service.request(9901).status } }.map { it.get() }
            assertTrue(results.all { it=="PENDING" })
            assertEquals(1, jdbc.queryForObject("select count(*) from article_summary_jobs", Int::class.java))
            assertEquals(0L, store.budget().attemptCount)
        } finally { pool.shutdownNow() }
    }

    @Test fun `successful summary is reused across visits`() {
        Mockito.`when`(provider.enrich(anyInput())).thenReturn(output())
        assertEquals("PENDING",service.request(9901).status)
        assertTrue(service.runOne())
        assertEquals("READY",service.get(9901).status)
        assertEquals("실제 기사 요약입니다.",service.request(9901).summary)
        assertFalse(service.runOne())
        assertEquals(20000L,store.budget().chargedMicros)
        Mockito.verify(provider,Mockito.times(1)).enrich(anyInput())
    }

    @Test fun `budget boundary stops before dispatch and preserves queued article`() {
        jdbc.update("update summary_budget set charged_micros=3990000")
        service.request(9901)
        assertFalse(service.runOne())
        assertEquals("BUDGET_EXHAUSTED",service.get(9901).status)
        assertEquals(3990000L,store.budget().chargedMicros)
        Mockito.verifyNoInteractions(provider)
    }

    @Test fun `timeout keeps conservative charge and is never resubmitted`() {
        Mockito.`when`(provider.enrich(anyInput())).thenThrow(RuntimeException("secret provider failure"))
        service.request(9901)
        service.runOne()
        assertEquals("UNKNOWN",service.get(9901).status)
        service.request(9901)
        assertFalse(service.runOne())
        assertEquals(20000L,store.budget().chargedMicros)
        assertFalse(service.get(9901).toString().contains("secret"))
        Mockito.verify(provider,Mockito.times(1)).enrich(anyInput())
    }

    @Test fun `seed never causes a paid job`() {
        assertEquals("READY",service.request(1).status)
        assertEquals(0L,store.budget().attemptCount)
        Mockito.verifyNoInteractions(provider)
    }

    @Test fun `abandoned dispatched job becomes unknown without refund`() {
        service.request(9901)
        assertNotNull(store.claim())
        jdbc.update("update article_summary_jobs set started_at=now()-interval '20 minutes'")
        assertFalse(service.runOne())
        assertEquals("UNKNOWN",service.get(9901).status)
        assertEquals(20000L,store.budget().chargedMicros)
        Mockito.verifyNoInteractions(provider)
    }

    private fun anyInput(): SummaryInput = any(SummaryInput::class.java) ?: SummaryInput("", "", "", "", emptyList(), "", "")

    private fun output()=SummaryOutput("실제 기사 요약입니다.","기술 독자에게 중요한 내용입니다.",listOf("AI"),"AI",70,
        "gpt-5.6-luna","article-summary-v1","FEED_EXCERPT",false,listOf("Evidence sentence."),100,50,"resp_fixture")
}
