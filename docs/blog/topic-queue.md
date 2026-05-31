# Blog Topic Queue

이 문서는 개발 중 발견한 기술 블로그 후보를 임시로 모아 두는 큐다. 목적은 개발 흐름을 끊지 않으면서, 나중에 실제 코드와 검증 결과에 근거한 글을 쓰는 것이다.

## 운영 원칙

- 후보는 완성된 글이 아니라 "나중에 좋은 글이 될 수 있는 근거"만 적는다.
- 같은 주제가 이미 있으면 새 항목을 만들지 않고 기존 항목을 보강한다.
- 구현 전 아이디어는 `설계 메모`, 구현과 검증이 끝난 작업은 `기술 블로그` 후보로 구분한다.
- 글을 작성할 때는 사용자에게 먼저 핵심 질문을 던지고, 사용자의 답변을 바탕으로 최종 글을 만든다.

## [written] AI 에이전트용 개발 문서를 단일 출처로 통합한 이유

- 날짜: 2026-05-31
- 관련 작업: AGENTS.md/CLAUDE.md 중복 제거, 에이전트 개발 문서를 단일 출처 + 4블록 구조로 재설계
- 관련 파일:
  - `AGENTS.md`
  - `CLAUDE.md`
  - `docs/blog/WRITING_GUIDE.ko.md`
- 감지 이유:
  - 같은 규칙이 AGENTS.md와 CLAUDE.md 두 곳에 있어 이미 드리프트가 발생했다.
  - 안정 규칙과 변동 상태(MVP Scope)가 섞여 상태 변경 시 여러 파일을 고쳐야 했다.
  - 에이전트에게 "어디서부터 읽어라"라는 읽기 순서가 없었다.
  - "완료"의 정의(검증 명령)가 세 파일에 분산돼 검증 없이 끝내기 쉬웠다.
- 글의 핵심 질문:
  - AI 에이전트가 주 개발자인 프로젝트에서 문서는 왜 "실행 시스템"으로 설계해야 하는가?
  - 규칙 중복을 "동기화 규칙"이 아니라 단일 출처로 막아야 하는 이유는?
  - "완료"를 명령어로 강제하면 무엇이 달라지는가?
- 검증 근거:
  - AGENTS.md 7블록 재작성, 변동 정보 제거 후 STATUS/ROADMAP 포인터로 대체.
  - CLAUDE.md 헤더에 정본 위임 명시, 중복 블록을 포인터로 축소.
  - DoD 명령어는 `frontend/package.json`, `ai/.venv` 등 실제 설정에서 확인.
  - 효과(에이전트 개발 품질 향상)는 다음 세션에서 관찰 예정 — 현재 미검증.
- 추천 글 유형: 회사 기술 블로그 / 개발 방법론 회고
- 상태: written
- 작성된 글: `2026-05-31-agent-docs-consolidation.md`

## [written] Flyway로 schema migration을 관리한 이유

- 날짜: 2026-05-28
- 관련 작업: PostgreSQL persistence MVP의 schema history와 검증 전략 정리
- 관련 파일:
  - `backend/src/main/resources/db/migration/V1__create_persistence_schema.sql`
  - `backend/src/main/resources/db/migration/V2__seed_article_data.sql`
  - `backend/src/main/resources/application.yml`
  - `backend/src/test/kotlin/com/sigak/persistence/PersistenceSmokeTest.kt`
- 감지 이유:
  - Hibernate automatic DDL 대신 Flyway migration을 schema 기준으로 선택했다.
  - JPA `ddl-auto=validate`와 Testcontainers PostgreSQL로 schema drift를 감지한다.
  - 검색/RAG projection의 source of truth가 되는 DB schema를 리뷰 가능한 형태로 남긴다.
- 글의 핵심 질문:
  - Flyway가 없었다면 환경별 DB 상태가 어떻게 달라질 수 있는가?
  - schema 제약을 SQL migration에 직접 남기는 이유는 무엇인가?
  - JPA는 왜 schema 생성자가 아니라 검증자로 두었는가?
- 검증 근거:
  - `PersistenceSmokeTest`가 Flyway migration 후 seed article/source/current enrichment 수를 검증한다.
- 추천 글 유형: 회사 기술 블로그
- 상태: written
- 작성된 글: `2026-05-28-flyway-adoption.md`

## [written] SchemaSpy로 DB 구조를 자동 문서화한 이유

