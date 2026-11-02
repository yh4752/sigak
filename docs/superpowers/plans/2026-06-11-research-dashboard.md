# Research Dashboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a portfolio-grade `/research` dashboard that reads real experiment artifacts through Spring Boot and renders retrieval, graph, catalog, and limitation evidence in React.

**Architecture:** Spring Boot owns the research API boundary through `GET /api/research/overview`; it reads committed JSON artifacts from `experiments/` and maps them to explicit DTOs. React calls only that backend API through a Zod-validated client, then renders a polished evidence dashboard with section-level unavailable states.

**Tech Stack:** Kotlin, Spring Boot MVC, Jackson `JsonNode`, JUnit 5, MockMvc, React, TypeScript, Vite, Axios, Zod, Vitest, Testing Library.

---

## Reference Spec

Read first:

- `docs/superpowers/specs/2026-06-11-research-dashboard-design.md`
- `AGENTS.md`
- `docs/CODING_CONVENTIONS.md`

## File Structure

Backend create:

- `backend/src/main/kotlin/com/sigak/research/config/ResearchArtifactProperties.kt`
  - Binds `sigak.research.artifact-root`.
- `backend/src/main/kotlin/com/sigak/research/config/ResearchConfig.kt`
  - Enables research configuration properties.
- `backend/src/main/kotlin/com/sigak/research/dto/ResearchOverviewResponse.kt`
  - Public API response DTOs for the dashboard.
- `backend/src/main/kotlin/com/sigak/research/service/ResearchArtifactReader.kt`
  - Resolves the artifact root and reads JSON files as `JsonNode`.
- `backend/src/main/kotlin/com/sigak/research/service/ResearchOverviewService.kt`
  - Maps retrieval, graph, by-query, and expanded catalog artifacts into dashboard DTOs.
- `backend/src/main/kotlin/com/sigak/research/controller/ResearchOverviewController.kt`
  - Exposes `GET /api/research/overview`.

Backend modify:

- `backend/src/main/resources/application.yml`
  - Adds `sigak.research.artifact-root`.
- `.env.example`
  - Adds `SIGAK_RESEARCH_ARTIFACT_ROOT`.

Backend test create:

- `backend/src/test/kotlin/com/sigak/research/config/ResearchArtifactPropertiesTest.kt`
- `backend/src/test/kotlin/com/sigak/research/service/ResearchArtifactReaderTest.kt`
- `backend/src/test/kotlin/com/sigak/research/service/ResearchOverviewServiceTest.kt`
- `backend/src/test/kotlin/com/sigak/research/controller/ResearchOverviewControllerTest.kt`

Frontend create:

- `frontend/src/api/research.ts`
  - Zod schema, exported types, `fetchResearchOverview`.
- `frontend/src/api/research.test.ts`
  - API client validation tests.
- `frontend/src/pages/ResearchPage.tsx`
  - Dashboard route component.
- `frontend/src/pages/ResearchPage.css`
  - Research dashboard visual treatment.
- `frontend/src/pages/ResearchPage.test.tsx`
  - Dashboard rendering and unavailable-state tests.

Frontend modify:

- `frontend/src/App.tsx`
  - Adds `/research` route.
- `frontend/src/App.test.tsx`
  - Verifies research navigation remains available in the app shell.
- `frontend/src/components/NavBar.tsx`
  - Adds `Research` link.
- `frontend/src/components/NavBar.css`
  - Styles compact nav links.
- `frontend/src/components/NavBar.test.tsx`
  - Verifies `Research` link.

Docs modify after implementation:

- `docs/STATUS.md`
- `docs/STATUS.ko.md`
- `docs/ROADMAP.md`
- `docs/ROADMAP.ko.md`
- `docs/blog/YYYY-MM-DD-dev-log.md`
- `docs/blog/topic-queue.md`

## Public Contract

Endpoint:

```txt
GET /api/research/overview
```

Response fields used by frontend:

```json
{
  "generatedAt": "2026-06-12T00:00:00Z",
  "retrieval": {
    "available": true,
    "unavailableReason": null,
    "catalogId": "api-ready-2026-06-02",
    "catalogArticleCount": 6,
    "evaluatedQueryCount": 3,
    "k": 5,
    "generatedAt": "2026-06-02T15:06:29.546Z",
    "systems": [
      {
        "name": "hybrid",
        "attemptedQueryCount": 3,
        "completedQueryCount": 3,
        "failedQueryCount": 0,
        "degradedQueryCount": 0,
        "macroTop1StrongHit": 0.6667,
        "macroRecallAtK": 0.8333,
        "macroMrrAtK": 0.8333,
        "averageLatencyMs": 15.0
      }
    ],
    "warnings": []
  },
  "graph": {
    "available": true,
    "unavailableReason": null,
    "catalogId": "api-ready-2026-06-02",
    "catalogArticleCount": 6,
    "evaluatedQueryCount": 3,
    "k": 5,
    "generatedAt": "2026-06-04T03:21:51.802Z",
    "macroSearchPositiveCoverageAtK": 0.6667,
    "macroRelatedBaselineCoverageAtK": 0.3333,
    "macroGraphContextCoverageAtK": 0.3333,
    "macroGraphReasonedCoverageAtK": 0.3333,
    "graphContextFailureRate": 0.0,
    "emptyContextRate": 0.0,
    "averageGraphLatencyMs": 33.53,
    "queryEvidence": [
      {
        "query": "graph rag failure",
        "intent": "Find articles about graph RAG failure analysis.",
        "searchPositiveCoverageAtK": 0.5,
        "relatedBaselineCoverageAtK": 0.0,
        "graphContextCoverageAtK": 0.5,
        "graphReasonedCoverageAtK": 0.5,
        "reasonCoverage": 1.0,
        "topicContextCoverage": 1.0,
        "averageGraphLatencyMs": 8.8,
        "searchMissedPositiveIds": [1]
      }
    ],
    "warnings": []
  },
  "expandedCatalog": {
    "available": true,
    "unavailableReason": null,
    "catalogId": "api-ready-2026-06-05",
    "generatedAt": "2026-06-04T15:45:30.641155Z",
    "articleCount": 41,
    "labelStatus": "draft_only",
    "benchmarkStatus": "blocked_until_reviewed_labels",
    "warnings": [
      "Reviewed labels are required before quality claims use the expanded catalog."
    ]
  },
  "limitations": [
    "Current retrieval and graph metrics are smoke evidence, not broad quality claims."
  ]
}
```

Unavailable sections keep the same shape, set `available=false`, set `unavailableReason`, clear item lists, and leave metric fields `null`.

---

## Task 1: Backend DTOs, Properties, And Artifact Reader

**Files:**

- Create: `backend/src/main/kotlin/com/sigak/research/config/ResearchArtifactProperties.kt`
- Create: `backend/src/main/kotlin/com/sigak/research/config/ResearchConfig.kt`
- Create: `backend/src/main/kotlin/com/sigak/research/dto/ResearchOverviewResponse.kt`
- Create: `backend/src/main/kotlin/com/sigak/research/service/ResearchArtifactReader.kt`
- Create: `backend/src/test/kotlin/com/sigak/research/config/ResearchArtifactPropertiesTest.kt`
- Create: `backend/src/test/kotlin/com/sigak/research/service/ResearchArtifactReaderTest.kt`

- [ ] **Step 1: Write the configuration binding test**

Create `backend/src/test/kotlin/com/sigak/research/config/ResearchArtifactPropertiesTest.kt`:

```kotlin
package com.sigak.research.config

import kotlin.test.Test
import kotlin.test.assertEquals
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration

class ResearchArtifactPropertiesTest {

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(ResearchArtifactPropertiesConfig::class.java)

    @Test
    fun bindsResearchArtifactRoot() {
        contextRunner
            .withPropertyValues("sigak.research.artifact-root=/tmp/sigak-experiments")
            .run { context ->
                val properties = context.getBean(ResearchArtifactProperties::class.java)

                assertEquals("/tmp/sigak-experiments", properties.artifactRoot)
            }
    }

    @Configuration
    @EnableConfigurationProperties(ResearchArtifactProperties::class)
    private class ResearchArtifactPropertiesConfig
}
```

