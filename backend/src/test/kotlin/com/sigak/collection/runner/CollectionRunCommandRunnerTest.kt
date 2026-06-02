package com.sigak.collection.runner

import com.sigak.collection.service.CollectionRunService
import com.sigak.collection.service.CollectionFailureClassifier
import com.sigak.collection.service.NoOpCollectionFailureRecorder
import com.sigak.collection.service.SourceCollectionResult
import com.sigak.collection.service.SourceCollector
import com.sigak.collection.service.SourceRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import org.springframework.boot.DefaultApplicationArguments

class CollectionRunCommandRunnerTest {

    @Test
    fun runDoesNothingWhenCollectionRunCommandIsNotRequested() {
        val exit = RecordingApplicationExit()
        val output = RecordingCommandOutput()
        val collectedSourceIds = mutableListOf<String>()
        val runner = runner(
            exit = exit,
            output = output,
            collectedSourceIds = collectedSourceIds
        )

        runner.run(DefaultApplicationArguments("--server.port=8081"))

        assertEquals(emptyList(), collectedSourceIds)
        assertEquals(emptyList(), exit.codes)
        assertEquals(emptyList(), output.lines)
        assertEquals(emptyList(), output.errorLines)
    }

    @Test
    fun runExecutesCollectionAndExitsZeroForCollectionRunCommand() {
        val exit = RecordingApplicationExit()
        val output = RecordingCommandOutput()
        val collectedSourceIds = mutableListOf<String>()
        val runner = runner(
            exit = exit,
            output = output,
            collectedSourceIds = collectedSourceIds
        )

        runner.run(DefaultApplicationArguments("collection-run", "--sources=github-blog", "--max=1"))

        assertEquals(listOf("github-blog"), collectedSourceIds)
        assertEquals(listOf(0), exit.codes)
        assertEquals(true, output.lines.single().contains("Collection run status: COMPLETED"))
        assertEquals(emptyList(), output.errorLines)
    }

    @Test
    fun runExitsOneWhenCommandArgumentsAreInvalid() {
        val exit = RecordingApplicationExit()
        val output = RecordingCommandOutput()
        val collectedSourceIds = mutableListOf<String>()
        val runner = runner(
            exit = exit,
            output = output,
            collectedSourceIds = collectedSourceIds
        )

        runner.run(DefaultApplicationArguments("collection-run", "--max=many"))

        assertEquals(emptyList(), collectedSourceIds)
        assertEquals(listOf(1), exit.codes)
        assertEquals(emptyList(), output.lines)
        assertEquals("Collection run failed: collection-run --max must be a number.", output.errorLines.single())
    }

    private fun runner(
        exit: RecordingApplicationExit,
        output: RecordingCommandOutput,
        collectedSourceIds: MutableList<String>
    ): CollectionRunCommandRunner =
        CollectionRunCommandRunner(
            parser = CollectionRunCommandParser(),
            service = CollectionRunService(
                sourceRegistry = SourceRegistry(),
                sourceCollector = SourceCollector { source, _ ->
                    collectedSourceIds.add(source.id)
                    SourceCollectionResult(
                        sourceId = source.id,
                        discoveredCount = 1,
                        publishedArticleIds = listOf(10L + collectedSourceIds.size),
                        skippedArticleIds = emptyList(),
                        failedCount = 0,
                        failureSummaries = emptyList()
                    )
                },
                collectionFailureClassifier = CollectionFailureClassifier(),
                collectionFailureRecorder = NoOpCollectionFailureRecorder
            ),
            formatter = CollectionRunCommandFormatter(),
            output = output,
            exit = exit
        )

    private class RecordingApplicationExit : ApplicationExit {
        val codes = mutableListOf<Int>()

        override fun exit(code: Int) {
            codes.add(code)
        }
    }

    private class RecordingCommandOutput : CollectionRunCommandOutput {
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