- 날짜: 2026-05-28
- 관련 작업: Docker Compose `tools` profile에 SchemaSpy 일회성 ERD 생성 workflow 추가
- 관련 파일:
  - `infra/docker-compose.yml`
  - `infra/README.ko.md`
  - `infra/README.md`
  - `.gitignore`
- 감지 이유:
  - DB 구조 시각화 도구를 상시 서비스가 아니라 필요할 때만 실행하는 도구로 분리했다.
  - 생성물은 `outputs/`에 두고 Git에는 생성 방법만 남겼다.
  - Docker 초심자를 위해 명령어 치트시트와 주의사항을 함께 문서화했다.
- 글의 핵심 질문:
  - 개인 GUI 도구만 쓰면 어떤 문서화 문제가 생기는가?
  - Compose profile과 `run --rm`은 로컬 개발 경험을 어떻게 개선하는가?
  - 생성물을 커밋하지 않고 재생성 방법을 남기는 이유는 무엇인가?
- 검증 근거:
  - `docker compose -f infra/docker-compose.yml --profile tools config`
  - `docker compose -f infra/docker-compose.yml --profile tools run --rm db-schema`
  - `test -f outputs/db-schema/index.html`
- 추천 글 유형: 회사 기술 블로그
- 상태: written
- 작성된 글: `2026-05-28-schemaspy-adoption.md`

## [ready-to-write] PostgreSQL Source of Truth와 Elasticsearch Projection Store를 분리한 이유

- 날짜: 2026-05-28
- 관련 작업: Article search projection rebuild와 검색 인프라 readiness 연결
- 관련 파일:
  - `backend/src/main/kotlin/com/sigak/search/projection/ArticleSearchProjectionRebuildService.kt`
  - `backend/src/main/kotlin/com/sigak/search/projection/ElasticsearchArticleSearchProjectionIndexer.kt`
  - `backend/src/main/kotlin/com/sigak/search/config/SearchInfrastructureProperties.kt`
  - `infra/docker-compose.yml`
- 감지 이유:
  - PostgreSQL을 원본 데이터 저장소로 두고 Elasticsearch를 재생성 가능한 projection store로 분리했다.
  - 검색 인덱스가 깨지거나 mapping이 바뀌어도 rebuild할 수 있는 구조를 만들었다.
  - Qdrant와 Neo4j도 같은 projection store 원칙으로 확장할 수 있다.
- 글의 핵심 질문:
  - 왜 Elasticsearch에 원본 article을 저장하지 않았는가?
  - projection store가 깨졌을 때 rebuild 가능성은 운영적으로 어떤 의미가 있는가?
  - source of truth와 projection store를 나누면 구현 복잡도는 어떻게 달라지는가?
- 검증 근거:
  - `POST /api/internal/search-projections/articles/rebuild` smoke check에서 `indexedCount=5` 확인
  - Elasticsearch `_count`에서 `count=5` 확인
- 추천 글 유형: 회사 기술 블로그
- 상태: ready-to-write

## [ready-to-write] Elasticsearch 검색 fallback과 metric 설계

- 날짜: 2026-05-28
- 관련 작업: `/api/articles?query=...` Elasticsearch 우선 검색과 PostgreSQL fallback 연결
- 관련 파일:
  - `backend/src/main/kotlin/com/sigak/article/service/ArticleService.kt`
  - `backend/src/main/kotlin/com/sigak/search/service/ArticleKeywordSearchService.kt`
  - `backend/src/main/kotlin/com/sigak/search/service/ElasticsearchArticleKeywordSearchService.kt`
  - `backend/src/main/kotlin/com/sigak/search/metrics/ArticleSearchMetricsRecorder.kt`
  - `backend/src/main/kotlin/com/sigak/search/metrics/ArticleSearchMetricsController.kt`
  - `backend/src/test/kotlin/com/sigak/article/service/ArticleServiceTest.kt`
  - `backend/src/test/kotlin/com/sigak/search/service/ElasticsearchArticleKeywordSearchServiceTest.kt`
  - `backend/src/test/kotlin/com/sigak/search/metrics/ArticleSearchMetricsRecorderTest.kt`
  - `backend/src/test/kotlin/com/sigak/search/metrics/ArticleSearchMetricsControllerTest.kt`
  - `docs/API_SPEC.md`
