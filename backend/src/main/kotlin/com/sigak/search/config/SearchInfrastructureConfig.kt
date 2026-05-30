package com.sigak.search.config

import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
@EnableConfigurationProperties(SearchInfrastructureProperties::class)
class SearchInfrastructureConfig(
    private val properties: SearchInfrastructureProperties
) {

    @Bean
    @Qualifier("elasticsearchRestClient")
    fun elasticsearchRestClient(): RestClient =
        RestClient.builder()
            .baseUrl(properties.elasticsearch.url)
            .build()

    @Bean
    @Qualifier("qdrantRestClient")
    fun qdrantRestClient(): RestClient =
        RestClient.builder()
            .baseUrl(properties.qdrant.url)
            .build()

    @Bean
    fun neo4jDriver(): Driver =
        GraphDatabase.driver(
            properties.neo4j.uri,
            AuthTokens.basic(properties.neo4j.username, properties.neo4j.password)
        )
}
