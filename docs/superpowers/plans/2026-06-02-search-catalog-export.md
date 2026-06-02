# Search Catalog Export Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** PostgreSQL의 API-ready article을 `docs/search-evaluation/labeling.html`이 import할 수 있는 frozen catalog JSON으로 export하는 Spring Boot command를 만든다.

**Architecture:** 새 `search-catalog-export` command runner를 추가한다. Command는 API-ready article을 읽고 catalog model로 변환한 뒤 JSON 파일로 저장한다. Public/internal HTTP API, benchmark runner, `/research` UI는 만들지 않는다.

**Tech Stack:** Kotlin, Spring Boot `ApplicationRunner`, Jackson `ObjectMapper`, JUnit/Kotlin test, PostgreSQL-backed existing `ArticleService`.

**Completion note (2026-06-02):** Subagent-driven implementation and local verification finished. Final local smoke generated `experiments/datasets/raw/articles.catalog.json` with `catalogId=api-ready-2026-06-02` and article count `6`; verification details are recorded in `docs/blog/2026-06-02-dev-log.md` and `docs/STATUS.md`.

---

## Reference Spec

- `docs/superpowers/specs/2026-06-02-search-catalog-export-design.md`
- `docs/superpowers/specs/2026-06-01-search-labeling-static-html-design.md`
- `docs/search-evaluation/labeling.html`
- `docs/CODING_CONVENTIONS.md`

## File Structure

Create:

- `backend/src/main/kotlin/com/sigak/search/evaluation/catalog/SearchCatalogExportCommand.kt`
  - Parsed command value object.
- `backend/src/main/kotlin/com/sigak/search/evaluation/catalog/SearchCatalogExportCommandParser.kt`
  - Parses `search-catalog-export`, `--output`, `--limit`, and `--catalog-id`.
- `backend/src/main/kotlin/com/sigak/search/evaluation/catalog/SearchCatalog.kt`
  - Catalog JSON model.
- `backend/src/main/kotlin/com/sigak/search/evaluation/catalog/SearchCatalogFactory.kt`
  - Converts `ArticleResponse` values into catalog JSON model.
- `backend/src/main/kotlin/com/sigak/search/evaluation/catalog/SearchCatalogArticleReader.kt`
  - Small Spring boundary that reads API-ready articles from `ArticleService.getArticles(null)`.
- `backend/src/main/kotlin/com/sigak/search/evaluation/catalog/SearchCatalogExportService.kt`
  - Builds one catalog from command + API-ready article reader.
- `backend/src/main/kotlin/com/sigak/search/evaluation/catalog/SearchCatalogExportJsonWriter.kt`
  - Writes pretty JSON to the requested output path.
- `backend/src/main/kotlin/com/sigak/search/evaluation/catalog/SearchCatalogExportCommandFormatter.kt`
  - Formats success/error command output.
- `backend/src/main/kotlin/com/sigak/search/evaluation/catalog/SearchCatalogExportCommandOutput.kt`
  - stdout/stderr abstraction for tests.
- `backend/src/main/kotlin/com/sigak/search/evaluation/catalog/SearchCatalogExportCommandRunner.kt`
  - Spring `ApplicationRunner` that connects parser, service, writer, output, and exit.
- `backend/src/test/kotlin/com/sigak/search/evaluation/catalog/SearchCatalogExportCommandParserTest.kt`
- `backend/src/test/kotlin/com/sigak/search/evaluation/catalog/SearchCatalogFactoryTest.kt`
- `backend/src/test/kotlin/com/sigak/search/evaluation/catalog/SearchCatalogExportServiceTest.kt`
- `backend/src/test/kotlin/com/sigak/search/evaluation/catalog/SearchCatalogExportJsonWriterTest.kt`
- `backend/src/test/kotlin/com/sigak/search/evaluation/catalog/SearchCatalogExportCommandRunnerTest.kt`
- `experiments/README.md`
- `experiments/datasets/raw/.gitkeep`
- `experiments/datasets/labels/.gitkeep`
- `experiments/datasets/processed/.gitkeep`

Modify:

- `docs/search-evaluation/queries.md`
  - Add the frozen catalog export command and import flow.
- `docs/STATUS.md`
- `docs/STATUS.ko.md`
- `docs/ROADMAP.md`
- `docs/ROADMAP.ko.md`
- `docs/blog/2026-06-02-dev-log.md`
- `docs/blog/topic-queue.md`

Important note:

- Do not commit during implementation unless the user explicitly asks for commit/push.
- The existing `com.sigak.collection.runner.ApplicationExit` is generic enough to reuse for this command. Do not move it in this task; moving it would create extra refactor blast radius.

## Task 1: Preflight And Existing Context Check

**Files:**
- Read: `AGENTS.md`
- Read: `docs/STATUS.md`
- Read: `docs/ROADMAP.md`
- Read: `docs/superpowers/specs/2026-06-02-search-catalog-export-design.md`
- Read: `docs/CODING_CONVENTIONS.md`
- Read: `backend/src/main/kotlin/com/sigak/collection/runner/CollectionRunCommandRunner.kt`
- Read: `backend/src/main/kotlin/com/sigak/article/service/ArticleService.kt`

- [x] **Step 1: Confirm working tree before editing**

Run:

```bash
git status --short --branch
```

Expected:

- Existing documentation/static HTML changes may already be present.
- Do not revert unrelated user or prior-session changes.

- [x] **Step 2: Re-read the spec and code conventions**

Run:

```bash
sed -n '1,420p' docs/superpowers/specs/2026-06-02-search-catalog-export-design.md
sed -n '1,260p' docs/CODING_CONVENTIONS.md
```

Expected:

- Spec says command runner, not HTTP endpoint.
- Spec says use API-ready articles and preserve labeling tool catalog schema.
- Comments added in Kotlin must be Korean and explain intent/trade-off.