- [ ] **Step 2: Run the focused configuration test and confirm it fails**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.research.config.ResearchArtifactPropertiesTest
```

Expected: FAIL because `ResearchArtifactProperties` does not exist.

- [ ] **Step 3: Add research configuration classes**

Create `backend/src/main/kotlin/com/sigak/research/config/ResearchArtifactProperties.kt`:

```kotlin
package com.sigak.research.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "sigak.research")
data class ResearchArtifactProperties(
    val artifactRoot: String = "experiments"
)
```

Create `backend/src/main/kotlin/com/sigak/research/config/ResearchConfig.kt`:

```kotlin
package com.sigak.research.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(ResearchArtifactProperties::class)
class ResearchConfig
```

- [ ] **Step 4: Add dashboard response DTOs**

Create `backend/src/main/kotlin/com/sigak/research/dto/ResearchOverviewResponse.kt`:

```kotlin
package com.sigak.research.dto

import java.time.Instant

data class ResearchOverviewResponse(
    val generatedAt: Instant,
    val retrieval: RetrievalResearchSectionResponse,
    val graph: GraphResearchSectionResponse,
    val expandedCatalog: ExpandedCatalogResearchSectionResponse,
    val limitations: List<String>
)

data class RetrievalResearchSectionResponse(
    val available: Boolean,
    val unavailableReason: String?,
    val catalogId: String?,
    val catalogArticleCount: Int?,
    val evaluatedQueryCount: Int?,
    val k: Int?,
    val generatedAt: Instant?,
    val systems: List<RetrievalSystemMetricResponse>,
    val warnings: List<String>
)

data class RetrievalSystemMetricResponse(
    val name: String,
    val attemptedQueryCount: Int,
    val completedQueryCount: Int,
    val failedQueryCount: Int,
    val degradedQueryCount: Int,
    val macroTop1StrongHit: Double,
    val macroRecallAtK: Double,
    val macroMrrAtK: Double,
    val averageLatencyMs: Double
)

data class GraphResearchSectionResponse(
    val available: Boolean,
    val unavailableReason: String?,
    val catalogId: String?,
    val catalogArticleCount: Int?,
    val evaluatedQueryCount: Int?,
    val k: Int?,
    val generatedAt: Instant?,
    val macroSearchPositiveCoverageAtK: Double?,
    val macroRelatedBaselineCoverageAtK: Double?,
    val macroGraphContextCoverageAtK: Double?,
    val macroGraphReasonedCoverageAtK: Double?,
    val graphContextFailureRate: Double?,
    val emptyContextRate: Double?,
    val averageGraphLatencyMs: Double?,
    val queryEvidence: List<GraphQueryEvidenceResponse>,
    val warnings: List<String>
)

data class GraphQueryEvidenceResponse(
    val query: String,
    val intent: String?,
    val searchPositiveCoverageAtK: Double,
    val relatedBaselineCoverageAtK: Double,
    val graphContextCoverageAtK: Double,
    val graphReasonedCoverageAtK: Double,
    val reasonCoverage: Double,
    val topicContextCoverage: Double,
    val averageGraphLatencyMs: Double,
    val searchMissedPositiveIds: List<Long>
)

data class ExpandedCatalogResearchSectionResponse(
    val available: Boolean,
    val unavailableReason: String?,
    val catalogId: String?,
    val generatedAt: Instant?,
    val articleCount: Int?,
    val labelStatus: String,
    val benchmarkStatus: String,
    val warnings: List<String>
)
```

- [ ] **Step 5: Write artifact reader tests**

Create `backend/src/test/kotlin/com/sigak/research/service/ResearchArtifactReaderTest.kt`:

```kotlin
package com.sigak.research.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.sigak.research.config.ResearchArtifactProperties
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ResearchArtifactReaderTest {

    @TempDir
    lateinit var tempDir: Path

    private val objectMapper = ObjectMapper()

    @Test
    fun readsJsonFromConfiguredArtifactRoot() {
        val artifactFile = tempDir.resolve("results/retrieval/latest/metrics.comparison.json")
        artifactFile.parent.createDirectories()
        artifactFile.writeText("""{"catalogId":"api-ready-2026-06-02"}""")
        val reader = ResearchArtifactReader(
            properties = ResearchArtifactProperties(artifactRoot = tempDir.toString()),
            objectMapper = objectMapper
        )

        val result = reader.readJson("results/retrieval/latest/metrics.comparison.json")

        assertTrue(result is ResearchArtifactReadResult.Available)
        assertEquals("api-ready-2026-06-02", result.json.get("catalogId").asText())
    }

    @Test
    fun reportsMissingArtifactWithoutExposingAbsolutePath() {
        val reader = ResearchArtifactReader(
            properties = ResearchArtifactProperties(artifactRoot = tempDir.toString()),
            objectMapper = objectMapper
        )

        val result = reader.readJson("results/retrieval/latest/missing.json")

        assertTrue(result is ResearchArtifactReadResult.Unavailable)
        assertEquals(
            "Artifact not found: results/retrieval/latest/missing.json",
            result.reason
        )
    }
}
```

- [ ] **Step 6: Run the focused reader tests and confirm they fail**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.research.service.ResearchArtifactReaderTest
```

Expected: FAIL because `ResearchArtifactReader` does not exist.

- [ ] **Step 7: Implement the artifact reader**

Create `backend/src/main/kotlin/com/sigak/research/service/ResearchArtifactReader.kt`:

```kotlin
package com.sigak.research.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.sigak.research.config.ResearchArtifactProperties
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import org.springframework.stereotype.Component

sealed class ResearchArtifactReadResult {
    data class Available(val json: JsonNode) : ResearchArtifactReadResult()
    data class Unavailable(val reason: String) : ResearchArtifactReadResult()
}

@Component
class ResearchArtifactReader(
    private val properties: ResearchArtifactProperties,
    private val objectMapper: ObjectMapper
) {

    fun readJson(relativePath: String): ResearchArtifactReadResult {
        val artifactPath = resolveArtifactPath(relativePath)
        if (!artifactPath.exists()) {
            return ResearchArtifactReadResult.Unavailable("Artifact not found: $relativePath")
        }

        return try {
            ResearchArtifactReadResult.Available(objectMapper.readTree(Files.readString(artifactPath)))
        } catch (e: RuntimeException) {
            ResearchArtifactReadResult.Unavailable("Artifact could not be parsed: $relativePath")
        }
    }

    private fun resolveArtifactPath(relativePath: String): Path =
        resolveArtifactRoot().resolve(relativePath).normalize()

    private fun resolveArtifactRoot(): Path {
        val configuredRoot = Path.of(properties.artifactRoot)
        if (configuredRoot.isAbsolute) {
            return configuredRoot.normalize()
        }

        val cwdRelative = configuredRoot.normalize()
        if (cwdRelative.exists()) {
            return cwdRelative
        }

        val parentRelative = Path.of("..").resolve(configuredRoot).normalize()
        if (parentRelative.exists()) {
            return parentRelative
        }

        // 로컬 실행 위치가 달라도 응답은 section-level unavailable로 내려가야 한다.
        return cwdRelative
    }
}
```

