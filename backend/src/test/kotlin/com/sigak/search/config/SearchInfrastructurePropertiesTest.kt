package com.sigak.search.config

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
                "sigak.search.elasticsearch.url=http://es:9200",
                "sigak.search.qdrant.url=http://qdrant:6333",
                "sigak.search.neo4j.uri=bolt://neo4j:7687",
                "sigak.search.neo4j.username=neo4j",
                "sigak.search.neo4j.password=test-password"
            )
            .run { context ->
                val properties = context.getBean(SearchInfrastructureProperties::class.java)

                assertEquals("http://es:9200", properties.elasticsearch.url)
                assertEquals("http://qdrant:6333", properties.qdrant.url)
                assertEquals("bolt://neo4j:7687", properties.neo4j.uri)
                assertEquals("neo4j", properties.neo4j.username)
                assertEquals("test-password", properties.neo4j.password)
            }
    }

    @Configuration
    @EnableConfigurationProperties(SearchInfrastructureProperties::class)
    private class SearchInfrastructurePropertiesConfig
}
