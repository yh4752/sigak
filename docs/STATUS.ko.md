# Sigak 현재 상태

[English](STATUS.md) | [한국어](STATUS.ko.md)

마지막 업데이트: 2026-10-30

이 문서는 살아 있는 상태 문서다. 로드맵 phase가 완료되거나, 주요 리스크가 바뀌거나, 검증 결과가 오래되면 갱신한다.

## 1. 요약

Sigak은 AI, 소프트웨어 개발, 컴퓨터 과학 분야의 중요한 기술 뉴스를 선별하고, 요약과 "왜 중요한가" 인사이트를 제공하는 AI 기반 뉴스 인사이트 플랫폼이다.

현재 프로젝트는 문서화, 기본 모노레포 구조, Spring Boot 백엔드 API, PostgreSQL 기반 영속성, React 프론트엔드, FastAPI mock AI 서버, selected-source 수집-영속화 파이프라인까지 MVP의 주요 골격을 갖춘 상태다.

2026-05-27 기준으로 MVP 목표를 3주 공개 포트폴리오 릴리즈로 재정렬했다. Sigak v0.1은 collection, PostgreSQL source-of-truth 저장, Elasticsearch keyword search, Qdrant vector search, Neo4j graph projection, hybrid retrieval, graph-aware article detail, 재현 가능한 local metrics를 보여주는 AI search vertical slice를 목표로 한다.

2026-07-12 기준 초기 `/research` dashboard를 구현했다. Spring Boot는 `GET /api/research/overview`로 커밋된 retrieval comparison, graph-aware smoke, expanded catalog artifact를 읽고, React는 Zod 검증을 거쳐 포트폴리오용 evidence dashboard를 렌더링한다. 당시 기록된 세션에서 research backend focused test와 frontend 전체 test/lint/build gate는 통과했고, Docker와 PostgreSQL을 사용할 수 없어 backend 전체 test/check gate와 runtime boot smoke는 차단되어 미검증으로 남았다. 다음 구현 과제는 설계와 계획이 완료된 운영 문서 RAG 평가 하네스이며, 이후 생성되는 artifact는 smoke data를 과장하지 않는 범위에서 dashboard의 RAG, failure, latency, token, cost section으로 이어진다.

2026-07-26에는 깨끗한 `main` worktree에서 frontend lockfile 불일치를 발견했다. 기존 `npm ci`는 기록된 의존성 트리를 설치하지 못했으며, lockfile을 다시 해석하고 Axios를 `1.18.1`로 올린 뒤 최신 React Router v7(`7.18.1`)을 유지해 clean install 재현성을 복구했다. 이후 `npm test`, lint, build는 8개 파일과 41개 test 기준으로 통과했고, Docker가 필요 없는 research backend focused test도 통과했다. 반면 Docker daemon이 없어 backend 전체 `test`와 `check`는 당시 218개 중 30개 Testcontainers 실패로 차단됐다. `npm audit --omit=dev`는 React Router RSC advisory를 high severity dependency entry 2개로 계속 보고한다. Sigak은 RSC·SSR runtime이 없는 client-rendered Vite 앱이지만, 안전한 upstream release가 나오면 의존성을 다시 검토해야 하는 리스크로 남긴다.

2026-10-10에는 Docker를 사용할 수 있어 backend 전체 test/check(218개), frontend test/lint/build(41개), AI pytest(8개)를 통과했다. PostgreSQL을 연결한 runtime smoke에서 기존 research artifact와 41개 article catalog를 확인했고, 검색 projection이 내려간 상태의 검색은 1개 결과와 `POSTGRES_FALLBACK`을 반환했다. 운영 문서 검색 평가 도구의 snapshot, 근거 검증, fixed/heading chunking, BM25/dense/hybrid, slice, intent bootstrap, 원자적 artifact 저장, 검토 문서를 구현했으며 fixture test 70개가 통과했다. Deterministic 3-query smoke는 18개 run을 실행 오류 없이 생성했다. 전체 초안은 24개 paired answerable intent, 12개 unanswerable, 16개 cross-lingual semantic query로 구성되며 60개 모두 `DRAFT`다. Checkpoint B 전에 사람이 dataset을 검토해야 한다. 전체 multilingual 검색 평가, reranking, 생성, judge, 답변 calibration은 미검증 또는 미구현이다. 위 dependency advisory는 이번 세션에 재검사하지 않았다.

