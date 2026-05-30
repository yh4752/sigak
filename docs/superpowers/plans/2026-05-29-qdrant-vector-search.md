# Qdrant Vector Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build internal Qdrant article vector projection rebuild and semantic vector search APIs for Sigak.

**Architecture:** PostgreSQL remains the source of truth, FastAPI generates multilingual embeddings, and Qdrant stores rebuildable article vector points. Spring Boot exposes internal endpoints for rebuild/search, reloads final article responses from PostgreSQL, and records vector search timings separately from keyword search.

**Tech Stack:** Spring Boot 3.3, Kotlin, RestClient, FastAPI embedding endpoint, Qdrant REST API v1.12, PostgreSQL/JPA, Gradle/JUnit/MockRestServiceServer.

---

## Scope Check

This plan implements the approved design in `docs/superpowers/specs/2026-05-29-qdrant-vector-search-design.md`.

Included:

- Qdrant article collection config.
- Article ID ordered reload API inside `ArticleService`.
- Article embedding input builder.
- Qdrant collection delete/create/upsert/search REST client.
- Internal vector projection rebuild endpoint.
- Internal vector search endpoint.
- In-memory vector search metrics endpoint.
- API/status documentation updates.

Excluded:

- Public `/api/articles` vector search integration.
- Hybrid ranking with Elasticsearch.
- Neo4j graph projection.
- External paid embedding provider.

## File Structure

Create:

- `backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorTextBuilder.kt`
  Converts `ArticleResponse` into stable embedding input text.
- `backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorDocument.kt`
  Qdrant point model used by Spring services before REST serialization.
- `backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorProjectionIndexer.kt`
  Port for Qdrant collection rebuild and vector search operations.
- `backend/src/main/kotlin/com/sigak/search/vector/QdrantArticleVectorProjectionIndexer.kt`
  Qdrant REST implementation.
- `backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorProjectionRebuildResponse.kt`
  Internal rebuild API response DTO.
- `backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorProjectionRebuildService.kt`
  Orchestrates article load, embedding, Qdrant recreate/upsert.
- `backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorProjectionController.kt`
  Internal rebuild endpoint.
- `backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorSearchRequest.kt`
  Internal vector search request DTO.
- `backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorSearchResponse.kt`
  Internal vector search response DTOs.
- `backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorSearchService.kt`
  Orchestrates query embedding, Qdrant search, article reload, metrics.
- `backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorSearchController.kt`
  Internal vector search endpoint.
- `backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorSearchMetricObservation.kt`
  One vector search metric observation.
- `backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorSearchMetricsResponse.kt`
  Metric summary response DTO.
- `backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorSearchMetricsRecorder.kt`
  In-memory vector search metric recorder.
- `backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorSearchMetricsController.kt`
  Internal vector metrics endpoint.

Modify:

- `backend/src/main/kotlin/com/sigak/search/config/SearchInfrastructureProperties.kt`
  Add Qdrant article collection, distance, default limit, max limit properties.
- `backend/src/main/resources/application.yml`
  Add env-backed Qdrant vector settings.
- `backend/src/test/resources/application.yml`
  Add matching test defaults.
- `backend/src/main/kotlin/com/sigak/article/service/ArticleService.kt`
  Expose ordered API-ready article reload by ID list.
- `backend/src/test/kotlin/com/sigak/search/config/SearchInfrastructurePropertiesTest.kt`
  Bind new Qdrant properties.
- `docs/API_SPEC.md`, `docs/API_SPEC.ko.md`, `docs/STATUS.md`, `docs/STATUS.ko.md`, `docs/blog/topic-queue.md`
  Document internal vector search endpoints and blog topic.

Test:

- `backend/src/test/kotlin/com/sigak/article/service/ArticleServiceTest.kt`
- `backend/src/test/kotlin/com/sigak/search/vector/ArticleVectorTextBuilderTest.kt`
- `backend/src/test/kotlin/com/sigak/search/vector/QdrantArticleVectorProjectionIndexerTest.kt`
- `backend/src/test/kotlin/com/sigak/search/vector/ArticleVectorProjectionRebuildServiceTest.kt`
- `backend/src/test/kotlin/com/sigak/search/vector/ArticleVectorProjectionControllerTest.kt`
- `backend/src/test/kotlin/com/sigak/search/vector/ArticleVectorSearchServiceTest.kt`
- `backend/src/test/kotlin/com/sigak/search/vector/ArticleVectorSearchControllerTest.kt`
- `backend/src/test/kotlin/com/sigak/search/vector/ArticleVectorSearchMetricsRecorderTest.kt`
- `backend/src/test/kotlin/com/sigak/search/vector/ArticleVectorSearchMetricsControllerTest.kt`

## Task 1: Qdrant Configuration Properties

**Files:**

- Modify: `backend/src/main/kotlin/com/sigak/search/config/SearchInfrastructureProperties.kt`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/test/resources/application.yml`
- Modify: `backend/src/test/kotlin/com/sigak/search/config/SearchInfrastructurePropertiesTest.kt`

- [ ] **Step 1: Write the failing property binding test**

Update `backend/src/test/kotlin/com/sigak/search/config/SearchInfrastructurePropertiesTest.kt`:

```kotlin
package com.sigak.search.config

import kotlin.test.Test
import kotlin.test.assertEquals
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration

class SearchInfrastructurePropertiesTest {

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(SearchInfrastructurePropertiesConfig::class.java)

    @Test
    fun bindsSearchInfrastructureProperties() {
        contextRunner
            .withPropertyValues(
                "sigak.search.elasticsearch.url=http://es:9200",
                "sigak.search.elasticsearch.article-index-name=test-articles",
                "sigak.search.qdrant.url=http://qdrant:6333",
                "sigak.search.qdrant.article-collection-name=test-article-vectors",
                "sigak.search.qdrant.distance=Cosine",
                "sigak.search.qdrant.default-limit=7",
                "sigak.search.qdrant.max-limit=31",
                "sigak.search.neo4j.uri=bolt://neo4j:7687",
                "sigak.search.neo4j.username=neo4j",
                "sigak.search.neo4j.password=test-password"
            )
            .run { context ->
                val properties = context.getBean(SearchInfrastructureProperties::class.java)

                assertEquals("http://es:9200", properties.elasticsearch.url)
                assertEquals("test-articles", properties.elasticsearch.articleIndexName)
                assertEquals("http://qdrant:6333", properties.qdrant.url)
                assertEquals("test-article-vectors", properties.qdrant.articleCollectionName)
                assertEquals("Cosine", properties.qdrant.distance)
                assertEquals(7, properties.qdrant.defaultLimit)
                assertEquals(31, properties.qdrant.maxLimit)
                assertEquals("bolt://neo4j:7687", properties.neo4j.uri)
                assertEquals("neo4j", properties.neo4j.username)
                assertEquals("test-password", properties.neo4j.password)
            }
    }

    @Configuration
    @EnableConfigurationProperties(SearchInfrastructureProperties::class)
    private class SearchInfrastructurePropertiesConfig
}
```

- [ ] **Step 2: Run the focused test and confirm RED**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.search.config.SearchInfrastructurePropertiesTest
```

Expected: FAIL with unresolved references for `articleCollectionName`, `distance`, `defaultLimit`, or `maxLimit`.

- [ ] **Step 3: Add Qdrant properties**

Update the `Qdrant` data class in `backend/src/main/kotlin/com/sigak/search/config/SearchInfrastructureProperties.kt`:

```kotlin
data class Qdrant(
    val url: String = "http://localhost:6333",
    val articleCollectionName: String = "sigak-article-vectors-minilm-v1",
    val distance: String = "Cosine",
    val defaultLimit: Int = 10,
    val maxLimit: Int = 50
)
```

- [ ] **Step 4: Add application defaults**

Update the `sigak.search.qdrant` block in `backend/src/main/resources/application.yml`:

```yaml
    qdrant:
      url: ${SIGAK_QDRANT_URL:http://localhost:6333}
      article-collection-name: ${SIGAK_QDRANT_ARTICLE_COLLECTION:sigak-article-vectors-minilm-v1}
      distance: ${SIGAK_QDRANT_DISTANCE:Cosine}
      default-limit: ${SIGAK_QDRANT_DEFAULT_LIMIT:10}
      max-limit: ${SIGAK_QDRANT_MAX_LIMIT:50}
```

Apply the same block to `backend/src/test/resources/application.yml`.

- [ ] **Step 5: Run the focused test and confirm GREEN**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.search.config.SearchInfrastructurePropertiesTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/sigak/search/config/SearchInfrastructureProperties.kt \
  backend/src/main/resources/application.yml \
  backend/src/test/resources/application.yml \
  backend/src/test/kotlin/com/sigak/search/config/SearchInfrastructurePropertiesTest.kt
