package com.sigak.collection.service

import com.sigak.collection.dto.EnrichmentRequest
import kotlin.test.Test
import kotlin.test.assertEquals

class MockEnrichmentClientTest {

    private val client = MockEnrichmentClient()

    @Test
    fun enrichReturnsDeterministicLocalResponse() {
        val response = client.enrich(
            EnrichmentRequest(
                title = "Evaluating Retrieval Agents",
                source = "arXiv cs.AI",
                url = "http://arxiv.org/abs/2605.00001v1",
                publishedAt = "2026-05-05T00:00:00Z",
                topics = listOf("CS_RESEARCH"),
                rawContent = "We study retrieval agents in technical knowledge workflows."
            )
        )

        assertEquals("Evaluating Retrieval Agents discusses We study retrieval agents in technical knowledge workflows.", response.summary)
        assertEquals("CS_RESEARCH", response.suggestedPrimaryCategory)
        assertEquals(70, response.suggestedImportanceScore)
    }
}
