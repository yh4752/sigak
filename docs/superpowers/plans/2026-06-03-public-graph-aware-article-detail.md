# Public Graph-Aware Article Detail Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add a public article graph-context API and show Neo4j relation reasons on the article detail page without changing the shared `ArticleResponse` contract.

**Architecture:** PostgreSQL remains the source of truth for public article existence and article body data. Neo4j remains a rebuildable projection store and contributes optional relation reason context through a separate public endpoint. The frontend fetches graph context as supplementary data and degrades to the existing related article UI when graph context is missing or unavailable.

**Tech Stack:** Kotlin, Spring Boot, Spring MVC, JPA repository, Neo4j projection service, MockMvc, Kotlin test, React, TypeScript, Vite, Axios, Zod, Vitest.

---

## Reference Context

- Spec: `docs/superpowers/specs/2026-06-03-public-graph-aware-article-detail-design.md`
- Prior graph projection spec: `docs/superpowers/specs/2026-06-03-neo4j-graph-projection-design.md`
- Project rules: `AGENTS.md`
- Coding rules: `docs/CODING_CONVENTIONS.md`
- Existing public article API:
  - `backend/src/main/kotlin/com/sigak/article/controller/ArticleController.kt`
  - `backend/src/main/kotlin/com/sigak/article/service/ArticleService.kt`
  - `backend/src/main/kotlin/com/sigak/article/dto/ArticleResponse.kt`
- Existing internal graph context:
  - `backend/src/main/kotlin/com/sigak/search/graph/ArticleGraphContextService.kt`
  - `backend/src/main/kotlin/com/sigak/search/graph/ArticleGraphContextResponse.kt`
  - `backend/src/main/kotlin/com/sigak/search/graph/ArticleGraphContextController.kt`
- Existing frontend article detail:
  - `frontend/src/api/articles.ts`
  - `frontend/src/pages/ArticleDetailPage.tsx`
  - `frontend/src/pages/ArticleDetailPage.css`

## Worktree Guard

- Run `git status --short --branch` before implementation.
- This branch already contains uncommitted Neo4j graph projection work. Do not revert or overwrite it.
- Several older blog files may appear as untracked. Ignore them unless this plan explicitly touches them.
- Do not run `git commit` or `git push` unless the user explicitly asks for it.

## Scope Guard

This implementation includes:

- Public graph context DTO.
- Public graph context service.
- `GET /api/articles/{id}/graph-context`.
- Frontend API client for graph context.
- Detail page relation reason rendering.
- Backend/frontend tests.
- API/status/roadmap/dev-log/topic-queue updates after implementation.

This implementation does not include:

- Changes to `ArticleResponse`.
- Search ranking changes.
- Frontend calls to internal graph APIs.
- Graph explorer.
- GraphRAG chatbot.
- Scheduled Neo4j rebuild.
- Topic synonym or multilingual normalization.

## File Structure

Create:

- `backend/src/main/kotlin/com/sigak/article/dto/ArticlePublicGraphContextResponse.kt`
  - Public-safe graph context response DTO. Excludes timings and internal relation type.
- `backend/src/main/kotlin/com/sigak/article/service/ArticlePublicGraphContextService.kt`
  - Checks public article existence through PostgreSQL and maps optional Neo4j context to public response.
- `backend/src/test/kotlin/com/sigak/article/service/ArticlePublicGraphContextServiceTest.kt`
  - Isolated service tests for mapping, fallback, and not-found behavior.

Modify:

- `backend/src/main/kotlin/com/sigak/article/controller/ArticleController.kt`
  - Add `GET /api/articles/{id}/graph-context`.
- `backend/src/test/kotlin/com/sigak/article/controller/ArticleControllerTest.kt`
  - Add public graph context endpoint tests.
- `backend/src/test/kotlin/com/sigak/docs/OpenApiDocumentationTest.kt`
  - Assert the new path is documented.
- `frontend/src/api/articles.ts`
  - Add graph context Zod schema, type, and fetch function.
- `frontend/src/api/articles.test.ts`
  - Test fetch/validation for graph context.
- `frontend/src/pages/ArticleDetailPage.tsx`
  - Fetch graph context and render related article reason when present.
- `frontend/src/pages/ArticleDetailPage.css`
  - Add compact styles for reason/shared topic text.
- `frontend/src/pages/ArticleDetailPage.test.tsx`
  - Test reason display and graph-context failure fallback.
