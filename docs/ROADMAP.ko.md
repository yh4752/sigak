# Sigak 로드맵

[English](ROADMAP.md) | [한국어](ROADMAP.ko.md)

마지막 업데이트: 2026-06-09

이 로드맵은 Sigak의 제품과 연구 실행 계획을 한 곳에서 관리하는 기준 문서입니다. 이전 MVP 로드맵, 서비스 마스터 로드맵, 연구 구현 로드맵을 서비스 트랙과 연구 트랙으로 통합합니다.

## 1. 로드맵 원칙

Sigak에는 두 개의 층이 있습니다.

- **사용자-facing 서비스:** article list, article detail, search, AI summary, why-it-matters insight, topics, related articles.
- **연구 dashboard:** retrieval, RAG, graph-aware insight, fine-tuning, failure analysis 결과를 서비스 안에서 시각화하는 페이지.

실행 순서:

```txt
working service
-> reliable collection/enrichment
-> reproducible experiments
-> research visualization
-> deployment and portfolio packaging
```

연구 기능은 장식용 chart가 아니라 실제 pipeline에서 나온 결과에 기반해야 합니다. Mock mode는 로컬 개발용으로 유지하고, real LLM enrichment 또는 추가 local-model mode는 이후 단계에서 확장합니다.

## 2. 상태 표시

- [x] 현재 phase 기준으로 구현되었거나 충분히 문서화됨
- [ ] 아직 구현되지 않음

## 3. 현재 기반

상태: 대부분 완료

- [x] 프로젝트 가이드 문서
- [x] 초기 모노레포 구조: `backend/`, `frontend/`, `ai/`, `infra/`
- [x] Kotlin Spring Boot 백엔드
- [x] React, TypeScript, Vite 프론트엔드
- [x] FastAPI mock AI 서버
- [x] PostgreSQL persistence 기반
- [x] Article list/detail/search API
- [x] 큐레이션 seed article data
- [x] Source registry
- [x] RSS/Atom과 arXiv collector boundary
- [x] Duplicate detection을 포함한 collection-to-persistence pipeline
- [x] Product, API, source policy, ADR, research strategy 문서

즉시 안정화 리스크:

- [x] 백엔드 공개 article API는 API-ready article만 노출해야 한다.
- [x] Article detail은 navigation 시 stale related article을 지워야 한다.
- [x] FastAPI enrichment schema는 whitespace-only input을 거절하고 importance score 범위를 제한해야 한다.

## 4. 3주 포트폴리오 MVP 재정렬

상태: 진행 중. Elasticsearch/Qdrant/hybrid search slice, Neo4j internal graph projection/context lookup, public graph-aware article detail, internal collection trigger, command runner, failure diagnostics 조회, runtime failure sample, local demo flow, 정적 retrieval 라벨링 UI, smoke 검증된 catalog export command, retrieval benchmark smoke runner, keyword/vector/strict-hybrid/public comparison runner, graph-aware evaluation runner, 1차 portfolio README/demo-flow refresh는 준비되었습니다. 3-query/6-article 기준 graph-aware smoke도 실행했습니다. 다만 real enrichment, 더 큰 labeled dataset, research dashboard, 최종 release packaging은 아직 남아 있습니다.

대상 기간: 2026-05-27부터 2026-06-16까지

다음 마일스톤은 Sigak v0.1입니다. 목표는 3주 안에 production-grade RAG platform을 만드는 것이 아니라, 공개 포트폴리오에서 실행하고 검토할 수 있는 작은 AI search/RAG-ready vertical slice를 완성하는 것입니다.

대상 demo 흐름:

```txt
selected source collection
-> PostgreSQL source-of-truth storage
-> Elasticsearch keyword indexing
-> FastAPI real embedding boundary
-> Qdrant vector indexing
-> Neo4j article/topic/relation projection
-> hybrid search with RRF
-> graph-aware article detail
-> local metrics and retrieval benchmark
```

v0.1 포함 범위:

