package com.sigak.collection.service

import com.sigak.collection.collector.ArxivCollector
import com.sigak.collection.collector.RssAtomCollector
import com.sigak.collection.domain.NewsSource
import com.sigak.collection.domain.SourceType
import com.sigak.collection.dto.EnrichmentResponse
import kotlin.test.Test
import kotlin.test.assertEquals

class SourceCollectionServiceTest {

    private var fetchedUrl = ""
    private val publishedTitles = mutableListOf<String>()
    private val service = SourceCollectionService(
        sourceContentFetcher = { url ->
            fetchedUrl = url
            rssXml()
        },
        rssAtomCollector = RssAtomCollector(),
        arxivCollector = ArxivCollector(),
        collectionPipelineService = CollectionPipelineService(
            articleNormalizer = ArticleNormalizer(),
            enrichmentClient = { request ->
                EnrichmentResponse(
                    summary = "${request.title} summary",
                    whyItMatters = "${request.source} insight",
                    suggestedTopics = request.topics,
                    suggestedPrimaryCategory = request.topics.first(),
                    suggestedImportanceScore = 70
                )
            },
            collectedArticlePublisher = { article, _ ->
                publishedTitles.add(article.title)
                CollectedArticlePublishResult(
                    articleId = 101L,
                    outcome = CollectedArticlePublishOutcome.PUBLISHED
                )
            }
        )
    )

    @Test
    fun collectFetchesRssSourceParsesArticlesAndPublishesThem() {
        val source = NewsSource(
            id = "example-feed",
            name = "Example Engineering Blog",
            type = SourceType.RSS_ATOM,
            url = "https://example.com/feed.xml",
            categoryHint = "SOFTWARE_ENGINEERING"
        )

        val result = service.collect(source)

        assertEquals("https://example.com/feed.xml", fetchedUrl)
        assertEquals(1, result.discoveredCount)
        assertEquals(listOf(101L), result.publishedArticleIds)
        assertEquals(0, result.failedCount)
        assertEquals(listOf("Reliable Builds for AI Toolchains"), publishedTitles)
    }

    private fun rssXml(): String =
        """
        <rss version="2.0">
          <channel>
            <item>
              <guid>https://example.com/articles/reliable-builds</guid>
              <title>Reliable Builds for AI Toolchains</title>
              <link>https://example.com/articles/reliable-builds</link>
              <pubDate>Tue, 05 May 2026 09:00:00 GMT</pubDate>
              <description>Build systems need stronger provenance as AI coding tools grow.</description>
            </item>
          </channel>
        </rss>
        """.trimIndent()
}
