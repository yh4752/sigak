package com.sigak.collection.runner

import org.springframework.stereotype.Component

interface CollectionRunCommandOutput {
    fun writeLine(value: String)

    fun writeErrorLine(value: String)
}

@Component
class SystemCollectionRunCommandOutput : CollectionRunCommandOutput {

    override fun writeLine(value: String) {
        println(value)
    }

    override fun writeErrorLine(value: String) {
        System.err.println(value)
    }
}