- controlled collection trigger
- indexing rebuild trigger
- Elasticsearch 기반 keyword search
- semantic retrieval을 위한 실제 embedding model mode
- Qdrant 기반 vector search
- reciprocal rank fusion 기반 hybrid search
- article, topic, article relation을 위한 Neo4j graph projection
- article detail의 relation reason 또는 related concept 표시
- indexing/search latency metrics
- 10-15개 labeled query 기반 작은 retrieval benchmark
- `/research` 또는 report 기반 metrics view
- 포트폴리오용 README, ADR, demo script

v0.1 제외 범위:

- full GraphRAG chatbot
- Airflow orchestration
- user account와 saved article
- full graph explorer
- 대규모 retrieval benchmark
- production observability stack

마일스톤:

| 날짜 | 마일스톤 | 완료 신호 |
| --- | --- | --- |
| 2026-06-02 | Search infrastructure slice | Article을 Elasticsearch와 Qdrant에 색인하고 keyword, vector, hybrid mode로 검색할 수 있다. |
| 2026-06-09 | Graph and metrics slice | Neo4j projection, graph-aware detail, indexing metrics, latency metrics, retrieval benchmark artifact를 재현할 수 있다. |
| 2026-06-16 | Sigak v0.1 portfolio MVP | README, ADR, demo script, test, release note가 portfolio review 가능한 상태다. |

## 5. 서비스 트랙

### 단계 S1: 서비스 MVP 안정화

목표: 연구 dashboard 없이도 현재 article browsing 경험을 demo 가능한 수준으로 안정화합니다.

- [x] 공개 article list/search/detail API에서 current enrichment가 있는 `PUBLISHED` article만 노출합니다.
- [x] Response mapping 전에 repository/service logic에서 API-ready filtering을 적용합니다.
- [x] current enrichment가 없는 article에 대한 backend test를 추가합니다.
- [x] Article detail이 바뀔 때마다 `relatedArticles`를 비웁니다.
- [x] 늦게 도착한 related article fetch 결과가 새 화면을 덮지 않도록 guard 또는 cancel 처리합니다.
- [x] FastAPI에서 whitespace-only enrichment request text를 거절합니다.
- [x] `suggestedImportanceScore`를 `0-100`으로 제한합니다.
- [x] 수정 후 backend, frontend, AI test suite를 실행합니다.

완료 기준:

- `/`, `/articles/:id`, `GET /api/articles`가 persisted data에서도 안정적으로 동작합니다.
- 유료 API key 없이 seed data demo가 동작합니다.
- 알려진 code review finding이 해결됩니다.

### 단계 S2: End-to-End Collection과 Enrichment

목표: source collection, enrichment, persistence, UI를 연결합니다.

대상 흐름:

```txt
source registry
-> fetch
-> parse
-> normalize
-> enrich
-> persist
-> publish
-> display
```

- [x] 선별된 RSS/Atom과 arXiv source에 대한 source fetch execution을 구현합니다.
- [x] canonical URL, external source ID, title, source, published date 기반 duplicate detection을 추가합니다.
- [ ] discovered, extracted, enriched, published, failed 상태 전이를 추가합니다.
- [x] collected article과 enrichment output을 저장하는 persistence writer를 추가합니다.
- [x] source collection을 실행할 internal/admin trigger 또는 command runner를 추가합니다.
- [ ] 현재 enrichment boundary 뒤에 Spring Boot HTTP FastAPI enrichment client를 추가합니다.
- [x] 로컬 개발을 위한 mock enrichment mode를 유지합니다.
- [x] local backend, frontend checks, PostgreSQL, AI server, collection trigger, diagnostics, projection rebuild, search metrics 흐름을 문서화합니다.

완료 기준:

- 적어도 하나의 collected article이 `/api/articles`와 frontend에 나타날 수 있습니다.
- Raw content와 enrichment output이 분리 저장됩니다.
- 로컬 개발은 외부 API key 없이 계속 동작합니다.

### 단계 S3: Collection 운영과 Real AI Enrichment

목표: controlled entry point에서 collection을 실행할 수 있게 만들고, mock enrichment에서 FastAPI-backed real enrichment로 전환할 수 있게 합니다.

