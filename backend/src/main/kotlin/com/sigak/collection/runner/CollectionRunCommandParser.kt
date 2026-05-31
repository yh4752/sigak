package com.sigak.collection.runner

import com.sigak.collection.dto.CollectionRunRequest
import org.springframework.stereotype.Component

private const val COLLECTION_RUN_COMMAND = "collection-run"
private const val SOURCES_OPTION_PREFIX = "--sources="
private const val MAX_OPTION_PREFIX = "--max="

@Component
class CollectionRunCommandParser {

    fun parse(args: Array<String>): CollectionRunCommand? {
        if (args.firstOrNull() != COLLECTION_RUN_COMMAND) {
            return null
        }

        var sourceIds: List<String>? = null
        var maxArticlesPerSource: Int? = null

        args.drop(1).forEach { arg ->
            when {
                arg.startsWith(SOURCES_OPTION_PREFIX) -> {
                    sourceIds = sourceIdsFor(arg.removePrefix(SOURCES_OPTION_PREFIX))
                }
                arg.startsWith(MAX_OPTION_PREFIX) -> {
                    maxArticlesPerSource = maxArticlesPerSourceFor(arg.removePrefix(MAX_OPTION_PREFIX))
                }
                else -> throw IllegalArgumentException("Unknown collection-run option: $arg")
            }
        }

        return CollectionRunCommand(
            request = CollectionRunRequest(
                sourceIds = sourceIds,
                maxArticlesPerSource = maxArticlesPerSource
            )
        )
    }

    private fun sourceIdsFor(value: String): List<String>? {
        val sourceIds = value
            .split(",")
            .map { sourceId -> sourceId.trim() }
            .filter { sourceId -> sourceId.isNotBlank() }
            .distinct()

        return sourceIds.ifEmpty { null }
    }

    private fun maxArticlesPerSourceFor(value: String): Int =
        value.toIntOrNull()
            ?: throw IllegalArgumentException("collection-run --max must be a number.")
}
