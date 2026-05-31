package com.sigak.collection.service

enum class CollectedArticlePublishOutcome {
    PUBLISHED,
    SKIPPED_DUPLICATE
}

data class CollectedArticlePublishResult(
    val articleId: Long,
    val outcome: CollectedArticlePublishOutcome
)
