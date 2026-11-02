package com.sigak.article.fulltext

import kotlin.test.*

class ConfiguredArticleBodyExtractorTest {
    private val extractor = ArticleBodyExtractor()
    private val opening = "Detailed opening evidence and architecture choices. ".repeat(8)
    private val body = "<h1>Engineering</h1><p>$opening</p><pre><code>if (n &lt; 3) next();</code></pre><p>Closing evidence explains the final result.</p>"

    @Test fun `new source uses a single article without a host-specific code branch`() {
        val text=extractor.extractConfigured("https://engineering.example.test/post", "<main><article>$body</article></main>",null,emptyList())
        assertTrue(text.contains("Closing evidence"))
        assertTrue(text.contains("if (n < 3) next();"))
    }
    @Test fun `ambiguous articles and explicit missing root never fall back`() {
        val html="<main><article>$body</article><article>$body</article></main>"
        assertEquals("BODY_AMBIGUOUS",assertFailsWith<FullTextException> { extractor.extractConfigured("https://engineering.example.test/a",html,null,emptyList()) }.code)
        assertFailsWith<FullTextException> { extractor.extractConfigured("https://engineering.example.test/a","<article>$body</article>",".missing",emptyList()) }
    }
    @Test fun `configured exclusions remove adjacent recommendation content`() {
        val text=extractor.extractConfigured("https://engineering.example.test/a","<section class='body'>$body<div class='related'><p>UNRELATED EVIDENCE</p></div></section>","section.body",listOf(".related"))
        assertFalse(text.contains("UNRELATED"))
    }
    @Test fun `feed bodies preserve code but reject teaser and link lists`() {
        assertTrue(extractor.extractFeed(body).contains("if (n < 3) next();"))
        assertEquals("BODY_EXCERPT",assertFailsWith<FullTextException> { extractor.extractFeed("$body<p>Continue reading on the original site</p>") }.code)
        val links="<p><a href='/a'>$opening</a></p><p><a href='/b'>Closing evidence explains the final result.</a></p>"
        assertEquals("BODY_LINK_LIST",assertFailsWith<FullTextException> { extractor.extractFeed(links) }.code)
    }
    @Test fun `source exclusions apply to feed bodies as well as pages`() {
        val html=body+"<p class='feed-footer'>Feed syndication marker with an unrelated source promotion.</p>"
        val text=extractor.extractFeed(html,listOf(".feed-footer"))
        assertFalse(text.contains("syndication marker"))
        assertTrue(text.contains("Closing evidence"))
    }

}
