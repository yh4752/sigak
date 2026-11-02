package com.sigak.research.config

import kotlin.test.Test
import kotlin.test.assertEquals
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration

class ResearchArtifactPropertiesTest {

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(ResearchArtifactPropertiesConfig::class.java)

    @Test
    fun bindsResearchArtifactRoot() {
        contextRunner
            .withPropertyValues("sigak.research.artifact-root=/tmp/sigak-experiments")
            .run { context ->
                val properties = context.getBean(ResearchArtifactProperties::class.java)

                assertEquals("/tmp/sigak-experiments", properties.artifactRoot)
            }
    }

    @Configuration
    @EnableConfigurationProperties(ResearchArtifactProperties::class)
    private class ResearchArtifactPropertiesConfig
}
