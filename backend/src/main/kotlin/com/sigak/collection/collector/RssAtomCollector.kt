package com.sigak.collection.collector

import com.sigak.collection.domain.CollectedArticle
import com.sigak.collection.domain.NewsSource
import java.io.StringReader
import java.net.URI
import org.xml.sax.InputSource
import java.io.StringWriter
import javax.xml.XMLConstants
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.jsoup.Jsoup
import org.jsoup.nodes.Entities
import org.springframework.stereotype.Component
import org.w3c.dom.Element
import org.w3c.dom.Node

@Component
class RssAtomCollector {
    fun parse(source: NewsSource, xml: String): List<CollectedArticle> {
        val document = secureDocumentBuilderFactory(namespaceAware = true).newDocumentBuilder()
            .parse(InputSource(StringReader(xml)))
        val rss = document.getElementsByTagNameNS("*", "item")
        if (rss.length > 0) return (0 until rss.length).mapNotNull { rss.item(it).asElement()?.toRss(source) }
        val atom = document.getElementsByTagNameNS(ATOM, "entry")
        return (0 until atom.length).mapNotNull { atom.item(it).asElement()?.toAtom(source) }
    }

    private fun Element.toRss(source: NewsSource): CollectedArticle? {
        val title = child("title")?.textContent?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val link = child("link")?.textContent?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val excerpt = child("description")?.textContent.orEmpty()
        val full = children().firstOrNull { it.localName == "encoded" && it.namespaceURI == CONTENT }?.textContent?.trim()?.takeIf { it.isNotBlank() }
        return article(source, title, resolveLink(source,link), child("guid")?.textContent?.trim().orEmpty().ifBlank { link },
            child("pubDate")?.textContent.orEmpty(), excerpt, full)
    }

    private fun Element.toAtom(source: NewsSource): CollectedArticle? {
        val title = child("title")?.textContent?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val id = child("id")?.textContent?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val link = children().firstOrNull { it.localName == "link" && it.namespaceURI == ATOM && it.getAttribute("rel").ifBlank { "alternate" } == "alternate" }
            ?.getAttribute("href")?.takeIf { it.isNotBlank() } ?: id
        val excerpt = child("summary")?.atomContent().orEmpty()
        val full = child("content")?.atomContent()?.takeIf { it.isNotBlank() }
        return article(source, title, resolveLink(source,link), id, child("published")?.textContent ?: child("updated")?.textContent.orEmpty(), excerpt, full)
    }

    private fun Element.resolveLink(source: NewsSource, link: String): String {
        val ancestors = generateSequence(this as Node?) { it.parentNode }.filterIsInstance<Element>().toList().asReversed()
        var base = URI(source.url)
        ancestors.forEach { element -> element.getAttributeNS(XMLConstants.XML_NS_URI,"base").takeIf { it.isNotBlank() }?.let { base = base.resolve(it) } }
        return base.resolve(link.trim()).toString()
    }

    private fun article(source: NewsSource, title: String, link: String, id: String, date: String,
        excerpt: String, full: String?): CollectedArticle {
        val raw = full ?: excerpt
        return CollectedArticle(source.name, source.type, id, link, link, title, date.trim(), emptyList(), raw,
            Jsoup.parseBodyFragment(raw).text().ifBlank { title }, source.categoryHint,
            sourceKey = source.id, sourceConfigVersion = source.configVersion,
            feedBodyHtml = full, feedExcerpt = Jsoup.parseBodyFragment(excerpt).text())
    }

    private fun Element.atomContent(): String? {
        if (hasAttribute("src")) return null
        return when (getAttribute("type").ifBlank { "text" }.lowercase()) {
            "html" -> textContent.trim()
            "text" -> textContent.trim().takeIf { it.isNotBlank() }?.let { "<p>" + Entities.escape(it).replace("\n\n", "</p><p>") + "</p>" }
            "xhtml" -> children().filter { it.namespaceURI == XHTML && it.localName == "div" }.singleOrNull()?.let { serializeXhtml(it) }
            else -> null
        }
    }

    private fun Element.child(name: String): Element? = children().firstOrNull { it.localName == name && it.namespaceURI == namespaceURI }
    private fun Element.children(): List<Element> = (0 until childNodes.length).mapNotNull { childNodes.item(it).asElement() }

    private fun serializeXhtml(element: Element): String {
        val document = secureDocumentBuilderFactory(true).newDocumentBuilder().newDocument()
        val copy = document.importNode(element, true)
        document.appendChild(copy)
        fun unprefix(node: Node) {
            if (node is Element && node.namespaceURI == XHTML) document.renameNode(node, null, node.localName)
            for (index in 0 until node.childNodes.length) unprefix(node.childNodes.item(index))
        }
        unprefix(copy)
        val factory = TransformerFactory.newInstance().apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "")
        }
        val writer = StringWriter()
        factory.newTransformer().apply { setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes") }
            .transform(DOMSource(document), StreamResult(writer))
        return writer.toString()
    }

    companion object {
        private const val ATOM = "http://www.w3.org/2005/Atom"
        private const val CONTENT = "http://purl.org/rss/1.0/modules/content/"
        private const val XHTML = "http://www.w3.org/1999/xhtml"
    }
}
