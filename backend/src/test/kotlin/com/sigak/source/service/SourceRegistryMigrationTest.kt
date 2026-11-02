package com.sigak.source.service

import java.util.UUID
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFails
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.containers.PostgreSQLContainer

class SourceRegistryMigrationTest {
    @Test
    fun `real V5 upgrade retains collected identities histories and charged budget`() {
        SourceContractDatabase.newSchema().use { fixture ->
            fixture.migrate("5")
            val jdbc = fixture.jdbc
            val sourceIds = legacySources.associate { source ->
                source.key to jdbc.queryForObject(
                    "insert into news_sources(source_key,name,type,url,category_hint) values(?,?,?,?,?) returning id",
                    Long::class.java, source.key, source.name, source.type, source.articleUrl, source.category
                )!!
            }
            val articleIds = sourceIds.entries.map { (key, sourceId) ->
                jdbc.queryForObject("""insert into articles(source_id,external_id,title,url,canonical_url,published_at,
                    event_type,primary_category,importance_score,processing_status,created_at,updated_at)
                    values(?,?,?, ?,?,'2026-10-30T00:00:00Z','NEWS','AI',50,'PUBLISHED',
                    '2026-10-30T00:00:00Z','2026-10-30T00:00:00Z') returning id""",
                    Long::class.java, sourceId, "external-$key", "Historical $key",
                    "https://archive.example.test/$key", "https://archive.example.test/$key")!!
            }
            for (id in articleIds) {
                jdbc.update("""insert into article_raw_contents(article_id,raw_content,extracted_text,collected_at)
                    values(?,? ,?,'2026-10-30T00:00:00Z')""", id, "<p>Original raw $id</p>", "Original extracted $id")
                jdbc.update("""insert into article_enrichments(article_id,summary,why_it_matters,model_name,prompt_version,
                    is_current,enriched_at) values(?,?,?,'mock-enrichment','collection-v1',false,'2026-10-30T00:00:00Z'),
                    (?,?,?,'gpt-5.6-luna','article-summary-v2-fulltext',true,'2026-10-30T00:01:00Z')""",
                    id, "Old summary $id", "Old insight $id", id, "Current summary $id", "Current insight $id")
            }
            jdbc.update("""insert into article_full_texts(article_id,status,full_text,final_url,content_hash,
                requested_summary,fetched_at,fetch_duration_ms) values(?,'READY',?,?,?,true,'2026-10-30T00:00:30Z',321)""",
                articleIds.first(), "Preserved full text including final evidence.", "https://archive.example.test/final", "f".repeat(64))
            for ((index, status) in listOf("READY", "UNKNOWN", "DISPATCHED").withIndex()) {
                jdbc.update("""insert into article_summary_jobs(article_id,input_hash,prompt_version,model_name,
                    request_json,status,result_json,reserved_micros,actual_cost_micros,started_at)
                    values(?,?,'article-summary-v2-fulltext','gpt-5.6-luna',?,?,?,20000,?,'2026-10-30T00:01:00Z')""",
                    articleIds[index], (index + 1).toString().repeat(64), "{\"rawContent\":\"saved input $index\"}", status,
                    if (status == "READY") "{\"summary\":\"saved result\"}" else null,
                    if (status == "READY") 321L else null)
            }
            jdbc.update("""update summary_budget set charged_micros=60000,attempt_count=3,daily_count=3,
                dispatch_day='2026-10-30',stopped_reason='PROVIDER_OR_USAGE_ERROR' where id=1""")
            val tables = listOf("news_sources", "articles", "article_raw_contents", "article_enrichments",
                "article_topics", "article_relations", "article_summary_jobs", "summary_budget", "article_full_texts")
            val before = tables.associateWith { snapshot(jdbc, it) }

            fixture.migrate("6")

            for ((table, rows) in before) {
                val columns = rows.firstOrNull()?.keys?.joinToString(",") ?: "*"
                val order = if (table == "article_full_texts") "article_id" else "id"
                assertEquals(rows, jdbc.queryForList("select $columns from $table order by $order"), table)
            }
            for (source in legacySources) {
                val migrated = jdbc.queryForMap("select * from news_sources where source_key=?", source.key)
                assertEquals(sourceIds[source.key], (migrated["id"] as Number).toLong(), source.key)
                assertEquals(source.feedUrl, migrated["feed_url"], source.key)
                assertEquals(source.articleUrl, migrated["url"], "Legacy URL must not be mistaken for the new feed URL")
                assertEquals(true, migrated["collection_enabled"])
                assertEquals("PASSED", migrated["validation_status"])
                assertEquals(1L, (migrated["validated_config_version"] as Number).toLong())
            }
            assertOperationalAndSeedState(jdbc)
            assertEquals(0, jdbc.queryForObject("""select count(*) from articles a left join news_sources s on s.id=a.source_id
                where s.id is null""", Int::class.java))
            assertEquals(0, jdbc.queryForObject("select count(*) from article_full_texts where body_origin<>'PAGE'", Int::class.java))
        }
    }