- `docs/API_SPEC.md`
- `docs/API_SPEC.ko.md`
- `docs/STATUS.md`
- `docs/STATUS.ko.md`
- `docs/ROADMAP.md`
- `docs/ROADMAP.ko.md`
- `docs/blog/2026-06-03-dev-log.md`
- `docs/blog/topic-queue.md`

Do not modify:

- `backend/src/main/kotlin/com/sigak/article/dto/ArticleResponse.kt`
- public search/hybrid ranking code
- collection pipeline

## Output Contract

```http
GET /api/articles/{id}/graph-context
```

Success:

```json
{
  "articleId": 4,
  "relatedArticleReasons": [
    {
      "articleId": 1,
      "reason": "Graph RAG evaluation connects to agent and retrieval evaluation.",
      "sharedTopics": ["evaluation"]
    }
  ],
  "topics": [
    {
      "name": "graph rag",
      "displayName": "Graph RAG",
      "relatedArticleIds": [1]
    }
  ]
}
```

Public article not found:

```http
404 Not Found
```

Invalid ID:

```http
400 Bad Request
```

Neo4j context missing or unavailable:

```json
{
  "articleId": 4,
  "relatedArticleReasons": [],
  "topics": []
}
```

## Task 1: Preflight And Contract Protection

**Files:**
- Read: `AGENTS.md`
- Read: `docs/STATUS.md`
- Read: `docs/ROADMAP.md`
- Read: `docs/CODING_CONVENTIONS.md`
- Read: `docs/superpowers/specs/2026-06-03-public-graph-aware-article-detail-design.md`
- Read: `backend/src/main/kotlin/com/sigak/article/dto/ArticleResponse.kt`
- Read: `frontend/src/api/articles.ts`

- [x] **Step 1: Confirm current worktree**

Run:

```bash
git status --short --branch
```

Expected:

```text
## codex/neo4j-graph-projection
```

Additional uncommitted Neo4j graph projection files are expected. Do not revert unrelated blog files or prior graph files.

- [x] **Step 2: Confirm existing shared ArticleResponse contract**

Check:

```bash
sed -n '1,120p' backend/src/main/kotlin/com/sigak/article/dto/ArticleResponse.kt
sed -n '1,80p' frontend/src/api/articles.ts
```

Expected:

- No graph context fields exist in `ArticleResponse`.
- The frontend `articleSchema` still validates only existing article fields.

- [x] **Step 3: Run focused baseline tests**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.article.controller.ArticleControllerTest --tests com.sigak.article.service.ArticleServiceTest
```

Expected:

```text
BUILD SUCCESSFUL
```

Run:

```bash
cd frontend
npm test -- articles.test.ts ArticleDetailPage.test.tsx
```

Expected:

```text
Test Files ... passed
Tests ... passed
```

## Task 2: Add Public Graph Context DTO And Service

**Files:**
- Create: `backend/src/main/kotlin/com/sigak/article/dto/ArticlePublicGraphContextResponse.kt`
- Create: `backend/src/main/kotlin/com/sigak/article/service/ArticlePublicGraphContextService.kt`
- Create: `backend/src/test/kotlin/com/sigak/article/service/ArticlePublicGraphContextServiceTest.kt`

- [x] **Step 1: Write failing service tests**

Create `backend/src/test/kotlin/com/sigak/article/service/ArticlePublicGraphContextServiceTest.kt`:

```kotlin
package com.sigak.article.service

