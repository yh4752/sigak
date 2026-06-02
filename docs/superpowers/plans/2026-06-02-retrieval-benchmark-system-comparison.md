# Retrieval Benchmark System Comparison Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 같은 label JSON으로 keyword, vector, strict hybrid, public 검색 결과를 나란히 평가하는 retrieval benchmark 비교 흐름을 만든다.

**Architecture:** Public article API는 사용자 경험용 fallback/degrade 동작을 유지한다. Backend에는 internal-only strict retrieval run endpoint를 추가하고, Node runner는 internal strict systems와 기존 public API system을 같은 metric/report artifact로 합친다.

**Tech Stack:** Kotlin, Spring Boot, MockMvc, Kotlin test, Node.js ESM, Node built-in `node:test`, built-in `fetch`, PostgreSQL API-ready reload boundary, Elasticsearch keyword search, Qdrant vector search, Reciprocal Rank Fusion.

---

## Reference Context

- Spec: `docs/superpowers/specs/2026-06-02-retrieval-benchmark-system-comparison-design.md`
- Current runner plan: `docs/superpowers/plans/2026-06-02-retrieval-benchmark-smoke-runner.md`
- Backend conventions: `docs/CODING_CONVENTIONS.md`
- Runner modules:
  - `experiments/scripts/retrieval-benchmark/cli.mjs`
  - `experiments/scripts/retrieval-benchmark/search-client.mjs`
  - `experiments/scripts/retrieval-benchmark/metrics.mjs`
  - `experiments/scripts/retrieval-benchmark/report-writer.mjs`
  - `experiments/scripts/retrieval-benchmark/runner.mjs`
- Backend search modules:
  - `backend/src/main/kotlin/com/sigak/search/hybrid/ArticlePublicSearchService.kt`
  - `backend/src/main/kotlin/com/sigak/search/hybrid/ArticleVectorCandidateSearchService.kt`
  - `backend/src/main/kotlin/com/sigak/search/hybrid/ReciprocalRankFusion.kt`
  - `backend/src/main/kotlin/com/sigak/search/service/ArticleKeywordSearchService.kt`
  - `backend/src/main/kotlin/com/sigak/article/service/ArticleService.kt`

## Worktree Guard

- Run `git status --short --branch` before implementation.
- If unrelated files are modified, read them only when the task touches the same file.
- Do not revert user changes.
- Do not run `git commit` or `git push` unless the user asks for it. This project rule overrides generic frequent-commit guidance.

## Mode Boundary And Safety Guard

Keep these boundaries explicit in code, tests, reports, and docs:

- Backend internal endpoint systems are only `KEYWORD`, `VECTOR`, and strict `HYBRID`.
- `PUBLIC` is never sent to `/api/internal/search-evaluation/retrieval-runs`.
- `PUBLIC` run artifacts come only from `GET /api/articles?query=...`, because they represent user-visible behavior.
- strict `HYBRID` is an experiment system. It fails when keyword or vector fails and does not degrade.
- public search is a product behavior. It may degrade to `KEYWORD_ONLY`, `VECTOR_ONLY`, or `POSTGRES_FALLBACK` so users still see results.
- Reports must explain that strict `HYBRID` and `PUBLIC` are intentionally different, not inconsistent.
- Public article API response shape must stay unchanged.
- Internal metadata may include embedding provider, model name, and dimension only. It must not include API keys, request headers, environment variable values, service URLs, credentials, raw embedding vectors, or stack traces.
- Every task has a focused verification command. If actual output differs from the expected result, stop and debug before continuing to the next task.

## File Structure

Create:

- `backend/src/main/kotlin/com/sigak/search/hybrid/ArticleRetrievalCandidateService.kt`
  - Shared candidate boundary for keyword/vector attempts, timings, and stable failure reason strings.
- `backend/src/test/kotlin/com/sigak/search/hybrid/ArticleRetrievalCandidateServiceTest.kt`
  - Unit tests for candidate attempts and failure reason mapping.
- `backend/src/main/kotlin/com/sigak/search/evaluation/retrieval/ArticleRetrievalEvaluationProperties.kt`
  - Config flag for internal endpoint exposure.
- `backend/src/main/kotlin/com/sigak/search/evaluation/retrieval/ArticleRetrievalEvaluationConfig.kt`
  - Enables the properties class.
- `backend/src/main/kotlin/com/sigak/search/evaluation/retrieval/ArticleRetrievalEvaluationDtos.kt`
  - Request/response DTOs and enums for strict run output.
- `backend/src/main/kotlin/com/sigak/search/evaluation/retrieval/ArticleRetrievalEvaluationService.kt`
  - Creates strict `KEYWORD`, `VECTOR`, and `HYBRID` run items.
- `backend/src/main/kotlin/com/sigak/search/evaluation/retrieval/ArticleRetrievalEvaluationController.kt`
  - Thin internal controller under `/api/internal/search-evaluation`.
- `backend/src/test/kotlin/com/sigak/search/evaluation/retrieval/ArticleRetrievalEvaluationServiceTest.kt`
  - Service tests for strict semantics, stale candidate counting, and ordering.
- `backend/src/test/kotlin/com/sigak/search/evaluation/retrieval/ArticleRetrievalEvaluationControllerTest.kt`
  - Controller tests for validation, disabled guard, and `PUBLIC` rejection.

Modify:

- `backend/src/main/kotlin/com/sigak/search/hybrid/ArticlePublicSearchService.kt`
  - Use `ArticleRetrievalCandidateService` instead of owning private candidate attempt logic.
- `backend/src/test/kotlin/com/sigak/search/hybrid/ArticlePublicSearchServiceTest.kt`
  - Adjust construction to inject the shared candidate service and preserve current public fallback tests.
- `backend/src/main/resources/application.yml`
  - Add disabled-by-default guard `sigak.internal.search-evaluation.enabled=false` and opt in with `SIGAK_INTERNAL_SEARCH_EVALUATION_ENABLED=true`.
- `experiments/scripts/retrieval-benchmark/cli.mjs`
  - Add `--systems` and `--limit`.
- `experiments/scripts/retrieval-benchmark/search-client.mjs`
  - Add internal evaluation client and public metrics lookup helper.
- `experiments/scripts/retrieval-benchmark/metrics.mjs`
  - Add system-aware by-query rows, by-system summary, effective metrics, failure/degrade counts, and comparison warnings.
- `experiments/scripts/retrieval-benchmark/report-writer.mjs`
  - Write `runs.<system>.json`, `metrics.by-system.json`, `metrics.comparison.json`, and comparison report.
- `experiments/scripts/retrieval-benchmark/runner.mjs`
  - Keep existing smoke path when `--systems` is omitted; use comparison mode when provided.
- Existing Node tests under `experiments/scripts/retrieval-benchmark/*.test.mjs`
  - Extend tests for comparison mode without removing current smoke behavior.
- `docs/API_SPEC.md`
  - Document the internal endpoint and exposure guard.
- `experiments/README.md`
  - Document comparison command and artifact layout.
- `docs/search-evaluation/queries.md`
  - Add Korean explanation of keyword/vector/hybrid/public comparison.
- `docs/STATUS.md`, `docs/STATUS.ko.md`, `docs/ROADMAP.md`, `docs/ROADMAP.ko.md`
  - Update after verified implementation.
- `docs/blog/2026-06-02-dev-log.md`, `docs/blog/topic-queue.md`
  - Record only commands and results actually verified in the implementation session.

## Output Contract

Comparison command:

```bash
node experiments/scripts/retrieval-benchmark.mjs \
  --labels=experiments/datasets/labels/search-labels.api-ready-2026-06-02.2026-06-02.json \
  --base-url=http://localhost:8080 \
  --output-dir=experiments/results/retrieval/latest \
  --systems=keyword,vector,hybrid,public \
  --k=5 \
  --limit=20
```

Generated files:

```text
experiments/results/retrieval/latest/
├── runs.keyword.json
├── runs.vector.json
├── runs.hybrid.json
├── runs.public.json
├── metrics.by-query.json
├── metrics.by-system.json
├── metrics.comparison.json
└── report.md
```

## Task 1: Preflight And Existing Contract Protection

**Files:**
- Read: `AGENTS.md`
- Read: `docs/STATUS.md`
- Read: `docs/ROADMAP.md`
- Read: `docs/superpowers/specs/2026-06-02-retrieval-benchmark-system-comparison-design.md`
- Read: `docs/CODING_CONVENTIONS.md`
- Read: `backend/src/main/kotlin/com/sigak/search/hybrid/ArticlePublicSearchService.kt`
- Read: `experiments/scripts/retrieval-benchmark/runner.mjs`

- [ ] **Step 1: Confirm branch and uncommitted changes**

Run:

```bash
git status --short --branch
```

Expected:

```text
## codex/collection-projection-demo-flow...origin/codex/collection-projection-demo-flow
```

If additional modified files appear, record which planned task touches them. Do not revert them.

