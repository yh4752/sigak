package com.sigak.article.fulltext

import kotlin.test.*

class ArticleBodyExtractorTest {
    private val extractor=ArticleBodyExtractor()
    private val body="<h2>Opening</h2><p>"+"Supported article evidence. ".repeat(20)+"</p><h2>Conclusion</h2><p>Closing paragraph with the final result.</p>"

    @Test fun `openai preserves the closing section and removes noise`() {
        val text=extractor.extract("https://openai.com/index/example", "<main><article>$body<nav>MENU NOISE</nav><aside>RELATED NOISE</aside></article></main>")
        assertTrue(text.contains("Closing paragraph"));assertFalse(text.contains("NOISE"))
    }
    @Test fun `github selects content instead of earlier author article`() {
        val text=extractor.extract("https://github.blog/news/example", "<main><article class='author-bio'><p>WRONG AUTHOR</p></article><section class='post__content'>$body<article class='post-card'>WRONG RELATED</article></section></main>")
        assertTrue(text.contains("Closing paragraph"));assertFalse(text.contains("WRONG"))
    }
    @Test fun `google body includes middle and tail paragraphs`() {
        assertTrue(extractor.extract("https://blog.google/products/example", "<article>$body</article>").contains("final result"))
    }
    @Test fun `empty or incomplete root is not a full article`() {
        assertFails { extractor.extract("https://github.blog/news/example", "<article>Author only</article>") }
        assertFails { extractor.extract("https://openai.com/index/example", "<article><p>Short teaser.</p></article>") }
    }
    @Test fun `unapproved url and local targets are rejected`() {
        for(url in listOf("http://openai.com/index/x","https://localhost/x","https://openai.com.evil.test/x","https://openai.com:8443/x","https://user@openai.com/x","https://arxiv.org/abs/x")) {
            assertFalse(ArticleBodyPolicy.supported(url))
        }
        assertFalse(ArticleBodyPolicy.publicAddress(java.net.InetAddress.getByName("127.0.0.1")))
        assertFalse(ArticleBodyPolicy.publicAddress(java.net.InetAddress.getByName("169.254.169.254")))
    }
    @Test fun `literal code and first middle tail content survive noise removal`() {
        val html="""<article><header>HEADER NOISE</header><p>${"FIRST evidence sentence. ".repeat(15)}</p>
            <h2>Middle section</h2><p>Middle paragraph explains implementation behavior.</p>
            <pre><code>if (value &lt; limit) { return &quot;&lt;tag&gt;&quot;; }
  next();</code></pre><p>TAIL evidence contains the final result.</p>
            <form>FORM NOISE</form><div class="newsletter">NEWSLETTER NOISE</div>
            <div class="toc">TOC NOISE</div><footer>FOOTER NOISE</footer><script>SCRIPT NOISE</script></article>"""
        val text=extractor.extract("https://openai.com/index/example",html)
        assertTrue(text.contains("FIRST evidence"))
        assertTrue(text.contains("Middle paragraph"))
        assertTrue(text.contains("TAIL evidence"))
        assertTrue(text.contains("if (value < limit) { return \"<tag>\"; }\n  next();"))
        assertFalse(text.contains("NOISE"))
    }

    @Test fun `source body containers exclude related cards and sharing widgets`() {
        val openai="<article><div>SHARE NOISE</div><div data-toc-content>$body</div><div><h2>Keep reading</h2><p>RELATED NOISE</p></div></article>"
        val google="<article><section class='uni-article-hero'>SHARE NOISE</section><section><div class='article-container__content'>$body<uni-related-content-tout>RELATED NOISE</uni-related-content-tout><div class='uni-blog-article-tags__wrapper'>TAG NOISE</div></div></section></article>"
        assertFalse(extractor.extract("https://openai.com/index/example",openai).contains("NOISE"))
        assertFalse(extractor.extract("https://blog.google/products/example",google).contains("NOISE"))
        val github="<section class='post__content'>$body<section><h2>Tags:</h2>TAG NOISE</section><div><h2>Written by</h2>AUTHOR NOISE</div></section>"
        assertFalse(extractor.extract("https://github.blog/news/example",github).contains("NOISE"))
    }

    @Test fun `public wordpress source addresses are not mistaken for reserved blocks`() {
        assertTrue(ArticleBodyPolicy.publicAddress(java.net.InetAddress.getByName("192.0.66.2")))
        assertTrue(ArticleBodyPolicy.publicAddress(java.net.InetAddress.getByName("198.51.42.2")))
        assertFalse(ArticleBodyPolicy.publicAddress(java.net.InetAddress.getByName("192.0.2.2")))
        assertFalse(ArticleBodyPolicy.publicAddress(java.net.InetAddress.getByName("198.51.100.2")))
    }

}
