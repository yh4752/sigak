package com.sigak.search.graph

import org.neo4j.driver.Driver
import org.neo4j.driver.Record
import org.neo4j.driver.Session
import org.neo4j.driver.TransactionContext
import org.springframework.stereotype.Component

@Component
class Neo4jArticleGraphProjectionIndexer(
    private val driver: Driver
) : ArticleGraphProjectionIndexer {

    override fun rebuild(documents: List<ArticleGraphProjectionDocument>): ArticleGraphProjectionIndexResult {
        ensureConstraints()

        withSession { session ->
            session.executeWrite { transaction ->
                transaction.run(DELETE_PROJECTION_CYPHER)
                documents.forEach { document -> transaction.createArticle(document) }
                documents.flatMap { document -> document.topics.map { topic -> document.articleId to topic } }
                    .forEach { (articleId, topic) -> transaction.mergeTopic(articleId, topic) }
                documents.flatMap { document -> document.outgoingRelations }
                    .forEach { relation -> transaction.mergeRelation(relation) }
            }
        }

        return ArticleGraphProjectionIndexResult(
            articleNodeCount = documents.size,
            topicNodeCount = documents.flatMap { document -> document.topics }.map { topic -> topic.name }.toSet().size,
            hasTopicRelationshipCount = documents
                .flatMap { document -> document.topics.map { topic -> document.articleId to topic.name } }
                .toSet()
                .size,
            relatedToRelationshipCount = documents
                .flatMap { document ->
                    document.outgoingRelations.map { relation ->
                        relation.sourceArticleId to relation.targetArticleId
                    }
                }
                .toSet()
                .size
        )
    }

    override fun findContext(articleId: Long): ArticleGraphContextProjection? =
        withSession { session ->
            val record = session.run(CONTEXT_CYPHER, mapOf("articleId" to articleId)).single()
            if (!record.get("articleExists").asBoolean()) {
                null
            } else {
                ArticleGraphContextProjection(
                    articleId = articleId,
                    topics = record.toTopics(),
                    relatedArticles = record.toRelatedArticles()
                )
            }
        }

    private fun ensureConstraints() {
        withSession { session ->
            session.run(ARTICLE_ID_CONSTRAINT_CYPHER)
            session.run(TOPIC_NAME_CONSTRAINT_CYPHER)
        }
    }

    private fun TransactionContext.createArticle(document: ArticleGraphProjectionDocument) {
        run(
            CREATE_ARTICLE_CYPHER,
            mapOf(
                "articleId" to document.articleId,
                "title" to document.title,
                "source" to document.source,
                "url" to document.url,
                "publishedAt" to document.publishedAt,
                "eventType" to document.eventType,
                "primaryCategory" to document.primaryCategory,
                "importanceScore" to document.importanceScore
            )
        )
    }

    private fun TransactionContext.mergeTopic(articleId: Long, topic: ArticleGraphTopicDocument) {
        run(
            MERGE_TOPIC_CYPHER,
            mapOf(
                "articleId" to articleId,
                "name" to topic.name,
                "displayName" to topic.displayName,
                "position" to topic.position
            )
        )
    }

    private fun TransactionContext.mergeRelation(relation: ArticleGraphRelationDocument) {
        run(
            MERGE_RELATED_TO_CYPHER,
            mapOf(
                "sourceArticleId" to relation.sourceArticleId,
                "targetArticleId" to relation.targetArticleId,
                "relationType" to relation.relationType,
                "reason" to relation.reason
            )
        )
    }

    private fun Record.toTopics(): List<ArticleGraphTopicContextProjection> =
        get("topics").asList { value ->
            val topic = value.asMap()
            ArticleGraphTopicContextProjection(
                name = topic.getRequiredString("name"),
                displayName = topic.getRequiredString("displayName"),
                relatedArticleIds = topic.getLongList("relatedArticleIds")
            )
        }

    private fun Record.toRelatedArticles(): List<ArticleGraphRelatedArticleProjection> =
        get("relatedArticles").asList { value ->
            val relatedArticle = value.asMap()
            ArticleGraphRelatedArticleProjection(
                articleId = relatedArticle.getRequiredLong("articleId"),
                title = relatedArticle.getRequiredString("title"),
                relationType = relatedArticle.getRequiredString("relationType"),
                reason = relatedArticle["reason"] as String?,
                sharedTopics = relatedArticle.getStringList("sharedTopics")
            )
        }

    private fun <T> withSession(block: (Session) -> T): T =
        driver.session().use(block)

    private fun Map<String, Any>.getRequiredString(key: String): String =
        this[key] as String

    private fun Map<String, Any>.getRequiredLong(key: String): Long =
        (this[key] as Number).toLong()

    private fun Map<String, Any>.getLongList(key: String): List<Long> =
        getValueList(key).map { value -> (value as Number).toLong() }

    private fun Map<String, Any>.getStringList(key: String): List<String> =
        getValueList(key).map { value -> value as String }

    private fun Map<String, Any>.getValueList(key: String): List<*> =
        this[key] as List<*>

    private companion object {
        val ARTICLE_ID_CONSTRAINT_CYPHER = """
            CREATE CONSTRAINT sigak_article_article_id IF NOT EXISTS
            FOR (article:Article)
            REQUIRE article.articleId IS UNIQUE
        """.trimIndent()

        val TOPIC_NAME_CONSTRAINT_CYPHER = """
            CREATE CONSTRAINT sigak_topic_name IF NOT EXISTS
            FOR (topic:Topic)
            REQUIRE topic.name IS UNIQUE
        """.trimIndent()

        val DELETE_PROJECTION_CYPHER = """
            MATCH (node)
            WHERE node:Article OR node:Topic
            DETACH DELETE node
        """.trimIndent()

        val CREATE_ARTICLE_CYPHER = """
            CREATE (article:Article {
                articleId: ${'$'}articleId,
                title: ${'$'}title,
                source: ${'$'}source,
                url: ${'$'}url,
                publishedAt: ${'$'}publishedAt,
                eventType: ${'$'}eventType,
                primaryCategory: ${'$'}primaryCategory,
                importanceScore: ${'$'}importanceScore
            })
        """.trimIndent()

        val MERGE_TOPIC_CYPHER = """
            MATCH (article:Article {articleId: ${'$'}articleId})
            MERGE (topic:Topic {name: ${'$'}name})
            ON CREATE SET topic.displayName = ${'$'}displayName
            MERGE (article)-[hasTopic:HAS_TOPIC]->(topic)
            SET hasTopic.position = ${'$'}position
        """.trimIndent()

        val MERGE_RELATED_TO_CYPHER = """
            MATCH (source:Article {articleId: ${'$'}sourceArticleId})
            MATCH (target:Article {articleId: ${'$'}targetArticleId})
            MERGE (source)-[relatedTo:RELATED_TO]->(target)
            SET relatedTo.relationType = ${'$'}relationType,
                relatedTo.reason = ${'$'}reason
        """.trimIndent()

        val CONTEXT_CYPHER = """
            OPTIONAL MATCH (article:Article {articleId: ${'$'}articleId})
            WITH article, article IS NOT NULL AS articleExists
            OPTIONAL MATCH (article)-[articleTopic:HAS_TOPIC]->(topic:Topic)
            OPTIONAL MATCH (topic)<-[:HAS_TOPIC]-(topicArticle:Article)
            WITH article,
                articleExists,
                topic,
                topic.name AS name,
                topic.displayName AS displayName,
                articleTopic.position AS position,
                [
                    relatedArticleId IN collect(DISTINCT topicArticle.articleId)
                    WHERE relatedArticleId IS NOT NULL AND relatedArticleId <> article.articleId
                ] AS relatedArticleIds
            ORDER BY position ASC, displayName ASC
            WITH article,
                articleExists,
                [
                    topicEntry IN collect({
                        name: name,
                        displayName: displayName,
                        relatedArticleIds: relatedArticleIds
                    })
                    WHERE topicEntry.name IS NOT NULL
                ] AS topics
            OPTIONAL MATCH (article)-[relation:RELATED_TO]->(related:Article)
            OPTIONAL MATCH (article)-[:HAS_TOPIC]->(sharedTopic:Topic)<-[:HAS_TOPIC]-(related)
            WITH article,
                articleExists,
                topics,
                related,
                relation,
                [
                    sharedTopicName IN collect(DISTINCT sharedTopic.displayName)
                    WHERE sharedTopicName IS NOT NULL
                ] AS sharedTopics
            ORDER BY related.articleId ASC
            RETURN articleExists AS articleExists,
                topics AS topics,
                [
                    relatedArticle IN collect({
                        articleId: related.articleId,
                        title: related.title,
                        relationType: relation.relationType,
                        reason: relation.reason,
                        sharedTopics: sharedTopics
                    })
                    WHERE relatedArticle.articleId IS NOT NULL
                ] AS relatedArticles
        """.trimIndent()
    }
}
