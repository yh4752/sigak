package com.sigak.collection.automation

import com.sigak.collection.config.CollectionAutomationConfig
import com.sigak.collection.config.CollectionAutomationProperties
import com.sigak.collection.runner.CollectionAutomationScheduledJob
import com.sigak.collection.service.CollectionAutomationService
import com.sigak.collection.service.SourceRegistry
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.mockito.Mockito.mock
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.scheduling.annotation.Scheduled

class CollectionAutomationConfigTest {
    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(CollectionAutomationConfig::class.java)
        .withBean(CollectionAutomationService::class.java, { mock(CollectionAutomationService::class.java) })
        .withBean(SourceRegistry::class.java, { SourceRegistry() })

    @Test
    fun jobIsNotRegisteredByDefault() {
        contextRunner.run { context ->
            assertNull(context.startupFailure)
            assertEquals(false, context.getBean(CollectionAutomationProperties::class.java).enabled)
            assertEquals(0, context.getBeansOfType(CollectionAutomationScheduledJob::class.java).size)
        }
    }

    @Test
    fun registersJobAndBindsDurationsAndSelectedSourcesWhenEnabled() {
        contextRunner.withPropertyValues(
            "sigak.collection.automation.enabled=true",
            "sigak.collection.automation.fixed-delay=PT15M",
            "sigak.collection.automation.initial-delay=PT1H",
            "sigak.collection.automation.source-ids=github-blog,openai-blog",
            "sigak.collection.automation.max-articles-per-source=3"
        ).run { context ->
            assertNull(context.startupFailure)
            assertEquals(1, context.getBeansOfType(CollectionAutomationScheduledJob::class.java).size)
            val properties = context.getBean(CollectionAutomationProperties::class.java)
            assertEquals(Duration.ofMinutes(15), properties.fixedDelay)
            assertEquals(Duration.ofHours(1), properties.initialDelay)
            assertEquals(listOf("github-blog", "openai-blog"), properties.sourceIds)
            assertEquals(3, properties.maxArticlesPerSource)
        }
    }

    @Test
    fun invalidSourceAndTooShortIntervalFailStartup() {
        for (property in listOf("source-ids=missing-source", "fixed-delay=PT1S")) {
            contextRunner.withPropertyValues("sigak.collection.automation.enabled=true", "sigak.collection.automation.$property")
                .run { context -> assertNotNull(context.startupFailure) }
        }
    }

    @Test
    fun emptySourceSettingUsesTheRegisteredSources() {
        contextRunner.withPropertyValues(
            "sigak.collection.automation.enabled=true",
            "sigak.collection.automation.source-ids=",
            "sigak.collection.automation.initial-delay=PT1H"
        ).run { context ->
            assertNull(context.startupFailure)
            assertEquals(emptyList(), context.getBean(CollectionAutomationProperties::class.java).sourceIds)
        }
    }

    @Test
    fun scheduleUsesExternalDurationProperties() {
        val scheduled = CollectionAutomationScheduledJob::class.java.getMethod("tick").getAnnotation(Scheduled::class.java)
        assertEquals("\${sigak.collection.automation.fixed-delay:PT10M}", scheduled.fixedDelayString)
        assertEquals("\${sigak.collection.automation.initial-delay:PT30S}", scheduled.initialDelayString)
    }
}
