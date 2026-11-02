package com.sigak.support

import com.sigak.collection.domain.SourceType
import com.sigak.source.service.SourceDefinition
import com.sigak.source.service.SourceNetworkGate
import java.time.Instant

fun sourceDefinitionFixture(key: String = "example-feed", host: String = "engineering.example.test", version: Long = 1): SourceDefinition = SourceDefinition(
    id=99,key=key,name="Engineering",type=SourceType.RSS_ATOM,siteUrl="https://$host/",feedUrl="https://$host/feed",
    categoryHint="SOFTWARE_ENGINEERING",enabled=true,validationStatus="PASSED",allowedHosts=setOf(host),bodyMode="PAGE",
    feedContentVerified=false,bodySelector=null,excludeSelectors=emptyList(),version=version,validatedVersion=version,
    validatedAt=Instant.parse("2026-11-02T00:00:00Z"),report=emptyMap()
)

fun passThroughSourceNetworkGate(): SourceNetworkGate = object : SourceNetworkGate {
    override fun <T> withPermit(key:String,action:()->T):T = action()
}
