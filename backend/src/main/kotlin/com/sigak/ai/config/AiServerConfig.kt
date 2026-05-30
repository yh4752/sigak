package com.sigak.ai.config

import java.net.http.HttpClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
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
            // Uvicorn은 h2c 업그레이드를 지원하지 않으므로 FastAPI 호출은 HTTP/1.1로 고정한다.
            .requestFactory(JdkClientHttpRequestFactory(http11Client()))
            .build()

    private fun http11Client(): HttpClient =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build()
}
