package com.sigak.search.graph

import com.sigak.article.domain.ArticleEnrichmentEntity
import com.sigak.article.domain.ArticleEntity
import com.sigak.article.domain.ArticleRelationEntity
import com.sigak.article.domain.ArticleTopicEntity
import com.sigak.article.domain.EventType
import com.sigak.article.domain.PrimaryCategory
import com.sigak.article.domain.ProcessingStatus
import com.sigak.article.domain.RelationType
import com.sigak.article.repository.ArticleRepository
import com.sigak.source.domain.NewsSourceEntity
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class ArticleGraphProjectionReaderTest {

    private val articleRepository = mock(ArticleRepository::class.java)
    private val reader = ArticleGraphProjectionReader(articleRepository)

    @Test
    fun readApiReadyGraphDocumentsPreservesTopicsRelationsAndReasons() {
        val source = source()
        val targetArticle = article(id = 2L, title = "Target article", source = source)
        val sourceArticle = article(id = 1L, title = "Source article", source = source)
        sourceArticle.topics.add(topic(sourceArticle, " knowledge graphs ", 1))
        sourceArticle.topics.add(topic(sourceArticle, " Graph RAG ", 0))
        sourceArticle.outgoingRelations.add(
            relation(
                id = 10L,
                sourceArticle = sourceArticle,
                targetArticle = targetArticle,
                reason = "Both articles explain graph-aware retrieval."
            )
        )

        `when`(articleRepository.findApiReadyArticles(ProcessingStatus.PUBLISHED))
            .thenReturn(listOf(sourceArticle, targetArticle))

        val documents = reader.readApiReadyGraphDocuments()

        assertEquals(2, documents.size)
        val document = documents.first { projectionDocument -> projectionDocument.articleId == 1L }
        assertEquals(1L, document.articleId)
        assertEquals("Source article", document.title)
        assertEquals("OpenAI", document.source)
        assertEquals("https://example.com/articles/1", document.url)
        assertEquals("2026-06-03T00:00:00Z", document.publishedAt)
        assertEquals("OFFICIAL_ANNOUNCEMENT", document.eventType)
        assertEquals("AI", document.primaryCategory)
        assertEquals(80, document.importanceScore)
        assertEquals(listOf("Graph RAG", "knowledge graphs"), document.topics.map { topic -> topic.displayName })
        assertEquals(listOf("graph rag", "knowledge graphs"), document.topics.map { topic -> topic.name })
        assertEquals(listOf(0, 1), document.topics.map { topic -> topic.position })
        assertEquals(1, document.outgoingRelations.size)
        assertEquals(1L, document.outgoingRelations.single().sourceArticleId)
        assertEquals(2L, document.outgoingRelations.single().targetArticleId)
        assertEquals("RELATED", document.outgoingRelations.single().relationType)
        assertEquals("Both articles explain graph-aware retrieval.", document.outgoingRelations.single().reason)
        verify(articleRepository).fetchArticleResponseGraph(listOf(sourceArticle, targetArticle))
    }

    @Test
    fun readApiReadyGraphDocumentsSkipsRelationsWhoseTargetsAreNotApiReady() {
        val source = source()
        val missingTarget = article(id = 99L, title = "Draft article", source = source)
        val sourceArticle = article(id = 1L, title = "Source article", source = source)
        sourceArticle.outgoingRelations.add(
            relation(
                id = 11L,
                sourceArticle = sourceArticle,
                targetArticle = missingTarget,
                reason = "Target is not API-ready."
            )
        )

        `when`(articleRepository.findApiReadyArticles(ProcessingStatus.PUBLISHED))
            .thenReturn(listOf(sourceArticle))

        val documents = reader.readApiReadyGraphDocuments()

        assertEquals(emptyList(), documents.single().outgoingRelations)
    }

    private fun source(): NewsSourceEntity =
        NewsSourceEntity(
            id = 1L,
            sourceKey = "openai",
            name = "OpenAI",
            type = "rss",
            url = "https://example.com/feed.xml"
        )

    private fun article(
        id: Long,
        title: String,
        source: NewsSourceEntity
    ): ArticleEntity =
        ArticleEntity(
            id = id,
            source = source,
            title = title,
            url = "https://example.com/articles/$id",
            canonicalUrl = "https://example.com/articles/$id",
            publishedAt = Instant.parse("2026-06-03T00:00:00Z"),
            eventType = EventType.OFFICIAL_ANNOUNCEMENT,
            primaryCategory = PrimaryCategory.AI,
            importanceScore = 80,
            processingStatus = ProcessingStatus.PUBLISHED,
            createdAt = Instant.parse("2026-06-03T00:00:00Z"),
            updatedAt = Instant.parse("2026-06-03T00:00:00Z")
        ).also { article ->
            article.enrichments.add(
                ArticleEnrichmentEntity(
                    article = article,
                    summary = "Summary",
                    whyItMatters = "Why it matters",
                    suggestedPrimaryCategory = PrimaryCategory.AI.name,
                    suggestedImportanceScore = 80,
                    modelName = "test",
                    promptVersion = "test",
                    current = true,
                    enrichedAt = Instant.parse("2026-06-03T00:00:00Z")
                )
            )
        }

    private fun topic(article: ArticleEntity, name: String, position: Int): ArticleTopicEntity =
        ArticleTopicEntity(article = article, topic = name, position = position)

    private fun relation(
        id: Long?,
        sourceArticle: ArticleEntity,
        targetArticle: ArticleEntity,
        reason: String?
    ): ArticleRelationEntity =
        ArticleRelationEntity(
            id = id,
            sourceArticle = sourceArticle,
            targetArticle = targetArticle,
            relationType = RelationType.RELATED,
            reason = reason
        )
}
