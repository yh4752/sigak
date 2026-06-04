package com.sigak.collection.config

import com.sigak.collection.service.SourceFetchDelay
import com.sigak.collection.service.ArxivFetchGate
import com.sigak.collection.service.FileBackedArxivFetchGate
import com.sigak.collection.service.ThreadSourceFetchDelay
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Clock
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
    val readTimeout: Duration = Duration.ofSeconds(30)
)

@ConfigurationProperties(prefix = "sigak.collection.arxiv")
data class ArxivFetchProperties(
    val minRequestInterval: Duration = Duration.ofSeconds(3),
    val rateLimitRetryDelay: Duration = Duration.ofSeconds(30),
    val transientFetchRetryDelay: Duration = Duration.ofSeconds(5),
    val maxTransientFetchRetries: Int = 1,
    val maxRateLimitRetries: Int = 2,
    val cooldownStateFile: Path = Paths.get(System.getProperty("java.io.tmpdir"), "sigak", "arxiv-export-fetch.cooldown")
)

@Configuration
@EnableConfigurationProperties(value = [CollectionHttpProperties::class, ArxivFetchProperties::class])
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

    @Bean
    @Qualifier("collectionFetchClock")
    fun collectionFetchClock(): Clock = Clock.systemUTC()

    @Bean
    fun sourceFetchDelay(): SourceFetchDelay = ThreadSourceFetchDelay()

    @Bean
    fun arxivFetchGate(
        arxivFetchProperties: ArxivFetchProperties,
        sourceFetchDelay: SourceFetchDelay,
        @Qualifier("collectionFetchClock") clock: Clock
    ): ArxivFetchGate =
        FileBackedArxivFetchGate(
            properties = arxivFetchProperties,
            sourceFetchDelay = sourceFetchDelay,
            clock = clock,
            cooldownStateFile = arxivFetchProperties.cooldownStateFile
        )
}
