package com.sigak.research.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sigak.research.config.ResearchArtifactProperties
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class ResearchArtifactReaderTest {

    @TempDir
    lateinit var tempDir: Path

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun readsJsonFromConfiguredArtifactRoot() {
        val artifactFile = tempDir.resolve("results/retrieval/latest/metrics.comparison.json")
        artifactFile.parent.createDirectories()
        artifactFile.writeText("""{"catalogId":"api-ready-2026-06-02"}""")
        val reader = ResearchArtifactReader(
            properties = ResearchArtifactProperties(artifactRoot = tempDir.toString()),
            objectMapper = objectMapper
        )

        val result = reader.readJson("results/retrieval/latest/metrics.comparison.json")

        assertTrue(result is ResearchArtifactReadResult.Available)
        assertEquals("api-ready-2026-06-02", result.json.get("catalogId").asText())
    }

    @Test
    fun reportsMissingArtifactWithoutExposingAbsolutePath() {
        val reader = ResearchArtifactReader(
            properties = ResearchArtifactProperties(artifactRoot = tempDir.toString()),
            objectMapper = objectMapper
        )

        val result = reader.readJson("results/retrieval/latest/missing.json")

        assertTrue(result is ResearchArtifactReadResult.Unavailable)
        assertEquals(
            "Artifact not found: results/retrieval/latest/missing.json",
            result.reason
        )
    }
}
