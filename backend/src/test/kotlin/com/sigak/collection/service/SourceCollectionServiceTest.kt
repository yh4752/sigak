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
        val result = service.collect(source(), maxArticlesPerSource = 10)

        assertEquals("https://example.com/feed.xml", fetchedUrl)
        assertEquals(1, result.discoveredCount)
        assertEquals(listOf(101L), result.publishedArticleIds)
        assertEquals(emptyList(), result.skippedArticleIds)
        assertEquals(0, result.failedCount)
        assertEquals(emptyList(), result.failureSummaries)
        assertEquals(listOf("Reliable Builds for AI Toolchains"), publishedTitles)
    }

    @Test
    fun collectSeparatesDuplicateSkippedArticlesFromPublishedArticles() {
        val service = SourceCollectionService(
            sourceContentFetcher = { rssXml() },
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
                collectedArticlePublisher = { _, _ ->
                    CollectedArticlePublishResult(
                        articleId = 77L,
                        outcome = CollectedArticlePublishOutcome.SKIPPED_DUPLICATE
                    )
                }
            )
        )

        val result = service.collect(source(), maxArticlesPerSource = 10)

        assertEquals(emptyList(), result.publishedArticleIds)
        assertEquals(listOf(77L), result.skippedArticleIds)
        assertEquals(0, result.failedCount)
    }

    @Test
    fun collectAppliesMaxArticlesPerSourceBeforePublishing() {
        val publishedTitles = mutableListOf<String>()
        val service = SourceCollectionService(
            sourceContentFetcher = { rssXmlWithTwoItems() },
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
                        articleId = publishedTitles.size.toLong(),
                        outcome = CollectedArticlePublishOutcome.PUBLISHED
                    )
                }
            )
        )

        val result = service.collect(source(), maxArticlesPerSource = 1)

        assertEquals(1, result.discoveredCount)
        assertEquals(listOf("Reliable Builds for AI Toolchains"), publishedTitles)
        assertEquals(listOf(1L), result.publishedArticleIds)
    }

    private fun source(): NewsSource =
        NewsSource(
            id = "example-feed",
            name = "Example Engineering Blog",
            type = SourceType.RSS_ATOM,
            url = "https://example.com/feed.xml",
            categoryHint = "SOFTWARE_ENGINEERING"
        )

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

    private fun rssXmlWithTwoItems(): String =
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
            <item>
              <guid>https://example.com/articles/second-builds</guid>
              <title>Second Build Reliability Note</title>
              <link>https://example.com/articles/second-builds</link>
              <pubDate>Tue, 05 May 2026 10:00:00 GMT</pubDate>
              <description>Teams compare build signals.</description>
            </item>
          </channel>
        </rss>
        """.trimIndent()
}
