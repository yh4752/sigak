package com.sigak.support

import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer

@ActiveProfiles("integration-test")
abstract class PostgresIntegrationTest {

    companion object {
        init {
            if (System.getProperty("api.version").isNullOrBlank()) {
                System.setProperty("api.version", "1.44")
            }
        }

        private val postgres = PostgreSQLContainer("postgres:16-alpine").apply {
            withDatabaseName("sigak_test")
            withUsername("sigak")
            withPassword("sigak")
            start()
        }

        @DynamicPropertySource
        @JvmStatic
        fun registerPostgresProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.jpa.hibernate.ddl-auto") { "validate" }
            registry.add("spring.flyway.enabled") { "true" }
        }
    }
}