- [x] **Step 3: Confirm existing API-ready boundary**

Run:

```bash
sed -n '1,240p' backend/src/main/kotlin/com/sigak/article/service/ArticleService.kt
sed -n '1,220p' backend/src/main/kotlin/com/sigak/article/dto/ArticleResponse.kt
```

Expected:

- `ArticleService.getArticles(null)` returns API-ready article responses.
- `ArticleResponse` has the fields needed by catalog export.

## Task 2: Command Parser

**Files:**
- Create: `backend/src/main/kotlin/com/sigak/search/evaluation/catalog/SearchCatalogExportCommand.kt`
- Create: `backend/src/main/kotlin/com/sigak/search/evaluation/catalog/SearchCatalogExportCommandParser.kt`
- Test: `backend/src/test/kotlin/com/sigak/search/evaluation/catalog/SearchCatalogExportCommandParserTest.kt`

- [x] **Step 1: Write the failing parser test**

Create `backend/src/test/kotlin/com/sigak/search/evaluation/catalog/SearchCatalogExportCommandParserTest.kt`:

```kotlin
package com.sigak.search.evaluation.catalog

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SearchCatalogExportCommandParserTest {

    private val parser = SearchCatalogExportCommandParser()

    @Test
    fun parseReturnsNullWhenSearchCatalogExportCommandIsNotRequested() {
        assertNull(parser.parse(emptyArray()))
        assertNull(parser.parse(arrayOf("--server.port=8081")))
        assertNull(parser.parse(arrayOf("collection-run", "--sources=github-blog")))
    }

    @Test
    fun parseBuildsCommandFromOutputLimitAndCatalogIdOptions() {
        val command = parser.parse(
            arrayOf(
                "search-catalog-export",
                "--output=../experiments/datasets/raw/articles.catalog.json",
                "--limit=30",
                "--catalog-id=api-ready-test"
            )
        )

        assertEquals(Path.of("../experiments/datasets/raw/articles.catalog.json"), command?.output)
        assertEquals(30, command?.limit)
        assertEquals("api-ready-test", command?.catalogId)
    }

    @Test
    fun parseUsesDefaultLimitAndCatalogIdWhenOptionalOptionsAreMissing() {
        val command = parser.parse(
            arrayOf(
                "search-catalog-export",
                "--output=../experiments/datasets/raw/articles.catalog.json"
            )
        )

        assertEquals(Path.of("../experiments/datasets/raw/articles.catalog.json"), command?.output)
        assertEquals(50, command?.limit)
        assertEquals(null, command?.catalogId)
    }

    @Test
    fun parseRequiresOutputOption() {
        val exception = assertFailsWith<IllegalArgumentException> {
            parser.parse(arrayOf("search-catalog-export", "--limit=30"))
        }

        assertEquals("search-catalog-export --output is required.", exception.message)
    }

    @Test
    fun parseRejectsBlankOutputOption() {
        val exception = assertFailsWith<IllegalArgumentException> {
            parser.parse(arrayOf("search-catalog-export", "--output=   "))
        }

        assertEquals("search-catalog-export --output must not be blank.", exception.message)
    }

    @Test
    fun parseRejectsNonNumericLimit() {
        val exception = assertFailsWith<IllegalArgumentException> {
            parser.parse(
                arrayOf(
                    "search-catalog-export",
                    "--output=../experiments/datasets/raw/articles.catalog.json",
                    "--limit=many"
                )
            )
        }

        assertEquals("search-catalog-export --limit must be a number.", exception.message)
    }

    @Test
    fun parseRejectsLimitLessThanOne() {
        val exception = assertFailsWith<IllegalArgumentException> {
            parser.parse(
                arrayOf(
                    "search-catalog-export",
                    "--output=../experiments/datasets/raw/articles.catalog.json",
                    "--limit=0"
                )
            )
        }

        assertEquals("search-catalog-export --limit must be at least 1.", exception.message)
    }

    @Test
    fun parseRejectsBlankCatalogId() {
        val exception = assertFailsWith<IllegalArgumentException> {
            parser.parse(
                arrayOf(
                    "search-catalog-export",
                    "--output=../experiments/datasets/raw/articles.catalog.json",
                    "--catalog-id=   "
                )
            )
        }

        assertEquals("search-catalog-export --catalog-id must not be blank.", exception.message)
    }

    @Test
    fun parseRejectsUnknownOptions() {
        val exception = assertFailsWith<IllegalArgumentException> {
            parser.parse(
                arrayOf(
                    "search-catalog-export",
                    "--output=../experiments/datasets/raw/articles.catalog.json",
                    "--format=csv"
                )
            )
        }

        assertEquals("Unknown search-catalog-export option: --format=csv", exception.message)
    }
}
```

- [x] **Step 2: Run parser test and verify it fails**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.search.evaluation.catalog.SearchCatalogExportCommandParserTest
```

Expected:

- Fails because `SearchCatalogExportCommandParser` does not exist.

- [x] **Step 3: Add the command value object**

Create `backend/src/main/kotlin/com/sigak/search/evaluation/catalog/SearchCatalogExportCommand.kt`:

```kotlin
package com.sigak.search.evaluation.catalog

import java.nio.file.Path

data class SearchCatalogExportCommand(
    val output: Path,
    val limit: Int = DEFAULT_SEARCH_CATALOG_EXPORT_LIMIT,
    val catalogId: String? = null
)

const val DEFAULT_SEARCH_CATALOG_EXPORT_LIMIT = 50
```

- [x] **Step 4: Add the parser implementation**

Create `backend/src/main/kotlin/com/sigak/search/evaluation/catalog/SearchCatalogExportCommandParser.kt`:

```kotlin
package com.sigak.search.evaluation.catalog

import java.nio.file.Path
import org.springframework.stereotype.Component

