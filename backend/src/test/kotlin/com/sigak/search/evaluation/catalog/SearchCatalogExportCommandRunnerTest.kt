package com.sigak.search.evaluation.catalog

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sigak.article.dto.ArticleResponse
import com.sigak.collection.runner.ApplicationExit
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.springframework.boot.DefaultApplicationArguments

class SearchCatalogExportCommandRunnerTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun runDoesNothingWhenSearchCatalogExportCommandIsNotRequested() {
        val exit = RecordingApplicationExit()
        val output = RecordingCommandOutput()
        val runner = runner(exit = exit, output = output, articles = listOf(article(1)))

        runner.run(DefaultApplicationArguments("--server.port=8081"))

        assertEquals(emptyList(), exit.codes)
        assertEquals(emptyList(), output.lines)
        assertEquals(emptyList(), output.errorLines)
    }

    @Test
    fun runExportsCatalogAndExitsZeroForSearchCatalogExportCommand() {
        val exit = RecordingApplicationExit()
        val output = RecordingCommandOutput()
        val exportPath = tempDir.resolve("raw/articles.catalog.json")
        val runner = runner(exit = exit, output = output, articles = listOf(article(1), article(2)))

        runner.run(
            DefaultApplicationArguments(
                "search-catalog-export",
                "--output=$exportPath",
                "--limit=1",
                "--catalog-id=api-ready-test"
            )
        )

        assertEquals(listOf(0), exit.codes)
        assertTrue(Files.exists(exportPath))
        assertTrue(exportPath.readText().contains("\"catalogId\" : \"api-ready-test\""))
        assertTrue(output.lines.single().contains("Search catalog export completed"))
        assertTrue(output.lines.single().contains("articleCount=1"))
        assertEquals(emptyList(), output.errorLines)
    }

    @Test
    fun runExitsOneWhenCommandArgumentsAreInvalid() {
        val exit = RecordingApplicationExit()
        val output = RecordingCommandOutput()
        val runner = runner(exit = exit, output = output, articles = listOf(article(1)))

        runner.run(DefaultApplicationArguments("search-catalog-export", "--limit=many"))

        assertEquals(listOf(1), exit.codes)
        assertEquals(emptyList(), output.lines)
        assertEquals(
            "Search catalog export failed: search-catalog-export --limit must be a number.",
            output.errorLines.single()
        )
    }

    @Test
    fun runExitsOneWhenNoApiReadyArticlesExist() {
        val exit = RecordingApplicationExit()
        val output = RecordingCommandOutput()
        val exportPath = tempDir.resolve("raw/articles.catalog.json")
        val runner = runner(exit = exit, output = output, articles = emptyList())

        runner.run(
            DefaultApplicationArguments(
                "search-catalog-export",
                "--output=$exportPath"
            )
        )

        assertEquals(listOf(1), exit.codes)
        assertEquals(emptyList(), output.lines)
        assertEquals(
            "Search catalog export failed: No API-ready articles are available for search catalog export.",
            output.errorLines.single()
        )
    }

    @Test
    fun runExitsOneWhenCatalogWriteFails() {
        val exit = RecordingApplicationExit()
        val output = RecordingCommandOutput()
        val runner = runner(exit = exit, output = output, articles = listOf(article(1)))

        runner.run(
            DefaultApplicationArguments(
                "search-catalog-export",
                "--output=$tempDir"
            )
        )

        assertEquals(listOf(1), exit.codes)
        assertEquals(emptyList(), output.lines)
        assertTrue(output.errorLines.single().startsWith("Search catalog export failed:"))
    }

    private fun runner(
        exit: RecordingApplicationExit,
        output: RecordingCommandOutput,
        articles: List<ArticleResponse>
    ): SearchCatalogExportCommandRunner =
        SearchCatalogExportCommandRunner(
            parser = SearchCatalogExportCommandParser(),
            service = SearchCatalogExportService(
                articleReader = SearchCatalogArticleReader { articles },
                catalogFactory = SearchCatalogFactory()
            ),
            writer = SearchCatalogExportJsonWriter(jacksonObjectMapper()),
            formatter = SearchCatalogExportCommandFormatter(),
            output = output,
            exit = exit
        )

    private fun article(id: Long): ArticleResponse =
        ArticleResponse(
            id = id,
            title = "Article $id",
            source = "Source $id",
            url = "https://example.com/articles/$id",
            publishedAt = "2026-05-07T00:00:00Z",
            eventType = "NEWS",
            primaryCategory = "AI",
            topics = listOf("topic-$id"),
            summary = "summary for $id",
            whyItMatters = "why it matters for $id",
            importanceScore = 70,
            relatedArticleIds = emptyList()
        )

    private class RecordingApplicationExit : ApplicationExit {
        val codes = mutableListOf<Int>()

        override fun exit(code: Int) {
            codes.add(code)
        }
    }

    private class RecordingCommandOutput : SearchCatalogExportCommandOutput {
        val lines = mutableListOf<String>()
        val errorLines = mutableListOf<String>()

        override fun writeLine(value: String) {
            lines.add(value)
        }

        override fun writeErrorLine(value: String) {
            errorLines.add(value)
        }
    }
}