git commit -m "feat: add qdrant vector search settings"
```

## Task 2: Ordered Article Reload Boundary

**Files:**

- Modify: `backend/src/main/kotlin/com/sigak/article/service/ArticleService.kt`
- Modify: `backend/src/test/kotlin/com/sigak/article/service/ArticleServiceTest.kt`

- [ ] **Step 1: Write the failing ArticleService test**

Append this test to `backend/src/test/kotlin/com/sigak/article/service/ArticleServiceTest.kt`:

```kotlin
@Test
fun getApiReadyArticlesByIdsReturnsArticlesInRequestedOrder() {
    val articles = articleService.getApiReadyArticlesByIds(listOf(4L, 1L, 4L, 999L))

    assertEquals(listOf(4L, 1L), articles.map { it.id })
}
```

- [ ] **Step 2: Run the focused test and confirm RED**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.article.service.ArticleServiceTest
```

Expected: FAIL with unresolved reference `getApiReadyArticlesByIds`.

- [ ] **Step 3: Expose ordered reload method**

In `backend/src/main/kotlin/com/sigak/article/service/ArticleService.kt`, add this public method near `getArticle`:

```kotlin
@Transactional(readOnly = true)
fun getApiReadyArticlesByIds(articleIds: List<Long>): List<ArticleResponse> =
    findApiReadyArticleResponsesByIds(articleIds)
```

Keep the existing private `findApiReadyArticleResponsesByIds` method unchanged. It already deduplicates IDs, fetches graph relations, and preserves request order.

- [ ] **Step 4: Run the focused test and confirm GREEN**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.article.service.ArticleServiceTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/sigak/article/service/ArticleService.kt \
  backend/src/test/kotlin/com/sigak/article/service/ArticleServiceTest.kt
git commit -m "feat: expose ordered article reload"
```

## Task 3: Article Vector Text Builder

**Files:**

- Create: `backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorTextBuilder.kt`
- Create: `backend/src/test/kotlin/com/sigak/search/vector/ArticleVectorTextBuilderTest.kt`

- [ ] **Step 1: Write the failing text builder test**

Create `backend/src/test/kotlin/com/sigak/search/vector/ArticleVectorTextBuilderTest.kt`:

```kotlin
package com.sigak.search.vector

import com.sigak.article.dto.ArticleResponse
import kotlin.test.Test
import kotlin.test.assertEquals

class ArticleVectorTextBuilderTest {

    private val builder = ArticleVectorTextBuilder()

    @Test
    fun buildsStableEmbeddingInputFromSearchRelevantArticleFields() {
        val article = ArticleResponse(
            id = 3,
            title = "Critical Package Registry Attack Targets AI Toolchains",
            source = "Security Advisory Board",
            url = "https://example.com/articles/ai-toolchain-package-attack",
            publishedAt = "2026-05-03T15:45:00Z",
            eventType = "SECURITY",
            primaryCategory = "SECURITY",
            topics = listOf("supply chain security", "AI tooling"),
            summary = "A coordinated package registry attack targeted developer environments.",
            whyItMatters = "AI development stacks combine packages, credentials, and automation.",
            importanceScore = 93,
            relatedArticleIds = listOf(1, 5)
        )

        val text = builder.build(article)

        assertEquals(
            """
            Title: Critical Package Registry Attack Targets AI Toolchains
            Summary: A coordinated package registry attack targeted developer environments.
            Why it matters: AI development stacks combine packages, credentials, and automation.
            Category: SECURITY
            Topics: supply chain security, AI tooling
            Event type: SECURITY
            """.trimIndent(),
            text
        )
    }
}
```

- [ ] **Step 2: Run the focused test and confirm RED**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.search.vector.ArticleVectorTextBuilderTest
```

Expected: FAIL with unresolved reference `ArticleVectorTextBuilder`.

- [ ] **Step 3: Implement the text builder**

Create `backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorTextBuilder.kt`:

```kotlin
package com.sigak.search.vector

import com.sigak.article.dto.ArticleResponse
import org.springframework.stereotype.Component

@Component
class ArticleVectorTextBuilder {

    fun build(article: ArticleResponse): String =
        listOf(
            "Title: ${article.title}",
            "Summary: ${article.summary}",
            "Why it matters: ${article.whyItMatters}",
            "Category: ${article.primaryCategory}",
            "Topics: ${article.topics.joinToString(", ")}",
            "Event type: ${article.eventType}"
        ).joinToString(separator = "\n")
}
```

- [ ] **Step 4: Run the focused test and confirm GREEN**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.search.vector.ArticleVectorTextBuilderTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorTextBuilder.kt \
  backend/src/test/kotlin/com/sigak/search/vector/ArticleVectorTextBuilderTest.kt
git commit -m "feat: build article vector text"
```

## Task 4: Qdrant Vector Projection Indexer

**Files:**

- Create: `backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorDocument.kt`
- Create: `backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorProjectionIndexer.kt`
- Create: `backend/src/main/kotlin/com/sigak/search/vector/QdrantArticleVectorProjectionIndexer.kt`
- Create: `backend/src/test/kotlin/com/sigak/search/vector/QdrantArticleVectorProjectionIndexerTest.kt`

- [ ] **Step 1: Write the failing Qdrant REST client test**

Create `backend/src/test/kotlin/com/sigak/search/vector/QdrantArticleVectorProjectionIndexerTest.kt`:

```kotlin
package com.sigak.search.vector

