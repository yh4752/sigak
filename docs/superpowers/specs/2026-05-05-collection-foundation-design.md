# Phase 5 Collection Foundation Design

**Date:** 2026-05-05

## Goal
Phase 5 establishes Sigak's article collection and LLM enrichment foundation.

The goal is not to build a broad crawler. The goal is to collect realistic technical news and research candidates from selected sources, normalize them into Sigak's article model, and prepare them for LLM-generated `summary`, `whyItMatters`, topics, category, and importance candidates.

## Product Direction
Sigak should follow a curated technical source model:

```txt
DISCOVER -> FETCH -> EXTRACT -> NORMALIZE -> ENRICH_WITH_LLM -> REVIEW_OR_PUBLISH -> INDEX
```

This matches the intended product shape:
- collect from reliable AI, developer, engineering, security, infrastructure, and research sources
- preserve original source attribution
- store raw or extracted content before AI processing
- treat LLM output as enrichment, not source truth
- allow later reprocessing for semantic search and Graph RAG

## Source Strategy
The initial collector should use a source registry and source-specific connectors.

Initial source types:
- RSS/Atom feeds for official blogs, release feeds, security feeds, and engineering blogs
- arXiv API queries for research sources, especially `cs.AI`, `cs.LG`, and `cs.CL`
- manual or newsletter import for curated URLs that do not have clean feeds

Hacker News is excluded from Phase 5 collection. It can be reconsidered later as a discovery or ranking signal, but it should not be treated as an original source.

## Architecture
Spring Boot remains the main application backend and owns collection orchestration, persistence, article APIs, and business rules.

FastAPI remains responsible for AI-specific enrichment. It should receive normalized article input after extraction and return suggested enrichment fields. Local development must support mock enrichment so paid API keys are not required.

Recommended boundaries:

```txt
Spring Boot
- SourceRegistry
- Collector interface
- RssAtomCollector
- ArxivCollector
- ManualImportCollector
- ArticleExtractor
- ArticleNormalizer
- CollectionService
- EnrichmentClient

FastAPI
- enrichment router
- enrichment schema
- mock enrichment service
- later LLM client
```

## Data Flow
`DISCOVER` finds candidate URLs or external IDs from a configured source.

`FETCH` retrieves the feed item, API record, or source page when allowed.

`EXTRACT` turns raw input into useful article text and metadata such as title, author names, canonical URL, published date, and raw content.

`NORMALIZE` maps the extracted result into Sigak's internal article shape.

`ENRICH_WITH_LLM` sends normalized article input to FastAPI and receives summary and insight candidates.

`REVIEW_OR_PUBLISH` decides whether the article can be shown immediately or should stay in a reviewed/curated state. The MVP may start with automatic publish for trusted sources and later add review tools if quality becomes noisy.

`INDEX` sends published articles to keyword, vector, or graph indexes later. This is not required for the first Phase 5 implementation.

## Data Model Direction
Collected source data and AI-enriched output should be separable.

Collected article fields:

```txt
sourceName
sourceType
externalId
url
canonicalUrl
title
publishedAt
authorNames
rawContent
extractedText
collectedAt
extractionStatus
```

Enrichment fields:

```txt
summary
whyItMatters
suggestedTopics
suggestedPrimaryCategory
suggestedImportanceScore
modelName
promptVersion
enrichedAt
enrichmentStatus
```

The public article API can continue to expose a stable article response. Internally, the enriched fields should remain regenerable from saved article text.

## LLM Enrichment Contract
FastAPI should eventually accept normalized article input:

```json
{
  "title": "string",
  "source": "string",
  "url": "string",
  "publishedAt": "2026-05-05T00:00:00Z",
  "topics": ["string"],
  "rawContent": "string"
}
```

FastAPI should return enrichment candidates:

```json
{
  "summary": "string",
  "whyItMatters": "string",
  "suggestedTopics": ["string"],
  "suggestedPrimaryCategory": "AI",
  "suggestedImportanceScore": 80
}
```

In Phase 5, this can be implemented with mock responses first. Real LLM calls can be added once the collection input is stable.

## Error Handling
Each pipeline step should produce an explicit status instead of hiding failures.

Examples:
- source fetch failed
- robots or source policy blocked extraction
- parsing failed
- normalized article was duplicate
- enrichment failed
- enrichment returned invalid category or empty summary

Failed articles should remain inspectable so the pipeline can be improved without losing source context.

## Testing
Phase 5 implementation should include focused tests:
- source registry validation
- RSS/Atom collector parsing with fixture data
- arXiv API response parsing with fixture data
- normalization from collected fields to Sigak article fields
- duplicate detection rules when persistence is introduced
- FastAPI mock enrichment smoke tests
- Spring Boot enrichment client tests once the AI service is wired

## MVP Scope
Included:
- source registry design
- connector architecture
- RSS/Atom and arXiv as first-class collection paths
- normalized article input for LLM enrichment
- mock enrichment before paid API use
- clear separation of source data and AI output

Deferred:
- broad open-web crawling
- Hacker News ingestion
- full admin review UI
- Elasticsearch indexing
- Qdrant indexing
- Graph RAG extraction
- personalized recommendations

## Approval Notes
This design intentionally changes the Phase 5 center of gravity from "AI summary endpoint first" to "collection and enrichment pipeline first." AI summary is still part of the pipeline, but collection, extraction, and normalization come before meaningful LLM output.
