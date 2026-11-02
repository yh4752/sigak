package com.sigak.research.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(ResearchArtifactProperties::class)
class ResearchConfig
