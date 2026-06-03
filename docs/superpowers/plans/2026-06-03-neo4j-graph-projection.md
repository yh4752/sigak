# Neo4j Graph Projection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** PostgreSQL API-ready article/topic/relation metadata를 Neo4j에 재생성 가능한 graph projection으로 만들고, internal graph context API로 relation reason과 topic context를 조회한다.

**Architecture:** PostgreSQL remains the source of truth. Neo4j is a rebuildable projection store, like Elasticsearch and Qdrant. Public article response shape does not change in this implementation; graph data is verified through internal endpoints first.

**Tech Stack:** Kotlin, Spring Boot, Spring MVC, JPA, Neo4j Java Driver, MockMvc, Kotlin test, Docker Compose, PostgreSQL, Neo4j.

---

## Reference Context

- Spec: `docs/superpowers/specs/2026-06-03-neo4j-graph-projection-design.md`
- Project rules: `AGENTS.md`
- Coding rules: `docs/CODING_CONVENTIONS.md`
- Existing Neo4j config:
  - `backend/src/main/kotlin/com/sigak/search/config/SearchInfrastructureProperties.kt`
  - `backend/src/main/kotlin/com/sigak/search/config/SearchInfrastructureConfig.kt`
  - `backend/src/main/resources/application.yml`
- Existing article graph metadata:
  - `backend/src/main/kotlin/com/sigak/article/domain/ArticleEntity.kt`
  - `backend/src/main/kotlin/com/sigak/article/domain/ArticleTopicEntity.kt`
  - `backend/src/main/kotlin/com/sigak/article/domain/ArticleRelationEntity.kt`
  - `backend/src/main/kotlin/com/sigak/article/domain/RelationType.kt`
  - `backend/src/main/kotlin/com/sigak/article/repository/ArticleRepository.kt`
  - `backend/src/main/kotlin/com/sigak/article/repository/ArticleResponseGraphRepository.kt`
  - `backend/src/main/kotlin/com/sigak/article/repository/ArticleResponseGraphRepositoryImpl.kt`
- Existing projection examples:
  - `backend/src/main/kotlin/com/sigak/search/projection/`
  - `backend/src/main/kotlin/com/sigak/search/vector/`

## Worktree Guard

- Run `git status --short --branch` before implementation.
- The spec file `docs/superpowers/specs/2026-06-03-neo4j-graph-projection-design.md` is currently expected to be uncommitted.
- Do not revert user or parallel-session changes.
- Do not run `git commit` or `git push` unless the user explicitly asks for it.
- If files unrelated to this plan are modified, ignore them unless this plan touches the same file.

## Scope Guard

This implementation includes:

- Neo4j graph projection documents.
- PostgreSQL projection reader.
- Neo4j projection indexer.
- Rebuild service and internal rebuild endpoint.
- Graph context query service and internal context endpoint.
- API docs, status/roadmap, dev-log, topic queue.
- Backend tests and local Neo4j smoke verification.

This implementation does not include:

- Public article detail response changes.
- Frontend graph UI.
- Graph explorer.
- GraphRAG chatbot.
- LLM relation extraction.
- Scheduled rebuild.
- Topic synonym or multilingual normalization beyond trim + lowercase.
- Testcontainers Neo4j integration test.

## File Structure

Create:

- `backend/src/main/kotlin/com/sigak/search/graph/ArticleGraphProjectionDocument.kt`
  - Projection document DTOs used between PostgreSQL reader and Neo4j indexer.
- `backend/src/main/kotlin/com/sigak/search/graph/ArticleGraphProjectionReader.kt`
  - Reads API-ready articles and converts topics/relations into graph projection documents.
- `backend/src/main/kotlin/com/sigak/search/graph/ArticleGraphProjectionIndexer.kt`
  - Interface for rebuild and context operations.
- `backend/src/main/kotlin/com/sigak/search/graph/Neo4jArticleGraphProjectionIndexer.kt`
  - Neo4j Java Driver implementation. Owns Cypher.
- `backend/src/main/kotlin/com/sigak/search/graph/ArticleGraphProjectionRebuildResponse.kt`
  - Rebuild endpoint response.
- `backend/src/main/kotlin/com/sigak/search/graph/ArticleGraphProjectionRebuildService.kt`
  - Orchestrates reader + indexer rebuild and failure response.
- `backend/src/main/kotlin/com/sigak/search/graph/ArticleGraphProjectionController.kt`
  - Thin internal rebuild controller.
- `backend/src/main/kotlin/com/sigak/search/graph/ArticleGraphContextResponse.kt`
  - Internal graph context response DTOs.
- `backend/src/main/kotlin/com/sigak/search/graph/ArticleGraphContextService.kt`
  - Reads article graph context from Neo4j through the indexer.
- `backend/src/main/kotlin/com/sigak/search/graph/ArticleGraphContextController.kt`
  - Thin internal graph context controller.

Create tests:

- `backend/src/test/kotlin/com/sigak/search/graph/ArticleGraphProjectionReaderTest.kt`
- `backend/src/test/kotlin/com/sigak/search/graph/ArticleGraphProjectionRebuildServiceTest.kt`
- `backend/src/test/kotlin/com/sigak/search/graph/ArticleGraphProjectionControllerTest.kt`
- `backend/src/test/kotlin/com/sigak/search/graph/ArticleGraphContextServiceTest.kt`
- `backend/src/test/kotlin/com/sigak/search/graph/ArticleGraphContextControllerTest.kt`
- `backend/src/test/kotlin/com/sigak/docs/OpenApiDocumentationTest.kt`

Modify:

- `docs/API_SPEC.md`
- `docs/API_SPEC.ko.md`
- `docs/STATUS.md`
- `docs/STATUS.ko.md`
- `docs/ROADMAP.md`
- `docs/ROADMAP.ko.md`
- `docs/blog/2026-06-03-dev-log.md`
- `docs/blog/topic-queue.md`

Do not modify in this plan:

- `frontend/`
- public `ArticleResponse`
- `ArticleController`
- collection pipeline
- search ranking logic

## Output Contracts

### Rebuild

```http
POST /api/internal/graph-projections/articles/rebuild
```

Response:

```json
{
  "status": "completed",
  "rebuiltAt": "2026-06-03T09:00:00Z",
  "articleNodeCount": 5,
  "topicNodeCount": 15,
  "hasTopicRelationshipCount": 15,
  "relatedToRelationshipCount": 10,
  "durationMs": 120,
  "failedReason": null
}
```

Failure response uses the same shape with `status="failed"`, count fields set to `0`, `rebuiltAt=null`, and `failedReason` set to a short message.
The count values above are fresh seed-data examples. Local smoke must record the actual count values returned by the current PostgreSQL volume.

### Context

```http
GET /api/internal/graph/articles/{id}/context
```

Response:

```json
{
  "articleId": 4,
  "topics": [
    {
      "name": "graph rag",
      "displayName": "Graph RAG",
      "relatedArticleIds": [1]
    }
  ],
  "relatedArticles": [
    {
      "articleId": 1,
      "title": "OpenAI Releases Agent Evaluation Toolkit",
      "relationType": "RELATED",
      "reason": "Graph RAG evaluation connects to agent and retrieval evaluation.",
      "sharedTopics": []
    }
  ],
  "timings": {
    "neo4jElapsedMs": 8,
    "totalElapsedMs": 8
  }
}
```

## Task 1: Preflight And Contract Protection