- [ ] **Step 8: Run focused backend tests for Task 1**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.research.config.ResearchArtifactPropertiesTest --tests com.sigak.research.service.ResearchArtifactReaderTest
```

Expected: PASS.

- [ ] **Step 9: Commit Task 1**

Run:

```bash
git add backend/src/main/kotlin/com/sigak/research backend/src/test/kotlin/com/sigak/research
git commit -m "feat: add research artifact reader foundation"
```

---

## Task 2: Backend Overview Service And Controller

**Files:**

- Create: `backend/src/main/kotlin/com/sigak/research/service/ResearchOverviewService.kt`
- Create: `backend/src/main/kotlin/com/sigak/research/controller/ResearchOverviewController.kt`
- Create: `backend/src/test/kotlin/com/sigak/research/service/ResearchOverviewServiceTest.kt`
- Create: `backend/src/test/kotlin/com/sigak/research/controller/ResearchOverviewControllerTest.kt`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `.env.example`

- [ ] **Step 1: Write service tests for available artifacts**

Create `backend/src/test/kotlin/com/sigak/research/service/ResearchOverviewServiceTest.kt`:

```kotlin
package com.sigak.research.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.sigak.research.config.ResearchArtifactProperties
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ResearchOverviewServiceTest {

    @TempDir
    lateinit var tempDir: Path

    private val objectMapper = ObjectMapper()

    @Test
    fun mapsResearchArtifactsToOverviewResponse() {
        writeArtifact(
            "results/retrieval/latest/metrics.comparison.json",
            """
            {
              "catalogId": "api-ready-2026-06-02",
              "catalogArticleCount": 6,
              "evaluatedQueryCount": 3,
              "k": 5,
              "generatedAt": "2026-06-02T15:06:29.546Z",
              "systems": [
                {
                  "system": "keyword",
                  "attemptedQueryCount": 3,
                  "completedQueryCount": 3,
                  "failedQueryCount": 0,
                  "degradedQueryCount": 0,
                  "macroTop1StrongHit": 1.0,
                  "macroRecallAtK": 0.6666666667,
                  "macroMrrAtK": 1.0,
                  "averageLatencyMs": 21.6666666667
                },
                {
                  "system": "hybrid",
                  "attemptedQueryCount": 3,
                  "completedQueryCount": 3,
                  "failedQueryCount": 0,
                  "degradedQueryCount": 0,
                  "macroTop1StrongHit": 0.6666666667,
                  "macroRecallAtK": 0.8333333333,
                  "macroMrrAtK": 0.8333333333,
                  "averageLatencyMs": 15.0
                }
              ],
              "warnings": ["label set is smaller than 10 reviewed queries"]
            }
            """.trimIndent()
        )
        writeArtifact(
            "results/graph/latest/graph-context.metrics.summary.json",
            """
            {
              "catalogId": "api-ready-2026-06-02",
              "catalogArticleCount": 6,
              "evaluatedQueryCount": 3,
              "k": 5,
              "generatedAt": "2026-06-04T03:21:51.802Z",
              "macroSearchPositiveCoverageAtK": 0.6666666667,
              "macroRelatedBaselineCoverageAtK": 0.3333333333,
              "macroGraphContextCoverageAtK": 0.3333333333,
              "macroGraphReasonedCoverageAtK": 0.3333333333,
              "graphContextFailureRate": 0.0,
              "emptyContextRate": 0.0,
              "averageGraphLatencyMs": 33.5333333333,
              "warnings": ["graph latency is measured as public API round-trip"]
            }
            """.trimIndent()
        )
        writeArtifact(
            "results/graph/latest/graph-context.metrics.by-query.json",
            """
            [
              {
                "query": "graph rag failure",
                "intent": "Find graph RAG failure analysis.",
                "searchPositiveCoverageAtK": 0.5,
                "relatedBaselineCoverageAtK": 0.0,
                "graphContextCoverageAtK": 0.5,
                "graphReasonedCoverageAtK": 0.5,
                "reasonCoverage": 1.0,
                "topicContextCoverage": 1.0,
                "averageGraphLatencyMs": 8.8,
                "searchMissedPositiveIds": [1]
              }
            ]
            """.trimIndent()
        )
        writeArtifact(
            "datasets/raw/articles.catalog.api-ready-2026-06-05.json",
            """
            {
              "catalogId": "api-ready-2026-06-05",
              "generatedAt": "2026-06-04T15:45:30.641155Z",
              "articles": [
                {"id": 1, "title": "A"},
                {"id": 2, "title": "B"}
              ]
            }
            """.trimIndent()
        )
        val service = newService()

        val response = service.getOverview()

        assertTrue(response.retrieval.available)
        assertEquals("api-ready-2026-06-02", response.retrieval.catalogId)
        assertEquals(2, response.retrieval.systems.size)
        assertEquals("hybrid", response.retrieval.systems[1].name)
        assertTrue(response.graph.available)
        assertEquals(0.3333333333, response.graph.macroGraphContextCoverageAtK)
        assertEquals("graph rag failure", response.graph.queryEvidence.single().query)
        assertTrue(response.expandedCatalog.available)
        assertEquals(2, response.expandedCatalog.articleCount)
        assertEquals("draft_only", response.expandedCatalog.labelStatus)
        assertEquals("blocked_until_reviewed_labels", response.expandedCatalog.benchmarkStatus)
        assertTrue(response.limitations.isNotEmpty())
        assertNotNull(response.generatedAt)
    }

    @Test
    fun degradesMissingArtifactsBySection() {
        val service = newService()

        val response = service.getOverview()

        assertEquals(false, response.retrieval.available)
        assertEquals("Artifact not found: results/retrieval/latest/metrics.comparison.json", response.retrieval.unavailableReason)
        assertEquals(emptyList(), response.retrieval.systems)
        assertEquals(false, response.graph.available)
        assertEquals(emptyList(), response.graph.queryEvidence)
        assertEquals(false, response.expandedCatalog.available)
    }

    private fun writeArtifact(relativePath: String, content: String) {
        val artifactFile = tempDir.resolve(relativePath)
        artifactFile.parent.createDirectories()
        artifactFile.writeText(content)
    }

    private fun newService(): ResearchOverviewService =
        ResearchOverviewService(
            artifactReader = ResearchArtifactReader(
                properties = ResearchArtifactProperties(artifactRoot = tempDir.toString()),
                objectMapper = objectMapper
            )
        )
}
```

- [ ] **Step 2: Run the focused service test and confirm it fails**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.research.service.ResearchOverviewServiceTest
```

Expected: FAIL because `ResearchOverviewService` does not exist.

- [ ] **Step 3: Implement the overview service**

Create `backend/src/main/kotlin/com/sigak/research/service/ResearchOverviewService.kt`.
The implementation must include these public methods and constants:

```kotlin
package com.sigak.research.service

import com.fasterxml.jackson.databind.JsonNode
import com.sigak.research.dto.ExpandedCatalogResearchSectionResponse
import com.sigak.research.dto.GraphQueryEvidenceResponse
import com.sigak.research.dto.GraphResearchSectionResponse
import com.sigak.research.dto.ResearchOverviewResponse
import com.sigak.research.dto.RetrievalResearchSectionResponse
import com.sigak.research.dto.RetrievalSystemMetricResponse
import java.time.Instant
import org.springframework.stereotype.Service

private const val RETRIEVAL_COMPARISON_PATH = "results/retrieval/latest/metrics.comparison.json"
private const val GRAPH_SUMMARY_PATH = "results/graph/latest/graph-context.metrics.summary.json"
private const val GRAPH_BY_QUERY_PATH = "results/graph/latest/graph-context.metrics.by-query.json"
private const val EXPANDED_CATALOG_PATH = "datasets/raw/articles.catalog.api-ready-2026-06-05.json"

@Service
class ResearchOverviewService(
    private val artifactReader: ResearchArtifactReader
) {

    fun getOverview(): ResearchOverviewResponse =
        ResearchOverviewResponse(
            generatedAt = Instant.now(),
            retrieval = readRetrievalSection(),
            graph = readGraphSection(),
            expandedCatalog = readExpandedCatalogSection(),
            limitations = listOf(
                "Current retrieval and graph metrics are smoke evidence, not broad quality claims.",
                "The expanded catalog needs reviewed labels before it can support benchmark claims."
            )
        )
}
```

Add private mapping helpers with these exact field mappings:

```kotlin
private fun readRetrievalSection(): RetrievalResearchSectionResponse =
    when (val result = artifactReader.readJson(RETRIEVAL_COMPARISON_PATH)) {
        is ResearchArtifactReadResult.Available -> {
            val json = result.json
            RetrievalResearchSectionResponse(
                available = true,
                unavailableReason = null,
                catalogId = json.textOrNull("catalogId"),
                catalogArticleCount = json.intOrNull("catalogArticleCount"),
                evaluatedQueryCount = json.intOrNull("evaluatedQueryCount"),
                k = json.intOrNull("k"),
                generatedAt = json.instantOrNull("generatedAt"),
                systems = json.arrayOrEmpty("systems").map { system ->
                    RetrievalSystemMetricResponse(
                        name = system.requiredText("system"),
                        attemptedQueryCount = system.requiredInt("attemptedQueryCount"),
                        completedQueryCount = system.requiredInt("completedQueryCount"),
                        failedQueryCount = system.requiredInt("failedQueryCount"),
                        degradedQueryCount = system.requiredInt("degradedQueryCount"),
                        macroTop1StrongHit = system.requiredDouble("macroTop1StrongHit"),
                        macroRecallAtK = system.requiredDouble("macroRecallAtK"),
                        macroMrrAtK = system.requiredDouble("macroMrrAtK"),
                        averageLatencyMs = system.requiredDouble("averageLatencyMs")
                    )
                },
                warnings = json.stringArray("warnings")
            )
        }
        is ResearchArtifactReadResult.Unavailable -> unavailableRetrieval(result.reason)
    }
```

