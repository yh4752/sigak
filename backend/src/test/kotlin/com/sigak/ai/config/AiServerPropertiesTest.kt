package com.sigak.ai.config

import kotlin.test.Test
import kotlin.test.assertEquals
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration

class AiServerPropertiesTest {

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(AiServerPropertiesConfig::class.java)

    @Test
    fun bindsAiServerProperties() {
        contextRunner
            .withPropertyValues("sigak.ai-server.url=http://ai-server:8000")
            .run { context ->
                val properties = context.getBean(AiServerProperties::class.java)

                assertEquals("http://ai-server:8000", properties.url)
            }
    }

    @Configuration
    @EnableConfigurationProperties(AiServerProperties::class)
    private class AiServerPropertiesConfig
}
