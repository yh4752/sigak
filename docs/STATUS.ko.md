# Sigak 현재 상태

[English](STATUS.md) | [한국어](STATUS.ko.md)

마지막 업데이트: 2026-05-28

이 문서는 살아 있는 상태 문서다. 로드맵 phase가 완료되거나, 주요 리스크가 바뀌거나, 검증 결과가 오래되면 갱신한다.

## 1. 요약

Sigak은 AI, 소프트웨어 개발, 컴퓨터 과학 분야의 중요한 기술 뉴스를 선별하고, 요약과 "왜 중요한가" 인사이트를 제공하는 AI 기반 뉴스 인사이트 플랫폼이다.

현재 프로젝트는 문서화, 기본 모노레포 구조, Spring Boot 백엔드 API, PostgreSQL 기반 영속성, React 프론트엔드, FastAPI mock AI 서버, selected-source 수집-영속화 파이프라인까지 MVP의 주요 골격을 갖춘 상태다.

2026-05-27 기준으로 MVP 목표를 3주 공개 포트폴리오 릴리즈로 재정렬했다. Sigak v0.1은 collection, PostgreSQL source-of-truth 저장, Elasticsearch keyword search, Qdrant vector search, Neo4j graph projection, hybrid retrieval, graph-aware article detail, 재현 가능한 local metrics를 보여주는 AI search vertical slice를 목표로 한다.

현재 단계의 핵심 평가는 다음과 같다.

| 영역 | 현재 상태 | 평가 |
| --- | --- | --- |
| 제품 방향 | MVP 범위와 비범위가 문서화됨 | 양호 |
| 백엔드 | persisted article list/detail/search API 구현, query 검색은 Elasticsearch 우선 + PostgreSQL fallback으로 연결, FastAPI embedding client 경계 추가 | 양호, API-ready filtering, fallback 검색, AI client wiring 보완 완료 |
| 프론트엔드 | 홈, 검색, 상세, 관련 기사 UI 구현 | 양호, 상세 화면 stale state 보완 완료 |
| AI 서버 | FastAPI mock enrichment endpoint와 deterministic embedding endpoint 구현, 실제 embedding mode가 다음 retrieval 기본 경로 | AI/RAG 경계 초기 완료, semantic retrieval 품질은 실제 모델 보강 필요 |
| 데이터 | PostgreSQL schema, seed data, graph-ready metadata, 수집 article 저장 구현 | MVP 기반 완료 |
| Search infra | Elasticsearch readiness, article projection rebuild, keyword search path 연결 완료. Qdrant와 Neo4j application 연결은 대기 | keyword slice 진행 중 |
| 인프라 | PostgreSQL, Elasticsearch, Qdrant, Neo4j, AI server, SchemaSpy Docker Compose 구성 | 로컬 기반 양호, projection flow 확장이 다음 단계 |
| 문서 | README, API spec, roadmap, ADR 정리 | 양호 |

## 2. Sigak v0.1 목표

대상 기간: 2026-05-27부터 2026-06-16까지

목표 포지셔닝:

```txt
public AI news search MVP
-> hybrid retrieval
-> graph-aware article insight
-> reproducible local metrics
```

대상 demo 흐름:

```txt
collect selected sources
-> persist articles in PostgreSQL
-> rebuild Elasticsearch, Qdrant, and Neo4j projections
-> run keyword, vector, and hybrid search
-> inspect graph-aware article detail
-> review indexing/search metrics and retrieval benchmark
```

v0.1 포함 범위:

- controlled collection trigger
- indexing rebuild trigger
- Elasticsearch keyword search
- FastAPI embedding boundary
- Qdrant vector search
- RRF 기반 hybrid search
- Neo4j article/topic/relation projection
- article detail의 relation reason 또는 related concept
- indexing/search latency metrics
- 10-15개 labeled query 기반 작은 retrieval benchmark
- 포트폴리오 README, ADR, demo script, release note

명시적 제외 범위:

- full GraphRAG chatbot
- Airflow orchestration
- user account와 saved article
- full graph explorer
- 대규모 benchmark suite
- production observability stack

## 3. 현재까지 진행한 일

### 3.1 프로젝트 방향 및 문서화

완료된 내용:

- `README.md`에 프로젝트 목적, 아키텍처, 로컬 실행 방법 정리
- `docs/PRODUCT.md`에 제품 정의, 대상 사용자, 핵심 가치, MVP 범위 정리
- `docs/API_SPEC.md`에 article API contract와 internal enrichment contract 정리
- `docs/ROADMAP.md`에 서비스 트랙과 연구 트랙 통합 로드맵 정리
- `docs/SOURCE_POLICY.md`에 초기 뉴스 소스 정책 정리
- `docs/decisions/`에 주요 ADR 기록