**Files:**
- Read: `AGENTS.md`
- Read: `docs/STATUS.md`
- Read: `docs/ROADMAP.md`
- Read: `docs/CODING_CONVENTIONS.md`
- Read: `docs/superpowers/specs/2026-06-03-neo4j-graph-projection-design.md`
- Read: `backend/src/main/kotlin/com/sigak/search/config/SearchInfrastructureConfig.kt`
- Read: `backend/src/main/kotlin/com/sigak/article/repository/ArticleRepository.kt`

- [x] **Step 1: Confirm branch and uncommitted changes**

Run:

```bash
git status --short --branch
```

Expected:

```text
## main...origin/main
?? docs/superpowers/specs/2026-06-03-neo4j-graph-projection-design.md
?? docs/superpowers/plans/2026-06-03-neo4j-graph-projection.md
```

If additional files appear, check whether this plan touches them. Do not revert unrelated changes.

- [x] **Step 2: Confirm existing backend health before editing**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.search.service.SearchInfrastructureHealthServiceTest --tests com.sigak.article.service.ArticleServiceTest
```

Expected:

```text
BUILD SUCCESSFUL
```

If `SearchInfrastructureHealthServiceTest` does not exist, run:

```bash
cd backend
./gradlew test --tests com.sigak.article.service.ArticleServiceTest
```

Expected:

```text
BUILD SUCCESSFUL
```

## Task 2: Add Graph Projection Documents And Reader

**Files:**
- Create: `backend/src/main/kotlin/com/sigak/search/graph/ArticleGraphProjectionDocument.kt`
- Create: `backend/src/main/kotlin/com/sigak/search/graph/ArticleGraphProjectionReader.kt`
- Create: `backend/src/test/kotlin/com/sigak/search/graph/ArticleGraphProjectionReaderTest.kt`

- [x] **Step 1: Create failing reader test**

Create `backend/src/test/kotlin/com/sigak/search/graph/ArticleGraphProjectionReaderTest.kt`:

```kotlin
package com.sigak.search.graph

import com.sigak.article.domain.ArticleEnrichmentEntity
import com.sigak.article.domain.ArticleEntity
import com.sigak.article.domain.ArticleRelationEntity
import com.sigak.article.domain.ArticleTopicEntity
import com.sigak.article.domain.EventType
import com.sigak.article.domain.PrimaryCategory
import com.sigak.article.domain.ProcessingStatus
import com.sigak.article.domain.RelationType
import com.sigak.article.repository.ArticleRepository
import com.sigak.source.domain.NewsSourceEntity
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class ArticleGraphProjectionReaderTest {

    private val articleRepository = mock<ArticleRepository>()
    private val reader = ArticleGraphProjectionReader(articleRepository)

    @Test
    fun readApiReadyGraphDocumentsPreservesTopicsRelationsAndReasons() {
        val source = NewsSourceEntity(id = 1L, sourceKey = "openai", name = "OpenAI")
        val target = article(id = 2L, title = "Target article", source = source)
        val sourceArticle = article(id = 1L, title = "Source article", source = source)
        sourceArticle.topics.add(topic(sourceArticle, "Graph RAG", 0))
        sourceArticle.topics.add(topic(sourceArticle, "knowledge graphs", 1))
        sourceArticle.outgoingRelations.add(
            ArticleRelationEntity(
                id = 10L,
                sourceArticle = sourceArticle,
                targetArticle = target,
                relationType = RelationType.RELATED,
                reason = "Both articles explain graph-aware retrieval."
            )
        )

        `when`(articleRepository.findApiReadyArticles(ProcessingStatus.PUBLISHED))
            .thenReturn(listOf(sourceArticle, target))

        val documents = reader.readApiReadyGraphDocuments()

        assertEquals(2, documents.size)
        val document = documents.first { it.articleId == 1L }
        assertEquals("Source article", document.title)
        assertEquals("OpenAI", document.source)
        assertEquals("OFFICIAL_ANNOUNCEMENT", document.eventType)
        assertEquals("AI", document.primaryCategory)
        assertEquals(listOf("Graph RAG", "knowledge graphs"), document.topics.map { it.displayName })
        assertEquals(listOf("graph rag", "knowledge graphs"), document.topics.map { it.name })
        assertEquals(listOf(0, 1), document.topics.map { it.position })
        assertEquals(1, document.outgoingRelations.size)
        assertEquals(2L, document.outgoingRelations.single().targetArticleId)
        assertEquals("RELATED", document.outgoingRelations.single().relationType)
        assertEquals("Both articles explain graph-aware retrieval.", document.outgoingRelations.single().reason)
        verify(articleRepository).fetchArticleResponseGraph(listOf(sourceArticle, target))
    }

    @Test
    fun readApiReadyGraphDocumentsSkipsRelationsWhoseTargetsAreNotApiReady() {
        val source = NewsSourceEntity(id = 1L, sourceKey = "openai", name = "OpenAI")
        val missingTarget = article(id = 99L, title = "Draft article", source = source)
        val sourceArticle = article(id = 1L, title = "Source article", source = source)
        sourceArticle.outgoingRelations.add(
            ArticleRelationEntity(
                id = 11L,
                sourceArticle = sourceArticle,
                targetArticle = missingTarget,
                relationType = RelationType.RELATED,
                reason = "Target is not API-ready."
            )
        )

        `when`(articleRepository.findApiReadyArticles(ProcessingStatus.PUBLISHED))
            .thenReturn(listOf(sourceArticle))

        val documents = reader.readApiReadyGraphDocuments()

        assertEquals(emptyList(), documents.single().outgoingRelations)
    }

    private fun article(
        id: Long,
        title: String,
        source: NewsSourceEntity
    ): ArticleEntity =
        ArticleEntity(
            id = id,
            source = source,
            title = title,
            url = "https://example.com/articles/$id",
            canonicalUrl = "https://example.com/articles/$id",
            publishedAt = Instant.parse("2026-06-03T00:00:00Z"),
            eventType = EventType.OFFICIAL_ANNOUNCEMENT,
            primaryCategory = PrimaryCategory.AI,
            importanceScore = 80,
            processingStatus = ProcessingStatus.PUBLISHED,
            createdAt = Instant.parse("2026-06-03T00:00:00Z"),
            updatedAt = Instant.parse("2026-06-03T00:00:00Z")
        ).also { article ->
            article.enrichments.add(
                ArticleEnrichmentEntity(
                    article = article,
                    summary = "Summary",
                    whyItMatters = "Why it matters",
                    suggestedPrimaryCategory = PrimaryCategory.AI.name,
                    suggestedImportanceScore = 80,
                    modelName = "test",
                    promptVersion = "test",
                    current = true,
                    enrichedAt = Instant.parse("2026-06-03T00:00:00Z")
                )
            )
        }

    private fun topic(article: ArticleEntity, name: String, position: Int): ArticleTopicEntity =
        ArticleTopicEntity(article = article, topic = name, position = position)
}
```

- [x] **Step 2: Run reader test and confirm it fails**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.search.graph.ArticleGraphProjectionReaderTest
```

Expected:

```text
Compilation error: Unresolved reference: ArticleGraphProjectionReader
```

- [x] **Step 3: Add projection document DTOs**

Create `backend/src/main/kotlin/com/sigak/search/graph/ArticleGraphProjectionDocument.kt`:

```kotlin
package com.sigak.search.graph

data class ArticleGraphProjectionDocument(
    val articleId: Long,
    val title: String,
    val source: String,
    val url: String,
    val publishedAt: String,
    val eventType: String,
    val primaryCategory: String,
    val importanceScore: Int,
    val topics: List<ArticleGraphTopicDocument>,
    val outgoingRelations: List<ArticleGraphRelationDocument>
)

data class ArticleGraphTopicDocument(
    val name: String,
    val displayName: String,
    val position: Int
)

data class ArticleGraphRelationDocument(
    val sourceArticleId: Long,
    val targetArticleId: Long,
    val relationType: String,
    val reason: String?
)
```

- [x] **Step 4: Add projection reader**

Create `backend/src/main/kotlin/com/sigak/search/graph/ArticleGraphProjectionReader.kt`:

```kotlin
package com.sigak.search.graph

import com.sigak.article.domain.ArticleEntity
import com.sigak.article.domain.ProcessingStatus
import com.sigak.article.repository.ArticleRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ArticleGraphProjectionReader(
    private val articleRepository: ArticleRepository
) {

    @Transactional(readOnly = true)
    fun readApiReadyGraphDocuments(): List<ArticleGraphProjectionDocument> {
        val articles = articleRepository.findApiReadyArticles(ProcessingStatus.PUBLISHED)
        articleRepository.fetchArticleResponseGraph(articles)

        val apiReadyArticleIds = articles.map { article -> requireNotNull(article.id) }.toSet()

        return articles.map { article -> article.toGraphProjectionDocument(apiReadyArticleIds) }
    }

    private fun ArticleEntity.toGraphProjectionDocument(apiReadyArticleIds: Set<Long>): ArticleGraphProjectionDocument {
        val articleId = requireNotNull(id)

        return ArticleGraphProjectionDocument(
            articleId = articleId,
            title = title,
            source = source.name,
            url = url,
            publishedAt = publishedAt.toString(),
            eventType = eventType.name,
            primaryCategory = primaryCategory.name,
            importanceScore = importanceScore,
            topics = topics
                .sortedBy { topic -> topic.position }
                .map { topic ->
                    ArticleGraphTopicDocument(
                        name = topic.topic.toGraphTopicName(),
                        displayName = topic.topic.trim(),
                        position = topic.position
                    )
                },
            outgoingRelations = outgoingRelations
                .sortedBy { relation -> relation.id ?: Long.MAX_VALUE }
                .mapNotNull { relation ->
                    val targetArticleId = requireNotNull(relation.targetArticle.id)
                    if (targetArticleId !in apiReadyArticleIds) {
                        null
                    } else {
                        ArticleGraphRelationDocument(
                            sourceArticleId = articleId,
                            targetArticleId = targetArticleId,
                            relationType = relation.relationType.name,
                            reason = relation.reason
                        )
                    }
                }
        )
    }

    private fun String.toGraphTopicName(): String =
        trim().lowercase()
}
```

- [x] **Step 5: Run reader test and confirm it passes**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.search.graph.ArticleGraphProjectionReaderTest
```

Expected:

```text
BUILD SUCCESSFUL
```

## Task 3: Add Rebuild Service With Fake Indexer Tests

**Files:**
- Create: `backend/src/main/kotlin/com/sigak/search/graph/ArticleGraphProjectionIndexer.kt`
- Create: `backend/src/main/kotlin/com/sigak/search/graph/ArticleGraphProjectionRebuildResponse.kt`
- Create: `backend/src/main/kotlin/com/sigak/search/graph/ArticleGraphProjectionRebuildService.kt`
- Create: `backend/src/test/kotlin/com/sigak/search/graph/ArticleGraphProjectionRebuildServiceTest.kt`

- [x] **Step 1: Write failing rebuild service tests**

Create `backend/src/test/kotlin/com/sigak/search/graph/ArticleGraphProjectionRebuildServiceTest.kt`:

```kotlin
package com.sigak.search.graph

import kotlin.test.Test
import kotlin.test.assertEquals

class ArticleGraphProjectionRebuildServiceTest {

    private val reader = RecordingArticleGraphProjectionReader()
    private val indexer = RecordingArticleGraphProjectionIndexer()
    private val service = ArticleGraphProjectionRebuildService(reader, indexer)

    @Test
    fun rebuildReturnsCompletedCountsAndRebuiltAt() {
        reader.documents = listOf(
            graphDocument(
                articleId = 1L,
                topics = listOf(ArticleGraphTopicDocument(name = "graph rag", displayName = "Graph RAG", position = 0)),
                relations = listOf(
                    ArticleGraphRelationDocument(
                        sourceArticleId = 1L,
                        targetArticleId = 2L,
                        relationType = "RELATED",
                        reason = "Related reason"
                    )
                )
            )
        )
        indexer.result = ArticleGraphProjectionIndexResult(
            articleNodeCount = 1,
            topicNodeCount = 1,
            hasTopicRelationshipCount = 1,
            relatedToRelationshipCount = 1
        )

        val response = service.rebuild()

        assertEquals("completed", response.status)
        assertEquals(1, response.articleNodeCount)
        assertEquals(1, response.topicNodeCount)
        assertEquals(1, response.hasTopicRelationshipCount)
        assertEquals(1, response.relatedToRelationshipCount)
        assertEquals(null, response.failedReason)
        assertEquals(true, response.rebuiltAt != null)
        assertEquals(reader.documents, indexer.rebuiltDocuments)
    }

    @Test
    fun rebuildReturnsCompletedWhenNoArticlesExist() {
        reader.documents = emptyList()
        indexer.result = ArticleGraphProjectionIndexResult(
            articleNodeCount = 0,
            topicNodeCount = 0,
            hasTopicRelationshipCount = 0,
            relatedToRelationshipCount = 0
        )

        val response = service.rebuild()

        assertEquals("completed", response.status)
        assertEquals(0, response.articleNodeCount)
        assertEquals(0, response.topicNodeCount)
        assertEquals(0, response.hasTopicRelationshipCount)
        assertEquals(0, response.relatedToRelationshipCount)
        assertEquals(null, response.failedReason)
    }

    @Test
    fun rebuildReturnsFailedResponseWhenIndexerFails() {
        reader.documents = listOf(graphDocument(articleId = 1L))
        indexer.exception = RuntimeException("neo4j unavailable")

        val response = service.rebuild()

        assertEquals("failed", response.status)
        assertEquals(null, response.rebuiltAt)
        assertEquals(0, response.articleNodeCount)
        assertEquals(0, response.topicNodeCount)
        assertEquals(0, response.hasTopicRelationshipCount)
        assertEquals(0, response.relatedToRelationshipCount)
        assertEquals("neo4j unavailable", response.failedReason)
    }

    private fun graphDocument(
        articleId: Long,
        topics: List<ArticleGraphTopicDocument> = emptyList(),
        relations: List<ArticleGraphRelationDocument> = emptyList()
    ): ArticleGraphProjectionDocument =
        ArticleGraphProjectionDocument(
            articleId = articleId,
            title = "Article $articleId",
            source = "Source",
            url = "https://example.com/articles/$articleId",
            publishedAt = "2026-06-03T00:00:00Z",
            eventType = "NEWS",
            primaryCategory = "AI",
            importanceScore = 80,
            topics = topics,
            outgoingRelations = relations
        )

    private class RecordingArticleGraphProjectionReader : ArticleGraphProjectionReaderPort {
        var documents: List<ArticleGraphProjectionDocument> = emptyList()

        override fun readApiReadyGraphDocuments(): List<ArticleGraphProjectionDocument> =
            documents
    }

