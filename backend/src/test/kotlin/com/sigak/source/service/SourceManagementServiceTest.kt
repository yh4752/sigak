package com.sigak.source.service

import com.sigak.SigakBackendApplication
import com.sigak.article.fulltext.ArticleBodyExtractor
import com.sigak.common.http.ExternalDocument
import com.sigak.common.http.ExternalFetchException
import com.sigak.common.http.PublicDocumentClient
import com.sigak.support.PostgresIntegrationTest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

@SpringBootTest(classes=[SigakBackendApplication::class])
class SourceManagementServiceTest : PostgresIntegrationTest() {
    @Autowired lateinit var store:SourceStore
    @Autowired lateinit var jdbc:JdbcTemplate
    private val docs=mutableMapOf<String,ExternalDocument>()
    private val calls=mutableListOf<String>()
    private val clock=Clock.fixed(Instant.parse("2026-11-02T00:00:00Z"),ZoneOffset.UTC)
    private val keys=mutableListOf<String>()
    private val gate=object:SourceNetworkGate { override fun <T> withPermit(key:String, action:()->T):T = action() }
    private val client=PublicDocumentClient { url, _ -> calls.add(url); docs[url] ?: throw ExternalFetchException("HTTP_403") }
    private fun service()=SourceManagementService(store,SourceProbe(client,ArticleBodyExtractor()),gate,clock)

    @AfterEach fun cleanup() { keys.forEach { jdbc.update("delete from news_sources where source_key=?",it) } }

    private fun fixture(count:Int=3):SourceDefinition {
        val base="https://probe-${UUID.randomUUID()}.example.test"
        val source=service().register("$base/").also { keys.add(it.key) }
        docs["$base/"]=ExternalDocument("<link rel='alternate' type='application/rss+xml' href='/feed'>","$base/","text/html",1)
        val body="<h1>Engineering choices</h1><p>"+"Opening evidence and implementation tradeoffs. ".repeat(10)+"</p><pre><code>x &lt; y</code></pre><p>Closing evidence explains the final result.</p>"
        val items=(1..count).joinToString("") { i ->
            docs["$base/post/$i"]=ExternalDocument("<article>$body</article>","$base/post/$i","text/html",1)
            "<item><guid>$i</guid><title>Engineering choices</title><link>$base/post/$i</link><pubDate>Tue, 05 May 2026 09:00:00 GMT</pubDate><description>Short excerpt</description><c:encoded><![CDATA[$body]]></c:encoded></item>"
        }
        docs["$base/feed"]=ExternalDocument("<rss xmlns:c='http://purl.org/rss/1.0/modules/content/'><channel><title>한글 기술 블로그</title>$items</channel></rss>","$base/feed","application/rss+xml",1)
        return source
    }

    @Test fun `URL registration discovers feed and activation requires reviewed current samples`() {
        val source=fixture()
        val checked=service().check(source.key,source.version)
        assertEquals("PASSED",checked.source.validationStatus)
        assertFalse(checked.source.enabled)
        assertEquals(3,checked.samples.size)
        assertEquals("한글 기술 블로그",checked.source.name)
        assertFailsWith<SourceOperationException> { service().enable(source.key,checked.source.version,false,false) }
        val enabled=service().enable(source.key,checked.source.version,true,true)
        assertTrue(enabled.collectable())
        assertTrue(enabled.feedContentVerified)
        assertEquals(5,calls.size)
    }
    @Test fun `duplicate registration is stable and changing display name keeps identity and validity`() {
        val source=fixture()
        assertEquals(source.key,service().register(source.siteUrl!!).key)
        val checked=service().check(source.key,source.version).source
        val renamed=service().configure(checked.key,checked.version,SourcePatch(name="새 이름"))
        assertEquals(source.id,renamed.id)
        assertEquals("PASSED",renamed.validationStatus)
        assertEquals(renamed.version,renamed.validatedVersion)
    }
    @Test fun `multiple feeds and insufficient samples are explicit failures`() {
        val source=fixture(2)
        assertEquals("INSUFFICIENT_SAMPLES",service().check(source.key,source.version).source.report["reason"])
        val second=fixture()
        docs[second.siteUrl!!]=ExternalDocument("<link rel='alternate' type='application/rss+xml' href='/feed'><link rel='alternate' type='application/atom+xml' href='/other'>",second.siteUrl!!,"text/html",0)
        val checked=service().check(second.key,second.version).source
        assertEquals("MULTIPLE_FEEDS",checked.report["reason"])
        assertEquals("NEEDS_CONFIG",checked.validationStatus)
    }
    @Test fun `configuration invalidates activation and stale versions cannot enable`() {
        val source=fixture()
        val checked=service().check(source.key,source.version).source
        val enabled=service().enable(source.key,checked.version,true,false)
        val changed=service().configure(source.key,enabled.version,SourcePatch(bodySelector=".article"))
        assertFalse(changed.enabled)
        assertEquals("UNVERIFIED",changed.validationStatus)
        assertFailsWith<SourceOperationException> { service().enable(source.key,enabled.version,true,false) }
    }
    @Test fun `blocked input never activates and does not publish articles or jobs`() {
        val source=fixture()
        docs.clear()
        val before=jdbc.queryForObject("select count(*) from article_summary_jobs",Int::class.java)
        val checked=service().check(source.key,source.version).source
        assertEquals("BLOCKED",checked.validationStatus)
        assertFalse(checked.enabled)
        assertEquals(before,jdbc.queryForObject("select count(*) from article_summary_jobs",Int::class.java))
    }
    @Test fun `unapproved feed host is reported without connecting there`() {
        val source=fixture()
        docs[source.siteUrl!!]=ExternalDocument("<link rel='alternate' type='application/rss+xml' href='https://other.example.test/feed'>",source.siteUrl!!,"text/html",0)
        val checked=service().check(source.key,source.version).source
        assertEquals("HOST_NOT_ALLOWED",checked.report["reason"])
        assertFalse(calls.any { it.contains("other.example.test") })
    }
    @Test fun `an in flight validation cannot overwrite a later block`() {
        val source=fixture()
        val checked=service().check(source.key,source.version).source
        val enabled=service().enable(source.key,checked.version,true,false)
        store.block(enabled.key,enabled.version,"HTTP_429")
        assertFailsWith<SourceOperationException> { store.save(enabled,enabled.version) }
        assertEquals("BLOCKED",store.require(source.key).validationStatus)
    }

