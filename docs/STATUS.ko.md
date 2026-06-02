# Sigak 현재 상태

[English](STATUS.md) | [한국어](STATUS.ko.md)

마지막 업데이트: 2026-06-02

이 문서는 살아 있는 상태 문서다. 로드맵 phase가 완료되거나, 주요 리스크가 바뀌거나, 검증 결과가 오래되면 갱신한다.

## 1. 요약

Sigak은 AI, 소프트웨어 개발, 컴퓨터 과학 분야의 중요한 기술 뉴스를 선별하고, 요약과 "왜 중요한가" 인사이트를 제공하는 AI 기반 뉴스 인사이트 플랫폼이다.

현재 프로젝트는 문서화, 기본 모노레포 구조, Spring Boot 백엔드 API, PostgreSQL 기반 영속성, React 프론트엔드, FastAPI mock AI 서버, selected-source 수집-영속화 파이프라인까지 MVP의 주요 골격을 갖춘 상태다.

2026-05-27 기준으로 MVP 목표를 3주 공개 포트폴리오 릴리즈로 재정렬했다. Sigak v0.1은 collection, PostgreSQL source-of-truth 저장, Elasticsearch keyword search, Qdrant vector search, Neo4j graph projection, hybrid retrieval, graph-aware article detail, 재현 가능한 local metrics를 보여주는 AI search vertical slice를 목표로 한다.

현재 단계의 핵심 평가는 다음과 같다.

| 영역 | 현재 상태 | 평가 |
| --- | --- | --- |
| 제품 방향 | MVP 범위와 비범위가 문서화됨 | 양호 |
| 백엔드 | persisted article list/detail/search API 구현, query 검색은 Elasticsearch keyword 후보와 Qdrant vector 후보를 함께 사용하고 PostgreSQL fallback으로 연결, internal Qdrant vector diagnostics와 collection failure diagnostics API 구현 | 양호, API-ready filtering, hybrid fallback 검색, AI client wiring, internal vector search와 collection failure 조회 보완 완료 |
| 프론트엔드 | 홈, 검색, 상세, 관련 기사 UI 구현 | 양호, 상세 화면 stale state 보완 완료 |
| AI 서버 | FastAPI mock enrichment endpoint와 configurable embedding provider 구현, local FastEmbed multilingual mode가 기본 retrieval 경로 | AI/RAG 경계 초기 완료, Qdrant projection이 Spring Boot를 통해 embedding vector를 소비함 |
| 데이터 | PostgreSQL schema, seed data, graph-ready metadata, 수집 article 저장 구현 | MVP 기반 완료 |
| Search infra | Elasticsearch readiness, keyword projection/search, Qdrant vector projection/search, public hybrid search, fallback mode, search metrics 연결 완료. Neo4j는 대기 | 현재 phase의 keyword/vector/hybrid slice 완료 |
| 인프라 | PostgreSQL, Elasticsearch, Qdrant, Neo4j, AI server, SchemaSpy Docker Compose 구성 | 로컬 기반 양호, projection flow 확장이 다음 단계 |
| 문서 | README, API spec, roadmap, ADR, 검색 평가 가이드/도구, experiments 디렉터리 가이드, smoke benchmark runner 정리 | 양호, 사용자 수동 smoke를 거친 정적 HTML workflow로 retrieval benchmark 라벨링을 시작할 수 있고 6개 article local catalog 기준 첫 3-query smoke label set과 run/metric/report artifact 생성 흐름이 존재함 |

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
- `docs/search-evaluation/labeling.html`에 retrieval benchmark relevance label을 입력하고 label JSON으로 export할 수 있는 정적 브라우저 도구 추가
- `experiments/README.md`에 raw/labels/processed/results dataset 디렉터리, API-ready article catalog export command, benchmark runner command, 실행 전제, smoke 결과 해석 정리

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

현재 검색 동작:

- `/api/articles?query=...`는 Elasticsearch keyword 후보, Qdrant vector 후보, reciprocal rank fusion 기반 hybrid search로 연결되었다.
- PostgreSQL field filtering은 두 projection path가 모두 실패할 때의 fallback 경로로 유지한다.

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
- configurable embedding endpoint 구현
  - `POST /api/embeddings/text`