2026-10-30 재검증에서 backend 전체 test/check 218개, frontend test/lint/build 41개, AI pytest 8개, 평가 도구 pytest 70개가 통과했다. 평가 도구는 sandbox의 tokenizer 사전 다운로드 차단으로 처음 실패했지만 네트워크를 허용한 동일 검사에서 통과했다. 기존 개발 서비스가 중지돼 있어 다시 시작했고 PostgreSQL API-ready 기사 41개를 보존했다. ES/Qdrant 각각 41개를 인덱싱했고 Neo4j는 article node 41개, topic node 18개, article-topic 51개, related 10개를 재생성했다. `graph` 검색은 fallback 없이 `HYBRID`, 결과 20개였다.

별도 `infra/deploy/` 단일 서버 구성을 구현했다. 소스에서 빌드한 backend/frontend 이미지, Caddy 단일 origin, 내부 API 차단, 필수 비밀번호, 분리된 영구 volume을 사용한다. 로컬 배포 주소 `http://127.0.0.1:8088`에서 실제 HTTP smoke 6개가 통과했다. 새 DB는 샘플 기사 5개이며 local multilingual embedding과 vector/graph rebuild, `HYBRID` 검색도 확인했다. Linux clean install에서 드러난 optional peer lock 항목 두 개를 보완하고 이미지 빌드를 통과했다. ARM을 지원하지 않던 JDK Alpine tag는 Jammy로 교체했다. 독립 배포 검토에서 중대한 잔여 문제는 없었다. 클라우드 계정·서버·도메인은 없다. 임시 Cloudflare HTTPS 공유 주소에서는 같은 HTTP 검사 6개와 브라우저 표시를 확인했으며, 상시 호스팅과 사용자 도메인의 TLS는 미검증이다. 임시 주소는 이 컴퓨터와 tunnel 프로세스가 켜져 있어야 한다. 초기 배포 작업에는 수집 스케줄이 없었고 아래 후속 작업에서 연결했다. 별도 상시 모니터링은 미완이다. 초기 Git 확인에서 로컬 HEAD는 원격 main보다 17개 commit 앞서 있었고 10월 RAG 작업은 미커밋이었다. 검증된 변경은 `codex/ops-rag-evaluation`에 push하고 draft PR #20으로 원격 반영했다. Main 병합은 하지 않았다.

### 기술 뉴스 자동 수집 후속 작업 (2026-10-30)

사용자는 별도 news-rag의 경향신문 데이터 동기화 대신 Sigak 기술 뉴스 출처 자동 실행을 선택했다. 단일 Spring fixed-delay job이 기존 source registry와 수집 pipeline, PostgreSQL 중복 검사·저장, keyword/vector/graph 갱신을 재사용한다. 개발 기본값은 꺼짐이며 배포에서는 시작 후 30초, 완료 후 600초 간격으로 출처별 최대 20개를 처리한다. 내부 읽기 전용 상태는 마지막 전체 성공·신규 저장·일부 실패·미완 projection 복구를 구분한다. arXiv cooldown은 volume에 보존하고 process snapshot은 재시작 시 초기화한다.

Backend 전체 test/check 235개가 통과했다. 짧은 주기의 실제 검증에서 RSS 신규 50개를 저장했고 다음 회차는 중복 50개를 건너뛰며 불필요한 rebuild를 생략했다. 전체 6개 출처와 운영 간격 600초로 복원한 첫 실행은 발견 80개, 신규 23개, 중복 57개, 출처·기사 실패 0개로 완료됐다. 운영 두 번째 실행도 이전 완료 후 600초 뒤인 10:00:50(KST)에 시작해 10:00:57 완료됐으며, 6개 출처 성공·신규 0개·중복 80개를 확인했다. 검증 시점 배포 DB는 실제 수집 73개와 seed 5개, 총 78개다. ES/Qdrant도 각각 78개이며 최신 실기사를 HYBRID 검색에서 찾았고 stale 후보 0·fallback 없음, graph topic context도 확인했다. 이 자동 수집 검증 당시 mock enrichment는 유지했고 아래 상세 요청 요약 후속 작업에서 요청 시 교체한다. RAG dataset은 수동 경로를 명확히 하고 더 이상 false premise가 아닌 질문을 교체해 v2로 올렸으며 60개 모두 DRAFT, v1도 보존했다. 장기 무인 운영과 상시 호스팅은 미검증이다.

### 상세 방문 시 실제 AI 요약 (2026-10-30)

