# Sigak Status

Last updated: 2026-05-08

This is a living status document. Update it whenever a roadmap phase is completed, a major risk changes, or verification results become outdated.

## 1. 요약

Sigak은 AI, 소프트웨어 개발, 컴퓨터 과학 분야의 중요한 기술 뉴스를 선별하고, 요약과 "왜 중요한가" 인사이트를 제공하는 AI 기반 뉴스 인사이트 플랫폼이다.

현재 프로젝트는 문서화, 기본 모노레포 구조, Spring Boot 백엔드 API, PostgreSQL 기반 영속성, React 프론트엔드, FastAPI mock AI 서버, selected-source 수집-영속화 파이프라인까지 MVP의 주요 골격을 갖춘 상태다. 아직 수집 실행 트리거, 실제 FastAPI HTTP 연동, Elasticsearch/Qdrant 기반 검색, 제한적 Graph RAG 인사이트는 남아 있다.

현재 단계의 핵심 평가는 다음과 같다.

| 영역 | 현재 상태 | 평가 |
| --- | --- | --- |
| 제품 방향 | MVP 범위와 비범위가 문서화됨 | 양호 |
| 백엔드 | persisted article list/detail/search API 구현 | 양호, API-ready filtering 보완 완료 |
| 프론트엔드 | 홈, 검색, 상세, 관련 기사 UI 구현 | 양호, 상세 화면 stale state 보완 완료 |
| AI 서버 | FastAPI mock enrichment endpoint 구현 | 초기 기반 완료, 입력 검증 보완 완료 |
| 데이터 | PostgreSQL schema, seed data, graph-ready metadata, 수집 article 저장 구현 | MVP 기반 완료 |
| 인프라 | PostgreSQL Docker Compose 구성 | 부분 완료, 전체 서비스 compose는 미완료 |
| 문서 | README, API spec, roadmap, ADR 정리 | 양호 |

## 2. 현재까지 진행한 일

### 2.1 프로젝트 방향 및 문서화

완료된 내용:

- `README.md`에 프로젝트 목적, 아키텍처, 로컬 실행 방법 정리
- `docs/PRODUCT.md`에 제품 정의, 대상 사용자, 핵심 가치, MVP 범위 정리
- `docs/API_SPEC.md`에 article API contract와 internal enrichment contract 정리
- `docs/ROADMAP.md`에 서비스 트랙과 연구 트랙 통합 로드맵 정리
- `docs/SOURCE_POLICY.md`에 초기 뉴스 소스 정책 정리
- `docs/decisions/`에 주요 ADR 기록

현재 문서 기준으로 Sigak의 방향은 "중요한 기술 변화, 맥락과 관계를 포함해 설명하는 서비스"로 정리되어 있다. 이 방향은 단순 뉴스 목록보다 포트폴리오에서 보여줄 수 있는 기술적 차별성이 분명하다.

### 2.2 백엔드

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

- 현재 search는 PostgreSQL persisted data 위의 in-memory filter다. MVP 초반에는 적절하지만, 실제 수집 데이터가 늘어나면 DB query 또는 Elasticsearch로 이전해야 한다.

### 2.3 프론트엔드

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

### 2.4 AI 서버

완료된 내용:

- FastAPI app 구성
- health endpoint 구현
- mock enrichment endpoint 구현
  - `POST /api/enrichment/article`
- enrichment request/response schema 구성
- pytest 기반 smoke test 작성

강점:

- paid API key 없이 로컬 개발 가능하다.
- Spring Boot와 FastAPI 책임 분리가 문서와 코드에서 일관된다.
- internal enrichment contract가 `docs/API_SPEC.md`에 정리되어 있다.

보완 필요:

- Spring Boot가 아직 FastAPI를 HTTP로 호출하지 않는다. 현재 백엔드 enrichment client는 mock implementation이다.

### 2.5 수집 및 enrichment foundation

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

### 2.6 인프라 및 로컬 개발

완료된 내용:

- root `.env.example` 작성
- PostgreSQL Docker Compose 구성
- backend/frontend/ai 각각 로컬 실행 문서 작성
- Testcontainers 기반 PostgreSQL integration test 구성

보완 필요:

- Docker Compose가 아직 PostgreSQL만 실행한다.
- backend, frontend, ai server를 한 번에 올리는 local compose 구성은 미완료다.
- Elasticsearch와 Qdrant는 아직 compose에 포함되지 않았다. MVP 안정화 전까지는 의도적으로 미루는 것이 좋다.

