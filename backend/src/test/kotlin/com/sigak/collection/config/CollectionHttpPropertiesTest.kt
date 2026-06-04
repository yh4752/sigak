package com.sigak.collection.config

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration

class CollectionHttpPropertiesTest {

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(CollectionHttpPropertiesConfig::class.java)

    @Test
    fun usesConservativeDefaultTimeoutsForExternalSources() {
        contextRunner.run { context ->
            val properties = context.getBean(CollectionHttpProperties::class.java)

            assertEquals(Duration.ofSeconds(5), properties.connectTimeout)
            assertEquals(Duration.ofSeconds(30), properties.readTimeout)
        }
    }

    @Test
    fun bindsArxivFetchProperties() {
        contextRunner
            .withPropertyValues(
                "sigak.collection.arxiv.min-request-interval=4s",
                "sigak.collection.arxiv.rate-limit-retry-delay=45s",
                "sigak.collection.arxiv.transient-fetch-retry-delay=6s",
                "sigak.collection.arxiv.max-transient-fetch-retries=2",
                "sigak.collection.arxiv.max-rate-limit-retries=3"
            )
            .run { context ->
                val properties = context.getBean(ArxivFetchProperties::class.java)

                assertEquals(Duration.ofSeconds(4), properties.minRequestInterval)
                assertEquals(Duration.ofSeconds(45), properties.rateLimitRetryDelay)
                assertEquals(Duration.ofSeconds(6), properties.transientFetchRetryDelay)
                assertEquals(2, properties.maxTransientFetchRetries)
                assertEquals(3, properties.maxRateLimitRetries)
            }
    }

    @Configuration
    @EnableConfigurationProperties(value = [CollectionHttpProperties::class, ArxivFetchProperties::class])
    private class CollectionHttpPropertiesConfig
}
