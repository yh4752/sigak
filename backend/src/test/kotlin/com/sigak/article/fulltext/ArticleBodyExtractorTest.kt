package com.sigak.article.fulltext

import kotlin.test.*

class ArticleBodyExtractorTest {
    private val extractor=ArticleBodyExtractor()
    private val body="<h2>Opening</h2><p>"+"Supported article evidence. ".repeat(20)+"</p><h2>Conclusion</h2><p>Closing paragraph with the final result.</p>"

    @Test fun `openai preserves the closing section and removes noise`() {
        val text=extractLegacy("https://openai.com/index/example", "<main><article>$body<nav>MENU NOISE</nav><aside class='post-card'>RELATED NOISE</aside></article></main>")
        assertTrue(text.contains("Closing paragraph"));assertFalse(text.contains("NOISE"))
    }
    @Test fun `github selects content instead of earlier author article`() {
        val text=extractLegacy("https://github.blog/news/example", "<main><article class='author-bio'><p>WRONG AUTHOR</p></article><section class='post__content'>$body<article class='post-card'>WRONG RELATED</article></section></main>")
        assertTrue(text.contains("Closing paragraph"));assertFalse(text.contains("WRONG"))
    }
    @Test fun `google body includes middle and tail paragraphs`() {
        assertTrue(extractLegacy("https://blog.google/products/example", "<article>$body</article>").contains("final result"))
    }
    @Test fun `article callout lists survive page and feed extraction`() {
        val html = """<article>$body
            <aside><ul><li>Use the local model for confident requests.</li>
            <li>Fallback to the larger model for uncertain requests.</li></ul>
            <p>Validate coverage against the required accuracy.</p></aside>
            <aside class="post-card">RELATED NOISE</aside></article>"""
        val texts = listOf(
            extractor.extractConfigured("https://engineering.example.test/article", html, null, emptyList()),
            extractor.extractFeed(html)
        )
        texts.forEach { text ->
            assertTrue(text.contains("Use the local model for confident requests."))
            assertTrue(text.contains("Fallback to the larger model for uncertain requests."))
            assertTrue(text.contains("Validate coverage against the required accuracy."))
            assertFalse(text.contains("RELATED NOISE"))
        }
    }
    @Test fun `empty or incomplete root is not a full article`() {
        assertFails { extractLegacy("https://github.blog/news/example", "<article>Author only</article>") }
        assertFails { extractLegacy("https://openai.com/index/example", "<article><p>Short teaser.</p></article>") }
    }
    @Test fun `unapproved url and local targets are rejected`() {
        for(url in listOf("http://openai.com/index/x","https://localhost/x","https://openai.com.evil.test/x","https://openai.com:8443/x","https://user@openai.com/x","https://arxiv.org/abs/x")) {
            assertFails { com.sigak.common.http.PublicUrlPolicy.requireUrl(url,setOf("openai.com","blog.google","github.blog")) }
        }
        assertFalse(com.sigak.common.http.PublicUrlPolicy.publicAddress(java.net.InetAddress.getByName("127.0.0.1")))
        assertFalse(com.sigak.common.http.PublicUrlPolicy.publicAddress(java.net.InetAddress.getByName("169.254.169.254")))
    }
    @Test fun `literal code and first middle tail content survive noise removal`() {
        val html="""<article><header>HEADER NOISE</header><p>${"FIRST evidence sentence. ".repeat(15)}</p>
            <h2>Middle section</h2><p>Middle paragraph explains implementation behavior.</p>
            <pre><code>if (value &lt; limit) { return &quot;&lt;tag&gt;&quot;; }
  next();</code></pre><p>TAIL evidence contains the final result.</p>
            <form>FORM NOISE</form><div class="newsletter">NEWSLETTER NOISE</div>
            <div class="toc">TOC NOISE</div><footer>FOOTER NOISE</footer><script>SCRIPT NOISE</script></article>"""
        val text=extractLegacy("https://openai.com/index/example",html)
        assertTrue(text.contains("FIRST evidence"))
        assertTrue(text.contains("Middle paragraph"))
        assertTrue(text.contains("TAIL evidence"))
        assertTrue(text.contains("if (value < limit) { return \"<tag>\"; }\n  next();"))
        assertFalse(text.contains("NOISE"))
    }

    @Test fun `source body containers exclude related cards and sharing widgets`() {
        val openai="<article><div>SHARE NOISE</div><div data-toc-content>$body</div><div><h2>Keep reading</h2><p>RELATED NOISE</p></div></article>"
        val google="<article><section class='uni-article-hero'>SHARE NOISE</section><section><div class='article-container__content'>$body<uni-related-content-tout>RELATED NOISE</uni-related-content-tout><div class='uni-blog-article-tags__wrapper'>TAG NOISE</div></div></section></article>"
        assertFalse(extractLegacy("https://openai.com/index/example",openai).contains("NOISE"))
        assertFalse(extractLegacy("https://blog.google/products/example",google).contains("NOISE"))
        val github="<section class='post__content'>$body<section><h2>Tags:</h2>TAG NOISE</section><div><h2>Written by</h2>AUTHOR NOISE</div></section>"
        assertFalse(extractLegacy("https://github.blog/news/example",github).contains("NOISE"))
    }

    @Test fun `public wordpress source addresses are not mistaken for reserved blocks`() {
        assertTrue(com.sigak.common.http.PublicUrlPolicy.publicAddress(java.net.InetAddress.getByName("192.0.66.2")))
        assertTrue(com.sigak.common.http.PublicUrlPolicy.publicAddress(java.net.InetAddress.getByName("198.51.42.2")))
        assertFalse(com.sigak.common.http.PublicUrlPolicy.publicAddress(java.net.InetAddress.getByName("192.0.2.2")))
        assertFalse(com.sigak.common.http.PublicUrlPolicy.publicAddress(java.net.InetAddress.getByName("198.51.100.2")))
    }
    private fun extractLegacy(url:String,html:String):String {
        val selector=when(java.net.URI(url).host) {
            "github.blog" -> "section.post__content"
            "openai.com" -> "article [data-toc-content],article:not(:has([data-toc-content]))"
            else -> "article .article-container__content,article:not(:has(.article-container__content))"
        }
        return extractor.extractConfigured(url,html,selector,listOf("section:has(>h2:matchesOwn(^Tags:$))","div:has(>h2:matchesOwn(^Written by$))"))
    }

}