현재 문서 기준으로 Sigak의 방향은 "중요한 기술 변화, 맥락과 관계를 포함해 설명하는 서비스"로 정리되어 있다. 이 방향은 단순 뉴스 목록보다 포트폴리오에서 보여줄 수 있는 기술적 차별성이 분명하다.

### 3.2 백엔드

완료된 내용:

- Kotlin + Spring Boot 기반 백엔드 구성
- REST API 구조 구성
- layered architecture 적용
  - controller
  - service
  - repository
  - domain/entity
  - dto
  - config
- article list API 구현
- article detail API 구현
- keyword search 구현
- Swagger/OpenAPI 문서 생성 구성
- explicit CORS 설정 추가
- PostgreSQL + Flyway 기반 persistence schema 구성
- JPA entity와 repository 구성
- curated seed article data 구성
- service/controller/integration test 작성

현재 article API 응답은 다음 MVP 핵심 필드를 포함한다.

- title
- source
- url
- publishedAt
- eventType
- primaryCategory
- topics
- summary
- whyItMatters
- importanceScore
- relatedArticleIds

강점:

- 컨트롤러가 얇고, API 응답 DTO를 사용해 entity를 직접 노출하지 않는다.
- `raw article content`와 `enrichment`가 분리되어 있어 향후 재처리와 Graph RAG 확장에 유리하다.
- seed data가 단순 제목 목록이 아니라 event type, category, topic, relation까지 포함한다.

보완 필요:

- `/api/articles?query=...`는 Elasticsearch keyword search로 연결되었지만, ranking tuning, 사용자 검색 metric, hybrid search는 아직 필요하다.
- PostgreSQL field filtering은 Elasticsearch 장애 시 fallback 경로로 유지한다.

### 3.3 프론트엔드

완료된 내용:

- React + TypeScript + Vite 구성
- React Router 기반 route 구성
  - `/`
  - `/articles/:id`
- Axios API client 구성
- Zod 기반 API response validation 구성
- 홈 화면 구현
  - centered search
  - Today's Important News
  - Popular News
- article list/search result item 구현
- article detail 화면 구현
  - summary
  - why it matters
  - topics
  - source link
  - related articles
- loading, empty, error state 구현
- frontend API client, page, component tests 작성

강점:

- API 호출이 client module로 분리되어 있어 유지보수하기 쉽다.
- Zod 검증을 API boundary에 둔 점은 백엔드 응답 변경을 빠르게 감지하는 데 좋다.
- MVP UI가 과도하게 복잡하지 않고, 핵심 흐름인 search -> detail -> related article 탐색에 집중되어 있다.

보완 필요:

- 향후 category/topic filter가 추가되면 검색 상태와 URL query parameter 동기화를 고려할 필요가 있다.

### 3.4 AI 서버

완료된 내용:

- FastAPI app 구성
- health endpoint 구현
- mock enrichment endpoint 구현
  - `POST /api/enrichment/article`
- deterministic embedding endpoint 구현
  - `POST /api/embeddings/text`
- enrichment request/response schema 구성
- pytest 기반 smoke test 작성

강점:

- paid API key 없이 로컬 개발 가능하다.
- Spring Boot와 FastAPI 책임 분리가 문서와 코드에서 일관된다.
- internal enrichment contract가 `docs/API_SPEC.md`에 정리되어 있다.
- Qdrant indexing smoke test를 실제 embedding 품질 작업보다 먼저 검증할 수 있는 embedding boundary가 생겼다.

보완 필요:

- Spring Boot는 FastAPI embedding endpoint를 HTTP로 호출할 수 있다. 다만 enrichment는 아직 local mock client를 사용하고, Qdrant projection wiring은 아직 필요하다.
- 현재 embedding output은 deterministic test data다. Vector search 품질을 포트폴리오에서 주장하기 전 실제 embedding model mode를 추가해야 한다.

### 3.5 수집 및 enrichment foundation

완료된 내용:

- source registry 구성
- RSS/Atom collector boundary 구현
- arXiv collector boundary 구현
- collected article model 구성
- article normalizer 구현
- mock enrichment client 구현
- 수집 article persistence writer 구현
- canonical URL, 원본 URL, external ID, source/title/publishedAt 기반 duplicate detection 구현
- raw content, current enrichment, topics 저장 흐름 연결
- collector, normalizer, pipeline, persistence service tests 작성

