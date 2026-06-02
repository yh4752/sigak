package com.sigak.search.evaluation.catalog

import java.nio.file.Path
import org.springframework.stereotype.Component

private const val SEARCH_CATALOG_EXPORT_COMMAND = "search-catalog-export"
private const val OUTPUT_OPTION_PREFIX = "--output="
private const val LIMIT_OPTION_PREFIX = "--limit="
private const val CATALOG_ID_OPTION_PREFIX = "--catalog-id="

@Component
class SearchCatalogExportCommandParser {

    fun parse(args: Array<String>): SearchCatalogExportCommand? {
        if (args.firstOrNull() != SEARCH_CATALOG_EXPORT_COMMAND) {
            return null
        }

        var output: Path? = null
        var limit = DEFAULT_SEARCH_CATALOG_EXPORT_LIMIT
        var catalogId: String? = null

        args.drop(1).forEach { arg ->
            when {
                arg.startsWith(OUTPUT_OPTION_PREFIX) -> {
                    output = outputFor(arg.removePrefix(OUTPUT_OPTION_PREFIX))
                }
                arg.startsWith(LIMIT_OPTION_PREFIX) -> {
                    limit = limitFor(arg.removePrefix(LIMIT_OPTION_PREFIX))
                }
                arg.startsWith(CATALOG_ID_OPTION_PREFIX) -> {
                    catalogId = catalogIdFor(arg.removePrefix(CATALOG_ID_OPTION_PREFIX))
                }
                else -> throw IllegalArgumentException("Unknown search-catalog-export option: $arg")
            }
        }

        return SearchCatalogExportCommand(
            output = output ?: throw IllegalArgumentException("search-catalog-export --output is required."),
            limit = limit,
            catalogId = catalogId
        )
    }

    private fun outputFor(value: String): Path {
        val output = value.trim()
        if (output.isBlank()) {
            throw IllegalArgumentException("search-catalog-export --output must not be blank.")
        }

        return Path.of(output)
    }

    private fun limitFor(value: String): Int {
        val limit = value.toIntOrNull()
            ?: throw IllegalArgumentException("search-catalog-export --limit must be a number.")

        if (limit < 1) {
            throw IllegalArgumentException("search-catalog-export --limit must be at least 1.")
        }

        return limit
    }

    private fun catalogIdFor(value: String): String {
        val catalogId = value.trim()
        if (catalogId.isBlank()) {
            throw IllegalArgumentException("search-catalog-export --catalog-id must not be blank.")
        }

        return catalogId
    }
}
