# 0003: Collection과 Enrichment Pipeline

[English](0003-collection-and-enrichment-pipeline.md) | [한국어](0003-collection-and-enrichment-pipeline.ko.md)

## 상태
승인됨

## 날짜
2026-05-05

## 맥락
Sigak의 AI summary와 insight 기능이 의미를 가지려면 article data가 필요합니다. 현재 MVP는 `summary`, `whyItMatters`, topics, category, importance score, related article ID 등 AI-enriched output과 닮은 field를 가진 curated mock article을 사용합니다.

다음 단계에서는 broad web crawler를 만들지 않아야 합니다. 범위 없는 crawling은 MVP가 충분히 안정되기 전에 source quality, attribution, legal, extraction, maintenance 문제를 만듭니다. 동시에 프로젝트는 순수 hand-written seed data를 넘어 realistic article text를 AI pipeline이 처리할 수 있어야 합니다.

## 결정
Phase 5는 collection과 LLM enrichment foundation에 집중합니다.

Sigak은 명시적인 source registry와 connector-style collection을 사용합니다. 초기 source type은 다음과 같습니다.
- 선별된 공식 AI, developer, engineering, security, infrastructure source의 RSS/Atom feed
- 선별 연구 카테고리를 위한 arXiv API query
- 안정적인 feed가 없는 curated link를 위한 manual 또는 newsletter import

Hacker News는 초기 collector에서 제외합니다. 나중에 discovery 또는 ranking signal로 재검토할 수 있지만, 원문 article source로 저장해서는 안 됩니다.

Collection pipeline은 다음 처리 흐름을 따릅니다.

```txt
DISCOVER -> FETCH -> EXTRACT -> NORMALIZE -> ENRICH_WITH_LLM -> REVIEW_OR_PUBLISH -> INDEX
```

Raw collected content와 AI-enriched output은 충분히 분리 저장해야 합니다. 그래야 저장된 article을 원본 source에서 다시 가져오지 않고도 새 prompt, model, category rule, embedding model, graph extraction step으로 재처리할 수 있습니다.

## 결과
- Automated collection이 확장되기 전에 source quality를 통제합니다.
- Crawler를 과하게 만들지 않고도 현실적인 data pipeline을 보여줄 수 있습니다.
- LLM output은 source of truth가 아니라 보존된 source data 위의 enrichment layer가 됩니다.
- 향후 Graph RAG, semantic search, relationship extraction은 저장된 raw content를 재사용할 수 있습니다.
- Backend pipeline이 발전해도 frontend article contract는 안정적으로 유지할 수 있습니다.

## 검토한 대안
- FastAPI AI summary 먼저 구현: AI integration을 보여주기에는 좋지만 realistic raw article input이 없으면 약합니다.
- Generic open-web crawler: 범위는 넓지만 MVP scope, 품질, attribution 측면에서 위험합니다.
- Hacker News를 초기 source로 사용: API 접근이 쉽고 community signal이 강하지만 원문 출처가 아니라 aggregator입니다.