강점:

- broad crawling이 아니라 selected source registry에서 시작하는 방향이 MVP에 적절하다.
- raw content와 extracted text를 분리해 향후 재처리 비용을 줄이는 방향이 좋다.
- Hacker News 같은 community aggregator를 초기 collector에서 제외한 결정이 product quality와 source reliability 관점에서 합리적이다.
- 수집된 article이 `PUBLISHED` 상태와 current enrichment를 갖추면 공개 API와 frontend에서 같은 응답 모델로 볼 수 있다.

보완 필요:

- scheduled collection은 아직 구현되지 않았다.
- admin/internal trigger endpoint 또는 command runner가 아직 없다.
- retry, failure status, observability가 아직 없다.
- Spring Boot가 아직 FastAPI를 HTTP로 호출하지 않는다. 현재 collection pipeline은 mock enrichment boundary를 사용한다.

### 3.6 인프라 및 로컬 개발

완료된 내용:

- root `.env.example` 작성
- PostgreSQL, Elasticsearch, Qdrant, Neo4j, AI server, SchemaSpy Docker Compose 구성
- backend/frontend/ai 각각 로컬 실행 문서 작성
- Testcontainers 기반 PostgreSQL integration test 구성
- search infrastructure health endpoint 구현
- article search projection rebuild endpoint 구현
- SchemaSpy 일회성 DB 구조 시각화 workflow 구성

보완 필요:

- Qdrant와 Neo4j의 application-level projection flow는 아직 필요하다.
- 검색 metric은 현재 기본 로그 수준이며, 이후 재현 가능한 benchmark artifact로 확장해야 한다.

## 4. 코드 리뷰 findings 처리 현황

### 4.1 API-ready article filtering 처리

위치:

- `backend/src/main/kotlin/com/sigak/article/service/ArticleService.kt`

처리 전 문제:

`getArticles()`가 모든 article을 불러오고 enrichment/topics/relations를 fetch한 뒤 response로 변환한다. 이후 query filter를 적용한다. 현재 seed data는 모든 article에 current enrichment가 있어서 괜찮지만, 수집 pipeline이 붙으면 enrichment 미완료 article도 DB에 저장될 수 있다. 이 경우 검색 결과와 무관한 article 때문에 response 변환 단계에서 500 오류가 날 수 있다.

처리 결과:

- 공개 list/search/detail API는 repository query 단계에서 `processingStatus = PUBLISHED`이고 current enrichment가 있는 article만 조회한다.
- enrichment가 없는 article이 저장되어도 공개 API response 변환 전에 제외된다.
- 관련 service regression test를 추가했다.

### 4.2 Article detail related articles stale state

위치:

- `frontend/src/pages/ArticleDetailPage.tsx`

처리 전 문제:

article이 바뀌었을 때 `relatedArticles`를 먼저 초기화하지 않는다. 관련 기사가 있는 article에서 관련 기사가 없는 article로 이동하면 이전 related article 목록이 잠시 또는 계속 남을 수 있다.

처리 결과:

- article ID 또는 article 상태가 바뀔 때 related article state를 먼저 비운다.
- 늦게 도착한 related article fetch 결과가 새 article 화면을 덮지 않도록 guard를 추가했다.
- navigation regression test를 추가했다.

### 4.3 AI enrichment input validation

위치:

- `ai/app/schemas/enrichment.py`

처리 전 문제:

`Field(min_length=1)`은 `"   "` 같은 whitespace-only string을 허용한다. mock enrichment에서는 빈 summary에 가까운 결과가 만들어질 수 있고, 이후 실제 LLM 연동에서도 품질 문제가 생길 수 있다.

처리 결과:

- required text field는 strip 후 빈 문자열을 거절한다.
- `suggestedImportanceScore`는 0-100 범위로 제한한다.
- request validation과 response schema regression test를 추가했다.

### 4.4 Elasticsearch keyword search와 PostgreSQL fallback

공개 article search 흐름은 이제 non-blank `query`에 대해 Elasticsearch를 우선 사용한다. Elasticsearch는 article ID 후보만 반환하고, Spring Boot는 PostgreSQL에서 API-ready article response를 다시 조립한다. 따라서 검색 인덱스는 빠른 후보 생성용 projection store로 남고, 최종 응답의 source of truth는 PostgreSQL이 유지한다.

