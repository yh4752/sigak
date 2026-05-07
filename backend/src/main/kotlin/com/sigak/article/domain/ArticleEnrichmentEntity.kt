package com.sigak.article.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "article_enrichments")
class ArticleEnrichmentEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "article_id", nullable = false)
    var article: ArticleEntity = ArticleEntity(),

    @Column(nullable = false, columnDefinition = "text")
    var summary: String = "",

    @Column(name = "why_it_matters", nullable = false, columnDefinition = "text")
    var whyItMatters: String = "",

    @Column(name = "suggested_primary_category", length = 80)
    var suggestedPrimaryCategory: String? = null,

    @Column(name = "suggested_importance_score")
    var suggestedImportanceScore: Int? = null,

    @Column(name = "model_name", nullable = false, length = 120)
    var modelName: String = "",

    @Column(name = "prompt_version", nullable = false, length = 80)
    var promptVersion: String = "",

    @Column(name = "is_current", nullable = false)
    var current: Boolean = false,

    @Column(name = "enriched_at", nullable = false)
    var enrichedAt: Instant = Instant.EPOCH
)
