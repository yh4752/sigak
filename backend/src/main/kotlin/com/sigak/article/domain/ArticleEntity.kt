package com.sigak.article.domain

import com.sigak.source.domain.NewsSourceEntity
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "articles")
class ArticleEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    var source: NewsSourceEntity = NewsSourceEntity(),

    @Column(name = "external_id", length = 255)
    var externalId: String? = null,

    @Column(nullable = false, length = 500)
    var title: String = "",

    @Column(nullable = false, columnDefinition = "text")
    var url: String = "",

    @Column(name = "canonical_url", nullable = false, columnDefinition = "text")
    var canonicalUrl: String = "",

    @Column(name = "published_at", nullable = false)
    var publishedAt: Instant = Instant.EPOCH,

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 60)
    var eventType: EventType = EventType.NEWS,

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_category", nullable = false, length = 80)
    var primaryCategory: PrimaryCategory = PrimaryCategory.AI,

    @Column(name = "importance_score", nullable = false)
    var importanceScore: Int = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 60)
    var processingStatus: ProcessingStatus = ProcessingStatus.DISCOVERED,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.EPOCH
) {
    @OneToOne(mappedBy = "article", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var rawContent: ArticleRawContentEntity? = null

    @OneToMany(mappedBy = "article", cascade = [CascadeType.ALL], orphanRemoval = true)
    var enrichments: MutableList<ArticleEnrichmentEntity> = mutableListOf()

    @OneToMany(mappedBy = "article", cascade = [CascadeType.ALL], orphanRemoval = true)
    var topics: MutableList<ArticleTopicEntity> = mutableListOf()

    @OneToMany(mappedBy = "sourceArticle", cascade = [CascadeType.ALL], orphanRemoval = true)
    var outgoingRelations: MutableList<ArticleRelationEntity> = mutableListOf()
}
