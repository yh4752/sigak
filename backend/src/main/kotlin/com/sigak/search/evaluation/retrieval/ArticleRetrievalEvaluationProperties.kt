package com.sigak.search.evaluation.retrieval

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "sigak.internal.search-evaluation")
data class ArticleRetrievalEvaluationProperties(
    val enabled: Boolean = false
)
