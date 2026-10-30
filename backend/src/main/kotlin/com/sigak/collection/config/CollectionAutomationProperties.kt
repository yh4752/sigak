package com.sigak.collection.config

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "sigak.collection.automation")
data class CollectionAutomationProperties(
    val enabled: Boolean = false,
    val fixedDelay: Duration = Duration.ofMinutes(10),
    val initialDelay: Duration = Duration.ofSeconds(30),
    val maxArticlesPerSource: Int = 20,
    val sourceIds: List<String> = emptyList()
) {
    fun validate() {
        require(fixedDelay >= Duration.ofSeconds(60)) { "collection automation fixed delay must be at least 60 seconds" }
        require(!initialDelay.isNegative) { "collection automation initial delay must not be negative" }
        require(maxArticlesPerSource in 1..20) { "collection automation batch must be 1..20 per source" }
    }
}