## 3. 코드 리뷰 findings 처리 현황

### 3.1 API-ready article filtering

위치:

- `backend/src/main/kotlin/com/sigak/article/service/ArticleService.kt`

처리 전 문제:

`getArticles()`가 모든 article을 불러오고 enrichment/topics/relations를 fetch한 뒤 response로 변환한다. 이후 query filter를 적용한다. 현재 seed data는 모든 article에 current enrichment가 있어서 괜찮지만, 수집 pipeline이 붙으면 enrichment 미완료 article도 DB에 저장될 수 있다. 이 경우 검색 결과와 무관한 article 때문에 response 변환 단계에서 500 오류가 날 수 있다.

처리 결과:

- 공개 list/search/detail API는 repository query 단계에서 `processingStatus = PUBLISHED`이고 current enrichment가 있는 article만 조회한다.
- enrichment가 없는 article이 저장되어도 공개 API response 변환 전에 제외된다.
- 관련 service regression test를 추가했다.

### 3.2 Article detail related articles stale state

위치:

- `frontend/src/pages/ArticleDetailPage.tsx`

처리 전 문제:

article이 바뀌었을 때 `relatedArticles`를 먼저 초기화하지 않는다. 관련 기사가 있는 article에서 관련 기사가 없는 article로 이동하면 이전 related article 목록이 잠시 또는 계속 남을 수 있다.

처리 결과:

- article ID 또는 article 상태가 바뀔 때 related article state를 먼저 비운다.
- 늦게 도착한 related article fetch 결과가 새 article 화면을 덮지 않도록 guard를 추가했다.
- navigation regression test를 추가했다.

### 3.3 AI enrichment input validation

위치:

- `ai/app/schemas/enrichment.py`

처리 전 문제:

`Field(min_length=1)`은 `"   "` 같은 whitespace-only string을 허용한다. mock enrichment에서는 빈 summary에 가까운 결과가 만들어질 수 있고, 이후 실제 LLM 연동에서도 품질 문제가 생길 수 있다.

처리 결과:

- required text field는 strip 후 빈 문자열을 거절한다.
- `suggestedImportanceScore`는 0-100 범위로 제한한다.
- request validation과 response schema regression test를 추가했다.

## 4. 검증 현황

최근 확인한 검증 명령:

| 영역 | 명령 | 결과 |
| --- | --- | --- |
| Backend | `./gradlew test` | 성공 |
| Frontend tests | `npm test` | 26 tests 통과 |
| Frontend build | `npm run build` | 성공 |
| Frontend lint | `npm run lint` | 성공 |
| AI tests | `.venv/bin/python -m pytest` | 3 tests 통과 |

참고:

- AI 서버 테스트는 global `python` 또는 `python3`가 아니라 `ai/.venv`의 Python으로 실행해야 한다.
- 백엔드 테스트는 Testcontainers와 PostgreSQL을 사용한다.

## 5. 앞으로 진행해야 할 일

### 5.1 단기 우선순위

1. 수집 실행 트리거 추가
   - internal/admin collection endpoint 또는 command runner
   - source별 실행 결과 반환
   - fetched/published/skipped/failed count 제공

2. collection status 관리 강화
   - discovered
   - fetched
   - extracted
   - enriched
   - published
   - failed

3. README와 API spec 업데이트
   - collection pipeline 실행 방법
   - AI server 실행 방법
   - 현재 local development flow 정리

### 5.2 MVP 안정화 단계

진행해야 할 내용:

- Spring Boot에서 FastAPI enrichment endpoint 호출
- local mock mode와 FastAPI HTTP mode 선택 가능하게 구성
- collection failure에 대한 retry 또는 최소한의 failure 기록 추가
- Docker Compose에 backend, ai server, frontend까지 포함할지 결정
- local run command 단순화

이 단계의 목표는 "수집한 article을 운영자가 의도적으로 실행하고, 결과를 확인하며, 필요할 때 FastAPI 기반 enrichment로 전환할 수 있는 상태"를 만드는 것이다.

### 5.3 Graph RAG-ready 확장

아직 남은 내용:

- explicit concept entity 또는 topic/concept normalization
- article-concept relationship 저장
- article-article relation reason을 API에 노출할지 결정
- related concepts UI 또는 small related graph 검토
- Qdrant embedding 저장 여부 결정
- graph-backed retrieval의 최소 범위 정의

