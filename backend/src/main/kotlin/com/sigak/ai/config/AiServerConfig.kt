package com.sigak.ai.config

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
@EnableConfigurationProperties(AiServerProperties::class)
class AiServerConfig(
    private val properties: AiServerProperties
) {

    @Bean
    @Qualifier("aiServerRestClient")
    fun aiServerRestClient(): RestClient =
        RestClient.builder()
            .baseUrl(properties.url)
            .build()
}
