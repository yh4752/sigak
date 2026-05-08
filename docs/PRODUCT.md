# Sigak Product

[English](PRODUCT.md) | [한국어](PRODUCT.ko.md)

Last updated: 2026-05-07

## 1. Product Definition
Sigak is an AI-powered technical news insight platform for people interested in AI, software development, and computer science.

Sigak does not aim to collect every technical article on the internet. It focuses on important technical changes and explains why they matter.

## 2. Product Goal
The 1.0.0 goal is to build a portfolio-grade MVP that shows:

- important technical news curation
- clear backend architecture
- search and article exploration
- AI-assisted summarization and insight generation
- Graph RAG-ready data modeling
- a simple, deployable local development setup

The project should be strong enough to demonstrate practical engineering judgment for internship applications by late June 2026.

## 3. MVP Priorities

1. Build a working product before adding advanced architecture.
2. Keep Spring Boot as the main backend and API boundary.
3. Use FastAPI only for AI/RAG-related capabilities.
4. Use React, TypeScript, and Vite for the frontend.
5. Keep Docker Compose local development simple.
6. Preserve raw article text and metadata for future enrichment, semantic search, and Graph RAG.
7. Document major architecture decisions as the project evolves.

## 4. Target Users
Primary users are people who want to follow important changes in AI, software development, and computer science without reading every source directly.

Examples:
- developers following AI and software engineering trends
- students interested in AI, CS, and development
- people who want to understand important technical events with context
- readers who want fewer but more meaningful technical updates

## 5. Core Value
Sigak's core value is not only summarization. The product should:

1. select important technical news
2. explain why each item matters
3. connect each item to related technical concepts, organizations, and events
4. prepare the data structure for Graph RAG-based search and insight generation

The strongest product direction is:

> Important technical changes, explained with context and relationships.

## 6. MVP Scope

Initial MVP scope:

- news article list
- news article detail
- keyword search
- AI summary for a selected article
- importance and why-it-matters insight
- simple frontend UI
- Docker Compose local setup
- clear README
- Graph RAG-ready article metadata
- limited relationship-based insight or graph-backed retrieval

Later scope is intentionally deferred until the MVP is stable:

- full Obsidian-style graph explorer
- broad multi-source automated collection
- advanced hybrid search
- broad RAG over many articles
- personalized recommendations
- user accounts
- saved articles
- advanced dashboards

## 7. Service Responsibilities

The frontend communicates primarily with the Spring Boot backend. Spring Boot owns user-facing APIs, business rules, persistence, and orchestration. FastAPI owns AI-specific work such as summarization, enrichment, embeddings, and RAG-related workflows.

Article data should preserve raw source text and processed enrichment separately. This lets Sigak reprocess existing content for semantic search, graph relationships, or improved LLM prompts without scraping the same sources again.

Selected-source collection is part of the MVP path, but broad open-web crawling is not. The collection pipeline starts from an explicit source registry, normalizes article data, and then calls FastAPI for AI enrichment.

## 8. Content Scope
Sigak focuses on AI, development, and computer science.

### Included
- important AI, development, and CS news
- official announcements from companies, labs, standards bodies, or major open source projects
- severe security issues
- influential research papers
- major releases of tools, platforms, languages, or frameworks

### Excluded For MVP
- general personal technical blog posts
- simple tutorials
- promotional content
- low-impact library updates
- rumors or unverified community discussions

Personal technical blogs may be reconsidered later only if source quality and selection rules become clear.

## 9. Event Types
Each article or item should have one primary event type.

Initial event types:

- `NEWS`: general technical news
- `OFFICIAL_ANNOUNCEMENT`: official company, lab, organization, or project announcement
- `RESEARCH`: important paper or research result
- `SECURITY`: severe security issue, vulnerability, exploit, or security incident
- `RELEASE`: major product, tool, framework, language, or platform release

Possible later event type:

- `STANDARD`: standards, specifications, PEPs, JEPs, RFCs, or similar changes

For the MVP, standards-related content can be classified as `OFFICIAL_ANNOUNCEMENT` or `RELEASE` until the need for a separate type becomes clear.

## 10. Technology Categories
Categories describe the primary technical field of an article. They should stay broad enough for simple navigation, while detailed concepts should be handled as topics or graph nodes.

Each article should have one `primaryCategory` and multiple `topics`.

Initial categories:

- `AI`
- `SECURITY`
- `SOFTWARE_ENGINEERING`
- `BACKEND`
- `FRONTEND`
- `DATA`
- `INFRA_CLOUD`
- `DEVTOOLS`
- `CS_RESEARCH`

