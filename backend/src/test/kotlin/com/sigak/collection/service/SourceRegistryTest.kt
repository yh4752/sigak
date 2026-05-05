package com.sigak.collection.service

import com.sigak.collection.domain.SourceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SourceRegistryTest {

    private val sourceRegistry = SourceRegistry()

    @Test
    fun sourcesIncludeOfficialFeedsAndArxivResearchSources() {
        val sources = sourceRegistry.sources()

        assertTrue(sources.any { it.name == "OpenAI Blog" && it.type == SourceType.RSS_ATOM })
        assertTrue(sources.any { it.name == "Google AI Blog" && it.type == SourceType.RSS_ATOM })
        assertTrue(sources.any { it.name == "arXiv cs.AI" && it.type == SourceType.ARXIV })
        assertTrue(sources.any { it.name == "arXiv cs.LG" && it.type == SourceType.ARXIV })
        assertTrue(sources.none { it.name.contains("Hacker News", ignoreCase = true) })
    }

    @Test
    fun sourcesHaveStableIdentifiers() {
        val ids = sourceRegistry.sources().map { it.id }

        assertEquals(ids.distinct(), ids)
    }
}
