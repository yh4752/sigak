package com.sigak.article.repository

import com.sigak.article.domain.ArticleEntity
import com.sigak.article.domain.ProcessingStatus
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ArticleRepository : JpaRepository<ArticleEntity, Long> {
    @EntityGraph(attributePaths = ["source"])
    @Query(
        """
        select distinct article
        from ArticleEntity article
        join article.enrichments enrichment
        where article.processingStatus = :status
            and enrichment.current = true
        order by article.importanceScore desc, article.publishedAt desc, article.id asc
        """
    )
    fun findApiReadyArticles(@Param("status") status: ProcessingStatus): List<ArticleEntity>

    @EntityGraph(attributePaths = ["source"])
    @Query(
        """
        select distinct article
        from ArticleEntity article
        join article.enrichments enrichment
        where article.id = :id
            and article.processingStatus = :status
            and enrichment.current = true
        """
    )
    fun findApiReadyWithSourceById(
        @Param("id") id: Long,
        @Param("status") status: ProcessingStatus
    ): ArticleEntity?

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