Elasticsearch가 내려가거나 응답에 실패하면 기존 PostgreSQL field filtering으로 fallback한다. 이때 query length, result count, fallback 여부, elapsed time을 internal in-memory metrics endpoint에 기록해 MVP 사용성을 지키면서도 이후 benchmark 작업으로 확장할 관찰 지점을 남긴다.

## 5. 검증 현황

최근 확인한 검증 명령:

| 영역 | 명령 | 결과 |
| --- | --- | --- |
| Backend | `./gradlew test` | 성공 |
| Backend search slice | `./gradlew test --tests com.sigak.search.service.ElasticsearchArticleKeywordSearchServiceTest --tests com.sigak.article.service.ArticleServiceTest` | 성공 |
| Backend article API | `./gradlew test --tests com.sigak.article.controller.ArticleControllerTest` | 성공 |
| Local Elasticsearch search smoke | `rebuild -> _count -> /api/articles?query=graph -> metrics -> Elasticsearch 중단 -> fallback query -> metrics` | 성공, 5개 article 색인, fallback article 4 반환, `totalSearchCount=2`, `fallbackSearchCount=1` 확인 |
| Backend search metrics | `./gradlew test --tests com.sigak.search.metrics.ArticleSearchMetricsRecorderTest --tests com.sigak.search.metrics.ArticleSearchMetricsControllerTest --tests com.sigak.article.service.ArticleServiceTest` | 성공 |
| Backend FastAPI embedding client | `./gradlew test --tests com.sigak.ai.embedding.FastApiEmbeddingClientTest` | 성공 |
| AI embedding endpoint | `.venv/bin/python -m pytest tests/test_embedding_router.py` | 성공 |
| Frontend tests | `npm test` | 26 tests 통과 |
| Frontend build | `npm run build` | 성공 |
| Frontend lint | `npm run lint` | 성공 |
| AI tests | `.venv/bin/python -m pytest` | 3 tests 통과 |

참고:

- AI 서버 테스트는 global `python` 또는 `python3`가 아니라 `ai/.venv`의 Python으로 실행해야 한다.
- 백엔드 테스트는 Testcontainers와 PostgreSQL을 사용한다.

## 6. 앞으로 진행해야 할 일

### 6.1 3주 우선순위

1. 로컬 search infrastructure 추가
   - Docker Compose를 Elasticsearch, Qdrant, Neo4j, AI server까지 확장
   - healthcheck와 environment variable 정의
   - PostgreSQL을 source of truth로 유지

2. 수집 실행 트리거 추가
   - internal/admin collection endpoint 또는 command runner
   - source별 실행 결과 반환
   - fetched/published/skipped/failed count 제공

3. Indexing과 search 추가
   - indexing rebuild trigger
   - Elasticsearch keyword indexing/search
   - FastAPI embedding boundary
   - Qdrant vector indexing/search
   - RRF 기반 hybrid search

4. Graph-aware insight 추가
   - Neo4j article/topic/relation projection
   - article detail에 relation reason 또는 related concept 표시
   - UI는 작고 읽기 쉽게 유지

5. Metrics와 포트폴리오 packaging 추가
   - indexing duration/count metrics
   - search latency p50/p95 metrics
   - Recall@5, MRR@5 benchmark
   - README, ADR, demo script, release note

### 6.2 마일스톤

| 날짜 | 마일스톤 | 완료 신호 |
| --- | --- | --- |
| 2026-06-02 | Search infrastructure slice | Article을 Elasticsearch와 Qdrant에 색인하고 keyword, vector, hybrid mode로 검색할 수 있다. |
| 2026-06-09 | Graph and metrics slice | Neo4j projection, graph-aware detail, indexing metrics, latency metrics, retrieval benchmark artifact를 재현할 수 있다. |
| 2026-06-16 | Sigak v0.1 portfolio MVP | README, ADR, demo script, test, release note가 portfolio review 가능한 상태다. |

### 6.3 리스크 관리 기준

- Main vector retrieval path에는 실제 embedding model을 사용한다. Deterministic embedding은 모델 세팅이 local smoke test를 늦출 때 fallback/test mode로만 유지한다.
- Elasticsearch, Qdrant, Neo4j는 primary data store가 아니라 projection store로 다룬다.
- v0.1에서는 full graph explorer를 만들지 않는다.
- Benchmark label은 수동 검토 가능한 작은 규모로 유지한다.
- 넓은 기능 범위보다 명확한 로컬 재현성을 우선한다.

## 7. 권장 개발 순서

### 1단계. 코드 리뷰 findings 수정

상태: 완료

