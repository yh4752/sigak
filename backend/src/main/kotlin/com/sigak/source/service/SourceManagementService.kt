package com.sigak.source.service

import com.sigak.article.domain.PrimaryCategory
import com.sigak.common.http.PublicUrlPolicy
import java.time.Clock
import java.time.Duration
import org.jsoup.Jsoup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service


data class SourcePatch(
    val name: String? = null, val feedUrl: String? = null, val categoryHint: String? = null,
    val allowedHosts: Set<String>? = null, val bodyMode: String? = null, val bodySelector: String? = null,
    val excludeSelectors: List<String>? = null
)
data class SourceCheckOutput(val source: SourceDefinition, val samples: List<SourceSample>)

@Service
class SourceManagementService internal constructor(
    private val store: SourceStore, private val probe: SourceProbe, private val gate: SourceNetworkGate, private val clock: Clock
) {
    @Autowired constructor(store: SourceStore, probe: SourceProbe, gate: SourceNetworkGate) : this(store,probe,gate,Clock.systemUTC())

    fun register(url: String): SourceDefinition {
        if (url.length > 4096) throw SourceOperationException("URL_TOO_LONG")
        val canonical = PublicUrlPolicy.canonical(url.trim())
        val source = store.register(canonical,PublicUrlPolicy.host(canonical))
        val existingKey = source.report["existingSourceKey"] as? String
        return if (source.report["reason"] == "FEED_ALREADY_REGISTERED" && existingKey != null) store.find(existingKey) ?: source else source
    }

    fun check(key: String, expectedVersion: Long): SourceCheckOutput {
        val original = current(key,expectedVersion)
        return gate.withPermit(key) {
            val result = probe.check(original)
            val commit = {
                val existing = result.feedUrl?.takeIf { result.requiredHosts.isEmpty() }?.let { store.findByFeed(it) }
                if (existing != null && existing.id != original.id) {
                    // 다른 URL의 동일 feed는 기존 운영 설정을 덮어쓰지 않고 그 출처 ID로 모은다.
                    store.save(original.copy(enabled=false,version=original.version+1,validationStatus="NEEDS_CONFIG",validatedVersion=null,
                        report=mapOf("reason" to "FEED_ALREADY_REGISTERED","existingSourceKey" to existing.key)),original.version)
                    SourceCheckOutput(existing,emptyList())
                } else {
                val changedFeed = result.feedUrl != null && result.feedUrl != original.feedUrl
                val version = original.version + 1
                val updated = original.copy(feedUrl=if (result.status == "PASSED" || (result.feedUrl != null && result.requiredHosts.isEmpty())) result.feedUrl else original.feedUrl,
                    name=if (original.name == original.siteUrl?.let { PublicUrlPolicy.host(it) }) result.name ?: original.name else original.name,
                    enabled=false,validationStatus=result.status,version=version,validatedVersion=if(result.status=="PASSED") version else null,
                    validatedAt=clock.instant(),feedContentVerified=if(changedFeed) false else original.feedContentVerified,report=result.report())
                SourceCheckOutput(store.save(updated,original.version),result.samples)
                }
            }
            if (result.feedUrl != null && result.requiredHosts.isEmpty()) store.withFeedLock(result.feedUrl,commit) else commit()
        }
    }

    fun configure(key: String, expectedVersion: Long, patch: SourcePatch): SourceDefinition = store.withLockedSource(key,expectedVersion) { original ->
        val name = patch.name?.trim() ?: original.name
        if (name.isBlank() || name.length > 255) throw SourceOperationException("INVALID_SOURCE_NAME")
        val hosts = patch.allowedHosts?.map { PublicUrlPolicy.host("https://${it.trim().lowercase()}/") }?.toSet() ?: original.allowedHosts
        if (hosts.isEmpty() || hosts.size > 8) throw SourceOperationException("INVALID_ALLOWED_HOSTS")
        val feed = patch.feedUrl?.takeIf { it.isNotBlank() }?.let { PublicUrlPolicy.requireUrl(it,hosts).toString() } ?: original.feedUrl
        val mode = patch.bodyMode ?: original.bodyMode
        if (mode !in setOf("AUTO","PAGE","FEED_FULL")) throw SourceOperationException("INVALID_BODY_MODE")
        val category = patch.categoryHint ?: original.categoryHint
        if (category != null && PrimaryCategory.entries.none { it.name == category }) throw SourceOperationException("INVALID_CATEGORY")
        val selector = patch.bodySelector?.takeIf { it.isNotBlank() } ?: if (patch.bodySelector==null) original.bodySelector else null
        val excludes = patch.excludeSelectors ?: original.excludeSelectors
        if ((selector?.length ?: 0) > 2048 || excludes.size > 16 || excludes.any { it.length > 2048 }) throw SourceOperationException("INVALID_SELECTOR")
        try { (listOfNotNull(selector)+excludes).forEach { Jsoup.parse("<main></main>").select(it) } }
        catch (_: org.jsoup.select.Selector.SelectorParseException) { throw SourceOperationException("INVALID_SELECTOR") }
        val changed = feed != original.feedUrl || hosts != original.allowedHosts || mode != original.bodyMode || selector != original.bodySelector || excludes != original.excludeSelectors || category != original.categoryHint
        val version = original.version+1
        store.save(original.copy(name=name,feedUrl=feed,allowedHosts=hosts,bodyMode=mode,bodySelector=selector,excludeSelectors=excludes,categoryHint=category,
            version=version,enabled=if(changed) false else original.enabled,validationStatus=if(changed) "UNVERIFIED" else original.validationStatus,
            validatedVersion=if(changed || original.validatedVersion!=original.version) null else version,
            feedContentVerified=if(changed) false else original.feedContentVerified,report=if(changed) emptyMap() else original.report),original.version)
    }

    fun enable(key: String, expectedVersion: Long, reviewed: Boolean, feedReviewed: Boolean = false): SourceDefinition = store.withLockedSource(key,expectedVersion) { source ->
        if (!reviewed) throw SourceOperationException("SAMPLE_REVIEW_REQUIRED")
        if (source.validationStatus != "PASSED" || source.validatedVersion != source.version || source.feedUrl == null) throw SourceOperationException("SOURCE_NOT_VALIDATED")
        val validated = source.validatedAt ?: throw SourceOperationException("SOURCE_NOT_VALIDATED")
        val age = Duration.between(validated,clock.instant())
        if (age.isNegative || age > Duration.ofHours(24)) throw SourceOperationException("SOURCE_VALIDATION_EXPIRED")
        if ((feedReviewed || source.bodyMode=="FEED_FULL") && source.report["feedSamplesValid"] != true) throw SourceOperationException("FEED_SAMPLE_REVIEW_REQUIRED")
        if ((feedReviewed || source.bodyMode=="FEED_FULL") && ((source.report["feedSummarizableSampleCount"] as? Number)?.toInt() ?: 0) < 1) throw SourceOperationException("FEED_NO_SUMMARIZABLE_SAMPLE")
        val version = source.version+1
        store.save(source.copy(enabled=true,version=version,validatedVersion=version,
            feedContentVerified=source.feedContentVerified || feedReviewed || source.bodyMode=="FEED_FULL",
            report=source.report + mapOf("reviewedAt" to clock.instant().toString(),"feedReviewed" to (feedReviewed || source.bodyMode=="FEED_FULL"))),source.version)
    }

    fun disable(key: String, expectedVersion: Long): SourceDefinition = store.withLockedSource(key,expectedVersion) { source ->
        val version = source.version+1
        store.save(source.copy(enabled=false,version=version,validatedVersion=if(source.validatedVersion==source.version) version else null),source.version)
    }

    fun list(): List<SourceDefinition> = store.all()
    private fun current(key: String, version: Long): SourceDefinition = store.require(key).also { if(it.version!=version) throw SourceOperationException("SOURCE_VERSION_CHANGED") }
}
