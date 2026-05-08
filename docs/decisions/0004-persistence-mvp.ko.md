# 0004: Persistence MVP

[English](0004-persistence-mvp.md) | [한국어](0004-persistence-mvp.ko.md)

## 상태
승인됨

## 날짜
2026-05-06

## 맥락
Sigak의 article API는 큐레이션된 in-memory mock data에서 시작했습니다. Phase 6에서는 article metadata, raw content, enrichment output, topics, related article link를 검토하고 향후 search/Graph RAG 작업에 재사용할 수 있도록 실제 persistence가 필요합니다.

## 결정
로컬 relational database로 PostgreSQL을 사용하고, 로컬 database setup에는 Docker Compose를 사용하며, schema/seed migration에는 Flyway, backend persistence에는 Spring Data JPA를 사용합니다.

Public article API는 안정적으로 유지합니다. 백엔드는 persisted article row를 기존 `ArticleResponse` DTO로 mapping합니다.

Phase 6 relational model은 다음 table을 포함합니다.
- `news_sources`
- `articles`
- `article_raw_contents`
- `article_enrichments`
- `article_topics`
- `article_relations`

Raw source text와 enrichment output을 분리 저장해, source를 다시 fetch하지 않고도 새 prompt, model, category rule, embedding model, graph extraction step으로 article을 재처리할 수 있게 합니다.

## 결과
- 백엔드가 실제 relational persistence layer를 보여줍니다.
- Flyway로 schema history를 명시적이고 reviewable하게 유지합니다.
- Testcontainers는 in-memory substitute가 아니라 PostgreSQL에 대해 migration을 검증합니다.
- Phase 6에서 full graph schema를 구현하지 않아도 Phase 8 concept와 relationship-aware insight를 준비할 수 있습니다.
- 로컬 개발에서 backend article API를 사용하려면 PostgreSQL이 필요합니다.

## 검토한 대안
- 단일 `articles` table: 빠르지만 raw-content separation, enrichment history, relationship visualization에 약합니다.
- Hibernate automatic DDL: 편리하지만 portfolio-grade schema review와 migration history에 약합니다.
- Test용 H2: 빠르지만 PostgreSQL과 Flyway behavior에 덜 충실합니다.
- Phase 6에서 full graph schema 구현: 언젠가는 유용하지만 persistence MVP가 안정되기 전에는 범위가 큽니다.
