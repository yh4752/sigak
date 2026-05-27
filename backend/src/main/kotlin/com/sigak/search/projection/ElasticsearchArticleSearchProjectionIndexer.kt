package com.sigak.search.projection

import com.sigak.search.config.SearchInfrastructureProperties
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

@Component
class ElasticsearchArticleSearchProjectionIndexer(
    @Qualifier("elasticsearchRestClient")
    private val elasticsearchClient: RestClient,
    private val properties: SearchInfrastructureProperties
) : ArticleSearchProjectionIndexer {

    override fun indexName(): String =
        properties.elasticsearch.articleIndexName

    override fun replaceAll(documents: List<ArticleSearchProjectionDocument>) {
        ensureIndexExists()

        documents.forEach { document ->
            elasticsearchClient.put()
                .uri("/{indexName}/_doc/{id}?refresh=true", indexName(), document.id)
                .body(document)
                .retrieve()
                .toBodilessEntity()
        }
    }

    private fun ensureIndexExists() {
        try {
            elasticsearchClient.head()
                .uri("/{indexName}", indexName())
                .retrieve()
                .toBodilessEntity()
        } catch (exception: RestClientResponseException) {
            if (exception.statusCode != HttpStatus.NOT_FOUND) {
                throw exception
            }
            createIndex()
        }
    }

    private fun createIndex() {
        // MVP 단계에서는 검색 가능한 article 필드만 명시하고, analyzer tuning은 검색 품질 비교 단계에서 조정한다.
        elasticsearchClient.put()
            .uri("/{indexName}", indexName())
            .body(articleIndexMapping())
            .retrieve()
            .toBodilessEntity()
    }

    private fun articleIndexMapping(): Map<String, Any> =
        mapOf(
            "mappings" to mapOf(
                "properties" to mapOf(
                    "id" to mapOf("type" to "long"),
                    "title" to mapOf("type" to "text"),
                    "source" to mapOf("type" to "keyword"),
                    "url" to mapOf("type" to "keyword"),
                    "publishedAt" to mapOf("type" to "date"),
                    "eventType" to mapOf("type" to "keyword"),
                    "primaryCategory" to mapOf("type" to "keyword"),
                    "topics" to mapOf("type" to "keyword"),
                    "summary" to mapOf("type" to "text"),
                    "whyItMatters" to mapOf("type" to "text"),
                    "importanceScore" to mapOf("type" to "integer"),
                    "relatedArticleIds" to mapOf("type" to "long")
                )
            )
        )
}
