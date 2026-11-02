package com.sigak.article.fulltext

import com.sigak.common.http.ExternalDocument
import com.sigak.common.http.ExternalFetchException
import com.sigak.common.http.PublicDocumentClient
import com.sigak.source.service.SourceStore
import com.sigak.support.passThroughSourceNetworkGate
import com.sigak.support.sourceDefinitionFixture
import kotlin.test.*
import org.mockito.Mockito.*

class ArticleBodyFetcherTest {
    private val source=sourceDefinitionFixture()
    private val store=mock(SourceStore::class.java)
    private val html="<article><h1>Engineering</h1><p>"+"Opening evidence and architecture tradeoffs. ".repeat(10)+"</p><p>Closing evidence explains the final result.</p></article>"
    private val url="https://engineering.example.test/article"
    private val calls=mutableListOf<String>()
    private val client=PublicDocumentClient { target,hosts ->
        assertEquals(source.allowedHosts,hosts)
        calls.add(target)
        ExternalDocument(html,target,"text/html",2)
    }
    private fun fetcher(client:PublicDocumentClient=this.client)=HttpArticleBodyFetcher(ArticleBodyExtractor(),client,store,passThroughSourceNetworkGate())

    @Test fun `article source FK selects a configured new host and full body`() {
        `when`(store.forArticle(42)).thenReturn(source)
        val result=fetcher().fetch(42,url)
        assertTrue(result.text.contains("Closing evidence"))
        assertEquals(url,result.finalUrl)
        assertEquals(listOf(url),calls)
        verify(store,atLeastOnce()).forArticle(42)
    }
    @Test fun `unapproved article or blocked source never calls transport`() {
        assertFailsWith<FullTextException> { fetcher().fetch(42,url) }
        `when`(store.forArticle(42)).thenReturn(source.copy(validationStatus="BLOCKED",enabled=false))
        assertFailsWith<FullTextException> { fetcher().fetch(42,url) }
        assertTrue(calls.isEmpty())
    }
    @Test fun `another article host cannot borrow source approval`() {
        `when`(store.forArticle(42)).thenReturn(source)
        assertEquals("HOST_NOT_ALLOWED",assertFailsWith<FullTextException> { fetcher().fetch(42,"https://other.example.test/article") }.code)
        assertTrue(calls.isEmpty())
    }
    @Test fun `transport failures expose only stable safe codes`() {
        `when`(store.forArticle(42)).thenReturn(source)
        val failing=PublicDocumentClient { _,_ -> throw ExternalFetchException("HTTP_403") }
        assertEquals("HTTP_403",assertFailsWith<FullTextException> { fetcher(failing).fetch(42,url) }.code)
    }
    @Test fun `XML is not interpreted as article HTML`() {
        `when`(store.forArticle(42)).thenReturn(source)
        val xml=PublicDocumentClient { target,_ -> ExternalDocument("<feed/>",target,"application/atom+xml",1) }
        assertEquals("BODY_NOT_HTML",assertFailsWith<FullTextException> { fetcher(xml).fetch(42,url) }.code)
    }
    @Test fun `a changed configuration is rejected after acquiring source permit`() {
        `when`(store.forArticle(42)).thenReturn(source,source.copy(version=2,validatedVersion=2))
        assertEquals("SOURCE_VERSION_CHANGED",assertFailsWith<FullTextException> { fetcher().fetch(42,url) }.code)
        assertTrue(calls.isEmpty())
    }
}
