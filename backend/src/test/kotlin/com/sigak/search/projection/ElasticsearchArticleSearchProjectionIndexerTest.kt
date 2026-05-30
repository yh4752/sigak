package com.sigak.search.projection

import com.sigak.search.config.SearchInfrastructureProperties
import kotlin.test.Test
import org.hamcrest.Matchers.containsString
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class ElasticsearchArticleSearchProjectionIndexerTest {

    private val elasticsearchClientBuilder = RestClient.builder().baseUrl("http://elasticsearch:9200")
    private val elasticsearchServer = MockRestServiceServer.bindTo(elasticsearchClientBuilder).build()
    private val elasticsearchClient = elasticsearchClientBuilder.build()
    private val properties = SearchInfrastructureProperties(
        elasticsearch = SearchInfrastructureProperties.Elasticsearch(
            url = "http://elasticsearch:9200",
            articleIndexName = "sigak-articles-v1"
        )
    )
    private val indexer = ElasticsearchArticleSearchProjectionIndexer(
        elasticsearchClient = elasticsearchClient,
        properties = properties
    )

    @Test
    fun createsIndexAndIndexesDocumentsWhenIndexDoesNotExist() {
        elasticsearchServer.expect(ExpectedCount.once(), requestTo("http://elasticsearch:9200/sigak-articles-v1"))
            .andExpect(method(HttpMethod.HEAD))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))
        elasticsearchServer.expect(ExpectedCount.once(), requestTo("http://elasticsearch:9200/sigak-articles-v1"))
            .andExpect(method(HttpMethod.PUT))
            .andExpect(content().string(containsString("title")))
            .andRespond(withSuccess("""{"acknowledged":true}""", MediaType.APPLICATION_JSON))
        elasticsearchServer.expect(
            ExpectedCount.once(),
            requestTo("http://elasticsearch:9200/sigak-articles-v1/_doc/3?refresh=true")
        )
            .andExpect(method(HttpMethod.PUT))
            .andExpect(content().string(containsString("Critical Package Registry Attack Targets AI Toolchains")))
            .andRespond(withSuccess("""{"result":"created"}""", MediaType.APPLICATION_JSON))

        indexer.replaceAll(
            listOf(
                ArticleSearchProjectionDocument(
                    id = 3,
                    title = "Critical Package Registry Attack Targets AI Toolchains",
                    source = "Security Advisory Board",
                    url = "https://example.com/articles/ai-toolchain-package-attack",
                    publishedAt = "2026-05-03T15:45:00Z",
                    eventType = "SECURITY",
                    primaryCategory = "SECURITY",
                    topics = listOf("supply chain security", "AI tooling"),
                    summary = "A coordinated package registry attack targeted developer environments.",
                    whyItMatters = "AI development stacks combine packages, credentials, and automation.",
                    importanceScore = 93,
                    relatedArticleIds = listOf(1, 5)
                )
            )
        )

        elasticsearchServer.verify()
    }
}