- [ ] **Step 2: Run existing focused tests before editing**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.search.hybrid.ArticlePublicSearchServiceTest
cd ..
node --test experiments/scripts/retrieval-benchmark/*.test.mjs
```

Expected:

```text
BUILD SUCCESSFUL
# pass
```

If a command fails before editing, stop implementation and debug the pre-existing failure first.

## Task 2: Extract Shared Retrieval Candidate Service

**Files:**
- Create: `backend/src/main/kotlin/com/sigak/search/hybrid/ArticleRetrievalCandidateService.kt`
- Create: `backend/src/test/kotlin/com/sigak/search/hybrid/ArticleRetrievalCandidateServiceTest.kt`
- Modify: `backend/src/main/kotlin/com/sigak/search/hybrid/ArticlePublicSearchService.kt`
- Modify: `backend/src/test/kotlin/com/sigak/search/hybrid/ArticlePublicSearchServiceTest.kt`

- [ ] **Step 1: Write failing candidate service tests**

Create `backend/src/test/kotlin/com/sigak/search/hybrid/ArticleRetrievalCandidateServiceTest.kt` with these behaviors:

```kotlin
package com.sigak.search.hybrid

import com.sigak.search.config.SearchInfrastructureProperties
import com.sigak.search.service.ArticleKeywordSearchService
import kotlin.test.Test
import kotlin.test.assertEquals

class ArticleRetrievalCandidateServiceTest {

    private val keywordSearchService = RecordingKeywordSearchService()
    private val vectorCandidateSearcher = RecordingVectorCandidateSearcher()
    private val service = ArticleRetrievalCandidateService(
        articleKeywordSearchService = keywordSearchService,
        articleVectorCandidateSearcher = vectorCandidateSearcher,
        properties = SearchInfrastructureProperties()
    )

    @Test
    fun searchReturnsKeywordAndVectorAttemptsWhenBothSucceed() {
        keywordSearchService.articleIds = listOf(10L, 20L)
        vectorCandidateSearcher.result = vectorResult(
            ArticleSearchCandidate(articleId = 30L, rank = 1, score = 0.91)
        )

        val result = service.search(" graph ")

        assertEquals("graph", result.query)
        assertEquals(listOf(10L, 20L), result.keyword.value?.map { candidate -> candidate.articleId })
        assertEquals(listOf(30L), result.vector.value?.candidates?.map { candidate -> candidate.articleId })
        assertEquals(null, result.keyword.failureReason)
        assertEquals(null, result.vector.failureReason)
        assertEquals("graph", keywordSearchService.requestedQuery)
        assertEquals(20, keywordSearchService.requestedLimit)
        assertEquals("graph", vectorCandidateSearcher.requestedQuery)
        assertEquals(20, vectorCandidateSearcher.requestedLimit)
    }

    @Test
    fun searchPreservesStableKeywordFailureReason() {
        keywordSearchService.exception = RuntimeException("elasticsearch unavailable")
        vectorCandidateSearcher.result = vectorResult()

        val result = service.search("graph")

        assertEquals("KEYWORD_SEARCH_FAILED", result.keyword.failureReason)
        assertEquals(true, result.keyword.failed)
        assertEquals(false, result.vector.failed)
    }

    @Test
    fun searchPreservesVectorSpecificFailureReasonAndTimings() {
        keywordSearchService.articleIds = listOf(10L)
        vectorCandidateSearcher.exception = ArticleVectorCandidateSearchException(
            reasonCode = "QDRANT_SEARCH_FAILED",
            embeddingElapsedMs = 3,
            vectorElapsedMs = 9,
            cause = RuntimeException("qdrant unavailable")
        )

        val result = service.search("graph")

        assertEquals("QDRANT_SEARCH_FAILED", result.vector.failureReason)
        assertEquals(3, result.vector.embeddingElapsedMs)
        assertEquals(9, result.vector.vectorElapsedMs)
    }

    private companion object {
        fun vectorResult(vararg candidates: ArticleSearchCandidate): ArticleVectorCandidateSearchResult =
            ArticleVectorCandidateSearchResult(
                query = "graph",
                candidates = candidates.toList(),
                embeddingProvider = "local",
                embeddingModelName = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
                embeddingDimension = 384,
                embeddingElapsedMs = 0,
                vectorElapsedMs = 0,
                totalElapsedMs = 0
            )
    }

    private class RecordingKeywordSearchService : ArticleKeywordSearchService {
        var articleIds: List<Long> = emptyList()
        var exception: RuntimeException? = null
        var requestedQuery: String? = null
        var requestedLimit: Int? = null

        override fun searchArticleIds(query: String, limit: Int): List<Long> {
            requestedQuery = query
            requestedLimit = limit
            exception?.let { throw it }
            return articleIds
        }
    }

    private class RecordingVectorCandidateSearcher : ArticleVectorCandidateSearcher {
        var result = vectorResult()
        var exception: RuntimeException? = null
        var requestedQuery: String? = null
        var requestedLimit: Int? = null

        override fun collectionName(): String = "sigak-article-vectors-test"

        override fun search(query: String, limit: Int): ArticleVectorCandidateSearchResult {
            requestedQuery = query
            requestedLimit = limit
            exception?.let { throw it }
            return result
        }
    }
}
```

- [ ] **Step 2: Run the new test and verify it fails**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.search.hybrid.ArticleRetrievalCandidateServiceTest
```

Expected:

```text
Unresolved reference: ArticleRetrievalCandidateService
```

- [ ] **Step 3: Create the shared candidate service**

Create `backend/src/main/kotlin/com/sigak/search/hybrid/ArticleRetrievalCandidateService.kt`:

```kotlin
package com.sigak.search.hybrid

import com.sigak.common.time.elapsedMillis
import com.sigak.search.config.SearchInfrastructureProperties
import com.sigak.search.service.ArticleKeywordSearchService
import org.springframework.stereotype.Service

@Service
class ArticleRetrievalCandidateService(
    private val articleKeywordSearchService: ArticleKeywordSearchService,
    private val articleVectorCandidateSearcher: ArticleVectorCandidateSearcher,
    private val properties: SearchInfrastructureProperties
) : ArticleRetrievalCandidateProvider {

    override fun search(query: String): ArticleRetrievalCandidateSearchResult {
        val normalizedQuery = query.trim()

        return ArticleRetrievalCandidateSearchResult(
            query = normalizedQuery,
            keyword = searchKeyword(normalizedQuery),
            vector = searchVector(normalizedQuery)
        )
    }

    override fun searchKeyword(query: String): ArticleRetrievalCandidateAttempt<List<ArticleSearchCandidate>> {
        val normalizedQuery = query.trim()

        return searchKeywordCandidates(normalizedQuery)
    }

    override fun searchVector(query: String): ArticleRetrievalCandidateAttempt<ArticleVectorCandidateSearchResult> {
        val normalizedQuery = query.trim()

        return searchVectorCandidates(normalizedQuery)
    }

    private fun searchKeywordCandidates(query: String): ArticleRetrievalCandidateAttempt<List<ArticleSearchCandidate>> =
        runCatchingMeasured {
            articleKeywordSearchService.searchArticleIds(
                query = query,
                limit = properties.hybrid.keywordCandidateLimit
            ).mapIndexed { index, articleId ->
                ArticleSearchCandidate(articleId = articleId, rank = index + 1)
            }
        }.toKeywordAttempt()

    private fun searchVectorCandidates(query: String): ArticleRetrievalCandidateAttempt<ArticleVectorCandidateSearchResult> =
        runCatchingMeasured {
            articleVectorCandidateSearcher.search(
                query = query,
                limit = properties.hybrid.vectorCandidateLimit
            )
        }.toVectorAttempt()

    private fun <T> SearchAttempt<T>.toKeywordAttempt(): ArticleRetrievalCandidateAttempt<T> =
        ArticleRetrievalCandidateAttempt(
            value = value,
            exception = exception,
            failureReason = exception?.let { "KEYWORD_SEARCH_FAILED" },
            elapsedMs = elapsedMs,
            embeddingElapsedMs = 0,
            vectorElapsedMs = 0
        )

    private fun SearchAttempt<ArticleVectorCandidateSearchResult>.toVectorAttempt():
        ArticleRetrievalCandidateAttempt<ArticleVectorCandidateSearchResult> {
        val vectorFailure = exception as? ArticleVectorCandidateSearchException

        return ArticleRetrievalCandidateAttempt(
            value = value,
            exception = exception,
            failureReason = exception?.toVectorFailureReason(),
            elapsedMs = elapsedMs,
            embeddingElapsedMs = value?.embeddingElapsedMs ?: vectorFailure?.embeddingElapsedMs ?: 0,
            vectorElapsedMs = value?.vectorElapsedMs ?: vectorFailure?.vectorElapsedMs ?: 0
        )
    }

    private fun Exception.toVectorFailureReason(): String =
        when (this) {
            is ArticleVectorCandidateSearchException -> reasonCode
            else -> "VECTOR_SEARCH_FAILED"
        }

    private fun <T> runCatchingMeasured(block: () -> T): SearchAttempt<T> {
        val startedAt = System.nanoTime()

        return try {
            SearchAttempt(value = block(), exception = null, elapsedMs = elapsedMillis(startedAt))
        } catch (exception: Exception) {
            // 검색 projection client는 실패 타입이 달라질 수 있어 evaluation 경계에서도 Exception까지 안정적으로 기록한다.
            SearchAttempt(value = null, exception = exception, elapsedMs = elapsedMillis(startedAt))
        }
    }

    private data class SearchAttempt<T>(
        val value: T?,
        val exception: Exception?,
        val elapsedMs: Long
    )
}

interface ArticleRetrievalCandidateProvider {
    fun search(query: String): ArticleRetrievalCandidateSearchResult

    fun searchKeyword(query: String): ArticleRetrievalCandidateAttempt<List<ArticleSearchCandidate>>

    fun searchVector(query: String): ArticleRetrievalCandidateAttempt<ArticleVectorCandidateSearchResult>
}

data class ArticleRetrievalCandidateSearchResult(
    val query: String,
    val keyword: ArticleRetrievalCandidateAttempt<List<ArticleSearchCandidate>>,
    val vector: ArticleRetrievalCandidateAttempt<ArticleVectorCandidateSearchResult>
)

data class ArticleRetrievalCandidateAttempt<T>(
    val value: T?,
    val exception: Exception?,
    val failureReason: String?,
    val elapsedMs: Long,
    val embeddingElapsedMs: Long,
    val vectorElapsedMs: Long
) {
    val failed: Boolean
        get() = failureReason != null
}
```

- [ ] **Step 4: Refactor public search service to use shared candidate service**

Modify `ArticlePublicSearchService` constructor:

```kotlin
class ArticlePublicSearchService(
    private val articleRetrievalCandidateService: ArticleRetrievalCandidateService,
    private val articleKeywordSearchService: ArticleKeywordSearchService,
    private val reciprocalRankFusion: ReciprocalRankFusion,
    private val properties: SearchInfrastructureProperties
)
```

Use the shared result for hybrid mode:

```kotlin
val candidateResult = articleRetrievalCandidateService.search(normalizedQuery)
val keywordResult = candidateResult.keyword
val vectorResult = candidateResult.vector
val keywordCandidates = keywordResult.value.orEmpty()
val vectorCandidates = vectorResult.value?.candidates.orEmpty()
val fusionResult = fuseCandidates(keywordCandidates, vectorCandidates)
val mode = resolveMode(keywordResult, vectorResult)
```

Update helpers to use `ArticleRetrievalCandidateAttempt`:

```kotlin
private fun resolveMode(
    keywordResult: ArticleRetrievalCandidateAttempt<List<ArticleSearchCandidate>>,
    vectorResult: ArticleRetrievalCandidateAttempt<ArticleVectorCandidateSearchResult>
): ArticlePublicSearchMode =
    when {
        !keywordResult.failed && !vectorResult.failed -> ArticlePublicSearchMode.HYBRID
        !keywordResult.failed -> ArticlePublicSearchMode.KEYWORD_ONLY
        !vectorResult.failed -> ArticlePublicSearchMode.VECTOR_ONLY
        else -> ArticlePublicSearchMode.POSTGRES_FALLBACK
    }

private fun fallbackReason(
    keywordResult: ArticleRetrievalCandidateAttempt<List<ArticleSearchCandidate>>,
    vectorResult: ArticleRetrievalCandidateAttempt<ArticleVectorCandidateSearchResult>
): String? =
    listOfNotNull(keywordResult.failureReason, vectorResult.failureReason)
        .takeIf { reasons -> reasons.isNotEmpty() }
        ?.joinToString("; ")
```

Keep `searchKeywordOnly()` unchanged except for deleting now-unused private hybrid helper methods.

- [ ] **Step 5: Update public search service tests to inject candidate service**

In `ArticlePublicSearchServiceTest`, create the candidate service in the test setup:

```kotlin
private val candidateService = ArticleRetrievalCandidateService(
    articleKeywordSearchService = keywordSearchService,
    articleVectorCandidateSearcher = vectorCandidateSearcher,
    properties = properties
)
private val service = ArticlePublicSearchService(
    articleRetrievalCandidateService = candidateService,
    articleKeywordSearchService = keywordSearchService,
    reciprocalRankFusion = fusion,
    properties = properties
)
```

For the limited service block, create a limited candidate service with the same limited properties:

```kotlin
val limitedCandidateService = ArticleRetrievalCandidateService(
    articleKeywordSearchService = keywordSearchService,
    articleVectorCandidateSearcher = vectorCandidateSearcher,
    properties = limitedProperties
)
val limitedService = ArticlePublicSearchService(
    articleRetrievalCandidateService = limitedCandidateService,
    articleKeywordSearchService = keywordSearchService,
    reciprocalRankFusion = fusion,
    properties = limitedProperties
)
```

- [ ] **Step 6: Run focused hybrid tests**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.search.hybrid.ArticleRetrievalCandidateServiceTest --tests com.sigak.search.hybrid.ArticlePublicSearchServiceTest
```

Expected:

```text
BUILD SUCCESSFUL
```

## Task 3: Add Strict Internal Retrieval Evaluation Endpoint

**Files:**
- Create: `backend/src/main/kotlin/com/sigak/search/evaluation/retrieval/ArticleRetrievalEvaluationProperties.kt`
- Create: `backend/src/main/kotlin/com/sigak/search/evaluation/retrieval/ArticleRetrievalEvaluationConfig.kt`
- Create: `backend/src/main/kotlin/com/sigak/search/evaluation/retrieval/ArticleRetrievalEvaluationDtos.kt`
- Create: `backend/src/main/kotlin/com/sigak/search/evaluation/retrieval/ArticleRetrievalEvaluationService.kt`
- Create: `backend/src/main/kotlin/com/sigak/search/evaluation/retrieval/ArticleRetrievalEvaluationController.kt`
- Create: `backend/src/test/kotlin/com/sigak/search/evaluation/retrieval/ArticleRetrievalEvaluationServiceTest.kt`
- Create: `backend/src/test/kotlin/com/sigak/search/evaluation/retrieval/ArticleRetrievalEvaluationControllerTest.kt`
- Modify: `backend/src/main/resources/application.yml`

- [ ] **Step 1: Write service tests for strict system behavior**

Create `ArticleRetrievalEvaluationServiceTest` with tests named:

```kotlin
@Test
fun createRunsReturnsKeywordVectorAndHybridRunsWhenDependenciesSucceed()

@Test
fun createRunsFailsHybridInsteadOfDegradingWhenVectorFails()

@Test
fun createRunsOmitsStaleCandidateIdsAfterApiReadyReload()

@Test
fun createRunsRejectsDisabledEndpoint()
```

Use fakes:

```kotlin
private class RecordingCandidateService : ArticleRetrievalCandidateProvider {
    var keyword = ArticleRetrievalCandidateAttempt(
            value = listOf(ArticleSearchCandidate(articleId = 4L, rank = 1)),
            exception = null,
            failureReason = null,
            elapsedMs = 2,
            embeddingElapsedMs = 0,
            vectorElapsedMs = 0
    )
    var vector = ArticleRetrievalCandidateAttempt(
        value = vectorResult(ArticleSearchCandidate(articleId = 1L, rank = 1, score = 0.9)),
        exception = null,
        failureReason = null,
        elapsedMs = 5,
        embeddingElapsedMs = 3,
        vectorElapsedMs = 2
    )
    var keywordCallCount = 0
    var vectorCallCount = 0

    override fun search(query: String): ArticleRetrievalCandidateSearchResult =
        ArticleRetrievalCandidateSearchResult(
            query = query.trim(),
            keyword = searchKeyword(query),
            vector = searchVector(query)
        )

    override fun searchKeyword(query: String): ArticleRetrievalCandidateAttempt<List<ArticleSearchCandidate>> {
        keywordCallCount += 1
        return keyword
    }

    override fun searchVector(query: String): ArticleRetrievalCandidateAttempt<ArticleVectorCandidateSearchResult> {
        vectorCallCount += 1
        return vector
    }
}
```

To make this fake possible, the interface has already been introduced in `ArticleRetrievalCandidateService.kt`:

```kotlin
interface ArticleRetrievalCandidateProvider {
    fun search(query: String): ArticleRetrievalCandidateSearchResult

    fun searchKeyword(query: String): ArticleRetrievalCandidateAttempt<List<ArticleSearchCandidate>>

    fun searchVector(query: String): ArticleRetrievalCandidateAttempt<ArticleVectorCandidateSearchResult>
}
```

Use an API-ready reloader fake and define `vectorResult()` in a companion object:

```kotlin
private class RecordingApiReadyArticleReloader(
    private val visibleIds: Set<Long>
) : ApiReadyArticleReloader {
    override fun reloadApiReadyArticleIds(articleIds: List<Long>): List<Long> =
        articleIds.distinct().filter { articleId -> visibleIds.contains(articleId) }
}

private companion object {
    fun vectorResult(vararg candidates: ArticleSearchCandidate): ArticleVectorCandidateSearchResult =
        ArticleVectorCandidateSearchResult(
            query = "graph",
            candidates = candidates.toList(),
            embeddingProvider = "local",
            embeddingModelName = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
            embeddingDimension = 384,
            embeddingElapsedMs = 3,
            vectorElapsedMs = 2,
            totalElapsedMs = 5
        )
}
```

Expected assertions:

```kotlin
assertEquals(ArticleRetrievalRunStatus.COMPLETED, hybrid.status)
assertEquals(listOf(4L, 1L), hybrid.rankedArticleIds)
assertEquals(false, hybrid.degraded)
assertEquals("HYBRID", hybrid.resolvedMode)
assertEquals(0, hybrid.staleCandidateCount)
```

For vector failure:

```kotlin
assertEquals(ArticleRetrievalRunStatus.FAILED, hybrid.status)
assertEquals("HYBRID_DEPENDENCY_FAILED", hybrid.failureReason)
assertEquals(emptyList(), hybrid.rankedArticleIds)
```

For stale IDs:

```kotlin
assertEquals(listOf(4L, 1L), keyword.rankedArticleIds)
assertEquals(1, keyword.staleCandidateCount)
```

For system-specific calls:

```kotlin
service.createRuns(ArticleRetrievalRunRequest(queries = listOf("graph"), systems = listOf(ArticleRetrievalEvaluationSystem.KEYWORD)))
assertEquals(1, candidateService.keywordCallCount)
assertEquals(0, candidateService.vectorCallCount)
```

- [ ] **Step 2: Create DTOs, properties, and reloader boundary**

Create `ArticleRetrievalEvaluationProperties.kt`:

```kotlin
package com.sigak.search.evaluation.retrieval

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "sigak.internal.search-evaluation")
data class ArticleRetrievalEvaluationProperties(
    val enabled: Boolean = true
)
```

Create `ArticleRetrievalEvaluationConfig.kt`:

```kotlin
package com.sigak.search.evaluation.retrieval

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(ArticleRetrievalEvaluationProperties::class)
class ArticleRetrievalEvaluationConfig
```

Create `ArticleRetrievalEvaluationDtos.kt`:

```kotlin
package com.sigak.search.evaluation.retrieval

import java.time.Instant

enum class ArticleRetrievalEvaluationSystem {
    KEYWORD,
    VECTOR,
    HYBRID
}

enum class ArticleRetrievalRunStatus {
    COMPLETED,
    FAILED
}

data class ArticleRetrievalRunRequest(
    val queries: List<String> = emptyList(),
    val systems: List<ArticleRetrievalEvaluationSystem>? = null,
    val limit: Int? = null
)

data class ArticleRetrievalRunResponse(
    val generatedAt: Instant,
    val limit: Int,
    val runs: List<ArticleRetrievalRunItemResponse>
)

data class ArticleRetrievalRunItemResponse(
    val query: String,
    val system: ArticleRetrievalEvaluationSystem,
    val status: ArticleRetrievalRunStatus,
    val rankedArticleIds: List<Long>,
    val candidateCount: Int,
    val staleCandidateCount: Int,
    val failureReason: String?,
    val degraded: Boolean,
    val resolvedMode: String?,
    val timings: ArticleRetrievalRunTimingsResponse,
    val metadata: ArticleRetrievalRunMetadataResponse?
)

data class ArticleRetrievalRunTimingsResponse(
    val keywordElapsedMs: Long,
    val embeddingElapsedMs: Long,
    val vectorElapsedMs: Long,
    val fusionElapsedMs: Long,
    val articleReloadElapsedMs: Long,
    val totalElapsedMs: Long
)

data class ArticleRetrievalRunMetadataResponse(
    val embeddingProvider: String?,
    val embeddingModelName: String?,
    val embeddingDimension: Int?
)
```

Create `ApiReadyArticleReloader` in the service file:

```kotlin
interface ApiReadyArticleReloader {
    fun reloadApiReadyArticleIds(articleIds: List<Long>): List<Long>
}
```

Create adapter:

```kotlin
@Component
class ArticleServiceApiReadyArticleReloader(
    private val articleService: ArticleService
) : ApiReadyArticleReloader {
    override fun reloadApiReadyArticleIds(articleIds: List<Long>): List<Long> =
        articleService.getApiReadyArticlesByIds(articleIds).map { article -> article.id }
}
```

- [ ] **Step 3: Implement evaluation service**

Create `ArticleRetrievalEvaluationService.kt` with:

```kotlin
package com.sigak.search.evaluation.retrieval

import com.sigak.article.service.ArticleService
import com.sigak.common.time.elapsedMillis
import com.sigak.common.time.measureElapsed
import com.sigak.search.config.SearchInfrastructureProperties
import com.sigak.search.hybrid.ArticleRetrievalCandidateProvider
import com.sigak.search.hybrid.ArticleSearchCandidate
import com.sigak.search.hybrid.ArticleVectorCandidateSearchResult
import com.sigak.search.hybrid.ReciprocalRankFusion
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service

@Service
class ArticleRetrievalEvaluationService(
    private val candidateProvider: ArticleRetrievalCandidateProvider,
    private val reciprocalRankFusion: ReciprocalRankFusion,
    private val articleReloader: ApiReadyArticleReloader,
    private val searchProperties: SearchInfrastructureProperties,
    private val evaluationProperties: ArticleRetrievalEvaluationProperties
) {

    fun createRuns(request: ArticleRetrievalRunRequest): ArticleRetrievalRunResponse {
        if (!evaluationProperties.enabled) {
            throw IllegalStateException("Internal search evaluation endpoint is disabled.")
        }

        val queries = normalizedQueries(request.queries)
        val systems = request.systems ?: ArticleRetrievalEvaluationSystem.values().toList()
        val limit = request.limit ?: searchProperties.hybrid.resultLimit
        require(limit in 1..searchProperties.hybrid.resultLimit) {
            "limit must be between 1 and ${searchProperties.hybrid.resultLimit}"
        }

        val runs = queries.flatMap { query ->
            val candidates = QueryCandidateAttempts(
                query = query,
                candidateProvider = candidateProvider
            )
            systems.map { system ->
                runSystem(query = query, system = system, candidates = candidates, limit = limit)
            }
        }

        return ArticleRetrievalRunResponse(
            generatedAt = java.time.Instant.now(),
            limit = limit,
            runs = runs
        )
    }

    private fun normalizedQueries(queries: List<String>): List<String> {
        require(queries.isNotEmpty()) { "queries must contain at least one query" }
        require(queries.size <= 50) { "queries must contain at most 50 queries" }

        return queries.map { query ->
            query.trim().also { normalizedQuery ->
                require(normalizedQuery.isNotBlank()) { "queries must not contain blank values" }
            }
        }
    }
}
```

Then add a lazy query-level candidate holder in the same file:

```kotlin
private class QueryCandidateAttempts(
    private val query: String,
    private val candidateProvider: ArticleRetrievalCandidateProvider
) {
    val keyword by lazy { candidateProvider.searchKeyword(query) }

    val vector by lazy { candidateProvider.searchVector(query) }
}
```

Then add private helpers in the same class:

```kotlin
private fun runSystem(
    query: String,
    system: ArticleRetrievalEvaluationSystem,
    candidates: QueryCandidateAttempts,
    limit: Int
): ArticleRetrievalRunItemResponse {
    val totalStartedAt = System.nanoTime()

    return when (system) {
        ArticleRetrievalEvaluationSystem.KEYWORD -> runKeyword(query, candidates, limit, totalStartedAt)
        ArticleRetrievalEvaluationSystem.VECTOR -> runVector(query, candidates, limit, totalStartedAt)
        ArticleRetrievalEvaluationSystem.HYBRID -> runHybrid(query, candidates, limit, totalStartedAt)
    }
}
```

Use these system helpers:

```kotlin
private fun runKeyword(
    query: String,
    candidates: QueryCandidateAttempts,
    limit: Int,
    totalStartedAt: Long
): ArticleRetrievalRunItemResponse {
    val keyword = candidates.keyword
    if (keyword.failed) {
        return failedRun(query, ArticleRetrievalEvaluationSystem.KEYWORD, keyword.failureReason, totalStartedAt)
    }

    return completedRun(
        query = query,
        system = ArticleRetrievalEvaluationSystem.KEYWORD,
        candidates = keyword.value.orEmpty().take(limit),
        metadata = null,
        timings = ArticleRetrievalRunTimingsResponse(
            keywordElapsedMs = keyword.elapsedMs,
            embeddingElapsedMs = 0,
            vectorElapsedMs = 0,
            fusionElapsedMs = 0,
            articleReloadElapsedMs = 0,
            totalElapsedMs = 0
        ),
        totalStartedAt = totalStartedAt,
        resolvedMode = "KEYWORD",
        limit = limit
    )
}

private fun runVector(
    query: String,
    candidates: QueryCandidateAttempts,
    limit: Int,
    totalStartedAt: Long
): ArticleRetrievalRunItemResponse {
    val vector = candidates.vector
    if (vector.failed) {
        return failedRun(query, ArticleRetrievalEvaluationSystem.VECTOR, vector.failureReason, totalStartedAt)
    }

    val vectorResult = requireNotNull(vector.value)
    return completedRun(
        query = query,
        system = ArticleRetrievalEvaluationSystem.VECTOR,
        candidates = vectorResult.candidates.take(limit),
        metadata = vectorResult.toMetadata(),
        timings = ArticleRetrievalRunTimingsResponse(
            keywordElapsedMs = 0,
            embeddingElapsedMs = vectorResult.embeddingElapsedMs,
            vectorElapsedMs = vectorResult.vectorElapsedMs,
            fusionElapsedMs = 0,
            articleReloadElapsedMs = 0,
            totalElapsedMs = 0
        ),
        totalStartedAt = totalStartedAt,
        resolvedMode = "VECTOR",
        limit = limit
    )
}

private fun runHybrid(
    query: String,
    candidates: QueryCandidateAttempts,
    limit: Int,
    totalStartedAt: Long
): ArticleRetrievalRunItemResponse {
    val keyword = candidates.keyword
    val vector = candidates.vector
    if (keyword.failed || vector.failed) {
        return failedRun(query, ArticleRetrievalEvaluationSystem.HYBRID, "HYBRID_DEPENDENCY_FAILED", totalStartedAt)
    }

    val vectorResult = requireNotNull(vector.value)
    val fusion = measureElapsed {
        reciprocalRankFusion.fuse(
            keywordCandidates = keyword.value.orEmpty(),
            vectorCandidates = vectorResult.candidates,
            rrfK = searchProperties.hybrid.rrfK,
            keywordWeight = searchProperties.hybrid.keywordWeight,
            vectorWeight = searchProperties.hybrid.vectorWeight,
            limit = limit
        )
    }

    return completedRun(
        query = query,
        system = ArticleRetrievalEvaluationSystem.HYBRID,
        candidates = fusion.value,
        metadata = vectorResult.toMetadata(),
        timings = ArticleRetrievalRunTimingsResponse(
            keywordElapsedMs = keyword.elapsedMs,
            embeddingElapsedMs = vectorResult.embeddingElapsedMs,
            vectorElapsedMs = vectorResult.vectorElapsedMs,
            fusionElapsedMs = fusion.elapsedMs,
            articleReloadElapsedMs = 0,
            totalElapsedMs = 0
        ),
        totalStartedAt = totalStartedAt,
        resolvedMode = "HYBRID",
        limit = limit
    )
}
```

Use API-ready reload:

```kotlin
private fun completedRun(
    query: String,
    system: ArticleRetrievalEvaluationSystem,
    candidates: List<ArticleSearchCandidate>,
    metadata: ArticleRetrievalRunMetadataResponse?,
    timings: ArticleRetrievalRunTimingsResponse,
    totalStartedAt: Long,
    resolvedMode: String,
    limit: Int
): ArticleRetrievalRunItemResponse {
    val candidateIds = candidates.map { candidate -> candidate.articleId }.take(limit)
    val reload = measureElapsed { articleReloader.reloadApiReadyArticleIds(candidateIds) }
    val staleCandidateCount = candidateIds.distinct().size - reload.value.size

    return ArticleRetrievalRunItemResponse(
        query = query,
        system = system,
        status = ArticleRetrievalRunStatus.COMPLETED,
        rankedArticleIds = reload.value,
        candidateCount = candidateIds.distinct().size,
        staleCandidateCount = staleCandidateCount,
        failureReason = null,
        degraded = false,
        resolvedMode = resolvedMode,
        timings = timings.copy(
            articleReloadElapsedMs = reload.elapsedMs,
            totalElapsedMs = elapsedMillis(totalStartedAt)
        ),
        metadata = metadata
    )
}

private fun failedRun(
    query: String,
    system: ArticleRetrievalEvaluationSystem,
    failureReason: String?,
    totalStartedAt: Long
): ArticleRetrievalRunItemResponse =
    ArticleRetrievalRunItemResponse(
        query = query,
        system = system,
        status = ArticleRetrievalRunStatus.FAILED,
        rankedArticleIds = emptyList(),
        candidateCount = 0,
        staleCandidateCount = 0,
        failureReason = failureReason,
        degraded = false,
        resolvedMode = null,
        timings = ArticleRetrievalRunTimingsResponse(
            keywordElapsedMs = 0,
            embeddingElapsedMs = 0,
            vectorElapsedMs = 0,
            fusionElapsedMs = 0,
            articleReloadElapsedMs = 0,
            totalElapsedMs = elapsedMillis(totalStartedAt)
        ),
        metadata = null
    )

private fun ArticleVectorCandidateSearchResult.toMetadata(): ArticleRetrievalRunMetadataResponse =
    ArticleRetrievalRunMetadataResponse(
        embeddingProvider = embeddingProvider,
        embeddingModelName = embeddingModelName,
        embeddingDimension = embeddingDimension
    )
```

- [ ] **Step 4: Implement controller with local guard semantics**

Create `ArticleRetrievalEvaluationController.kt`:

```kotlin
package com.sigak.search.evaluation.retrieval

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
@RequestMapping("/api/internal/search-evaluation/retrieval-runs")
@Tag(name = "Internal Search Evaluation", description = "Internal retrieval benchmark APIs for local experiment runs.")
class ArticleRetrievalEvaluationController(
    private val service: ArticleRetrievalEvaluationService
) {

    @PostMapping
    @Operation(
        summary = "Create retrieval benchmark runs",
        description = "Creates strict keyword, vector, and hybrid retrieval runs for local evaluation artifacts."
    )
    fun createRuns(@RequestBody request: ArticleRetrievalRunRequest): ArticleRetrievalRunResponse =
        service.createRuns(request)

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleIllegalArgumentException(exception: IllegalArgumentException): Map<String, String> =
        mapOf("message" to exception.message.orEmpty())

    @ExceptionHandler(IllegalStateException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleIllegalStateException(exception: IllegalStateException): Map<String, String> =
        mapOf("message" to exception.message.orEmpty())
}
```

Add disabled-by-default local guard to `application.yml`:

```yaml
sigak:
  internal:
    search-evaluation:
      enabled: false
```

If `sigak:` already exists, merge these nested keys without duplicating the root.

- [ ] **Step 5: Write controller tests**

Create `ArticleRetrievalEvaluationControllerTest` with:

```kotlin
@WebMvcTest(ArticleRetrievalEvaluationController::class)
class ArticleRetrievalEvaluationControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var service: ArticleRetrievalEvaluationService

    @Test
    fun createRunsReturnsStrictEvaluationRuns()

    @Test
    fun createRunsRejectsBlankQueries()

    @Test
    fun createRunsRejectsPublicSystem()

    @Test
    fun createRunsReturnsNotFoundWhenEvaluationIsDisabled()

    @Test
    fun createRunsDoesNotExposeSensitiveMetadata()
}
```

Use this request for the success case:

```json
{"queries":["graph rag failure"],"systems":["KEYWORD","VECTOR","HYBRID"],"limit":20}
```

Assert:

```kotlin
.andExpect(status().isOk)
.andExpect(jsonPath("$.runs[0].system").value("KEYWORD"))
.andExpect(jsonPath("$.runs[0].status").value("COMPLETED"))
.andExpect(jsonPath("$.runs[0].rankedArticleIds[0]").value(4))
.andExpect(jsonPath("$.runs[0].degraded").value(false))
```

For `PUBLIC`, expect Jackson enum binding to reject the request with a client error:

```kotlin
mockMvc.perform(
    post("/api/internal/search-evaluation/retrieval-runs")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"queries":["graph"],"systems":["PUBLIC"],"limit":20}""")
)
    .andExpect(status().is4xxClientError)
```

For sensitive metadata, mock a response with normal embedding metadata and assert that no secret-like fields exist:

```kotlin
mockMvc.perform(
    post("/api/internal/search-evaluation/retrieval-runs")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"queries":["graph"],"systems":["VECTOR"],"limit":20}""")
)
    .andExpect(status().isOk)
    .andExpect(jsonPath("$.runs[0].metadata.embeddingProvider").value("local"))
    .andExpect(jsonPath("$.runs[0].metadata.embeddingModelName").value("sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"))
    .andExpect(jsonPath("$.runs[0].metadata.embeddingDimension").value(384))
    .andExpect(jsonPath("$.runs[0].metadata.apiKey").doesNotExist())
    .andExpect(jsonPath("$.runs[0].metadata.headers").doesNotExist())
    .andExpect(jsonPath("$.runs[0].metadata.environment").doesNotExist())
    .andExpect(jsonPath("$.runs[0].metadata.embedding").doesNotExist())
    .andExpect(jsonPath("$.runs[0].metadata.serviceUrl").doesNotExist())
```

- [ ] **Step 6: Run backend focused tests**

Run:

```bash
cd backend
./gradlew test \
  --tests com.sigak.search.hybrid.ArticleRetrievalCandidateServiceTest \
  --tests com.sigak.search.hybrid.ArticlePublicSearchServiceTest \
  --tests com.sigak.search.evaluation.retrieval.ArticleRetrievalEvaluationServiceTest \
  --tests com.sigak.search.evaluation.retrieval.ArticleRetrievalEvaluationControllerTest
```

Expected:

```text
BUILD SUCCESSFUL
```

## Task 4: Extend Runner CLI And Clients For System Comparison

**Files:**
- Modify: `experiments/scripts/retrieval-benchmark/cli.mjs`
- Modify: `experiments/scripts/retrieval-benchmark/cli.test.mjs`
- Modify: `experiments/scripts/retrieval-benchmark/search-client.mjs`
- Modify: `experiments/scripts/retrieval-benchmark/search-client.test.mjs`

- [ ] **Step 1: Extend CLI tests**

Add tests:

```js
test('parseBenchmarkArgs reads systems and limit for comparison mode', () => {
  const args = parseBenchmarkArgs([
    '--labels=labels.json',
    '--base-url=http://localhost:8080',
    '--output-dir=out',
    '--systems=keyword,vector,hybrid,public',
    '--k=5',
    '--limit=20',
  ]);

  assert.deepEqual(args.systems, ['keyword', 'vector', 'hybrid', 'public']);
  assert.equal(args.limit, 20);
});

test('parseBenchmarkArgs keeps systems undefined for existing smoke mode', () => {
  const args = parseBenchmarkArgs([
    '--labels=labels.json',
    '--base-url=http://localhost:8080',
    '--output-dir=out',
  ]);

  assert.equal(args.systems, undefined);
  assert.equal(args.limit, 20);
});

test('parseBenchmarkArgs rejects unknown systems', () => {
  assert.throws(
    () => parseBenchmarkArgs([
      '--labels=labels.json',
      '--base-url=http://localhost:8080',
      '--output-dir=out',
      '--systems=hybrid,graph',
    ]),
    /Option --systems contains unknown value: graph/
  );
});

test('parseBenchmarkArgs rejects limit smaller than k', () => {
  assert.throws(
    () => parseBenchmarkArgs([
      '--labels=labels.json',
      '--base-url=http://localhost:8080',
      '--output-dir=out',
      '--k=5',
      '--limit=3',
    ]),
    /Option --limit must be greater than or equal to --k/
  );
});
```

- [ ] **Step 2: Update CLI parser**

Update known options:

```js
const REQUIRED_OPTIONS = ['labels', 'base-url', 'output-dir'];
const KNOWN_OPTIONS = new Set([...REQUIRED_OPTIONS, 'k', 'systems', 'limit']);
const KNOWN_SYSTEMS = new Set(['keyword', 'vector', 'hybrid', 'public']);
```

Return:

```js
const k = options.has('k') ? parsePositiveInteger(options.get('k'), 'k') : 5;
const limit = options.has('limit') ? parsePositiveInteger(options.get('limit'), 'limit') : 20;

if (limit < k) {
  throw new Error('Option --limit must be greater than or equal to --k.');
}

return {
  labelsPath: options.get('labels'),
  baseUrl: options.get('base-url'),
  outputDir: options.get('output-dir'),
  k,
  limit,
  systems: options.has('systems') ? parseSystems(options.get('systems')) : undefined,
};
```

Add:

```js
function parseSystems(value) {
  const systems = value.split(',').map((system) => system.trim().toLowerCase()).filter(Boolean);

  if (systems.length === 0) {
    throw new Error('Option --systems must include at least one system.');
  }

  for (const system of systems) {
    if (!KNOWN_SYSTEMS.has(system)) {
      throw new Error(`Option --systems contains unknown value: ${system}`);
    }
  }

  return [...new Set(systems)];
}
```

- [ ] **Step 3: Add internal and public client tests**

Add `createRetrievalEvaluationClient` tests:

```js
test('retrieval evaluation client posts strict systems and maps run items', async () => {
  const requests = [];
  const client = createRetrievalEvaluationClient({
    baseUrl: 'http://localhost:8080',
    fetchImpl: async (url, options) => {
      requests.push({ url: String(url), body: JSON.parse(options.body) });
      return jsonResponse({
        generatedAt: '2026-06-02T00:00:00Z',
        limit: 20,
        runs: [{
          query: 'graph',
          system: 'HYBRID',
          status: 'COMPLETED',
          rankedArticleIds: [4, 1],
          candidateCount: 2,
          staleCandidateCount: 0,
          failureReason: null,
          degraded: false,
          resolvedMode: 'HYBRID',
          timings: { totalElapsedMs: 12 },
          metadata: { embeddingProvider: 'deterministic', embeddingModelName: 'deterministic', embeddingDimension: 8 },
        }],
      });
    },
  });

  const result = await client.createRuns({ queries: ['graph'], systems: ['hybrid'], limit: 20 });

  assert.equal(requests[0].url, 'http://localhost:8080/api/internal/search-evaluation/retrieval-runs');
  assert.deepEqual(requests[0].body.systems, ['HYBRID']);
  assert.equal(result.runs[0].system, 'hybrid');
  assert.equal(result.runs[0].latencyMs, 12);
  assert.equal(result.runs[0].latencySource, 'backend_total');
});
```

Add public metrics lookup behavior:

```js
test('public search client records resolved mode when metrics endpoint is available', async () => {
  const client = createArticleSearchClient({
    baseUrl: 'http://localhost:8080',
    now: (() => {
      const values = [100, 125];
      return () => values.shift();
    })(),
    fetchImpl: async (url) => {
      if (String(url).includes('/api/internal/search-metrics/articles')) {
        return jsonResponse({ lastSearch: { mode: 'VECTOR_ONLY', fallbackReason: 'KEYWORD_SEARCH_FAILED' } });
      }
      return jsonResponse([{ id: 1 }, { id: 2 }]);
    },
  });

  const result = await client.searchArticles('graph', { includeMetrics: true });

  assert.deepEqual(result.rankedArticleIds, [1, 2]);
  assert.equal(result.latencyMs, 25);
  assert.equal(result.resolvedMode, 'VECTOR_ONLY');
  assert.equal(result.degraded, true);
  assert.equal(result.failureReason, 'KEYWORD_SEARCH_FAILED');
});

function jsonResponse(body, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  };
}
```

- [ ] **Step 4: Implement client functions**

In `search-client.mjs`, keep `createArticleSearchClient` and allow options:

```js
async searchArticles(query, { includeMetrics = false } = {}) {
  const searchResult = await searchPublicArticles(query);
  if (!includeMetrics) {
    return searchResult;
  }

  const metrics = await readArticleSearchMetrics().catch(() => undefined);
  return {
    ...searchResult,
    resolvedMode: metrics?.lastSearch?.mode,
    degraded: metrics?.lastSearch?.mode && metrics.lastSearch.mode !== 'HYBRID',
    failureReason: metrics?.lastSearch?.fallbackReason,
  };
}
```

Add:

```js
export function createRetrievalEvaluationClient({ baseUrl, fetchImpl = fetch }) {
  const normalizedBaseUrl = baseUrl.replace(/\/+$/, '');

  return {
    async createRuns({ queries, systems, limit }) {
      const response = await fetchImpl(`${normalizedBaseUrl}/api/internal/search-evaluation/retrieval-runs`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          queries,
          systems: systems.map((system) => system.toUpperCase()),
          limit,
        }),
      });

      if (!response.ok) {
        throw new Error(`Retrieval evaluation API failed with status ${response.status}.`);
      }

      const body = await response.json();
      return {
        ...body,
        runs: body.runs.map((run) => ({
          ...run,
          system: run.system.toLowerCase(),
          latencyMs: run.timings?.totalElapsedMs ?? null,
          latencySource: 'backend_total',
        })),
      };
    },
  };
}
```

- [ ] **Step 5: Run runner client tests**

Run:

```bash
node --test experiments/scripts/retrieval-benchmark/cli.test.mjs experiments/scripts/retrieval-benchmark/search-client.test.mjs
```

Expected:

```text
# pass
```

## Task 5: Add System-Aware Metrics And Reports

**Files:**
- Modify: `experiments/scripts/retrieval-benchmark/metrics.mjs`
- Modify: `experiments/scripts/retrieval-benchmark/metrics.test.mjs`
- Modify: `experiments/scripts/retrieval-benchmark/report-writer.mjs`
- Modify: `experiments/scripts/retrieval-benchmark/report-writer.test.mjs`

- [ ] **Step 1: Add metrics tests for failures and effective metrics**

Add:

```js
test('calculateSystemSummaryMetrics separates macro and effective metrics', () => {
  const rows = [
    { system: 'hybrid', status: 'COMPLETED', top1StrongHit: 1, recallAtK: 1, mrrAtK: 1, latencyMs: 20, staleCandidateCount: 0, degraded: false, resolvedMode: 'HYBRID' },
    { system: 'hybrid', status: 'FAILED', top1StrongHit: null, recallAtK: null, mrrAtK: null, latencyMs: null, staleCandidateCount: 0, degraded: false, resolvedMode: null, failureReason: 'QDRANT_SEARCH_FAILED' },
  ];

  const summary = calculateSystemSummaryMetrics({ system: 'hybrid', rows });

  assert.equal(summary.attemptedQueryCount, 2);
  assert.equal(summary.completedQueryCount, 1);
  assert.equal(summary.failedQueryCount, 1);
  assert.equal(summary.failureRate, 0.5);
  assert.equal(summary.macroRecallAtK, 1);
  assert.equal(summary.effectiveRecallAtK, 0.5);
  assert.deepEqual(summary.failureReasonCounts, { QDRANT_SEARCH_FAILED: 1 });
});

test('calculateSystemSummaryMetrics records degraded public runs separately', () => {
  const summary = calculateSystemSummaryMetrics({
    system: 'public',
    rows: [
      { system: 'public', status: 'COMPLETED', top1StrongHit: 0, recallAtK: 0.5, mrrAtK: 0, latencyMs: 25, staleCandidateCount: 0, degraded: true, resolvedMode: 'VECTOR_ONLY' },
    ],
  });

  assert.equal(summary.degradedQueryCount, 1);
  assert.equal(summary.degradedRate, 1);
  assert.deepEqual(summary.resolvedModeCounts, { VECTOR_ONLY: 1 });
});
```

- [ ] **Step 2: Implement system-aware metric functions**

Keep existing `calculateQueryMetrics()` for completed rows and add:

```js
export function calculateRunRowMetrics({ labeledQuery, runItem, k }) {
  if (runItem.status === 'FAILED') {
    return {
      query: labeledQuery.query,
      intent: labeledQuery.intent,
      system: runItem.system,
      status: 'FAILED',
      strongArticleIds: labeledQuery.strongArticleIds,
      acceptableArticleIds: labeledQuery.acceptableArticleIds,
      resultArticleIds: [],
      top1StrongHit: null,
      recallAtK: null,
      mrrAtK: null,
      latencyMs: null,
      failureReason: runItem.failureReason,
      degraded: false,
      resolvedMode: runItem.resolvedMode ?? null,
      staleCandidateCount: runItem.staleCandidateCount ?? 0,
    };
  }

  return {
    ...calculateQueryMetrics({
      labeledQuery,
      rankedArticleIds: runItem.rankedArticleIds,
      latencyMs: runItem.latencyMs,
      k,
    }),
    system: runItem.system,
    status: 'COMPLETED',
    failureReason: runItem.failureReason ?? null,
    degraded: Boolean(runItem.degraded),
    resolvedMode: runItem.resolvedMode ?? null,
    staleCandidateCount: runItem.staleCandidateCount ?? 0,
  };
}
```

Add:

```js
export function calculateSystemSummaryMetrics({ system, rows }) {
  const completedRows = rows.filter((row) => row.status === 'COMPLETED');
  const failedRows = rows.filter((row) => row.status === 'FAILED');

  return {
    system,
    attemptedQueryCount: rows.length,
    completedQueryCount: completedRows.length,
    failedQueryCount: failedRows.length,
    failureRate: ratio(failedRows.length, rows.length),
    degradedQueryCount: rows.filter((row) => row.degraded).length,
    degradedRate: ratio(rows.filter((row) => row.degraded).length, rows.length),
    failureReasonCounts: countBy(failedRows.map((row) => row.failureReason).filter(Boolean)),
    resolvedModeCounts: countBy(rows.map((row) => row.resolvedMode).filter(Boolean)),
    macroTop1StrongHit: nullableAverage(completedRows.map((row) => row.top1StrongHit)),
    macroRecallAtK: nullableAverage(completedRows.map((row) => row.recallAtK)),
    macroMrrAtK: nullableAverage(completedRows.map((row) => row.mrrAtK)),
    effectiveTop1StrongHit: average(rows.map((row) => row.status === 'COMPLETED' ? row.top1StrongHit : 0)),
    effectiveRecallAtK: average(rows.map((row) => row.status === 'COMPLETED' ? row.recallAtK : 0)),
    effectiveMrrAtK: average(rows.map((row) => row.status === 'COMPLETED' ? row.mrrAtK : 0)),
    averageLatencyMs: nullableAverage(completedRows.map((row) => row.latencyMs)),
    totalStaleCandidateCount: rows.reduce((sum, row) => sum + (row.staleCandidateCount ?? 0), 0),
  };
}
```

Add comparison helper:

```js
export function calculateComparisonMetrics({ catalogId, catalogArticleCount, k, generatedAt, systems, byQueryMetrics }) {
  const bySystem = systems.map((system) => calculateSystemSummaryMetrics({
    system,
    rows: byQueryMetrics.filter((row) => row.system === system),
  }));

  return {
    catalogId,
    catalogArticleCount,
    evaluatedQueryCount: new Set(byQueryMetrics.map((row) => row.query)).size,
    k,
    generatedAt,
    systems: bySystem,
    bestObservedSystemsByMetric: bestObservedSystems(bySystem),
    warnings: comparisonWarnings({ catalogArticleCount, evaluatedQueryCount: new Set(byQueryMetrics.map((row) => row.query)).size, bySystem }),
  };
}
```

Add metric helpers in the same module:

```js
function nullableAverage(values) {
  const numericValues = values.filter((value) => Number.isFinite(value));
  return numericValues.length === 0 ? null : average(numericValues);
}

function ratio(numerator, denominator) {
  return denominator === 0 ? 0 : numerator / denominator;
}

function countBy(values) {
  return values.reduce((counts, value) => {
    counts[value] = (counts[value] ?? 0) + 1;
    return counts;
  }, {});
}

function bestObservedSystems(bySystem) {
  return {
    macroTop1StrongHit: bestSystemBy(bySystem, 'macroTop1StrongHit'),
    macroRecallAtK: bestSystemBy(bySystem, 'macroRecallAtK'),
    macroMrrAtK: bestSystemBy(bySystem, 'macroMrrAtK'),
    effectiveRecallAtK: bestSystemBy(bySystem, 'effectiveRecallAtK'),
    averageLatencyMs: bestSystemBy(bySystem, 'averageLatencyMs', 'ascending'),
  };
}

function bestSystemBy(bySystem, metricName, direction = 'descending') {
  const rows = bySystem.filter((row) => Number.isFinite(row[metricName]));
  if (rows.length === 0) {
    return null;
  }

  return [...rows].sort((left, right) => {
    const comparison = left[metricName] - right[metricName];
    return direction === 'ascending' ? comparison : -comparison;
  })[0].system;
}

function comparisonWarnings({ catalogArticleCount, evaluatedQueryCount, bySystem }) {
  const warnings = [];

  if (evaluatedQueryCount < 10) {
    warnings.push('label set is smaller than 10 reviewed queries, so this is a smoke benchmark');
  }

  if (catalogArticleCount < 20) {
    warnings.push('catalog article count is below 20, so ranking difficulty is still low');
  }

  for (const system of bySystem) {
    if (system.failedQueryCount > 0) {
      warnings.push(`${system.system} has failed query runs`);
    }
    if (system.degradedQueryCount > 0) {
      warnings.push(`${system.system} has degraded query runs`);
    }
  }

  return warnings;
}
```

- [ ] **Step 3: Extend report writer tests**

Add assertions:

```js
assert.equal(artifacts.runPaths.keyword.endsWith('runs.keyword.json'), true);
assert.equal(artifacts.bySystemPath.endsWith('metrics.by-system.json'), true);
assert.equal(artifacts.comparisonPath.endsWith('metrics.comparison.json'), true);
assert.match(markdown, /best observed system in this smoke run/);
assert.match(markdown, /Failure Rate/);
assert.match(markdown, /Degraded Rate/);
assert.match(markdown, /Strict HYBRID/);
assert.match(markdown, /PUBLIC is user-visible/);
assert.match(markdown, /PUBLIC is not sent to the internal evaluation endpoint/);
assert.match(markdown, /label set is smaller than 10 reviewed queries/);
```

- [ ] **Step 4: Implement multi-system artifact writer**

Keep the existing writer contract for single-run smoke mode and add:

```js
export async function writeComparisonArtifacts({ outputDir, runsBySystem, byQueryMetrics, bySystemMetrics, comparison }) {
  await mkdir(outputDir, { recursive: true });

  const runPaths = {};
  for (const [system, run] of Object.entries(runsBySystem)) {
    const runPath = join(outputDir, `runs.${system}.json`);
    await writeJson(runPath, run);
    runPaths[system] = runPath;
  }

  const byQueryPath = join(outputDir, 'metrics.by-query.json');
  const bySystemPath = join(outputDir, 'metrics.by-system.json');
  const comparisonPath = join(outputDir, 'metrics.comparison.json');
  const reportPath = join(outputDir, 'report.md');

  await writeJson(byQueryPath, byQueryMetrics);
  await writeJson(bySystemPath, bySystemMetrics);
  await writeJson(comparisonPath, comparison);
  await writeFile(reportPath, buildComparisonMarkdownReport({ byQueryMetrics, bySystemMetrics, comparison }), 'utf8');

  return { runPaths, byQueryPath, bySystemPath, comparisonPath, reportPath };
}
```

In `buildComparisonMarkdownReport`, include these sections:

```markdown
# Retrieval Benchmark Smoke Comparison Report

## System Comparison

| System | Completed | Failed | Failure Rate | Degraded Rate | Top1 Strong Hit | Recall@K | MRR@K | Effective Recall@K | Avg LatencyMs |

## Query Results

| Query | System | Status | Resolved Mode | Top1 Strong Hit | Recall@K | MRR@K | LatencyMs | Result IDs | Failure Reason |

## Limitations

- This is a smoke benchmark when reviewed query count is below 10.
- The phrase "best observed system in this smoke run" is used because the current dataset is small.

## Strict HYBRID vs PUBLIC

- Strict HYBRID is an experiment system created by the internal endpoint.
- Strict HYBRID fails when keyword or vector retrieval fails.
- PUBLIC is user-visible behavior from GET /api/articles and may degrade.
- PUBLIC is not sent to the internal evaluation endpoint.
```

- [ ] **Step 5: Run metrics and report writer tests**

Run:

```bash
node --test experiments/scripts/retrieval-benchmark/metrics.test.mjs experiments/scripts/retrieval-benchmark/report-writer.test.mjs
```

Expected:

```text
# pass
```

## Task 6: Orchestrate Comparison Mode In Runner

**Files:**
- Modify: `experiments/scripts/retrieval-benchmark/runner.mjs`
- Modify: `experiments/scripts/retrieval-benchmark/runner.test.mjs`
- Modify: `experiments/scripts/retrieval-benchmark.mjs`

- [ ] **Step 1: Add runner tests for comparison mode and smoke compatibility**

Extend `runner.test.mjs`:

```js
test('runRetrievalBenchmark keeps existing public smoke path when systems are omitted', async () => {
  const result = await runRetrievalBenchmark({
    labelsPath,
    baseUrl: 'http://localhost:8080',
    outputDir,
    k: 5,
    searchClient: {
      searchArticles: async () => ({ rankedArticleIds: [4, 1], latencyMs: 20 }),
    },
    generatedAt: '2026-06-02T00:00:00.000Z',
  });

  assert.equal(result.run.system, 'hybrid');
  assert.equal(result.summary.evaluatedQueryCount, 1);
});

test('runRetrievalBenchmark creates comparison artifacts when systems are provided', async () => {
  const result = await runRetrievalBenchmark({
    labelsPath,
    baseUrl: 'http://localhost:8080',
    outputDir,
    k: 5,
    limit: 20,
    systems: ['keyword', 'hybrid', 'public'],
    evaluationClient: {
      createRuns: async () => ({
        runs: [
          completedRun({ query: 'graph rag failure', system: 'keyword', rankedArticleIds: [1, 4] }),
          completedRun({ query: 'graph rag failure', system: 'hybrid', rankedArticleIds: [4, 1] }),
        ],
      }),
    },
    searchClient: {
      searchArticles: async () => ({
        rankedArticleIds: [4, 1],
        latencyMs: 25,
        status: 'COMPLETED',
        resolvedMode: 'HYBRID',
        degraded: false,
      }),
    },
    generatedAt: '2026-06-02T00:00:00.000Z',
  });

  assert.equal(result.comparison.systems.length, 3);
  assert.equal(result.runsBySystem.hybrid.system, 'hybrid');
  assert.equal(result.byQueryMetrics.length, 3);
});
```

Define test helper:

```js
function completedRun({ query, system, rankedArticleIds }) {
  return {
    query,
    system,
    status: 'COMPLETED',
    rankedArticleIds,
    candidateCount: rankedArticleIds.length,
    staleCandidateCount: 0,
    failureReason: null,
    degraded: false,
    resolvedMode: system.toUpperCase(),
    latencyMs: 12,
    latencySource: 'backend_total',
    timings: { totalElapsedMs: 12 },
    metadata: null,
  };
}
```

- [ ] **Step 2: Implement runner branching**

In `runner.mjs`, keep current body behind:

```js
export async function runRetrievalBenchmark(options) {
  if (!options.systems) {
    return runPublicSmokeBenchmark(options);
  }

  return runSystemComparisonBenchmark({
    ...options,
    searchClient: options.searchClient ?? createArticleSearchClient({ baseUrl: options.baseUrl }),
    evaluationClient: options.evaluationClient ?? createRetrievalEvaluationClient({ baseUrl: options.baseUrl }),
  });
}
```

Comparison flow:

```js
const strictSystems = systems.filter((system) => system !== 'public');
const runsBySystem = {};

if (strictSystems.length > 0) {
  const internalResult = await evaluationClient.createRuns({
    queries: labels.queries.map((query) => query.query),
    systems: strictSystems,
    limit,
  });

  for (const system of strictSystems) {
    runsBySystem[system] = buildRunArtifact({
      labelsPath,
      baseUrl,
      system,
      k,
      limit,
      queries: internalResult.runs.filter((run) => run.system === system),
    });
  }
}

if (systems.includes('public')) {
  const publicRuns = [];
  for (const labeledQuery of labels.queries) {
    const searchResult = await searchClient.searchArticles(labeledQuery.query, { includeMetrics: true });
    publicRuns.push({
      query: labeledQuery.query,
      system: 'public',
      status: 'COMPLETED',
      rankedArticleIds: searchResult.rankedArticleIds,
      candidateCount: searchResult.rankedArticleIds.length,
      staleCandidateCount: 0,
      failureReason: searchResult.failureReason ?? null,
      degraded: Boolean(searchResult.degraded),
      resolvedMode: searchResult.resolvedMode ?? null,
      latencyMs: searchResult.latencyMs,
      latencySource: 'client_round_trip',
    });
  }
  runsBySystem.public = buildRunArtifact({ labelsPath, baseUrl, system: 'public', k, limit, queries: publicRuns });
}
```

Then flatten run items and calculate metrics:

```js
const runItems = Object.values(runsBySystem).flatMap((run) => run.queries);
const labelsByQuery = new Map(labels.queries.map((query) => [query.query, query]));
const byQueryMetrics = runItems.map((runItem) => calculateRunRowMetrics({
  labeledQuery: labelsByQuery.get(runItem.query),
  runItem,
  k,
}));
const comparison = calculateComparisonMetrics({
  catalogId: labels.catalogId,
  catalogArticleCount: labels.catalogArticleCount,
  k,
  generatedAt,
  systems,
  byQueryMetrics,
});
```

Add this artifact helper:

```js
function buildRunArtifact({ labelsPath, baseUrl, system, k, limit, queries }) {
  return {
    labelsPath,
    baseUrl,
    system,
    k,
    limit,
    queries,
  };
}
```

- [ ] **Step 3: Wire CLI entrypoint**

In `experiments/scripts/retrieval-benchmark.mjs`, pass `systems` and `limit` from parsed args to `runRetrievalBenchmark`.

Print all artifact paths returned by either writer:

```js
for (const [name, path] of Object.entries(flattenArtifactPaths(result.artifacts))) {
  console.log(`${name}: ${path}`);
}
```

Add this path flattener in the entrypoint:

```js
function flattenArtifactPaths(artifacts) {
  return Object.entries(artifacts).reduce((paths, [name, value]) => {
    if (typeof value === 'string') {
      paths[name] = value;
      return paths;
    }

    if (value && typeof value === 'object') {
      for (const [childName, childValue] of Object.entries(value)) {
        paths[`${name}.${childName}`] = childValue;
      }
    }

    return paths;
  }, {});
}
```

- [ ] **Step 4: Run all runner tests**

Run:

```bash
node --test experiments/scripts/retrieval-benchmark/*.test.mjs
```

Expected:

```text
# pass
```

## Task 7: Documentation And Local Smoke Verification

**Files:**
- Modify: `docs/API_SPEC.md`
- Modify: `experiments/README.md`
- Modify: `docs/search-evaluation/queries.md`
- Modify: `docs/STATUS.md`
- Modify: `docs/STATUS.ko.md`
- Modify: `docs/ROADMAP.md`
- Modify: `docs/ROADMAP.ko.md`
- Modify: `docs/blog/2026-06-02-dev-log.md`
- Modify: `docs/blog/topic-queue.md`

- [ ] **Step 1: Update API spec**

Add internal endpoint section:

```markdown
### POST `/api/internal/search-evaluation/retrieval-runs`

Internal-only endpoint for local retrieval benchmark comparison.

- `KEYWORD`: Elasticsearch keyword candidate run.
- `VECTOR`: Qdrant vector candidate run.
- `HYBRID`: strict RRF run that fails when keyword or vector dependency fails.
- `PUBLIC` is not accepted here; the benchmark runner calls `GET /api/articles?query=...` for user-visible public behavior.

Important distinction:

- Internal `HYBRID` is an experiment mode and never degrades.
- Public `/api/articles` search is product behavior and may degrade to keyword-only, vector-only, or PostgreSQL fallback.
- These are reported separately so retrieval quality and user-visible resilience are not mixed.

Security guard:

- Default: `sigak.internal.search-evaluation.enabled=false`.
- Local comparison runs should opt in with `SIGAK_INTERNAL_SEARCH_EVALUATION_ENABLED=true`.
- Deployed environments should keep it disabled or block `/api/internal/**`.
- Response metadata can include embedding provider/model/dimension only.
- Response metadata must not include API keys, request headers, credentials, environment values, service URLs, raw embedding vectors, or stack traces.
```

- [ ] **Step 2: Update experiment docs**

In `experiments/README.md`, add:

```markdown
## Retrieval System Comparison

Use this after Elasticsearch and Qdrant projections are rebuilt.

```bash
node experiments/scripts/retrieval-benchmark.mjs \
  --labels=experiments/datasets/labels/search-labels.api-ready-2026-06-02.2026-06-02.json \
  --base-url=http://localhost:8080 \
  --output-dir=experiments/results/retrieval/latest \
  --systems=keyword,vector,hybrid,public \
  --k=5 \
  --limit=20
```

`HYBRID` here is strict experiment behavior from the internal endpoint. It fails instead of degrading when keyword or vector retrieval fails.
`PUBLIC` is the user-visible API behavior from `GET /api/articles?query=...` and may degrade. Do not send `public` to the backend internal endpoint.
```

- [ ] **Step 3: Update Korean labeling guide**

In `docs/search-evaluation/queries.md`, add a short Korean section:

```markdown
## 검색 방식 비교를 읽는 법

- `keyword`: Elasticsearch가 텍스트 일치 후보를 만든 결과다.
- `vector`: Qdrant가 임베딩 유사도 후보를 만든 결과다.
- `hybrid`: internal endpoint가 만드는 strict 실험 결과다. keyword와 vector가 모두 성공했을 때만 RRF로 섞고, 둘 중 하나라도 실패하면 실패로 기록한다.
- `public`: 사용자가 실제 API에서 보는 결과다. runner가 `GET /api/articles?query=...`를 호출해 만들며, 장애가 있으면 keyword-only, vector-only, PostgreSQL fallback으로 degrade될 수 있다.

주의:

- `public`은 `/api/internal/search-evaluation/retrieval-runs` 요청에 넣지 않는다.
- strict `hybrid`의 실패와 public의 degrade는 서로 다른 의미다.
- report에서는 retrieval 품질과 사용자 경험 안정성을 분리해서 읽는다.

작은 label set에서는 “가장 좋은 검색 방식”이라고 단정하지 않는다. report의 표현은 `best observed system in this smoke run`으로 제한한다.
```

- [ ] **Step 4: Run verification gate**

Run:

```bash
cd backend
./gradlew test
./gradlew check
cd ..
node --test experiments/scripts/retrieval-benchmark/*.test.mjs
git diff --check
```

Expected:

```text
BUILD SUCCESSFUL
# pass
```

If any command output differs from the expected result, do not update status or dev-log as complete. Record the mismatch as `미검증` or stop and debug it before moving on.

- [ ] **Step 5: Run local smoke when services are available**

Start services:

```bash
docker compose -f infra/docker-compose.yml up -d --pull never postgres elasticsearch qdrant
```

Run AI server:

```bash
cd ai
SIGAK_EMBEDDING_PROVIDER=deterministic .venv/bin/python -m uvicorn app.main:app --host 127.0.0.1 --port 8000
```

Run backend in another shell:

```bash
cd backend
SIGAK_AI_SERVER_URL=http://localhost:8000 ./gradlew bootRun
```

Rebuild projections:

```bash
curl -X POST 'http://localhost:8080/api/internal/search-projections/articles/rebuild'
curl -X POST 'http://localhost:8080/api/internal/search-projections/article-vectors/rebuild'
```

Run comparison:

```bash
node experiments/scripts/retrieval-benchmark.mjs \
  --labels=experiments/datasets/labels/search-labels.api-ready-2026-06-02.2026-06-02.json \
  --base-url=http://localhost:8080 \
  --output-dir=experiments/results/retrieval/latest \
  --systems=keyword,vector,hybrid,public \
  --k=5 \
  --limit=20
```

Record these values in docs:

```text
Elasticsearch indexedCount=
Qdrant indexedCount=
keyword completed/failed=
vector completed/failed=
hybrid completed/failed=
public completed/failed/degraded=
Recall@5 by system=
MRR@5 by system=
failureReasonCounts by system=
```

- [ ] **Step 6: Update status, roadmap, dev-log, and topic queue**

Use only actual verification output from Step 4 and Step 5.

If local smoke was not run, write `미검증` for smoke values and do not invent counts.

## Task 8: Final Review

**Files:**
- Review all files changed in Tasks 2-7.

- [ ] **Step 1: Run architectural review**

Check:

```text
Public API response shape is unchanged.
Internal endpoint does not accept PUBLIC.
Docs explain that PUBLIC comes from GET /api/articles, not the internal endpoint.
Strict HYBRID fails on keyword/vector dependency failure.
Report explains that strict HYBRID intentionally does not degrade, while PUBLIC may degrade.
PUBLIC run is created only by the Node runner through GET /api/articles.
Failed query/system rows are visible in JSON and Markdown.
Macro metrics and effective metrics are both present.
Small dataset warnings are present.
Endpoint exposure guard is documented.
No API keys, request headers, credentials, environment variable values, service URLs, raw vectors, or stack traces appear in endpoint metadata.
```

- [ ] **Step 2: Inspect diff**

Run:

```bash
git diff --stat
git diff -- backend/src/main/kotlin/com/sigak/search/hybrid/ArticlePublicSearchService.kt
git diff -- backend/src/main/kotlin/com/sigak/search/evaluation/retrieval
git diff -- experiments/scripts/retrieval-benchmark
git diff -- docs/API_SPEC.md experiments/README.md docs/search-evaluation/queries.md
```

Expected:

```text
Only planned files changed.
No public article DTO fields added.
No frontend files changed.
```

- [ ] **Step 3: Report verified and unverified work**

Report:

```text
완료:
- strict internal retrieval endpoint
- runner multi-system comparison mode
- metrics/report artifact expansion
- docs updates

검증:
- backend focused/full tests
- backend check
- Node runner tests
- git diff --check
- local smoke values if Step 5 ran

미검증:
- any skipped smoke or deployment guard behavior
```