사용자 비용 절약 의견을 반영해 수집은 AI 없이 원문부터 공개하고, 실제 상세 진입 때만
공유 비동기 요약 작업을 요청한다. 여러 방문자의 요청은 하나로 합치고 저장된 결과를
재사용한다. 목록·검색·공개 GET은 유료 생성을 시작하지 않는다. Spring이 PostgreSQL의
작업·이력·비용을 관리하고 private FastAPI의 고정 OpenAI provider를 호출한다.
요약/why-it-matters는 생성 전 null이며 소개글·원문 링크를 먼저 표시한다.
Keyword/vector/fallback 검색은 생성된 요약과 독립적인 수집 텍스트를 사용한다.

Backend 전체 test/check249개, frontend test/lint/build47개, AI pytest55개를 통과했다.
RAG fixture70개도 통과했다. 바뀐 architecture/fallback 계약의 초안 근거·정답4개를 v3로
갱신하고 v2는 원문 그대로 보존했으며, 사람 검토0개는 그대로다. 실제 소개글 기반
요약2건을 저장했고 관측 시 token 기준 추정 비용 합계677 USD micros(0.000677달러),
앱 보수적 차감40000micros(0.04달러), 호출2회·미확정0건이었다. 같은 기사에6회 동시
재요청해도 READY 결과만 재사용했고 호출 수는 늘지 않았다. Backend 재시작 후 첫 결과와
비용 보존도 확인했다. 브라우저에서 생성 중 화면과 한국어 요약·근거 표시를 확인했다.

승인 예산은 누적5달러이며 앱은4달러·일20회·총200회에서 멈춘다. 호출마다0.02달러를
보수적으로 차감하고 실제 token 추정액과 구분한다. 기본 pilot은 최대10회이며 실제 검증 후
active를 명시적으로 설정한다. 미확정 호출은 자동 재시도하지 않고 인증·모델·결제·사용량
오류는 추가 호출을 멈춘다. 사용자 요청으로 요약 권한만 있는 키를 발급해 비공개 배포
설정에 저장했으며 만료일은2026-11-29다. 개발 mock은 유지하고 개발 AI 포트도 localhost로
제한했다. 배포 AI는 외부 포트가 없다. 계정 전체 잔액·자동 충전·장기 운영은 미검증이다.
짧은 소개글2건은 전체 요약 품질이나 기사 전문 수집을 검증한 결과가 아니다. 최종 배포는 사용자 승인한
active 모드다. 마지막 관측 catalog는148개(실제143개+seed5개)이고 실제 요약은2개뿐이다.
정기 수집·projection 한 회차가 완료돼 세 저장소가각148개·미완 복구false였으며 기사7을
HYBRID·stale0·fallback없음으로 찾았다. 실제 Origin header와 다른 origin 차단을 포함한
로컬/외부 HTTPS smoke는각7개 통과했다. 만료된 임시 터널은 갱신하고 정확한 public origin을
설정·검증했다. 상시 호스팅은 남은 과제다.

현재 단계의 핵심 평가는 다음과 같다.