- enrichment request/response schema 구성
- pytest 기반 smoke test 작성

강점:

- paid API key 없이 로컬 개발 가능하다.
- Spring Boot와 FastAPI 책임 분리가 문서와 코드에서 일관된다.
- internal enrichment contract가 `docs/API_SPEC.md`에 정리되어 있다.
- embedding boundary는 semantic retrieval을 위한 local model mode와 빠른 wiring smoke test를 위한 deterministic mode를 함께 제공한다.

보완 필요:

- Spring Boot는 FastAPI embedding endpoint를 HTTP로 호출할 수 있다. 다만 enrichment는 아직 local mock client를 사용한다.
- Real enrichment는 아직 남아 있다. Vector embedding은 search projection 작업에 연결된 상태다.

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
- source/article count response를 반환하는 internal controlled collection run endpoint 구현
- controlled collection run command runner 구현
- `runId`, failure kind, retry hint, article hint를 포함하는 persistent collection failure event 구현
- collection failure event 조회용 internal read-only diagnostics endpoint 구현
- collector, normalizer, pipeline, persistence service tests 작성

강점:

- broad crawling이 아니라 selected source registry에서 시작하는 방향이 MVP에 적절하다.
- raw content와 extracted text를 분리해 향후 재처리 비용을 줄이는 방향이 좋다.
- Hacker News 같은 community aggregator를 초기 collector에서 제외한 결정이 product quality와 source reliability 관점에서 합리적이다.
- 수집된 article이 `PUBLISHED` 상태와 current enrichment를 갖추면 공개 API와 frontend에서 같은 응답 모델로 볼 수 있다.

보완 필요:

- scheduled collection은 아직 구현되지 않았다.
- internal controlled collection trigger는 local HTTP와 command runner로 실행할 수 있지만, full run history는 아직 미뤄져 있다.
- failure event, diagnostics lookup, response count 이상의 자동 retry와 더 넓은 observability는 아직 없다.
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

- Neo4j application-level projection flow는 아직 필요하다.
- 검색 metric은 internal in-memory endpoint와 재현 가능한 smoke benchmark artifact 경로를 모두 갖춘 상태다.
- API-ready PostgreSQL article을 frozen catalog로 export하는 command는 구현됐고, 6개 article local artifact로 smoke 검증했다.
- retrieval benchmark runner는 3개 reviewed query로 smoke 검증했다. 의미 있는 품질 주장을 하려면 더 많은 labeled example과 공정한 keyword/vector/hybrid 비교 run이 필요하다.

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

### 4.4 Hybrid public search와 PostgreSQL fallback

공개 article search 흐름은 이제 non-blank `query`에 대해 Elasticsearch를 keyword 후보 source로, Qdrant를 vector 후보 source로 사용한다. 후보 ID는 reciprocal rank fusion으로 합쳐지고, Spring Boot는 PostgreSQL에서 API-ready article response를 다시 조립한다. 따라서 projection store는 빠른 후보 생성용으로 남고, 최종 응답의 source of truth는 PostgreSQL이 유지한다.

한쪽 projection path가 실패하면 `KEYWORD_ONLY` 또는 `VECTOR_ONLY`로 degrade하고, 두 path가 모두 실패하면 기존 PostgreSQL field filtering으로 fallback한다. 이때 search mode, candidate count, stale candidate count, failure flag, fallback reason, latency breakdown을 internal in-memory metrics endpoint에 기록해 MVP 사용성을 지키면서도 이후 benchmark 작업으로 확장할 관찰 지점을 남긴다.

### 4.5 Qdrant internal vector search

백엔드는 이제 API-ready PostgreSQL article에서 Qdrant article vector projection을 재생성할 수 있다. Rebuild 흐름은 article embedding input text를 만들고, FastAPI embedding endpoint를 호출하며, embedding provider/model/dimension 일관성을 검증한 뒤, 설정된 Qdrant collection을 재생성하고 article metadata payload와 함께 vector를 저장한다.