### Category Notes
- `AI`: LLMs, agents, RAG, ML, AI products, AI platforms
- `SECURITY`: CVEs, vulnerabilities, supply chain security, AI security
- `SOFTWARE_ENGINEERING`: architecture, testing, quality, maintainability, engineering practices
- `BACKEND`: APIs, servers, distributed systems, messaging, JVM/Spring, backend frameworks
- `FRONTEND`: web platform, browsers, UI frameworks, frontend tooling
- `DATA`: databases, search, analytics, storage, data engineering
- `INFRA_CLOUD`: cloud, DevOps, SRE, Kubernetes, observability, deployment
- `DEVTOOLS`: IDEs, compilers, package managers, build tools, developer workflows
- `CS_RESEARCH`: algorithms, programming languages, operating systems, systems research, HCI, theory

## 11. Category vs Topic
Sigak should separate broad categories from detailed topics.

Example:

```txt
primaryCategory: AI
eventType: RESEARCH
topics: ["RAG", "Graph RAG", "knowledge graph", "retrieval"]
```

Example:

```txt
primaryCategory: SECURITY
eventType: SECURITY
topics: ["MCP", "remote code execution", "AI supply chain"]
```

This keeps the UI simple while allowing future Graph RAG features to use richer concepts and relationships.

## 12. Data Strategy
Sigak should use a mixed data strategy.

### MVP Start
Use manually curated mock data to prove the product structure:
- article list
- article detail
- importance
- summary
- why it matters
- primary categories
- topics
- related items

### Later MVP
Add RSS/API-based collection for selected sources, including official technical sources and selected research sources.

### Important Principle
The pipeline should avoid forcing future re-collection when semantic search or Graph RAG is added.

Store raw and processed article data separately so existing content can be reprocessed later:

```txt
DISCOVER -> FETCH -> EXTRACT -> NORMALIZE -> ENRICH_WITH_LLM -> REVIEW_OR_PUBLISH -> INDEX
```

Graph RAG should be added through reprocessing saved article text and metadata, not by scraping the same content again.

Phase 5 should establish this collection and enrichment foundation before focusing on user-facing AI generation. The first implementation should use a source registry and connector-style collectors rather than broad open-web crawling.

Initial connector candidates:
- RSS/Atom connector for official AI, developer, and engineering blogs
- arXiv API connector for research sources such as `cs.AI`, `cs.LG`, and `cs.CL`
- manual or newsletter import connector for curated links that do not have clean feeds

Hacker News is excluded from the initial collector. It can be reconsidered later as a discovery or ranking signal, but it should not be treated as the original article source.

## 13. Graph RAG Direction
Graph RAG is part of the desired 1.0.0 direction, but full Obsidian-style graph exploration is deferred.

### 1.0.0 Focus
- relationship-aware article metadata
- related concepts
- related articles
- relationship-based insight generation
- limited graph-backed Q&A or explanation
- optional small related graph on article detail

### Deferred
Full Obsidian-style graph explorer is deferred to a later version.

Reason:
- it has high frontend complexity
- it needs enough high-quality graph data to be useful
- poor graph relationships can reduce trust
- the product value should first come from better insight quality, not only visual novelty

## 14. Home Experience
The home screen should be minimal and search-centered.

Recommended structure:
- centered search bar
- today's important news
- popular news
- lightweight category or topic filters

The main user flow should be:

1. user opens Sigak
2. user sees today's important technical news
3. user clicks an article
4. user reads summary, importance, why it matters, topics, and related items
5. user searches when they have a specific topic in mind

Priority order:

1. today's important news
2. article detail insight
3. search

## 15. Article Detail Experience
Article detail is where Sigak's core value should be most visible.

Recommended fields:
- title
- source
- original URL
- published date
- event type
- primary category
- short summary
- why it matters
- topics
- related articles
- related concepts
- optional small related graph

The raw `importanceScore` should not be shown on the MVP article detail page. It is an internal ranking signal for curated lists. The detail page should make importance understandable through `whyItMatters` and, if needed later, a qualitative label rather than a precise numeric score.

## 16. Insight Tone
Sigak should explain technical news in a way that is easy to understand but not shallow.

The tone should:
- be beginner-friendly enough for readers without deep background
- still give meaningful technical context for developers and CS-interested readers
- explain what changed
- explain why the change matters
- connect the item to broader technical trends
- avoid hype and unsupported predictions

The product should not focus on interview preparation as a primary tone.

