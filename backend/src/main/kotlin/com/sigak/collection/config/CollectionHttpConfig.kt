package com.sigak.collection.config

import java.time.Duration
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

@ConfigurationProperties(prefix = "sigak.collection.http")
data class CollectionHttpProperties(
    val connectTimeout: Duration = Duration.ofSeconds(5),
    val readTimeout: Duration = Duration.ofSeconds(10)
)

@Configuration
@EnableConfigurationProperties(CollectionHttpProperties::class)
class CollectionHttpConfig(
    private val properties: CollectionHttpProperties
) {

    @Bean
    @Qualifier("sourceContentRestClient")
    fun sourceContentRestClient(): RestClient =
        RestClient.builder()
            .requestFactory(sourceContentRequestFactory())
            .build()

    private fun sourceContentRequestFactory(): SimpleClientHttpRequestFactory =
        SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(properties.connectTimeout)
            setReadTimeout(properties.readTimeout)
        }
}