Internal vector search endpoint는 query를 embedding하고, Qdrant에서 article ID와 score를 검색한 뒤, API-ready article response를 PostgreSQL에서 다시 읽는다. 응답에는 embedding, Qdrant search, article reload, total elapsed time이 포함된다. Public `/api/articles` 검색은 이제 같은 하위 vector candidate boundary를 재사용하되, diagnostics endpoint는 별도로 유지한다.

### 4.6 Related article bulk lookup과 refactor cleanup

공개 `GET /api/articles?ids=...`는 이제 요청 ID 순서대로 API-ready article을 다시 읽을 수 있다. 프론트엔드는 이를 사용해 related article을 ID 개수만큼 개별 요청하지 않고 한 번의 bulk 요청으로 가져온다. 응답 모양은 list/search article response와 동일하게 유지한다.

이번 review cleanup에서는 article response graph prefetch를 repository fragment로 옮기고, elapsed-time 측정 helper와 published-date parser를 공통화했다. 내부 enrichment response에는 `modelName` metadata를 추가했고, collection failure 관련 의존성은 명시적 생성자 주입으로 바꿨으며, source HTTP fetch에는 configurable connect/read timeout을 추가했다.

## 5. 검증 현황

최근 확인한 검증 명령:

| 영역 | 명령 | 결과 |
| --- | --- | --- |
| Backend review findings refactor | `./gradlew test` -> `./gradlew check` | 성공, bulk article API, parser, timing, repository prefetch, timeout, enrichment metadata 변경 이후 두 명령 모두 `BUILD SUCCESSFUL` |
| Frontend related bulk lookup | `npm test` -> `npm run lint` -> `npm run build` | 성공, Vitest 6개 test file과 29개 test 통과, ESLint error 없음, Vite build 성공 |
| AI enrichment metadata | `.venv/bin/python -m pytest` | 성공, 8 tests 통과, warnings 20개 |
| Backend | `./gradlew test` | 성공 |
| Backend search slice | `./gradlew test --tests com.sigak.search.hybrid.ArticlePublicSearchServiceTest --tests com.sigak.article.service.ArticleServiceTest` | 성공 |
| Backend article API | `./gradlew test --tests com.sigak.article.controller.ArticleControllerTest` | 성공 |
| Collection-to-projection demo smoke | `compose up postgres/elasticsearch/qdrant/ai -> bootRun -> POST /api/internal/collections/runs -> GET /api/internal/collections/failure-events -> ES/Qdrant projection rebuild -> GET /api/articles?query=graph -> GET /api/internal/search-metrics/articles -> compose down` | 성공, collection run `COMPLETED`, duplicate `skippedArticleIds=[6]`, diagnostics `returnedCount=0`, ES/Qdrant 6개 article 색인, public search mode `HYBRID` |
| Collection failure diagnostics runtime smoke | 의도적으로 잘못된 local proxy로 `bootRun` -> `github-blog` 대상 `POST /api/internal/collections/runs` -> `GET /api/internal/collections/failure-events` | 성공, run `cd28c139-0275-465a-a04d-4ff5bea2597a`가 `FETCH_SOURCE`에서 실패했고 `failureEventId=1`, `failureKind=TRANSIENT_FETCH`, `retryable=true`, diagnostics `returnedCount=1` 확인 |
| Internal vector search metrics smoke | `{"query":"graph rag","limit":3}`로 `POST /api/internal/vector-search/articles` -> `GET /api/internal/search-metrics/article-vectors` | 성공, top result는 article `4`, embedding provider `local`, model `sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2`, total elapsed `45ms` |
| Frontend/API local smoke | `npm test` -> `npm run lint` -> `npm run build` -> `curl http://127.0.0.1:5173/` -> `GET /api/articles/4` | test/lint/build/API 확인 성공. 단, in-app browser 자동화는 local URL 보안 정책으로 차단되어 screenshot은 없음 |
| Local hybrid search smoke | `compose up postgres/elasticsearch/qdrant/ai -> bootRun -> ES/Qdrant projection rebuild -> graph/security/vector query -> Qdrant 중단 -> Elasticsearch 중단 -> 둘 다 중단 -> metrics` | 성공, 두 projection 모두 5개 article 색인, `HYBRID`, `KEYWORD_ONLY`, `VECTOR_ONLY`, `POSTGRES_FALLBACK` mode 확인 |
| Backend search metrics | `./gradlew test --tests com.sigak.search.metrics.ArticleSearchMetricsRecorderTest --tests com.sigak.search.metrics.ArticleSearchMetricsControllerTest --tests com.sigak.article.service.ArticleServiceTest` | 성공 |
| Backend Qdrant vector search slice | `./gradlew test --tests 'com.sigak.search.vector.*'` | 성공 |
| Backend FastAPI embedding client | `./gradlew test --tests com.sigak.ai.embedding.FastApiEmbeddingClientTest` | 성공 |
| AI embedding endpoint | `.venv/bin/python -m pytest tests/test_embedding_router.py` | 성공 |
| Frontend tests | `npm test` | 29 tests 통과 |
| Frontend build | `npm run build` | 성공 |
| Frontend lint | `npm run lint` | 성공 |
| AI tests | `.venv/bin/python -m pytest` | 8 tests 통과 |
| Search labeling static tooling | `node` embedded JSON/script syntax check -> `git diff --check` -> `rg` 외부 리소스 scan | 성공, embedded JSON과 browser script syntax 확인, whitespace check 통과, 외부 script/link/http resource reference 없음 |
| Search labeling manual browser smoke | 사용자가 `file:///Users/yonghyun/my-projects/sigak/docs/search-evaluation/labeling.html`을 실제 브라우저에서 열어 수동 테스트 | 사용자 보고 기준 정상 동작 확인. 단, Codex in-app browser의 local `file://` screenshot/click/download parse 자동화는 정책상 차단 |
| Search catalog export focused package tests | `./gradlew test --tests 'com.sigak.search.evaluation.catalog.*'` | 성공 |
| Search catalog export 이후 backend full test | `./gradlew test` | 성공 |
| Search catalog export 이후 backend check | `./gradlew check` | 성공 |
| Search catalog export smoke | `docker compose -f infra/docker-compose.yml up -d --pull never postgres -> pg_isready -> ./gradlew bootRun --args='search-catalog-export --output=../experiments/datasets/raw/articles.catalog.json --limit=50 --catalog-id=api-ready-2026-06-02'` | 성공, `articleCount=6`, output `experiments/datasets/raw/articles.catalog.json` |
| Search catalog JSON parse | `experiments/datasets/raw/articles.catalog.json` 대상 `node -e` schema check | 성공, `catalogId=api-ready-2026-06-02`, article count `6` |
| Search labeling sort/static check | `docs/search-evaluation/labeling.html` 대상 `node` embedded JSON/script syntax check | 성공, article sort control marker와 script syntax 확인 |
| Search label JSON validation | `experiments/datasets/labels/search-labels.api-ready-2026-06-02.2026-06-02.json` 대상 `node` schema/catalog consistency check | 성공, reviewed query 3개, explicit label 12개, 잘못된 article ID/relevance 없음 |

