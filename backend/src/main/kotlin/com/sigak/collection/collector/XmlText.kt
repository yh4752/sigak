package com.sigak.collection.collector

import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

internal fun secureDocumentBuilderFactory(namespaceAware: Boolean = false): DocumentBuilderFactory =
    DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = namespaceAware
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        isExpandEntityReferences = false
    }

internal fun Element.firstText(tagName: String): String? {
    val nodes = getElementsByTagName(tagName)
    if (nodes.length == 0) {
        return null
    }
    return nodes.item(0).textContent?.trim()?.takeIf { it.isNotBlank() }
}

internal fun Node.asElement(): Element? = this as? Element
