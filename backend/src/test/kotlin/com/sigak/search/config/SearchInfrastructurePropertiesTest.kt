package com.sigak.search.config

import com.sigak.search.hybrid.ArticleSearchMode
import kotlin.test.Test
import kotlin.test.assertEquals
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration

class SearchInfrastructurePropertiesTest {

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(SearchInfrastructurePropertiesConfig::class.java)

    @Test
    fun bindsSearchInfrastructureProperties() {
        contextRunner
            .withPropertyValues(
                "sigak.search.mode=hybrid",
                "sigak.search.hybrid.rrf-k=70",
                "sigak.search.hybrid.keyword-weight=1.2",
                "sigak.search.hybrid.vector-weight=0.8",
                "sigak.search.hybrid.keyword-candidate-limit=25",
                "sigak.search.hybrid.vector-candidate-limit=30",
                "sigak.search.hybrid.result-limit=15",
                "sigak.search.elasticsearch.url=http://es:9200",
                "sigak.search.elasticsearch.article-index-name=test-articles",
                "sigak.search.qdrant.url=http://qdrant:6333",
                "sigak.search.qdrant.article-collection-name=test-article-vectors",
                "sigak.search.qdrant.distance=Cosine",
                "sigak.search.qdrant.default-limit=7",
                "sigak.search.qdrant.max-limit=31",
                "sigak.search.neo4j.uri=bolt://neo4j:7687",
                "sigak.search.neo4j.username=neo4j",
                "sigak.search.neo4j.password=test-password"
            )
            .run { context ->
                val properties = context.getBean(SearchInfrastructureProperties::class.java)

                assertEquals(ArticleSearchMode.HYBRID, properties.mode)
                assertEquals(70, properties.hybrid.rrfK)
                assertEquals(1.2, properties.hybrid.keywordWeight)
                assertEquals(0.8, properties.hybrid.vectorWeight)
                assertEquals(25, properties.hybrid.keywordCandidateLimit)
                assertEquals(30, properties.hybrid.vectorCandidateLimit)
                assertEquals(15, properties.hybrid.resultLimit)
                assertEquals("http://es:9200", properties.elasticsearch.url)
                assertEquals("test-articles", properties.elasticsearch.articleIndexName)
                assertEquals("http://qdrant:6333", properties.qdrant.url)
                assertEquals("test-article-vectors", properties.qdrant.articleCollectionName)
                assertEquals("Cosine", properties.qdrant.distance)
                assertEquals(7, properties.qdrant.defaultLimit)
                assertEquals(31, properties.qdrant.maxLimit)
                assertEquals("bolt://neo4j:7687", properties.neo4j.uri)
                assertEquals("neo4j", properties.neo4j.username)
                assertEquals("test-password", properties.neo4j.password)
            }
    }

    @Configuration
    @EnableConfigurationProperties(SearchInfrastructureProperties::class)
    private class SearchInfrastructurePropertiesConfig
}
