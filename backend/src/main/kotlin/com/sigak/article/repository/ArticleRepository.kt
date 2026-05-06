package com.sigak.article.repository

import com.sigak.article.domain.ArticleEntity
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ArticleRepository : JpaRepository<ArticleEntity, Long> {
    @EntityGraph(attributePaths = ["source"])
    fun findAllByOrderByImportanceScoreDescPublishedAtDescIdAsc(): List<ArticleEntity>

    @EntityGraph(attributePaths = ["source"])
    @Query("select article from ArticleEntity article where article.id = :id")
    fun findWithSourceById(@Param("id") id: Long): ArticleEntity?

    @Query(
        """
        select distinct article
        from ArticleEntity article
        left join fetch article.enrichments
        where article.id in :ids
        """
    )
    fun fetchEnrichmentsByArticleIdIn(@Param("ids") ids: Collection<Long>): List<ArticleEntity>

    @Query(
        """
        select distinct article
        from ArticleEntity article
        left join fetch article.topics
        where article.id in :ids
        """
    )
    fun fetchTopicsByArticleIdIn(@Param("ids") ids: Collection<Long>): List<ArticleEntity>

    @Query(
        """
        select distinct article
        from ArticleEntity article
        left join fetch article.outgoingRelations relation
        left join fetch relation.targetArticle
        where article.id in :ids
        """
    )
    fun fetchOutgoingRelationsByArticleIdIn(@Param("ids") ids: Collection<Long>): List<ArticleEntity>
}