    @Test
    fun `V5 seed only upgrade creates exactly six sources without changing seed foreign keys`() {
        SourceContractDatabase.newSchema().use { fixture ->
            fixture.migrate("5")
            val seeds = fixture.jdbc.queryForList("select id,source_key,url from news_sources order by id")
            val foreignKeys = fixture.jdbc.queryForList("select id,source_id from articles order by id")
            fixture.migrate("6")
            assertEquals(seeds, fixture.jdbc.queryForList("select id,source_key,url from news_sources where id<=5 order by id"))
            assertEquals(foreignKeys, fixture.jdbc.queryForList("select id,source_id from articles order by id"))
            assertEquals(11, fixture.jdbc.queryForObject("select count(*) from news_sources", Int::class.java))
            assertOperationalAndSeedState(fixture.jdbc)
        }
    }

    @Test
    fun `known key with an unexpected host fails migration without partial data changes`() {
        SourceContractDatabase.newSchema().use { fixture ->
            fixture.migrate("5")
            fixture.jdbc.update("""insert into news_sources(source_key,name,type,url,category_hint)
                values('openai-blog','Conflicting source','RSS_ATOM','https://unexpected.example.test/first-post','AI')""")
            val before = snapshot(fixture.jdbc, "news_sources")
            val budget = snapshot(fixture.jdbc, "summary_budget")
            val failure = assertFails { fixture.migrate("6") }
            assertTrue(generateSequence<Throwable>(failure) { it.cause }.any {
                it.message.orEmpty().contains("SOURCE_REGISTRY_MIGRATION_CONFLICT")
            })
            assertEquals(before, snapshot(fixture.jdbc, "news_sources"))
            assertEquals(budget, snapshot(fixture.jdbc, "summary_budget"))
            assertEquals(0, fixture.jdbc.queryForObject("""select count(*) from information_schema.columns
                where table_schema=current_schema() and table_name='news_sources' and column_name='feed_url'""", Int::class.java))
        }
    }

    @Test
    fun `known arxiv source may retain an HTTPS article URL while migrating its feed`() {
        SourceContractDatabase.newSchema().use { fixture ->
            fixture.migrate("5")
            val id = fixture.jdbc.queryForObject("""insert into news_sources(source_key,name,type,url,category_hint)
                values('arxiv-cs-ai','arXiv cs.AI','ARXIV','https://arxiv.org/abs/2605.00001v1','CS_RESEARCH') returning id""", Long::class.java)
            fixture.migrate("6")
            val row = fixture.jdbc.queryForMap("select id,feed_url from news_sources where source_key='arxiv-cs-ai'")
            assertEquals(id, (row["id"] as Number).toLong())
            assertEquals(legacySources.single { it.key == "arxiv-cs-ai" }.feedUrl, row["feed_url"])
        }
    }

    private fun snapshot(jdbc: JdbcTemplate, table: String): List<Map<String, Any>> =
        jdbc.queryForList("select * from $table order by ${if (table == "article_full_texts") "article_id" else "id"}")

