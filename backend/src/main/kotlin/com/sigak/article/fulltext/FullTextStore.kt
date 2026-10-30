package com.sigak.article.fulltext

import java.security.MessageDigest
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@ConfigurationProperties("sigak.fulltext")
data class FullTextProperties(val enabled: Boolean = false)
data class ClaimedFullText(val articleId: Long, val url: String)

@Repository
class FullTextStore(private val jdbc: JdbcTemplate, transactionManager: PlatformTransactionManager) {
    private val transactions = TransactionTemplate(transactionManager)
    fun find(id: Long): Map<String, Any>? = jdbc.queryForList("select * from article_full_texts where article_id=?", id).firstOrNull()

    fun enqueue(id: Long, url: String, requested: Boolean) {
        val supported = ArticleBodyPolicy.supported(url)
        if (!supported && !requested) return
        jdbc.update("""insert into article_full_texts(article_id,status,requested_summary,error_code) values(?,?,?,?)
            on conflict(article_id) do update set requested_summary=article_full_texts.requested_summary or excluded.requested_summary""",
            id, if (supported) "PENDING" else "UNSUPPORTED", requested, if (supported) null else "BODY_UNSUPPORTED")
    }
    fun awaitingSummary(prompt: String): Long? = jdbc.queryForList("""select f.article_id from article_full_texts f
        where f.status='READY' and f.requested_summary=true and not exists
        (select 1 from article_summary_jobs j where j.article_id=f.article_id and j.prompt_version=?)
        order by f.created_at limit 1""", prompt).firstOrNull()?.let { (it["article_id"] as Number).toLong() }

    fun claim(): ClaimedFullText? = transactions.execute {
        jdbc.update("""update article_full_texts set status='FAILED',error_code='BODY_INTERRUPTED'
            where status='FETCHING' and started_at < now()-interval '2 minutes'""")
        val row = jdbc.queryForList("""select f.article_id,a.url from article_full_texts f join articles a on a.id=f.article_id
            where f.status='PENDING' order by f.requested_summary desc,f.created_at limit 1 for update of f skip locked""").firstOrNull()
            ?: return@execute null
        val id = (row["article_id"] as Number).toLong()
        jdbc.update("update article_full_texts set status='FETCHING',started_at=now() where article_id=?", id)
        ClaimedFullText(id, row["url"] as String)
    }
    fun complete(job: ClaimedFullText, result: FullTextResult): Boolean {
        val hash = MessageDigest.getInstance("SHA-256").digest(result.text.toByteArray()).joinToString("") { "%02x".format(it) }
        jdbc.update("""update article_full_texts set status='READY',full_text=?,final_url=?,content_hash=?,
            fetched_at=now(),fetch_duration_ms=?,error_code=null where article_id=? and status='FETCHING'""",
            result.text, result.finalUrl, hash, result.durationMs, job.articleId)
        return find(job.articleId)?.get("requested_summary") == true
    }
    fun fail(id: Long, code: String) {
        jdbc.update("update article_full_texts set status='FAILED',error_code=? where article_id=? and status='FETCHING'", code, id)
    }
    fun tooLong(id: Long) {
        jdbc.update("update article_full_texts set status='TOO_LONG',error_code='BODY_TOO_LONG' where article_id=?", id)
    }
}
