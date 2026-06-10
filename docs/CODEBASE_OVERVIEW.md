# Codebase Overview

Last updated: 2026-06-10

이 문서는 Sigak 코드베이스 전체(backend/frontend/ai)의 구조, 책임, 핵심 흐름을 한 번에 파악하기 위한 정리 문서다. 규칙은 `AGENTS.md`, 현재 상태는 `docs/STATUS.md`, API 계약은 `docs/API_SPEC.md`가 기준이다.

## 1. 전체 구조

```txt
frontend (React + Vite)
    └── HTTP → backend (Spring Boot, Kotlin)  ← 유일한 public API 경계
                  ├── PostgreSQL (source of truth, Flyway)
                  ├── Elasticsearch (keyword projection, 재생성 가능)
                  ├── Qdrant (vector projection, 재생성 가능)
                  ├── Neo4j (graph projection, 재생성 가능)
                  └── HTTP → ai (FastAPI)  ← enrichment/embedding 전용, frontend는 직접 호출하지 않음
```

규모(주요 소스 기준): backend main ~5.8k LOC / backend test ~6.7k LOC / frontend ~1.0k LOC / ai ~0.4k LOC.

## 2. Backend (`backend/src/main/kotlin/com/sigak`)

### 패키지 책임

| 패키지 | 책임 |
| --- | --- |
| `article` | 공개 article API의 도메인/DTO/서비스/컨트롤러. `ArticleService`가 목록·검색·상세·ID 벌크 조회를 담당 |
| `source` | 뉴스 소스 엔티티와 repository |
| `collection` | 수집 파이프라인: RSS/Atom·arXiv collector → normalizer → enrichment → 영속화, 실패 분류/기록, REST·CLI 트리거 |
| `search.config` | Elasticsearch/Qdrant/Neo4j 클라이언트 빈과 `sigak.search.*` 설정 바인딩 |
| `search.service` | Elasticsearch keyword 검색 + 인프라 health check |
| `search.projection` | Elasticsearch article projection rebuild |
| `search.vector` | Qdrant vector projection rebuild, 내부 vector 검색 API, vector 검색 메트릭 |
| `search.graph` | Neo4j graph projection rebuild + article graph context 조회 |
| `search.hybrid` | keyword/vector 후보 조회(`ArticleRetrievalCandidateService`), RRF 융합, 공개 검색 모드 결정(`ArticlePublicSearchService`) |
| `search.metrics` | 공개 검색 메트릭 (in-memory 최근 100건) |
| `search.evaluation` | retrieval 벤치마크 API(`@ConditionalOnProperty`로 기본 비활성)와 검색 카탈로그 export CLI |
| `ai` | FastAPI embedding 클라이언트 (HTTP/1.1 고정 RestClient) |
| `common` | `Measured`/`measureElapsed`/`MeasuredAttempt`/`runCatchingMeasured`(time), `RecentObservationWindow`/`percentileOf`(metrics) |
| `config` | CORS(GET, localhost:5173만 허용), OpenAPI 메타 |

### 핵심 흐름 1 — 공개 검색 `GET /api/articles?query=`

1. `ArticleController` → `ArticleService.getArticles(query)`
2. `ArticlePublicSearchService.search()`가 `ArticleRetrievalCandidateService`로 keyword(Elasticsearch)·vector(임베딩 → Qdrant) 후보를 각각 시도
3. 성공 조합에 따라 모드 결정: 둘 다 성공 `HYBRID`(RRF 융합) / 한쪽만 `KEYWORD_ONLY`·`VECTOR_ONLY` / 둘 다 실패 `POSTGRES_FALLBACK`(PostgreSQL 필드 필터링)
4. 후보 ID로 PostgreSQL에서 API-ready article을 재로드해 응답 형태를 안정적으로 유지
5. 단계별 latency·후보 수·fallback 사유를 `ArticleSearchMetricsRecorder`에 기록

"API-ready"의 정의: `processingStatus = PUBLISHED`이고 `current = true`인 enrichment가 존재하는 article. N+1 방지는 `ArticleResponseGraphRepositoryImpl.fetchArticleResponseGraph()`가 담당.

### 핵심 흐름 2 — 수집 파이프라인 (`POST /api/internal/collections/runs` 또는 CLI `collection-run`)

`CollectionRunService` → 소스별 `SourceCollectionService.collect()` → fetch(XXE 차단된 XML 파싱) → `RssAtomCollector`/`ArxivCollector` parse → `CollectionPipelineService`(normalize → enrichment) → `CollectedArticlePersistenceService.publish()`(중복 검사: canonical URL → external ID → 제목+발행일 순). 실패는 `CollectionFailureClassifier`로 분류해 `collection_failure_events`에 기록하고, 기사 단위 실패가 소스 전체 실패로 번지지 않게 격리한다. 현재 enrichment는 Spring 내 `MockEnrichmentClient`가 처리한다(FastAPI enrichment 엔드포인트는 존재하나 아직 backend에서 호출하지 않음).

