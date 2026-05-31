package com.sigak.search.config

import com.sigak.search.hybrid.ArticleSearchMode
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "sigak.search")
data class SearchInfrastructureProperties(
    val mode: ArticleSearchMode = ArticleSearchMode.HYBRID,
    val hybrid: Hybrid = Hybrid(),
    val elasticsearch: Elasticsearch = Elasticsearch(),
    val qdrant: Qdrant = Qdrant(),
    val neo4j: Neo4j = Neo4j()
) {
    data class Hybrid(
        val rrfK: Int = 60,
        val keywordWeight: Double = 1.0,
        val vectorWeight: Double = 1.0,
        val keywordCandidateLimit: Int = 20,
        val vectorCandidateLimit: Int = 20,
        val resultLimit: Int = 20
    )

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
