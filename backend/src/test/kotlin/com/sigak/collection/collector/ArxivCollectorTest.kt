package com.sigak.collection.collector

import com.sigak.collection.domain.NewsSource
import com.sigak.collection.domain.SourceType
import kotlin.test.Test
import kotlin.test.assertEquals

class ArxivCollectorTest {

    private val collector = ArxivCollector()

    private val source = NewsSource(
        id = "arxiv-cs-ai",
        name = "arXiv cs.AI",
        type = SourceType.ARXIV,
        url = "https://export.arxiv.org/api/query?search_query=cat:cs.AI",
        categoryHint = "CS_RESEARCH"
    )

    @Test
    fun parseArxivEntryIntoCollectedArticle() {
        val xml = """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry>
                <id>http://arxiv.org/abs/2605.00001v1</id>
                <published>2026-05-05T00:00:00Z</published>
                <title>Evaluating Retrieval Agents</title>
                <summary>We study retrieval agents in technical knowledge workflows.</summary>
                <author><name>Ada Lovelace</name></author>
                <author><name>Grace Hopper</name></author>
                <link href="http://arxiv.org/abs/2605.00001v1" rel="alternate" type="text/html"/>
              </entry>
            </feed>
        """.trimIndent()

        val articles = collector.parse(source, xml)

        assertEquals(1, articles.size)
        assertEquals("arXiv cs.AI", articles[0].sourceName)
        assertEquals(SourceType.ARXIV, articles[0].sourceType)
        assertEquals("http://arxiv.org/abs/2605.00001v1", articles[0].externalId)
        assertEquals("Evaluating Retrieval Agents", articles[0].title)
        assertEquals(listOf("Ada Lovelace", "Grace Hopper"), articles[0].authorNames)
        assertEquals("We study retrieval agents in technical knowledge workflows.", articles[0].extractedText)
    }
}
