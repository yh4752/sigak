package com.sigak.collection.service

import com.sigak.collection.domain.CollectedArticle
import com.sigak.collection.domain.SourceType
import com.sigak.collection.dto.EnrichmentResponse
import kotlin.test.Test
import kotlin.test.assertEquals

class CollectionPipelineServiceTest {

    private val normalizer = ArticleNormalizer()
    private val publishedArticleIds = mutableListOf<Long>()
    private val pipelineService = CollectionPipelineService(
        articleNormalizer = normalizer,
        enrichmentClient = { request ->
            EnrichmentResponse(
                summary = "${request.title} summary",
                whyItMatters = "${request.source} insight",
                suggestedTopics = request.topics,
                suggestedPrimaryCategory = request.topics.first(),
                suggestedImportanceScore = 70
            )
        },
        collectedArticlePublisher = { _, _ ->
            publishedArticleIds.add(42L)
            42L
        }
    )

    @Test
    fun enrichCollectedArticleReturnsEnrichmentCandidate() {
        val collectedArticle = CollectedArticle(
            sourceName = "arXiv cs.AI",
            sourceType = SourceType.ARXIV,
            externalId = "http://arxiv.org/abs/2605.00001v1",
            url = "http://arxiv.org/abs/2605.00001v1",
            canonicalUrl = "http://arxiv.org/abs/2605.00001v1",
            title = "Evaluating Retrieval Agents",
            publishedAt = "2026-05-05T00:00:00Z",
            authorNames = listOf("Ada Lovelace"),
            rawContent = "We study retrieval agents.",
            extractedText = "We study retrieval agents."
        )

        val enrichment = pipelineService.enrich(collectedArticle)

        assertEquals("Evaluating Retrieval Agents summary", enrichment.summary)
        assertEquals("arXiv cs.AI insight", enrichment.whyItMatters)
        assertEquals(listOf("CS_RESEARCH"), enrichment.suggestedTopics)
        assertEquals("CS_RESEARCH", enrichment.suggestedPrimaryCategory)
    }

    @Test
    fun publishCollectedArticleEnrichesAndPersistsArticle() {
        val collectedArticle = collectedArticle()

        val publishedArticleId = pipelineService.publish(collectedArticle)

        assertEquals(42L, publishedArticleId)
        assertEquals(listOf(42L), publishedArticleIds)
    }

    private fun collectedArticle(): CollectedArticle =
        CollectedArticle(
            sourceName = "arXiv cs.AI",
            sourceType = SourceType.ARXIV,
            externalId = "http://arxiv.org/abs/2605.00001v1",
            url = "http://arxiv.org/abs/2605.00001v1",
            canonicalUrl = "http://arxiv.org/abs/2605.00001v1",
            title = "Evaluating Retrieval Agents",
            publishedAt = "2026-05-05T00:00:00Z",
            authorNames = listOf("Ada Lovelace"),
            rawContent = "We study retrieval agents.",
            extractedText = "We study retrieval agents."
        )
}
