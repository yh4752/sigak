# Collection Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Phase 5 foundation for selected-source article collection, normalization, and mock LLM enrichment.

**Architecture:** Spring Boot owns source registry, collector parsing, normalization, and orchestration. FastAPI owns AI enrichment and starts with deterministic mock responses so local development does not require paid API keys. The public article API remains stable while collected source data and enrichment output are modeled separately.

**Tech Stack:** Kotlin, Spring Boot 3.3, JUnit 5, Java XML DOM parser, Python, FastAPI, Pydantic, Pytest

---

## File Structure

```txt
backend/src/main/kotlin/com/sigak/collection/
├── domain/
│   ├── ArticleEnrichment.kt
│   ├── CollectedArticle.kt
│   ├── CollectionStatus.kt
│   ├── NewsSource.kt
│   └── SourceType.kt
├── dto/
│   ├── EnrichmentRequest.kt
│   └── EnrichmentResponse.kt
├── service/
│   ├── ArticleNormalizer.kt
│   ├── CollectionPipelineService.kt
│   └── SourceRegistry.kt
└── collector/
    ├── ArxivCollector.kt
    ├── RssAtomCollector.kt
    └── XmlText.kt

backend/src/test/kotlin/com/sigak/collection/
├── collector/
│   ├── ArxivCollectorTest.kt
│   └── RssAtomCollectorTest.kt
└── service/
    ├── ArticleNormalizerTest.kt
    ├── CollectionPipelineServiceTest.kt
    └── SourceRegistryTest.kt

ai/
├── requirements.txt
├── app/
│   ├── __init__.py
│   ├── main.py
│   ├── routers/
│   │   ├── __init__.py
│   │   └── enrichment.py
│   ├── schemas/
│   │   ├── __init__.py
│   │   └── enrichment.py
│   └── services/
│       ├── __init__.py
│       └── mock_enrichment_service.py
└── tests/
    └── test_enrichment_router.py
```

---

## Task 1: Commit Approved Phase 5 Design Docs

**Files:**
- Stage: `docs/ROADMAP.md`
- Stage: `docs/PRODUCT.md`
- Stage: `docs/SOURCE_POLICY.md`
- Stage: `docs/decisions/0003-collection-and-enrichment-pipeline.md`
- Stage: `docs/superpowers/specs/2026-05-05-collection-foundation-design.md`
- Stage: `docs/superpowers/plans/2026-05-05-collection-foundation.md`

- [ ] **Step 1: Review staged scope**

Run:

```bash
git status --short
```

Expected: only the Phase 5 docs and this plan are modified or untracked.

- [ ] **Step 2: Check patch formatting**

Run:

```bash
git diff --check
```

Expected: no output and exit code 0.

- [ ] **Step 3: Commit design and plan docs**

Run:

```bash
git add docs/ROADMAP.md docs/PRODUCT.md docs/SOURCE_POLICY.md docs/decisions/0003-collection-and-enrichment-pipeline.md docs/superpowers/specs/2026-05-05-collection-foundation-design.md docs/superpowers/plans/2026-05-05-collection-foundation.md
git commit -m "docs: define collection foundation phase"
```

Expected: commit succeeds.

---

## Task 2: Collection Domain Model

**Files:**
- Create: `backend/src/main/kotlin/com/sigak/collection/domain/SourceType.kt`
- Create: `backend/src/main/kotlin/com/sigak/collection/domain/CollectionStatus.kt`
- Create: `backend/src/main/kotlin/com/sigak/collection/domain/NewsSource.kt`
- Create: `backend/src/main/kotlin/com/sigak/collection/domain/CollectedArticle.kt`
- Create: `backend/src/main/kotlin/com/sigak/collection/domain/ArticleEnrichment.kt`
- Create: `backend/src/test/kotlin/com/sigak/collection/service/SourceRegistryTest.kt`

- [ ] **Step 1: Write the failing source registry test**

Create `backend/src/test/kotlin/com/sigak/collection/service/SourceRegistryTest.kt`:

```kotlin
package com.sigak.collection.service

import com.sigak.collection.domain.SourceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SourceRegistryTest {

    private val sourceRegistry = SourceRegistry()

    @Test
    fun sourcesIncludeOfficialFeedsAndArxivResearchSources() {
        val sources = sourceRegistry.sources()

        assertTrue(sources.any { it.name == "OpenAI Blog" && it.type == SourceType.RSS_ATOM })
        assertTrue(sources.any { it.name == "Google AI Blog" && it.type == SourceType.RSS_ATOM })
        assertTrue(sources.any { it.name == "arXiv cs.AI" && it.type == SourceType.ARXIV })
        assertTrue(sources.any { it.name == "arXiv cs.LG" && it.type == SourceType.ARXIV })
        assertTrue(sources.none { it.name.contains("Hacker News", ignoreCase = true) })
    }

    @Test
    fun sourcesHaveStableIdentifiers() {
        val ids = sourceRegistry.sources().map { it.id }

        assertEquals(ids.distinct(), ids)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd backend && ./gradlew test --tests "com.sigak.collection.service.SourceRegistryTest"
```

Expected: FAIL because collection domain files and `SourceRegistry` do not exist.

- [ ] **Step 3: Add collection domain files**

Create `backend/src/main/kotlin/com/sigak/collection/domain/SourceType.kt`:

```kotlin
package com.sigak.collection.domain

enum class SourceType {
    RSS_ATOM,
    ARXIV,
    MANUAL
}
```

Create `backend/src/main/kotlin/com/sigak/collection/domain/CollectionStatus.kt`:

```kotlin
package com.sigak.collection.domain

enum class CollectionStatus {
    DISCOVERED,
    FETCHED,
    EXTRACTED,
    NORMALIZED,
    ENRICHED,
    PUBLISHED,
    FAILED
}
```

Create `backend/src/main/kotlin/com/sigak/collection/domain/NewsSource.kt`:

```kotlin
package com.sigak.collection.domain

data class NewsSource(
    val id: String,
    val name: String,
    val type: SourceType,
    val url: String,
    val categoryHint: String? = null
)
```

Create `backend/src/main/kotlin/com/sigak/collection/domain/CollectedArticle.kt`:

```kotlin
package com.sigak.collection.domain

data class CollectedArticle(
    val sourceName: String,
    val sourceType: SourceType,
    val externalId: String,
    val url: String,
    val canonicalUrl: String,
    val title: String,
    val publishedAt: String,
    val authorNames: List<String>,
    val rawContent: String,
    val extractedText: String,
    val status: CollectionStatus = CollectionStatus.EXTRACTED
)
```

Create `backend/src/main/kotlin/com/sigak/collection/domain/ArticleEnrichment.kt`:

```kotlin
package com.sigak.collection.domain

data class ArticleEnrichment(
    val summary: String,
    val whyItMatters: String,
    val suggestedTopics: List<String>,
    val suggestedPrimaryCategory: String,
    val suggestedImportanceScore: Int,
    val modelName: String,
    val promptVersion: String
)
```

- [ ] **Step 4: Add source registry**

Create `backend/src/main/kotlin/com/sigak/collection/service/SourceRegistry.kt`:

```kotlin
package com.sigak.collection.service

import com.sigak.collection.domain.NewsSource
import com.sigak.collection.domain.SourceType
import org.springframework.stereotype.Component

@Component
class SourceRegistry {

    fun sources(): List<NewsSource> = listOf(
        NewsSource(
            id = "openai-blog",
            name = "OpenAI Blog",
            type = SourceType.RSS_ATOM,
            url = "https://openai.com/news/rss.xml",
            categoryHint = "AI"
        ),
        NewsSource(
            id = "google-ai-blog",
            name = "Google AI Blog",
            type = SourceType.RSS_ATOM,
            url = "https://blog.google/technology/ai/rss/",
            categoryHint = "AI"
        ),
        NewsSource(
            id = "github-blog",
            name = "GitHub Blog",
            type = SourceType.RSS_ATOM,
            url = "https://github.blog/feed/",
            categoryHint = "DEVTOOLS"
        ),
        NewsSource(
            id = "arxiv-cs-ai",
            name = "arXiv cs.AI",
            type = SourceType.ARXIV,
            url = "https://export.arxiv.org/api/query?search_query=cat:cs.AI&sortBy=submittedDate&sortOrder=descending&max_results=10",
            categoryHint = "CS_RESEARCH"
        ),
        NewsSource(
            id = "arxiv-cs-lg",
            name = "arXiv cs.LG",
            type = SourceType.ARXIV,
            url = "https://export.arxiv.org/api/query?search_query=cat:cs.LG&sortBy=submittedDate&sortOrder=descending&max_results=10",
            categoryHint = "CS_RESEARCH"
        ),
        NewsSource(
            id = "arxiv-cs-cl",
            name = "arXiv cs.CL",
            type = SourceType.ARXIV,
            url = "https://export.arxiv.org/api/query?search_query=cat:cs.CL&sortBy=submittedDate&sortOrder=descending&max_results=10",
            categoryHint = "CS_RESEARCH"
        )
    )
}
```

- [ ] **Step 5: Run test to verify it passes**

Run:

```bash
cd backend && ./gradlew test --tests "com.sigak.collection.service.SourceRegistryTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

Run:

```bash
git add backend/src/main/kotlin/com/sigak/collection backend/src/test/kotlin/com/sigak/collection/service/SourceRegistryTest.kt
git commit -m "feat: add collection source registry"
```

---

## Task 3: RSS/Atom Collector Parser

**Files:**
- Create: `backend/src/main/kotlin/com/sigak/collection/collector/XmlText.kt`
- Create: `backend/src/main/kotlin/com/sigak/collection/collector/RssAtomCollector.kt`
- Create: `backend/src/test/kotlin/com/sigak/collection/collector/RssAtomCollectorTest.kt`

- [ ] **Step 1: Write the failing RSS/Atom parser test**

Create `backend/src/test/kotlin/com/sigak/collection/collector/RssAtomCollectorTest.kt`:

```kotlin
package com.sigak.collection.collector

import com.sigak.collection.domain.NewsSource
import com.sigak.collection.domain.SourceType
import kotlin.test.Test
import kotlin.test.assertEquals

class RssAtomCollectorTest {

    private val collector = RssAtomCollector()

    private val source = NewsSource(
        id = "example-feed",
        name = "Example Engineering Blog",
        type = SourceType.RSS_ATOM,
        url = "https://example.com/feed.xml",
        categoryHint = "SOFTWARE_ENGINEERING"
    )