| 영역 | 현재 상태 | 평가 |
| --- | --- | --- |
| 제품 방향 | MVP 범위와 비범위가 문서화됨 | 양호 |
| 백엔드 | persisted article list/detail/search API 구현, query 검색은 Elasticsearch keyword 후보와 Qdrant vector 후보를 함께 사용하고 PostgreSQL fallback으로 연결, public graph-aware article detail, internal Qdrant vector diagnostics, Neo4j graph projection/context, collection failure diagnostics API 구현 | 양호, API-ready filtering, hybrid fallback 검색, AI client wiring, internal vector search, public graph context, graph projection context, collection failure 조회 보완 완료 |
| 프론트엔드 | 홈, 검색, 상세, 관련 기사, graph reason 표시 UI, `/research` evidence dashboard 구현 | 양호, 상세 화면 stale state 보완과 graph reason fallback 처리 완료. Research metric은 Zod 검증을 거친 backend API에서 렌더링 |
| AI 서버 | FastAPI mock·상세 요청 시 실제 OpenAI 요약과 사용량·근거 검증, configurable embedding provider 구현, local FastEmbed multilingual mode가 기본 retrieval 경로 | AI/RAG 경계 초기 완료, Qdrant projection이 Spring Boot를 통해 embedding vector를 소비함 |
| 데이터 | PostgreSQL schema, seed data, graph-ready metadata, 수집 article 저장 구현 | MVP 기반 완료 |
| Search infra | Elasticsearch readiness, keyword projection/search, Qdrant vector projection/search, Neo4j graph projection/context lookup, public hybrid search, fallback mode, search metrics, strict keyword/vector/hybrid retrieval benchmark run, graph-aware evaluation artifact, 확장 API-ready 검색 catalog, 초기 research dashboard artifact reader 연결 완료 | 현재 phase의 keyword/vector/hybrid/graph projection과 graph-aware evaluation slice 완료. Dashboard는 smoke-only limitation을 표시하며, 품질 주장을 하려면 더 큰 label set이 필요함 |
| 인프라 | PostgreSQL, Elasticsearch, Qdrant, Neo4j, AI server, SchemaSpy Docker Compose 구성 | 로컬 기반 양호, portfolio packaging 확장이 다음 단계 |
| 문서 | README, API spec, roadmap, ADR, 검색 평가 가이드/도구, experiments 디렉터리 가이드, smoke benchmark runner, system comparison runner, graph-aware evaluation runner 정리 | 양호, 사용자 수동 smoke를 거친 정적 HTML workflow로 retrieval benchmark 라벨링을 시작할 수 있고 6개 article local catalog 기준 첫 3-query smoke label set, 41개 article 확장 catalog, public smoke, keyword/vector/strict-hybrid/public 비교, graph-aware evaluation artifact 생성 흐름이 존재함 |

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
- `docs/API_SPEC.md`에 internal retrieval evaluation endpoint와 strict `HYBRID` 실험 run / `PUBLIC` 사용자-visible search behavior의 차이 정리
- 확장 API-ready 검색 catalog artifact `experiments/datasets/raw/articles.catalog.api-ready-2026-06-05.json`를 `catalogId=api-ready-2026-06-05`, article count `41`로 생성했다. 기존 6개 article 기준 `api-ready-2026-06-02` smoke catalog는 baseline으로 보존한다.
- root `README.md`와 `docs/DEMO_FLOW.md`가 현재 v0.1 portfolio demo flow를 설명한다. PostgreSQL source-of-truth 저장, Elasticsearch/Qdrant/Neo4j rebuildable projection, public hybrid search, graph-aware detail, smoke-only retrieval/graph artifact 확인 흐름을 포함한다.

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
  - `/research`
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
- retrieval comparison, graph-aware smoke evidence, expanded catalog 상태, limitation panel을 보여주는 research dashboard 화면 구현
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

- 단일 backend 정기 수집과 배포 기본 활성화, 내부 process 진단을 구현했다. 전체 실행 이력·분산 스케줄링은 미완이다.
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
- Portfolio README와 local demo flow에 graph projection과 smoke artifact 확인 경로를 반영했다. 단, expanded benchmark 품질 주장은 reviewed label이 생길 때까지 막아 둔다.

보완 필요:

- Graph-aware evaluation runner는 구현 및 smoke 검증까지 완료했다. 41개 article 확장 catalog는 생성했지만, 품질 주장을 하려면 더 큰 label set이 필요하다.
- 검색 metric은 internal in-memory endpoint와 재현 가능한 smoke benchmark artifact 경로를 모두 갖춘 상태다.
- API-ready PostgreSQL article을 frozen catalog로 export하는 command는 구현됐고, 6개 article local artifact와 별도 41개 article 확장 artifact로 검증했다.
- retrieval benchmark runner는 3개 reviewed query로 smoke 검증했다.
- system comparison runner는 같은 label set에서 keyword, vector, strict hybrid, public artifact를 생성할 수 있다. 첫 3-query/6-article comparison smoke에서는 네 system 모두 failed/degraded run 없이 완료됐지만, 의미 있는 품질 주장을 하려면 더 많은 labeled example이 필요하다.
- graph-aware evaluation runner는 public search와 public graph-context endpoint만 사용해 graph context run, query별 metric, summary, markdown report append artifact를 생성할 수 있다.

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

### 4.6 Neo4j internal graph projection과 context 조회

백엔드는 이제 API-ready PostgreSQL article에서 Neo4j article graph projection을 재생성할 수 있다. Projection은 article metadata, 정규화된 topic, position이 있는 `HAS_TOPIC` 관계, relation type과 저장된 reason이 있는 `RELATED_TO` 관계를 저장한다. PostgreSQL은 source of truth로 유지되며, Neo4j에는 raw content, summary, why-it-matters 본문, embedding vector, secret을 저장하지 않는다.

Internal graph context lookup은 article의 projected topic, topic-neighbor article ID, outgoing related article, relation reason, shared topic, Neo4j timing metadata를 반환한다.

### 4.7 Public graph-aware article detail

