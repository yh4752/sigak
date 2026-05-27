# Search Infrastructure Readiness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a small Spring Boot readiness boundary for Elasticsearch, Qdrant, and Neo4j before implementing article indexing or hybrid search.

**Architecture:** Spring Boot remains the main API boundary. Search stores are treated as rebuildable projections, so this slice only adds typed configuration, minimal HTTP/driver clients, a service that reports readiness, and an internal API for local smoke checks.

**Tech Stack:** Kotlin, Spring Boot 3.3, Spring `RestClient`, Neo4j Java Driver, JUnit 5, MockMvc.

---

### Task 1: Configuration Binding Tests

**Files:**
- Create: `backend/src/test/kotlin/com/sigak/search/config/SearchInfrastructurePropertiesTest.kt`
- Create: `backend/src/main/kotlin/com/sigak/search/config/SearchInfrastructureProperties.kt`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/test/resources/application.yml`

- [ ] **Step 1: Write the failing test**

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
                "sigak.search.qdrant.url=http://qdrant:6333",
                "sigak.search.neo4j.uri=bolt://neo4j:7687",
                "sigak.search.neo4j.username=neo4j",
                "sigak.search.neo4j.password=test-password"
            )
            .run { context ->
                val properties = context.getBean(SearchInfrastructureProperties::class.java)

                assertEquals("http://es:9200", properties.elasticsearch.url)
                assertEquals("http://qdrant:6333", properties.qdrant.url)
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

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.sigak.search.config.SearchInfrastructurePropertiesTest`

Expected: FAIL because `SearchInfrastructureProperties` does not exist.

- [ ] **Step 3: Add minimal properties class and YAML values**

```kotlin
package com.sigak.search.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "sigak.search")
data class SearchInfrastructureProperties(
    val elasticsearch: Elasticsearch = Elasticsearch(),
    val qdrant: Qdrant = Qdrant(),
    val neo4j: Neo4j = Neo4j()
) {
    data class Elasticsearch(
        val url: String = "http://localhost:9200"
    )

    data class Qdrant(
        val url: String = "http://localhost:6333"
    )

    data class Neo4j(
        val uri: String = "bolt://localhost:7687",
        val username: String = "neo4j",
        val password: String = "sigak-neo4j-password"
    )
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests com.sigak.search.config.SearchInfrastructurePropertiesTest`

Expected: PASS.

### Task 2: Readiness Service

**Files:**
- Create: `backend/src/main/kotlin/com/sigak/search/config/SearchInfrastructureConfig.kt`
- Create: `backend/src/main/kotlin/com/sigak/search/dto/SearchInfrastructureHealthResponse.kt`
- Create: `backend/src/main/kotlin/com/sigak/search/service/SearchInfrastructureHealthService.kt`
- Create: `backend/src/test/kotlin/com/sigak/search/service/SearchInfrastructureHealthServiceTest.kt`
- Modify: `backend/build.gradle`

- [ ] **Step 1: Write the failing service test**

```kotlin
package com.sigak.search.service

import com.sigak.search.config.SearchInfrastructureProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.AfterEach
import org.neo4j.driver.Driver
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.http.MediaType
import org.springframework.http.HttpMethod
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SearchInfrastructureHealthServiceTest {

    private val elasticsearchClient = RestClient.builder().baseUrl("http://elasticsearch:9200").build()
    private val qdrantClient = RestClient.builder().baseUrl("http://qdrant:6333").build()
    private val elasticsearchServer = MockRestServiceServer.bindTo(elasticsearchClient).build()
    private val qdrantServer = MockRestServiceServer.bindTo(qdrantClient).build()
    private val neo4jDriver = mock<Driver>()

    @AfterEach
    fun tearDown() {
        elasticsearchServer.verify()
        qdrantServer.verify()
    }

    @Test
    fun reportsAllSearchStoresAsReadyWhenHealthChecksSucceed() {
        elasticsearchServer.expect(ExpectedCount.once(), requestTo("/_cluster/health"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""{"status":"green"}""", MediaType.APPLICATION_JSON))
        qdrantServer.expect(ExpectedCount.once(), requestTo("/healthz"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""{"title":"qdrant"}""", MediaType.APPLICATION_JSON))
        whenever(neo4jDriver.verifyConnectivity()).thenReturn(Unit)

        val service = SearchInfrastructureHealthService(
            elasticsearchClient,
            qdrantClient,
            neo4jDriver
        )

        val response = service.checkHealth()

        assertEquals("ready", response.overallStatus)
        assertEquals("ready", response.elasticsearch.status)
        assertEquals("ready", response.qdrant.status)
        assertEquals("ready", response.neo4j.status)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.sigak.search.service.SearchInfrastructureHealthServiceTest`

Expected: FAIL because the service, DTO, Neo4j dependency, and Mockito Kotlin dependency do not exist yet.

- [ ] **Step 3: Add minimal implementation**

Use Spring `RestClient` for Elasticsearch and Qdrant. Use Neo4j Java Driver only for `verifyConnectivity()`. Catch failures and return `unavailable` for that store so a local smoke endpoint can explain partial infrastructure problems.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests com.sigak.search.service.SearchInfrastructureHealthServiceTest`

Expected: PASS.

### Task 3: Internal Controller

**Files:**
- Create: `backend/src/main/kotlin/com/sigak/search/controller/SearchInfrastructureHealthController.kt`
- Create: `backend/src/test/kotlin/com/sigak/search/controller/SearchInfrastructureHealthControllerTest.kt`

- [ ] **Step 1: Write the failing controller test**

Test `GET /api/internal/search-infrastructure/health` with MockMvc and a mocked service. Assert that `overallStatus`, `elasticsearch.status`, `qdrant.status`, and `neo4j.status` are returned.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.sigak.search.controller.SearchInfrastructureHealthControllerTest`

Expected: FAIL because the controller does not exist.

- [ ] **Step 3: Add minimal controller**

Expose only the internal readiness endpoint. Keep it separate from public article APIs because this endpoint is for local development and operational smoke checks.

- [ ] **Step 4: Run controller test**

Run: `./gradlew test --tests com.sigak.search.controller.SearchInfrastructureHealthControllerTest`

Expected: PASS.

### Task 4: Verification and Documentation

**Files:**
- Modify: `backend/README.md`
- Modify: `backend/README.ko.md`

- [ ] **Step 1: Document environment values and endpoint**

Mention the search infrastructure properties and local smoke endpoint.

- [ ] **Step 2: Run focused backend tests**

Run: `./gradlew test --tests com.sigak.search.*`

Expected: PASS.

- [ ] **Step 3: Run full backend tests**

Run: `./gradlew test`

Expected: PASS.

- [ ] **Step 4: Check diff**

Run: `git diff --check`

Expected: no output.
