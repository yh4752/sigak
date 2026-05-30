package com.sigak.search.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "sigak.search")
data class SearchInfrastructureProperties(
    val elasticsearch: Elasticsearch = Elasticsearch(),
    val qdrant: Qdrant = Qdrant(),
    val neo4j: Neo4j = Neo4j()
) {
    data class Elasticsearch(
        val url: String = "http://localhost:9200",
        val articleIndexName: String = "sigak-articles-v1"
    )

    data class Qdrant(
        val url: String = "http://localhost:6333",
        val articleCollectionName: String = "sigak-article-vectors-minilm-v1",
        val distance: String = "Cosine",
        val defaultLimit: Int = 10,
        val maxLimit: Int = 50
    )

    data class Neo4j(
        val uri: String = "bolt://localhost:7687",
        val username: String = "neo4j",
        val password: String = "sigak-neo4j-password"
    )
}
