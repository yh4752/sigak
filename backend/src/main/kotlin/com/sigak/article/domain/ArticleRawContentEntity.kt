package com.sigak.article.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "article_raw_contents")
class ArticleRawContentEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "article_id", nullable = false, unique = true)
    var article: ArticleEntity = ArticleEntity(),

    @Column(name = "raw_content", nullable = false, columnDefinition = "text")
    var rawContent: String = "",

    @Column(name = "extracted_text", nullable = false, columnDefinition = "text")
    var extractedText: String = "",

    @Column(name = "collected_at", nullable = false)
    var collectedAt: Instant = Instant.EPOCH
)
