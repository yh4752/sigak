package com.sigak.config

import org.springframework.beans.factory.annotation.Value
import java.net.URI
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class CorsConfig(@Value("\${sigak.public-origin:}") publicOrigin: String) {
    private val allowedOrigins = listOf("http://localhost:5173", "http://127.0.0.1:5173") +
        listOfNotNull(publicOrigin.takeIf { it.isNotBlank() })

    init {
        if(publicOrigin.isNotBlank()) {
            val uri=URI(publicOrigin)
            require(uri.scheme in setOf("http","https") && uri.host != null && uri.rawUserInfo == null &&
                uri.path.isNullOrEmpty() && uri.query == null && uri.fragment == null) { "Public origin must be one exact HTTP(S) origin" }
        }
    }

    @Bean
    fun corsConfigurer(): WebMvcConfigurer =
        object : WebMvcConfigurer {
            override fun addCorsMappings(registry: CorsRegistry) {
                registry.addMapping("/api/articles/*/summary")
                    .allowedOrigins(*allowedOrigins.toTypedArray())
                    .allowedMethods("GET", "POST")
                registry.addMapping("/api/**")
                    .allowedOrigins(*allowedOrigins.toTypedArray())
                    .allowedMethods("GET")
            }
        }
}
