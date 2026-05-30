package com.sigak.search.vector

interface ArticleVectorProjectionIndexer {
    fun collectionName(): String

    fun deleteCollectionIfExists()

    fun recreateCollection(dimension: Int)

    fun upsertAll(documents: List<ArticleVectorDocument>)

    fun search(vector: List<Double>, limit: Int): List<ArticleVectorSearchHit>
}
