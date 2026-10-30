package com.sigak.article.fulltext

import java.net.URI
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor
import org.springframework.stereotype.Component

@Component
class ArticleBodyExtractor {
    fun extract(url: String, html: String): String {
        if (!ArticleBodyPolicy.supported(url)) throw FullTextException("SOURCE_NOT_SUPPORTED")
        val document = Jsoup.parse(html, url)
        document.select("nav,aside,header,footer,script,style,noscript,form,.author-bio,.post-card,.newsletter,.toc,uni-related-content-tout,.uni-blog-article-tags__wrapper,[class*=newsletter],[class*=table-of-contents]").remove()
        // GitHub의 첫 article은 저자 소개이므로 검증한 본문 컨테이너만 선택한다.
        val host = URI(url).host.lowercase()
        val body = when (host) {
            "github.blog" -> document.selectFirst("section.post__content")
            "openai.com" -> document.selectFirst("article [data-toc-content]")
                ?: document.selectFirst("main article") ?: document.selectFirst("article")
            "blog.google" -> document.selectFirst("article .article-container__content")
                ?: document.selectFirst("main article") ?: document.selectFirst("article")
            else -> null
        } ?: throw FullTextException("BODY_NOT_FOUND")
        if (host == "github.blog") {
            body.select("h2,h3").toList().filter { it.text().trim() in setOf("Written by", "Tags:") }
                .forEach { it.parent()?.takeIf { parent -> parent != body }?.remove() }
        }
        val paragraphs = body.select("p").count { it.text().trim().length >= 20 }
        val text = readableText(body)
        if (paragraphs < 2 || text.length < 200 || text.count { it.isLetterOrDigit() } < 100) {
            throw FullTextException("BODY_TOO_SHORT")
        }
        return text
    }

    private fun readableText(body: Element): String {
        val text = StringBuilder()
        var literalDepth = 0
        val blocks = setOf("p", "h1", "h2", "h3", "h4", "h5", "h6", "pre", "blockquote", "li", "tr")
        NodeTraversor.traverse(object : NodeVisitor {
            override fun head(node: Node, depth: Int) {
                if (node is Element) {
                    if (node.normalName() in blocks && text.isNotEmpty() && !text.endsWith("\n")) text.append('\n')
                    if (node.normalName() in setOf("pre", "code")) literalDepth++
                    if (node.normalName() == "br") text.append('\n')
                } else if (node is TextNode) {
                    // HTML을 다시 정규식으로 지우면 코드의 비교 연산자·리터럴까지 사라진다.
                    text.append(if (literalDepth > 0) node.wholeText else node.wholeText.replace(Regex("\\s+"), " "))
                }
            }

            override fun tail(node: Node, depth: Int) {
                if (node is Element) {
                    if (node.normalName() in setOf("pre", "code")) literalDepth--
                    if (node.normalName() in blocks) text.append("\n\n")
                }
            }
        }, body)
        return text.toString().trim()
    }
}
