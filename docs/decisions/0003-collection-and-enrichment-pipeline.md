# 0003: Collection and Enrichment Pipeline

## Status
Accepted

## Date
2026-05-05

## Context
Sigak needs article data before its AI summary and insight features can become meaningful. The current MVP uses curated mock articles with fields that already resemble AI-enriched output, including `summary`, `whyItMatters`, topics, category, importance score, and related article IDs.

The next phase should avoid building a broad web crawler. Unbounded crawling creates source quality, attribution, legal, extraction, and maintenance problems before the MVP has enough product stability. At the same time, the project should move beyond purely hand-written seed data so the AI pipeline can process realistic article text.

## Decision
Phase 5 will focus on a collection and LLM enrichment foundation.

Sigak will use an explicit source registry and connector-style collection. Initial source types are:
- RSS/Atom feeds from selected official AI, developer, engineering, security, and infrastructure sources
- arXiv API queries for selected research categories
- manual or newsletter import for curated links that lack stable feeds

Hacker News is excluded from the initial collector. It may be reconsidered later as a discovery or ranking signal, but it should not be stored as the original article source.

The collection pipeline should follow this processing flow:

```txt
DISCOVER -> FETCH -> EXTRACT -> NORMALIZE -> ENRICH_WITH_LLM -> REVIEW_OR_PUBLISH -> INDEX
```

Raw collected content and AI-enriched output must be stored separately enough that a saved article can be reprocessed with a new prompt, model, category rule, embedding model, or graph extraction step without fetching the original source again.

## Consequences
- Source quality is controlled before automated collection expands.
- The project can demonstrate a realistic data pipeline without overbuilding a crawler.
- LLM output becomes an enrichment layer over preserved source data, not the source of truth.
- Future Graph RAG, semantic search, and relationship extraction can reuse stored raw content.
- The frontend article contract can stay stable while the backend pipeline evolves.

## Alternatives Considered
- FastAPI AI summary first: useful for showing AI integration, but weak without realistic raw article input.
- Generic open-web crawler: broad coverage, but too risky for MVP scope, quality, and attribution.
- Hacker News as an initial source: easy API access and strong community signals, but it is an aggregator rather than an original source.