    @Test fun `another landing URL resolves to the same existing feed without overwriting it`() {
        val original=fixture()
        val checked=service().check(original.key,original.version).source
        val enabled=service().enable(original.key,checked.version,true,false)
        val aliasUrl=original.siteUrl!!+"another-path"
        docs[aliasUrl]=docs[original.siteUrl]!!.copy(finalUrl=aliasUrl)
        val alias=service().register(aliasUrl).also { keys.add(it.key) }
        val result=service().check(alias.key,alias.version)
        assertEquals(enabled.id,result.source.id)
        assertEquals(enabled,store.require(enabled.key))
    }
    @Test fun `XHTML landing pages discover their alternate feeds`() {
        val source=fixture()
        docs[source.siteUrl!!]=docs[source.siteUrl]!!.copy(contentType="application/xhtml+xml")
        assertEquals("PASSED",service().check(source.key,source.version).source.validationStatus)
    }
    @Test fun `expired validations cannot activate even with a review acknowledgement`() {
        val source=fixture()
        val checked=service().check(source.key,source.version).source
        val later=SourceManagementService(store,SourceProbe(client,ArticleBodyExtractor()),gate,
            Clock.fixed(clock.instant().plusSeconds(25*3600),ZoneOffset.UTC))
        assertEquals("SOURCE_VALIDATION_EXPIRED",assertFailsWith<SourceOperationException> { later.enable(checked.key,checked.version,true,false) }.code)
    }

    @Test fun `AUTO feed activation validates the feed input limit before switching routes`() {
        val source=fixture()
        val feedUrl=source.siteUrl!!+"feed"
        val oversized="<p>"+"Large full evidence. ".repeat(5000)+"</p><p>Closing evidence explains the final result.</p>"
        val original=docs[feedUrl]!!
        docs[feedUrl]=original.copy(text=Regex("<c:encoded><!\\[CDATA\\[.*?]]></c:encoded>",RegexOption.DOT_MATCHES_ALL).replace(original.text,"<c:encoded><![CDATA[$oversized]]></c:encoded>"))
        val checked=service().check(source.key,source.version).source
        assertEquals("PASSED",checked.validationStatus)
        assertEquals("FEED_NO_SUMMARIZABLE_SAMPLE",assertFailsWith<SourceOperationException> { service().enable(checked.key,checked.version,true,true) }.code)
    }
    @Test fun `changed feed URL invalidates old AUTO feed approval before checking PAGE route`() {
        val source=fixture()
        val checked=service().check(source.key,source.version).source
        val enabled=service().enable(source.key,checked.version,true,true)
        val oldFeed=enabled.feedUrl!!
        docs[oldFeed]=docs[oldFeed]!!.copy(finalUrl=source.siteUrl!!+"new-feed")
        docs.keys.filter { it.contains("/post/") }.forEach { url -> docs[url]=ExternalDocument("<main><p>Short</p></main>",url,"text/html",0) }
        val result=service().check(enabled.key,enabled.version).source
        assertEquals("NEEDS_CONFIG",result.validationStatus)
        assertFalse(result.feedContentVerified)
    }

    @Test fun `a cross host article reports its actual host without connecting there`() {
        val source=fixture()
        val feedUrl=source.siteUrl!!+"feed"
        val original=docs[feedUrl]!!
        docs[feedUrl]=original.copy(text=original.text.replace(source.siteUrl+"post/1","https://extra.example.test/post/1"))
        val checked=service().check(source.key,source.version).source
        assertEquals(listOf("extra.example.test"),checked.report["requiredHosts"])
        assertEquals("https://extra.example.test/post/1",checked.report["rejectedUrl"])
        assertFalse(calls.any { it.contains("extra.example.test") })
    }

}