- [x] controlled internal/admin collection trigger 또는 command runner를 추가합니다.
- [x] 각 run에 대해 fetched, published, skipped, failed count를 반환합니다.
- [x] 나쁜 feed나 invalid collected article을 디버깅할 수 있을 만큼 failure information을 기록합니다.
- [x] collection failure event를 조회하는 internal read-only diagnostics endpoint를 추가합니다.
- [ ] `AI_ENRICHMENT_MODE=mock|openai|local`을 추가합니다.
- [ ] FastAPI에 OpenAI 또는 local-model enrichment service를 구현합니다.
- [ ] summary, why-it-matters, topics, category, importance candidate에 structured output을 사용합니다.
- [ ] model name, prompt version, latency, cost metadata를 기록합니다.
- [ ] invalid output retry 또는 fallback behavior를 추가합니다.
- [ ] 모든 evidence를 덮어쓰지 않고 enrichment history를 보존합니다.

완료 기준:

- 같은 article을 mock mode와 real mode에서 enrich할 수 있습니다.
- Real mode output은 schema-valid입니다.
- API key는 environment variable에서만 읽습니다.

### 단계 S4: Search Hardening

목표: PostgreSQL을 source of truth로 유지하면서 keyword, vector, fused retrieval을 보여주는 공개 hybrid search slice를 만듭니다.

- [x] Elasticsearch 기반 keyword indexing과 search를 추가합니다.
- [x] Local vector indexing을 위한 FastAPI embedding boundary를 추가합니다.
- [x] Main vector search path를 위한 실제 embedding model mode를 추가합니다.
- [x] Qdrant 기반 article vector search를 추가합니다.
- [x] Elasticsearch와 Qdrant 결과를 reciprocal rank fusion으로 합치는 hybrid search를 추가합니다.
- [x] 재현 가능한 local smoke test를 위해 deterministic embedding mode를 fallback/test mode로 유지합니다.
- [x] 구현이 바뀌어도 article response shape를 안정적으로 유지합니다.

완료 기준:

- 프론트엔드 관점에서 search behavior가 안정적으로 유지됩니다.
- 같은 query set으로 keyword, vector, hybrid search를 비교할 수 있습니다.
- Article card/detail page를 불필요하게 바꾸지 않고 search implementation을 발전시킬 수 있습니다.

### 단계 S5: 제한된 Graph-Aware Insight

목표: full graph explorer를 만들기 전에 작은 Neo4j projection으로 article relationship을 실제로 유용하게 만듭니다.

- [x] Graph-ready article metadata field를 추가합니다.
- [x] 큐레이션 article에 related article ID metadata를 추가합니다.
- [x] Article과 topic을 Neo4j에 projection합니다.
- [x] Article-topic relationship을 저장하거나 projection합니다.
- [x] Article-article relation reason을 저장합니다.
- [x] Article detail에 relation reason 또는 related concept를 보여줍니다.
- [x] Graph-aware context를 단순 retrieval baseline과 비교 평가합니다.

완료 기준:

- Article detail이 related article 또는 concept가 왜 중요한지 설명합니다.
- Graph 작업은 제한적이고 유용하며 evidence-backed 상태로 유지됩니다.

### 단계 S6: 연구 Dashboard MVP

목표: 실제 experiment result를 시각화하는 `/research` page를 제품 안에 추가합니다.

초기 route:

```txt
/research
```

이후 route:

```txt
/research/retrieval
/research/rag
/research/graph
/research/fine-tuning
/research/failures
```

초기 backend API 후보:

```txt
GET /api/research/overview
GET /api/research/retrieval-summary
GET /api/research/rag-summary
GET /api/research/failure-cases
```

- [ ] `experiments/results/*.json`에서 초기 dashboard data를 읽습니다.
- [ ] Dataset summary, retrieval benchmark, RAG evaluation, cost/latency, failure case를 보여줍니다.
- [ ] 과장된 claim이 아니라 table과 chart를 사용합니다.
- [ ] Dashboard를 article reading flow와 시각적으로 구분합니다.

완료 기준:

- `/research`가 실제 experiment output을 표시합니다.
- Failure case가 숨겨지지 않고 보입니다.
- Dashboard가 제품 story를 대체하지 않고 강화합니다.

### 단계 S7: Local Development, Deployment, Polish

목표: Sigak을 쉽게 실행하고, 리뷰하고, 배포할 수 있게 만듭니다.

