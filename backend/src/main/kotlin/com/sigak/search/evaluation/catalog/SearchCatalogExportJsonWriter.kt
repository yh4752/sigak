package com.sigak.search.evaluation.catalog

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import org.springframework.stereotype.Component

@Component
class SearchCatalogExportJsonWriter(
    private val objectMapper: ObjectMapper
) {

    fun write(output: Path, catalog: SearchCatalog) {
        output.parent?.let { parent ->
            Files.createDirectories(parent)
        }

        objectMapper
            .writerWithDefaultPrettyPrinter()
            .writeValue(output.toFile(), catalog)
    }
}