import com.sigak.search.config.SearchInfrastructureProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import org.hamcrest.Matchers.containsString
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class QdrantArticleVectorProjectionIndexerTest {

    private val qdrantClientBuilder = RestClient.builder().baseUrl("http://qdrant:6333")
    private val qdrantServer = MockRestServiceServer.bindTo(qdrantClientBuilder).build()
    private val qdrantClient = qdrantClientBuilder.build()
    private val properties = SearchInfrastructureProperties(
        qdrant = SearchInfrastructureProperties.Qdrant(
            url = "http://qdrant:6333",
            articleCollectionName = "test-article-vectors",
            distance = "Cosine",
            defaultLimit = 10,
            maxLimit = 50
        )
    )
    private val indexer = QdrantArticleVectorProjectionIndexer(
        qdrantClient = qdrantClient,
        properties = properties
    )

    @Test
    fun recreatesCollectionAndUpsertsArticleVectorPoints() {
        qdrantServer.expect(ExpectedCount.once(), requestTo("http://qdrant:6333/collections/test-article-vectors"))
            .andExpect(method(HttpMethod.DELETE))
            .andRespond(withSuccess("""{"status":"ok","result":true}""", MediaType.APPLICATION_JSON))
        qdrantServer.expect(ExpectedCount.once(), requestTo("http://qdrant:6333/collections/test-article-vectors"))
            .andExpect(method(HttpMethod.PUT))
            .andExpect(content().string(containsString("\"size\":3")))
            .andExpect(content().string(containsString("\"distance\":\"Cosine\"")))
            .andRespond(withSuccess("""{"status":"ok","result":true}""", MediaType.APPLICATION_JSON))
        qdrantServer.expect(ExpectedCount.once(), requestTo("http://qdrant:6333/collections/test-article-vectors/points?wait=true"))
            .andExpect(method(HttpMethod.PUT))
            .andExpect(content().string(containsString("\"id\":3")))
            .andExpect(content().string(containsString("\"articleId\":3")))
            .andExpect(content().string(containsString("\"embeddingModelName\":\"test-model\"")))
            .andRespond(withSuccess("""{"status":"ok","result":{"status":"completed"}}""", MediaType.APPLICATION_JSON))

        indexer.recreateCollection(dimension = 3)
        indexer.upsertAll(
            listOf(
                ArticleVectorDocument(
                    id = 3,
                    vector = listOf(0.1, 0.2, 0.3),
                    payload = ArticleVectorPayload(
                        articleId = 3,
                        title = "Vector search article",
                        source = "Example",
                        url = "https://example.com/article",
                        publishedAt = "2026-05-29T00:00:00Z",
                        eventType = "AI",
                        primaryCategory = "AI",
                        topics = listOf("vector search"),
                        importanceScore = 90,
                        relatedArticleIds = listOf(1),
                        embeddingProvider = "local",
                        embeddingModelName = "test-model",
                        embeddingDimension = 3
                    )
                )
            )
        )

        qdrantServer.verify()
    }

    @Test
    fun ignoresMissingCollectionWhenRecreatingCollection() {
        qdrantServer.expect(ExpectedCount.once(), requestTo("http://qdrant:6333/collections/test-article-vectors"))
            .andExpect(method(HttpMethod.DELETE))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))
        qdrantServer.expect(ExpectedCount.once(), requestTo("http://qdrant:6333/collections/test-article-vectors"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withSuccess("""{"status":"ok","result":true}""", MediaType.APPLICATION_JSON))

        indexer.recreateCollection(dimension = 3)

        qdrantServer.verify()
    }

    @Test
    fun searchesArticleVectorPoints() {
        qdrantServer.expect(ExpectedCount.once(), requestTo("http://qdrant:6333/collections/test-article-vectors/points/search"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().string(containsString("\"vector\":[0.1,0.2,0.3]")))
            .andExpect(content().string(containsString("\"limit\":2")))
            .andExpect(content().string(containsString("\"with_payload\":true")))
            .andRespond(
                withSuccess(
                    """
                    {
                      "status": "ok",
                      "result": [
                        {"id": 7, "score": 0.91, "payload": {"articleId": 7}},
                        {"id": 3, "score": 0.82, "payload": {"articleId": 3}}
                      ]
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON
                )
            )

        val hits = indexer.search(vector = listOf(0.1, 0.2, 0.3), limit = 2)

        assertEquals(listOf(7L, 3L), hits.map { hit -> hit.articleId })
        assertEquals(listOf(0.91, 0.82), hits.map { hit -> hit.score })
        qdrantServer.verify()
    }
}
```

- [ ] **Step 2: Run the focused test and confirm RED**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.search.vector.QdrantArticleVectorProjectionIndexerTest
```

Expected: FAIL with unresolved references for Qdrant vector classes.

- [ ] **Step 3: Create vector document models**

Create `backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorDocument.kt`:

```kotlin
package com.sigak.search.vector

data class ArticleVectorDocument(
    val id: Long,
    val vector: List<Double>,
    val payload: ArticleVectorPayload
)

data class ArticleVectorPayload(
    val articleId: Long,
    val title: String,
    val source: String,
    val url: String,
    val publishedAt: String,
    val eventType: String,
    val primaryCategory: String,
    val topics: List<String>,
    val importanceScore: Int,
    val relatedArticleIds: List<Long>,
    val embeddingProvider: String,
    val embeddingModelName: String,
    val embeddingDimension: Int
)

data class ArticleVectorSearchHit(
    val articleId: Long,
    val score: Double
)
```

- [ ] **Step 4: Create projection indexer port**

Create `backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorProjectionIndexer.kt`:

```kotlin
package com.sigak.search.vector

interface ArticleVectorProjectionIndexer {
    fun collectionName(): String

    fun deleteCollectionIfExists()

    fun recreateCollection(dimension: Int)

    fun upsertAll(documents: List<ArticleVectorDocument>)

    fun search(vector: List<Double>, limit: Int): List<ArticleVectorSearchHit>
}
```

- [ ] **Step 5: Implement Qdrant REST indexer**

Create `backend/src/main/kotlin/com/sigak/search/vector/QdrantArticleVectorProjectionIndexer.kt`:

```kotlin
package com.sigak.search.vector

import com.fasterxml.jackson.annotation.JsonProperty
import com.sigak.search.config.SearchInfrastructureProperties
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

@Component
class QdrantArticleVectorProjectionIndexer(
    @Qualifier("qdrantRestClient")
    private val qdrantClient: RestClient,
    private val properties: SearchInfrastructureProperties
) : ArticleVectorProjectionIndexer {

    override fun collectionName(): String =
        properties.qdrant.articleCollectionName

    override fun deleteCollectionIfExists() {
        try {
            qdrantClient.delete()
                .uri("/collections/{collectionName}", collectionName())
                .retrieve()
                .toBodilessEntity()
        } catch (exception: RestClientResponseException) {
            if (exception.statusCode != HttpStatus.NOT_FOUND) {
                throw exception
            }
        }
    }

    override fun recreateCollection(dimension: Int) {
        deleteCollectionIfExists()
        createCollection(dimension)
    }

    override fun upsertAll(documents: List<ArticleVectorDocument>) {
        if (documents.isEmpty()) {
            return
        }

        qdrantClient.put()
            .uri("/collections/{collectionName}/points?wait=true", collectionName())
            .body(QdrantUpsertRequest(points = documents.map { document -> document.toPoint() }))
            .retrieve()
            .toBodilessEntity()
    }

    override fun search(vector: List<Double>, limit: Int): List<ArticleVectorSearchHit> {
        val response = qdrantClient.post()
            .uri("/collections/{collectionName}/points/search", collectionName())
            .body(
                QdrantSearchRequest(
                    vector = vector,
                    limit = limit,
                    withPayload = true,
                    withVector = false
                )
            )
            .retrieve()
            .body(QdrantSearchResponse::class.java)
            ?: error("Qdrant search response body must not be empty.")

        return response.result.map { result ->
            ArticleVectorSearchHit(
                articleId = result.payload?.articleId ?: result.id,
                score = result.score
            )
        }
    }

    private fun createCollection(dimension: Int) {
        // Qdrant collection은 하나의 unnamed vector에 대해 dimension과 metric이 고정된다.
        qdrantClient.put()
            .uri("/collections/{collectionName}", collectionName())
            .body(
                QdrantCreateCollectionRequest(
                    vectors = QdrantVectorParams(
                        size = dimension,
                        distance = properties.qdrant.distance
                    )
                )
            )
            .retrieve()
            .toBodilessEntity()
    }

    private fun ArticleVectorDocument.toPoint(): QdrantPoint =
        QdrantPoint(
            id = id,
            vector = vector,
            payload = payload
        )
}

private data class QdrantCreateCollectionRequest(
    val vectors: QdrantVectorParams
)

private data class QdrantVectorParams(
    val size: Int,
    val distance: String
)

private data class QdrantUpsertRequest(
    val points: List<QdrantPoint>
)

private data class QdrantPoint(
    val id: Long,
    val vector: List<Double>,
    val payload: ArticleVectorPayload
)

private data class QdrantSearchRequest(
    val vector: List<Double>,
    val limit: Int,
    @JsonProperty("with_payload")
    val withPayload: Boolean,
    @JsonProperty("with_vector")
    val withVector: Boolean
)

private data class QdrantSearchResponse(
    val result: List<QdrantSearchResult>
)

private data class QdrantSearchResult(
    val id: Long,
    val score: Double,
    val payload: QdrantSearchPayload?
)

private data class QdrantSearchPayload(
    val articleId: Long?
)
```

- [ ] **Step 6: Run the focused test and confirm GREEN**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.search.vector.QdrantArticleVectorProjectionIndexerTest
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorDocument.kt \
  backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorProjectionIndexer.kt \
  backend/src/main/kotlin/com/sigak/search/vector/QdrantArticleVectorProjectionIndexer.kt \
  backend/src/test/kotlin/com/sigak/search/vector/QdrantArticleVectorProjectionIndexerTest.kt
git commit -m "feat: add qdrant article vector indexer"
```

## Task 5: Article Vector Projection Rebuild

**Files:**

- Create: `backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorProjectionRebuildResponse.kt`
- Create: `backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorProjectionRebuildService.kt`
- Create: `backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorProjectionController.kt`
- Create: `backend/src/test/kotlin/com/sigak/search/vector/ArticleVectorProjectionRebuildServiceTest.kt`
- Create: `backend/src/test/kotlin/com/sigak/search/vector/ArticleVectorProjectionControllerTest.kt`

- [ ] **Step 1: Write the failing rebuild service test**

Create `backend/src/test/kotlin/com/sigak/search/vector/ArticleVectorProjectionRebuildServiceTest.kt`:

```kotlin
package com.sigak.search.vector

import com.sigak.ai.embedding.EmbeddingClient
import com.sigak.ai.embedding.EmbeddingResponse
import com.sigak.article.dto.ArticleResponse
import com.sigak.article.service.ArticleService
import kotlin.test.Test
import kotlin.test.assertEquals
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class ArticleVectorProjectionRebuildServiceTest {

    private val articleService = mock(ArticleService::class.java)
    private val embeddingClient = mock(EmbeddingClient::class.java)
    private val textBuilder = ArticleVectorTextBuilder()
    private val indexer = RecordingArticleVectorProjectionIndexer()
    private val rebuildService = ArticleVectorProjectionRebuildService(
        articleService = articleService,
        embeddingClient = embeddingClient,
        textBuilder = textBuilder,
        indexer = indexer
    )

    @Test
    fun rebuildEmbedsArticlesAndUpsertsVectorsIntoQdrant() {
        val article = articleResponse(id = 3)
        `when`(articleService.getArticles(null)).thenReturn(listOf(article))
        `when`(embeddingClient.embedText(textBuilder.build(article)))
            .thenReturn(
                EmbeddingResponse(
                    provider = "local",
                    modelName = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
                    dimension = 3,
                    embedding = listOf(0.1, 0.2, 0.3)
                )
            )

        val response = rebuildService.rebuild()

        assertEquals("completed", response.status)
        assertEquals("test-article-vectors", response.collectionName)
        assertEquals(1, response.indexedCount)
        assertEquals("local", response.embeddingProvider)
        assertEquals("sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2", response.embeddingModelName)
        assertEquals(3, response.embeddingDimension)
        assertEquals(null, response.failedReason)
        assertEquals(3, indexer.recreatedDimension)
        assertEquals(listOf(3L), indexer.documents.map { document -> document.id })
        assertEquals("Vector Search Article", indexer.documents.first().payload.title)
    }

    @Test
    fun rebuildFailsWhenEmbeddingMetadataChangesWithinSameCollection() {
        val firstArticle = articleResponse(id = 1)
        val secondArticle = articleResponse(id = 2)
        `when`(articleService.getArticles(null)).thenReturn(listOf(firstArticle, secondArticle))
        `when`(embeddingClient.embedText(textBuilder.build(firstArticle)))
            .thenReturn(
                EmbeddingResponse(
                    provider = "local",
                    modelName = "model-a",
                    dimension = 3,
                    embedding = listOf(0.1, 0.2, 0.3)
                )
            )
        `when`(embeddingClient.embedText(textBuilder.build(secondArticle)))
            .thenReturn(
                EmbeddingResponse(
                    provider = "local",
                    modelName = "model-a",
                    dimension = 4,
                    embedding = listOf(0.1, 0.2, 0.3, 0.4)
                )
            )

        val response = rebuildService.rebuild()

        assertEquals("failed", response.status)
        assertEquals(0, response.indexedCount)
        assertEquals("test-article-vectors", response.collectionName)
        assertEquals(true, response.failedReason!!.contains("Embedding metadata mismatch"))
        assertEquals(null, indexer.recreatedDimension)
        assertEquals(0, indexer.deleteCount)
        assertEquals(emptyList(), indexer.documents)
    }

    @Test
    fun rebuildDeletesCollectionWhenThereAreNoApiReadyArticles() {
        `when`(articleService.getArticles(null)).thenReturn(emptyList())

        val response = rebuildService.rebuild()

        assertEquals("completed", response.status)
        assertEquals(0, response.indexedCount)
        assertEquals(1, indexer.deleteCount)
        assertEquals(null, indexer.recreatedDimension)
        assertEquals(emptyList(), indexer.documents)
    }

    private fun articleResponse(id: Long): ArticleResponse =
        ArticleResponse(
            id = id,
            title = "Vector Search Article",
            source = "Example",
            url = "https://example.com/article-$id",
            publishedAt = "2026-05-29T00:00:00Z",
            eventType = "AI",
            primaryCategory = "AI",
            topics = listOf("vector search"),
            summary = "Qdrant stores article embeddings.",
            whyItMatters = "Semantic search helps readers find conceptually related news.",
            importanceScore = 90,
            relatedArticleIds = listOf(1)
        )

    private class RecordingArticleVectorProjectionIndexer : ArticleVectorProjectionIndexer {
        var deleteCount: Int = 0
        var recreatedDimension: Int? = null
        var documents: List<ArticleVectorDocument> = emptyList()

        override fun collectionName(): String = "test-article-vectors"

        override fun deleteCollectionIfExists() {
            deleteCount += 1
        }

        override fun recreateCollection(dimension: Int) {
            deleteCollectionIfExists()
            recreatedDimension = dimension
        }

        override fun upsertAll(documents: List<ArticleVectorDocument>) {
            this.documents = documents
        }

        override fun search(vector: List<Double>, limit: Int): List<ArticleVectorSearchHit> =
            emptyList()
    }
}
```

- [ ] **Step 2: Run the focused test and confirm RED**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.search.vector.ArticleVectorProjectionRebuildServiceTest
```

Expected: FAIL with unresolved references for rebuild response/service.

- [ ] **Step 3: Implement rebuild response**

Create `backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorProjectionRebuildResponse.kt`:

```kotlin
package com.sigak.search.vector

data class ArticleVectorProjectionRebuildResponse(
    val status: String,
    val collectionName: String,
    val indexedCount: Int,
    val embeddingProvider: String?,
    val embeddingModelName: String?,
    val embeddingDimension: Int?,
    val durationMs: Long,
    val failedReason: String?
)
```

- [ ] **Step 4: Implement rebuild service**

Create `backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorProjectionRebuildService.kt`:

```kotlin
package com.sigak.search.vector

import com.sigak.ai.embedding.EmbeddingClient
import com.sigak.ai.embedding.EmbeddingResponse
import com.sigak.article.dto.ArticleResponse
import com.sigak.article.service.ArticleService
import org.springframework.stereotype.Service

@Service
class ArticleVectorProjectionRebuildService(
    private val articleService: ArticleService,
    private val embeddingClient: EmbeddingClient,
    private val textBuilder: ArticleVectorTextBuilder,
    private val indexer: ArticleVectorProjectionIndexer
) {

    fun rebuild(): ArticleVectorProjectionRebuildResponse {
        val collectionName = indexer.collectionName()
        var indexedCount = 0
        var embeddingMetadata: EmbeddingMetadata? = null
        var failedReason: String? = null

        val durationMs = measureTimeMillis {
            try {
                val articles = articleService.getArticles(null)
                if (articles.isEmpty()) {
                    indexer.deleteCollectionIfExists()
                    indexedCount = 0
                    return@measureTimeMillis
                }

                val documents = articles.map { article ->
                    val embedding = embeddingClient.embedText(textBuilder.build(article))
                    val currentMetadata = embedding.toMetadata()
                    if (embeddingMetadata == null) {
                        embeddingMetadata = currentMetadata
                    }
                    require(embeddingMetadata == currentMetadata) {
                        "Embedding metadata mismatch. expected=$embeddingMetadata actual=$currentMetadata"
                    }
                    article.toVectorDocument(embedding)
                }

                val dimension = requireNotNull(embeddingMetadata).dimension
                indexer.recreateCollection(dimension)
                indexer.upsertAll(documents)
                indexedCount = documents.size
            } catch (exception: Exception) {
                // Projection rebuild는 원본 PostgreSQL을 바꾸지 않으므로 실패를 응답으로 노출하고 재시도 가능하게 둔다.
                failedReason = exception.message ?: exception::class.simpleName
            }
        }

        return ArticleVectorProjectionRebuildResponse(
            status = if (failedReason == null) COMPLETED else FAILED,
            collectionName = collectionName,
            indexedCount = indexedCount,
            embeddingProvider = embeddingMetadata?.provider,
            embeddingModelName = embeddingMetadata?.modelName,
            embeddingDimension = embeddingMetadata?.dimension,
            durationMs = durationMs,
            failedReason = failedReason
        )
    }

    private fun ArticleResponse.toVectorDocument(embedding: EmbeddingResponse): ArticleVectorDocument =
        ArticleVectorDocument(
            id = id,
            vector = embedding.embedding,
            payload = ArticleVectorPayload(
                articleId = id,
                title = title,
                source = source,
                url = url,
                publishedAt = publishedAt,
                eventType = eventType,
                primaryCategory = primaryCategory,
                topics = topics,
                importanceScore = importanceScore,
                relatedArticleIds = relatedArticleIds,
                embeddingProvider = embedding.provider,
                embeddingModelName = embedding.modelName,
                embeddingDimension = embedding.dimension
            )
        )

    private fun EmbeddingResponse.toMetadata(): EmbeddingMetadata =
        EmbeddingMetadata(
            provider = provider,
            modelName = modelName,
            dimension = dimension
        )

    private data class EmbeddingMetadata(
        val provider: String,
        val modelName: String,
        val dimension: Int
    )

    private companion object {
        const val COMPLETED = "completed"
        const val FAILED = "failed"
    }
}
```

- [ ] **Step 5: Run rebuild service test and confirm GREEN**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.search.vector.ArticleVectorProjectionRebuildServiceTest
```

Expected: PASS.

- [ ] **Step 6: Write the failing rebuild controller test**

Create `backend/src/test/kotlin/com/sigak/search/vector/ArticleVectorProjectionControllerTest.kt`:

```kotlin
package com.sigak.search.vector

import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(ArticleVectorProjectionController::class)
class ArticleVectorProjectionControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var rebuildService: ArticleVectorProjectionRebuildService

    @Test
    fun rebuildArticleVectorProjectionReturnsIndexingMetrics() {
        `when`(rebuildService.rebuild())
            .thenReturn(
                ArticleVectorProjectionRebuildResponse(
                    status = "completed",
                    collectionName = "sigak-article-vectors-minilm-v1",
                    indexedCount = 5,
                    embeddingProvider = "local",
                    embeddingModelName = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
                    embeddingDimension = 384,
                    durationMs = 42,
                    failedReason = null
                )
            )

        mockMvc.perform(post("/api/internal/search-projections/article-vectors/rebuild"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("completed"))
            .andExpect(jsonPath("$.collectionName").value("sigak-article-vectors-minilm-v1"))
            .andExpect(jsonPath("$.indexedCount").value(5))
            .andExpect(jsonPath("$.embeddingProvider").value("local"))
            .andExpect(jsonPath("$.embeddingDimension").value(384))
            .andExpect(jsonPath("$.durationMs").value(42))
            .andExpect(jsonPath("$.failedReason").doesNotExist())
    }
}
```

- [ ] **Step 7: Run controller test and confirm RED**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.search.vector.ArticleVectorProjectionControllerTest
```

Expected: FAIL with unresolved reference `ArticleVectorProjectionController`.

- [ ] **Step 8: Implement rebuild controller**

Create `backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorProjectionController.kt`:

```kotlin
package com.sigak.search.vector

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/internal/search-projections/article-vectors")
@Tag(name = "Internal Vector Projections", description = "Local rebuild triggers for Qdrant article vector projections.")
class ArticleVectorProjectionController(
    private val rebuildService: ArticleVectorProjectionRebuildService
) {

    @PostMapping("/rebuild")
    @Operation(
        summary = "Rebuild article vector projection",
        description = "Embeds API-ready articles from PostgreSQL and upserts them into the Qdrant article vector collection."
    )
    fun rebuild(): ArticleVectorProjectionRebuildResponse =
        rebuildService.rebuild()
}
```

- [ ] **Step 9: Run rebuild tests and confirm GREEN**

Run:

```bash
cd backend
./gradlew test --tests 'com.sigak.search.vector.ArticleVectorProjection*'
```

Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorProjectionRebuildResponse.kt \
  backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorProjectionRebuildService.kt \
  backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorProjectionController.kt \
  backend/src/test/kotlin/com/sigak/search/vector/ArticleVectorProjectionRebuildServiceTest.kt \
  backend/src/test/kotlin/com/sigak/search/vector/ArticleVectorProjectionControllerTest.kt
git commit -m "feat: add article vector projection rebuild"
```

## Task 6: Internal Vector Search API

**Files:**

- Create: `backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorSearchRequest.kt`
- Create: `backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorSearchResponse.kt`
- Create: `backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorSearchService.kt`
- Create: `backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorSearchController.kt`
- Create: `backend/src/test/kotlin/com/sigak/search/vector/ArticleVectorSearchServiceTest.kt`
- Create: `backend/src/test/kotlin/com/sigak/search/vector/ArticleVectorSearchControllerTest.kt`

- [ ] **Step 1: Write the failing vector search service test**

Create `backend/src/test/kotlin/com/sigak/search/vector/ArticleVectorSearchServiceTest.kt`:

```kotlin
package com.sigak.search.vector

import com.sigak.ai.embedding.EmbeddingClient
import com.sigak.ai.embedding.EmbeddingResponse
import com.sigak.article.dto.ArticleResponse
import com.sigak.article.service.ArticleService
import com.sigak.search.config.SearchInfrastructureProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class ArticleVectorSearchServiceTest {

    private val embeddingClient = mock(EmbeddingClient::class.java)
    private val articleService = mock(ArticleService::class.java)
    private val indexer = RecordingArticleVectorProjectionIndexer()
    private val metricsRecorder = ArticleVectorSearchMetricsRecorder()
    private val properties = SearchInfrastructureProperties(
        qdrant = SearchInfrastructureProperties.Qdrant(
            articleCollectionName = "test-article-vectors",
            defaultLimit = 10,
            maxLimit = 50
        )
    )
    private val searchService = ArticleVectorSearchService(
        embeddingClient = embeddingClient,
        articleService = articleService,
        indexer = indexer,
        metricsRecorder = metricsRecorder,
        properties = properties
    )

    @Test
    fun searchesQdrantAndReloadsArticlesInVectorResultOrder() {
        `when`(embeddingClient.embedText("AI supply chain security"))
            .thenReturn(
                EmbeddingResponse(
                    provider = "local",
                    modelName = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
                    dimension = 3,
                    embedding = listOf(0.1, 0.2, 0.3)
                )
            )
        indexer.hits = listOf(
            ArticleVectorSearchHit(articleId = 7, score = 0.91),
            ArticleVectorSearchHit(articleId = 3, score = 0.82)
        )
        `when`(articleService.getApiReadyArticlesByIds(listOf(7L, 3L)))
            .thenReturn(listOf(articleResponse(7), articleResponse(3)))

        val response = searchService.search(ArticleVectorSearchRequest(query = " AI supply chain security ", limit = 2))

        assertEquals("AI supply chain security", response.query)
        assertEquals("test-article-vectors", response.collectionName)
        assertEquals("local", response.embeddingProvider)
        assertEquals(3, response.embeddingDimension)
        assertEquals(listOf(7L, 3L), response.results.map { result -> result.article.id })
        assertEquals(listOf(0.91, 0.82), response.results.map { result -> result.score })
        assertEquals(listOf(0.1, 0.2, 0.3), indexer.requestedVector)
        assertEquals(2, indexer.requestedLimit)
        assertEquals(1, metricsRecorder.summarize().totalSearchCount)
    }

    @Test
    fun rejectsBlankQuery() {
        val exception = kotlin.runCatching {
            searchService.search(ArticleVectorSearchRequest(query = "   ", limit = 2))
        }.exceptionOrNull()

        assertEquals("Vector search query must not be blank.", exception!!.message)
    }

    @Test
    fun rejectsLimitGreaterThanConfiguredMaximum() {
        val exception = kotlin.runCatching {
            searchService.search(ArticleVectorSearchRequest(query = "AI", limit = 51))
        }.exceptionOrNull()

        assertEquals("Vector search limit must be between 1 and 50.", exception!!.message)
    }

    private fun articleResponse(id: Long): ArticleResponse =
        ArticleResponse(
            id = id,
            title = "Article $id",
            source = "Example",
            url = "https://example.com/article-$id",
            publishedAt = "2026-05-29T00:00:00Z",
            eventType = "AI",
            primaryCategory = "AI",
            topics = listOf("vector search"),
            summary = "Summary $id",
            whyItMatters = "Reason $id",
            importanceScore = 90,
            relatedArticleIds = emptyList()
        )

    private class RecordingArticleVectorProjectionIndexer : ArticleVectorProjectionIndexer {
        var requestedVector: List<Double> = emptyList()
        var requestedLimit: Int = 0
        var hits: List<ArticleVectorSearchHit> = emptyList()

        override fun collectionName(): String = "test-article-vectors"

        override fun deleteCollectionIfExists() = Unit

        override fun recreateCollection(dimension: Int) = Unit

        override fun upsertAll(documents: List<ArticleVectorDocument>) = Unit

        override fun search(vector: List<Double>, limit: Int): List<ArticleVectorSearchHit> {
            requestedVector = vector
            requestedLimit = limit
            return hits
        }
    }
}
```

- [ ] **Step 2: Run the focused service test and confirm RED**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.search.vector.ArticleVectorSearchServiceTest
```

Expected: FAIL with unresolved references for search request/response/service/metrics.

- [ ] **Step 3: Create vector search request and response DTOs**

Create `backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorSearchRequest.kt`:

```kotlin
package com.sigak.search.vector

data class ArticleVectorSearchRequest(
    val query: String,
    val limit: Int? = null
)
```

Create `backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorSearchResponse.kt`:

```kotlin
package com.sigak.search.vector

import com.sigak.article.dto.ArticleResponse

data class ArticleVectorSearchResponse(
    val query: String,
    val collectionName: String,
    val embeddingProvider: String,
    val embeddingModelName: String,
    val embeddingDimension: Int,
    val results: List<ArticleVectorSearchResult>,
    val timings: ArticleVectorSearchTimings
)

data class ArticleVectorSearchResult(
    val score: Double,
    val article: ArticleResponse
)

data class ArticleVectorSearchTimings(
    val embeddingElapsedMs: Long,
    val qdrantElapsedMs: Long,
    val articleLoadElapsedMs: Long,
    val totalElapsedMs: Long
)
```

- [ ] **Step 4: Add vector metrics classes needed by the service**

Create `backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorSearchMetricObservation.kt`:

```kotlin
package com.sigak.search.vector

data class ArticleVectorSearchMetricObservation(
    val queryLength: Int,
    val resultCount: Int,
    val embeddingElapsedMs: Long,
    val qdrantElapsedMs: Long,
    val articleLoadElapsedMs: Long,
    val totalElapsedMs: Long
)
```

Create `backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorSearchMetricsResponse.kt`:

```kotlin
package com.sigak.search.vector

data class ArticleVectorSearchMetricsResponse(
    val totalSearchCount: Long,
    val averageTotalElapsedMs: Double,
    val p50TotalElapsedMs: Long,
    val p95TotalElapsedMs: Long,
    val averageEmbeddingElapsedMs: Double,
    val averageQdrantElapsedMs: Double,
    val averageArticleLoadElapsedMs: Double,
    val lastSearch: ArticleVectorSearchMetricSnapshotResponse?
)

data class ArticleVectorSearchMetricSnapshotResponse(
    val queryLength: Int,
    val resultCount: Int,
    val embeddingElapsedMs: Long,
    val qdrantElapsedMs: Long,
    val articleLoadElapsedMs: Long,
    val totalElapsedMs: Long
)
```

Create `backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorSearchMetricsRecorder.kt`:

```kotlin
package com.sigak.search.vector

import org.springframework.stereotype.Service

@Service
class ArticleVectorSearchMetricsRecorder {

    private val observations = ArrayDeque<ArticleVectorSearchMetricObservation>()

    @Synchronized
    fun record(observation: ArticleVectorSearchMetricObservation) {
        observations.addLast(observation)
        if (observations.size > MAX_RECENT_OBSERVATIONS) {
            observations.removeFirst()
        }
    }

    @Synchronized
    fun reset() {
        observations.clear()
    }

    @Synchronized
    fun summarize(): ArticleVectorSearchMetricsResponse {
        if (observations.isEmpty()) {
            return ArticleVectorSearchMetricsResponse(
                totalSearchCount = 0,
                averageTotalElapsedMs = 0.0,
                p50TotalElapsedMs = 0,
                p95TotalElapsedMs = 0,
                averageEmbeddingElapsedMs = 0.0,
                averageQdrantElapsedMs = 0.0,
                averageArticleLoadElapsedMs = 0.0,
                lastSearch = null
            )
        }

        val snapshot = observations.toList()
        val totalLatencies = snapshot.map { observation -> observation.totalElapsedMs }.sorted()

        return ArticleVectorSearchMetricsResponse(
            totalSearchCount = snapshot.size.toLong(),
            averageTotalElapsedMs = totalLatencies.average(),
            p50TotalElapsedMs = percentile(totalLatencies, 0.50),
            p95TotalElapsedMs = percentile(totalLatencies, 0.95),
            averageEmbeddingElapsedMs = snapshot.map { observation -> observation.embeddingElapsedMs }.average(),
            averageQdrantElapsedMs = snapshot.map { observation -> observation.qdrantElapsedMs }.average(),
            averageArticleLoadElapsedMs = snapshot.map { observation -> observation.articleLoadElapsedMs }.average(),
            lastSearch = snapshot.last().toSnapshotResponse()
        )
    }

    private fun percentile(sortedValues: List<Long>, percentile: Double): Long {
        val index = kotlin.math.ceil(sortedValues.size * percentile).toInt().coerceAtLeast(1) - 1

        return sortedValues[index.coerceAtMost(sortedValues.lastIndex)]
    }

    private fun ArticleVectorSearchMetricObservation.toSnapshotResponse(): ArticleVectorSearchMetricSnapshotResponse =
        ArticleVectorSearchMetricSnapshotResponse(
            queryLength = queryLength,
            resultCount = resultCount,
            embeddingElapsedMs = embeddingElapsedMs,
            qdrantElapsedMs = qdrantElapsedMs,
            articleLoadElapsedMs = articleLoadElapsedMs,
            totalElapsedMs = totalElapsedMs
        )

    private companion object {
        private const val MAX_RECENT_OBSERVATIONS = 100
    }
}
```

- [ ] **Step 5: Implement vector search service**

Create `backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorSearchService.kt`:

```kotlin
package com.sigak.search.vector

import com.sigak.ai.embedding.EmbeddingClient
import com.sigak.article.service.ArticleService
import com.sigak.search.config.SearchInfrastructureProperties
import kotlin.system.measureTimeMillis
import org.springframework.stereotype.Service

@Service
class ArticleVectorSearchService(
    private val embeddingClient: EmbeddingClient,
    private val articleService: ArticleService,
    private val indexer: ArticleVectorProjectionIndexer,
    private val metricsRecorder: ArticleVectorSearchMetricsRecorder,
    private val properties: SearchInfrastructureProperties
) {

    fun search(request: ArticleVectorSearchRequest): ArticleVectorSearchResponse {
        val normalizedQuery = request.query.trim()
        require(normalizedQuery.isNotBlank()) { "Vector search query must not be blank." }
        val limit = request.limit ?: properties.qdrant.defaultLimit
        require(limit in 1..properties.qdrant.maxLimit) {
            "Vector search limit must be between 1 and ${properties.qdrant.maxLimit}."
        }

        val totalStartedAt = System.nanoTime()
        var embeddingElapsedMs = 0L
        var qdrantElapsedMs = 0L
        var articleLoadElapsedMs = 0L

        val embedding = measureTimedValue {
            embeddingClient.embedText(normalizedQuery)
        }.also { result -> embeddingElapsedMs = result.elapsedMs }.value

        val hits = measureTimedValue {
            indexer.search(vector = embedding.embedding, limit = limit)
        }.also { result -> qdrantElapsedMs = result.elapsedMs }.value

        val articles = measureTimedValue {
            articleService.getApiReadyArticlesByIds(hits.map { hit -> hit.articleId })
        }.also { result -> articleLoadElapsedMs = result.elapsedMs }.value

        val scoresByArticleId = hits.associate { hit -> hit.articleId to hit.score }
        val results = articles.map { article ->
            ArticleVectorSearchResult(
                score = requireNotNull(scoresByArticleId[article.id]),
                article = article
            )
        }
        val totalElapsedMs = elapsedMillis(totalStartedAt)

        metricsRecorder.record(
            ArticleVectorSearchMetricObservation(
                queryLength = normalizedQuery.length,
                resultCount = results.size,
                embeddingElapsedMs = embeddingElapsedMs,
                qdrantElapsedMs = qdrantElapsedMs,
                articleLoadElapsedMs = articleLoadElapsedMs,
                totalElapsedMs = totalElapsedMs
            )
        )

        return ArticleVectorSearchResponse(
            query = normalizedQuery,
            collectionName = indexer.collectionName(),
            embeddingProvider = embedding.provider,
            embeddingModelName = embedding.modelName,
            embeddingDimension = embedding.dimension,
            results = results,
            timings = ArticleVectorSearchTimings(
                embeddingElapsedMs = embeddingElapsedMs,
                qdrantElapsedMs = qdrantElapsedMs,
                articleLoadElapsedMs = articleLoadElapsedMs,
                totalElapsedMs = totalElapsedMs
            )
        )
    }

    private fun <T> measureTimedValue(block: () -> T): TimedValue<T> {
        val startedAt = System.nanoTime()
        val value = block()

        return TimedValue(value = value, elapsedMs = elapsedMillis(startedAt))
    }

    private fun elapsedMillis(startedAt: Long): Long =
        (System.nanoTime() - startedAt) / 1_000_000

    private data class TimedValue<T>(
        val value: T,
        val elapsedMs: Long
    )
}
```

- [ ] **Step 6: Run vector search service test and confirm GREEN**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.search.vector.ArticleVectorSearchServiceTest
```

Expected: PASS.

- [ ] **Step 7: Write the failing vector search controller test**

Create `backend/src/test/kotlin/com/sigak/search/vector/ArticleVectorSearchControllerTest.kt`:

```kotlin
package com.sigak.search.vector

import com.fasterxml.jackson.databind.ObjectMapper
import com.sigak.article.dto.ArticleResponse
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(ArticleVectorSearchController::class)
class ArticleVectorSearchControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var searchService: ArticleVectorSearchService

    @Test
    fun searchesArticlesByVectorSimilarity() {
        val request = ArticleVectorSearchRequest(query = "AI supply chain security", limit = 2)
        `when`(searchService.search(request))
            .thenReturn(
                ArticleVectorSearchResponse(
                    query = "AI supply chain security",
                    collectionName = "sigak-article-vectors-minilm-v1",
                    embeddingProvider = "local",
                    embeddingModelName = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
                    embeddingDimension = 384,
                    results = listOf(
                        ArticleVectorSearchResult(
                            score = 0.91,
                            article = articleResponse(7)
                        )
                    ),
                    timings = ArticleVectorSearchTimings(
                        embeddingElapsedMs = 10,
                        qdrantElapsedMs = 5,
                        articleLoadElapsedMs = 3,
                        totalElapsedMs = 18
                    )
                )
            )

        mockMvc.perform(
            post("/api/internal/vector-search/articles")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.query").value("AI supply chain security"))
            .andExpect(jsonPath("$.collectionName").value("sigak-article-vectors-minilm-v1"))
            .andExpect(jsonPath("$.embeddingProvider").value("local"))
            .andExpect(jsonPath("$.results[0].score").value(0.91))
            .andExpect(jsonPath("$.results[0].article.id").value(7))
            .andExpect(jsonPath("$.timings.totalElapsedMs").value(18))
    }

    @Test
    fun returnsBadRequestWhenServiceRejectsInput() {
        val request = ArticleVectorSearchRequest(query = " ", limit = 2)
        `when`(searchService.search(request))
            .thenThrow(IllegalArgumentException("Vector search query must not be blank."))

        mockMvc.perform(
            post("/api/internal/vector-search/articles")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)
    }

    private fun articleResponse(id: Long): ArticleResponse =
        ArticleResponse(
            id = id,
            title = "Article $id",
            source = "Example",
            url = "https://example.com/article-$id",
            publishedAt = "2026-05-29T00:00:00Z",
            eventType = "AI",
            primaryCategory = "AI",
            topics = listOf("vector search"),
            summary = "Summary $id",
            whyItMatters = "Reason $id",
            importanceScore = 90,
            relatedArticleIds = emptyList()
        )
}
```

- [ ] **Step 8: Run controller test and confirm RED**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.search.vector.ArticleVectorSearchControllerTest
```

Expected: FAIL with unresolved reference `ArticleVectorSearchController`.

- [ ] **Step 9: Implement vector search controller**

Create `backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorSearchController.kt`:

```kotlin
package com.sigak.search.vector

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/internal/vector-search/articles")
@Tag(name = "Internal Vector Search", description = "Local semantic article search backed by FastAPI embeddings and Qdrant.")
class ArticleVectorSearchController(
    private val searchService: ArticleVectorSearchService
) {

    @PostMapping
    @Operation(
        summary = "Search articles by vector similarity",
        description = "Embeds the query, searches the Qdrant article vector collection, and reloads article responses from PostgreSQL."
    )
    fun search(@RequestBody request: ArticleVectorSearchRequest): ArticleVectorSearchResponse =
        searchService.search(request)

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleInvalidRequest(exception: IllegalArgumentException): Map<String, String> =
        mapOf("message" to (exception.message ?: "Invalid vector search request."))
}
```

- [ ] **Step 10: Run vector search tests and confirm GREEN**

Run:

```bash
cd backend
./gradlew test --tests 'com.sigak.search.vector.ArticleVectorSearch*'
```

Expected: PASS.

- [ ] **Step 11: Commit**

```bash
git add backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorSearchRequest.kt \
  backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorSearchResponse.kt \
  backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorSearchService.kt \
  backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorSearchController.kt \
  backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorSearchMetricObservation.kt \
  backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorSearchMetricsResponse.kt \
  backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorSearchMetricsRecorder.kt \
  backend/src/test/kotlin/com/sigak/search/vector/ArticleVectorSearchServiceTest.kt \
  backend/src/test/kotlin/com/sigak/search/vector/ArticleVectorSearchControllerTest.kt
git commit -m "feat: add internal article vector search"
```

## Task 7: Vector Search Metrics API

**Files:**

- Create: `backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorSearchMetricsController.kt`
- Create: `backend/src/test/kotlin/com/sigak/search/vector/ArticleVectorSearchMetricsRecorderTest.kt`
- Create: `backend/src/test/kotlin/com/sigak/search/vector/ArticleVectorSearchMetricsControllerTest.kt`

- [ ] **Step 1: Write the metrics recorder test**

Create `backend/src/test/kotlin/com/sigak/search/vector/ArticleVectorSearchMetricsRecorderTest.kt`:

```kotlin
package com.sigak.search.vector

import kotlin.test.Test
import kotlin.test.assertEquals

class ArticleVectorSearchMetricsRecorderTest {

    private val recorder = ArticleVectorSearchMetricsRecorder()

    @Test
    fun summarizesVectorSearchTimings() {
        recorder.record(observation(total = 10, embedding = 4, qdrant = 3, articleLoad = 3))
        recorder.record(observation(total = 20, embedding = 8, qdrant = 7, articleLoad = 5))

        val response = recorder.summarize()

        assertEquals(2, response.totalSearchCount)
        assertEquals(15.0, response.averageTotalElapsedMs)
        assertEquals(10, response.p50TotalElapsedMs)
        assertEquals(20, response.p95TotalElapsedMs)
        assertEquals(6.0, response.averageEmbeddingElapsedMs)
        assertEquals(5.0, response.averageQdrantElapsedMs)
        assertEquals(4.0, response.averageArticleLoadElapsedMs)
        assertEquals(20, response.lastSearch!!.totalElapsedMs)
    }

    @Test
    fun returnsEmptySummaryWhenNoSearchesRecorded() {
        val response = recorder.summarize()

        assertEquals(0, response.totalSearchCount)
        assertEquals(0.0, response.averageTotalElapsedMs)
        assertEquals(0, response.p50TotalElapsedMs)
        assertEquals(0, response.p95TotalElapsedMs)
        assertEquals(null, response.lastSearch)
    }

    private fun observation(
        total: Long,
        embedding: Long,
        qdrant: Long,
        articleLoad: Long
    ): ArticleVectorSearchMetricObservation =
        ArticleVectorSearchMetricObservation(
            queryLength = 12,
            resultCount = 3,
            embeddingElapsedMs = embedding,
            qdrantElapsedMs = qdrant,
            articleLoadElapsedMs = articleLoad,
            totalElapsedMs = total
        )
}
```

- [ ] **Step 2: Run metrics recorder test and confirm GREEN**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.search.vector.ArticleVectorSearchMetricsRecorderTest
```

Expected: PASS because Task 6 already created the recorder. If it fails, fix `ArticleVectorSearchMetricsRecorder` before continuing.

- [ ] **Step 3: Write the failing metrics controller test**

Create `backend/src/test/kotlin/com/sigak/search/vector/ArticleVectorSearchMetricsControllerTest.kt`:

```kotlin
package com.sigak.search.vector

import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(ArticleVectorSearchMetricsController::class)
class ArticleVectorSearchMetricsControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var metricsRecorder: ArticleVectorSearchMetricsRecorder

    @Test
    fun returnsVectorSearchMetrics() {
        `when`(metricsRecorder.summarize())
            .thenReturn(
                ArticleVectorSearchMetricsResponse(
                    totalSearchCount = 2,
                    averageTotalElapsedMs = 15.0,
                    p50TotalElapsedMs = 10,
                    p95TotalElapsedMs = 20,
                    averageEmbeddingElapsedMs = 6.0,
                    averageQdrantElapsedMs = 5.0,
                    averageArticleLoadElapsedMs = 4.0,
                    lastSearch = ArticleVectorSearchMetricSnapshotResponse(
                        queryLength = 12,
                        resultCount = 3,
                        embeddingElapsedMs = 8,
                        qdrantElapsedMs = 7,
                        articleLoadElapsedMs = 5,
                        totalElapsedMs = 20
                    )
                )
            )

        mockMvc.perform(get("/api/internal/search-metrics/article-vectors"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalSearchCount").value(2))
            .andExpect(jsonPath("$.averageTotalElapsedMs").value(15.0))
            .andExpect(jsonPath("$.p95TotalElapsedMs").value(20))
            .andExpect(jsonPath("$.averageEmbeddingElapsedMs").value(6.0))
            .andExpect(jsonPath("$.lastSearch.totalElapsedMs").value(20))
    }
}
```

- [ ] **Step 4: Run metrics controller test and confirm RED**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.search.vector.ArticleVectorSearchMetricsControllerTest
```

Expected: FAIL with unresolved reference `ArticleVectorSearchMetricsController`.

- [ ] **Step 5: Implement metrics controller**

Create `backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorSearchMetricsController.kt`:

```kotlin
package com.sigak.search.vector

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/internal/search-metrics/article-vectors")
@Tag(name = "Internal Vector Search Metrics", description = "Local vector search latency summaries for MVP development.")
class ArticleVectorSearchMetricsController(
    private val metricsRecorder: ArticleVectorSearchMetricsRecorder
) {

    @GetMapping
    @Operation(
        summary = "Get article vector search metrics",
        description = "Returns in-memory embedding, Qdrant, article reload, and total latency metrics collected since backend startup."
    )
    fun getMetrics(): ArticleVectorSearchMetricsResponse =
        metricsRecorder.summarize()
}
```

- [ ] **Step 6: Run metrics tests and confirm GREEN**

Run:

```bash
cd backend
./gradlew test --tests 'com.sigak.search.vector.ArticleVectorSearchMetrics*'
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorSearchMetricsController.kt \
  backend/src/test/kotlin/com/sigak/search/vector/ArticleVectorSearchMetricsRecorderTest.kt \
  backend/src/test/kotlin/com/sigak/search/vector/ArticleVectorSearchMetricsControllerTest.kt
git commit -m "feat: add article vector search metrics"
```

## Task 8: Documentation Updates

**Files:**

- Modify: `README.md`
- Modify: `README.ko.md`
- Modify: `backend/README.md`
- Modify: `backend/README.ko.md`
- Modify: `docs/API_SPEC.md`
- Modify: `docs/API_SPEC.ko.md`
- Modify: `docs/STATUS.md`
- Modify: `docs/STATUS.ko.md`
- Modify: `docs/blog/topic-queue.md`

- [ ] **Step 1: Update API spec**

Add these endpoint sections to `docs/API_SPEC.md` and `docs/API_SPEC.ko.md` near the existing internal search projection/metrics sections:

```markdown
### POST /api/internal/search-projections/article-vectors/rebuild

Rebuilds the Qdrant article vector projection from API-ready PostgreSQL articles.

Response fields:

- `status`: `completed` or `failed`
- `collectionName`: Qdrant collection name
- `indexedCount`: number of indexed article vectors
- `embeddingProvider`: embedding provider used for this rebuild
- `embeddingModelName`: embedding model name used for this rebuild
- `embeddingDimension`: vector dimension
- `durationMs`: total rebuild duration
- `failedReason`: failure reason when rebuild fails

### POST /api/internal/vector-search/articles

Runs internal semantic article search through FastAPI embeddings and Qdrant.

Request:

```json
{
  "query": "AI supply chain security risk",
  "limit": 10
}
```

Response includes:

- `query`
- `collectionName`
- `embeddingProvider`
- `embeddingModelName`
- `embeddingDimension`
- `results[].score`
- `results[].article`
- `timings.embeddingElapsedMs`
- `timings.qdrantElapsedMs`
- `timings.articleLoadElapsedMs`
- `timings.totalElapsedMs`

### GET /api/internal/search-metrics/article-vectors

Returns in-memory vector search timing metrics collected since backend startup.
```

- [ ] **Step 2: Update status docs**

Add this status entry to `docs/STATUS.md` and `docs/STATUS.ko.md`:

```markdown
- Qdrant article vector search is implemented as an internal workflow:
  - rebuild API creates article vector points from PostgreSQL articles through FastAPI embeddings
  - internal vector search embeds a query, searches Qdrant, reloads article responses from PostgreSQL, and returns timing breakdowns
  - public article search and hybrid ranking remain deferred until vector search quality is evaluated
```

- [ ] **Step 3: Update README files**

Add a short internal search section to `README.md`, `README.ko.md`, `backend/README.md`, and `backend/README.ko.md`:

```markdown
### Internal Qdrant Vector Search

Sigak keeps PostgreSQL as the source of truth and stores article embeddings in Qdrant as a rebuildable projection.

```bash
curl -X POST http://localhost:8080/api/internal/search-projections/article-vectors/rebuild
curl -X POST http://localhost:8080/api/internal/vector-search/articles \
  -H 'Content-Type: application/json' \
  -d '{"query":"AI supply chain security risk","limit":10}'
curl http://localhost:8080/api/internal/search-metrics/article-vectors
```

The public article API is not yet wired to vector search. This keeps keyword search behavior stable while vector quality and latency are measured.
```

- [ ] **Step 4: Add blog topic queue item**

Append this entry to `docs/blog/topic-queue.md`:

```markdown
## Qdrant Vector Search Projection

- Trigger: Implemented internal article vector projection and semantic search.
- Engineering angle: why PostgreSQL remains source of truth while Qdrant is a rebuildable projection store.
- Trade-off: internal API first instead of public search integration, so quality and latency can be evaluated before user-facing behavior changes.
- Technical points: multilingual embedding metadata, collection dimension policy, Qdrant point payloads, search timing breakdown.
- Evidence to include: rebuild endpoint, vector search endpoint, metrics endpoint, focused tests, `./gradlew test`.
```

- [ ] **Step 5: Run documentation diff check**

Run:

```bash
git diff --check
```

Expected: no output.

- [ ] **Step 6: Commit**

```bash
git add README.md README.ko.md backend/README.md backend/README.ko.md \
  docs/API_SPEC.md docs/API_SPEC.ko.md docs/STATUS.md docs/STATUS.ko.md docs/blog/topic-queue.md
git commit -m "docs: document qdrant vector search workflow"
```

## Task 9: Full Verification and Smoke Test

**Files:**

- No source files created in this task.

- [ ] **Step 1: Run backend unit/integration tests**

Run:

```bash
cd backend
./gradlew test
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run whitespace check**

Run:

```bash
git diff --check
```

Expected: no output.

- [ ] **Step 3: Start local infrastructure**

Run:

```bash
docker compose -f infra/docker-compose.yml up -d postgres elasticsearch qdrant ai
```

Expected: containers start successfully. If `ai` needs to download the FastEmbed model on first request, the first embedding/search call can take longer.

- [ ] **Step 4: Start backend locally**

Run in a separate terminal:

```bash
cd backend
./gradlew bootRun
```

Expected: Spring Boot starts on port 8080.

- [ ] **Step 5: Rebuild Qdrant article vector projection**

Run:

```bash
curl -s -X POST http://localhost:8080/api/internal/search-projections/article-vectors/rebuild | jq
```

Expected:

```json
{
  "status": "completed",
  "collectionName": "sigak-article-vectors-minilm-v1",
  "indexedCount": 1,
  "embeddingProvider": "local",
  "embeddingModelName": "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
  "embeddingDimension": 384,
  "durationMs": 1000,
  "failedReason": null
}
```

`indexedCount` and `durationMs` can vary based on seed data and local machine speed.

- [ ] **Step 6: Run internal vector search**

Run:

```bash
curl -s -X POST http://localhost:8080/api/internal/vector-search/articles \
  -H 'Content-Type: application/json' \
  -d '{"query":"AI supply chain security risk","limit":10}' | jq
```

Expected:

```json
{
  "query": "AI supply chain security risk",
  "collectionName": "sigak-article-vectors-minilm-v1",
  "embeddingProvider": "local",
  "embeddingDimension": 384,
  "results": [
    {
      "score": 0.7,
      "article": {
        "id": 1,
        "title": "..."
      }
    }
  ],
  "timings": {
    "embeddingElapsedMs": 1,
    "qdrantElapsedMs": 1,
    "articleLoadElapsedMs": 1,
    "totalElapsedMs": 3
  }
}
```

Scores and timings can vary locally.

- [ ] **Step 7: Check vector metrics**

Run:

```bash
curl -s http://localhost:8080/api/internal/search-metrics/article-vectors | jq
```

Expected:

```json
{
  "totalSearchCount": 1,
  "averageTotalElapsedMs": 1.0,
  "p50TotalElapsedMs": 1,
  "p95TotalElapsedMs": 1,
  "averageEmbeddingElapsedMs": 1.0,
  "averageQdrantElapsedMs": 1.0,
  "averageArticleLoadElapsedMs": 1.0,
  "lastSearch": {
    "queryLength": 29,
    "resultCount": 1,
    "embeddingElapsedMs": 1,
    "qdrantElapsedMs": 1,
    "articleLoadElapsedMs": 1,
    "totalElapsedMs": 3
  }
}
```

Counts, result count, and timings will reflect local smoke test behavior.

- [ ] **Step 8: Stop local services used for smoke test**

Run:

```bash
docker compose -f infra/docker-compose.yml down
```

Expected: local compose services stop. Volumes remain because this command does not pass `-v`.

- [ ] **Step 9: Commit smoke-test-driven fixes only when they exist**

Run:

```bash
git status --short
```

Expected when no smoke-test fix was needed: no output.

If the command prints source files changed by a smoke-test fix, stage those exact files and commit:

```bash
git add backend/src/main/kotlin/com/sigak/search/vector/QdrantArticleVectorProjectionIndexer.kt
git commit -m "fix: stabilize qdrant vector search smoke test"
```

## Final Verification Checklist

- [ ] `cd backend && ./gradlew test` passes.
- [ ] `git diff --check` passes.
- [ ] Internal rebuild endpoint returns `status=completed` against local Docker Compose.
- [ ] Internal vector search endpoint returns article results with scores and timings.
- [ ] Vector metrics endpoint records at least one search after smoke test.
- [ ] Docs mention that public article search is not yet wired to Qdrant vector search.
- [ ] Working tree contains only intentional changes.

## Expected Commit Sequence

1. `feat: add qdrant vector search settings`
2. `feat: expose ordered article reload`
3. `feat: build article vector text`
4. `feat: add qdrant article vector indexer`
5. `feat: add article vector projection rebuild`
6. `feat: add internal article vector search`
7. `feat: add article vector search metrics`
8. `docs: document qdrant vector search workflow`
