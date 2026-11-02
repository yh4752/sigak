package com.sigak.collection.service

import com.sigak.SigakBackendApplication
import com.sigak.support.PostgresIntegrationTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

@SpringBootTest(classes = [SigakBackendApplication::class])
class SourceRegistryTest : PostgresIntegrationTest() {
    @Autowired private lateinit var registry: SourceRegistry
    @Autowired private lateinit var jdbc: JdbcTemplate

    @Test
    fun `registry reads current display name from database without rebuilding`() {
        jdbc.update("update news_sources set name='변경된 공식 블로그' where source_key='openai-blog'")
        try {
            assertEquals("변경된 공식 블로그", registry.sources().single { it.id == "openai-blog" }.name)
        } finally {
            jdbc.update("update news_sources set name='OpenAI Blog' where source_key='openai-blog'")
        }
    }

    @Test
    fun `only the six migrated operational sources are enabled`() {
        assertEquals(setOf("openai-blog", "google-ai-blog", "github-blog", "arxiv-cs-ai", "arxiv-cs-lg", "arxiv-cs-cl"), registry.sources().map { it.id }.toSet())
        assertFalse(registry.sources().any { it.id in setOf("openai", "arxiv", "cncf", "postgresql-weekly") })
    }
}
