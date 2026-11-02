package com.sigak.article.fulltext

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor
import org.springframework.stereotype.Component

@Component
class ArticleBodyExtractor {
    fun extractConfigured(url: String, html: String, selector: String?, exclusions: List<String>): String {
        com.sigak.common.http.PublicUrlPolicy.requireUrl(url)
        val document = Jsoup.parse(html, url)
        clean(document)
        val body = try {
            if (!selector.isNullOrBlank()) {
                val candidates = document.select(selector)
                if (candidates.size != 1) throw FullTextException(if (candidates.isEmpty()) "BODY_NOT_FOUND" else "BODY_AMBIGUOUS")
                candidates.single()
            } else {
                var selected: Element? = null
                for (root in listOf("main article", "article", "main")) {
                    val candidates = document.select(root)
                    if (candidates.size > 1) throw FullTextException("BODY_AMBIGUOUS")
                    if (candidates.size == 1) {
                        selected = candidates.single()
                        if (root == "main" && selected.select("h1,h2,h3").isEmpty()) throw FullTextException("BODY_NOT_FOUND")
                        break
                    }
                }
                selected ?: throw FullTextException("BODY_NOT_FOUND")
            }
        } catch (exception: org.jsoup.select.Selector.SelectorParseException) {
            throw FullTextException("BODY_SELECTOR_INVALID")
        }
        removeExcluded(body,exclusions)
        return validatedText(body)
    }

    fun extractFeed(html: String, exclusions: List<String> = emptyList()): String {
        if (html.toByteArray(Charsets.UTF_8).size > 2 * 1024 * 1024) throw FullTextException("BODY_TOO_LARGE")
        val document = Jsoup.parseBodyFragment(html)
        clean(document)
        removeExcluded(document.body(),exclusions)
        return validatedText(document.body())
    }

    private fun removeExcluded(body: Element, exclusions: List<String>) {
        try {
            exclusions.forEach { expression -> body.select(expression).toList().filter { it !== body }.forEach { it.remove() } }
        } catch (exception: org.jsoup.select.Selector.SelectorParseException) {
            throw FullTextException("BODY_SELECTOR_INVALID")
        }
    }

    private fun clean(element: Element) {
        element.select("nav,aside,header,footer,script,style,noscript,form,.author-bio,.post-card,.newsletter,.toc,uni-related-content-tout,.uni-blog-article-tags__wrapper,[class*=newsletter],[class*=table-of-contents]").remove()
    }

    private fun validatedText(body: Element): String {
        val text = readableText(body)
        if (body.select("p").count { it.text().trim().length >= 20 } < 2 || text.length < 200 || text.count { it.isLetterOrDigit() } < 100) {
            throw FullTextException("BODY_TOO_SHORT")
        }
        val tail = body.select("p,a").takeLast(4).joinToString(" ") { it.text() }
        if (Regex("(?i)(continue reading on|read the full (article|post)|원문에서\\s*계속\\s*읽기)").containsMatchIn(tail)) {
            throw FullTextException("BODY_EXCERPT")
        }
        val linkedCharacters = body.select("a").sumOf { it.text().length }
        if (linkedCharacters.toDouble() / body.text().length.coerceAtLeast(1) > 0.2) throw FullTextException("BODY_LINK_LIST")
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