    private fun assertOperationalAndSeedState(jdbc: JdbcTemplate) {
        assertEquals(legacySources.map { it.key }.toSet(),
            jdbc.queryForList("select source_key from news_sources where collection_enabled=true", String::class.java).toSet())
        assertEquals(0, jdbc.queryForObject("select count(*) from news_sources where id<=5 and collection_enabled=true", Int::class.java))
        assertEquals(6, jdbc.queryForObject("""select count(*) from news_sources where collection_enabled=true
            and validation_report->>'reason'='LEGACY_MIGRATION' and validated_config_version=config_version""", Int::class.java))
    }

    private data class LegacySource(val key: String, val name: String, val type: String, val articleUrl: String,
        val feedUrl: String, val category: String)

    private val legacySources = listOf(
        LegacySource("openai-blog", "OpenAI Blog", "RSS_ATOM", "https://openai.com/index/first-article",
            "https://openai.com/news/rss.xml", "AI"),
        LegacySource("google-ai-blog", "Google AI Blog", "RSS_ATOM", "https://blog.google/technology/ai/first-article",
            "https://blog.google/technology/ai/rss/", "AI"),
        LegacySource("github-blog", "GitHub Blog", "RSS_ATOM", "https://github.blog/news/first-article",
            "https://github.blog/feed/", "DEVTOOLS"),
        LegacySource("arxiv-cs-ai", "arXiv cs.AI", "ARXIV", "http://arxiv.org/abs/2605.00001v1",
            "https://export.arxiv.org/api/query?search_query=cat:cs.AI&sortBy=submittedDate&sortOrder=descending&max_results=10", "CS_RESEARCH"),
        LegacySource("arxiv-cs-lg", "arXiv cs.LG", "ARXIV", "http://arxiv.org/abs/2605.00002v1",
            "https://export.arxiv.org/api/query?search_query=cat:cs.LG&sortBy=submittedDate&sortOrder=descending&max_results=10", "CS_RESEARCH"),
        LegacySource("arxiv-cs-cl", "arXiv cs.CL", "ARXIV", "http://arxiv.org/abs/2605.00003v1",
            "https://export.arxiv.org/api/query?search_query=cat:cs.CL&sortBy=submittedDate&sortOrder=descending&max_results=10", "CS_RESEARCH")
    )
}

// 두 검증 클래스가 작은 전용 DB 하나를 공유하되 migration마다 별도 schema로 격리한다.
internal object SourceContractDatabase {
    private val postgres: PostgreSQLContainer<*> by lazy {
        if (System.getProperty("api.version").isNullOrBlank()) System.setProperty("api.version", "1.44")
        PostgreSQLContainer("postgres:16-alpine").apply {
            withDatabaseName("source_contracts")
            withUsername("source_test")
            withPassword("source_test")
            withCommand("postgres", "-c", "shared_buffers=32MB", "-c", "max_connections=40")
            withCreateContainerCmdModifier { command -> command.hostConfig?.withMemory(256L * 1024 * 1024) }
            start()
        }
    }

    fun dataSource(schema: String? = null): DriverManagerDataSource = DriverManagerDataSource().apply {
        val base = postgres.jdbcUrl
        setUrl(base + (schema?.let { (if ('?' in base) "&" else "?") + "currentSchema=$it" } ?: ""))
        username = postgres.username
        password = postgres.password
    }

    fun newSchema(): SchemaFixture {
        val schema = "source_" + UUID.randomUUID().toString().replace("-", "")
        JdbcTemplate(dataSource()).execute("create schema $schema")
        return SchemaFixture(schema, dataSource(schema))
    }

    class SchemaFixture(private val schema: String, val dataSource: DataSource) : AutoCloseable {
        val jdbc = JdbcTemplate(dataSource)
        fun migrate(version: String) = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
            .schemas(schema).defaultSchema(schema).target(MigrationVersion.fromVersion(version)).load().migrate()
        override fun close() { JdbcTemplate(SourceContractDatabase.dataSource()).execute("drop schema $schema cascade") }
    }
}
