package com.sigak.search.evaluation.catalog

import org.springframework.stereotype.Component

interface SearchCatalogExportCommandOutput {
    fun writeLine(value: String)

    fun writeErrorLine(value: String)
}

@Component
class SystemSearchCatalogExportCommandOutput : SearchCatalogExportCommandOutput {

    override fun writeLine(value: String) {
        println(value)
    }

    override fun writeErrorLine(value: String) {
        System.err.println(value)
    }
}
