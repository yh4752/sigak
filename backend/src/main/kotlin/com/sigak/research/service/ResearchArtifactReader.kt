package com.sigak.research.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.sigak.research.config.ResearchArtifactProperties
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import org.springframework.stereotype.Component

sealed class ResearchArtifactReadResult {
    data class Available(val json: JsonNode) : ResearchArtifactReadResult()
    data class Unavailable(val reason: String) : ResearchArtifactReadResult()
}

@Component
class ResearchArtifactReader(
    private val properties: ResearchArtifactProperties,
    private val objectMapper: ObjectMapper
) {

    fun readJson(relativePath: String): ResearchArtifactReadResult {
        val artifactPath = resolveArtifactPath(relativePath)
        if (!artifactPath.exists()) {
            return ResearchArtifactReadResult.Unavailable("Artifact not found: $relativePath")
        }

        return try {
            ResearchArtifactReadResult.Available(objectMapper.readTree(Files.readString(artifactPath)))
        } catch (exception: Exception) {
            ResearchArtifactReadResult.Unavailable("Artifact could not be parsed: $relativePath")
        }
    }

    private fun resolveArtifactPath(relativePath: String): Path =
        resolveArtifactRoot().resolve(relativePath).normalize()

    private fun resolveArtifactRoot(): Path {
        val configuredRoot = Path.of(properties.artifactRoot)
        if (configuredRoot.isAbsolute) {
            return configuredRoot.normalize()
        }

        val cwdRelative = configuredRoot.normalize()
        if (cwdRelative.exists()) {
            return cwdRelative
        }

        val parentRelative = Path.of("..").resolve(configuredRoot).normalize()
        if (parentRelative.exists()) {
            return parentRelative
        }

        // 로컬 실행 위치가 달라도 응답은 section-level unavailable로 내려가야 한다.
        return cwdRelative
    }
}