private const val SEARCH_CATALOG_EXPORT_COMMAND = "search-catalog-export"
private const val OUTPUT_OPTION_PREFIX = "--output="
private const val LIMIT_OPTION_PREFIX = "--limit="
private const val CATALOG_ID_OPTION_PREFIX = "--catalog-id="

@Component
class SearchCatalogExportCommandParser {

    fun parse(args: Array<String>): SearchCatalogExportCommand? {
        if (args.firstOrNull() != SEARCH_CATALOG_EXPORT_COMMAND) {
            return null
        }

        var output: Path? = null
        var limit = DEFAULT_SEARCH_CATALOG_EXPORT_LIMIT
        var catalogId: String? = null

        args.drop(1).forEach { arg ->
            when {
                arg.startsWith(OUTPUT_OPTION_PREFIX) -> {
                    output = outputPathFor(arg.removePrefix(OUTPUT_OPTION_PREFIX))
                }
                arg.startsWith(LIMIT_OPTION_PREFIX) -> {
                    limit = limitFor(arg.removePrefix(LIMIT_OPTION_PREFIX))
                }
                arg.startsWith(CATALOG_ID_OPTION_PREFIX) -> {
                    catalogId = catalogIdFor(arg.removePrefix(CATALOG_ID_OPTION_PREFIX))
                }
                else -> throw IllegalArgumentException("Unknown search-catalog-export option: $arg")
            }
        }

        return SearchCatalogExportCommand(
            output = output ?: throw IllegalArgumentException("search-catalog-export --output is required."),
            limit = limit,
            catalogId = catalogId
        )
    }

    private fun outputPathFor(value: String): Path {
        val normalized = value.trim()
        if (normalized.isBlank()) {
            throw IllegalArgumentException("search-catalog-export --output must not be blank.")
        }

        return Path.of(normalized)
    }

    private fun limitFor(value: String): Int {
        val limit = value.toIntOrNull()
            ?: throw IllegalArgumentException("search-catalog-export --limit must be a number.")

        if (limit < 1) {
            throw IllegalArgumentException("search-catalog-export --limit must be at least 1.")
        }

        return limit
    }

    private fun catalogIdFor(value: String): String {
        val normalized = value.trim()
        if (normalized.isBlank()) {
            throw IllegalArgumentException("search-catalog-export --catalog-id must not be blank.")
        }

        return normalized
    }
}
```

- [x] **Step 5: Run parser test and verify it passes**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.search.evaluation.catalog.SearchCatalogExportCommandParserTest
```

Expected:

- `BUILD SUCCESSFUL`

## Task 3: Catalog Model And Factory

**Files:**
- Create: `backend/src/main/kotlin/com/sigak/search/evaluation/catalog/SearchCatalog.kt`
- Create: `backend/src/main/kotlin/com/sigak/search/evaluation/catalog/SearchCatalogFactory.kt`
- Test: `backend/src/test/kotlin/com/sigak/search/evaluation/catalog/SearchCatalogFactoryTest.kt`

- [x] **Step 1: Write the failing factory test**

Create `backend/src/test/kotlin/com/sigak/search/evaluation/catalog/SearchCatalogFactoryTest.kt`:

```kotlin
package com.sigak.search.evaluation.catalog

import com.sigak.article.dto.ArticleResponse
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SearchCatalogFactoryTest {

    private val factory = SearchCatalogFactory()

    @Test
    fun buildMapsArticleResponsesToLabelingCatalogSchema() {
        val catalog = factory.build(
            articles = listOf(
                article(
                    id = 7,
                    title = "Graph RAG Evaluation",
                    source = "Research Blog",
                    primaryCategory = "CS_RESEARCH",
                    topics = listOf("Graph RAG", "retrieval quality")
                )
            ),
            catalogId = "api-ready-test",
            generatedAt = Instant.parse("2026-06-02T00:00:00Z"),
            limit = 50
        )

        assertEquals(1, catalog.version)
        assertEquals("api-ready-test", catalog.catalogId)
        assertEquals("2026-06-02T00:00:00Z", catalog.generatedAt)
        assertEquals("postgres-api-ready", catalog.source)
        assertEquals(1, catalog.articles.size)

        val article = catalog.articles.single()
        assertEquals(7L, article.id)
        assertEquals("Graph RAG Evaluation", article.title)
        assertEquals("CS_RESEARCH", article.category)
        assertEquals(listOf("Graph RAG", "retrieval quality"), article.topics)
        assertEquals("summary for 7", article.summaryKo)
        assertEquals("why it matters for 7", article.whyItMattersKo)
        assertEquals("2026-05-07T00:00:00Z", article.publishedAt)
        assertEquals("Research Blog", article.source)
        assertEquals("https://example.com/articles/7", article.url)
    }

    @Test
    fun buildAppliesLimitBeforeMappingArticles() {
        val catalog = factory.build(
            articles = listOf(article(1), article(2), article(3)),
            catalogId = "api-ready-test",
            generatedAt = Instant.parse("2026-06-02T00:00:00Z"),
            limit = 2
        )

        assertEquals(listOf(1L, 2L), catalog.articles.map { it.id })
    }

    @Test
    fun buildRejectsEmptyApiReadyArticles() {
        val exception = assertFailsWith<IllegalStateException> {
            factory.build(
                articles = emptyList(),
                catalogId = "api-ready-test",
                generatedAt = Instant.parse("2026-06-02T00:00:00Z"),
                limit = 50
            )
        }

        assertEquals("No API-ready articles are available for search catalog export.", exception.message)
    }

    private fun article(
        id: Long,
        title: String = "Article $id",
        source: String = "Source $id",
        primaryCategory: String = "AI",
        topics: List<String> = listOf("topic-$id")
    ): ArticleResponse =
        ArticleResponse(
            id = id,
            title = title,
            source = source,
            url = "https://example.com/articles/$id",
            publishedAt = "2026-05-07T00:00:00Z",
            eventType = "NEWS",
            primaryCategory = primaryCategory,
            topics = topics,
            summary = "summary for $id",
            whyItMatters = "why it matters for $id",
            importanceScore = 70,
            relatedArticleIds = emptyList()
        )
}
```

