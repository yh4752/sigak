package com.sigak.collection.service

import com.sigak.collection.dto.CollectionFailureStage

class SourceCollectionException(
    val stage: CollectionFailureStage,
    message: String,
    cause: Throwable
) : RuntimeException(message, cause)