MVP에서는 full graph explorer보다 article detail에서 관계 기반 설명을 강화하는 편이 낫다.

### 5.4 검색 확장

현재는 in-memory keyword search다. 다음 단계는 데이터 규모에 따라 선택한다.

권장 순서:

1. PostgreSQL query 기반 search로 먼저 이동
2. 데이터가 늘고 검색 품질 요구가 생기면 Elasticsearch 도입
3. semantic search가 필요해지면 Qdrant와 embedding pipeline 추가
4. 마지막에 hybrid search 검토

Elasticsearch와 Qdrant를 지금 바로 넣는 것은 MVP 안정화 전에는 과하다.

## 6. 권장 개발 순서

### Step 1. 코드 리뷰 findings 수정

상태: 완료

완료 내용:

- 백엔드 API는 PUBLISHED/current enrichment article만 노출한다.
- 상세 화면에서 article 전환 시 related article이 남지 않는다.
- AI endpoint는 whitespace-only input을 거절한다.
- 관련 테스트가 추가 또는 갱신된다.

### Step 2. Persisted collection pipeline 연결

상태: 완료

완료 내용:

- 현재 collector와 persistence schema를 실제 workflow로 연결했다.
- 수집된 article을 DB에 저장할 수 있다.
- raw content와 enrichment가 분리 저장된다.
- canonical URL, 원본 URL, external ID, source/title/publishedAt 기준으로 중복 저장을 막는다.
- mock enrichment 결과를 current enrichment로 저장할 수 있다.

### Step 3. Collection trigger와 실행 관측성

다음 목표:

- 선택한 source 수집을 명시적으로 실행하고 결과를 확인할 수 있게 만든다.

완료 기준:

- internal/admin endpoint 또는 command runner로 source collection을 실행할 수 있다.
- 실행 결과에 fetched/published/skipped/failed count와 실패 이유가 포함된다.
- 실패 기록 또는 최소한의 retry 기준이 문서화된다.

### Step 4. Local development polish

다음 목표:

- 프로젝트를 처음 보는 사람이 쉽게 실행할 수 있게 만든다.

완료 기준:

- README만 보고 backend/frontend/ai/postgres를 실행할 수 있다.
- `.env.example`에 필요한 값이 빠짐없이 정리되어 있다.
- Docker Compose 범위가 명확하다.

### Step 5. Limited relationship insight

다음 목표:

- Sigak의 차별점인 관계 기반 인사이트를 article detail에 작게 반영한다.

완료 기준:

- related article ID뿐 아니라 relation reason 또는 related concept를 보여준다.
- full graph UI 없이도 "이 기사가 무엇과 연결되는지"가 드러난다.

## 7. 현재 MVP 완성도 평가

현재 완성도:

- 제품 방향: 높음
- 백엔드 구조: 높음
- 프론트 기본 흐름: 중상
- AI/RAG 실사용성: 초기 단계
- collection 실행/자동화: 저장 파이프라인 완료, 실행 트리거는 초기 단계
- 로컬 배포 편의성: 중간
- 포트폴리오 문서화: 높음

종합하면, Sigak은 "기획 문서와 기본 CRUD/search 화면만 있는 프로젝트"를 넘어선 상태다. 특히 backend persistence, API spec, source policy, AI enrichment boundary까지 마련되어 있어 포트폴리오 설득력이 있다.

다만 다음 단계에서 반드시 보여줘야 할 것은 실행 가능한 collection 운영 흐름이다.

```txt
source trigger -> collect -> normalize -> enrich -> persist -> search/list/detail
```

이 흐름을 명시적으로 실행하고 결과를 관측할 수 있으면 Sigak은 단순 데모가 아니라 실제 MVP로 보이기 시작한다.

## 8. 결론

현재까지의 진행은 MVP 방향과 잘 맞는다. 특히 Spring Boot를 주 API boundary로 두고, FastAPI를 AI/RAG 전용 서비스로 분리한 선택은 프로젝트 목표에 적합하다. React frontend도 복잡한 상태 관리 없이 핵심 사용자 흐름을 구현하고 있어 MVP 우선순위와 맞다.

다음 개발의 핵심은 새로운 큰 기능을 추가하는 것이 아니라, 이미 연결된 수집-저장 흐름을 실행 가능하고 관측 가능하게 만드는 것이다. 우선 internal/admin collection trigger를 추가한 뒤, mock/http enrichment mode 전환과 실패 기록을 작게 붙이는 것이 가장 효과적인 다음 단계다.