참고:

- AI 서버 테스트는 global `python` 또는 `python3`가 아니라 `ai/.venv`의 Python으로 실행해야 한다.
- 백엔드 테스트는 Testcontainers와 PostgreSQL을 사용한다.

## 6. 앞으로 진행해야 할 일

### 6.1 3주 우선순위

1. Controlled collection operation 보강
   - failure kind가 늘어날 때 manual retry decision table 최신화
   - failure inspection 예시는 실제 runtime sample과 연결해 유지

2. Neo4j graph projection 추가
   - PostgreSQL 기준 article과 topic projection
   - article-topic relationship 저장 또는 projection
   - article detail에 relation reason 또는 related concept 표시

3. Retrieval benchmark와 포트폴리오 metric 추가
   - 정적 라벨링 HTML로 작은 labeled query set 작성
   - 현재 6개 article frozen catalog로 첫 라벨링을 시작하고, 더 큰 catalog를 위해 collection/source curation 보강
   - indexing duration/count metrics
   - search latency p50/p95 metrics
   - 첫 3-query smoke set을 넘어 Recall@5, MRR@5 label 보강
   - benchmark runner를 공정한 keyword/vector/hybrid 비교로 확장
   - README, ADR, demo script, release note

4. Graph 작업 중에도 hybrid search 안정성 유지
   - 공개 article response shape 유지
   - PostgreSQL을 source of truth로 유지
   - Elasticsearch, Qdrant, Neo4j는 rebuildable projection으로 취급

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