## 17. Search Direction
Search should start simple but be designed for semantic and graph-based expansion.

### MVP Start
- keyword search over title, summary, topics, and primary category

### Later
- semantic search using embeddings
- graph-aware retrieval using concepts and relationships
- natural language questions over recent technical trends

The search UI should remain stable even as the backend search implementation becomes more advanced.

## 18. Initial Article Model Direction
The final schema can evolve, but early mock data should already resemble the future data model.

The current MVP API contract is documented in `API_SPEC.md`.

Candidate article fields:

```txt
id
title
url
source
publishedAt
eventType
primaryCategory
topics
summary
whyItMatters
importanceScore
contentText
processingStatus
relatedArticleIds
relatedConcepts
```

Collection and enrichment should keep source data and AI output separable:

```txt
CollectedArticle
- sourceName
- sourceType
- externalId
- url
- canonicalUrl
- title
- publishedAt
- authorNames
- rawContent
- extractedText
- collectedAt
- extractionStatus

ArticleEnrichment
- summary
- whyItMatters
- suggestedTopics
- suggestedPrimaryCategory
- suggestedImportanceScore
- modelName
- promptVersion
- enrichedAt
- enrichmentStatus
```

Candidate future graph fields:

```txt
ConceptNode
- id
- name
- type

ArticleConcept
- articleId
- conceptId
- relationType
- confidence
```

## 19. Importance Score
`importanceScore` is a 0-100 integer that represents the curated importance of an article.

For MVP seed data, `importanceScore` is assigned manually. Later AI enrichment may suggest a score, but the final stored score can be manually reviewed or adjusted by rules.

The score is currently a ranking and curation signal, not a required user-facing detail field. This avoids implying false precision while the MVP still uses curated mock data.

| Range | Level | Meaning |
| --- | --- | --- |
| `90-100` | Critical | Major security issues, major AI/CS changes, or very important official announcements that can affect the technical ecosystem. |
| `75-89` | High | Important changes many developers or teams should know about. |
| `50-74` | Medium | Meaningful changes for a specific technical audience. |
| `0-49` | Low | Recordable items that should not usually appear in the main important feed. |

Most MVP seed articles should be `75+` because Sigak is focused on selection, not volume.

## 20. Source Selection Summary
Sigak should keep source quality high before broad automation.

Include sources such as:
- official company, lab, standards body, or major open source project announcements
- reputable AI, CS, and software development news sources
- security advisories, CVE records, and incident reports
- influential research papers or research announcements
- major open source release notes

Exclude sources such as:
- general personal blog posts
- simple tutorials
- promotional content
- low-impact patch releases
- rumors or unverified community discussions

Seed data should cover multiple event types and categories, include `summary`, `whyItMatters`, `topics`, and related items, and preserve enough source metadata for future reprocessing.

See `SOURCE_POLICY.md` for the full draft policy.

## 21. Decided Planning Points
- Graph RAG is included in 1.0.0 only at a limited relationship-insight or graph-backed retrieval level.
- Full Obsidian-style graph exploration is deferred until after 1.0.0.
- Articles use one `primaryCategory` plus multiple `topics`.
- Source selection policy should be drafted before building broad automated collection.
- MVP data starts with curated seed data, then moves toward selected RSS/API collection.
- `importanceScore` uses a 0-100 manually curated score for MVP seed data and is used for ranking rather than raw detail-page display.
- Current article list/detail API shape is documented in `API_SPEC.md`.
- Search results should use the same article response shape as `GET /api/articles`.

## 22. Open Planning Areas
These areas still need product decisions before implementation becomes too large.

- Graph RAG enrichment pipeline boundaries
- exact 1.0.0 acceptance criteria

## 23. Current Decisions Summary
- Target domain: AI, development, and computer science
- Primary value: important news selection plus context and relationships
- Data strategy: curated data first, RSS/API collection later
- Graph RAG: limited relationship-based insight for 1.0.0; full graph explorer deferred
- Home UX: centered search bar plus today's important and popular news
- Insight tone: beginner-friendly but technically meaningful
- Event types: `NEWS`, `OFFICIAL_ANNOUNCEMENT`, `RESEARCH`, `SECURITY`, `RELEASE`
- Categories: `AI`, `SECURITY`, `SOFTWARE_ENGINEERING`, `BACKEND`, `FRONTEND`, `DATA`, `INFRA_CLOUD`, `DEVTOOLS`, `CS_RESEARCH`
- Article classification: one `primaryCategory` plus multiple `topics`
- Article API: list/detail response shape is documented in `API_SPEC.md`
