package com.sigak.source.service

import com.sigak.collection.domain.NewsSource
import com.sigak.collection.domain.SourceType
import java.time.Instant

data class SourceDefinition(
    val id: Long,
    val key: String,
    val name: String,
    val type: SourceType,
    val siteUrl: String?,
    val feedUrl: String?,
    val categoryHint: String?,
    val enabled: Boolean,
    val validationStatus: String,
    val allowedHosts: Set<String>,
    val bodyMode: String,
    val feedContentVerified: Boolean,
    val bodySelector: String?,
    val excludeSelectors: List<String>,
    val version: Long,
    val validatedVersion: Long?,
    val validatedAt: Instant?,
    val report: Map<String, Any?>,
    val lastCollectionAt: Instant? = null,
    val lastCollectionError: String? = null
) {
    fun collectable(): Boolean = enabled && validationStatus == "PASSED" && validatedVersion == version && feedUrl != null
    fun bodyApproved(): Boolean = validationStatus == "PASSED" && validatedVersion == version && type == SourceType.RSS_ATOM
    fun useFeed(): Boolean = bodyMode == "FEED_FULL" || (bodyMode == "AUTO" && feedContentVerified)
    fun asNewsSource(): NewsSource = NewsSource(key, name, type, requireNotNull(feedUrl), categoryHint, version)
}

class SourceOperationException(val code: String) : RuntimeException(code)
