package com.sigak.collection.runner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CollectionRunCommandParserTest {

    private val parser = CollectionRunCommandParser()

    @Test
    fun parseReturnsNullWhenCollectionRunCommandIsNotRequested() {
        assertNull(parser.parse(emptyArray()))
        assertNull(parser.parse(arrayOf("--server.port=8081")))
    }

    @Test
    fun parseBuildsRequestFromSourcesAndMaxOptions() {
        val command = parser.parse(
            arrayOf(
                "collection-run",
                "--sources= github-blog,openai-blog, ,github-blog ",
                "--max=3"
            )
        )

        assertEquals(listOf("github-blog", "openai-blog"), command?.request?.sourceIds)
        assertEquals(3, command?.request?.maxArticlesPerSource)
    }

    @Test
    fun parseUsesDefaultRequestWhenOptionsAreMissing() {
        val command = parser.parse(arrayOf("collection-run"))

        assertEquals(null, command?.request?.sourceIds)
        assertEquals(null, command?.request?.maxArticlesPerSource)
    }

    @Test
    fun parseRejectsUnknownOptionsBeforeCollectionRuns() {
        val exception = assertFailsWith<IllegalArgumentException> {
            parser.parse(arrayOf("collection-run", "--later=true"))
        }

        assertEquals("Unknown collection-run option: --later=true", exception.message)
    }

    @Test
    fun parseRejectsNonNumericMaxBeforeCollectionRuns() {
        val exception = assertFailsWith<IllegalArgumentException> {
            parser.parse(arrayOf("collection-run", "--max=many"))
        }

        assertEquals("collection-run --max must be a number.", exception.message)
    }
}