- [x] **Step 2: Run factory test and verify it fails**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.search.evaluation.catalog.SearchCatalogFactoryTest
```

Expected:

- Fails because `SearchCatalogFactory` and `SearchCatalog` do not exist.

- [x] **Step 3: Add catalog JSON model**

Create `backend/src/main/kotlin/com/sigak/search/evaluation/catalog/SearchCatalog.kt`:

```kotlin
package com.sigak.search.evaluation.catalog

data class SearchCatalog(
    val version: Int = 1,
    val catalogId: String,
    val generatedAt: String,
    val source: String,
    val articles: List<SearchCatalogArticle>
)

data class SearchCatalogArticle(
    val id: Long,
    val title: String,
    val category: String,
    val topics: List<String>,
    val summaryKo: String,
    val whyItMattersKo: String?,
    val publishedAt: String?,
    val source: String?,
    val url: String?
)
```

- [x] **Step 4: Add factory implementation**

Create `backend/src/main/kotlin/com/sigak/search/evaluation/catalog/SearchCatalogFactory.kt`:

```kotlin
package com.sigak.search.evaluation.catalog

import com.sigak.article.dto.ArticleResponse
import java.time.Instant
import org.springframework.stereotype.Component

private const val SEARCH_CATALOG_SOURCE = "postgres-api-ready"

@Component
class SearchCatalogFactory {

    fun build(
        articles: List<ArticleResponse>,
        catalogId: String,
        generatedAt: Instant,
        limit: Int
    ): SearchCatalog {
        val selectedArticles = articles.take(limit)
        if (selectedArticles.isEmpty()) {
            throw IllegalStateException("No API-ready articles are available for search catalog export.")
        }

        return SearchCatalog(
            catalogId = catalogId,
            generatedAt = generatedAt.toString(),
            source = SEARCH_CATALOG_SOURCE,
            articles = selectedArticles.map { article -> article.toCatalogArticle() }
        )
    }

    private fun ArticleResponse.toCatalogArticle(): SearchCatalogArticle =
        SearchCatalogArticle(
            id = id,
            title = title,
            category = primaryCategory,
            topics = topics,
            summaryKo = summary,
            whyItMattersKo = whyItMatters,
            publishedAt = publishedAt,
            source = source,
            url = url
        )
}
```

- [x] **Step 5: Run factory test and verify it passes**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.search.evaluation.catalog.SearchCatalogFactoryTest
```

Expected:

- `BUILD SUCCESSFUL`

## Task 4: Article Reader, Export Service, And JSON Writer

**Files:**
- Create: `backend/src/main/kotlin/com/sigak/search/evaluation/catalog/SearchCatalogArticleReader.kt`
- Create: `backend/src/main/kotlin/com/sigak/search/evaluation/catalog/SearchCatalogExportService.kt`
- Create: `backend/src/main/kotlin/com/sigak/search/evaluation/catalog/SearchCatalogExportJsonWriter.kt`
- Test: `backend/src/test/kotlin/com/sigak/search/evaluation/catalog/SearchCatalogExportServiceTest.kt`
- Test: `backend/src/test/kotlin/com/sigak/search/evaluation/catalog/SearchCatalogExportJsonWriterTest.kt`

- [x] **Step 1: Write the failing service test**

Create `backend/src/test/kotlin/com/sigak/search/evaluation/catalog/SearchCatalogExportServiceTest.kt`:

```kotlin
package com.sigak.search.evaluation.catalog

import com.sigak.article.dto.ArticleResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchCatalogExportServiceTest {

    @Test
    fun exportBuildsCatalogFromApiReadyArticleReader() {
        val service = SearchCatalogExportService(
            articleReader = StaticArticleReader(listOf(article(1), article(2))),
            catalogFactory = SearchCatalogFactory()
        )

        val catalog = service.export(
            SearchCatalogExportCommand(
                output = java.nio.file.Path.of("unused.json"),
                limit = 1,
                catalogId = "api-ready-test"
            )
        )

        assertEquals("api-ready-test", catalog.catalogId)
        assertEquals(listOf(1L), catalog.articles.map { it.id })
        assertTrue(catalog.generatedAt.isNotBlank())
    }

    @Test
    fun exportUsesDateBasedCatalogIdWhenCatalogIdIsMissing() {
        val service = SearchCatalogExportService(
            articleReader = StaticArticleReader(listOf(article(1))),
            catalogFactory = SearchCatalogFactory()
        )

        val catalog = service.export(
            SearchCatalogExportCommand(
                output = java.nio.file.Path.of("unused.json")
            )
        )

        assertTrue(Regex("api-ready-\\d{4}-\\d{2}-\\d{2}").matches(catalog.catalogId))
    }

    private class StaticArticleReader(
        private val articles: List<ArticleResponse>
    ) : SearchCatalogArticleReader {
        override fun readApiReadyArticles(): List<ArticleResponse> = articles
    }

    private fun article(id: Long): ArticleResponse =
        ArticleResponse(
            id = id,
            title = "Article $id",
            source = "Source $id",
            url = "https://example.com/articles/$id",
            publishedAt = "2026-05-07T00:00:00Z",
            eventType = "NEWS",
            primaryCategory = "AI",
            topics = listOf("topic-$id"),
            summary = "summary for $id",
            whyItMatters = "why it matters for $id",
            importanceScore = 70,
            relatedArticleIds = emptyList()
        )
}
```

- [x] **Step 2: Write the failing writer test**

Create `backend/src/test/kotlin/com/sigak/search/evaluation/catalog/SearchCatalogExportJsonWriterTest.kt`:

```kotlin
package com.sigak.search.evaluation.catalog

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class SearchCatalogExportJsonWriterTest {

    @TempDir
    lateinit var tempDir: Path

    private val writer = SearchCatalogExportJsonWriter(jacksonObjectMapper())

    @Test
    fun writeCreatesParentDirectoriesAndWritesPrettyJson() {
        val output = tempDir.resolve("nested/raw/articles.catalog.json")
        val catalog = SearchCatalog(
            catalogId = "api-ready-test",
            generatedAt = "2026-06-02T00:00:00Z",
            source = "postgres-api-ready",
            articles = listOf(
                SearchCatalogArticle(
                    id = 1,
                    title = "Article 1",
                    category = "AI",
                    topics = listOf("agent"),
                    summaryKo = "summary",
                    whyItMattersKo = "why",
                    publishedAt = "2026-05-07T00:00:00Z",
                    source = "Source",
                    url = "https://example.com/articles/1"
                )
            )
        )

        writer.write(output, catalog)

        assertTrue(Files.exists(output))
        assertTrue(output.readText().contains("\"catalogId\" : \"api-ready-test\""))
        assertEquals(true, output.readText().contains(System.lineSeparator()))
    }
}
```

- [x] **Step 3: Run service and writer tests and verify they fail**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.search.evaluation.catalog.SearchCatalogExportServiceTest --tests com.sigak.search.evaluation.catalog.SearchCatalogExportJsonWriterTest
```

Expected:

- Fails because service, reader, and writer do not exist.

- [x] **Step 4: Add the article reader boundary**

Create `backend/src/main/kotlin/com/sigak/search/evaluation/catalog/SearchCatalogArticleReader.kt`:

```kotlin
package com.sigak.search.evaluation.catalog

import com.sigak.article.dto.ArticleResponse
import com.sigak.article.service.ArticleService
import org.springframework.stereotype.Component

fun interface SearchCatalogArticleReader {
    fun readApiReadyArticles(): List<ArticleResponse>
}

@Component
class ArticleServiceSearchCatalogArticleReader(
    private val articleService: ArticleService
) : SearchCatalogArticleReader {

    override fun readApiReadyArticles(): List<ArticleResponse> =
        articleService.getArticles(null)
}
```

- [x] **Step 5: Add the export service**

Create `backend/src/main/kotlin/com/sigak/search/evaluation/catalog/SearchCatalogExportService.kt`:

```kotlin
package com.sigak.search.evaluation.catalog

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.springframework.stereotype.Service

@Service
class SearchCatalogExportService(
    private val articleReader: SearchCatalogArticleReader,
    private val catalogFactory: SearchCatalogFactory
) {

    fun export(command: SearchCatalogExportCommand): SearchCatalog {
        val catalogId = command.catalogId ?: defaultCatalogId()

        return catalogFactory.build(
            articles = articleReader.readApiReadyArticles(),
            catalogId = catalogId,
            generatedAt = Instant.now(),
            limit = command.limit
        )
    }

    private fun defaultCatalogId(): String =
        "api-ready-${LocalDate.now(ZoneOffset.UTC)}"
}
```

- [x] **Step 6: Add the JSON writer**

Create `backend/src/main/kotlin/com/sigak/search/evaluation/catalog/SearchCatalogExportJsonWriter.kt`:

```kotlin
package com.sigak.search.evaluation.catalog

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import org.springframework.stereotype.Component

@Component
class SearchCatalogExportJsonWriter(
    private val objectMapper: ObjectMapper
) {

    fun write(output: Path, catalog: SearchCatalog) {
        output.parent?.let { parent ->
            Files.createDirectories(parent)
        }

        objectMapper
            .writerWithDefaultPrettyPrinter()
            .writeValue(output.toFile(), catalog)
    }
}
```

- [x] **Step 7: Run service and writer tests and verify they pass**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.search.evaluation.catalog.SearchCatalogExportServiceTest --tests com.sigak.search.evaluation.catalog.SearchCatalogExportJsonWriterTest
```

Expected:

- `BUILD SUCCESSFUL`

## Task 5: Command Formatter, Output, And Runner

**Files:**
- Create: `backend/src/main/kotlin/com/sigak/search/evaluation/catalog/SearchCatalogExportCommandFormatter.kt`
- Create: `backend/src/main/kotlin/com/sigak/search/evaluation/catalog/SearchCatalogExportCommandOutput.kt`
- Create: `backend/src/main/kotlin/com/sigak/search/evaluation/catalog/SearchCatalogExportCommandRunner.kt`
- Test: `backend/src/test/kotlin/com/sigak/search/evaluation/catalog/SearchCatalogExportCommandRunnerTest.kt`

- [x] **Step 1: Write the failing runner test**

Create `backend/src/test/kotlin/com/sigak/search/evaluation/catalog/SearchCatalogExportCommandRunnerTest.kt`:

```kotlin
package com.sigak.search.evaluation.catalog

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sigak.article.dto.ArticleResponse
import com.sigak.collection.runner.ApplicationExit
import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.springframework.boot.DefaultApplicationArguments

class SearchCatalogExportCommandRunnerTest {

    @TempDir
    lateinit var tempDir: java.nio.file.Path

    @Test
    fun runDoesNothingWhenSearchCatalogExportCommandIsNotRequested() {
        val exit = RecordingApplicationExit()
        val output = RecordingCommandOutput()
        val runner = runner(exit = exit, output = output, articles = listOf(article(1)))

        runner.run(DefaultApplicationArguments("--server.port=8081"))

        assertEquals(emptyList(), exit.codes)
        assertEquals(emptyList(), output.lines)
        assertEquals(emptyList(), output.errorLines)
    }

    @Test
    fun runExportsCatalogAndExitsZeroForSearchCatalogExportCommand() {
        val exit = RecordingApplicationExit()
        val output = RecordingCommandOutput()
        val exportPath = tempDir.resolve("raw/articles.catalog.json")
        val runner = runner(exit = exit, output = output, articles = listOf(article(1), article(2)))

        runner.run(
            DefaultApplicationArguments(
                "search-catalog-export",
                "--output=$exportPath",
                "--limit=1",
                "--catalog-id=api-ready-test"
            )
        )

        assertEquals(listOf(0), exit.codes)
        assertTrue(Files.exists(exportPath))
        assertTrue(exportPath.readText().contains("\"catalogId\" : \"api-ready-test\""))
        assertTrue(output.lines.single().contains("Search catalog export completed"))
        assertTrue(output.lines.single().contains("articleCount=1"))
        assertEquals(emptyList(), output.errorLines)
    }

    @Test
    fun runExitsOneWhenCommandArgumentsAreInvalid() {
        val exit = RecordingApplicationExit()
        val output = RecordingCommandOutput()
        val runner = runner(exit = exit, output = output, articles = listOf(article(1)))

        runner.run(DefaultApplicationArguments("search-catalog-export", "--limit=many"))

        assertEquals(listOf(1), exit.codes)
        assertEquals(emptyList(), output.lines)
        assertEquals("Search catalog export failed: search-catalog-export --limit must be a number.", output.errorLines.single())
    }

    @Test
    fun runExitsOneWhenNoApiReadyArticlesExist() {
        val exit = RecordingApplicationExit()
        val output = RecordingCommandOutput()
        val exportPath = tempDir.resolve("raw/articles.catalog.json")
        val runner = runner(exit = exit, output = output, articles = emptyList())

        runner.run(
            DefaultApplicationArguments(
                "search-catalog-export",
                "--output=$exportPath"
            )
        )

        assertEquals(listOf(1), exit.codes)
        assertEquals(emptyList(), output.lines)
        assertEquals("Search catalog export failed: No API-ready articles are available for search catalog export.", output.errorLines.single())
    }

    private fun runner(
        exit: RecordingApplicationExit,
        output: RecordingCommandOutput,
        articles: List<ArticleResponse>
    ): SearchCatalogExportCommandRunner =
        SearchCatalogExportCommandRunner(
            parser = SearchCatalogExportCommandParser(),
            service = SearchCatalogExportService(
                articleReader = SearchCatalogArticleReader { articles },
                catalogFactory = SearchCatalogFactory()
            ),
            writer = SearchCatalogExportJsonWriter(jacksonObjectMapper()),
            formatter = SearchCatalogExportCommandFormatter(),
            output = output,
            exit = exit
        )

    private fun article(id: Long): ArticleResponse =
        ArticleResponse(
            id = id,
            title = "Article $id",
            source = "Source $id",
            url = "https://example.com/articles/$id",
            publishedAt = "2026-05-07T00:00:00Z",
            eventType = "NEWS",
            primaryCategory = "AI",
            topics = listOf("topic-$id"),
            summary = "summary for $id",
            whyItMatters = "why it matters for $id",
            importanceScore = 70,
            relatedArticleIds = emptyList()
        )

    private class RecordingApplicationExit : ApplicationExit {
        val codes = mutableListOf<Int>()

        override fun exit(code: Int) {
            codes.add(code)
        }
    }

    private class RecordingCommandOutput : SearchCatalogExportCommandOutput {
        val lines = mutableListOf<String>()
        val errorLines = mutableListOf<String>()

        override fun writeLine(value: String) {
            lines.add(value)
        }

        override fun writeErrorLine(value: String) {
            errorLines.add(value)
        }
    }
}
```

- [x] **Step 2: Run runner test and verify it fails**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.search.evaluation.catalog.SearchCatalogExportCommandRunnerTest
```

Expected:

- Fails because formatter, output, and runner do not exist.

- [x] **Step 3: Add command result and formatter**

Create `backend/src/main/kotlin/com/sigak/search/evaluation/catalog/SearchCatalogExportCommandFormatter.kt`:

```kotlin
package com.sigak.search.evaluation.catalog

import org.springframework.stereotype.Component

data class SearchCatalogExportCommandResult(
    val command: SearchCatalogExportCommand,
    val catalog: SearchCatalog
)

@Component
class SearchCatalogExportCommandFormatter {

    fun format(result: SearchCatalogExportCommandResult): String =
        buildString {
            appendLine("Search catalog export completed")
            appendLine("catalogId=${result.catalog.catalogId}")
            appendLine("articleCount=${result.catalog.articles.size}")
            appendLine("output=${result.command.output}")
        }.trimEnd()

    fun formatError(exception: Throwable): String =
        "Search catalog export failed: ${exception.message ?: exception::class.simpleName.orEmpty()}"
}
```

- [x] **Step 4: Add command output abstraction**

Create `backend/src/main/kotlin/com/sigak/search/evaluation/catalog/SearchCatalogExportCommandOutput.kt`:

```kotlin
package com.sigak.search.evaluation.catalog

import org.springframework.stereotype.Component

interface SearchCatalogExportCommandOutput {
    fun writeLine(value: String)

    fun writeErrorLine(value: String)
}

@Component
class SystemSearchCatalogExportCommandOutput : SearchCatalogExportCommandOutput {

    override fun writeLine(value: String) {
        println(value)
    }

    override fun writeErrorLine(value: String) {
        System.err.println(value)
    }
}
```

- [x] **Step 5: Add command runner**

Create `backend/src/main/kotlin/com/sigak/search/evaluation/catalog/SearchCatalogExportCommandRunner.kt`:

```kotlin
package com.sigak.search.evaluation.catalog