For graph summary, read both summary and by-query artifacts. If the summary file is missing, return an unavailable graph section. If only by-query is missing, return the summary metrics with an empty `queryEvidence` list and add the by-query reason to `warnings`.

Use this graph mapping shape:

```kotlin
private fun readGraphSection(): GraphResearchSectionResponse =
    when (val summaryResult = artifactReader.readJson(GRAPH_SUMMARY_PATH)) {
        is ResearchArtifactReadResult.Available -> {
            val summary = summaryResult.json
            val byQueryResult = artifactReader.readJson(GRAPH_BY_QUERY_PATH)
            val queryEvidence = when (byQueryResult) {
                is ResearchArtifactReadResult.Available ->
                    byQueryResult.json.map { query ->
                        GraphQueryEvidenceResponse(
                            query = query.requiredText("query"),
                            intent = query.textOrNull("intent"),
                            searchPositiveCoverageAtK = query.requiredDouble("searchPositiveCoverageAtK"),
                            relatedBaselineCoverageAtK = query.requiredDouble("relatedBaselineCoverageAtK"),
                            graphContextCoverageAtK = query.requiredDouble("graphContextCoverageAtK"),
                            graphReasonedCoverageAtK = query.requiredDouble("graphReasonedCoverageAtK"),
                            reasonCoverage = query.requiredDouble("reasonCoverage"),
                            topicContextCoverage = query.requiredDouble("topicContextCoverage"),
                            averageGraphLatencyMs = query.requiredDouble("averageGraphLatencyMs"),
                            searchMissedPositiveIds = query.longArray("searchMissedPositiveIds")
                        )
                    }
                is ResearchArtifactReadResult.Unavailable -> emptyList()
            }
            val byQueryWarnings = when (byQueryResult) {
                is ResearchArtifactReadResult.Available -> emptyList()
                is ResearchArtifactReadResult.Unavailable -> listOf(byQueryResult.reason)
            }

            GraphResearchSectionResponse(
                available = true,
                unavailableReason = null,
                catalogId = summary.textOrNull("catalogId"),
                catalogArticleCount = summary.intOrNull("catalogArticleCount"),
                evaluatedQueryCount = summary.intOrNull("evaluatedQueryCount"),
                k = summary.intOrNull("k"),
                generatedAt = summary.instantOrNull("generatedAt"),
                macroSearchPositiveCoverageAtK = summary.doubleOrNull("macroSearchPositiveCoverageAtK"),
                macroRelatedBaselineCoverageAtK = summary.doubleOrNull("macroRelatedBaselineCoverageAtK"),
                macroGraphContextCoverageAtK = summary.doubleOrNull("macroGraphContextCoverageAtK"),
                macroGraphReasonedCoverageAtK = summary.doubleOrNull("macroGraphReasonedCoverageAtK"),
                graphContextFailureRate = summary.doubleOrNull("graphContextFailureRate"),
                emptyContextRate = summary.doubleOrNull("emptyContextRate"),
                averageGraphLatencyMs = summary.doubleOrNull("averageGraphLatencyMs"),
                queryEvidence = queryEvidence,
                warnings = summary.stringArray("warnings") + byQueryWarnings
            )
        }
        is ResearchArtifactReadResult.Unavailable -> unavailableGraph(summaryResult.reason)
    }
```

Use this expanded catalog mapping shape:

```kotlin
private fun readExpandedCatalogSection(): ExpandedCatalogResearchSectionResponse =
    when (val result = artifactReader.readJson(EXPANDED_CATALOG_PATH)) {
        is ResearchArtifactReadResult.Available -> {
            val json = result.json
            ExpandedCatalogResearchSectionResponse(
                available = true,
                unavailableReason = null,
                catalogId = json.textOrNull("catalogId"),
                generatedAt = json.instantOrNull("generatedAt"),
                articleCount = json.arrayOrEmpty("articles").size,
                labelStatus = "draft_only",
                benchmarkStatus = "blocked_until_reviewed_labels",
                warnings = listOf("Reviewed labels are required before quality claims use the expanded catalog.")
            )
        }
        is ResearchArtifactReadResult.Unavailable -> unavailableExpandedCatalog(result.reason)
    }
```

Use these helper signatures:

```kotlin
private fun readGraphSection(): GraphResearchSectionResponse
private fun readExpandedCatalogSection(): ExpandedCatalogResearchSectionResponse
private fun unavailableRetrieval(reason: String): RetrievalResearchSectionResponse
private fun unavailableGraph(reason: String): GraphResearchSectionResponse
private fun unavailableExpandedCatalog(reason: String): ExpandedCatalogResearchSectionResponse
private fun JsonNode.textOrNull(field: String): String?
private fun JsonNode.intOrNull(field: String): Int?
private fun JsonNode.doubleOrNull(field: String): Double?
private fun JsonNode.instantOrNull(field: String): Instant?
private fun JsonNode.requiredText(field: String): String
private fun JsonNode.requiredInt(field: String): Int
private fun JsonNode.requiredDouble(field: String): Double
private fun JsonNode.arrayOrEmpty(field: String): List<JsonNode>
private fun JsonNode.stringArray(field: String): List<String>
private fun JsonNode.longArray(field: String): List<Long>
```

Add one Korean comment near the graph by-query fallback:

```kotlin
// Graph summary만 있어도 대시보드는 보여줘야 하므로 by-query artifact 실패는 section warning으로 낮춘다.
```

- [ ] **Step 4: Write the controller test**

Create `backend/src/test/kotlin/com/sigak/research/controller/ResearchOverviewControllerTest.kt`:

```kotlin
package com.sigak.research.controller

import com.sigak.research.dto.ExpandedCatalogResearchSectionResponse
import com.sigak.research.dto.GraphResearchSectionResponse
import com.sigak.research.dto.ResearchOverviewResponse
import com.sigak.research.dto.RetrievalResearchSectionResponse
import com.sigak.research.dto.RetrievalSystemMetricResponse
import com.sigak.research.service.ResearchOverviewService
import java.time.Instant
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(ResearchOverviewController::class)
class ResearchOverviewControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var overviewService: ResearchOverviewService

    @Test
    fun returnsResearchOverview() {
        `when`(overviewService.getOverview())
            .thenReturn(
                ResearchOverviewResponse(
                    generatedAt = Instant.parse("2026-06-12T00:00:00Z"),
                    retrieval = RetrievalResearchSectionResponse(
                        available = true,
                        unavailableReason = null,
                        catalogId = "api-ready-2026-06-02",
                        catalogArticleCount = 6,
                        evaluatedQueryCount = 3,
                        k = 5,
                        generatedAt = Instant.parse("2026-06-02T15:06:29.546Z"),
                        systems = listOf(
                            RetrievalSystemMetricResponse(
                                name = "hybrid",
                                attemptedQueryCount = 3,
                                completedQueryCount = 3,
                                failedQueryCount = 0,
                                degradedQueryCount = 0,
                                macroTop1StrongHit = 0.6667,
                                macroRecallAtK = 0.8333,
                                macroMrrAtK = 0.8333,
                                averageLatencyMs = 15.0
                            )
                        ),
                        warnings = listOf("smoke benchmark")
                    ),
                    graph = GraphResearchSectionResponse(
                        available = false,
                        unavailableReason = "Artifact not found: results/graph/latest/graph-context.metrics.summary.json",
                        catalogId = null,
                        catalogArticleCount = null,
                        evaluatedQueryCount = null,
                        k = null,
                        generatedAt = null,
                        macroSearchPositiveCoverageAtK = null,
                        macroRelatedBaselineCoverageAtK = null,
                        macroGraphContextCoverageAtK = null,
                        macroGraphReasonedCoverageAtK = null,
                        graphContextFailureRate = null,
                        emptyContextRate = null,
                        averageGraphLatencyMs = null,
                        queryEvidence = emptyList(),
                        warnings = emptyList()
                    ),
                    expandedCatalog = ExpandedCatalogResearchSectionResponse(
                        available = true,
                        unavailableReason = null,
                        catalogId = "api-ready-2026-06-05",
                        generatedAt = Instant.parse("2026-06-04T15:45:30.641155Z"),
                        articleCount = 41,
                        labelStatus = "draft_only",
                        benchmarkStatus = "blocked_until_reviewed_labels",
                        warnings = listOf("Reviewed labels are required before quality claims use the expanded catalog.")
                    ),
                    limitations = listOf("Current retrieval and graph metrics are smoke evidence, not broad quality claims.")
                )
            )

        mockMvc.perform(get("/api/research/overview"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.retrieval.available").value(true))
            .andExpect(jsonPath("$.retrieval.systems[0].name").value("hybrid"))
            .andExpect(jsonPath("$.graph.available").value(false))
            .andExpect(jsonPath("$.expandedCatalog.articleCount").value(41))
            .andExpect(jsonPath("$.limitations[0]").value("Current retrieval and graph metrics are smoke evidence, not broad quality claims."))
    }
}
```

