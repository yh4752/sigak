package com.sigak.search.evaluation.catalog

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.springframework.stereotype.Service

@Service
class SearchCatalogExportService(
    private val articleReader: SearchCatalogArticleReader,
    private val catalogFactory: SearchCatalogFactory
) {

    fun export(command: SearchCatalogExportCommand): SearchCatalog =
        catalogFactory.build(
            articles = articleReader.readApiReadyArticles(),
            catalogId = command.catalogId ?: defaultCatalogId(),
            generatedAt = Instant.now(),
            limit = command.limit
        )

    private fun defaultCatalogId(): String =
        "api-ready-${LocalDate.now(ZoneOffset.UTC)}"
}
