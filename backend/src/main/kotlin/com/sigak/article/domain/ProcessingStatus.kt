package com.sigak.article.domain

enum class ProcessingStatus {
    DISCOVERED,
    FETCHED,
    EXTRACTED,
    NORMALIZED,
    ENRICHED,
    PUBLISHED,
    FAILED
}