- [ ] **Step 5: Run the focused controller test and confirm it fails**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.research.controller.ResearchOverviewControllerTest
```

Expected: FAIL because `ResearchOverviewController` does not exist.

- [ ] **Step 6: Implement the controller**

Create `backend/src/main/kotlin/com/sigak/research/controller/ResearchOverviewController.kt`:

```kotlin
package com.sigak.research.controller

import com.sigak.research.dto.ResearchOverviewResponse
import com.sigak.research.service.ResearchOverviewService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/research")
@Tag(name = "Research Dashboard", description = "Portfolio research metrics generated from local experiment artifacts.")
class ResearchOverviewController(
    private val overviewService: ResearchOverviewService
) {

    @GetMapping("/overview")
    @Operation(
        summary = "Get research dashboard overview",
        description = "Returns retrieval, graph-aware context, expanded catalog, and limitation evidence for the portfolio research dashboard."
    )
    fun getOverview(): ResearchOverviewResponse =
        overviewService.getOverview()
}
```

- [ ] **Step 7: Add application configuration**

Modify `backend/src/main/resources/application.yml` under `sigak:`:

```yaml
  research:
    artifact-root: ${SIGAK_RESEARCH_ARTIFACT_ROOT:experiments}
```

Modify `.env.example`:

```dotenv
SIGAK_RESEARCH_ARTIFACT_ROOT=experiments
```

- [ ] **Step 8: Run focused backend tests for Task 2**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.research.service.ResearchOverviewServiceTest --tests com.sigak.research.controller.ResearchOverviewControllerTest
```

Expected: PASS.

- [ ] **Step 9: Run a local API smoke check**

Run:

```bash
cd backend
SIGAK_RESEARCH_ARTIFACT_ROOT=../experiments ./gradlew bootRun
```

In a second terminal:

```bash
curl -s http://localhost:8080/api/research/overview | jq '{retrievalAvailable: .retrieval.available, systems: [.retrieval.systems[].name], graphAvailable: .graph.available, expandedArticleCount: .expandedCatalog.articleCount}'
```

Expected JSON:

```json
{
  "retrievalAvailable": true,
  "systems": [
    "keyword",
    "vector",
    "hybrid",
    "public"
  ],
  "graphAvailable": true,
  "expandedArticleCount": 41
}
```

Stop `bootRun` after the smoke check.

- [ ] **Step 10: Commit Task 2**

Run:

```bash
git add backend/src/main/kotlin/com/sigak/research backend/src/test/kotlin/com/sigak/research backend/src/main/resources/application.yml .env.example
git commit -m "feat: expose research overview API"
```

---

## Task 3: Frontend Research API Client

**Files:**

- Create: `frontend/src/api/research.ts`
- Create: `frontend/src/api/research.test.ts`

- [ ] **Step 1: Write API client tests**

Create `frontend/src/api/research.test.ts`:

```ts
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchResearchOverview } from './research'
import { httpClient } from './httpClient'

vi.mock('./httpClient', () => ({
  httpClient: {
    get: vi.fn(),
  },
}))

const mockOverview = {
  generatedAt: '2026-06-12T00:00:00Z',
  retrieval: {
    available: true,
    unavailableReason: null,
    catalogId: 'api-ready-2026-06-02',
    catalogArticleCount: 6,
    evaluatedQueryCount: 3,
    k: 5,
    generatedAt: '2026-06-02T15:06:29.546Z',
    systems: [
      {
        name: 'hybrid',
        attemptedQueryCount: 3,
        completedQueryCount: 3,
        failedQueryCount: 0,
        degradedQueryCount: 0,
        macroTop1StrongHit: 0.6667,
        macroRecallAtK: 0.8333,
        macroMrrAtK: 0.8333,
        averageLatencyMs: 15,
      },
    ],
    warnings: ['smoke benchmark'],
  },
  graph: {
    available: true,
    unavailableReason: null,
    catalogId: 'api-ready-2026-06-02',
    catalogArticleCount: 6,
    evaluatedQueryCount: 3,
    k: 5,
    generatedAt: '2026-06-04T03:21:51.802Z',
    macroSearchPositiveCoverageAtK: 0.6667,
    macroRelatedBaselineCoverageAtK: 0.3333,
    macroGraphContextCoverageAtK: 0.3333,
    macroGraphReasonedCoverageAtK: 0.3333,
    graphContextFailureRate: 0,
    emptyContextRate: 0,
    averageGraphLatencyMs: 33.53,
    queryEvidence: [
      {
        query: 'graph rag failure',
        intent: 'Find graph RAG failure analysis.',
        searchPositiveCoverageAtK: 0.5,
        relatedBaselineCoverageAtK: 0,
        graphContextCoverageAtK: 0.5,
        graphReasonedCoverageAtK: 0.5,
        reasonCoverage: 1,
        topicContextCoverage: 1,
        averageGraphLatencyMs: 8.8,
        searchMissedPositiveIds: [1],
      },
    ],
    warnings: ['graph latency is measured as public API round-trip'],
  },
  expandedCatalog: {
    available: true,
    unavailableReason: null,
    catalogId: 'api-ready-2026-06-05',
    generatedAt: '2026-06-04T15:45:30.641155Z',
    articleCount: 41,
    labelStatus: 'draft_only',
    benchmarkStatus: 'blocked_until_reviewed_labels',
    warnings: ['Reviewed labels are required before quality claims use the expanded catalog.'],
  },
  limitations: ['Current retrieval and graph metrics are smoke evidence, not broad quality claims.'],
}

describe('fetchResearchOverview', () => {
  beforeEach(() => {
    vi.mocked(httpClient.get).mockReset()
  })

  it('requests the research overview and validates the response', async () => {
    vi.mocked(httpClient.get).mockResolvedValue({ data: mockOverview })

    const overview = await fetchResearchOverview()

    expect(httpClient.get).toHaveBeenCalledWith('/api/research/overview')
    expect(overview).toEqual(mockOverview)
  })

  it('rejects invalid research overview responses before they reach the UI', async () => {
    vi.mocked(httpClient.get).mockResolvedValue({
      data: {
        ...mockOverview,
        retrieval: { ...mockOverview.retrieval, systems: [{ name: 'hybrid' }] },
      },
    })

    await expect(fetchResearchOverview()).rejects.toThrow()
  })
})
```

- [ ] **Step 2: Run the focused API client test and confirm it fails**

Run:

```bash
cd frontend
npm test -- research.test.ts
```

Expected: FAIL because `frontend/src/api/research.ts` does not exist.

- [ ] **Step 3: Implement the API client**

Create `frontend/src/api/research.ts`:

