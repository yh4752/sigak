package com.sigak.collection.collector

import com.sigak.collection.domain.CollectedArticle
import com.sigak.collection.domain.NewsSource
import java.io.ByteArrayInputStream
import org.springframework.stereotype.Component
import org.w3c.dom.Element

@Component
class ArxivCollector {

    fun parse(source: NewsSource, xml: String): List<CollectedArticle> {
        val document = secureDocumentBuilderFactory(namespaceAware = true)
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(xml.toByteArray()))

        val entries = document.getElementsByTagNameNS("http://www.w3.org/2005/Atom", "entry")

        return (0 until entries.length)
            .mapNotNull { entries.item(it).asElement() }
            .mapNotNull { entry -> entry.toCollectedArticle(source) }
    }

    private fun Element.toCollectedArticle(source: NewsSource): CollectedArticle? {
        val title = firstText("title")?.replace(Regex("\\s+"), " ") ?: return null
        val id = firstText("id") ?: return null
        val publishedAt = firstText("published") ?: firstText("updated") ?: ""
        val summary = firstText("summary")?.replace(Regex("\\s+"), " ") ?: ""
        val link = atomLink() ?: id

        return CollectedArticle(
            sourceName = source.name,
            sourceType = source.type,
            externalId = id,
            url = link,
            canonicalUrl = link,
            title = title,
            publishedAt = publishedAt,
            authorNames = authorNames(),
            rawContent = summary,
            extractedText = summary,
            categoryHint = source.categoryHint
        )
    }

    private fun Element.authorNames(): List<String> {
        val authors = getElementsByTagName("author")
        return (0 until authors.length)
            .mapNotNull { authors.item(it).asElement() }
            .mapNotNull { author -> author.firstText("name") }
    }

    private fun Element.atomLink(): String? {
        val links = getElementsByTagName("link")
        return (0 until links.length)
            .mapNotNull { links.item(it).asElement() }
            .firstOrNull { it.getAttribute("rel").ifBlank { "alternate" } == "alternate" }
            ?.getAttribute("href")
            ?.takeIf { it.isNotBlank() }
    }
}