### 3단계. Hybrid search evidence 안정화

상태: 현재 search slice 기준 완료

완료 내용:

- Elasticsearch와 Qdrant projection rebuild 흐름이 연결되었다.
- Public `/api/articles?query=...`가 RRF 기반 hybrid search를 사용한다.
- Search metrics가 mode, fallback reason, candidate count, stale candidate count, latency breakdown을 기록한다.
- Local smoke로 `HYBRID`, `KEYWORD_ONLY`, `VECTOR_ONLY`, `POSTGRES_FALLBACK` mode를 확인했다.

### 4단계. Collection trigger와 실행 관측성

현재 상태:

- internal endpoint와 command runner로 source collection을 실행할 수 있다.
- 실행 결과에 fetched/published/skipped/failed count가 포함된다.
- 실패 event는 PostgreSQL에 저장되고 internal diagnostics endpoint로 조회할 수 있다.
- runtime smoke로 duplicate skip 성공 예시와 강제 `TRANSIENT_FETCH` failure event 예시를 확인했다.
- Manual retry guidance는 failure kind별 운영자 행동으로 정리했고, 자동 retry queue는 추가하지 않았다.

남은 일:

- full `collection_runs` lifecycle history는 미뤄져 있다.
- 자동 retry queue/scheduler는 미뤄져 있다.
- 새로운 failure kind나 collector 동작이 추가되면 retry guidance를 함께 갱신한다.

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
- AI/RAG 실사용성: embedding 기반 vector/hybrid search까지 연결됨, enrichment 실사용화는 다음 과제
- collection 실행/자동화: 저장 파이프라인, internal trigger, command runner, persistent failure event까지 연결됨. full run history와 자동 retry는 다음 과제
- 로컬 배포 편의성: 중상, multi-service compose 기반은 마련됐고 실행 문서와 배포 packaging이 다음 과제
- 포트폴리오 문서화: 높음

종합하면, Sigak은 "기획 문서와 기본 CRUD/search 화면만 있는 프로젝트"를 넘어선 상태다. Backend persistence, API spec, source policy, AI enrichment boundary, Elasticsearch/Qdrant 기반 hybrid search까지 연결되어 포트폴리오 설득력이 커졌다.

다만 다음 단계에서 반드시 보여줘야 할 것은 실행 가능한 공개 AI search 흐름이다.

```txt
source trigger -> collect -> persist -> index projections -> hybrid search -> graph-aware detail -> metrics
```

이 흐름을 명시적으로 실행하고 결과를 관측할 수 있으면 Sigak은 이력서에 적힌 AI search와 Graph RAG 경험을 공개적으로 검토할 수 있는 프로젝트가 된다.

## 9. 결론

현재까지의 진행은 MVP 방향과 잘 맞는다. Spring Boot를 주 API boundary로 두고, FastAPI를 AI/RAG 전용 서비스로 분리한 선택은 프로젝트 목표에 적합하다. PostgreSQL은 source of truth로 유지하고, Elasticsearch와 Qdrant는 재생성 가능한 projection store로 사용하고 있으며, Neo4j도 같은 원칙으로 추가하면 된다.

다음 개발의 핵심은 collection trigger, Neo4j projection, graph-aware detail, retrieval benchmark, portfolio packaging을 2026-06-16까지 하나의 재현 가능한 흐름으로 묶는 것이다.