- 감지 이유:
  - 사용자 검색 API를 Elasticsearch에 연결하면서 장애 fallback 전략을 실제 코드로 만들었다.
  - Elasticsearch는 article ID 후보만 반환하고, 최종 응답은 PostgreSQL에서 다시 조립하도록 source of truth 경계를 유지했다.
  - 검색 시간, 결과 수, fallback 여부를 internal endpoint에 남겨 public API 응답을 흔들지 않고 metric 설계의 출발점을 만들었다.
- 글의 핵심 질문:
  - Elasticsearch가 내려갔을 때 PostgreSQL fallback은 언제 사용해야 하는가?
  - fallback은 사용자 경험을 개선하지만 장애를 숨길 위험은 없는가?
  - 검색 metric은 어떤 단위로 남기는 것이 MVP에 적절한가?
- 검증 근거:
  - `./gradlew test --tests com.sigak.search.service.ElasticsearchArticleKeywordSearchServiceTest --tests com.sigak.article.service.ArticleServiceTest`
  - `./gradlew test --tests com.sigak.search.metrics.ArticleSearchMetricsRecorderTest --tests com.sigak.search.metrics.ArticleSearchMetricsControllerTest --tests com.sigak.article.service.ArticleServiceTest`
  - `./gradlew test --tests com.sigak.article.controller.ArticleControllerTest`
  - local smoke test에서 `POST /api/internal/search-projections/articles/rebuild`가 `indexedCount=5` 반환
  - Elasticsearch `_count`가 `count=5` 반환
  - `/api/articles?query=graph`가 article `4` 반환, backend log에서 `fallback=false` 확인
  - Elasticsearch 중단 후 같은 query가 article `4`를 반환, backend log에서 `fallback=true` 확인
  - `/api/internal/search-metrics/articles` smoke check에서 mode-aware metric(`hybridSearchCount`, `postgresFallbackSearchCount`, `lastSearch.mode`) 확인
- 추천 글 유형: 회사 기술 블로그
- 상태: ready-to-write

## [candidate] Public Hybrid Search에서 RRF와 fallback metric을 분리한 이유

- 날짜: 2026-05-30
- 관련 작업: `/api/articles?query=...` public search를 Elasticsearch keyword 후보 + Qdrant vector 후보 + RRF로 전환
- 관련 파일:
  - `backend/src/main/kotlin/com/sigak/search/hybrid/ArticlePublicSearchService.kt`
  - `backend/src/main/kotlin/com/sigak/search/hybrid/ReciprocalRankFusion.kt`
  - `backend/src/main/kotlin/com/sigak/article/service/ArticleService.kt`
  - `backend/src/main/kotlin/com/sigak/search/metrics/ArticleSearchMetricsRecorder.kt`
- 핵심 메시지:
  - PostgreSQL을 source of truth로 유지하면서 projection store는 candidate generator로만 사용했다.
  - 한쪽 projection 장애를 전체 실패로 보지 않고 `KEYWORD_ONLY`/`VECTOR_ONLY` degrade mode로 관측했다.
  - stale candidate를 PostgreSQL reload 단계에서 제거하고 metrics로 남겨 projection freshness를 확인할 수 있게 했다.
- 설계 질문:
  - Hybrid search의 public API에 score를 노출하지 않은 이유는 무엇인가?
  - RRF는 왜 첫 hybrid baseline으로 적합한가?
  - fallback metric은 장애를 숨기지 않으면서 사용자 경험을 어떻게 지키는가?
- 검증 근거:
  - `./gradlew test --tests com.sigak.search.hybrid.ArticlePublicSearchServiceTest`
  - `./gradlew test --tests com.sigak.search.metrics.ArticleSearchMetricsRecorderTest --tests com.sigak.article.service.ArticleServiceTest`
  - local smoke test에서 `HYBRID`, `KEYWORD_ONLY`, `VECTOR_ONLY`, `POSTGRES_FALLBACK` mode 확인
  - `docs/search-evaluation/queries.md`에 초기 query set과 현재 결과 기록
- 추천 글 유형: 회사 기술 블로그
- 상태: written
- 작성 글: `docs/blog/2026-05-30-dev-log.md`

## [candidate] Qdrant 도입 전 keyword search baseline과 embedding mode를 분리한 이유

