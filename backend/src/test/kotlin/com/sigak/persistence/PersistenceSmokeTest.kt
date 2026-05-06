package com.sigak.persistence

import com.sigak.SigakBackendApplication
import com.sigak.support.PostgresIntegrationTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

@SpringBootTest(classes = [SigakBackendApplication::class])
class PersistenceSmokeTest : PostgresIntegrationTest() {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun flywayCreatesSchemaAndSeedsArticles() {
        val articleCount = jdbcTemplate.queryForObject(
            "select count(*) from articles",
            Int::class.java
        )
        val sourceCount = jdbcTemplate.queryForObject(
            "select count(*) from news_sources",
            Int::class.java
        )
        val currentEnrichmentCount = jdbcTemplate.queryForObject(
            "select count(*) from article_enrichments where is_current = true",
            Int::class.java
        )

        assertEquals(5, articleCount)
        assertEquals(5, sourceCount)
        assertEquals(5, currentEnrichmentCount)
    }
}
