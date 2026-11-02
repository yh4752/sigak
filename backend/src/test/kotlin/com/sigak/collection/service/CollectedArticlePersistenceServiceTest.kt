package com.sigak.collection.service

import com.sigak.SigakBackendApplication
import com.sigak.article.service.ArticleService
import com.sigak.collection.domain.CollectedArticle
import com.sigak.collection.domain.SourceType
import com.sigak.collection.dto.EnrichmentResponse
import com.sigak.support.PostgresIntegrationTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

@SpringBootTest(classes = [SigakBackendApplication::class], properties = ["sigak.fulltext.enabled=true"])
class CollectedArticlePersistenceServiceTest : PostgresIntegrationTest() {

    @Autowired
    private lateinit var persistenceService: CollectedArticlePersistenceService

    @Autowired
    private lateinit var articleService: ArticleService

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @org.junit.jupiter.api.BeforeEach
    fun registerFixtureSource() {
        jdbcTemplate.update("""insert into news_sources(source_key,name,type,url,feed_url,collection_enabled,
            validation_status,config_version,validated_config_version,allowed_hosts)
            values('arxiv-cs-ai-test','arXiv cs.AI Test','ARXIV','https://arxiv.org/feed','https://arxiv.org/feed',true,'PASSED',1,1,'["arxiv.org"]')
            on conflict(source_key) do nothing""")
    }

    @org.junit.jupiter.api.AfterEach
    fun removeFixtureSource() { deleteTestSource() }

    @Test
    fun publishStoresCollectedArticleEnrichmentAndRawContentForPublicApi() {
        val collectedArticle = collectedArticle(
            externalId = "http://arxiv.org/abs/2605.99991v1",
            title = "Evaluating Collection Pipelines"
        )
        val enrichment = enrichmentResponse()

        try {
            val result = persistenceService.publish(collectedArticle, enrichment)
            val publishedArticleId = result.articleId

            assertEquals(CollectedArticlePublishOutcome.PUBLISHED, result.outcome)
            val article = articleService.getArticle(publishedArticleId)
            assertNotNull(article)
            assertEquals("Evaluating Collection Pipelines", article.title)
            assertEquals("RESEARCH", article.eventType)
            assertEquals("CS_RESEARCH", article.primaryCategory)
            assertEquals(listOf("CS_RESEARCH", "collection pipeline"), article.topics)
            assertEquals("A collected article was enriched and persisted.", article.summary)
            assertEquals("It proves the collection pipeline can feed the public API.", article.whyItMatters)

            assertEquals(
                1,
                jdbcTemplate.queryForObject(
                    "select count(*) from article_raw_contents where article_id = ?",
                    Int::class.java,
                    publishedArticleId
                )
            )
            assertEquals(
                1,
                jdbcTemplate.queryForObject(
                    "select count(*) from article_enrichments where article_id = ? and is_current = true",
                    Int::class.java,
                    publishedArticleId
                )
            )
            assertEquals(
                "test-enrichment-model",
                jdbcTemplate.queryForObject(
                    "select model_name from article_enrichments where article_id = ? and is_current = true",
                    String::class.java,
                    publishedArticleId
                )
            )
        } finally {
            deleteArticleByCanonicalUrl(collectedArticle.canonicalUrl)
            deleteTestSource()
        }
    }

    @Test
    fun publishReturnsExistingArticleWhenCanonicalUrlAlreadyExists() {
        val collectedArticle = collectedArticle(
            externalId = "http://arxiv.org/abs/2605.99992v1",
            title = "Duplicate Collection Pipelines"
        )
        val enrichment = enrichmentResponse()

        try {
            val firstResult = persistenceService.publish(collectedArticle, enrichment)
            val secondResult = persistenceService.publish(
                collectedArticle.copy(externalId = "http://arxiv.org/abs/2605.99992v2"),
                enrichment
            )

            assertEquals(CollectedArticlePublishOutcome.PUBLISHED, firstResult.outcome)
            assertEquals(CollectedArticlePublishOutcome.SKIPPED_DUPLICATE, secondResult.outcome)
            assertEquals(firstResult.articleId, secondResult.articleId)
            assertEquals(
                1,
                jdbcTemplate.queryForObject(
                    "select count(*) from articles where canonical_url = ?",
                    Int::class.java,
                    collectedArticle.canonicalUrl
                )
            )
        } finally {
            deleteArticleByCanonicalUrl(collectedArticle.canonicalUrl)
            deleteTestSource()
        }
    }

    @Test
    fun `new supported publication queues only free full text and duplicate stays shared`() {
        val source = collectedArticle("https://openai.com/index/free-prefetch-fixture", "Free prefetch fixture")
            .copy(sourceKey="openai-blog",sourceConfigVersion=1,sourceType=SourceType.RSS_ATOM,sourceName="OpenAI Blog")
        try {
            val result = persistenceService.publish(source,null)
            assertEquals("PENDING",jdbcTemplate.queryForObject(
                "select status from article_full_texts where article_id=?",String::class.java,result.articleId))
            assertEquals(false,jdbcTemplate.queryForObject(
                "select requested_summary from article_full_texts where article_id=?",Boolean::class.java,result.articleId))
            assertEquals(0,jdbcTemplate.queryForObject(
                "select count(*) from article_summary_jobs where article_id=?",Int::class.java,result.articleId))
            assertEquals(source.extractedText,jdbcTemplate.queryForObject(
                "select extracted_text from article_raw_contents where article_id=?",String::class.java,result.articleId))
            assertEquals(CollectedArticlePublishOutcome.SKIPPED_DUPLICATE,persistenceService.publish(source,null).outcome)
            assertEquals(1,jdbcTemplate.queryForObject(
                "select count(*) from article_full_texts where article_id=?",Int::class.java,result.articleId))
        } finally { deleteArticleByCanonicalUrl(source.canonicalUrl); deleteTestSource() }
    }

