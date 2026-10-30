package com.sigak.article.summary

import com.fasterxml.jackson.databind.ObjectMapper
import com.sigak.article.service.ArticleContentText
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException

@Repository
class SummaryJobStore(
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper,
    transactionManager: PlatformTransactionManager,
    private val properties: SummaryProperties
) {
    private val transactions = TransactionTemplate(transactionManager)

    fun status(articleId: Long): ArticleSummaryResponse {
        article(articleId)
        ready(articleId)?.let { return it }
        val job = latest(articleId) ?: return ArticleSummaryResponse(articleId, if (properties.enabled) "NOT_REQUESTED" else "UNAVAILABLE")
        return response(articleId, job)
    }

    fun request(articleId: Long): ArticleSummaryResponse = requireNotNull(transactions.execute {
        // 기사 행 잠금으로 여러 탭과 방문자의 동일 요청을 하나의 영속 작업으로 합친다.
        article(articleId, lock = true)
        ready(articleId)?.let { return@execute it }
        latest(articleId)?.let { return@execute response(articleId, it) }
        if (!properties.enabled) return@execute ArticleSummaryResponse(articleId, "UNAVAILABLE", errorCode = "AI_DISABLED")
        val input = input(articleId) ?: return@execute ArticleSummaryResponse(articleId, "UNAVAILABLE", errorCode = "NO_SOURCE_TEXT")
        if (input.url.contains("example.com")) return@execute ArticleSummaryResponse(articleId, "UNAVAILABLE", errorCode = "SEED_ARTICLE")
        val json = mapper.writeValueAsString(input)
        jdbc.update("""insert into article_summary_jobs(article_id,input_hash,prompt_version,model_name,request_json,status)
            values(?,?,?,?,?,'PENDING') on conflict do nothing""", articleId, hash(json), SUMMARY_PROMPT, SUMMARY_MODEL, json)
        ArticleSummaryResponse(articleId, "PENDING")
    })

    fun claim(): ClaimedSummaryJob? = transactions.execute {
        // 전송 여부가 불확실한 이전 작업은 재실행하면 과금이 중복될 수 있어 격리한다.
        jdbc.update("""update article_summary_jobs set status='UNKNOWN',error_code='INTERRUPTED',finished_at=now()
            where status='DISPATCHED' and started_at < now()-interval '5 minutes'""")
        val budget = jdbc.queryForMap("select * from summary_budget where id=1 for update")
        val job = jdbc.queryForList("select * from article_summary_jobs where status='PENDING' order by id limit 1 for update skip locked").firstOrNull()
            ?: return@execute null
        if (budget["stopped_reason"] != null) {
            failPending(job, "FAILED", "PROVIDER_STOPPED")
            return@execute null
        }
        val count = (budget["attempt_count"] as Number).toInt()
        val charged = (budget["charged_micros"] as Number).toLong()
        if (count >= (if (properties.rollout == "pilot") properties.pilotAttemptLimit else 200) || count >= 200 || charged + ATTEMPT_MICROS > MAX_BUDGET_MICROS) {
            failPending(job, "BUDGET_EXHAUSTED", "ATTEMPT_OR_BUDGET_LIMIT")
            return@execute null
        }
        val today = java.time.LocalDate.now(java.time.ZoneOffset.UTC)
        val day = (budget["dispatch_day"] as java.sql.Date).toLocalDate()
        val dailyCount = if (day == today) (budget["daily_count"] as Number).toInt() else 0
        if (dailyCount >= properties.dailyAttemptLimit) return@execute null
        jdbc.update("""update summary_budget set charged_micros=charged_micros+?,attempt_count=attempt_count+1,
            dispatch_day=?,daily_count=? where id=1""", ATTEMPT_MICROS, today, dailyCount + 1)
        jdbc.update("update article_summary_jobs set status='DISPATCHED',reserved_micros=?,started_at=now() where id=?", ATTEMPT_MICROS, job["id"])
        ClaimedSummaryJob((job["id"] as Number).toLong(), (job["article_id"] as Number).toLong(),
            job["input_hash"] as String, mapper.readValue(job["request_json"] as String, SummaryInput::class.java))
    }

    fun complete(job: ClaimedSummaryJob, output: SummaryOutput) {
        transactions.executeWithoutResult {
            article(job.articleId, lock = true)
            val row = jdbc.queryForMap("select status from article_summary_jobs where id=? for update", job.id)
            if (row["status"] != "DISPATCHED") return@executeWithoutResult
            if (input(job.articleId)?.let { hash(mapper.writeValueAsString(it)) } != job.inputHash) {
                jdbc.update("update article_summary_jobs set status='FAILED',error_code='INPUT_CHANGED',finished_at=now() where id=?",job.id)
                return@executeWithoutResult
            }
            jdbc.update("update article_enrichments set is_current=false where article_id=? and is_current=true",job.articleId)
            jdbc.update("""insert into article_enrichments(article_id,summary,why_it_matters,suggested_primary_category,
                suggested_importance_score,model_name,prompt_version,is_current,enriched_at) values(?,?,?,?,?,?,?,true,now())""",
                job.articleId, output.summary, output.whyItMatters, output.suggestedPrimaryCategory,
                output.suggestedImportanceScore,output.modelName,output.promptVersion)
            val actual = kotlin.math.ceil(output.inputTokens * 0.20 + output.outputTokens * 1.20).toLong()
            jdbc.update("update article_summary_jobs set status='READY',result_json=?,actual_cost_micros=?,finished_at=now() where id=?",
                mapper.writeValueAsString(output),actual,job.id)
        }
    }

    fun fail(job: ClaimedSummaryJob, stop: Boolean = false) {
        transactions.executeWithoutResult {
            jdbc.update("""update article_summary_jobs set status='UNKNOWN',error_code='PROVIDER_UNCONFIRMED',finished_at=now()
                where id=? and status='DISPATCHED'""",job.id)
            if (stop) jdbc.update("update summary_budget set stopped_reason='PROVIDER_OR_USAGE_ERROR' where id=1")
        }
    }

    fun budget(): SummaryBudgetResponse {
        val row=jdbc.queryForMap("select * from summary_budget where id=1")
        return SummaryBudgetResponse((row["charged_micros"] as Number).toLong(),
            jdbc.queryForObject("select coalesce(sum(actual_cost_micros),0) from article_summary_jobs",Long::class.java) ?: 0,
            (row["attempt_count"] as Number).toLong(),(row["daily_count"] as Number).toLong(),
            jdbc.queryForObject("select count(*) from article_summary_jobs where status='UNKNOWN'",Long::class.java) ?: 0,
            row["stopped_reason"] as String?)
    }

    private fun article(id: Long, lock: Boolean = false): Map<String, Any> =
        jdbc.queryForList("select id from articles where id=? and processing_status='PUBLISHED'" + if(lock) " for update" else "",id)
            .firstOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND,"Article not found")

    private fun latest(id: Long): Map<String, Any>? =
        jdbc.queryForList("select * from article_summary_jobs where article_id=? order by id desc limit 1",id).firstOrNull()

    private fun ready(id: Long): ArticleSummaryResponse? {
        val row=jdbc.queryForList("""select summary,why_it_matters,model_name,prompt_version from article_enrichments
            where article_id=? and is_current=true and (model_name='curated-seed' or model_name like 'gpt-5.6-luna%')""",id)
            .firstOrNull() ?: return null
        val job=jdbc.queryForList("""select * from article_summary_jobs where article_id=? and status='READY'
            and prompt_version=? and (result_json::jsonb ->> 'modelName')=? order by id desc limit 1""",
            id,row["prompt_version"],row["model_name"]).firstOrNull()
        if(job != null) return response(id, job)
        return ArticleSummaryResponse(id,"READY",row["summary"] as String,row["why_it_matters"] as String,modelName=row["model_name"] as String)
    }

    private fun input(id: Long): SummaryInput? {
        val row=jdbc.queryForList("""select a.title,a.url,a.published_at,s.name,s.type,r.extracted_text,r.raw_content
            from articles a join news_sources s on s.id=a.source_id join article_raw_contents r on r.article_id=a.id where a.id=?""",id).firstOrNull() ?: return null
        val text=ArticleContentText.plain((row["extracted_text"] as String).ifBlank { row["raw_content"] as String })
        if(text.isBlank()) return null
        val topics=jdbc.queryForList("select topic from article_topics where article_id=? order by position",String::class.java,id)
        return SummaryInput(row["title"] as String,row["name"] as String,row["url"] as String,
            (row["published_at"] as java.sql.Timestamp).toInstant().toString(),topics,text,
            if(row["type"]=="ARXIV") "ABSTRACT" else "FEED_EXCERPT")
    }

    private fun response(id: Long, row: Map<String, Any>): ArticleSummaryResponse {
        val status=row["status"] as String
        val output=(row["result_json"] as String?)?.let { mapper.readValue(it,SummaryOutput::class.java) }
        return ArticleSummaryResponse(id,if(status=="DISPATCHED") "PENDING" else status,
            output?.summary,output?.whyItMatters,output?.evidenceQuotes.orEmpty(),output?.modelName,
            output?.inputScope,output?.truncated ?: false,row["error_code"] as String?)
    }

    private fun failPending(job: Map<String, Any>, status: String, code: String) {
        jdbc.update("update article_summary_jobs set status=?,error_code=?,finished_at=now() where id=?",status,code,job["id"])
    }

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