    private class RecordingArticleGraphProjectionIndexer : ArticleGraphProjectionIndexer {
        var result = ArticleGraphProjectionIndexResult(0, 0, 0, 0)
        var exception: RuntimeException? = null
        var rebuiltDocuments: List<ArticleGraphProjectionDocument> = emptyList()

        override fun rebuild(documents: List<ArticleGraphProjectionDocument>): ArticleGraphProjectionIndexResult {
            exception?.let { throw it }
            rebuiltDocuments = documents
            return result
        }

        override fun findContext(articleId: Long): ArticleGraphContextProjection? =
            null
    }
}
```

- [x] **Step 2: Run rebuild service test and confirm it fails**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.search.graph.ArticleGraphProjectionRebuildServiceTest
```

Expected:

```text
Compilation error: Unresolved reference: ArticleGraphProjectionRebuildService
```

- [x] **Step 3: Introduce reader port and update reader**

Modify `backend/src/main/kotlin/com/sigak/search/graph/ArticleGraphProjectionReader.kt` so it implements a port:

```kotlin
package com.sigak.search.graph

import com.sigak.article.domain.ArticleEntity
import com.sigak.article.domain.ProcessingStatus
import com.sigak.article.repository.ArticleRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

interface ArticleGraphProjectionReaderPort {
    fun readApiReadyGraphDocuments(): List<ArticleGraphProjectionDocument>
}

@Component
class ArticleGraphProjectionReader(
    private val articleRepository: ArticleRepository
) : ArticleGraphProjectionReaderPort {

    @Transactional(readOnly = true)
    override fun readApiReadyGraphDocuments(): List<ArticleGraphProjectionDocument> {
        val articles = articleRepository.findApiReadyArticles(ProcessingStatus.PUBLISHED)
        articleRepository.fetchArticleResponseGraph(articles)

        val apiReadyArticleIds = articles.map { article -> requireNotNull(article.id) }.toSet()

        return articles.map { article -> article.toGraphProjectionDocument(apiReadyArticleIds) }
    }

    private fun ArticleEntity.toGraphProjectionDocument(apiReadyArticleIds: Set<Long>): ArticleGraphProjectionDocument {
        val articleId = requireNotNull(id)

        return ArticleGraphProjectionDocument(
            articleId = articleId,
            title = title,
            source = source.name,
            url = url,
            publishedAt = publishedAt.toString(),
            eventType = eventType.name,
            primaryCategory = primaryCategory.name,
            importanceScore = importanceScore,
            topics = topics
                .sortedBy { topic -> topic.position }
                .map { topic ->
                    ArticleGraphTopicDocument(
                        name = topic.topic.toGraphTopicName(),
                        displayName = topic.topic.trim(),
                        position = topic.position
                    )
                },
            outgoingRelations = outgoingRelations
                .sortedBy { relation -> relation.id ?: Long.MAX_VALUE }
                .mapNotNull { relation ->
                    val targetArticleId = requireNotNull(relation.targetArticle.id)
                    if (targetArticleId !in apiReadyArticleIds) {
                        null
                    } else {
                        ArticleGraphRelationDocument(
                            sourceArticleId = articleId,
                            targetArticleId = targetArticleId,
                            relationType = relation.relationType.name,
                            reason = relation.reason
                        )
                    }
                }
        )
    }

    private fun String.toGraphTopicName(): String =
        trim().lowercase()
}
```

- [x] **Step 4: Add indexer interface and context projection types**

Create `backend/src/main/kotlin/com/sigak/search/graph/ArticleGraphProjectionIndexer.kt`:

```kotlin
package com.sigak.search.graph

interface ArticleGraphProjectionIndexer {
    fun rebuild(documents: List<ArticleGraphProjectionDocument>): ArticleGraphProjectionIndexResult

    fun findContext(articleId: Long): ArticleGraphContextProjection?
}

data class ArticleGraphProjectionIndexResult(
    val articleNodeCount: Int,
    val topicNodeCount: Int,
    val hasTopicRelationshipCount: Int,
    val relatedToRelationshipCount: Int
)

data class ArticleGraphContextProjection(
    val articleId: Long,
    val topics: List<ArticleGraphTopicContextProjection>,
    val relatedArticles: List<ArticleGraphRelatedArticleProjection>
)

data class ArticleGraphTopicContextProjection(
    val name: String,
    val displayName: String,
    val relatedArticleIds: List<Long>
)

data class ArticleGraphRelatedArticleProjection(
    val articleId: Long,
    val title: String,
    val relationType: String,
    val reason: String?,
    val sharedTopics: List<String>
)
```

- [x] **Step 5: Add rebuild response and service**

Create `backend/src/main/kotlin/com/sigak/search/graph/ArticleGraphProjectionRebuildResponse.kt`:

```kotlin
package com.sigak.search.graph

data class ArticleGraphProjectionRebuildResponse(
    val status: String,
    val rebuiltAt: String?,
    val articleNodeCount: Int,
    val topicNodeCount: Int,
    val hasTopicRelationshipCount: Int,
    val relatedToRelationshipCount: Int,
    val durationMs: Long,
    val failedReason: String?
)
```

Create `backend/src/main/kotlin/com/sigak/search/graph/ArticleGraphProjectionRebuildService.kt`:

```kotlin
package com.sigak.search.graph

import com.sigak.common.time.measureElapsed
import java.time.Instant
import org.springframework.stereotype.Service

@Service
class ArticleGraphProjectionRebuildService(
    private val reader: ArticleGraphProjectionReaderPort,
    private val indexer: ArticleGraphProjectionIndexer
) {

    fun rebuild(): ArticleGraphProjectionRebuildResponse {
        var result = ArticleGraphProjectionIndexResult(0, 0, 0, 0)
        var rebuiltAt: Instant? = null
        var failedReason: String? = null

        val measured = measureElapsed {
            try {
                val documents = reader.readApiReadyGraphDocuments()
                result = indexer.rebuild(documents)
                rebuiltAt = Instant.now()
            } catch (exception: Exception) {
                failedReason = exception.message ?: exception::class.simpleName
                result = ArticleGraphProjectionIndexResult(0, 0, 0, 0)
                rebuiltAt = null
            }
        }

        return ArticleGraphProjectionRebuildResponse(
            status = if (failedReason == null) COMPLETED else FAILED,
            rebuiltAt = rebuiltAt?.toString(),
            articleNodeCount = result.articleNodeCount,
            topicNodeCount = result.topicNodeCount,
            hasTopicRelationshipCount = result.hasTopicRelationshipCount,
            relatedToRelationshipCount = result.relatedToRelationshipCount,
            durationMs = measured.elapsedMs,
            failedReason = failedReason
        )
    }

    private companion object {
        const val COMPLETED = "completed"
        const val FAILED = "failed"
    }
}
```

- [x] **Step 6: Run rebuild service tests and reader tests**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.search.graph.ArticleGraphProjectionReaderTest --tests com.sigak.search.graph.ArticleGraphProjectionRebuildServiceTest
```

Expected:

```text
BUILD SUCCESSFUL
```

## Task 4: Implement Neo4j Projection Indexer

**Files:**
- Create: `backend/src/main/kotlin/com/sigak/search/graph/Neo4jArticleGraphProjectionIndexer.kt`

- [x] **Step 1: Add Neo4j indexer implementation**

Create `backend/src/main/kotlin/com/sigak/search/graph/Neo4jArticleGraphProjectionIndexer.kt`:

```kotlin
package com.sigak.search.graph

