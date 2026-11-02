package com.sigak.source.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sigak.article.fulltext.ArticleBodyExtractor
import com.sigak.article.fulltext.FullTextException
import com.sigak.article.summary.SummaryInput
import com.sigak.collection.collector.RssAtomCollector
import com.sigak.collection.collector.secureDocumentBuilderFactory
import com.sigak.collection.service.PublishedAtParser
import com.sigak.common.http.ExternalFetchException
import com.sigak.common.http.PublicDocumentClient
import com.sigak.common.http.PublicUrlPolicy
import java.io.StringReader
import org.xml.sax.InputSource
import java.net.URI
import java.time.Instant
import org.jsoup.Jsoup
import org.springframework.stereotype.Component
import org.w3c.dom.Element


data class SourceSample(
    val title: String, val url: String, val text: String?, val feedText: String?, val origin: String,
    val summaryEligible: Boolean, val error: String?, val pageError: String?, val feedError: String?,
    val feedMatchesPage: Boolean, val feedSummaryEligible: Boolean = false
) {
    fun metadata(): Map<String,Any?> = mapOf("title" to title,"url" to url,"characters" to text?.length,
        "feedCharacters" to feedText?.length,"origin" to origin,"summaryEligible" to summaryEligible,
        "error" to error,"pageError" to pageError,"feedError" to feedError,"feedMatchesPage" to feedMatchesPage,"feedSummaryEligible" to feedSummaryEligible)
}

data class SourceProbeResult(
    val status: String, val reason: String, val feedUrl: String? = null, val name: String? = null,
    val samples: List<SourceSample> = emptyList(), val candidates: List<String> = emptyList(), val requiredHosts: Set<String> = emptySet(), val rejectedUrl: String? = null
) {
    fun report(): Map<String,Any?> = mapOf("reason" to reason,"samples" to samples.map { it.metadata() },
        "candidates" to candidates,"requiredHosts" to requiredHosts.sorted(),"rejectedUrl" to rejectedUrl,
        "feedSummarizableSampleCount" to samples.count { it.feedSummaryEligible },
        "feedSamplesValid" to (samples.size == 3 && samples.all { it.feedText != null && it.feedError == null }))
}

