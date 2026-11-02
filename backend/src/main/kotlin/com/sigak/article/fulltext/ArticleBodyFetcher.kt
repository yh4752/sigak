package com.sigak.article.fulltext

import com.sigak.common.http.ExternalFetchException
import com.sigak.common.http.PublicDocumentClient
import com.sigak.common.http.PublicUrlPolicy
import com.sigak.source.service.SourceNetworkGate
import com.sigak.source.service.SourceStore
import org.springframework.stereotype.Component


data class FullTextResult(val text: String, val finalUrl: String, val durationMs: Long)
fun interface ArticleBodyFetcher { fun fetch(articleId: Long, url: String): FullTextResult }
class FullTextException(val code: String) : RuntimeException(code)

@Component
class HttpArticleBodyFetcher(private val extractor: ArticleBodyExtractor, private val client: PublicDocumentClient,
    private val sources: SourceStore, private val network: SourceNetworkGate) : ArticleBodyFetcher {
    override fun fetch(articleId: Long, url: String): FullTextResult {
        val initial = sources.forArticle(articleId) ?: throw FullTextException("SOURCE_NOT_FOUND")
        if (!initial.bodyApproved()) throw FullTextException("SOURCE_NOT_APPROVED")
        try {
            PublicUrlPolicy.requireUrl(url,initial.allowedHosts)
            return network.withPermit(initial.key) {
                val source = sources.forArticle(articleId) ?: throw FullTextException("SOURCE_NOT_FOUND")
                if (source.version != initial.version || !source.bodyApproved()) throw FullTextException("SOURCE_VERSION_CHANGED")
                val document = client.fetch(url,source.allowedHosts)
                val mime = document.contentType.substringBefore(';').trim().lowercase()
                if (mime !in setOf("text/html","application/xhtml+xml")) throw FullTextException("BODY_NOT_HTML")
                FullTextResult(extractor.extractConfigured(document.finalUrl,document.text,source.bodySelector,source.excludeSelectors),document.finalUrl,document.durationMs)
            }
        } catch (error: ExternalFetchException) { throw FullTextException(error.code) }
    }
}
