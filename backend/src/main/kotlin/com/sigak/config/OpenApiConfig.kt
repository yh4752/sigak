package com.sigak.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun sigakOpenApi(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("Sigak API")
                    .description("Public Spring Boot REST API for Sigak. Collection and AI enrichment are internal foundation work until persistence and indexing are added.")
                    .version("0.1.0")
            )
}
