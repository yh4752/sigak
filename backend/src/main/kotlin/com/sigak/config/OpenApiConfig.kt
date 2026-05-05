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
                    .description("Backend REST API for the Sigak technical news insight platform.")
                    .version("0.1.0")
            )
}
