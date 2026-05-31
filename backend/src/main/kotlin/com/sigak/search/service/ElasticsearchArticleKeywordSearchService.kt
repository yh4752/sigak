package com.sigak.search.service

import com.fasterxml.jackson.databind.JsonNode
import com.sigak.search.config.SearchInfrastructureProperties
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

@Service
class ElasticsearchArticleKeywordSearchService(
    @Qualifier("elasticsearchRestClient")
    private val elasticsearchClient: RestClient,
    private val properties: SearchInfrastructureProperties
) : ArticleKeywordSearchService {

    override fun searchArticleIds(query: String): List<Long> =
        searchArticleIds(query = query, limit = 20)

    override fun searchArticleIds(query: String, limit: Int): List<Long> {
        val response = elasticsearchClient.post()
            .uri("/{indexName}/_search", properties.elasticsearch.articleIndexName)
            .body(searchRequest(query, limit))
            .retrieve()
            .body(JsonNode::class.java) ?: return emptyList()

        val hits = response.path("hits").path("hits")
        if (!hits.isArray) {
            return emptyList()
        }

        return hits.mapNotNull { hit ->
            hit.path("_id")
                .takeIf { idNode -> idNode.isTextual }
                ?.asText()
                ?.toLongOrNull()
        }
    }

    private fun searchRequest(query: String, limit: Int): Map<String, Any> =
        mapOf(
            "size" to limit,
            "_source" to false,
            "query" to mapOf(
                "multi_match" to mapOf(
                    "query" to query,
                    "fields" to listOf(
                        "title^3",
                        "summary^2",
                        "topics^2",
                        "primaryCategory",
                        "whyItMatters"
                    ),
                    "type" to "best_fields"
                )
            )
        )
}