Public article detail은 이제 companion endpoint인 `GET /api/articles/{id}/graph-context`를 호출할 수 있다. List, search, detail, bulk related article lookup이 공유하는 `ArticleResponse` 모양은 그대로 유지한다. Public graph context response는 related article reason, shared topic, topic context를 노출하지만 internal Neo4j timing과 relation type은 숨긴다.

프론트엔드는 `relatedArticleReasons`를 이미 조회한 related article과 `articleId`로 합치고, reason이 있으면 related article 제목 아래에 표시한다. Neo4j context가 없거나 조회할 수 없으면 public endpoint는 빈 graph context로 degrade하고 article detail 화면은 유지한다.

### 4.8 Related article bulk lookup과 refactor cleanup

공개 `GET /api/articles?ids=...`는 이제 요청 ID 순서대로 API-ready article을 다시 읽을 수 있다. 프론트엔드는 이를 사용해 related article을 ID 개수만큼 개별 요청하지 않고 한 번의 bulk 요청으로 가져온다. 응답 모양은 list/search article response와 동일하게 유지한다.

이번 review cleanup에서는 article response graph prefetch를 repository fragment로 옮기고, elapsed-time 측정 helper와 published-date parser를 공통화했다. 내부 enrichment response에는 `modelName` metadata를 추가했고, collection failure 관련 의존성은 명시적 생성자 주입으로 바꿨으며, source HTTP fetch에는 configurable connect/read timeout을 추가했다.

### 운영 문서 검색 평가 준비 (2026-10-10)

