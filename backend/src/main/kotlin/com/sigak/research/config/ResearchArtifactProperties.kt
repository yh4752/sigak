package com.sigak.research.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "sigak.research")
data class ResearchArtifactProperties(
    val artifactRoot: String = "experiments"
)