    @Test
    fun parseRssFeedItemIntoCollectedArticle() {
        val xml = """
            <rss version="2.0">
              <channel>
                <item>
                  <guid>https://example.com/articles/reliable-builds</guid>
                  <title>Reliable Builds for AI Toolchains</title>
                  <link>https://example.com/articles/reliable-builds</link>
                  <pubDate>Tue, 05 May 2026 09:00:00 GMT</pubDate>
                  <description>Build systems need stronger provenance as AI coding tools grow.</description>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val articles = collector.parse(source, xml)

        assertEquals(1, articles.size)
        assertEquals("Example Engineering Blog", articles[0].sourceName)
        assertEquals(SourceType.RSS_ATOM, articles[0].sourceType)
        assertEquals("https://example.com/articles/reliable-builds", articles[0].externalId)
        assertEquals("Reliable Builds for AI Toolchains", articles[0].title)
        assertEquals("Build systems need stronger provenance as AI coding tools grow.", articles[0].extractedText)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd backend && ./gradlew test --tests "com.sigak.collection.collector.RssAtomCollectorTest"
```

Expected: FAIL because `RssAtomCollector` does not exist.

- [ ] **Step 3: Add XML helper**

Create `backend/src/main/kotlin/com/sigak/collection/collector/XmlText.kt`:

```kotlin
package com.sigak.collection.collector

import org.w3c.dom.Element
import org.w3c.dom.Node
import javax.xml.parsers.DocumentBuilderFactory

internal fun secureDocumentBuilderFactory(namespaceAware: Boolean = false): DocumentBuilderFactory =
    DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = namespaceAware
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        isExpandEntityReferences = false
    }

internal fun Element.firstText(tagName: String): String? {
    val nodes = getElementsByTagName(tagName)
    if (nodes.length == 0) {
        return null
    }
    return nodes.item(0).textContent?.trim()?.takeIf { it.isNotBlank() }
}

internal fun Node.asElement(): Element? = this as? Element
```

- [ ] **Step 4: Add RSS/Atom collector**

Create `backend/src/main/kotlin/com/sigak/collection/collector/RssAtomCollector.kt`:

```kotlin
package com.sigak.collection.collector

import com.sigak.collection.domain.CollectedArticle
import com.sigak.collection.domain.NewsSource
import java.io.ByteArrayInputStream
import org.springframework.stereotype.Component
import org.w3c.dom.Element

@Component
class RssAtomCollector {

    fun parse(source: NewsSource, xml: String): List<CollectedArticle> {
        val document = secureDocumentBuilderFactory()
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(xml.toByteArray()))

        val rssItems = document.getElementsByTagName("item")
        if (rssItems.length > 0) {
            return (0 until rssItems.length)
                .mapNotNull { rssItems.item(it).asElement() }
                .mapNotNull { item -> item.toRssArticle(source) }
        }

        val atomEntries = document.getElementsByTagName("entry")
        return (0 until atomEntries.length)
            .mapNotNull { atomEntries.item(it).asElement() }
            .mapNotNull { entry -> entry.toAtomArticle(source) }
    }

    private fun Element.toRssArticle(source: NewsSource): CollectedArticle? {
        val title = firstText("title") ?: return null
        val link = firstText("link") ?: return null
        val externalId = firstText("guid") ?: link
        val publishedAt = firstText("pubDate") ?: ""
        val description = firstText("description") ?: ""

        return CollectedArticle(
            sourceName = source.name,
            sourceType = source.type,
            externalId = externalId,
            url = link,
            canonicalUrl = link,
            title = title,
            publishedAt = publishedAt,
            authorNames = emptyList(),
            rawContent = description,
            extractedText = description
        )
    }

    private fun Element.toAtomArticle(source: NewsSource): CollectedArticle? {
        val title = firstText("title") ?: return null
        val id = firstText("id") ?: return null
        val publishedAt = firstText("published") ?: firstText("updated") ?: ""
        val summary = firstText("summary") ?: ""
        val link = atomLink() ?: id

        return CollectedArticle(
            sourceName = source.name,
            sourceType = source.type,
            externalId = id,
            url = link,
            canonicalUrl = link,
            title = title,
            publishedAt = publishedAt,
            authorNames = emptyList(),
            rawContent = summary,
            extractedText = summary
        )
    }

    private fun Element.atomLink(): String? {
        val links = getElementsByTagName("link")
        return (0 until links.length)
            .mapNotNull { links.item(it).asElement() }
            .firstOrNull { it.getAttribute("rel").ifBlank { "alternate" } == "alternate" }
            ?.getAttribute("href")
            ?.takeIf { it.isNotBlank() }
    }
}
```

- [ ] **Step 5: Run RSS/Atom collector test**

Run:

```bash
cd backend && ./gradlew test --tests "com.sigak.collection.collector.RssAtomCollectorTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

Run:

```bash
git add backend/src/main/kotlin/com/sigak/collection/collector backend/src/test/kotlin/com/sigak/collection/collector/RssAtomCollectorTest.kt
git commit -m "feat: add rss atom collector parser"
```

---

## Task 4: arXiv Collector Parser

**Files:**
- Create: `backend/src/main/kotlin/com/sigak/collection/collector/ArxivCollector.kt`
- Create: `backend/src/test/kotlin/com/sigak/collection/collector/ArxivCollectorTest.kt`

- [ ] **Step 1: Write the failing arXiv parser test**

Create `backend/src/test/kotlin/com/sigak/collection/collector/ArxivCollectorTest.kt`:

```kotlin
package com.sigak.collection.collector

import com.sigak.collection.domain.NewsSource
import com.sigak.collection.domain.SourceType
import kotlin.test.Test
import kotlin.test.assertEquals

class ArxivCollectorTest {

    private val collector = ArxivCollector()

    private val source = NewsSource(
        id = "arxiv-cs-ai",
        name = "arXiv cs.AI",
        type = SourceType.ARXIV,
        url = "https://export.arxiv.org/api/query?search_query=cat:cs.AI",
        categoryHint = "CS_RESEARCH"
    )

    @Test
    fun parseArxivEntryIntoCollectedArticle() {
        val xml = """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry>
                <id>http://arxiv.org/abs/2605.00001v1</id>
                <published>2026-05-05T00:00:00Z</published>
                <title>Evaluating Retrieval Agents</title>
                <summary>We study retrieval agents in technical knowledge workflows.</summary>
                <author><name>Ada Lovelace</name></author>
                <author><name>Grace Hopper</name></author>
                <link href="http://arxiv.org/abs/2605.00001v1" rel="alternate" type="text/html"/>
              </entry>
            </feed>
        """.trimIndent()

        val articles = collector.parse(source, xml)

        assertEquals(1, articles.size)
        assertEquals("arXiv cs.AI", articles[0].sourceName)
        assertEquals(SourceType.ARXIV, articles[0].sourceType)
        assertEquals("http://arxiv.org/abs/2605.00001v1", articles[0].externalId)
        assertEquals("Evaluating Retrieval Agents", articles[0].title)
        assertEquals(listOf("Ada Lovelace", "Grace Hopper"), articles[0].authorNames)
        assertEquals("We study retrieval agents in technical knowledge workflows.", articles[0].extractedText)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd backend && ./gradlew test --tests "com.sigak.collection.collector.ArxivCollectorTest"
```

Expected: FAIL because `ArxivCollector` does not exist.

- [ ] **Step 3: Add arXiv collector**

Create `backend/src/main/kotlin/com/sigak/collection/collector/ArxivCollector.kt`:

```kotlin
package com.sigak.collection.collector

import com.sigak.collection.domain.CollectedArticle
import com.sigak.collection.domain.NewsSource
import java.io.ByteArrayInputStream
import org.springframework.stereotype.Component
import org.w3c.dom.Element

@Component
class ArxivCollector {

    fun parse(source: NewsSource, xml: String): List<CollectedArticle> {
        val document = secureDocumentBuilderFactory(namespaceAware = true)
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(xml.toByteArray()))

        val entries = document.getElementsByTagNameNS("http://www.w3.org/2005/Atom", "entry")

        return (0 until entries.length)
            .mapNotNull { entries.item(it).asElement() }
            .mapNotNull { entry -> entry.toCollectedArticle(source) }
    }

    private fun Element.toCollectedArticle(source: NewsSource): CollectedArticle? {
        val title = firstText("title")?.replace(Regex("\\s+"), " ") ?: return null
        val id = firstText("id") ?: return null
        val publishedAt = firstText("published") ?: firstText("updated") ?: ""
        val summary = firstText("summary")?.replace(Regex("\\s+"), " ") ?: ""
        val link = atomLink() ?: id

        return CollectedArticle(
            sourceName = source.name,
            sourceType = source.type,
            externalId = id,
            url = link,
            canonicalUrl = link,
            title = title,
            publishedAt = publishedAt,
            authorNames = authorNames(),
            rawContent = summary,
            extractedText = summary
        )
    }

    private fun Element.authorNames(): List<String> {
        val authors = getElementsByTagName("author")
        return (0 until authors.length)
            .mapNotNull { authors.item(it).asElement() }
            .mapNotNull { author -> author.firstText("name") }
    }

    private fun Element.atomLink(): String? {
        val links = getElementsByTagName("link")
        return (0 until links.length)
            .mapNotNull { links.item(it).asElement() }
            .firstOrNull { it.getAttribute("rel").ifBlank { "alternate" } == "alternate" }
            ?.getAttribute("href")
            ?.takeIf { it.isNotBlank() }
    }
}
```

- [ ] **Step 4: Run arXiv collector test**

Run:

```bash
cd backend && ./gradlew test --tests "com.sigak.collection.collector.ArxivCollectorTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

Run:

```bash
git add backend/src/main/kotlin/com/sigak/collection/collector/ArxivCollector.kt backend/src/test/kotlin/com/sigak/collection/collector/ArxivCollectorTest.kt
git commit -m "feat: add arxiv collector parser"
```

---

## Task 5: Normalize Collected Articles for Enrichment

**Files:**
- Create: `backend/src/main/kotlin/com/sigak/collection/dto/EnrichmentRequest.kt`
- Create: `backend/src/main/kotlin/com/sigak/collection/dto/EnrichmentResponse.kt`
- Create: `backend/src/main/kotlin/com/sigak/collection/service/ArticleNormalizer.kt`
- Create: `backend/src/test/kotlin/com/sigak/collection/service/ArticleNormalizerTest.kt`

- [ ] **Step 1: Write the failing normalizer test**

Create `backend/src/test/kotlin/com/sigak/collection/service/ArticleNormalizerTest.kt`:

```kotlin
package com.sigak.collection.service

import com.sigak.collection.domain.CollectedArticle
import com.sigak.collection.domain.SourceType
import kotlin.test.Test
import kotlin.test.assertEquals

class ArticleNormalizerTest {

    private val normalizer = ArticleNormalizer()

    @Test
    fun normalizeCollectedArticleIntoEnrichmentRequest() {
        val collectedArticle = CollectedArticle(
            sourceName = "arXiv cs.AI",
            sourceType = SourceType.ARXIV,
            externalId = "http://arxiv.org/abs/2605.00001v1",
            url = "http://arxiv.org/abs/2605.00001v1",
            canonicalUrl = "http://arxiv.org/abs/2605.00001v1",
            title = "Evaluating Retrieval Agents",
            publishedAt = "2026-05-05T00:00:00Z",
            authorNames = listOf("Ada Lovelace"),
            rawContent = "We study retrieval agents.",
            extractedText = "We study retrieval agents."
        )

        val request = normalizer.toEnrichmentRequest(collectedArticle)

        assertEquals("Evaluating Retrieval Agents", request.title)
        assertEquals("arXiv cs.AI", request.source)
        assertEquals("http://arxiv.org/abs/2605.00001v1", request.url)
        assertEquals("2026-05-05T00:00:00Z", request.publishedAt)
        assertEquals(listOf("CS_RESEARCH"), request.topics)
        assertEquals("We study retrieval agents.", request.rawContent)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd backend && ./gradlew test --tests "com.sigak.collection.service.ArticleNormalizerTest"
```

Expected: FAIL because `ArticleNormalizer` and enrichment DTOs do not exist.

- [ ] **Step 3: Add enrichment DTOs**

Create `backend/src/main/kotlin/com/sigak/collection/dto/EnrichmentRequest.kt`:

```kotlin
package com.sigak.collection.dto

data class EnrichmentRequest(
    val title: String,
    val source: String,
    val url: String,
    val publishedAt: String,
    val topics: List<String>,
    val rawContent: String
)
```

Create `backend/src/main/kotlin/com/sigak/collection/dto/EnrichmentResponse.kt`:

```kotlin
package com.sigak.collection.dto

data class EnrichmentResponse(
    val summary: String,
    val whyItMatters: String,
    val suggestedTopics: List<String>,
    val suggestedPrimaryCategory: String,
    val suggestedImportanceScore: Int
)
```

- [ ] **Step 4: Add article normalizer**

Create `backend/src/main/kotlin/com/sigak/collection/service/ArticleNormalizer.kt`:

```kotlin
package com.sigak.collection.service

import com.sigak.collection.domain.CollectedArticle
import com.sigak.collection.domain.SourceType
import com.sigak.collection.dto.EnrichmentRequest
import org.springframework.stereotype.Component

@Component
class ArticleNormalizer {

    fun toEnrichmentRequest(article: CollectedArticle): EnrichmentRequest =
        EnrichmentRequest(
            title = article.title.trim(),
            source = article.sourceName,
            url = article.canonicalUrl.ifBlank { article.url },
            publishedAt = article.publishedAt,
            topics = listOf(categoryFor(article)),
            rawContent = article.extractedText.ifBlank { article.rawContent }
        )

    private fun categoryFor(article: CollectedArticle): String =
        when (article.sourceType) {
            SourceType.ARXIV -> "CS_RESEARCH"
            SourceType.RSS_ATOM -> "SOFTWARE_ENGINEERING"
            SourceType.MANUAL -> "SOFTWARE_ENGINEERING"
        }
}
```

- [ ] **Step 5: Run normalizer test**

Run:

```bash
cd backend && ./gradlew test --tests "com.sigak.collection.service.ArticleNormalizerTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

Run:

```bash
git add backend/src/main/kotlin/com/sigak/collection/dto backend/src/main/kotlin/com/sigak/collection/service/ArticleNormalizer.kt backend/src/test/kotlin/com/sigak/collection/service/ArticleNormalizerTest.kt
git commit -m "feat: normalize collected articles for enrichment"
```

---

## Task 6: FastAPI Mock Enrichment Service

**Files:**
- Create: `ai/requirements.txt`
- Create: `ai/app/__init__.py`
- Create: `ai/app/main.py`
- Create: `ai/app/routers/__init__.py`
- Create: `ai/app/routers/enrichment.py`
- Create: `ai/app/schemas/__init__.py`
- Create: `ai/app/schemas/enrichment.py`
- Create: `ai/app/services/__init__.py`
- Create: `ai/app/services/mock_enrichment_service.py`
- Create: `ai/tests/test_enrichment_router.py`
- Modify: `ai/README.md`

- [ ] **Step 1: Write the failing FastAPI router test**

Create `ai/tests/test_enrichment_router.py`:

```python
from fastapi.testclient import TestClient

from app.main import app


client = TestClient(app)


def test_enrich_article_returns_mock_summary_and_insight():
    response = client.post(
        "/api/enrichment/article",
        json={
            "title": "Evaluating Retrieval Agents",
            "source": "arXiv cs.AI",
            "url": "http://arxiv.org/abs/2605.00001v1",
            "publishedAt": "2026-05-05T00:00:00Z",
            "topics": ["CS_RESEARCH"],
            "rawContent": "We study retrieval agents in technical knowledge workflows.",
        },
    )

    assert response.status_code == 200
    assert response.json() == {
        "summary": "Evaluating Retrieval Agents discusses We study retrieval agents in technical knowledge workflows.",
        "whyItMatters": "This matters because arXiv cs.AI is connected to CS_RESEARCH and may affect how technical teams understand the topic.",
        "suggestedTopics": ["CS_RESEARCH"],
        "suggestedPrimaryCategory": "CS_RESEARCH",
        "suggestedImportanceScore": 70,
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd ai && python3 -m venv .venv && . .venv/bin/activate && pip install fastapi "uvicorn[standard]" pytest httpx && pytest
```

Expected: FAIL because `app.main` does not exist.

- [ ] **Step 3: Add AI dependencies**

Create `ai/requirements.txt`:

```txt
fastapi==0.115.4
uvicorn[standard]==0.32.0
pytest==8.3.3
httpx==0.27.2
```

- [ ] **Step 4: Add enrichment schemas**

Create `ai/app/__init__.py`:

```python
```

Create `ai/app/schemas/__init__.py`:

```python
```

Create `ai/app/schemas/enrichment.py`:

```python
from pydantic import BaseModel, Field


class EnrichmentRequest(BaseModel):
    title: str = Field(min_length=1)
    source: str = Field(min_length=1)
    url: str = Field(min_length=1)
    publishedAt: str = Field(min_length=1)
    topics: list[str] = Field(default_factory=list)
    rawContent: str = Field(min_length=1)


class EnrichmentResponse(BaseModel):
    summary: str
    whyItMatters: str
    suggestedTopics: list[str]
    suggestedPrimaryCategory: str
    suggestedImportanceScore: int
```

- [ ] **Step 5: Add mock enrichment service**

Create `ai/app/services/__init__.py`:

```python
```

Create `ai/app/services/mock_enrichment_service.py`:

```python
from app.schemas.enrichment import EnrichmentRequest, EnrichmentResponse


def enrich_article(request: EnrichmentRequest) -> EnrichmentResponse:
    primary_topic = request.topics[0] if request.topics else "SOFTWARE_ENGINEERING"
    first_sentence = request.rawContent.strip().split(".")[0].strip()
    summary_text = f"{request.title} discusses {first_sentence}."

    return EnrichmentResponse(
        summary=summary_text,
        whyItMatters=(
            f"This matters because {request.source} is connected to {primary_topic} "
            "and may affect how technical teams understand the topic."
        ),
        suggestedTopics=request.topics or [primary_topic],
        suggestedPrimaryCategory=primary_topic,
        suggestedImportanceScore=70,
    )
```

- [ ] **Step 6: Add enrichment router and FastAPI app**

Create `ai/app/routers/__init__.py`:

```python
```

Create `ai/app/routers/enrichment.py`:

```python
from fastapi import APIRouter

from app.schemas.enrichment import EnrichmentRequest, EnrichmentResponse
from app.services.mock_enrichment_service import enrich_article


router = APIRouter(prefix="/api/enrichment", tags=["enrichment"])


@router.post("/article", response_model=EnrichmentResponse)
def enrich_article_endpoint(request: EnrichmentRequest) -> EnrichmentResponse:
    return enrich_article(request)
```

Create `ai/app/main.py`:

```python
from fastapi import FastAPI

from app.routers.enrichment import router as enrichment_router


app = FastAPI(title="Sigak AI Server")
app.include_router(enrichment_router)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}
```

- [ ] **Step 7: Update AI README**

Modify `ai/README.md`:

````markdown
# AI Server

The Sigak AI server uses FastAPI for AI/RAG-related capabilities.

## Responsibilities
- Article summarization
- Basic insight generation
- Future embedding and RAG workflows

## Current Status
The AI server provides a mock enrichment endpoint for local development. It does not require paid API keys.

## Local Run

```bash
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

## Tests

```bash
pytest
```

## Endpoints

```http
GET /health
POST /api/enrichment/article
```
````

- [ ] **Step 8: Run AI tests**

Run:

```bash
cd ai && . .venv/bin/activate && pip install -r requirements.txt && pytest
```

Expected: PASS.

- [ ] **Step 9: Commit**

Run:

```bash
git add ai
git commit -m "feat: add mock AI enrichment service"
```

---

## Task 7: Spring Boot Collection Pipeline Service

**Files:**
- Create: `backend/src/main/kotlin/com/sigak/collection/service/CollectionPipelineService.kt`
- Create: `backend/src/test/kotlin/com/sigak/collection/service/CollectionPipelineServiceTest.kt`

- [ ] **Step 1: Write the failing pipeline service test**

Create `backend/src/test/kotlin/com/sigak/collection/service/CollectionPipelineServiceTest.kt`:

```kotlin
package com.sigak.collection.service

import com.sigak.collection.domain.CollectedArticle
import com.sigak.collection.domain.SourceType
import com.sigak.collection.dto.EnrichmentResponse
import kotlin.test.Test
import kotlin.test.assertEquals

class CollectionPipelineServiceTest {

    private val normalizer = ArticleNormalizer()
    private val pipelineService = CollectionPipelineService(
        articleNormalizer = normalizer,
        enrichmentClient = { request ->
            EnrichmentResponse(
                summary = "${request.title} summary",
                whyItMatters = "${request.source} insight",
                suggestedTopics = request.topics,
                suggestedPrimaryCategory = request.topics.first(),
                suggestedImportanceScore = 70
            )
        }
    )

    @Test
    fun enrichCollectedArticleReturnsEnrichmentCandidate() {
        val collectedArticle = CollectedArticle(
            sourceName = "arXiv cs.AI",
            sourceType = SourceType.ARXIV,
            externalId = "http://arxiv.org/abs/2605.00001v1",
            url = "http://arxiv.org/abs/2605.00001v1",
            canonicalUrl = "http://arxiv.org/abs/2605.00001v1",
            title = "Evaluating Retrieval Agents",
            publishedAt = "2026-05-05T00:00:00Z",
            authorNames = listOf("Ada Lovelace"),
            rawContent = "We study retrieval agents.",
            extractedText = "We study retrieval agents."
        )

        val enrichment = pipelineService.enrich(collectedArticle)

        assertEquals("Evaluating Retrieval Agents summary", enrichment.summary)
        assertEquals("arXiv cs.AI insight", enrichment.whyItMatters)
        assertEquals(listOf("CS_RESEARCH"), enrichment.suggestedTopics)
        assertEquals("CS_RESEARCH", enrichment.suggestedPrimaryCategory)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd backend && ./gradlew test --tests "com.sigak.collection.service.CollectionPipelineServiceTest"
```

Expected: FAIL because `CollectionPipelineService` does not exist.

- [ ] **Step 3: Add pipeline service**

Create `backend/src/main/kotlin/com/sigak/collection/service/CollectionPipelineService.kt`:

```kotlin
package com.sigak.collection.service

import com.sigak.collection.domain.CollectedArticle
import com.sigak.collection.dto.EnrichmentRequest
import com.sigak.collection.dto.EnrichmentResponse
import org.springframework.stereotype.Service

fun interface EnrichmentClient {
    fun enrich(request: EnrichmentRequest): EnrichmentResponse
}

@Service
class CollectionPipelineService(
    private val articleNormalizer: ArticleNormalizer,
    private val enrichmentClient: EnrichmentClient
) {

    fun enrich(article: CollectedArticle): EnrichmentResponse {
        val request = articleNormalizer.toEnrichmentRequest(article)
        return enrichmentClient.enrich(request)
    }
}
```

- [ ] **Step 4: Run pipeline service test**

Run:

```bash
cd backend && ./gradlew test --tests "com.sigak.collection.service.CollectionPipelineServiceTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

Run:

```bash
git add backend/src/main/kotlin/com/sigak/collection/service/CollectionPipelineService.kt backend/src/test/kotlin/com/sigak/collection/service/CollectionPipelineServiceTest.kt
git commit -m "feat: add collection enrichment pipeline service"
```

---

## Task 8: Mock Enrichment Client Bean for Local Spring Boot

**Files:**
- Create: `backend/src/main/kotlin/com/sigak/collection/service/MockEnrichmentClient.kt`
- Create: `backend/src/test/kotlin/com/sigak/collection/service/MockEnrichmentClientTest.kt`

- [ ] **Step 1: Write the failing mock client test**

Create `backend/src/test/kotlin/com/sigak/collection/service/MockEnrichmentClientTest.kt`:

```kotlin
package com.sigak.collection.service

import com.sigak.collection.dto.EnrichmentRequest
import kotlin.test.Test
import kotlin.test.assertEquals

class MockEnrichmentClientTest {

    private val client = MockEnrichmentClient()

    @Test
    fun enrichReturnsDeterministicLocalResponse() {
        val response = client.enrich(
            EnrichmentRequest(
                title = "Evaluating Retrieval Agents",
                source = "arXiv cs.AI",
                url = "http://arxiv.org/abs/2605.00001v1",
                publishedAt = "2026-05-05T00:00:00Z",
                topics = listOf("CS_RESEARCH"),
                rawContent = "We study retrieval agents in technical knowledge workflows."
            )
        )

        assertEquals("Evaluating Retrieval Agents discusses We study retrieval agents in technical knowledge workflows.", response.summary)
        assertEquals("CS_RESEARCH", response.suggestedPrimaryCategory)
        assertEquals(70, response.suggestedImportanceScore)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd backend && ./gradlew test --tests "com.sigak.collection.service.MockEnrichmentClientTest"
```

Expected: FAIL because `MockEnrichmentClient` does not exist.

- [ ] **Step 3: Add mock enrichment client**

Create `backend/src/main/kotlin/com/sigak/collection/service/MockEnrichmentClient.kt`:

```kotlin
package com.sigak.collection.service

import com.sigak.collection.dto.EnrichmentRequest
import com.sigak.collection.dto.EnrichmentResponse
import org.springframework.stereotype.Component

@Component
class MockEnrichmentClient : EnrichmentClient {

    override fun enrich(request: EnrichmentRequest): EnrichmentResponse {
        val primaryTopic = request.topics.firstOrNull() ?: "SOFTWARE_ENGINEERING"
        val firstSentence = request.rawContent.trim().substringBefore(".").trim()

        return EnrichmentResponse(
            summary = "${request.title} discusses $firstSentence.",
            whyItMatters = "This matters because ${request.source} is connected to $primaryTopic and may affect how technical teams understand the topic.",
            suggestedTopics = request.topics.ifEmpty { listOf(primaryTopic) },
            suggestedPrimaryCategory = primaryTopic,
            suggestedImportanceScore = 70
        )
    }
}
```

- [ ] **Step 4: Run mock client test**

Run:

```bash
cd backend && ./gradlew test --tests "com.sigak.collection.service.MockEnrichmentClientTest"
```

Expected: PASS.

- [ ] **Step 5: Run full backend collection tests**

Run:

```bash
cd backend && ./gradlew test --tests "com.sigak.collection.*"
```

Expected: PASS.

- [ ] **Step 6: Commit**

Run:

```bash
git add backend/src/main/kotlin/com/sigak/collection/service/MockEnrichmentClient.kt backend/src/test/kotlin/com/sigak/collection/service/MockEnrichmentClientTest.kt
git commit -m "feat: add local mock enrichment client"
```

---

## Task 9: Documentation and Environment Notes

**Files:**
- Modify: `.env.example`
- Modify: `README.md`
- Modify: `backend/README.md`
- Modify: `docs/API_SPEC.md`
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Update root environment example**

Modify `.env.example`:

```txt
# Root environment example for local development.
# Copy to .env locally if needed. Do not commit real secrets.

SPRING_PROFILES_ACTIVE=local
BACKEND_PORT=8080
FRONTEND_PORT=5173
VITE_API_BASE_URL=http://localhost:8080
AI_PORT=8000
AI_BASE_URL=http://localhost:8000
AI_ENRICHMENT_MODE=mock
```

- [ ] **Step 2: Update backend README**

Append to `backend/README.md`:

```markdown

## Collection Foundation

The backend owns source registry, collector parsing, normalization, and collection orchestration.

Current Phase 5 collection boundaries:
- RSS/Atom source registry entries for selected official technical sources
- arXiv API source registry entries for selected research categories
- parser tests using local XML fixtures
- local mock enrichment client

The public article API remains backed by curated mock data until persistence is added.
```

- [ ] **Step 3: Update API spec with internal enrichment contract**

Append to `docs/API_SPEC.md`:

````markdown

## Internal AI Enrichment Contract

The public article API remains stable. Internally, collected articles are normalized before AI enrichment.

Spring Boot sends normalized article input to the AI service:

```http
POST /api/enrichment/article
```

```json
{
  "title": "Evaluating Retrieval Agents",
  "source": "arXiv cs.AI",
  "url": "http://arxiv.org/abs/2605.00001v1",
  "publishedAt": "2026-05-05T00:00:00Z",
  "topics": ["CS_RESEARCH"],
  "rawContent": "We study retrieval agents in technical knowledge workflows."
}
```

The AI service returns enrichment candidates:

```json
{
  "summary": "Evaluating Retrieval Agents discusses We study retrieval agents in technical knowledge workflows.",
  "whyItMatters": "This matters because arXiv cs.AI is connected to CS_RESEARCH and may affect how technical teams understand the topic.",
  "suggestedTopics": ["CS_RESEARCH"],
  "suggestedPrimaryCategory": "CS_RESEARCH",
  "suggestedImportanceScore": 70
}
```
````

- [ ] **Step 4: Update root README status**

Add this sentence to the current status section in `README.md`:

```markdown
Phase 5 defines the selected-source collection and mock enrichment foundation. The first implementation keeps RSS/Atom and arXiv parsing internal while the public article API remains stable.
```

- [ ] **Step 5: Update roadmap checkboxes**

In `docs/ROADMAP.md`, mark completed Phase 5 items:

```markdown
- [x] Define a source registry for selected technical news and research sources.
- [x] Add connector boundaries for RSS/Atom sources, arXiv API sources, and later manual/newsletter imports.
- [x] Start with selected official AI/developer sources and arXiv research categories.
- [x] Exclude Hacker News from the initial collector and treat community aggregators as optional later discovery signals.
- [x] Define the processing flow:
```

Leave enrichment persistence, real external fetching, scheduling, and indexing unchecked.

- [ ] **Step 6: Verify docs formatting**

Run:

```bash
git diff --check
```

Expected: no output and exit code 0.

- [ ] **Step 7: Commit**

Run:

```bash
git add .env.example README.md backend/README.md docs/API_SPEC.md docs/ROADMAP.md
git commit -m "docs: update collection foundation status"
```

---

## Task 10: Final Verification

**Files:**
- Verify all files changed during Phase 5 implementation.

- [ ] **Step 1: Run backend tests**

Run:

```bash
cd backend && ./gradlew test
```

Expected: all backend tests pass.

- [ ] **Step 2: Run AI tests**

Run:

```bash
cd ai && . .venv/bin/activate && pytest
```

Expected: all AI tests pass.

- [ ] **Step 3: Run patch check**

Run:

```bash
git diff --check HEAD
```

Expected: no output and exit code 0.

- [ ] **Step 4: Review git history**

Run:

```bash
git log --oneline -8
```

Expected: recent commits show small Phase 5 docs, backend collection, AI mock enrichment, and docs status commits.

---

## Self-Review

Spec coverage:
- Source registry: Task 2
- RSS/Atom connector boundary: Task 3
- arXiv connector boundary: Task 4
- Normalized LLM enrichment input: Task 5
- FastAPI mock enrichment: Task 6
- Spring Boot orchestration boundary: Task 7
- Local mock enrichment client: Task 8
- Docs and roadmap alignment: Task 9
- Verification: Task 10

The first implementation intentionally parses fixture XML instead of scheduling network collection. This keeps Phase 5 reviewable while establishing the connector and enrichment seams needed for real source fetching.