- `experiments/rag-evaluation/` 구현, fixture test 70개와 6개 baseline deterministic smoke 통과.
- 60개 초안의 정확한 인용 근거와 `datasets/review.md` 준비. 사람 검토는 미완.
- 전체 평가는 draft, 서로 다른 동일 evidence ID 정의, 원문 불일치와 불완전한 smoke pair를 거부한다.
- 생성, reranking, judge, dashboard 연동은 이번 준비 단계의 완료 범위에 포함하지 않는다.

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
| 확장 search catalog export focused package tests | `./gradlew test --tests 'com.sigak.search.evaluation.catalog.*'` | 성공, `BUILD SUCCESSFUL` |
| 확장 search catalog export smoke | `docker compose -f infra/docker-compose.yml up -d --pull never postgres -> pg_isready -> ./gradlew bootRun --args='search-catalog-export --output=../experiments/datasets/raw/articles.catalog.api-ready-2026-06-05.json --limit=50 --catalog-id=api-ready-2026-06-05'` | 성공, `articleCount=41`, output `experiments/datasets/raw/articles.catalog.api-ready-2026-06-05.json` |
| 확장 search catalog JSON parse | `experiments/datasets/raw/articles.catalog.api-ready-2026-06-05.json` 대상 `node -e` schema와 duplicate-ID check | 성공, `catalogId=api-ready-2026-06-05`, article count `41`, 첫 export ID `3`, 마지막 export ID `16` |
| Smoke baseline 내용 보존 | 기존 catalog와 label 대상 `node -e` content check | 성공, 기존 catalog `catalogId=api-ready-2026-06-02`, article count `6`; 기존 label `catalogId=api-ready-2026-06-02`, `catalogArticleCount=6`, reviewed query count `3`, explicit label count `12` |
| Smoke latest result 보존 | `git status --short experiments/results/retrieval/latest experiments/results/graph/latest`와 `git diff --name-only -- experiments/results/retrieval/latest experiments/results/graph/latest` | 성공, 변경된 result 경로 없음 |
| Portfolio docs refresh | 오래된 Neo4j 문구 scan -> smoke artifact file check -> retrieval/graph artifact summary 조회 -> `git diff --check` | 성공, 오래된 Neo4j limitation 문구가 남지 않았고 참조한 smoke artifact가 존재함. retrieval/graph summary는 `catalogId=api-ready-2026-06-02`, `evaluatedQueryCount=3`를 반환했고 whitespace check 통과. Runtime Docker smoke는 재실행하지 않음 |
| Search labeling sort/static check | `docs/search-evaluation/labeling.html` 대상 `node` embedded JSON/script syntax check | 성공, article sort control marker와 script syntax 확인 |
| Search label JSON validation | `experiments/datasets/labels/search-labels.api-ready-2026-06-02.2026-06-02.json` 대상 `node` schema/catalog consistency check | 성공, reviewed query 3개, explicit label 12개, 잘못된 article ID/relevance 없음 |
| Retrieval benchmark runner tests | `node --test experiments/scripts/retrieval-benchmark/*.test.mjs` | 성공, 81 tests, 81 passed, 0 failed |
| Retrieval comparison smoke | `compose up postgres/elasticsearch/qdrant -> deterministic AI server -> SIGAK_INTERNAL_SEARCH_EVALUATION_ENABLED=true backend bootRun -> ES/Qdrant projection rebuild -> node retrieval-benchmark --systems=keyword,vector,hybrid,public` | 성공, ES 6개 article 색인, Qdrant 6개 vector 색인, 3개 query 평가. keyword, vector, strict hybrid, public 모두 completed/failed/degraded count가 `3/0/0`. Macro Recall@5는 keyword `0.6666666666666666`, vector/hybrid/public `0.8333333333333334`. report warning은 label set 10개 미만, catalog 20개 미만 제한을 표시 |
| Backend Neo4j graph focused tests | `./gradlew test --tests 'com.sigak.search.graph.*'` | 성공 |
| Backend OpenAPI graph docs test | `./gradlew test --tests com.sigak.docs.OpenApiDocumentationTest` | 성공 |
| Backend full graph gate | `./gradlew test` -> `./gradlew check` | 성공, 두 명령 모두 `BUILD SUCCESSFUL` |
| Neo4j graph projection smoke | `compose up neo4j -> bootRun -> POST /api/internal/graph-projections/articles/rebuild -> GET /api/internal/graph/articles/4/context -> cypher count checks` | 성공, rebuild 응답은 `articleNodeCount=26`, `topicNodeCount=18`, `hasTopicRelationshipCount=36`, `relatedToRelationshipCount=10`, `durationMs=1037`; article `4` context는 `Graph RAG` topic과 stored reason이 있는 related article `1`, `5`를 반환; Cypher count도 rebuild 응답과 일치 |
| Backend public graph context focused tests | `./gradlew test --tests com.sigak.article.service.ArticlePublicGraphContextServiceTest --tests com.sigak.article.controller.ArticleControllerTest --tests com.sigak.docs.OpenApiDocumentationTest` | 성공 |
| Frontend public graph context focused tests | `npm test -- articles.test.ts ArticleDetailPage.test.tsx` | 성공, 2 files, 20 tests passed |
| Backend full public graph detail gate | `./gradlew test` -> `./gradlew check` | 성공, 두 명령 모두 `BUILD SUCCESSFUL` |
| Frontend full public graph detail gate | `npm test` -> `npm run lint` -> `npm run build` | 성공, `npm test`는 6 files, 33 tests passed |
| Public graph context smoke | `compose up neo4j -> bootRun -> POST /api/internal/graph-projections/articles/rebuild -> GET /api/articles/4/graph-context -> stop neo4j -> GET /api/articles/4/graph-context` | 성공, rebuild 응답은 `articleNodeCount=26`, `topicNodeCount=18`, `hasTopicRelationshipCount=36`, `relatedToRelationshipCount=10`, `durationMs=1535`; article `4` public graph context는 `timings` 없이 article `1`, `5`의 relation reason을 반환; `GET /api/articles/999/graph-context`는 `404`, `GET /api/articles/0/graph-context`는 `400`; Neo4j unavailable fallback은 `{"articleId":4,"relatedArticleReasons":[],"topics":[]}` 반환 |
| Graph-aware evaluation 설계 문서 자체 점검 | `rg -n "TBD\|TODO\|FIXME\|미정\|나중에 구현\|적절히\|필요하면" docs/superpowers/specs/2026-06-03-graph-aware-evaluation-design.md \|\| true` | 성공, placeholder 출력 없음 |
| Graph-aware evaluation runner tests | `node --test experiments/scripts/retrieval-benchmark/*.test.mjs` | 성공, 81 tests, 81 passed, 0 failed |
| Graph-aware evaluation smoke | `compose up neo4j -> SIGAK_INTERNAL_SEARCH_EVALUATION_ENABLED=true backend bootRun -> ES/Qdrant/Neo4j projection rebuild -> node retrieval-benchmark --systems=public --include-graph-context` | 성공, ES `26`개 article 색인, Qdrant `26`개 vector 색인(`sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2`), Neo4j rebuild는 `articleNodeCount=26`, `topicNodeCount=18`, `hasTopicRelationshipCount=36`, `relatedToRelationshipCount=10` 반환. 3개 query 평가 결과 Graph Context Coverage@5 `0.3333333333333333`, Graph Reasoned Coverage@5 `0.3333333333333333`, graph context failure rate `0`, empty context rate `0`, average graph latency `33.53333333333333ms`. warning은 label set smoke-only, graph density 부족, public API round-trip latency, stored projection reason 한계를 표시 |
| Ops RAG evaluation 설계 자체 점검 | `git diff --check` -> placeholder scan -> JSON example parse -> dataset bias control marker check -> corpus allowlist file check | 성공, whitespace와 placeholder 출력 없음. JSON 예제 11개 parse, full-60/paired-intent/slice/factorial/five-generation/matched-budget/response-language/human-review/claim-limit marker 확인, allowlist corpus 파일 7개 존재 확인 |
| Ops RAG 구현 계획 자체 점검 | plan marker script -> placeholder scan -> `git diff --check` | 성공, required header, 12개 task, TDD step, full-60/paired/cross-lingual/matched-budget/factorial/bootstrap/five-generation/50-human-review/claim-limit marker 확인. Markdown fence 264개가 짝수로 닫혔고 placeholder와 whitespace 오류 없음 |
| Research dashboard backend focused test | `./gradlew test --tests com.sigak.research.config.ResearchArtifactPropertiesTest --tests com.sigak.research.service.ResearchArtifactReaderTest --tests com.sigak.research.service.ResearchOverviewServiceTest --tests com.sigak.research.controller.ResearchOverviewControllerTest` | 성공, `BUILD SUCCESSFUL in 5s` |
| Research dashboard frontend focused test | `npm test -- research.test.ts NavBar.test.tsx ResearchPage.test.tsx App.test.tsx` | 성공, 4 files와 11 tests 통과 |
| Research dashboard frontend full gate | `npm test` -> `npm run lint` -> `npm run build` | 성공, `npm test`는 8 files와 41 tests 통과, ESLint 오류 없음, Vite build 완료 |
| Research dashboard backend full gate | `./gradlew test` -> `./gradlew check` | 차단, local Docker daemon이 내려가 있어 Testcontainers suite에서 218개 중 30개 실패 |
| Research dashboard API boot smoke | `SIGAK_RESEARCH_ARTIFACT_ROOT=../experiments ./gradlew bootRun` | 차단, local PostgreSQL이 내려가 있어 Flyway가 `localhost:5432`에 연결하지 못했고 `GET /api/research/overview` runtime smoke는 완료하지 못함 |
| Main merge frontend clean-install gate | `npm install --package-lock-only --ignore-scripts` -> `npm ci` -> `npm test` -> `npm run lint` -> `npm run build` | 성공, 다시 해석한 lockfile이 clean install되며 Vitest 8 files와 41 tests 통과, ESLint 오류 없음, Vite build 완료 |
| Main merge research backend focused test | `./gradlew test --tests com.sigak.research.config.ResearchArtifactPropertiesTest --tests com.sigak.research.service.ResearchArtifactReaderTest --tests com.sigak.research.service.ResearchOverviewServiceTest --tests com.sigak.research.controller.ResearchOverviewControllerTest` | 성공, `BUILD SUCCESSFUL` |
| Main merge backend full gate | `./gradlew test`, `./gradlew check` | 차단, Docker daemon이 없어 각각 Testcontainers suite까지 실행한 뒤 218개 중 30개 실패 |
| Main merge production dependency audit | `npm audit --omit=dev` | 미통과, 최신 v7에서도 React Router RSC advisory가 high severity dependency entry 2개로 남음. Sigak은 RSC·SSR runtime이 없지만 passing audit으로 처리하지 않고 의존성 재검토 리스크로 기록 |