import com.sigak.article.domain.ArticleEntity
import com.sigak.article.domain.ProcessingStatus
import com.sigak.article.repository.ArticleRepository
import com.sigak.search.graph.ArticleGraphContextNotFoundException
import com.sigak.search.graph.ArticleGraphContextResponse
import com.sigak.search.graph.ArticleGraphContextService
import com.sigak.search.graph.ArticleGraphContextTimingsResponse
import com.sigak.search.graph.ArticleGraphRelatedArticleResponse
import com.sigak.search.graph.ArticleGraphTopicContextResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class ArticlePublicGraphContextServiceTest {

    private val articleRepository = mock<ArticleRepository>()
    private val graphContextService = mock<ArticleGraphContextService>()
    private val service = ArticlePublicGraphContextService(articleRepository, graphContextService)

    @Test
    fun getContextReturnsPublicSafeGraphContext() {
        `when`(articleRepository.findApiReadyWithSourceById(4L, ProcessingStatus.PUBLISHED))
            .thenReturn(mock<ArticleEntity>())
        `when`(graphContextService.getContext(4L)).thenReturn(
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

        val response = service.getContext(4L)

        assertEquals(4L, response.articleId)
        assertEquals("graph rag", response.topics.single().name)
        assertEquals("Graph RAG", response.topics.single().displayName)
        assertEquals(listOf(1L), response.topics.single().relatedArticleIds)
        assertEquals(1L, response.relatedArticleReasons.single().articleId)
        assertEquals(
            "Graph RAG evaluation connects to agent and retrieval evaluation.",
            response.relatedArticleReasons.single().reason
        )
        assertEquals(listOf("evaluation"), response.relatedArticleReasons.single().sharedTopics)
    }

    @Test
    fun getContextReturnsEmptyContextWhenNeo4jProjectionIsMissing() {
        `when`(articleRepository.findApiReadyWithSourceById(4L, ProcessingStatus.PUBLISHED))
            .thenReturn(mock<ArticleEntity>())
        `when`(graphContextService.getContext(4L)).thenThrow(ArticleGraphContextNotFoundException(4L))

        val response = service.getContext(4L)

        assertEquals(4L, response.articleId)
        assertEquals(emptyList(), response.relatedArticleReasons)
        assertEquals(emptyList(), response.topics)
    }

    @Test
    fun getContextReturnsEmptyContextWhenNeo4jLookupFails() {
        `when`(articleRepository.findApiReadyWithSourceById(4L, ProcessingStatus.PUBLISHED))
            .thenReturn(mock<ArticleEntity>())
        `when`(graphContextService.getContext(4L)).thenThrow(RuntimeException("neo4j unavailable"))

        val response = service.getContext(4L)

        assertEquals(4L, response.articleId)
        assertEquals(emptyList(), response.relatedArticleReasons)
        assertEquals(emptyList(), response.topics)
    }

    @Test
    fun getContextThrowsNotFoundWhenArticleIsNotPublicApiReady() {
        `when`(articleRepository.findApiReadyWithSourceById(999L, ProcessingStatus.PUBLISHED))
            .thenReturn(null)

        val exception = assertFailsWith<ArticlePublicGraphContextNotFoundException> {
            service.getContext(999L)
        }

        assertEquals("Article not found: articleId=999", exception.message)
    }

    @Test
    fun getContextRejectsNonPositiveArticleId() {
        val exception = assertFailsWith<IllegalArgumentException> {
            service.getContext(0L)
        }

        assertEquals("articleId must be positive.", exception.message)
    }
}
```

- [x] **Step 2: Run service test and confirm it fails**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.article.service.ArticlePublicGraphContextServiceTest
```

Expected:

```text
Compilation error: Unresolved reference: ArticlePublicGraphContextService
```

- [x] **Step 3: Add public graph context DTO**

Create `backend/src/main/kotlin/com/sigak/article/dto/ArticlePublicGraphContextResponse.kt`:

```kotlin
package com.sigak.article.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Public graph context for an article detail page.")
data class ArticlePublicGraphContextResponse(
    @field:Schema(description = "Article ID this graph context belongs to.", example = "4")
    val articleId: Long,
    @field:Schema(description = "Display-safe relation reasons keyed by related article ID.")
    val relatedArticleReasons: List<ArticlePublicRelatedArticleReasonResponse>,
    @field:Schema(description = "Display-safe topic context from the graph projection.")
    val topics: List<ArticlePublicGraphTopicResponse>
)

data class ArticlePublicRelatedArticleReasonResponse(
    @field:Schema(description = "Related article ID.", example = "1")
    val articleId: Long,
    @field:Schema(description = "Stored relation reason, when available.")
    val reason: String?,
    @field:Schema(description = "Topic names shared by the current and related article.")
    val sharedTopics: List<String>
)

data class ArticlePublicGraphTopicResponse(
    @field:Schema(description = "Normalized topic name.", example = "graph rag")
    val name: String,
    @field:Schema(description = "Display topic name.", example = "Graph RAG")
    val displayName: String,
    @field:Schema(description = "Other article IDs connected through this topic.")
    val relatedArticleIds: List<Long>
)
```

- [x] **Step 4: Add public graph context service**

Create `backend/src/main/kotlin/com/sigak/article/service/ArticlePublicGraphContextService.kt`:

```kotlin
package com.sigak.article.service

import com.sigak.article.domain.ProcessingStatus
import com.sigak.article.dto.ArticlePublicGraphContextResponse
import com.sigak.article.dto.ArticlePublicGraphTopicResponse
import com.sigak.article.dto.ArticlePublicRelatedArticleReasonResponse
import com.sigak.article.repository.ArticleRepository
import com.sigak.search.graph.ArticleGraphContextNotFoundException
import com.sigak.search.graph.ArticleGraphContextResponse
import com.sigak.search.graph.ArticleGraphContextService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ArticlePublicGraphContextService(
    private val articleRepository: ArticleRepository,
    private val graphContextService: ArticleGraphContextService
) {

    @Transactional(readOnly = true)
    fun getContext(articleId: Long): ArticlePublicGraphContextResponse {
        require(articleId > 0) { "articleId must be positive." }

        articleRepository.findApiReadyWithSourceById(articleId, ProcessingStatus.PUBLISHED)
            ?: throw ArticlePublicGraphContextNotFoundException(articleId)

        val internalContext = try {
            graphContextService.getContext(articleId)
        } catch (exception: ArticleGraphContextNotFoundException) {
            null
        } catch (exception: RuntimeException) {
            // Graph context는 보조 정보이므로 Neo4j 장애를 public 상세 본문 장애로 전파하지 않는다.
            null
        }

        return internalContext?.toPublicResponse(articleId) ?: ArticlePublicGraphContextResponse(
            articleId = articleId,
            relatedArticleReasons = emptyList(),
            topics = emptyList()
        )
    }

    private fun ArticleGraphContextResponse.toPublicResponse(requestedArticleId: Long): ArticlePublicGraphContextResponse =
        ArticlePublicGraphContextResponse(
            articleId = requestedArticleId,
            relatedArticleReasons = relatedArticles.map { relatedArticle ->
                ArticlePublicRelatedArticleReasonResponse(
                    articleId = relatedArticle.articleId,
                    reason = relatedArticle.reason,
                    sharedTopics = relatedArticle.sharedTopics
                )
            },
            topics = topics.map { topic ->
                ArticlePublicGraphTopicResponse(
                    name = topic.name,
                    displayName = topic.displayName,
                    relatedArticleIds = topic.relatedArticleIds
                )
            }
        )
}

class ArticlePublicGraphContextNotFoundException(articleId: Long) :
    RuntimeException("Article not found: articleId=$articleId")
```

- [x] **Step 5: Run service test and confirm it passes**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.article.service.ArticlePublicGraphContextServiceTest
```

Expected:

```text
BUILD SUCCESSFUL
```

## Task 3: Add Public Graph Context Controller Endpoint

**Files:**
- Modify: `backend/src/main/kotlin/com/sigak/article/controller/ArticleController.kt`
- Modify: `backend/src/test/kotlin/com/sigak/article/controller/ArticleControllerTest.kt`
- Modify: `backend/src/test/kotlin/com/sigak/docs/OpenApiDocumentationTest.kt`

- [x] **Step 1: Write failing controller tests**

Modify `backend/src/test/kotlin/com/sigak/article/controller/ArticleControllerTest.kt`:

```kotlin
import com.sigak.search.graph.ArticleGraphContextResponse
import com.sigak.search.graph.ArticleGraphContextService
import com.sigak.search.graph.ArticleGraphContextTimingsResponse
import com.sigak.search.graph.ArticleGraphRelatedArticleResponse
import com.sigak.search.graph.ArticleGraphTopicContextResponse
```

Add a mock bean:

```kotlin
@MockBean
private lateinit var articleGraphContextService: ArticleGraphContextService
```

Add reset setup:

```kotlin
Mockito.reset(articleGraphContextService)
```

Add tests:

```kotlin
@Test
fun getArticleGraphContextReturnsPublicSafeRelationReasons() {
    Mockito.doReturn(
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
    ).`when`(articleGraphContextService).getContext(4L)

    mockMvc.perform(get("/api/articles/4/graph-context"))
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.articleId").value(4))
        .andExpect(jsonPath("$.relatedArticleReasons[0].articleId").value(1))
        .andExpect(jsonPath("$.relatedArticleReasons[0].reason").value("Graph RAG evaluation connects to agent and retrieval evaluation."))
        .andExpect(jsonPath("$.relatedArticleReasons[0].sharedTopics[0]").value("evaluation"))
        .andExpect(jsonPath("$.topics[0].name").value("graph rag"))
        .andExpect(jsonPath("$.topics[0].displayName").value("Graph RAG"))
        .andExpect(jsonPath("$.timings").doesNotExist())
        .andExpect(jsonPath("$.relatedArticleReasons[0].relationType").doesNotExist())
}

@Test
fun getArticleGraphContextReturnsEmptyContextWhenNeo4jContextIsUnavailable() {
    Mockito.doThrow(RuntimeException("neo4j unavailable"))
        .`when`(articleGraphContextService)
        .getContext(4L)

    mockMvc.perform(get("/api/articles/4/graph-context"))
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.articleId").value(4))
        .andExpect(jsonPath("$.relatedArticleReasons", hasSize<Any>(0)))
        .andExpect(jsonPath("$.topics", hasSize<Any>(0)))
}

@Test
fun getArticleGraphContextReturnsNotFoundForUnknownArticle() {
    mockMvc.perform(get("/api/articles/999/graph-context"))
        .andExpect(status().isNotFound)
}

@Test
fun getArticleGraphContextRejectsInvalidArticleId() {
    mockMvc.perform(get("/api/articles/0/graph-context"))
        .andExpect(status().isBadRequest)
}
```

- [x] **Step 2: Run controller tests and confirm they fail**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.article.controller.ArticleControllerTest
```

Expected:

```text
404 or compilation failure for missing graph-context endpoint
```

- [x] **Step 3: Add controller route**

Modify `backend/src/main/kotlin/com/sigak/article/controller/ArticleController.kt`:

```kotlin
import com.sigak.article.dto.ArticlePublicGraphContextResponse
import com.sigak.article.service.ArticlePublicGraphContextNotFoundException
import com.sigak.article.service.ArticlePublicGraphContextService
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
```

Update constructor:

```kotlin
class ArticleController(
    private val articleService: ArticleService,
    private val articlePublicGraphContextService: ArticlePublicGraphContextService
)
```

Add route before or after `getArticle`:

```kotlin
@GetMapping("/{id}/graph-context")
@Operation(
    summary = "Get article graph context",
    description = "Returns public-safe graph context for an article detail page. Neo4j projection context is optional and does not change the article detail response shape."
)
@ApiResponse(responseCode = "200", description = "Article graph context returned.")
@ApiResponse(responseCode = "400", description = "Invalid article ID.")
@ApiResponse(responseCode = "404", description = "Article not found.")
fun getArticleGraphContext(
    @Parameter(description = "Article ID.")
    @PathVariable
    id: Long
): ArticlePublicGraphContextResponse =
    articlePublicGraphContextService.getContext(id)
```

Add exception handlers:

```kotlin
@ExceptionHandler(IllegalArgumentException::class)
@ResponseStatus(HttpStatus.BAD_REQUEST)
fun handleIllegalArgumentException(exception: IllegalArgumentException): Map<String, String> =
    mapOf("message" to exception.message.orEmpty())

@ExceptionHandler(ArticlePublicGraphContextNotFoundException::class)
@ResponseStatus(HttpStatus.NOT_FOUND)
fun handleGraphContextNotFound(exception: ArticlePublicGraphContextNotFoundException): Map<String, String> =
    mapOf("message" to exception.message.orEmpty())
```

- [x] **Step 4: Update OpenAPI docs test**

Modify `backend/src/test/kotlin/com/sigak/docs/OpenApiDocumentationTest.kt`:

```kotlin
.andExpect(jsonPath("$.paths['/api/articles/{id}/graph-context'].get.summary").value("Get article graph context"))
```

- [x] **Step 5: Run backend focused tests**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.article.service.ArticlePublicGraphContextServiceTest --tests com.sigak.article.controller.ArticleControllerTest --tests com.sigak.docs.OpenApiDocumentationTest
```

Expected:

```text
BUILD SUCCESSFUL
```

## Task 4: Add Frontend Graph Context API Client

**Files:**
- Modify: `frontend/src/api/articles.ts`
- Modify: `frontend/src/api/articles.test.ts`

- [x] **Step 1: Write failing API client tests**

Modify imports in `frontend/src/api/articles.test.ts`:

```ts
import { fetchArticles, fetchArticle, fetchArticlesByIds, fetchArticleGraphContext } from './articles'
```

Add:

```ts
const mockGraphContext = {
  articleId: 4,
  relatedArticleReasons: [
    {
      articleId: 1,
      reason: 'Graph RAG evaluation connects to agent and retrieval evaluation.',
      sharedTopics: ['evaluation'],
    },
  ],
  topics: [
    {
      name: 'graph rag',
      displayName: 'Graph RAG',
      relatedArticleIds: [1],
    },
  ],
}

describe('fetchArticleGraphContext', () => {
  beforeEach(() => {
    vi.mocked(httpClient.get).mockReset()
  })

  it('fetches public graph context for an article and validates the response', async () => {
    vi.mocked(httpClient.get).mockResolvedValue({ data: mockGraphContext })

    const graphContext = await fetchArticleGraphContext(4)

    expect(httpClient.get).toHaveBeenCalledWith('/api/articles/4/graph-context')
    expect(graphContext).toEqual(mockGraphContext)
  })

  it('rejects invalid graph context responses before they reach the UI', async () => {
    vi.mocked(httpClient.get).mockResolvedValue({
      data: { ...mockGraphContext, relatedArticleReasons: [{ articleId: '1' }] },
    })

    await expect(fetchArticleGraphContext(4)).rejects.toThrow()
  })
})
```

- [x] **Step 2: Run frontend API test and confirm it fails**

Run:

```bash
cd frontend
npm test -- articles.test.ts
```

Expected:

```text
fetchArticleGraphContext is not a function
```

- [x] **Step 3: Add graph context schema and function**

Modify `frontend/src/api/articles.ts`:

```ts
const articleGraphRelatedReasonSchema = z.object({
  articleId: z.number(),
  reason: z.string().nullable(),
  sharedTopics: z.array(z.string()),
})

const articleGraphTopicSchema = z.object({
  name: z.string(),
  displayName: z.string(),
  relatedArticleIds: z.array(z.number()),
})

const articleGraphContextSchema = z.object({
  articleId: z.number(),
  relatedArticleReasons: z.array(articleGraphRelatedReasonSchema),
  topics: z.array(articleGraphTopicSchema),
})

export type ArticleGraphContext = z.infer<typeof articleGraphContextSchema>
export type ArticleGraphRelatedReason = z.infer<typeof articleGraphRelatedReasonSchema>

export async function fetchArticleGraphContext(id: number): Promise<ArticleGraphContext> {
  const response = await httpClient.get(`/api/articles/${id}/graph-context`)
  return articleGraphContextSchema.parse(response.data)
}
```

- [x] **Step 4: Run frontend API test and confirm it passes**

Run:

```bash
cd frontend
npm test -- articles.test.ts
```

Expected:

```text
Test Files 1 passed
```

## Task 5: Render Graph Reasons On Article Detail

**Files:**
- Modify: `frontend/src/pages/ArticleDetailPage.tsx`
- Modify: `frontend/src/pages/ArticleDetailPage.css`
- Modify: `frontend/src/pages/ArticleDetailPage.test.tsx`

- [x] **Step 1: Write failing detail page tests**

Modify mock import in `frontend/src/pages/ArticleDetailPage.test.tsx`:

```ts
import { fetchArticle, fetchArticleGraphContext, fetchArticlesByIds } from '../api/articles'
```

Modify module mock:

```ts
vi.mock('../api/articles', () => ({
  fetchArticles: vi.fn(),
  fetchArticle: vi.fn(),
  fetchArticleGraphContext: vi.fn(),
  fetchArticlesByIds: vi.fn(),
}))
```

Modify `beforeEach`:

```ts
vi.mocked(fetchArticleGraphContext).mockReset()
vi.mocked(fetchArticleGraphContext).mockResolvedValue({
  articleId: article.id,
  relatedArticleReasons: [],
  topics: [],
})
```

Add tests:

```ts
it('renders related article graph reason when graph context is available', async () => {
  const articleWithRelated = {
    ...article,
    id: 4,
    relatedArticleIds: [1],
  }
  const relatedArticle = {
    ...article,
    id: 1,
    title: 'OpenAI Releases Agent Evaluation Toolkit',
    relatedArticleIds: [],
  }
  vi.mocked(fetchArticle).mockResolvedValue(articleWithRelated)
  vi.mocked(fetchArticlesByIds).mockResolvedValue([relatedArticle])
  vi.mocked(fetchArticleGraphContext).mockResolvedValue({
    articleId: 4,
    relatedArticleReasons: [
      {
        articleId: 1,
        reason: 'Graph RAG evaluation connects to agent and retrieval evaluation.',
        sharedTopics: ['evaluation'],
      },
    ],
    topics: [],
  })

  renderDetailPage('4')

  expect(await screen.findByRole('link', { name: 'OpenAI Releases Agent Evaluation Toolkit' })).toBeInTheDocument()
  expect(await screen.findByText('Related reason')).toBeInTheDocument()
  expect(screen.getByText('Graph RAG evaluation connects to agent and retrieval evaluation.')).toBeInTheDocument()
  expect(screen.getByText('Shared topics: evaluation')).toBeInTheDocument()
})

it('keeps related articles visible when graph context request fails', async () => {
  const articleWithRelated = {
    ...article,
    relatedArticleIds: [3],
  }
  const relatedArticle = {
    ...article,
    id: 3,
    title: 'Related Graph RAG Article',
    relatedArticleIds: [],
  }
  vi.mocked(fetchArticle).mockResolvedValue(articleWithRelated)
  vi.mocked(fetchArticlesByIds).mockResolvedValue([relatedArticle])
  vi.mocked(fetchArticleGraphContext).mockRejectedValue(new Error('neo4j unavailable'))

  renderDetailPage()

  expect(await screen.findByRole('link', { name: 'Related Graph RAG Article' })).toBeInTheDocument()
  expect(screen.queryByText('Related reason')).not.toBeInTheDocument()
})
```

- [x] **Step 2: Run detail page test and confirm it fails**

Run:

```bash
cd frontend
npm test -- ArticleDetailPage.test.tsx
```

Expected:

```text
fetchArticleGraphContext is not mocked or related reason text is missing
```

- [x] **Step 3: Add graph context fetch state**

Modify `frontend/src/pages/ArticleDetailPage.tsx` imports:

```ts
import { fetchArticle, fetchArticleGraphContext, fetchArticlesByIds } from '../api/articles'
import type { Article, ArticleGraphContext, ArticleGraphRelatedReason } from '../api/articles'
```

Add state:

```ts
const [graphContext, setGraphContext] = useState<{ articleId: number, context: ArticleGraphContext } | null>(null)
```

Add effect:

```ts
useEffect(() => {
  if (!article) return

  let isCurrent = true
  fetchArticleGraphContext(article.id)
    .then((context) => {
      if (isCurrent) {
        setGraphContext({ articleId: article.id, context })
      }
    })
    .catch(() => {
      // Graph context는 보조 정보이므로 실패해도 상세 본문과 related article은 유지한다.
      if (isCurrent) {
        setGraphContext({
          articleId: article.id,
          context: {
            articleId: article.id,
            relatedArticleReasons: [],
            topics: [],
          },
        })
      }
    })
  return () => {
    isCurrent = false
  }
}, [article])
```

Add lookup before render return:

```ts
const visibleGraphContext =
  graphContext && article && graphContext.articleId === article.id ? graphContext.context : null
const relatedReasonsById = new Map(
  (visibleGraphContext?.relatedArticleReasons ?? []).map((reason) => [reason.articleId, reason]),
)
```

Add helper inside component:

```ts
function renderRelatedReason(reason: ArticleGraphRelatedReason | undefined) {
  if (!reason || (!reason.reason && reason.sharedTopics.length === 0)) {
    return null
  }

  return (
    <div className="detail-related__context">
      {reason.reason && (
        <p className="detail-related__reason">
          <span>Related reason</span>
          {reason.reason}
        </p>
      )}
      {reason.sharedTopics.length > 0 && (
        <p className="detail-related__shared">
          Shared topics: {reason.sharedTopics.join(', ')}
        </p>
      )}
    </div>
  )
}
```

Update related article item:

```tsx
{visibleRelatedArticles.map((related) => {
  const relatedReason = relatedReasonsById.get(related.id)

  return (
    <li key={related.id} className="detail-related__item">
      <div className="detail-related__body">
        <Link to={`/articles/${related.id}`} className="detail-related__title">
          {related.title}
        </Link>
        {renderRelatedReason(relatedReason)}
      </div>
      <span className="detail-related__tag">{related.primaryCategory}</span>
    </li>
  )
})}
```

- [x] **Step 4: Add compact reason styles**

Modify `frontend/src/pages/ArticleDetailPage.css`:

```css
.detail-related__body {
  min-width: 0;
}

.detail-related__context {
  margin-top: 5px;
}

.detail-related__reason,
.detail-related__shared {
  margin: 0;
  font-size: 11px;
  line-height: 1.5;
  color: var(--color-text-meta);
}

.detail-related__reason span {
  display: block;
  margin-bottom: 1px;
  color: var(--color-text-muted);
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
```

- [x] **Step 5: Run detail page tests and confirm they pass**

Run:

```bash
cd frontend
npm test -- ArticleDetailPage.test.tsx
```

Expected:

```text
Test Files 1 passed
```

## Task 6: Documentation And Verification

**Files:**
- Modify: `docs/API_SPEC.md`
- Modify: `docs/API_SPEC.ko.md`
- Modify: `docs/STATUS.md`
- Modify: `docs/STATUS.ko.md`
- Modify: `docs/ROADMAP.md`
- Modify: `docs/ROADMAP.ko.md`
- Modify: `docs/blog/2026-06-03-dev-log.md`
- Modify: `docs/blog/topic-queue.md`

- [x] **Step 1: Update API spec docs**

Add `GET /api/articles/{id}/graph-context` to `docs/API_SPEC.md` and `docs/API_SPEC.ko.md`.

Required points:

- This is a public article detail companion endpoint.
- It does not change `ArticleResponse`.
- Missing/unavailable Neo4j context returns empty graph context for an existing article.
- Public response excludes timings and internal relation type.

- [x] **Step 2: Update status and roadmap**

Update `docs/STATUS.md`, `docs/STATUS.ko.md`, `docs/ROADMAP.md`, and `docs/ROADMAP.ko.md`.

Required points:

- Mark public graph-aware article detail as implemented only after backend/frontend tests and smoke pass.
- Keep graph-aware evaluation as pending.
- Mention Neo4j context is still a projection and can degrade to empty context.

- [x] **Step 3: Update dev-log and topic queue**

Update `docs/blog/2026-06-03-dev-log.md` with only facts verified in this implementation session.

Update `docs/blog/topic-queue.md` by either:

- strengthening the existing Neo4j projection candidate with public detail notes, or
- adding a separate candidate if the implementation produces a distinct API-boundary/fallback lesson.

- [x] **Step 4: Run backend verification**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.article.service.ArticlePublicGraphContextServiceTest --tests com.sigak.article.controller.ArticleControllerTest --tests com.sigak.docs.OpenApiDocumentationTest
./gradlew test
./gradlew check
```

Expected:

```text
BUILD SUCCESSFUL
BUILD SUCCESSFUL
BUILD SUCCESSFUL
```

- [x] **Step 5: Run frontend verification**

Run:

```bash
cd frontend
npm test -- articles.test.ts ArticleDetailPage.test.tsx
npm test
npm run lint
npm run build
```

Expected:

```text
focused tests pass
full tests pass
lint passes
build passes
```

- [x] **Step 6: Run local smoke**

Start services as needed:

```bash
docker compose -f infra/docker-compose.yml up -d --pull never postgres neo4j
cd backend
./gradlew bootRun
```

In another shell:

```bash
curl -sS -m 30 -X POST http://localhost:8080/api/internal/graph-projections/articles/rebuild
curl -sS -m 15 http://localhost:8080/api/articles/4/graph-context
```

Expected:

- Rebuild returns `status=completed`.
- Public graph context for article `4` returns `articleId=4`.
- Response contains `relatedArticleReasons`.
- Response does not contain `timings`.

Stop only services started for this task.

- [x] **Step 7: Run whitespace check**

Run:

```bash
git diff --check
```

Expected:

```text
no output
```

## Final Checklist

- [x] `ArticleResponse` remains unchanged.
- [x] Public frontend does not call internal graph endpoints.
- [x] Public graph context endpoint hides timings and relation type.
- [x] Public article not found returns `404`.
- [x] Non-positive article ID returns `400`.
- [x] Neo4j missing/failure degrades to empty public graph context.
- [x] Related article reason renders only when available.
- [x] Existing related article UI works when graph context is empty or fails.
- [x] Backend focused tests pass.
- [x] Frontend focused tests pass.
- [x] Backend full test/check pass.
- [x] Frontend full test/lint/build pass.
- [x] Local smoke values are recorded in dev-log.