    @Test
    fun `registered source identity survives a Korean display name change`() {
        val key="openai-blog"
        val original=jdbcTemplate.queryForMap("select id,name from news_sources where source_key=?",key)
        val url="https://openai.com/index/stable-source-identity-fixture"
        jdbcTemplate.update("update news_sources set name='한글 표시 이름' where source_key=?",key)
        try {
            val source=com.sigak.collection.domain.NewsSource(key,"한글 표시 이름",SourceType.RSS_ATOM,"https://openai.com/news/rss.xml","AI",1)
            val xml="""<rss><channel><item><guid>$url</guid><title>Stable identity fixture</title><link>$url</link>
                <pubDate>Tue, 05 May 2026 09:00:00 GMT</pubDate><description>Source evidence</description></item></channel></rss>"""
            val article=com.sigak.collection.collector.RssAtomCollector().parse(source,xml).single()
            val result=persistenceService.publish(article,null)
            assertEquals((original["id"] as Number).toLong(),jdbcTemplate.queryForObject("select source_id from articles where id=?",Long::class.java,result.articleId))
        } finally {
            deleteArticleByCanonicalUrl(url)
            jdbcTemplate.update("update news_sources set name=? where source_key=?",original["name"],key)
            jdbcTemplate.update("delete from news_sources where source_key='manual-source' and not exists(select 1 from articles where articles.source_id=news_sources.id)")
        }
    }

    @Test
    fun `verified feed publication stores full text without fetching or requesting AI`() {
        val key="verified-feed-fixture"
        val url="https://engineering.example.test/full-body-fixture"
        jdbcTemplate.update("""insert into news_sources(source_key,name,type,url,feed_url,collection_enabled,
            validation_status,validated_config_version,allowed_hosts,body_mode,feed_content_verified)
            values(?,'한글 기술 블로그','RSS_ATOM','https://engineering.example.test/feed','https://engineering.example.test/feed',
                true,'PASSED',1,'["engineering.example.test"]','FEED_FULL',true)""",key)
        val html="<h1>Engineering choices</h1><p>"+"Opening evidence explains the architecture. ".repeat(12)+"</p><pre><code>x &lt; y</code></pre><p>Closing evidence explains the measured result.</p>"
        val article=collectedArticle(url,"Full feed fixture").copy(sourceKey=key,sourceConfigVersion=1,sourceType=SourceType.RSS_ATOM,
            sourceName="한글 기술 블로그",rawContent=html,extractedText=org.jsoup.Jsoup.parse(html).text(),feedBodyHtml=html,feedExcerpt="Short source excerpt")
        try {
            val before=jdbcTemplate.queryForObject("select count(*) from article_summary_jobs",Int::class.java)
            val result=persistenceService.publish(article,null)
            assertEquals(1,jdbcTemplate.queryForObject("select count(*) from article_full_texts where article_id=?",Int::class.java,result.articleId))
            val full=jdbcTemplate.queryForMap("select * from article_full_texts where article_id=?",result.articleId)
            assertEquals("READY",full["status"])
            assertEquals("FEED",full["body_origin"])
            assertEquals(false,full["requested_summary"])
            kotlin.test.assertTrue((full["full_text"] as String).contains("Closing evidence"))
            kotlin.test.assertTrue((full["full_text"] as String).contains("x < y"))
            assertEquals(before,jdbcTemplate.queryForObject("select count(*) from article_summary_jobs",Int::class.java))
            assertEquals("Short source excerpt",articleService.getArticle(result.articleId)?.sourceExcerpt)
        } finally {
            deleteArticleByCanonicalUrl(url)
            jdbcTemplate.update("delete from news_sources where source_key=?",key)
        }
    }

    private fun collectedArticle(externalId: String, title: String): CollectedArticle =
        CollectedArticle(
            sourceName = "arXiv cs.AI Test",
            sourceType = SourceType.ARXIV,
            externalId = externalId,
            url = externalId,
            canonicalUrl = externalId,
            title = title,
            publishedAt = "2026-05-08T00:00:00Z",
            authorNames = listOf("Ada Lovelace"),
            rawContent = "Raw article content from the collector.",
            extractedText = "Extracted article content from the collector.",
            categoryHint = "CS_RESEARCH",
            sourceKey = "arxiv-cs-ai-test",
            sourceConfigVersion = 1
        )

    private fun enrichmentResponse(): EnrichmentResponse =
        EnrichmentResponse(
            summary = "A collected article was enriched and persisted.",
            whyItMatters = "It proves the collection pipeline can feed the public API.",
            suggestedTopics = listOf("CS_RESEARCH", "collection pipeline"),
            suggestedPrimaryCategory = "CS_RESEARCH",
            suggestedImportanceScore = 76,
            modelName = "test-enrichment-model"
        )

    private fun deleteArticleByCanonicalUrl(canonicalUrl: String) {
        jdbcTemplate.update("delete from articles where canonical_url = ?", canonicalUrl)
    }

    private fun deleteTestSource() {
        jdbcTemplate.update("delete from news_sources where source_key = ?", "arxiv-cs-ai-test")
    }
}