- 날짜: 2026-05-28
- 관련 작업: Qdrant vector search와 hybrid search 구현 전 deterministic embedding boundary 추가, 실제 embedding model mode 방향 정리
- 관련 파일:
  - `ai/app/config.py`
  - `ai/app/routers/embedding.py`
  - `ai/app/services/embedding_provider.py`
  - `ai/app/services/local_sentence_transformer_embedding_service.py`
  - `ai/app/services/deterministic_embedding_service.py`
  - `ai/app/schemas/embedding.py`
  - `ai/tests/test_embedding_providers.py`
  - `ai/tests/test_embedding_router.py`
  - `backend/src/main/kotlin/com/sigak/ai/embedding/FastApiEmbeddingClient.kt`
  - `backend/src/test/kotlin/com/sigak/ai/embedding/FastApiEmbeddingClientTest.kt`
  - `infra/docker-compose.yml`
  - `backend/src/main/kotlin/com/sigak/search/config/SearchInfrastructureProperties.kt`
  - `docs/ROADMAP.md`
- 감지 이유:
  - vector search를 바로 붙이기 전에 keyword search baseline이 있어야 검색 품질을 비교할 수 있다.
  - embedding 생성 방식, collection schema, score normalization을 한 번에 결정하면 실패 원인 분리가 어렵다.
  - hybrid search는 keyword/vector 결과가 모두 있어야 의미가 있다.
  - FastAPI deterministic embedding endpoint를 먼저 추가해 유료 API 없이 Qdrant projection wiring을 검증할 수 있게 했다.
  - Spring Boot embedding client를 먼저 추가해 Qdrant upsert 로직이 FastAPI HTTP 계약에 직접 묶이지 않도록 했다.
  - 포트폴리오에서 semantic search 품질을 주장하려면 deterministic embedding이 아니라 실제 embedding model mode가 필요하다.
  - 따라서 deterministic은 fallback/test mode로 두고, main vector retrieval path는 한글/영어 기사를 함께 처리할 수 있는 FastEmbed 기반 multilingual embedding model로 전환하는 방향을 문서화했다.
  - FastAPI embedding provider 구조를 추가해 local model mode와 deterministic mode를 설정으로 전환할 수 있게 했다.
- 글의 핵심 질문:
  - keyword search baseline 없이 vector search를 붙이면 어떤 문제가 생기는가?
  - deterministic embedding은 어디까지 허용 가능한가?
  - 실제 embedding model을 쓰면 local reproducibility, 성능, 배포 비용의 trade-off가 어떻게 바뀌는가?
  - 검색 품질 비교를 위한 query set은 어떻게 만들 것인가?
- 검증 근거:
  - `.venv/bin/python -m pytest tests/test_embedding_router.py`
  - `.venv/bin/python -m pytest tests/test_embedding_router.py tests/test_embedding_providers.py`
  - `./gradlew test --tests com.sigak.ai.embedding.FastApiEmbeddingClientTest`
  - 아직 Qdrant projection/rebuild는 구현 전이다. 구현 후 smoke check와 query 결과를 추가한다.
- 추천 글 유형: 설계 메모
- 상태: candidate

## [candidate] Internal API와 Public API를 분리한 이유

- 날짜: 2026-05-28
- 관련 작업: 검색 인프라 readiness와 search projection rebuild endpoint 추가
- 관련 파일:
  - `backend/src/main/kotlin/com/sigak/search/controller/SearchInfrastructureHealthController.kt`
  - `backend/src/main/kotlin/com/sigak/search/projection/ArticleSearchProjectionController.kt`
  - `docs/API_SPEC.md`
- 감지 이유:
  - 사용자 기능 API와 운영/개발용 API를 `/api/internal/...` 경로로 분리했다.
  - rebuild, readiness 같은 작업은 public API와 다른 보안/운영 정책이 필요하다.
  - 추후 admin auth나 배포 시 network boundary로 보호할 수 있는 경계를 미리 만들었다.
- 글의 핵심 질문:
  - internal endpoint를 public API와 섞으면 어떤 문제가 생기는가?
  - MVP에서 internal API 보안은 어디까지 다뤄야 하는가?
  - API 문서에는 internal endpoint를 어떻게 표현해야 하는가?
- 검증 근거:
  - readiness endpoint smoke check에서 Elasticsearch/Qdrant/Neo4j ready 확인
  - projection rebuild endpoint smoke check에서 `indexedCount=5` 확인
- 추천 글 유형: 회사 기술 블로그
- 상태: candidate

