package com.sigak.collection.collector

import com.sigak.collection.domain.NewsSource
import com.sigak.collection.domain.SourceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        assertEquals("SOFTWARE_ENGINEERING", articles[0].categoryHint)
        assertEquals("Build systems need stronger provenance as AI coding tools grow.", articles[0].extractedText)
    }

    @Test
    fun parseRssFeedPrefersEncodedContentAndExtractsPlainText() {
        val xml = """
            <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/">
              <channel>
                <item>
                  <guid>https://example.com/articles/agent-release</guid>
                  <title>Agent Release Notes</title>
                  <link>https://example.com/articles/agent-release</link>
                  <pubDate>Tue, 05 May 2026 09:00:00 GMT</pubDate>
                  <description><![CDATA[<p>Short teaser.</p>]]></description>
                  <content:encoded><![CDATA[
                    <article>
                      <p>Full release notes explain the agent runtime.</p>
                      <p>The update changes production workflows.</p>
                    </article>
                  ]]></content:encoded>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val articles = collector.parse(source, xml)

        assertEquals("Full release notes explain the agent runtime. The update changes production workflows.", articles[0].extractedText)
        assertTrue(articles[0].rawContent.contains("<article>"))
        assertTrue(articles[0].rawContent.contains("Full release notes explain the agent runtime."))
    }

    @Test
    fun parseRssFeedUsesTitleAsExtractedTextWhenFeedContentIsMissing() {
        val xml = """
            <rss version="2.0">
              <channel>
                <item>
                  <guid>https://example.com/articles/system-card</guid>
                  <title>Model System Card</title>
                  <link>https://example.com/articles/system-card</link>
                  <pubDate>Tue, 05 May 2026 09:00:00 GMT</pubDate>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val articles = collector.parse(source, xml)

        assertEquals("", articles[0].rawContent)
        assertEquals("Model System Card", articles[0].extractedText)
    }
    @Test
    fun `rss content namespace works with an alternate prefix and preserves code`() {
        val xml="""<rss xmlns:c="http://purl.org/rss/1.0/modules/content/"><channel><item>
          <title>Namespace example</title><link>https://example.com/a</link><guid>a</guid>
          <pubDate>Tue, 05 May 2026 09:00:00 GMT</pubDate><description>Short excerpt</description>
          <c:encoded><![CDATA[<p>Opening evidence</p><pre><code>if (n &lt; 3) next();</code></pre><p>Closing evidence</p>]]></c:encoded>
        </item></channel></rss>"""
        val article=collector.parse(source,xml).single()
        assertTrue(article.rawContent.contains("Closing evidence"))
        assertTrue(article.extractedText.contains("if (n < 3) next();"))
    }

    @Test
    fun `atom html content is retained instead of losing the full body`() {
        val xml="""<feed xmlns="http://www.w3.org/2005/Atom"><entry><id>a</id>
          <title>HTML article</title><updated>2026-05-05T09:00:00Z</updated><link href="https://example.com/a"/>
          <summary>Short excerpt</summary><content type="html">&lt;p&gt;Opening evidence&lt;/p&gt;&lt;p&gt;Closing evidence&lt;/p&gt;</content>
        </entry></feed>"""
        val article=collector.parse(source,xml).single()
        assertTrue(article.rawContent.contains("<p>Closing evidence</p>"))
        assertTrue(article.extractedText.contains("Closing evidence"))
    }

    @Test
    fun `atom xhtml retains child markup and ignores external content`() {
        val xml="""<a:feed xmlns:a="http://www.w3.org/2005/Atom"><a:entry><a:id>a</a:id>
          <a:title>XHTML article</a:title><a:updated>2026-05-05T09:00:00Z</a:updated><a:link href="https://example.com/a"/>
          <a:content type="xhtml"><div xmlns="http://www.w3.org/1999/xhtml"><p>Opening</p><pre><code>x &lt; y</code></pre><p>Closing</p></div></a:content>
        </a:entry></a:feed>"""
        val article=collector.parse(source,xml).single()
        assertTrue(article.rawContent.contains("<code>"))
        assertTrue(article.extractedText.contains("x < y"))
        val external=xml.replace("type=\"xhtml\"", "type=\"xhtml\" src=\"https://private.example.test/secret\"")
        assertEquals("", collector.parse(source,external).single().rawContent)
    }

    @Test
    fun `decoded XML declaration does not reinterpret Korean characters`() {
        val xml="""<?xml version="1.0" encoding="EUC-KR"?><rss><channel><item><guid>a</guid><title>한글 본문</title><link>https://example.com/a</link><description>설명</description></item></channel></rss>"""
        assertEquals("한글 본문",collector.parse(source,xml).single().title)
    }

    @Test
    fun `extension namespaces cannot shadow an Atom title or alternate link`() {
        val xml="""<feed xmlns="http://www.w3.org/2005/Atom" xmlns:m="https://media.example.test/ns"><entry>
          <id>a</id><m:title>Wrong title</m:title><title>Correct title</title>
          <m:link href="https://wrong.example.test/"/><link href="https://example.com/a"/>
          <summary>Source excerpt</summary></entry></feed>"""
        val parsed=collector.parse(source,xml).single()
        assertEquals("Correct title",parsed.title)
        assertEquals("https://example.com/a",parsed.url)
    }

    @Test
    fun `full feed body without a description has an explicit empty public excerpt`() {
        val xml="""<rss xmlns:c="http://purl.org/rss/1.0/modules/content/"><channel><item><guid>a</guid><title>Title</title><link>https://example.com/a</link><c:encoded><![CDATA[<p>Private full source body</p>]]></c:encoded></item></channel></rss>"""
        assertEquals("",collector.parse(source,xml).single().feedExcerpt)
    }

    @Test
    fun `Atom relative links use the same inherited xml base in validation and collection`() {
        val xml="""<feed xmlns="http://www.w3.org/2005/Atom" xml:base="/blog/"><entry xml:base="2026/">
          <id>a</id><title>Relative article</title><updated>2026-05-05T09:00:00Z</updated><link href="post"/>
          <summary>Source excerpt</summary></entry></feed>"""
        assertEquals("https://example.com/blog/2026/post",collector.parse(source,xml).single().url)
    }

}