완료 내용:

- 백엔드 API는 PUBLISHED/current enrichment article만 노출한다.
- 상세 화면에서 article 전환 시 related article이 남지 않는다.
- AI endpoint는 whitespace-only input을 거절한다.
- 관련 테스트가 추가 또는 갱신된다.

### 2단계. Persisted collection pipeline 연결

상태: 완료

완료 내용:

- 현재 collector와 persistence schema를 실제 workflow로 연결했다.
- 수집된 article을 DB에 저장할 수 있다.
- raw content와 enrichment가 분리 저장된다.
- canonical URL, 원본 URL, external ID, source/title/publishedAt 기준으로 중복 저장을 막는다.
- mock enrichment 결과를 current enrichment로 저장할 수 있다.

### 3단계. Collection trigger와 실행 관측성

다음 목표:

- 선택한 source 수집을 명시적으로 실행하고 결과를 확인할 수 있게 만든다.

완료 기준:

- internal/admin endpoint 또는 command runner로 source collection을 실행할 수 있다.
- 실행 결과에 fetched/published/skipped/failed count와 실패 이유가 포함된다.
- 실패 기록 또는 최소한의 retry 기준이 문서화된다.

### 4단계. Search projection store 추가

다음 목표:

- PostgreSQL에서 search projection을 rebuild하고 keyword, vector, hybrid search를 비교할 수 있게 만든다.

완료 기준:

- Elasticsearch가 검색 가능한 article text와 metadata를 저장한다.
- Qdrant가 FastAPI embedding boundary에서 생성한 article vector를 저장한다.
- Hybrid search가 keyword와 vector 결과를 RRF로 병합한다.
- Projection rebuild를 local command 또는 internal endpoint로 재현할 수 있다.

### 5단계. Graph-aware insight 추가

다음 목표:

- 작은 Neo4j projection을 사용해 Sigak의 차별점인 관계 기반 인사이트를 article detail에 반영한다.

완료 기준:

- related article ID뿐 아니라 relation reason 또는 related concept를 보여준다.
- full graph UI 없이도 "이 기사가 무엇과 연결되는지"가 드러난다.

### 6단계. Metrics, benchmark, portfolio packaging 추가

다음 목표:

- 프로젝트를 공개 AI search 포트폴리오로 검토 가능한 상태로 만든다.

완료 기준:

- Indexing과 search latency metrics가 생성된다.
- 작은 retrieval benchmark가 keyword, vector, hybrid mode를 비교한다.
- README, ADR, demo script, release note가 architecture와 trade-off를 설명한다.

## 8. 현재 MVP 완성도 평가

현재 완성도:

- 제품 방향: 높음
- 백엔드 구조: 높음
- 프론트 기본 흐름: 중상
- AI/RAG 실사용성: 초기 단계지만 v0.1 search infrastructure slice의 명시적 목표로 재정렬됨
- collection 실행/자동화: 저장 파이프라인 완료, 실행 트리거는 초기 단계
- 로컬 배포 편의성: 중간, multi-service compose가 다음 인프라 리스크
- 포트폴리오 문서화: 높음

종합하면, Sigak은 "기획 문서와 기본 CRUD/search 화면만 있는 프로젝트"를 넘어선 상태다. 특히 backend persistence, API spec, source policy, AI enrichment boundary까지 마련되어 있어 포트폴리오 설득력이 있다.

다만 다음 단계에서 반드시 보여줘야 할 것은 실행 가능한 공개 AI search 흐름이다.

```txt
source trigger -> collect -> persist -> index projections -> hybrid search -> graph-aware detail -> metrics
```

이 흐름을 명시적으로 실행하고 결과를 관측할 수 있으면 Sigak은 이력서에 적힌 AI search와 Graph RAG 경험을 공개적으로 검토할 수 있는 프로젝트가 된다.

## 9. 결론

현재까지의 진행은 MVP 방향과 잘 맞는다. 특히 Spring Boot를 주 API boundary로 두고, FastAPI를 AI/RAG 전용 서비스로 분리한 선택은 프로젝트 목표에 적합하다. PostgreSQL은 source of truth로 유지하고, Elasticsearch, Qdrant, Neo4j는 재생성 가능한 projection store로 추가하는 방향이 3주 v0.1 목표에 맞다.

다음 개발의 핵심은 compose 확장, collection trigger, projection rebuild, hybrid search, graph-aware detail, local metrics, portfolio packaging을 2026-06-16까지 하나의 재현 가능한 흐름으로 묶는 것이다.
