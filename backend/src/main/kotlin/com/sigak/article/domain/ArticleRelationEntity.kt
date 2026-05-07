package com.sigak.article.domain

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
import jakarta.persistence.Table

@Entity
@Table(name = "article_relations")
class ArticleRelationEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_article_id", nullable = false)
    var sourceArticle: ArticleEntity = ArticleEntity(),

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_article_id", nullable = false)
    var targetArticle: ArticleEntity = ArticleEntity(),

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type", nullable = false, length = 80)
    var relationType: RelationType = RelationType.RELATED,

    @Column(columnDefinition = "text")
    var reason: String? = null
)