import org.neo4j.driver.Driver
import org.neo4j.driver.Query
import org.neo4j.driver.Session
import org.springframework.stereotype.Component

@Component
class Neo4jArticleGraphProjectionIndexer(
    private val driver: Driver
) : ArticleGraphProjectionIndexer {

    override fun rebuild(documents: List<ArticleGraphProjectionDocument>): ArticleGraphProjectionIndexResult {
        withSession { session ->
            // Neo4j schema command는 데이터 rebuild transaction과 분리해 드라이버/서버 호환성을 단순하게 유지한다.
            session.run(CREATE_ARTICLE_CONSTRAINT).consume()
            session.run(CREATE_TOPIC_CONSTRAINT).consume()
        }

        withSession { session ->
            session.executeWrite { transaction ->
                transaction.run(DELETE_PROJECTION).consume()

                if (documents.isNotEmpty()) {
                    transaction.run(Query(CREATE_ARTICLES, mapOf("articles" to documents.map { it.toArticleParameters() }))).consume()
                    transaction.run(Query(CREATE_TOPICS, mapOf("topics" to documents.flatMap { it.toTopicParameters() }))).consume()
                    transaction.run(Query(CREATE_RELATIONS, mapOf("relations" to documents.flatMap { it.toRelationParameters() }))).consume()
                }
            }
        }

        return ArticleGraphProjectionIndexResult(
            articleNodeCount = documents.size,
            topicNodeCount = documents.flatMap { it.topics }.map { it.name }.distinct().size,
            hasTopicRelationshipCount = documents.sumOf { it.topics.size },
            relatedToRelationshipCount = documents.sumOf { it.outgoingRelations.size }
        )
    }

    override fun findContext(articleId: Long): ArticleGraphContextProjection? =
        withSession { session ->
            val articleExists = session.executeRead { transaction ->
                transaction.run(Query(ARTICLE_EXISTS, mapOf("articleId" to articleId)))
                    .list()
                    .firstOrNull()
                    ?.get("exists")
                    ?.asBoolean()
                    ?: false
            }
            if (!articleExists) {
                return@withSession null
            }

            val topics = session.executeRead { transaction ->
                transaction.run(Query(FIND_TOPICS, mapOf("articleId" to articleId)))
                    .list { record ->
                        ArticleGraphTopicContextProjection(
                            name = record.get("name").asString(),
                            displayName = record.get("displayName").asString(),
                            relatedArticleIds = record.get("relatedArticleIds").asList { value -> value.asLong() }
                                .filter { relatedArticleId -> relatedArticleId != articleId }
                                .distinct()
                                .sorted()
                        )
                    }
            }

            val relatedArticles = session.executeRead { transaction ->
                transaction.run(Query(FIND_RELATED_ARTICLES, mapOf("articleId" to articleId)))
                    .list { record ->
                        ArticleGraphRelatedArticleProjection(
                            articleId = record.get("articleId").asLong(),
                            title = record.get("title").asString(),
                            relationType = record.get("relationType").asString(),
                            reason = if (record.get("reason").isNull) null else record.get("reason").asString(),
                            sharedTopics = record.get("sharedTopics").asList { value -> value.asString() }
                                .distinct()
                                .sorted()
                        )
                    }
            }

            ArticleGraphContextProjection(
                articleId = articleId,
                topics = topics,
                relatedArticles = relatedArticles
            )
        }

    private fun <T> withSession(block: (Session) -> T): T {
        val session = driver.session()
        try {
            return block(session)
        } finally {
            session.close()
        }
    }

    private fun ArticleGraphProjectionDocument.toArticleParameters(): Map<String, Any?> =
        mapOf(
            "articleId" to articleId,
            "title" to title,
            "source" to source,
            "url" to url,
            "publishedAt" to publishedAt,
            "eventType" to eventType,
            "primaryCategory" to primaryCategory,
            "importanceScore" to importanceScore
        )

    private fun ArticleGraphProjectionDocument.toTopicParameters(): List<Map<String, Any?>> =
        topics.map { topic ->
            mapOf(
                "articleId" to articleId,
                "name" to topic.name,
                "displayName" to topic.displayName,
                "position" to topic.position
            )
        }

    private fun ArticleGraphProjectionDocument.toRelationParameters(): List<Map<String, Any?>> =
        outgoingRelations.map { relation ->
            mapOf(
                "sourceArticleId" to relation.sourceArticleId,
                "targetArticleId" to relation.targetArticleId,
                "relationType" to relation.relationType,
                "reason" to relation.reason
            )
        }

    private companion object {
        const val CREATE_ARTICLE_CONSTRAINT = """
            CREATE CONSTRAINT sigak_article_article_id IF NOT EXISTS
            FOR (article:Article)
            REQUIRE article.articleId IS UNIQUE
        """

        const val CREATE_TOPIC_CONSTRAINT = """
            CREATE CONSTRAINT sigak_topic_name IF NOT EXISTS
            FOR (topic:Topic)
            REQUIRE topic.name IS UNIQUE
        """

        const val DELETE_PROJECTION = """
            MATCH (node)
            WHERE node:Article OR node:Topic
            DETACH DELETE node
        """

        const val CREATE_ARTICLES = """
            UNWIND ${'$'}articles AS row
            CREATE (:Article {
                articleId: row.articleId,
                title: row.title,
                source: row.source,
                url: row.url,
                publishedAt: row.publishedAt,
                eventType: row.eventType,
                primaryCategory: row.primaryCategory,
                importanceScore: row.importanceScore
            })
        """

        const val CREATE_TOPICS = """
            UNWIND ${'$'}topics AS row
            MATCH (article:Article {articleId: row.articleId})
            MERGE (topic:Topic {name: row.name})
            ON CREATE SET topic.displayName = row.displayName
            MERGE (article)-[relationship:HAS_TOPIC]->(topic)
            SET relationship.position = row.position
        """

        const val CREATE_RELATIONS = """
            UNWIND ${'$'}relations AS row
            MATCH (source:Article {articleId: row.sourceArticleId})
            MATCH (target:Article {articleId: row.targetArticleId})
            MERGE (source)-[relationship:RELATED_TO]->(target)
            SET relationship.relationType = row.relationType,
                relationship.reason = row.reason
        """

        const val ARTICLE_EXISTS = """
            MATCH (article:Article {articleId: ${'$'}articleId})
            RETURN count(article) > 0 AS exists
        """

        const val FIND_TOPICS = """
            MATCH (:Article {articleId: ${'$'}articleId})-[articleTopic:HAS_TOPIC]->(topic:Topic)
            OPTIONAL MATCH (topic)<-[:HAS_TOPIC]-(peer:Article)
            RETURN topic.name AS name,
                   topic.displayName AS displayName,
                   collect(distinct peer.articleId) AS relatedArticleIds
            ORDER BY articleTopic.position ASC, displayName ASC
        """

        const val FIND_RELATED_ARTICLES = """
            MATCH (:Article {articleId: ${'$'}articleId})-[relationship:RELATED_TO]->(related:Article)
            OPTIONAL MATCH (:Article {articleId: ${'$'}articleId})-[:HAS_TOPIC]->(topic:Topic)<-[:HAS_TOPIC]-(related)
            RETURN related.articleId AS articleId,
                   related.title AS title,
                   relationship.relationType AS relationType,
                   relationship.reason AS reason,
                   collect(distinct topic.displayName) AS sharedTopics
            ORDER BY related.articleId ASC
        """
    }
}
```

- [x] **Step 2: Compile graph package**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.search.graph.ArticleGraphProjectionRebuildServiceTest
```