```ts
import { z } from 'zod'
import { httpClient } from './httpClient'

const retrievalSystemMetricSchema = z.object({
  name: z.string(),
  attemptedQueryCount: z.number(),
  completedQueryCount: z.number(),
  failedQueryCount: z.number(),
  degradedQueryCount: z.number(),
  macroTop1StrongHit: z.number(),
  macroRecallAtK: z.number(),
  macroMrrAtK: z.number(),
  averageLatencyMs: z.number(),
})

const retrievalSectionSchema = z.object({
  available: z.boolean(),
  unavailableReason: z.string().nullable(),
  catalogId: z.string().nullable(),
  catalogArticleCount: z.number().nullable(),
  evaluatedQueryCount: z.number().nullable(),
  k: z.number().nullable(),
  generatedAt: z.string().nullable(),
  systems: z.array(retrievalSystemMetricSchema),
  warnings: z.array(z.string()),
})

const graphQueryEvidenceSchema = z.object({
  query: z.string(),
  intent: z.string().nullable(),
  searchPositiveCoverageAtK: z.number(),
  relatedBaselineCoverageAtK: z.number(),
  graphContextCoverageAtK: z.number(),
  graphReasonedCoverageAtK: z.number(),
  reasonCoverage: z.number(),
  topicContextCoverage: z.number(),
  averageGraphLatencyMs: z.number(),
  searchMissedPositiveIds: z.array(z.number()),
})

const graphSectionSchema = z.object({
  available: z.boolean(),
  unavailableReason: z.string().nullable(),
  catalogId: z.string().nullable(),
  catalogArticleCount: z.number().nullable(),
  evaluatedQueryCount: z.number().nullable(),
  k: z.number().nullable(),
  generatedAt: z.string().nullable(),
  macroSearchPositiveCoverageAtK: z.number().nullable(),
  macroRelatedBaselineCoverageAtK: z.number().nullable(),
  macroGraphContextCoverageAtK: z.number().nullable(),
  macroGraphReasonedCoverageAtK: z.number().nullable(),
  graphContextFailureRate: z.number().nullable(),
  emptyContextRate: z.number().nullable(),
  averageGraphLatencyMs: z.number().nullable(),
  queryEvidence: z.array(graphQueryEvidenceSchema),
  warnings: z.array(z.string()),
})

const expandedCatalogSectionSchema = z.object({
  available: z.boolean(),
  unavailableReason: z.string().nullable(),
  catalogId: z.string().nullable(),
  generatedAt: z.string().nullable(),
  articleCount: z.number().nullable(),
  labelStatus: z.string(),
  benchmarkStatus: z.string(),
  warnings: z.array(z.string()),
})

const researchOverviewSchema = z.object({
  generatedAt: z.string(),
  retrieval: retrievalSectionSchema,
  graph: graphSectionSchema,
  expandedCatalog: expandedCatalogSectionSchema,
  limitations: z.array(z.string()),
})

export type ResearchOverview = z.infer<typeof researchOverviewSchema>
export type RetrievalSystemMetric = z.infer<typeof retrievalSystemMetricSchema>
export type GraphQueryEvidence = z.infer<typeof graphQueryEvidenceSchema>

export async function fetchResearchOverview(): Promise<ResearchOverview> {
  const response = await httpClient.get('/api/research/overview')
  return researchOverviewSchema.parse(response.data)
}
```

- [ ] **Step 4: Run focused frontend API tests**

Run:

```bash
cd frontend
npm test -- research.test.ts
```

Expected: PASS.

- [ ] **Step 5: Commit Task 3**

Run:

```bash
git add frontend/src/api/research.ts frontend/src/api/research.test.ts
git commit -m "feat: add research overview frontend client"
```

---

## Task 4: Research Page, Routing, And Visual Design

**Files:**

- Create: `frontend/src/pages/ResearchPage.tsx`
- Create: `frontend/src/pages/ResearchPage.css`
- Create: `frontend/src/pages/ResearchPage.test.tsx`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/components/NavBar.tsx`
- Modify: `frontend/src/components/NavBar.css`
- Modify: `frontend/src/components/NavBar.test.tsx`

- [ ] **Step 1: Write NavBar test for the research link**

Modify `frontend/src/components/NavBar.test.tsx` by adding:

```ts
it('renders a Research link to the dashboard', () => {
  render(<MemoryRouter><NavBar /></MemoryRouter>)

  const researchLink = screen.getByRole('link', { name: 'Research' })

  expect(researchLink).toBeInTheDocument()
  expect(researchLink).toHaveAttribute('href', '/research')
})
```

- [ ] **Step 2: Run NavBar test and confirm it fails**

Run:

```bash
cd frontend
npm test -- NavBar.test.tsx
```

Expected: FAIL because `Research` link is not rendered.

- [ ] **Step 3: Update NavBar**

Modify `frontend/src/components/NavBar.tsx`:

```tsx
import { Link } from 'react-router-dom'
import './NavBar.css'

export default function NavBar() {
  return (
    <nav className="navbar">
      <Link to="/" className="navbar__logo">SIGAK</Link>
      <div className="navbar__links" aria-label="Primary navigation">
        <Link to="/research" className="navbar__link">Research</Link>
        <span className="navbar__hint">AI · Security · Engineering</span>
      </div>
    </nav>
  )
}
```

Modify `frontend/src/components/NavBar.css`:

```css
.navbar {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 11px 24px;
  border-bottom: 2px solid var(--color-text);
  background: var(--color-base);
}

.navbar__logo {
  font-family: var(--font-serif);
  font-size: 16px;
  font-weight: 700;
  color: var(--color-text);
  letter-spacing: 0;
}

.navbar__links {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-left: auto;
}

.navbar__link {
  color: var(--color-text);
  font-size: 12px;
  font-weight: 700;
}

.navbar__link:hover {
  color: var(--color-accent);
}

.navbar__hint {
  font-size: 11px;
  color: var(--color-text-meta);
}

