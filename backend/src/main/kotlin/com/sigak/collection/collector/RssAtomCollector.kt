package com.sigak.collection.collector

import com.sigak.collection.domain.CollectedArticle
import com.sigak.collection.domain.NewsSource
import java.io.ByteArrayInputStream
import org.springframework.stereotype.Component
import org.w3c.dom.Element

@Component
class RssAtomCollector {

    fun parse(source: NewsSource, xml: String): List<CollectedArticle> {
        val document = secureDocumentBuilderFactory()
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(xml.toByteArray()))

        val rssItems = document.getElementsByTagName("item")
        if (rssItems.length > 0) {
            return (0 until rssItems.length)
                .mapNotNull { rssItems.item(it).asElement() }
                .mapNotNull { item -> item.toRssArticle(source) }
        }

        val atomEntries = document.getElementsByTagName("entry")
        return (0 until atomEntries.length)
            .mapNotNull { atomEntries.item(it).asElement() }
            .mapNotNull { entry -> entry.toAtomArticle(source) }
    }

    private fun Element.toRssArticle(source: NewsSource): CollectedArticle? {
        val title = firstText("title") ?: return null
        val link = firstText("link") ?: return null
        val externalId = firstText("guid") ?: link
        val publishedAt = firstText("pubDate") ?: ""
        val feedContent = firstTextOf("content:encoded", "description") ?: ""
        val extractedText = feedContent.toPlainText().ifBlank { title }

        return CollectedArticle(
            sourceName = source.name,
            sourceType = source.type,
            externalId = externalId,
            url = link,
            canonicalUrl = link,
            title = title,
            publishedAt = publishedAt,
            authorNames = emptyList(),
            rawContent = feedContent,
            extractedText = extractedText,
            categoryHint = source.categoryHint
        )
    }

    private fun Element.toAtomArticle(source: NewsSource): CollectedArticle? {
        val title = firstText("title") ?: return null
        val id = firstText("id") ?: return null
        val publishedAt = firstText("published") ?: firstText("updated") ?: ""
        val summary = firstText("summary") ?: ""
        val link = atomLink() ?: id

        return CollectedArticle(
            sourceName = source.name,
            sourceType = source.type,
            externalId = id,
            url = link,
            canonicalUrl = link,
            title = title,
            publishedAt = publishedAt,
            authorNames = emptyList(),
            rawContent = summary,
            extractedText = summary,
            categoryHint = source.categoryHint
        )
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
