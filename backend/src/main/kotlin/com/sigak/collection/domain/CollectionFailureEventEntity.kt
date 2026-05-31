package com.sigak.collection.domain

import com.sigak.collection.dto.CollectionFailureStage
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "collection_failure_events")
class CollectionFailureEventEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "run_id", nullable = false)
    var runId: UUID = UUID(0, 0),

    @Column(name = "source_key", nullable = false, length = 120)
    var sourceKey: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 60)
    var stage: CollectionFailureStage = CollectionFailureStage.PUBLISH_ARTICLE,

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_kind", nullable = false, length = 80)
    var failureKind: CollectionFailureKind = CollectionFailureKind.UNKNOWN,

    @Column(nullable = false)
    var retryable: Boolean = false,

    @Column(nullable = false, columnDefinition = "text")
    var message: String = "",

    @Column(nullable = false, length = 160)
    var fingerprint: String = "",

    @Column(name = "article_external_id", length = 255)
    var articleExternalId: String? = null,

    @Column(name = "article_url", columnDefinition = "text")
    var articleUrl: String? = null,

    @Column(name = "article_title", length = 500)
    var articleTitle: String? = null,

    @Column(name = "occurred_at", nullable = false)
    var occurredAt: Instant = Instant.EPOCH
)