@media (max-width: 560px) {
  .navbar {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
  }

  .navbar__links {
    margin-left: 0;
  }
}
```

- [ ] **Step 4: Write ResearchPage rendering tests**

Create `frontend/src/pages/ResearchPage.test.tsx`:

```tsx
import { render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ResearchPage from './ResearchPage'
import { fetchResearchOverview } from '../api/research'

vi.mock('../api/research', () => ({
  fetchResearchOverview: vi.fn(),
}))

const mockOverview = {
  generatedAt: '2026-06-12T00:00:00Z',
  retrieval: {
    available: true,
    unavailableReason: null,
    catalogId: 'api-ready-2026-06-02',
    catalogArticleCount: 6,
    evaluatedQueryCount: 3,
    k: 5,
    generatedAt: '2026-06-02T15:06:29.546Z',
    systems: [
      {
        name: 'keyword',
        attemptedQueryCount: 3,
        completedQueryCount: 3,
        failedQueryCount: 0,
        degradedQueryCount: 0,
        macroTop1StrongHit: 1,
        macroRecallAtK: 0.6667,
        macroMrrAtK: 1,
        averageLatencyMs: 21.67,
      },
      {
        name: 'hybrid',
        attemptedQueryCount: 3,
        completedQueryCount: 3,
        failedQueryCount: 0,
        degradedQueryCount: 0,
        macroTop1StrongHit: 0.6667,
        macroRecallAtK: 0.8333,
        macroMrrAtK: 0.8333,
        averageLatencyMs: 15,
      },
    ],
    warnings: ['label set is smaller than 10 reviewed queries'],
  },
  graph: {
    available: true,
    unavailableReason: null,
    catalogId: 'api-ready-2026-06-02',
    catalogArticleCount: 6,
    evaluatedQueryCount: 3,
    k: 5,
    generatedAt: '2026-06-04T03:21:51.802Z',
    macroSearchPositiveCoverageAtK: 0.6667,
    macroRelatedBaselineCoverageAtK: 0.3333,
    macroGraphContextCoverageAtK: 0.3333,
    macroGraphReasonedCoverageAtK: 0.3333,
    graphContextFailureRate: 0,
    emptyContextRate: 0,
    averageGraphLatencyMs: 33.53,
    queryEvidence: [
      {
        query: 'graph rag failure',
        intent: 'Find graph RAG failure analysis.',
        searchPositiveCoverageAtK: 0.5,
        relatedBaselineCoverageAtK: 0,
        graphContextCoverageAtK: 0.5,
        graphReasonedCoverageAtK: 0.5,
        reasonCoverage: 1,
        topicContextCoverage: 1,
        averageGraphLatencyMs: 8.8,
        searchMissedPositiveIds: [1],
      },
    ],
    warnings: ['graph latency is measured as public API round-trip'],
  },
  expandedCatalog: {
    available: true,
    unavailableReason: null,
    catalogId: 'api-ready-2026-06-05',
    generatedAt: '2026-06-04T15:45:30.641155Z',
    articleCount: 41,
    labelStatus: 'draft_only',
    benchmarkStatus: 'blocked_until_reviewed_labels',
    warnings: ['Reviewed labels are required before quality claims use the expanded catalog.'],
  },
  limitations: ['Current retrieval and graph metrics are smoke evidence, not broad quality claims.'],
}

describe('ResearchPage', () => {
  beforeEach(() => {
    vi.mocked(fetchResearchOverview).mockReset()
  })

  it('renders portfolio research metrics from the overview API', async () => {
    vi.mocked(fetchResearchOverview).mockResolvedValue(mockOverview)

    render(<ResearchPage />)

    expect(screen.getByRole('status')).toHaveTextContent('Loading research metrics...')
    expect(await screen.findByRole('heading', { name: 'Research Evidence' })).toBeInTheDocument()
    expect(screen.getByText('Hybrid Retrieval')).toBeInTheDocument()
    expect(screen.getByText('keyword')).toBeInTheDocument()
    expect(screen.getByText('hybrid')).toBeInTheDocument()
    expect(screen.getByText('graph rag failure')).toBeInTheDocument()
    expect(screen.getByText('41 articles')).toBeInTheDocument()
    expect(screen.getByText('Current retrieval and graph metrics are smoke evidence, not broad quality claims.')).toBeInTheDocument()
  })

  it('renders section unavailable states without hiding the rest of the dashboard', async () => {
    vi.mocked(fetchResearchOverview).mockResolvedValue({
      ...mockOverview,
      retrieval: {
        ...mockOverview.retrieval,
        available: false,
        unavailableReason: 'Artifact not found: results/retrieval/latest/metrics.comparison.json',
        systems: [],
      },
    })

    render(<ResearchPage />)

    expect(await screen.findByText('Retrieval artifact unavailable')).toBeInTheDocument()
    expect(screen.getByText('Artifact not found: results/retrieval/latest/metrics.comparison.json')).toBeInTheDocument()
    expect(screen.getByText('Graph Context')).toBeInTheDocument()
  })

  it('renders a retry action when the overview API fails', async () => {
    vi.mocked(fetchResearchOverview).mockRejectedValue(new Error('network'))

    render(<ResearchPage />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to load research metrics.')
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument()
  })
})
```

- [ ] **Step 5: Run ResearchPage test and confirm it fails**

Run:

```bash
cd frontend
npm test -- ResearchPage.test.tsx
```

Expected: FAIL because `ResearchPage` does not exist.

- [ ] **Step 6: Implement ResearchPage**

Create `frontend/src/pages/ResearchPage.tsx`.
Use this component structure:

```tsx
import { useCallback, useEffect, useMemo, useState } from 'react'
import { fetchResearchOverview } from '../api/research'
import type { ResearchOverview } from '../api/research'
import './ResearchPage.css'

export default function ResearchPage() {
  const [overview, setOverview] = useState<ResearchOverview | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState('')

  const loadOverview = useCallback(async () => {
    setIsLoading(true)
    setErrorMessage('')
    try {
      setOverview(await fetchResearchOverview())
    } catch {
      setOverview(null)
      setErrorMessage('Unable to load research metrics.')
    } finally {
      setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    void loadOverview()
  }, [loadOverview])

  return (
    <main className="research-page">
      <header className="research-hero">
        <p className="research-eyebrow">Portfolio Evidence</p>
        <h1>Research Evidence</h1>
        <p className="research-hero__copy">
          Hybrid retrieval, graph context, and reproducible smoke metrics from Sigak's local experiment artifacts.
        </p>
      </header>

      {isLoading && <p className="research-status" role="status">Loading research metrics...</p>}

      {errorMessage && (
        <div className="research-state" role="alert">
          <p>{errorMessage}</p>
          <button type="button" onClick={loadOverview}>Retry</button>
        </div>
      )}

      {!isLoading && !errorMessage && overview && <ResearchDashboard overview={overview} />}
    </main>
  )
}
```

Add child components in the same file with focused names:

```tsx
function ResearchDashboard({ overview }: { overview: ResearchOverview }) {
  const scopeItems = [
    ['Generated', formatDate(overview.generatedAt)],
    ['Retrieval Catalog', overview.retrieval.catalogId ?? 'Unavailable'],
    ['Graph Catalog', overview.graph.catalogId ?? 'Unavailable'],
    ['Expanded Catalog', overview.expandedCatalog.catalogId ?? 'Unavailable'],
  ]

  return (
    <>
      <section className="research-scope" aria-label="Research artifact scope">
        {scopeItems.map(([label, value]) => (
          <div key={label} className="research-scope__item">
            <span>{label}</span>
            <strong>{value}</strong>
          </div>
        ))}
      </section>
      <PortfolioSignals />
      <RetrievalSection overview={overview} />
      <GraphSection overview={overview} />
      <CatalogSection overview={overview} />
      <Limitations limitations={overview.limitations} />
    </>
  )
}
```

Required formatting helpers:

```tsx
function formatPercent(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return 'n/a'
  }
  return `${Math.round(value * 100)}%`
}

function formatMs(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return 'n/a'
  }
  return `${value.toFixed(value >= 10 ? 1 : 2)} ms`
}

function formatDate(value: string | null | undefined): string {
  if (!value) {
    return 'Unavailable'
  }

  return new Intl.DateTimeFormat('en', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  }).format(new Date(value))
}
```

Required section behavior:

- `RetrievalSection` renders heading `Retrieval Benchmark`.
- If `overview.retrieval.available` is false, render `Retrieval artifact unavailable` and `overview.retrieval.unavailableReason`.
- If retrieval is available, render a table with columns `System`, `Recall@K`, `MRR@K`, `Top-1`, `Latency`, `Failed`, `Degraded`.
- `GraphSection` renders heading `Graph-Aware Insight`.
- If graph is available, render metric cells for `Graph Context`, `Reasoned Coverage`, `Failure Rate`, `Graph Latency`.
- `GraphSection` renders a query evidence table with the `query` text visible.
- `CatalogSection` renders `41 articles` when `articleCount` is `41`.
- `Limitations` renders every item from `overview.limitations`.

- [ ] **Step 7: Add ResearchPage CSS**

Create `frontend/src/pages/ResearchPage.css` with these class groups and visual constraints:

```css
.research-page {
  width: min(1040px, calc(100% - 48px));
  margin: 0 auto;
  padding: 44px 0 72px;
}

.research-hero {
  margin-bottom: 28px;
}

.research-eyebrow {
  margin: 0 0 8px;
  color: var(--color-accent);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0;
  text-transform: uppercase;
}

.research-hero h1 {
  margin: 0;
  color: var(--color-text);
  font-family: var(--font-serif);
  font-size: 42px;
  line-height: 1.05;
  letter-spacing: 0;
}

.research-hero__copy {
  max-width: 620px;
  margin: 14px 0 0;
  color: var(--color-text-muted);
  font-size: 15px;
  line-height: 1.6;
}

.research-scope,
.research-signal-grid,
.research-metric-grid {
  display: grid;
  gap: 12px;
}

.research-scope {
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-bottom: 28px;
  border-top: 1px solid var(--color-border);
  border-bottom: 1px solid var(--color-border);
}

.research-scope__item {
  padding: 14px 0;
}

.research-scope__item span,
.research-card__label,
.research-table th {
  color: var(--color-text-meta);
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
}

.research-scope__item strong {
  display: block;
  margin-top: 6px;
  color: var(--color-text);
  font-size: 13px;
}

.research-section {
  margin-top: 34px;
}

.research-section__header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
  border-bottom: 1px solid var(--color-border);
  padding-bottom: 10px;
}

.research-section h2 {
  margin: 0;
  font-size: 18px;
  letter-spacing: 0;
}

.research-section__meta {
  color: var(--color-text-meta);
  font-size: 12px;
}

.research-signal-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.research-card {
  min-height: 96px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-base);
  padding: 16px;
}

.research-card strong {
  display: block;
  margin-top: 8px;
  color: var(--color-text);
  font-size: 18px;
}

.research-card p {
  margin: 8px 0 0;
  color: var(--color-text-muted);
  font-size: 13px;
  line-height: 1.45;
}

.research-table-wrap {
  overflow-x: auto;
  border: 1px solid var(--color-border);
  border-radius: 8px;
}

.research-table {
  width: 100%;
  min-width: 720px;
  border-collapse: collapse;
}

