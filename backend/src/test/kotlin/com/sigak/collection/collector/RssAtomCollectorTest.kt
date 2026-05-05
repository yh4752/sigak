package com.sigak.collection.collector

import com.sigak.collection.domain.NewsSource
import com.sigak.collection.domain.SourceType
import kotlin.test.Test
import kotlin.test.assertEquals

class RssAtomCollectorTest {

    private val collector = RssAtomCollector()

    private val source = NewsSource(
        id = "example-feed",
        name = "Example Engineering Blog",
        type = SourceType.RSS_ATOM,
        url = "https://example.com/feed.xml",
        categoryHint = "SOFTWARE_ENGINEERING"
    )

    @Test
    fun parseRssFeedItemIntoCollectedArticle() {
        val xml = """
            <rss version="2.0">
              <channel>
                <item>
                  <guid>https://example.com/articles/reliable-builds</guid>
                  <title>Reliable Builds for AI Toolchains</title>
                  <link>https://example.com/articles/reliable-builds</link>
                  <pubDate>Tue, 05 May 2026 09:00:00 GMT</pubDate>
                  <description>Build systems need stronger provenance as AI coding tools grow.</description>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val articles = collector.parse(source, xml)

        assertEquals(1, articles.size)
        assertEquals("Example Engineering Blog", articles[0].sourceName)
        assertEquals(SourceType.RSS_ATOM, articles[0].sourceType)
        assertEquals("https://example.com/articles/reliable-builds", articles[0].externalId)
        assertEquals("Reliable Builds for AI Toolchains", articles[0].title)
        assertEquals("Build systems need stronger provenance as AI coding tools grow.", articles[0].extractedText)
    }
}