import com.sigak.collection.runner.ApplicationExit
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class SearchCatalogExportCommandRunner(
    private val parser: SearchCatalogExportCommandParser,
    private val service: SearchCatalogExportService,
    private val writer: SearchCatalogExportJsonWriter,
    private val formatter: SearchCatalogExportCommandFormatter,
    private val output: SearchCatalogExportCommandOutput,
    private val exit: ApplicationExit
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        val command = runCatching { parser.parse(args.sourceArgs) }
            .getOrElse { exception ->
                output.writeErrorLine(formatter.formatError(exception))
                exit.exit(1)
                return
            }

        if (command == null) {
            return
        }

        runCatching {
            val catalog = service.export(command)
            writer.write(command.output, catalog)
            SearchCatalogExportCommandResult(command = command, catalog = catalog)
        }.onSuccess { result ->
            output.writeLine(formatter.format(result))
            exit.exit(0)
        }.onFailure { exception ->
            output.writeErrorLine(formatter.formatError(exception))
            exit.exit(1)
        }
    }
}
```

- [x] **Step 6: Run runner test and verify it passes**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.search.evaluation.catalog.SearchCatalogExportCommandRunnerTest
```

Expected:

- `BUILD SUCCESSFUL`

- [x] **Step 7: Run all catalog tests**

Run:

```bash
cd backend
./gradlew test --tests 'com.sigak.search.evaluation.catalog.*'
```

Expected:

- `BUILD SUCCESSFUL`

## Task 6: Experiments Directory And User Documentation

**Files:**
- Create: `experiments/README.md`
- Create: `experiments/datasets/raw/.gitkeep`
- Create: `experiments/datasets/labels/.gitkeep`
- Create: `experiments/datasets/processed/.gitkeep`
- Modify: `docs/search-evaluation/queries.md`

- [x] **Step 1: Create experiments directory markers**

Create these empty files:

```text
experiments/datasets/raw/.gitkeep
experiments/datasets/labels/.gitkeep
experiments/datasets/processed/.gitkeep
```

Expected:

- Git can track the dataset directories without requiring generated data files.

- [x] **Step 2: Add experiments README**

Create `experiments/README.md`:

````markdown
# Sigak Experiments

이 디렉터리는 Sigak의 검색/RAG 평가 artifact를 보관한다.
현재 단계의 목표는 대규모 benchmark가 아니라, 작은 query set과 사람이 검토한 relevance label로 keyword, vector, hybrid search를 재현 가능하게 비교하는 것이다.

## Directory Structure

```text
experiments/
├── datasets/
│   ├── raw/        API-ready article catalog 같은 원천 평가 artifact
│   ├── labels/     사람이 작성한 query/article relevance label JSON
│   └── processed/  benchmark runner가 사용할 가공 dataset
└── results/        benchmark 실행 결과
```

`results/`는 benchmark runner가 생긴 뒤 만든다.

## Export Article Catalog

PostgreSQL의 API-ready article을 라벨링 도구가 import할 수 있는 catalog JSON으로 export한다.

```bash
cd backend
./gradlew bootRun --args='search-catalog-export --output=../experiments/datasets/raw/articles.catalog.json --limit=50'
```

옵션:

- `--output`: catalog JSON을 저장할 경로다.
- `--limit`: export할 최대 article 수다. 기본값은 `50`이다.
- `--catalog-id`: catalog 식별자다. 생략하면 `api-ready-YYYY-MM-DD` 형태를 사용한다.

## Labeling Flow

```text
catalog export
-> docs/search-evaluation/labeling.html 열기
-> Catalog JSON 가져오기
-> query별 relevance label 작성
-> label JSON 다운로드
-> experiments/datasets/labels/ 아래에 보관
```

현재 benchmark runner는 아직 없다.
label JSON을 만든 뒤 keyword/vector/hybrid 검색 결과를 비교하는 runner를 추가한다.
````

- [x] **Step 3: Update search evaluation guide with export flow**

In `docs/search-evaluation/queries.md`, add this section after `## HTML 라벨링 도구`:

````markdown
## Frozen catalog export

Seed article 5개는 도구 smoke check에는 충분하지만 검색 품질 benchmark에는 부족합니다.
더 많은 article을 라벨링하려면 PostgreSQL의 API-ready article을 frozen catalog JSON으로 export한 뒤 HTML 도구에서 가져옵니다.

```bash
cd backend
./gradlew bootRun --args='search-catalog-export --output=../experiments/datasets/raw/articles.catalog.json --limit=50'
```

그 다음 `docs/search-evaluation/labeling.html`을 열고 `카탈로그 JSON 가져오기` 버튼으로 `experiments/datasets/raw/articles.catalog.json`을 선택합니다.

이 catalog는 Elasticsearch, Qdrant, Neo4j가 아니라 PostgreSQL source of truth에서 나온 public/API-ready article 기준입니다.
````

- [x] **Step 4: Run documentation whitespace check**

Run:

```bash
git diff --check -- experiments/README.md docs/search-evaluation/queries.md
```

Expected:

- No output.

## Task 7: Status, Roadmap, Dev-log, And Topic Queue

**Files:**
- Modify: `docs/STATUS.md`
- Modify: `docs/STATUS.ko.md`
- Modify: `docs/ROADMAP.md`
- Modify: `docs/ROADMAP.ko.md`
- Modify: `docs/blog/2026-06-02-dev-log.md`
- Modify: `docs/blog/topic-queue.md`

- [x] **Step 1: Update status documents**

In `docs/STATUS.md` and `docs/STATUS.ko.md`:

- Keep `Last updated: 2026-06-02`.
- Add that the frozen catalog export command is implemented after implementation is verified.
- Record the exact commands that were run.
- If full backend checks are not run, mark them as unverified.

Use wording like:

```markdown
Search evaluation now has a command-runner design for exporting API-ready PostgreSQL articles into a static labeling catalog. Implementation verification is recorded in the recent verification table when the backend tests and export smoke run pass.
```

Use the Korean equivalent in `.ko.md`.

