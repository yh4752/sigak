package com.sigak.search.evaluation.catalog

import java.nio.file.Path

const val DEFAULT_SEARCH_CATALOG_EXPORT_LIMIT = 50

data class SearchCatalogExportCommand(
    val output: Path,
    val limit: Int = DEFAULT_SEARCH_CATALOG_EXPORT_LIMIT,
    val catalogId: String? = null
)
