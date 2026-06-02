package com.sigak.article.repository

import com.sigak.article.domain.ArticleEntity
import jakarta.persistence.EntityManager

class ArticleResponseGraphRepositoryImpl(
    private val entityManager: EntityManager
) : ArticleResponseGraphRepository {

    override fun fetchArticleResponseGraph(articles: List<ArticleEntity>) {
        // 응답 변환 시 lazy relation 접근으로 N+1 쿼리가 발생하지 않도록 필요한 그래프를 먼저 로드한다.
        val ids = articles.map { article -> requireNotNull(article.id) }
        if (ids.isEmpty()) {
            return
        }

        fetchArticles(
            """
            select distinct article
            from ArticleEntity article
            left join fetch article.enrichments
            where article.id in :ids
            """.trimIndent(),
            ids
        )
        fetchArticles(
            """
            select distinct article
            from ArticleEntity article
            left join fetch article.topics
            where article.id in :ids
            """.trimIndent(),
            ids
        )
        fetchArticles(
            """
            select distinct article
            from ArticleEntity article
            left join fetch article.outgoingRelations relation
            left join fetch relation.targetArticle
            where article.id in :ids
            """.trimIndent(),
            ids
        )
    }

    private fun fetchArticles(query: String, ids: Collection<Long>): List<ArticleEntity> =
        entityManager.createQuery(query, ArticleEntity::class.java)
            .setParameter("ids", ids)
            .resultList
}
