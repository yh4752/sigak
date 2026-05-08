# 0002: Product Scope and Graph RAG Strategy

[English](0002-product-scope-and-graph-rag-strategy.md) | [한국어](0002-product-scope-and-graph-rag-strategy.ko.md)

## Status
Accepted

## Date
2026-05-05

## Context
Sigak needs a clear product identity before adding more backend, frontend, and AI features.

The project should not become a generic news reader or a simple article summarizer. The intended 1.0.0 product is a technical news insight platform focused on AI, software development, and computer science.

The team also wants Graph RAG to be part of the 1.0.0 direction, while avoiding a large Obsidian-style graph explorer before the core article and insight workflow is stable.

## Decision
Sigak 1.0.0 will focus on important technical changes, explained with context and relationships.

The product will include:
- curated technical news and updates
- article detail pages with summary, importance, and why-it-matters insight
- one `primaryCategory` plus multiple `topics` per article
- Graph RAG-ready article and concept metadata
- limited relationship-based insight or graph-backed retrieval

The product will defer:
- Obsidian-style full graph exploration
- broad automated crawling
- advanced personalized recommendations
- saved articles and user accounts

Data collection will start with curated seed data, then add selected RSS/API sources after the article model and insight format are stable.

## Consequences
- The first APIs should model article metadata in a way that can later support enrichment and graph relationships.
- Curated data must include enough fields to test insight quality, not only title and URL.
- Search can start with keyword matching, but the API and data model should not block semantic or graph-aware retrieval later.
- Source quality rules are needed before broad automated collection.
- Full graph visualization remains available as a later differentiator without slowing the 1.0.0 MVP.

## Alternatives Considered
- Full graph explorer in 1.0.0: visually compelling, but too expensive for frontend complexity, graph data quality, and MVP timing.
- Graph RAG only after 1.0.0: simpler, but weakens the product's technical differentiation and delays important data-model decisions.
- Multi-category classification for each article: flexible, but more complex for early UI and filtering than one `primaryCategory` plus multiple `topics`.
- Broad RSS/API collection first: creates volume quickly, but risks noisy data before source quality and enrichment rules are clear.
