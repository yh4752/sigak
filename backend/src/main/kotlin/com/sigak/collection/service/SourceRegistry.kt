package com.sigak.collection.service

import com.sigak.collection.domain.NewsSource
import com.sigak.source.service.SourceStore
import org.springframework.stereotype.Component

@Component
class SourceRegistry(private val store: SourceStore) {
    fun sources(): List<NewsSource> = store.all().filter { it.collectable() }.map { it.asNewsSource() }
    fun knownIds(): Set<String> = store.all().map { it.key }.toSet()
}