## [ready-to-write] Qdrant Vector Search Projection을 internal API로 먼저 만든 이유

- 날짜: 2026-05-29
- 관련 작업: Qdrant article vector projection rebuild, internal vector search, vector search metrics 구현
- 관련 파일:
  - `backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorTextBuilder.kt`
  - `backend/src/main/kotlin/com/sigak/search/vector/QdrantArticleVectorProjectionIndexer.kt`
  - `backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorProjectionRebuildService.kt`
  - `backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorSearchService.kt`
  - `backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorSearchMetricsRecorder.kt`
  - `backend/src/test/kotlin/com/sigak/search/vector/`
  - `docs/superpowers/specs/2026-05-29-qdrant-vector-search-design.md`
  - `docs/superpowers/plans/2026-05-29-qdrant-vector-search.md`
- 감지 이유:
  - PostgreSQL을 source of truth로 유지하고 Qdrant는 재생성 가능한 projection store로 분리했다.
  - FastAPI embedding provider metadata(provider/model/dimension)를 projection payload와 rebuild 응답에 남겨 모델 변경과 재색인 근거를 설명할 수 있게 했다.
  - Public API에 바로 연결하지 않고 internal API로 먼저 만들어 vector 품질, stale hit 처리, latency breakdown을 검증할 수 있게 했다.
  - Qdrant 결과는 article ID와 score로만 사용하고, 최종 article response는 PostgreSQL에서 다시 읽도록 설계했다.
  - embedding, Qdrant search, article reload, total elapsed time을 분리해 병목을 설명할 수 있게 했다.
- 글의 핵심 질문:
  - Qdrant를 source of truth가 아니라 projection store로 둔 이유는 무엇인가?
  - vector search를 public API에 바로 붙이지 않은 이유는 무엇인가?
  - embedding model/provider/dimension metadata를 왜 projection에 남겨야 하는가?
  - stale Qdrant hit과 duplicate hit은 어떻게 다뤄야 하는가?
  - keyword, vector, hybrid search를 비교하려면 어떤 metric과 query set이 필요한가?
- 검증 근거:
  - `./gradlew test --tests 'com.sigak.search.vector.*'`
  - `POST /api/internal/search-projections/article-vectors/rebuild`
  - `POST /api/internal/vector-search/articles`
  - `GET /api/internal/search-metrics/article-vectors`
- 추천 글 유형: 회사 기술 블로그
- 상태: ready-to-write

## [candidate] Spring RestClient와 FastAPI 사이의 h2c 업그레이드 문제를 디버깅한 기록

- 날짜: 2026-05-29
- 관련 작업: Spring Boot `RestClient`가 FastAPI embedding endpoint 호출 시 body가 비어 보이던 422 오류 수정
- 관련 파일:
  - `backend/src/main/kotlin/com/sigak/ai/config/AiServerConfig.kt`
  - `backend/src/test/kotlin/com/sigak/ai/embedding/FastApiEmbeddingClientTest.kt`
- 감지 이유:
  - FastAPI를 직접 호출하면 정상인데 Spring Boot 경유 호출만 `body missing` 422가 발생했다.
  - raw HTTP 요청을 캡처해 Java HTTP client가 `Upgrade: h2c`와 `Transfer-Encoding: chunked`를 보내는 것을 확인했다.
  - Uvicorn은 h2c 업그레이드를 지원하지 않아 `RestClient`를 HTTP/1.1로 고정했다.
  - Mock HTTP 테스트만으로는 잡기 어려운 runtime protocol mismatch를 회귀 테스트로 남겼다.
- 글의 핵심 질문:
  - 단위 테스트는 통과하는데 로컬 통합 스모크에서만 실패한 이유는 무엇인가?
  - HTTP client의 기본 프로토콜 선택이 서버 런타임과 맞지 않으면 어떤 증상이 나타나는가?
  - raw request capture는 멀티 서비스 디버깅에서 어떤 근거를 제공하는가?
- 검증 근거:
  - `./gradlew test --tests com.sigak.ai.embedding.FastApiEmbeddingClientTest.configuredAiServerClientDoesNotRequestHttp2Upgrade`
  - `./gradlew test`
  - `POST /api/internal/search-projections/article-vectors/rebuild`가 실제 FastAPI embedding model로 `indexedCount=5` 반환
- 추천 글 유형: 디버깅 회고 / 회사 기술 블로그
- 상태: candidate
