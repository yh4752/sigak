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
            categoryHint = "CS_RESEARCH"
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
