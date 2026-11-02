package com.sigak.source.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.sigak.collection.domain.SourceType
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Repository
class SourceStore(private val jdbc: JdbcTemplate, private val mapper: ObjectMapper, manager: PlatformTransactionManager) {
    private val transactions = TransactionTemplate(manager)

    fun all(): List<SourceDefinition> = jdbc.query("select * from news_sources order by id", { row, _ -> read(row) })
    fun find(key: String): SourceDefinition? = jdbc.query("select * from news_sources where source_key=?", { row, _ -> read(row) }, key).firstOrNull()
    fun forArticle(id: Long): SourceDefinition? = jdbc.query("select s.* from news_sources s join articles a on a.source_id=s.id where a.id=?", { row, _ -> read(row) }, id).firstOrNull()
    fun require(key: String): SourceDefinition = find(key) ?: throw SourceOperationException("SOURCE_NOT_FOUND")

    fun findByFeed(url: String): SourceDefinition? = jdbc.query("select * from news_sources where feed_url=?", { row, _ -> read(row) },url).firstOrNull()

    fun <T> withFeedLock(url: String, action: () -> T): T = requireNotNull(transactions.execute {
        jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))",Any::class.java,"source-feed:$url")
        action()
    })

    fun register(url: String, host: String): SourceDefinition = requireNotNull(transactions.execute {
        // 동일 입력의 동시 등록도 같은 출처로 모으되 서로 다른 feed는 독립적으로 등록할 수 있다.
        jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))", Any::class.java, "source-register:$url")
        val existing = jdbc.query("select * from news_sources where site_url=? or feed_url=? order by id limit 1", { row, _ -> read(row) }, url, url).firstOrNull()
        if (existing != null) return@execute existing
        val key = "source-${UUID.randomUUID()}"
        jdbc.update("""insert into news_sources(source_key,name,type,url,site_url,allowed_hosts)
            values(?,?,'RSS_ATOM',?,?,?::jsonb)""", key, host, url, url, mapper.writeValueAsString(listOf(host)))
        require(key)
    })

    fun save(source: SourceDefinition, expectedVersion: Long): SourceDefinition {
        val count = jdbc.update("""update news_sources set name=?,site_url=?,feed_url=?,category_hint=?,collection_enabled=?,
            validation_status=?,allowed_hosts=?::jsonb,body_mode=?,feed_content_verified=?,body_selector=?,exclude_selectors=?::jsonb,
            config_version=?,validated_config_version=?,validated_at=?,validation_report=?::jsonb
            where source_key=? and config_version=?""",
            source.name, source.siteUrl, source.feedUrl, source.categoryHint, source.enabled, source.validationStatus,
            mapper.writeValueAsString(source.allowedHosts.sorted()), source.bodyMode, source.feedContentVerified,
            source.bodySelector, mapper.writeValueAsString(source.excludeSelectors), source.version,
            source.validatedVersion, source.validatedAt?.let(java.sql.Timestamp::from), mapper.writeValueAsString(source.report), source.key, expectedVersion)
        if (count != 1) throw SourceOperationException("SOURCE_VERSION_CHANGED")
        return require(source.key)
    }

    fun <T> withLockedSource(key: String, expectedVersion: Long, action: (SourceDefinition) -> T): T = requireNotNull(transactions.execute {
        val source = jdbc.query("select * from news_sources where source_key=? for update", { row, _ -> read(row) }, key).firstOrNull()
            ?: throw SourceOperationException("SOURCE_NOT_FOUND")
        if (source.version != expectedVersion) throw SourceOperationException("SOURCE_VERSION_CHANGED")
        action(source)
    })

    fun recordCollection(key: String, error: String?) {
        jdbc.update("update news_sources set last_collection_at=now(),last_collection_error=? where source_key=?", error?.take(80), key)
    }

    fun block(key: String, version: Long, code: String) {
        jdbc.update("""update news_sources set collection_enabled=false,validation_status='BLOCKED',config_version=config_version+1,validated_config_version=null,
            last_collection_error=? where source_key=? and config_version=?""", code.take(80), key, version)
    }

    private fun read(row: ResultSet): SourceDefinition = SourceDefinition(
        row.getLong("id"), row.getString("source_key"), row.getString("name"), SourceType.valueOf(row.getString("type")),
        row.getString("site_url"), row.getString("feed_url"), row.getString("category_hint"), row.getBoolean("collection_enabled"),
        row.getString("validation_status"), mapper.readValue<List<String>>(row.getString("allowed_hosts")).toSet(),
        row.getString("body_mode"), row.getBoolean("feed_content_verified"), row.getString("body_selector"),
        mapper.readValue(row.getString("exclude_selectors")), row.getLong("config_version"),
        (row.getObject("validated_config_version") as? Number)?.toLong(), row.getTimestamp("validated_at")?.toInstant(),
        mapper.readValue(row.getString("validation_report")), row.getTimestamp("last_collection_at")?.toInstant(), row.getString("last_collection_error")
    )
}