### 핵심 흐름 3 — projection rebuild (모두 내부 API, PostgreSQL은 불변)

- Elasticsearch: `POST /api/internal/search-projections/articles/rebuild`
- Qdrant: `POST /api/internal/search-projections/article-vectors/rebuild` (article마다 FastAPI 임베딩 호출, 모델 메타데이터 불일치 시 전체 실패)
- Neo4j: `POST /api/internal/graph-projections/articles/rebuild` (Article/Topic 노드 + HAS_TOPIC/RELATED_TO 관계 재생성)

### CLI 명령 (ApplicationRunner)

- `collection-run --sources=... --max=...`
- `search-catalog-export --output=... --limit=... --catalog-id=...`

## 3. Frontend (`frontend/src`)

- `api/`: Axios 클라이언트 + Zod 스키마. 모든 응답은 컴포넌트 도달 전에 `parse()`로 검증
- `pages/HomePage`: 목록/검색/인기(importanceScore 상위 3) 섹션
- `pages/ArticleDetailPage`: 상세 + related articles + graph context. 보조 정보(graph context, related)는 실패해도 본문을 유지하고, 응답에 articleId를 같이 보관해 stale 상태를 차단
- `components/`: NavBar, ArticleListItem (단순 표시 컴포넌트)

## 4. AI 서버 (`ai/app`)

- `routers/`: `/api/enrichment/article`, `/api/embeddings/text`, `/health`
- `services/`: `mock_enrichment_service`(결정론적 mock 요약), embedding provider 2종 — `deterministic`(SHA-256 해시 기반 8차원, 외부 의존성 없음)과 `local`(FastEmbed ONNX, 기본값) — `SIGAK_EMBEDDING_PROVIDER`로 선택
- `schemas/`: Pydantic 모델. backend DTO와 필드명이 일치(camelCase)

## 5. 이번 정리에서 적용한 리팩토링 (2026-06-10, 동작 변경 없음)

| 변경 | 내용 |
| --- | --- |
| `collection.collector` | 두 collector에 중복이던 `Element.atomLink()`를 `XmlText.kt`로 통합 |
| `common.time` | `MeasuredAttempt` + `runCatchingMeasured` 공통화 — `ArticleRetrievalCandidateService`, `ArticleVectorCandidateSearchService`의 private 중복(`SearchAttempt`) 제거 |
| `common.metrics` (신규) | `RecentObservationWindow` + `percentileOf` 공통화 — 두 메트릭 recorder의 윈도우/percentile 중복 제거 |
| `ElasticsearchArticleKeywordSearchService` | 인터페이스 default와 동일한 `searchArticleIds(query)` override 제거 |
| `ArticleNormalizer`, `CollectedArticlePersistenceService` | `PublishedAtParser`를 감싸기만 하던 한 줄 wrapper 인라인 |
| `Neo4jArticleGraphProjectionIndexer` | 수동 try/finally 세션 관리를 `driver.session().use(block)`로 단순화 |
| frontend `HomePage` | 불필요한 async wrapper 제거 (`void loadArticles()`) |
| frontend `api/articles.ts` | 목록 요청+Zod 검증을 `requestArticleList()`로 추출 |
| ai `deterministic_embedding_service` | 어디서도 사용하지 않는 module-level `embed_text()` 제거 (router는 `build_embedding_response` 사용) |

## 6. 남은 리팩토링 후보 (이번에 적용하지 않음 — 구조 변경이거나 트레이드오프 검토 필요)

- `CollectionStatus`와 `ProcessingStatus`가 동일한 enum 값을 중복 정의. 수집/공개 bounded context 분리 의도로 보이나, 의도라면 주석으로 명시할 가치가 있음
- `CollectionRunCommandRunner`와 `SearchCatalogExportCommandRunner`의 parse → run → format → exit 패턴 중복. CLI 명령이 하나 더 늘어나면 공통 runner 추출 권장
- `ArticleKeywordSearchService` 인터페이스 default의 `limit = 20` 하드코딩 — `sigak.search.hybrid.keyword-candidate-limit` 설정과 이원화되어 있음
- backend `MockEnrichmentClient`와 ai `mock_enrichment_service`가 같은 mock 로직을 양쪽에 유지(의도된 로컬 개발 편의). FastAPI enrichment를 실제 연동할 때 한쪽으로 정리 필요
- `EnrichmentResponse.modelName` 기본값 `"unknown-enrichment"`이 DTO 기본값과 `CollectedArticlePersistenceService.modelNameFor()` fallback에 중복
- ai `build_embedding_response(request, provider)`의 `provider` 파라미터가 untyped — `EmbeddingProvider` Protocol을 적용하려면 순환 import 정리가 선행돼야 함
- `ArticleDetailPage`의 세 useEffect가 같은 "stale 방지 + 보조 정보 실패 허용" 패턴을 반복 — custom hook 추출 후보