.research-table th,
.research-table td {
  border-bottom: 1px solid var(--color-border);
  padding: 12px 14px;
  text-align: left;
}

.research-table tbody tr:last-child td {
  border-bottom: 0;
}

.research-warning,
.research-unavailable,
.research-state {
  border: 1px solid var(--color-accent-border);
  border-radius: 8px;
  background: var(--color-accent-surface);
  color: var(--color-text);
  padding: 14px 16px;
}

.research-status {
  color: var(--color-text-meta);
  font-size: 13px;
}

.research-state button {
  margin-top: 10px;
  border: 1px solid var(--color-accent-border);
  border-radius: 4px;
  background: var(--color-base);
  color: var(--color-accent);
  cursor: pointer;
  font-size: 12px;
  font-weight: 700;
  padding: 7px 14px;
}

@media (max-width: 760px) {
  .research-page {
    width: min(100% - 32px, 1040px);
    padding-top: 32px;
  }

  .research-hero h1 {
    font-size: 34px;
  }

  .research-scope,
  .research-signal-grid,
  .research-metric-grid {
    grid-template-columns: 1fr;
  }

  .research-section__header {
    align-items: flex-start;
    flex-direction: column;
  }
}
```

The implementation may add additional classes for metric rows and warning lists, but must keep these constraints:

- no nested cards
- no gradient hero
- no decorative blobs
- no viewport-width font scaling
- no negative letter spacing

- [ ] **Step 8: Add route and app shell test**

Modify `frontend/src/App.tsx`:

```tsx
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import NavBar from './components/NavBar'
import HomePage from './pages/HomePage'
import ArticleDetailPage from './pages/ArticleDetailPage'
import ResearchPage from './pages/ResearchPage'

export default function App() {
  return (
    <BrowserRouter>
      <NavBar />
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/articles/:id" element={<ArticleDetailPage />} />
        <Route path="/research" element={<ResearchPage />} />
      </Routes>
    </BrowserRouter>
  )
}
```

Modify `frontend/src/App.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react'
import { expect, it, vi } from 'vitest'
import App from './App'

vi.mock('./api/articles', () => ({
  fetchArticles: vi.fn().mockResolvedValue([]),
  fetchArticle: vi.fn(),
  fetchArticleGraphContext: vi.fn(),
  fetchArticlesByIds: vi.fn(),
}))

vi.mock('./api/research', () => ({
  fetchResearchOverview: vi.fn(),
}))

it('renders the NavBar logo', async () => {
  render(<App />)
  expect(await screen.findByRole('link', { name: 'SIGAK' })).toBeInTheDocument()
})

it('renders a Research navigation link', async () => {
  render(<App />)
  expect(await screen.findByRole('link', { name: 'Research' })).toHaveAttribute('href', '/research')
})
```

- [ ] **Step 9: Run focused page and nav tests**

Run:

```bash
cd frontend
npm test -- NavBar.test.tsx ResearchPage.test.tsx App.test.tsx
```

Expected: PASS.

- [ ] **Step 10: Commit Task 4**

Run:

```bash
git add frontend/src/App.tsx frontend/src/App.test.tsx frontend/src/components/NavBar.tsx frontend/src/components/NavBar.css frontend/src/components/NavBar.test.tsx frontend/src/pages/ResearchPage.tsx frontend/src/pages/ResearchPage.css frontend/src/pages/ResearchPage.test.tsx
git commit -m "feat: add research dashboard page"
```

---

## Task 5: Full Verification, Visual Smoke, And Session Docs

**Files:**

- Modify: `docs/STATUS.md`
- Modify: `docs/STATUS.ko.md`
- Modify: `docs/ROADMAP.md`
- Modify: `docs/ROADMAP.ko.md`
- Create or modify: `docs/blog/2026-06-12-dev-log.md`
- Modify: `docs/blog/topic-queue.md`

- [ ] **Step 1: Run backend verification gate**

Run:

```bash
cd backend
./gradlew test
./gradlew check
```

Expected: both commands pass.

- [ ] **Step 2: Run frontend verification gate**

Run:

```bash
cd frontend
npm test
npm run lint
npm run build
```

Expected: all commands pass.

- [ ] **Step 3: Run backend API smoke with real artifacts**

Run:

```bash
cd backend
SIGAK_RESEARCH_ARTIFACT_ROOT=../experiments ./gradlew bootRun
```

In a second terminal:

```bash
curl -s http://localhost:8080/api/research/overview | jq '{retrievalAvailable: .retrieval.available, retrievalSystems: [.retrieval.systems[].name], graphAvailable: .graph.available, expandedArticleCount: .expandedCatalog.articleCount, limitationCount: (.limitations | length)}'
```

Expected:

```json
{
  "retrievalAvailable": true,
  "retrievalSystems": [
    "keyword",
    "vector",
    "hybrid",
    "public"
  ],
  "graphAvailable": true,
  "expandedArticleCount": 41,
  "limitationCount": 2
}
```

Stop `bootRun` after the smoke check.

- [ ] **Step 4: Run frontend visual smoke**

Run backend:

```bash
cd backend
SIGAK_RESEARCH_ARTIFACT_ROOT=../experiments ./gradlew bootRun
```

Run frontend:

```bash
cd frontend
npm run dev -- --host 127.0.0.1
```

Open:

```txt
http://127.0.0.1:5173/research
```

Verify manually or with the in-app browser:

- The page is not blank.
- The hero headline, signal strip, retrieval table, graph section, expanded catalog, and limitations are visible.
- The page reads as an analytical dashboard, not a landing page.
- Mobile width around 390px does not overlap text or table labels.
- The expanded catalog shows `41 articles`.
- The retrieval table shows keyword, vector, hybrid, and public.

Stop both servers after the smoke check.

- [ ] **Step 5: Update status and roadmap**

Update `docs/STATUS.md` and `docs/STATUS.ko.md`:

- Set `Last updated: 2026-06-12`.
- In frontend current state, mention `/research` dashboard.
- In search/research infra current state, mention that the dashboard reads committed retrieval/graph/catalog artifacts.
- In risks, keep reviewed labels as the remaining limitation.

Update `docs/ROADMAP.md` and `docs/ROADMAP.ko.md`:

- Mark S6 `/research` initial dashboard work as implemented.
- Keep RAG/fine-tuning/failure-case expansion unchecked.
- Keep reviewed labels and research report work as remaining.

- [ ] **Step 6: Write dev-log and update topic queue**

Read:

```bash
sed -n '1,220p' docs/blog/WRITING_GUIDE.ko.md
```

Create or update `docs/blog/2026-06-12-dev-log.md` with verified facts only:

- 작업 요약
- 변경 파일
- 실행한 검증 명령과 결과
- 확인한 API smoke values
- 확인한 frontend visual smoke details
- 남은 미검증 또는 후속 과제

Update `docs/blog/topic-queue.md` if the writing guide criteria match this session.

- [ ] **Step 7: Final git status check**

Run:

```bash
git status --short --branch
git log --oneline --decorate -8
```

Expected:

- Only intentional docs changes are unstaged before the final docs commit.
- The branch contains the spec commit, backend commits, frontend commits, and docs wrap-up commit.

- [ ] **Step 8: Commit Task 5**

Run:

```bash
git add docs/STATUS.md docs/STATUS.ko.md docs/ROADMAP.md docs/ROADMAP.ko.md docs/blog/2026-06-12-dev-log.md docs/blog/topic-queue.md
git commit -m "docs: record research dashboard implementation"
```

---

## Final Completion Checklist

- [ ] `cd backend && ./gradlew test` passed.
- [ ] `cd backend && ./gradlew check` passed.
- [ ] `cd frontend && npm test` passed.
- [ ] `cd frontend && npm run lint` passed.
- [ ] `cd frontend && npm run build` passed.
- [ ] `GET /api/research/overview` returned `retrieval.available=true`.
- [ ] `GET /api/research/overview` returned `expandedCatalog.articleCount=41`.
- [ ] `/research` rendered in the browser.
- [ ] Desktop and mobile visual smoke checks found no obvious overlap.
- [ ] `docs/STATUS.md` and `docs/ROADMAP.md` were updated.
- [ ] Korean companion docs were updated where the English docs changed.
- [ ] Dev-log and topic queue were updated with verified facts only.
