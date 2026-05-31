package com.sigak.collection.domain

enum class CollectionFailureKind {
    TRANSIENT_FETCH,
    SOURCE_FORMAT,
    INVALID_ARTICLE,
    PERSISTENCE,
    UNKNOWN
}
