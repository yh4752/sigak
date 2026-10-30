package com.sigak.article.summary

import com.sigak.ai.config.AiServerProperties
import java.net.http.HttpClient
import java.time.Duration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.web.client.RestClient

@Configuration
@EnableConfigurationProperties(SummaryProperties::class)
class SummaryConfig {
    @Bean
    fun summaryProviderClient(properties: AiServerProperties): SummaryProviderClient {
        val factory=JdkClientHttpRequestFactory(HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build())
        // AI 내부 요청의 45초와 연결 시간을 합친 것보다 길게 기다려 유효한 응답 유실을 줄인다.
        factory.setReadTimeout(Duration.ofSeconds(75))
        val client=RestClient.builder().baseUrl(properties.url).requestFactory(factory).build()
        return SummaryProviderClient { input ->
            client.post().uri("/api/enrichment/article").body(input).retrieve()
                .body(SummaryOutput::class.java) ?: error("Empty AI summary response")
        }
    }

    @Bean
    @ConditionalOnProperty(prefix="sigak.summary", name=["worker-enabled"], havingValue="true")
    fun summaryWorker(service: ArticleSummaryService): SummaryWorker = SummaryWorker(service)
}

class SummaryWorker(private val service: ArticleSummaryService) {
    @Scheduled(fixedDelayString="PT2S", initialDelayString="PT5S")
    fun tick() { service.runOne() }
}