참고:

- AI 서버 테스트는 global `python` 또는 `python3`가 아니라 `ai/.venv`의 Python으로 실행해야 한다.
- 백엔드 테스트는 Testcontainers와 PostgreSQL을 사용한다.

### 2026-10-10 검증

| 영역 | 명령 | 결과 |
| --- | --- | --- |
| 2026-10-10 backend 전체 gate | `./gradlew test check` | 성공, 218개 test, 실패·오류·skip 없음 |
| 2026-10-10 frontend 전체 gate | `npm test`, `npm run lint`, `npm run build` | 성공, 8개 파일·41개 test |
| 2026-10-10 AI server | `.venv/bin/python -m pytest` | 성공, 8개 test, 기존 deprecation warning 20개 |
| 2026-10-10 runtime smoke | research overview, article query `graph`, internal search metrics | Research retrieval/graph available, 확장 catalog 41개. 검색 1개 결과, `POSTGRES_FALLBACK`, `KEYWORD_SEARCH_FAILED; QDRANT_SEARCH_FAILED` |
| 2026-10-10 Ops retrieval fixture | package pytest, compileall, pip check | 성공, 70개 test·컴파일 성공·의존성 충돌 없음 |
| 2026-10-10 dataset 준비 | `validate-dataset --allow-draft`, `prepare-review` | 원문 근거 검증. 24개 intent·48개 answerable·12개 unanswerable·16개 cross-lingual, 사람 검토 0개. fixed 42/heading 80 chunk |
| 2026-10-10 deterministic smoke | `evaluate-retrieval --smoke`, `build-report` | 성공, 3개 query·18개 run·실행 실패 0개. 품질 결론 근거로 사용 불가 |