- [x] Root-level local environment example value를 추가합니다.
- [x] Backend와 frontend local run instruction을 추가합니다.
- [x] PostgreSQL Docker Compose setup을 추가합니다.
- [ ] 필요에 따라 Docker Compose를 backend, frontend, AI server, database까지 확장합니다.
- [ ] Service-specific environment variable을 문서화합니다.
- [ ] Deployment target을 결정합니다.
- [x] 로컬 collection-to-projection demo script를 추가합니다.
- [x] Root README에 compact architecture diagram을 추가합니다.
- [ ] Portfolio review용 최종 README를 준비합니다.

완료 기준:

- 리뷰어가 README만 보고 프로젝트를 실행할 수 있습니다.
- 서비스와 research dashboard가 demo-ready 상태입니다.

## 6. 연구 트랙

### 단계 R1: 연구 Dataset과 Label

목표: Sigak 서비스 데이터에서 작고 재현 가능한 dataset을 만듭니다.

- [x] Query/article relevance label을 입력할 정적 HTML 라벨링 도구를 준비합니다.
- [x] `experiments/README.md`를 생성합니다.
- [x] 라벨링 흐름을 위해 API-ready PostgreSQL article을 frozen catalog JSON으로 export합니다. Local smoke로 `experiments/datasets/raw/articles.catalog.json`에 article 6개를 생성했습니다.
- [ ] Stable chunk ID를 갖는 deterministic chunking을 추가합니다.
- [x] `experiments/datasets/raw/`를 생성합니다.
- [x] `experiments/datasets/processed/`를 생성합니다.
- [x] `experiments/datasets/labels/`를 생성합니다.
- [ ] 30-50개의 manually reviewed evaluation example을 만듭니다.
- [ ] `docs/research/DATA_CARD.md`를 작성합니다.

완료 기준:

- Dataset을 재생성할 수 있습니다.
- Source policy와 limitation이 문서화됩니다.

### 단계 R2: Retrieval Benchmark

목표: 기술 뉴스 이해를 위한 retrieval method를 비교합니다.

- [ ] Labeled example에서 query set을 만듭니다.
- [ ] Keyword 또는 BM25 baseline을 구현합니다.
- [ ] Dense retrieval baseline을 구현합니다.
- [ ] Keyword/dense baseline이 생긴 뒤 hybrid retrieval baseline을 구현합니다.
- [ ] Recall@k, Precision@k, MRR, nDCG, latency를 계산합니다.
- [ ] Output을 `experiments/results/retrieval/` 아래에 저장합니다.
- [ ] `experiments/reports/retrieval-benchmark.md`를 작성합니다.

완료 기준:

- 적어도 두 retrieval baseline을 비교합니다.
- Report가 quality, latency, complexity trade-off를 설명합니다.

### 단계 R3: RAG Evaluation

목표: retrieval이 summary와 why-it-matters generation을 개선하는지 평가합니다.

- [ ] Structured generation output schema를 정의합니다.
- [ ] no-RAG generation, article-only context, retrieved-context RAG, related-article context를 비교합니다.
- [ ] faithfulness, relevance, clarity, evidence coverage, latency, cost를 평가합니다.
- [ ] Raw output과 evaluation score를 저장합니다.
- [ ] `experiments/reports/rag-evaluation.md`를 작성합니다.

완료 기준:

- No-RAG와 RAG variant를 정직하게 비교합니다.
- Report가 retrieval이 도움이 되는 경우와 해가 되는 경우를 식별합니다.

### 단계 R4: Graph-Aware Insight Experiment

목표: concept와 relationship context가 insight quality를 개선하는지 검증합니다.

- [ ] Concept extraction schema를 추가합니다.
- [ ] Dataset article에 article-concept edge를 생성합니다.
- [ ] Article-article relation candidate를 생성합니다.
- [ ] 작은 human-reviewed relation set을 추가합니다.
- [x] search miss, related baseline coverage, graph reason coverage, topic coverage, public round-trip latency를 기록하는 public graph-aware smoke evaluation runner를 추가합니다.
- [ ] Graph-aware context를 top-k retrieval context와 비교합니다.
- [ ] `experiments/reports/graph-aware-insight.md`를 작성합니다.

완료 기준:

- Relationship output이 가능한 경우 type, reason, confidence를 포함합니다.
- Report에 accepted, rejected, ambiguous relation example이 포함됩니다.

### 단계 R5: Fine-Tuning Experiment

목표: fine-tuning을 장식용 기능이 아니라 통제된 NLP experiment로 사용합니다.

권장 첫 task:

- event type classification
- primary category classification
- importance bucket classification

- [ ] 하나의 classification task에 대한 train/eval JSONL을 만듭니다.
- [ ] Zero-shot과 few-shot prompt baseline을 추가합니다.
- [ ] 작은 open-source model에 LoRA 또는 QLoRA training을 추가합니다.
- [ ] accuracy, macro F1, format error rate, cost, latency를 평가합니다.
- [ ] Model weight는 commit하지 않고 adapter metadata를 저장합니다.
- [ ] `experiments/reports/fine-tuning-classification.md`를 작성합니다.

완료 기준:

- 적어도 하나의 fine-tuning experiment가 재현 가능합니다.
- Report가 fine-tuning이 실제로 도움이 되었는지 정직하게 말합니다.

### 단계 R6: 포트폴리오 연구 Packaging

목표: LLM/NLP 리뷰어가 연구 기여를 빠르게 이해할 수 있도록 packaging합니다.

- [ ] `docs/research/SIGAK_RESEARCH_REPORT.md`를 작성합니다.
- [ ] `docs/research/MODEL_CARD.md`를 작성합니다.
- [ ] `docs/research/DATA_CARD.md`를 업데이트합니다.
- [ ] README에 experiment result table을 추가합니다.
- [ ] 적어도 하나의 experiment에 대한 reproduction command를 추가합니다.
- [ ] limitation과 future work를 추가합니다.

완료 기준:

- 리뷰어가 제품과 연구 기여를 5분 안에 이해할 수 있습니다.
- 기술 리뷰어가 적어도 하나의 experiment를 재현할 수 있습니다.

## 7. 권장 실행 순서

```txt
Day 1: v0.1 범위와 문서 재정렬
-> Week 1: compose, collection trigger, indexing, keyword/vector/hybrid search
-> Week 2: Neo4j projection, graph-aware detail, metrics, retrieval benchmark
-> Week 3: research view, README, ADR, demo script, tests, release notes
```

중요 dependency:

- PostgreSQL은 source of truth로 유지하고, Elasticsearch, Qdrant, Neo4j는 재생성 가능한 projection으로 둡니다.
- Frontend 또는 research UI가 새 데이터에 의존하기 전에 collection과 indexing trigger가 먼저 있어야 합니다.
- 품질 개선을 주장하기 전에 hybrid search를 keyword, vector mode와 benchmark로 비교해야 합니다.
- v0.1의 graph-aware insight는 relation reason 또는 related concept 표시로 제한합니다.

## 8. Sigak v0.1 이전 비목표

- user account
- saved article
- personalized recommendation
- full graph explorer
- full GraphRAG clone
- large model full fine-tuning
- broad crawler
- complex multi-agent orchestration
- production-grade monitoring platform

## 9. 현재 다음 작업

1. Controlled collection execution 보강:
   - failure kind가 늘어날 때 manual retry guidance 최신화
   - collector 동작이 바뀔 때 runtime failure sample 최신화

2. Retrieval benchmark와 portfolio metric 추가:
   - `docs/search-evaluation/labeling.html`로 labeled query set 작성
   - 이미 생성된 `api-ready-2026-06-05` 41개 article 확장 catalog를 다음 reviewed label handoff 기준으로 사용
   - 더 큰 benchmark 품질 주장을 하기 전에 첫 smoke artifact를 10-15개 reviewed query label set으로 확장
   - label review 이후 확장 catalog에서 keyword/vector/strict-hybrid/public comparison 재실행
   - label-dependent benchmark가 끝난 뒤 최종 release note, ADR update, README polish

3. Graph-aware evaluation 근거 확장:
   - 품질 주장을 하기 전에 현재 3-query/6-article smoke set을 확장
   - 더 큰 dataset에서 public graph context를 단순 related article, retrieval baseline과 비교
   - graph reason이 article detail 경험에 도움이 되는 조건 문서화
   - 검증된 예시 안에서만 relation quality 주장 유지
