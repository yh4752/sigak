package com.sigak.search.evaluation.catalog

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SearchCatalogExportCommandParserTest {

    private val parser = SearchCatalogExportCommandParser()

    @Test
    fun parseReturnsNullWhenSearchCatalogExportCommandIsNotRequested() {
        assertNull(parser.parse(emptyArray()))
        assertNull(parser.parse(arrayOf("--server.port=8081")))
        assertNull(parser.parse(arrayOf("collection-run", "--sources=github-blog")))
    }

    @Test
    fun parseBuildsCommandFromOutputLimitAndCatalogIdOptions() {
        val command = parser.parse(
            arrayOf(
                "search-catalog-export",
                "--output=../experiments/datasets/raw/articles.catalog.json",
                "--limit=30",
                "--catalog-id=api-ready-test"
            )
        )

        assertEquals(Path.of("../experiments/datasets/raw/articles.catalog.json"), command?.output)
        assertEquals(30, command?.limit)
        assertEquals("api-ready-test", command?.catalogId)
    }

    @Test
    fun parseTrimsOutputOptionBeforeBuildingPath() {
        val command = parser.parse(
            arrayOf(
                "search-catalog-export",
                "--output=  ../experiments/datasets/raw/articles.catalog.json  "
            )
        )

        assertEquals(Path.of("../experiments/datasets/raw/articles.catalog.json"), command?.output)
    }

    @Test
    fun parseUsesDefaultLimitAndCatalogIdWhenOptionalOptionsAreMissing() {
        val command = parser.parse(
            arrayOf(
                "search-catalog-export",
                "--output=../experiments/datasets/raw/articles.catalog.json"
            )
        )

        assertEquals(Path.of("../experiments/datasets/raw/articles.catalog.json"), command?.output)
        assertEquals(50, command?.limit)
        assertEquals(null, command?.catalogId)
    }

    @Test
    fun parseRequiresOutputOption() {
        val exception = assertFailsWith<IllegalArgumentException> {
            parser.parse(arrayOf("search-catalog-export", "--limit=30"))
        }

        assertEquals("search-catalog-export --output is required.", exception.message)
    }

    @Test
    fun parseRejectsBlankOutputOption() {
        val exception = assertFailsWith<IllegalArgumentException> {
            parser.parse(arrayOf("search-catalog-export", "--output=   "))
        }

        assertEquals("search-catalog-export --output must not be blank.", exception.message)
    }

    @Test
    fun parseRejectsNonNumericLimit() {
        val exception = assertFailsWith<IllegalArgumentException> {
            parser.parse(
                arrayOf(
                    "search-catalog-export",
                    "--output=../experiments/datasets/raw/articles.catalog.json",
                    "--limit=many"
                )
            )
        }

        assertEquals("search-catalog-export --limit must be a number.", exception.message)
    }

    @Test
    fun parseRejectsLimitLessThanOne() {
        val exception = assertFailsWith<IllegalArgumentException> {
            parser.parse(
                arrayOf(
                    "search-catalog-export",
                    "--output=../experiments/datasets/raw/articles.catalog.json",
                    "--limit=0"
                )
            )
        }

        assertEquals("search-catalog-export --limit must be at least 1.", exception.message)
    }

    @Test
    fun parseRejectsBlankCatalogId() {
        val exception = assertFailsWith<IllegalArgumentException> {
            parser.parse(
                arrayOf(
                    "search-catalog-export",
                    "--output=../experiments/datasets/raw/articles.catalog.json",
                    "--catalog-id=   "
                )
            )
        }

        assertEquals("search-catalog-export --catalog-id must not be blank.", exception.message)
    }

    @Test
    fun parseRejectsUnknownOptions() {
        val exception = assertFailsWith<IllegalArgumentException> {
            parser.parse(
                arrayOf(
                    "search-catalog-export",
                    "--output=../experiments/datasets/raw/articles.catalog.json",
                    "--format=csv"
                )
            )
        }

        assertEquals("Unknown search-catalog-export option: --format=csv", exception.message)
    }
}