Expected:

```text
BUILD SUCCESSFUL
```

If Neo4j driver API names differ, fix only `Neo4jArticleGraphProjectionIndexer.kt` and rerun this command.

## Task 5: Add Rebuild Controller

**Files:**
- Create: `backend/src/main/kotlin/com/sigak/search/graph/ArticleGraphProjectionController.kt`
- Create: `backend/src/test/kotlin/com/sigak/search/graph/ArticleGraphProjectionControllerTest.kt`

- [x] **Step 1: Write failing controller test**

Create `backend/src/test/kotlin/com/sigak/search/graph/ArticleGraphProjectionControllerTest.kt`:

```kotlin
package com.sigak.search.graph

import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(ArticleGraphProjectionController::class)
class ArticleGraphProjectionControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var service: ArticleGraphProjectionRebuildService

    @Test
    fun rebuildReturnsGraphProjectionCounts() {
        `when`(service.rebuild()).thenReturn(
            ArticleGraphProjectionRebuildResponse(
                status = "completed",
                rebuiltAt = "2026-06-03T09:00:00Z",
                articleNodeCount = 6,
                topicNodeCount = 18,
                hasTopicRelationshipCount = 18,
                relatedToRelationshipCount = 10,
                durationMs = 120,
                failedReason = null
            )
        )

        mockMvc.perform(post("/api/internal/graph-projections/articles/rebuild"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("completed"))
            .andExpect(jsonPath("$.rebuiltAt").value("2026-06-03T09:00:00Z"))
            .andExpect(jsonPath("$.articleNodeCount").value(6))
            .andExpect(jsonPath("$.topicNodeCount").value(18))
            .andExpect(jsonPath("$.hasTopicRelationshipCount").value(18))
            .andExpect(jsonPath("$.relatedToRelationshipCount").value(10))
            .andExpect(jsonPath("$.durationMs").value(120))
            .andExpect(jsonPath("$.failedReason").value(nullValue()))
    }
}
```

- [x] **Step 2: Run controller test and confirm it fails**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.search.graph.ArticleGraphProjectionControllerTest
```

Expected:

```text
Compilation error: Unresolved reference: ArticleGraphProjectionController
```

- [x] **Step 3: Add rebuild controller**

Create `backend/src/main/kotlin/com/sigak/search/graph/ArticleGraphProjectionController.kt`:

```kotlin
package com.sigak.search.graph

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/internal/graph-projections/articles")
@Tag(name = "Internal Graph Projection", description = "Internal Neo4j graph projection APIs.")
class ArticleGraphProjectionController(
    private val service: ArticleGraphProjectionRebuildService
) {

    @PostMapping("/rebuild")
    @Operation(
        summary = "Rebuild article graph projection",
        description = "Recreates Neo4j Article, Topic, HAS_TOPIC, and RELATED_TO projection data from PostgreSQL."
    )
    fun rebuild(): ArticleGraphProjectionRebuildResponse =
        service.rebuild()
}
```

- [x] **Step 4: Run controller test and graph focused tests**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.search.graph.ArticleGraphProjectionControllerTest --tests com.sigak.search.graph.ArticleGraphProjectionReaderTest --tests com.sigak.search.graph.ArticleGraphProjectionRebuildServiceTest
```

Expected:

```text
BUILD SUCCESSFUL
```

## Task 6: Add Graph Context Service

**Files:**
- Create: `backend/src/main/kotlin/com/sigak/search/graph/ArticleGraphContextResponse.kt`
- Create: `backend/src/main/kotlin/com/sigak/search/graph/ArticleGraphContextService.kt`
- Create: `backend/src/test/kotlin/com/sigak/search/graph/ArticleGraphContextServiceTest.kt`

- [x] **Step 1: Write failing context service tests**

Create `backend/src/test/kotlin/com/sigak/search/graph/ArticleGraphContextServiceTest.kt`:

```kotlin
package com.sigak.search.graph

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ArticleGraphContextServiceTest {

    private val indexer = RecordingArticleGraphProjectionIndexer()
    private val service = ArticleGraphContextService(indexer)

    @Test
    fun getContextReturnsRelatedArticlesTopicsAndTimings() {
        indexer.context = ArticleGraphContextProjection(
            articleId = 4L,
            topics = listOf(
                ArticleGraphTopicContextProjection(
                    name = "graph rag",
                    displayName = "Graph RAG",
                    relatedArticleIds = listOf(1L)
                )
            ),
            relatedArticles = listOf(
                ArticleGraphRelatedArticleProjection(
                    articleId = 1L,
                    title = "OpenAI Releases Agent Evaluation Toolkit",
                    relationType = "RELATED",
                    reason = "Graph RAG evaluation connects to agent and retrieval evaluation.",
                    sharedTopics = listOf("evaluation")
                )
            )
        )

        val response = service.getContext(4L)

        assertEquals(4L, response.articleId)
        assertEquals("Graph RAG", response.topics.single().displayName)
        assertEquals(listOf(1L), response.topics.single().relatedArticleIds)
        assertEquals(1L, response.relatedArticles.single().articleId)
        assertEquals("RELATED", response.relatedArticles.single().relationType)
        assertEquals("Graph RAG evaluation connects to agent and retrieval evaluation.", response.relatedArticles.single().reason)
        assertEquals(listOf("evaluation"), response.relatedArticles.single().sharedTopics)
        assertEquals(true, response.timings.neo4jElapsedMs >= 0)
        assertEquals(true, response.timings.totalElapsedMs >= 0)
    }

    @Test
    fun getContextRejectsNonPositiveArticleIds() {
        val exception = assertFailsWith<IllegalArgumentException> {
            service.getContext(0L)
        }

        assertEquals("articleId must be positive.", exception.message)
    }

    @Test
    fun getContextThrowsNotFoundWhenProjectionArticleIsMissing() {
        indexer.context = null

        val exception = assertFailsWith<ArticleGraphContextNotFoundException> {
            service.getContext(99L)
        }

        assertEquals("Article graph context not found: articleId=99", exception.message)
    }

    private class RecordingArticleGraphProjectionIndexer : ArticleGraphProjectionIndexer {
        var context: ArticleGraphContextProjection? = null

        override fun rebuild(documents: List<ArticleGraphProjectionDocument>): ArticleGraphProjectionIndexResult =
            ArticleGraphProjectionIndexResult(0, 0, 0, 0)

        override fun findContext(articleId: Long): ArticleGraphContextProjection? =
            context
    }
}
```

- [x] **Step 2: Run context service test and confirm it fails**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.search.graph.ArticleGraphContextServiceTest
```

Expected:

```text
Compilation error: Unresolved reference: ArticleGraphContextService
```

- [x] **Step 3: Add context response DTOs**

Create `backend/src/main/kotlin/com/sigak/search/graph/ArticleGraphContextResponse.kt`:

```kotlin
package com.sigak.search.graph

data class ArticleGraphContextResponse(
    val articleId: Long,
    val topics: List<ArticleGraphTopicContextResponse>,
    val relatedArticles: List<ArticleGraphRelatedArticleResponse>,
    val timings: ArticleGraphContextTimingsResponse
)

data class ArticleGraphTopicContextResponse(
    val name: String,
    val displayName: String,
    val relatedArticleIds: List<Long>
)

data class ArticleGraphRelatedArticleResponse(
    val articleId: Long,
    val title: String,
    val relationType: String,
    val reason: String?,
    val sharedTopics: List<String>
)

