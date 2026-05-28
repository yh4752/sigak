package com.sigak.ai.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "sigak.ai-server")
data class AiServerProperties(
    val url: String = "http://localhost:8000"
)
