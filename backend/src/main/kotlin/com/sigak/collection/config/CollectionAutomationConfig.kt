package com.sigak.collection.config

import com.sigak.collection.runner.CollectionAutomationScheduledJob
import com.sigak.collection.service.CollectionAutomationService
import com.sigak.collection.service.SourceRegistry
import java.time.Clock
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration
@EnableScheduling
@EnableConfigurationProperties(CollectionAutomationProperties::class)
class CollectionAutomationConfig {
    @Bean("collectionAutomationClock")
    fun collectionAutomationClock(): Clock = Clock.systemUTC()

    @Bean
    @ConditionalOnProperty(prefix = "sigak.collection.automation", name = ["enabled"], havingValue = "true")
    fun collectionAutomationScheduledJob(
        properties: CollectionAutomationProperties,
        registry: SourceRegistry,
        service: CollectionAutomationService
    ): CollectionAutomationScheduledJob {
        properties.validate()
        val knownSources = registry.sources().map { it.id }.toSet()
        require(properties.sourceIds.all { it.trim() in knownSources }) {
            "collection automation source IDs must belong to the source registry"
        }
        return CollectionAutomationScheduledJob(service)
    }
}