data class ArticleGraphContextTimingsResponse(
    val neo4jElapsedMs: Long,
    val totalElapsedMs: Long
)
```

- [x] **Step 4: Add context service**

Create `backend/src/main/kotlin/com/sigak/search/graph/ArticleGraphContextService.kt`:

```kotlin
package com.sigak.search.graph

import com.sigak.common.time.measureElapsed
import org.springframework.stereotype.Service

@Service
class ArticleGraphContextService(
    private val indexer: ArticleGraphProjectionIndexer
) {

    fun getContext(articleId: Long): ArticleGraphContextResponse {
        require(articleId > 0) { "articleId must be positive." }

        val result = measureElapsed {
            indexer.findContext(articleId)
                ?: throw ArticleGraphContextNotFoundException(articleId)
        }

        return result.value.toResponse(
            neo4jElapsedMs = result.elapsedMs,
            totalElapsedMs = result.elapsedMs
        )
    }

    private fun ArticleGraphContextProjection.toResponse(
        neo4jElapsedMs: Long,
        totalElapsedMs: Long
    ): ArticleGraphContextResponse =
        ArticleGraphContextResponse(
            articleId = articleId,
            topics = topics.map { topic ->
                ArticleGraphTopicContextResponse(
                    name = topic.name,
                    displayName = topic.displayName,
                    relatedArticleIds = topic.relatedArticleIds
                )
            },
            relatedArticles = relatedArticles.map { related ->
                ArticleGraphRelatedArticleResponse(
                    articleId = related.articleId,
                    title = related.title,
                    relationType = related.relationType,
                    reason = related.reason,
                    sharedTopics = related.sharedTopics
                )
            },
            timings = ArticleGraphContextTimingsResponse(
                neo4jElapsedMs = neo4jElapsedMs,
                totalElapsedMs = totalElapsedMs
            )
        )
}

class ArticleGraphContextNotFoundException(articleId: Long) :
    RuntimeException("Article graph context not found: articleId=$articleId")
```

- [x] **Step 5: Run context service tests**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.search.graph.ArticleGraphContextServiceTest
```

Expected:

```text
BUILD SUCCESSFUL
```

## Task 7: Add Graph Context Controller

**Files:**
- Create: `backend/src/main/kotlin/com/sigak/search/graph/ArticleGraphContextController.kt`
- Create: `backend/src/test/kotlin/com/sigak/search/graph/ArticleGraphContextControllerTest.kt`

- [x] **Step 1: Write failing context controller tests**

Create `backend/src/test/kotlin/com/sigak/search/graph/ArticleGraphContextControllerTest.kt`:

```kotlin
package com.sigak.search.graph

import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(ArticleGraphContextController::class)
class ArticleGraphContextControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var service: ArticleGraphContextService

    @Test
    fun getContextReturnsGraphContext() {
        `when`(service.getContext(4L)).thenReturn(
            ArticleGraphContextResponse(
                articleId = 4L,
                topics = listOf(
                    ArticleGraphTopicContextResponse(
                        name = "graph rag",
                        displayName = "Graph RAG",
                        relatedArticleIds = listOf(1L)
                    )
                ),
                relatedArticles = listOf(
                    ArticleGraphRelatedArticleResponse(
                        articleId = 1L,
                        title = "OpenAI Releases Agent Evaluation Toolkit",
                        relationType = "RELATED",
                        reason = "Graph RAG evaluation connects to agent and retrieval evaluation.",
                        sharedTopics = listOf("evaluation")
                    )
                ),
                timings = ArticleGraphContextTimingsResponse(
                    neo4jElapsedMs = 8,
                    totalElapsedMs = 8
                )
            )
        )

        mockMvc.perform(get("/api/internal/graph/articles/4/context"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.articleId").value(4))
            .andExpect(jsonPath("$.topics[0].name").value("graph rag"))
            .andExpect(jsonPath("$.topics[0].displayName").value("Graph RAG"))
            .andExpect(jsonPath("$.topics[0].relatedArticleIds[0]").value(1))
            .andExpect(jsonPath("$.relatedArticles[0].articleId").value(1))
            .andExpect(jsonPath("$.relatedArticles[0].relationType").value("RELATED"))
            .andExpect(jsonPath("$.relatedArticles[0].reason").value("Graph RAG evaluation connects to agent and retrieval evaluation."))
            .andExpect(jsonPath("$.relatedArticles[0].sharedTopics[0]").value("evaluation"))
            .andExpect(jsonPath("$.timings.neo4jElapsedMs").value(8))
    }

    @Test
    fun getContextReturnsBadRequestForInvalidId() {
        `when`(service.getContext(0L)).thenThrow(IllegalArgumentException("articleId must be positive."))

        mockMvc.perform(get("/api/internal/graph/articles/0/context"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("articleId must be positive."))
    }

    @Test
    fun getContextReturnsNotFoundWhenProjectionArticleIsMissing() {
        `when`(service.getContext(99L)).thenThrow(ArticleGraphContextNotFoundException(99L))

        mockMvc.perform(get("/api/internal/graph/articles/99/context"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("Article graph context not found: articleId=99"))
    }
}
```

