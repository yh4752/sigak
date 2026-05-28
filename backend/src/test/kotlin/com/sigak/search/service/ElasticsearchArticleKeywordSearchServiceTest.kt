package com.sigak.search.service

import com.sigak.search.config.SearchInfrastructureProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import org.hamcrest.Matchers.containsString
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class ElasticsearchArticleKeywordSearchServiceTest {

    private val elasticsearchClientBuilder = RestClient.builder().baseUrl("http://elasticsearch:9200")
    private val elasticsearchServer = MockRestServiceServer.bindTo(elasticsearchClientBuilder).build()
    private val elasticsearchClient = elasticsearchClientBuilder.build()
    private val properties = SearchInfrastructureProperties(
        elasticsearch = SearchInfrastructureProperties.Elasticsearch(
            url = "http://elasticsearch:9200",
            articleIndexName = "sigak-articles-v1"
        )
    )
    private val keywordSearchService = ElasticsearchArticleKeywordSearchService(
        elasticsearchClient = elasticsearchClient,
        properties = properties
    )

    @Test
    fun searchArticleIdsReturnsHitIdsInElasticsearchOrder() {
        elasticsearchServer.expect(ExpectedCount.once(), requestTo("http://elasticsearch:9200/sigak-articles-v1/_search"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().string(containsString("multi_match")))
            .andExpect(content().string(containsString("graph rag")))
            .andRespond(
                withSuccess(
                    """
                    {
                      "hits": {
                        "hits": [
                          { "_id": "4" },
                          { "_id": "1" }
                        ]
                      }
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON
                )
            )

        val articleIds = keywordSearchService.searchArticleIds("graph rag")

        assertEquals(listOf(4L, 1L), articleIds)
        elasticsearchServer.verify()
    }

    @Test
    fun searchArticleIdsReturnsEmptyListWhenThereAreNoHits() {
        elasticsearchServer.expect(ExpectedCount.once(), requestTo("http://elasticsearch:9200/sigak-articles-v1/_search"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(
                withSuccess(
                    """
                    {
                      "hits": {
                        "hits": []
                      }
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON
                )
            )

        val articleIds = keywordSearchService.searchArticleIds("not-found")

        assertEquals(emptyList(), articleIds)
        elasticsearchServer.verify()
    }
}