- [x] **Step 2: Update roadmap documents**

In `docs/ROADMAP.md` and `docs/ROADMAP.ko.md`:

- Under Research Track R1, mark `Create experiments/README.md` complete after the file exists.
- Add or adjust the catalog export item so it says catalog JSON, not JSONL, if the roadmap still says JSONL for this specific search labeling flow.
- Leave benchmark runner and 30-50 labeled examples incomplete.

- [x] **Step 3: Update dev-log**

Append to `docs/blog/2026-06-02-dev-log.md`:

```markdown
## 추가 진행: API-ready article catalog export 설계/구현 준비

### 요약

검색 평가 라벨링 도구가 seed article 5개에 머무르지 않도록, PostgreSQL의 API-ready article을 frozen catalog JSON으로 export하는 command runner 설계와 구현 계획을 정리했다.

### 설계 결정

- public/internal HTTP endpoint 대신 Spring Boot command runner를 사용한다.
- catalog 기준은 Elasticsearch/Qdrant/Neo4j projection이 아니라 PostgreSQL source of truth와 `ArticleService.getArticles(null)`의 API-ready response다.
- benchmark runner와 metric 계산은 다음 단계로 남긴다.

### 검증

이번 항목에는 구현 후 실제 실행한 backend test, `git diff --check`, catalog JSON parse 결과만 기록한다.
실행하지 않은 suite는 `미검증`으로 남긴다.
```

After implementation, replace the last paragraph with actual command results.

- [x] **Step 4: Update topic queue**

In `docs/blog/topic-queue.md`, add or reinforce the existing retrieval evaluation candidate with:

```markdown
- API-ready article을 frozen catalog로 export해 라벨링 도구와 benchmark runner 사이의 dataset artifact 경계를 만들었다.
- source of truth와 projection store를 평가 dataset 생성에서도 분리했다.
- public API와 같은 API-ready 기준을 재사용해 평가 catalog와 사용자 노출 article 기준이 갈라지지 않게 했다.
```

- [x] **Step 5: Run docs check**

Run:

```bash
git diff --check -- docs/STATUS.md docs/STATUS.ko.md docs/ROADMAP.md docs/ROADMAP.ko.md docs/blog/2026-06-02-dev-log.md docs/blog/topic-queue.md
```

Expected:

- No output.

## Task 8: Full Verification And Local Export Smoke

**Files:**
- Verify: backend tests
- Verify: generated catalog JSON
- Verify: docs/static checks

- [x] **Step 1: Run focused catalog tests**

Run:

```bash
cd backend
./gradlew test --tests 'com.sigak.search.evaluation.catalog.*'
```

Expected:

- `BUILD SUCCESSFUL`

- [x] **Step 2: Run backend full test suite**

Run:

```bash
cd backend
./gradlew test
```

Expected:

- `BUILD SUCCESSFUL`

- [x] **Step 3: Run backend check**

Run:

```bash
cd backend
./gradlew check
```

Expected:

- `BUILD SUCCESSFUL`

- [x] **Step 4: Run export smoke if PostgreSQL is available**

From repository root:

```bash
docker compose -f infra/docker-compose.yml up -d --pull never postgres
until docker compose -f infra/docker-compose.yml exec -T postgres pg_isready -U sigak -d sigak; do sleep 1; done
cd backend
./gradlew bootRun --args='search-catalog-export --output=../experiments/datasets/raw/articles.catalog.json --limit=50 --catalog-id=api-ready-smoke'
```

Expected output includes:

```text
sigak-postgres:5432 - accepting connections
```

and:

```text
Search catalog export completed
catalogId=api-ready-smoke
articleCount=
output=../experiments/datasets/raw/articles.catalog.json
```

If Docker/PostgreSQL is unavailable, do not claim export smoke passed.
Record it as `미검증` with the exact blocker.

- [x] **Step 5: Parse generated catalog JSON**

From repository root:

```bash
node -e "const fs=require('fs'); const c=JSON.parse(fs.readFileSync('experiments/datasets/raw/articles.catalog.json','utf8')); if(c.version!==1) throw new Error('bad version'); if(c.catalogId!=='api-ready-smoke') throw new Error('bad catalogId'); if(!Array.isArray(c.articles)||c.articles.length===0) throw new Error('missing articles'); for (const a of c.articles) { for (const k of ['id','title','category','topics','summaryKo']) { if (a[k] === undefined || a[k] === null || a[k] === '') throw new Error('missing '+k); } } console.log(c.catalogId, c.articles.length);"
```

Expected:

- Prints `api-ready-smoke <count>`.
- Exit code `0`.

- [x] **Step 6: Decide whether to keep generated catalog JSON tracked**

Check:

```bash
git status --short -- experiments/datasets/raw/articles.catalog.json
```

Decision rule:

- If the generated catalog only contains public API-level metadata/summary and is useful as a frozen artifact, keep it for user review.
- If it contains collected external article content that should not be committed, remove the generated file and keep only `.gitkeep`.
- Do not delete user-created label JSON files.

- [x] **Step 7: Run final whitespace check**

From repository root:

```bash
git diff --check
```

Expected:

- No output.

- [x] **Step 8: Final git status review**

Run:

```bash
git status --short
```

Expected:

- Only intentional backend, experiments, docs, and prior approved search-labeling files are modified/untracked.
- Do not stage, commit, or push unless the user explicitly asks.

## Execution Handoff

Recommended execution mode:

1. Subagent-Driven
   - Best fit because parser, factory/service, writer/runner, docs, and verification can be reviewed between tasks.
2. Inline Execution
   - Acceptable if the user wants fewer handoffs and direct implementation in this session.

When executing, follow the repository lifecycle:

```text
Plan -> TDD implementation -> focused tests -> full backend checks -> export smoke -> docs/status/dev-log/topic queue -> report verified vs 미검증
```