@Component
class SourceProbe(private val client: PublicDocumentClient, private val extractor: ArticleBodyExtractor,
    private val mapper: ObjectMapper = jacksonObjectMapper()) {
    fun check(source: SourceDefinition): SourceProbeResult {
        val started = System.nanoTime()
        var feedUrl: String? = source.feedUrl
        var lastCandidate: String? = source.feedUrl ?: source.siteUrl
        try {
            val initial = source.feedUrl ?: requireNotNull(source.siteUrl)
            val first = client.fetch(initial,source.allowedHosts)
            deadline(started)
            val isHtml = first.contentType.substringBefore(';').trim().lowercase() in setOf("text/html","application/xhtml+xml")
            val feed = if (isHtml) {
                val document = Jsoup.parse(first.text,first.finalUrl)
                val candidates = document.select("link[rel~=alternate]").toList().filter {
                    it.attr("type").lowercase() in setOf("application/rss+xml","application/atom+xml")
                }.mapNotNull { link -> runCatching { PublicUrlPolicy.canonical(URI(first.finalUrl).resolve(link.attr("href")).toString()) }.getOrNull() }.distinct()
                if (candidates.size != 1) return SourceProbeResult("NEEDS_CONFIG",if (candidates.isEmpty()) "FEED_NOT_FOUND" else "MULTIPLE_FEEDS",candidates=candidates)
                feedUrl = candidates.single()
                lastCandidate = feedUrl
                PublicUrlPolicy.requireUrl(feedUrl,source.allowedHosts)
                client.fetch(feedUrl,source.allowedHosts)
            } else first
            deadline(started)
            feedUrl = feed.finalUrl
            val document = secureDocumentBuilderFactory(true).newDocumentBuilder().parse(InputSource(StringReader(feed.text)))
            val root = document.documentElement
            if (root.localName !in setOf("rss","feed","RDF")) return SourceProbeResult("NEEDS_CONFIG","INVALID_FEED",feedUrl)
            val channel = if (root.localName == "rss") directChildren(root).firstOrNull { it.localName == "channel" } else root
            val name = channel?.let { directChildren(it).firstOrNull { child -> child.localName == "title" }?.textContent?.trim() }?.takeIf { it.isNotBlank() }?.take(255)
            val parsedSource = source.copy(feedUrl=feedUrl,feedContentVerified=source.feedContentVerified && source.feedUrl==feedUrl,name=if (source.name == source.siteUrl?.let { PublicUrlPolicy.host(it) }) name ?: source.name else source.name)
            val articles = RssAtomCollector().parse(parsedSource.asNewsSource(),feed.text)
                .filter { it.title.isNotBlank() && PublishedAtParser.parseOrEpoch(it.publishedAt) != Instant.EPOCH }
                .sortedByDescending { PublishedAtParser.parseOrEpoch(it.publishedAt) }.take(3)
            if (articles.size < 3) return SourceProbeResult("NEEDS_CONFIG","INSUFFICIENT_SAMPLES",feedUrl,name)
            val samples = articles.map { article ->
                deadline(started)
                lastCandidate = URI(feedUrl).resolve(article.url).toString()
                val url = PublicUrlPolicy.requireUrl(lastCandidate!!,source.allowedHosts).toString()
                var feedError: String? = null
                val feedText = article.feedBodyHtml?.let { html -> try { extractor.extractFeed(html,source.excludeSelectors) } catch (error: FullTextException) { feedError=error.code; null } }
                var pageError: String? = null
                val pageText = try {
                    val page = client.fetch(url,source.allowedHosts)
                    deadline(started)
                    if (!page.contentType.substringBefore(';').trim().lowercase().let { it == "text/html" || it == "application/xhtml+xml" }) throw FullTextException("BODY_NOT_HTML")
                    extractor.extractConfigured(page.finalUrl,page.text,source.bodySelector,source.excludeSelectors)
                } catch (error: ExternalFetchException) { pageError=error.code; null }
                  catch (error: FullTextException) { pageError=error.code; null }
                val useFeed = parsedSource.useFeed()
                val text = if (useFeed) feedText else pageText
                val error = if (useFeed) feedError ?: if(feedText==null) "BODY_FEED_MISSING" else null else pageError
                fun eligible(candidate: String?): Boolean = candidate?.let {
                    val input = SummaryInput(article.title,parsedSource.name,url,PublishedAtParser.normalizeToIsoString(article.publishedAt),listOf(source.categoryHint ?: "SOFTWARE_ENGINEERING"),it,"FULL_TEXT")
                    mapper.writeValueAsBytes(mapper.writeValueAsString(input)).size <= 56*1024
                } ?: false
                SourceSample(article.title,url,text,feedText,if(useFeed) "FEED" else "PAGE",eligible(text),error,pageError,feedError,anchorsMatch(feedText,pageText),eligible(feedText))
            }
            val blocked = samples.firstOrNull { it.pageError in BLOCK_CODES }
            if (blocked != null) return SourceProbeResult("BLOCKED",blocked.pageError!!,feedUrl,name,samples)
            if (samples.any { it.text == null || it.error != null }) return SourceProbeResult("NEEDS_CONFIG","BODY_VALIDATION_FAILED",feedUrl,name,samples)
            if (samples.none { it.summaryEligible }) return SourceProbeResult("NEEDS_CONFIG","NO_SUMMARIZABLE_SAMPLE",feedUrl,name,samples)
            deadline(started)
            return SourceProbeResult("PASSED","SAMPLES_VALIDATED",feedUrl,name,samples)
        } catch (error: ExternalFetchException) {
            val required = if (error.code == "HOST_NOT_ALLOWED") lastCandidate?.let { runCatching { setOf(PublicUrlPolicy.host(it)) }.getOrNull() }.orEmpty() else emptySet()
            return SourceProbeResult(if (error.code in BLOCK_CODES) "BLOCKED" else "NEEDS_CONFIG",error.code,feedUrl,requiredHosts=required,rejectedUrl=lastCandidate)
        } catch (_: Exception) {
            return SourceProbeResult("NEEDS_CONFIG","FEED_PARSE_FAILED",feedUrl)
        }
    }

    private fun deadline(started: Long) { if (System.nanoTime()-started > 90_000_000_000L) throw ExternalFetchException("PROBE_TIMEOUT") }
    private fun directChildren(element: Element): List<Element> = (0 until element.childNodes.length).mapNotNull { element.childNodes.item(it) as? Element }
    private fun anchorsMatch(feed: String?, page: String?): Boolean {
        if (feed == null || page == null) return false
        fun normalized(text: String) = text.replace(Regex("\\s+")," ").trim()
        val left = normalized(feed)
        val right = normalized(page)
        return listOf(right.take(100),right.substring((right.length/2-50).coerceAtLeast(0),(right.length/2+50).coerceAtMost(right.length)),right.takeLast(100)).all { left.contains(it) }
    }
    companion object {
        private val BLOCK_CODES = setOf("HTTP_401","HTTP_403","HTTP_429","PRIVATE_ADDRESS","INVALID_URL","REDIRECT_REJECTED")
    }
}
