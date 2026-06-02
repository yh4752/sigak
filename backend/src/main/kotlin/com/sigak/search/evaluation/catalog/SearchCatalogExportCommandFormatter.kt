package com.sigak.search.evaluation.catalog

import org.springframework.stereotype.Component

data class SearchCatalogExportCommandResult(
    val command: SearchCatalogExportCommand,
    val catalog: SearchCatalog
)

@Component
class SearchCatalogExportCommandFormatter {

    fun format(result: SearchCatalogExportCommandResult): String =
        buildString {
            appendLine("Search catalog export completed")
            appendLine("catalogId=${result.catalog.catalogId}")
            appendLine("articleCount=${result.catalog.articles.size}")
            appendLine("output=${result.command.output}")
        }.trimEnd()

    fun formatError(exception: Throwable): String =
        "Search catalog export failed: ${exception.message ?: exception::class.simpleName.orEmpty()}"
}
