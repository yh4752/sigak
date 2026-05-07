# 0004: Persistence MVP

## Status
Accepted

## Date
2026-05-06

## Context
Sigak's article API started with curated in-memory mock data. Phase 6 needs real persistence so article metadata, raw content, enrichment output, topics, and related article links can be reviewed and reused for future search and Graph RAG work.

## Decision
Use PostgreSQL as the local relational database, Docker Compose for local database setup, Flyway for schema and seed migrations, and Spring Data JPA for backend persistence.

The public article API remains stable. The backend maps persisted article rows into the existing `ArticleResponse` DTO.

The Phase 6 relational model includes:
- `news_sources`
- `articles`
- `article_raw_contents`
- `article_enrichments`
- `article_topics`
- `article_relations`

Raw source text and enrichment output are stored separately so articles can be reprocessed with new prompts, models, category rules, embedding models, or graph extraction steps without fetching the source again.

## Consequences
- The backend demonstrates a real relational persistence layer.
- Flyway makes schema history explicit and reviewable.
- Testcontainers verifies migrations against PostgreSQL instead of an in-memory substitute.
- The model is ready for Phase 8 concepts and relationship-aware insight without implementing the full graph schema in Phase 6.
- Local development now requires PostgreSQL for the backend article API.

## Alternatives Considered
- Single `articles` table: faster, but weak for raw-content separation, enrichment history, and relationship visualization.
- Hibernate automatic DDL: convenient, but weaker for portfolio-grade schema review and migration history.
- H2 for tests: faster, but less faithful to PostgreSQL and Flyway behavior.
- Full graph schema in Phase 6: useful eventually, but too much before the persistence MVP is stable.
