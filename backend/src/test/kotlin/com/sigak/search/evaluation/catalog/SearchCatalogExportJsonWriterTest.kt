package com.sigak.search.evaluation.catalog

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class SearchCatalogExportJsonWriterTest {

    @TempDir
    lateinit var tempDir: Path

    private val writer = SearchCatalogExportJsonWriter(jacksonObjectMapper())

    @Test
    fun writeCreatesParentDirectoriesAndWritesPrettyJson() {
        val output = tempDir.resolve("nested/raw/articles.catalog.json")
        val catalog = SearchCatalog(
            catalogId = "api-ready-test",
            generatedAt = "2026-06-02T00:00:00Z",
            source = "postgres-api-ready",
            articles = listOf(
                SearchCatalogArticle(
                    id = 1,
                    title = "Article 1",
                    category = "AI",
                    topics = listOf("agent"),
                    summaryKo = "summary",
                    whyItMattersKo = "why",
                    publishedAt = "2026-05-07T00:00:00Z",
                    source = "Source",
                    url = "https://example.com/articles/1"
                )
            )
        )

        writer.write(output, catalog)

        assertTrue(Files.exists(output))
        assertTrue(output.readText().contains("\"catalogId\" : \"api-ready-test\""))
        assertEquals(true, output.readText().contains(System.lineSeparator()))
    }
}
