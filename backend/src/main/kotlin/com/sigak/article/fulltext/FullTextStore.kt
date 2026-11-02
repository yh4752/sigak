package com.sigak.article.fulltext

import java.security.MessageDigest
import com.sigak.source.service.SourceStore
import com.sigak.common.http.PublicUrlPolicy
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@ConfigurationProperties("sigak.fulltext")
data class FullTextProperties(val enabled: Boolean = false)
data class ClaimedFullText(val articleId: Long, val url: String, val sourceKey: String, val sourceVersion: Long)

@Repository
class FullTextStore(private val jdbc: JdbcTemplate, transactionManager: PlatformTransactionManager, private val sources: SourceStore) {
    private val transactions = TransactionTemplate(transactionManager)
    fun find(id: Long): Map<String, Any>? = jdbc.queryForList("select * from article_full_texts where article_id=?", id).firstOrNull()

    fun enqueue(id: Long, url: String, requested: Boolean) {
        val source = sources.forArticle(id)
        val supported = source?.bodyApproved() == true && runCatching { PublicUrlPolicy.requireUrl(url,source.allowedHosts) }.isSuccess
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
        val row = jdbc.queryForList("""select f.article_id,a.url,s.source_key,s.config_version from article_full_texts f join articles a on a.id=f.article_id join news_sources s on s.id=a.source_id
            where f.status='PENDING' order by f.requested_summary desc,f.created_at limit 1 for update of f skip locked""").firstOrNull()
            ?: return@execute null
        val id = (row["article_id"] as Number).toLong()
        jdbc.update("update article_full_texts set status='FETCHING',started_at=now() where article_id=?", id)
        ClaimedFullText(id, row["url"] as String,row["source_key"] as String,(row["config_version"] as Number).toLong())
    }
    fun complete(job: ClaimedFullText, result: FullTextResult): Boolean = sources.withLockedSource(job.sourceKey,job.sourceVersion) { source ->
        if (!source.bodyApproved()) throw FullTextException("SOURCE_NOT_APPROVED")
        jdbc.update("""update article_full_texts set status='READY',full_text=?,final_url=?,content_hash=?,
            fetched_at=now(),fetch_duration_ms=?,error_code=null,body_origin='PAGE',extractor_version='registry-body-v2',source_config_version=?
            where article_id=? and status='FETCHING'""",
            result.text,result.finalUrl,hash(result.text),result.durationMs,source.version,job.articleId)
        find(job.articleId)?.get("requested_summary") == true
    }

    fun storeFeed(id: Long, text: String, feedUrl: String, version: Long) {
        jdbc.update("""insert into article_full_texts(article_id,status,full_text,final_url,content_hash,
            fetched_at,fetch_duration_ms,body_origin,extractor_version,source_config_version,requested_summary)
            values(?,'READY',?,?,?,now(),0,'FEED','registry-body-v2',?,false) on conflict(article_id) do nothing""",
            id,text,feedUrl,hash(text),version)
    }

    fun rejectFeed(id: Long, code: String, version: Long) {
        jdbc.update("""insert into article_full_texts(article_id,status,error_code,body_origin,source_config_version)
            values(?,'FAILED',?,'FEED',?) on conflict(article_id) do nothing""",id,code,version)
    }

    private fun hash(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    fun fail(id: Long, code: String) {
        jdbc.update("update article_full_texts set status='FAILED',error_code=? where article_id=? and status='FETCHING'", code, id)
    }
    fun tooLong(id: Long) {
        jdbc.update("update article_full_texts set status='TOO_LONG',error_code='BODY_TOO_LONG' where article_id=?", id)
    }
}
