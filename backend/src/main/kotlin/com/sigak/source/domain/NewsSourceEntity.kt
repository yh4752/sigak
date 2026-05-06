package com.sigak.source.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "news_sources")
class NewsSourceEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "source_key", nullable = false, unique = true, length = 120)
    var sourceKey: String = "",

    @Column(nullable = false, length = 255)
    var name: String = "",

    @Column(nullable = false, length = 40)
    var type: String = "",

    @Column(nullable = false, columnDefinition = "text")
    var url: String = "",

    @Column(name = "category_hint", length = 80)
    var categoryHint: String? = null
)