## 6. 앞으로 진행해야 할 일

### 6.1 3주 우선순위

배포 후속 단계: 호스팅 계정·예산·hostname을 정하고 검증된 단일 서버 구성을 배치한 뒤 상시 외부 접속·사용자 도메인 TLS를 확인한다. 로컬 접속과 패키징은 검증했고 유료 서버는 생성하지 않았다.

RAG 선행 단계는 유지한다: `experiments/rag-evaluation/datasets/review.md` 사람 검토 후 Checkpoint B에 착수한다. 검색 평가 준비와 deterministic smoke는 구현됐으며 전체 품질 평가는 미완이다.

1. 운영 문서 RAG 평가 하네스 구현
   - 공개 Sigak 운영 문서를 versioned 운영 매뉴얼 corpus로 사용
   - 24개 answerable intent의 lexical/semantic query pair와 12개 unanswerable/adversarial query로 총 60개 reviewed query 작성
   - lexical, semantic-paraphrase, cross-lingual, query-type result slice 분리
   - paired query form을 독립 표본으로 취급하지 않도록 intent-cluster bootstrap confidence interval 계산
   - fixed-size와 heading-aware chunking 비교
   - BM25, dense, hybrid, hybrid+reranking retrieval 비교
   - no-RAG와 grounded RAG 답변 비교
   - deterministic citation check, LLM judge, human disagreement, latency, token usage, cost, failure case 기록

2. Controlled collection operation 보강
   - failure kind가 늘어날 때 manual retry decision table 최신화
   - failure inspection 예시는 실제 runtime sample과 연결해 유지

3. Graph-aware evaluation 근거 확장
   - `api-ready-2026-06-05` 41개 article 확장 catalog 기준 label 작성
   - 현재 3-query/6-article smoke dataset보다 큰 label set 확보
   - 확장 catalog에서 public graph context를 단순 related article, retrieval baseline과 비교
   - smoke data만으로 과장하지 않으면서 graph reason이 article detail에 도움이 되는 경우와 그렇지 않은 경우 문서화

4. Article retrieval benchmark와 포트폴리오 metric 확장
   - 현재 6개 article frozen catalog와 3-query smoke set을 재현성 baseline으로 유지
   - 확장 benchmark를 실행하기 전에 41개 article catalog 기준 10-15개 reviewed query label 작성
   - indexing duration/count metrics
   - search latency p50/p95 metrics
   - 첫 3-query smoke set을 넘어 Recall@5, MRR@5 label 보강
   - label set이 커져도 keyword/vector/strict-hybrid/public 비교 artifact를 재현 가능하게 유지
   - label-dependent benchmark path가 끝난 뒤 최종 ADR, release note, portfolio README polish

5. 새 RAG evaluation artifact가 준비된 뒤 초기 research dashboard 확장
   - 현재 retrieval, graph, expanded catalog, limitation panel은 안정적으로 유지
   - 검증된 artifact에서만 RAG, failure, latency, token, cost section 추가
   - artifact가 없어도 page 전체가 깨지지 않도록 section-level unavailable state 유지

6. 평가 작업 중에도 hybrid search 안정성 유지
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
- 일반 자동 retry queue는 미완이다. 정기 수집은 이후 회차에서 미완 projection 복구를 재시도한다.
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

현재까지의 진행은 MVP 방향과 잘 맞는다. Spring Boot를 주 API boundary로 두고, FastAPI를 AI/RAG 전용 서비스로 분리한 선택은 프로젝트 목표에 적합하다. PostgreSQL은 source of truth로 유지하고, Elasticsearch, Qdrant, Neo4j는 재생성 가능한 projection store로 사용하고 있다.

다음 단계는 준비된 운영 문서 dataset의 사람 검토와 RAG 평가 하네스 Checkpoint B 구현이며, 그 뒤로 collection operation 보강, retrieval 및 graph-aware evaluation 근거 확장, dashboard artifact, portfolio packaging을 진행한다.
