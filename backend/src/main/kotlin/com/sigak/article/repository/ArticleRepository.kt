package com.sigak.article.repository

import com.sigak.article.domain.ArticleEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ArticleRepository : JpaRepository<ArticleEntity, Long> {
    fun findAllByOrderByImportanceScoreDescPublishedAtDescIdAsc(): List<ArticleEntity>
}