- [x] **Step 2: Run context controller test and confirm it fails**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.search.graph.ArticleGraphContextControllerTest
```

Expected:

```text
Compilation error: Unresolved reference: ArticleGraphContextController
```

- [x] **Step 3: Add context controller**

Create `backend/src/main/kotlin/com/sigak/search/graph/ArticleGraphContextController.kt`:

```kotlin
package com.sigak.search.graph

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/internal/graph/articles")
@Tag(name = "Internal Article Graph", description = "Internal Neo4j article graph context APIs.")
class ArticleGraphContextController(
    private val service: ArticleGraphContextService
) {

    @GetMapping("/{id}/context")
    @Operation(
        summary = "Get article graph context",
        description = "Returns internal graph context for an article from the Neo4j projection."
    )
    fun getContext(@PathVariable id: Long): ArticleGraphContextResponse =
        service.getContext(id)

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleIllegalArgumentException(exception: IllegalArgumentException): Map<String, String> =
        mapOf("message" to exception.message.orEmpty())

    @ExceptionHandler(ArticleGraphContextNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleNotFound(exception: ArticleGraphContextNotFoundException): Map<String, String> =
        mapOf("message" to exception.message.orEmpty())
}
```

- [x] **Step 4: Run all graph focused tests**

Run:

```bash
cd backend
./gradlew test --tests 'com.sigak.search.graph.*'
```

Expected:

```text
BUILD SUCCESSFUL
```

## Task 8: Documentation Updates

**Files:**
- Modify: `docs/API_SPEC.md`
- Modify: `docs/API_SPEC.ko.md`
- Modify: `backend/src/test/kotlin/com/sigak/docs/OpenApiDocumentationTest.kt`
- Modify: `docs/STATUS.md`
- Modify: `docs/STATUS.ko.md`
- Modify: `docs/ROADMAP.md`
- Modify: `docs/ROADMAP.ko.md`
- Modify: `docs/blog/2026-06-03-dev-log.md`
- Modify: `docs/blog/topic-queue.md`

- [x] **Step 1: Update API spec**

Add to `docs/API_SPEC.md` endpoint list:

```markdown
POST /api/internal/graph-projections/articles/rebuild
GET /api/internal/graph/articles/{id}/context
```

Add an internal graph projection section:

```markdown
## Internal Graph Projection Contract

Neo4j is a rebuildable projection store. PostgreSQL remains the source of truth.

### Rebuild Article Graph Projection

```http
POST /api/internal/graph-projections/articles/rebuild
```

Response fields:

- `status`: `completed` or `failed`
- `rebuiltAt`: projection rebuild timestamp when completed
- `articleNodeCount`
- `topicNodeCount`
- `hasTopicRelationshipCount`
- `relatedToRelationshipCount`
- `durationMs`
- `failedReason`

### Get Article Graph Context

```http
GET /api/internal/graph/articles/{id}/context
```

Returns topics, related article reasons, shared topics, and timing metadata from Neo4j.
This endpoint is internal. Public article responses are unchanged.
```

Mirror the same content in Korean in `docs/API_SPEC.ko.md`.

- [x] **Step 2: Update OpenAPI documentation test**

Modify `backend/src/test/kotlin/com/sigak/docs/OpenApiDocumentationTest.kt` inside `openApiSpecDocumentsArticleEndpoints()`:

```kotlin
.andExpect(jsonPath("$.paths['/api/internal/graph-projections/articles/rebuild'].post.summary").value("Rebuild article graph projection"))
.andExpect(jsonPath("$.paths['/api/internal/graph/articles/{id}/context'].get.summary").value("Get article graph context"))
```

Run:

```bash
cd backend
./gradlew test --tests com.sigak.docs.OpenApiDocumentationTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [x] **Step 3: Update status and roadmap**

In `docs/STATUS.md` and `docs/STATUS.ko.md`, update:

- Search/infra or graph status to say Neo4j projection and internal graph context are implemented after verification.
- Recent verification with actual commands and observed smoke values.
- Next work to public graph-aware article detail and graph-aware evaluation.

In `docs/ROADMAP.md` and `docs/ROADMAP.ko.md`, mark these S5 items complete after verification:

```markdown
- [x] Project articles and topics into Neo4j.
- [x] Store or project article-topic relationships.
- [x] Store article-article relation reasons.
```

Leave this item pending until a later public API/frontend task:

```markdown
- [ ] Show relation reasons or related concepts on article detail.
```

- [x] **Step 4: Write dev-log**

Create or update `docs/blog/2026-06-03-dev-log.md` using `docs/blog/WRITING_GUIDE.ko.md`.

Include:

- Neo4j projection-only decision.
- PostgreSQL source of truth and Neo4j projection store boundary.
- Manual rebuild vs scheduled rebuild trade-off.
- Topic normalization limitation.
- Relation quality limitation.
- Commands actually run and actual outputs only.
- 미검증 items for public article detail and frontend if not run.

- [x] **Step 5: Update topic queue**

Add or strengthen this candidate in `docs/blog/topic-queue.md`:

```markdown
## [candidate] PostgreSQL source of truth와 Neo4j projection store를 분리한 이유
- 날짜: 2026-06-03
- 관련 작업: Neo4j article/topic/relation projection, internal graph context endpoint
- 관련 파일:
  - `backend/src/main/kotlin/com/sigak/search/graph/`
  - `docs/superpowers/specs/2026-06-03-neo4j-graph-projection-design.md`
  - `docs/superpowers/plans/2026-06-03-neo4j-graph-projection.md`
- 감지 이유:
  - PostgreSQL source of truth와 Neo4j projection store를 분리했다.
  - 자동 rebuild 대신 수동 rebuild와 `rebuiltAt`을 선택했다.
  - relation reason 품질과 topic normalization을 의도적으로 MVP 이후로 미뤘다.
  - local smoke로 node/relationship count와 graph context를 검증했다.
- 글의 핵심 질문:
  - Graph RAG를 바로 만들지 않고 relation reason projection부터 시작한 이유는 무엇인가?
  - Neo4j를 원본 저장소로 쓰지 않는 이유는 무엇인가?
  - graph projection stale risk는 MVP에서 어떻게 다뤄야 하는가?
- 검증 근거:
  - 실제 실행한 명령과 결과만 기록한다.
- 추천 글 유형: 회사 기술 블로그 / Graph RAG 단계적 도입 회고
- 상태: candidate
```

## Task 9: Verification And Local Smoke

**Files:**
- Verify all touched backend and docs files.
- No new code files should remain untested.

- [x] **Step 1: Run graph focused tests**

Run:

```bash
cd backend
./gradlew test --tests 'com.sigak.search.graph.*'
```

Expected:

```text
BUILD SUCCESSFUL
```

- [x] **Step 2: Run backend full gate**

Run:

```bash
cd backend
./gradlew test
./gradlew check
```

Expected:

```text
BUILD SUCCESSFUL
BUILD SUCCESSFUL
```

- [x] **Step 3: Run local Neo4j smoke**

Start services:

```bash
docker compose -f infra/docker-compose.yml up -d --pull never postgres neo4j
```

Start backend:

```bash
cd backend
./gradlew bootRun
```

Run rebuild:

```bash
curl -sS -m 30 -X POST http://localhost:8080/api/internal/graph-projections/articles/rebuild
```

Expected shape:

```json
{
  "status": "completed",
  "rebuiltAt": "2026-06-03T...",
  "articleNodeCount": 5,
  "topicNodeCount": 15,
  "hasTopicRelationshipCount": 15,
  "relatedToRelationshipCount": 10,
  "durationMs": 0,
  "failedReason": null
}
```

Fresh seed data normally returns 5 article nodes, 15 topic nodes, 15 `HAS_TOPIC` relationships, and 10 `RELATED_TO` relationships. The current local PostgreSQL volume can return higher counts if collection previously added API-ready articles. Record actual values in `docs/STATUS.md` and dev-log instead of copying the example.

Run context:

```bash
curl -sS -m 15 http://localhost:8080/api/internal/graph/articles/4/context
```

Expected:

```text
Response includes articleId=4, topics containing Graph RAG, and at least one related article with relationType=RELATED and a reason.
```

Optional Cypher count check:

```bash
docker compose -f infra/docker-compose.yml exec neo4j cypher-shell -u neo4j -p sigak-neo4j-password \
  'MATCH (article:Article) RETURN count(article) AS articleCount'
```

Expected:

```text
articleCount equals rebuild response articleNodeCount
```

- [x] **Step 4: Stop local services**

Stop backend with `Ctrl+C`.

Stop Docker services:

```bash
docker compose -f infra/docker-compose.yml stop postgres neo4j
```

Expected:

```text
postgres and neo4j stopped
```

Do not delete volumes unless the user asks.

- [x] **Step 5: Run whitespace check**

Run:

```bash
git diff --check
```

Expected:

```text
no output
```

## Final Review Checklist

Before reporting completion, verify:

- [x] PostgreSQL remains the source of truth.
- [x] Neo4j stores only projection metadata: article properties, topic names, relation type/reason.
- [x] Neo4j does not store raw content, summary body, why-it-matters body, embedding vectors, secrets, service URLs, request headers, or environment values.
- [x] Public article response shape is unchanged.
- [x] `rebuiltAt` appears only on successful rebuild responses.
- [x] Rebuild failure returns `status=failed` and does not throw stack traces to the user.
- [x] Graph context missing article returns `404`.
- [x] Topic normalization is trim + lowercase only.
- [x] Automatic rebuild remains deferred.
- [x] Relation quality claims are not overstated in docs or dev-log.
- [x] Backend graph focused tests pass.
- [x] Backend full test/check pass.
- [x] Local smoke values are recorded with actual counts.
