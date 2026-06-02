package com.sigak.article.repository

import com.sigak.article.domain.ArticleEntity
import com.sigak.article.domain.ProcessingStatus
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ArticleRepository : JpaRepository<ArticleEntity, Long>, ArticleResponseGraphRepository {
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
        where article.id in :ids
            and article.processingStatus = :status
            and enrichment.current = true
        """
    )
    fun findApiReadyArticlesByIdIn(
        @Param("ids") ids: Collection<Long>,
        @Param("status") status: ProcessingStatus
    ): List<ArticleEntity>

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

    fun findFirstByCanonicalUrlOrUrlOrderByIdAsc(canonicalUrl: String, url: String): ArticleEntity?

    fun findFirstBySourceSourceKeyAndExternalIdOrderByIdAsc(sourceKey: String, externalId: String): ArticleEntity?

    fun findFirstBySourceSourceKeyAndTitleIgnoreCaseAndPublishedAtOrderByIdAsc(
        sourceKey: String,
        title: String,
        publishedAt: java.time.Instant
    ): ArticleEntity?

}
