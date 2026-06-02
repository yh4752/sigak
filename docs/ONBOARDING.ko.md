# Sigak 신규 개발자 온보딩

이 문서는 Sigak을 처음 인수인계받는 개발자가 첫날 실행 흐름과 핵심 경계를 빠르게
잡기 위한 안내서다. 제품 방향과 전체 현황은 `docs/PRODUCT.md`, `docs/STATUS.md`,
`docs/ROADMAP.md`가 정본이고, 이 문서는 "어디서부터 읽고 무엇을 조심해야 하는가"에
집중한다.

## 1. 첫날 읽기 순서

1. `README.md` - 프로젝트 목적, 전체 구조, 로컬 실행 요약
2. `docs/STATUS.md` - 현재 구현 상태와 남은 리스크
3. `docs/ROADMAP.md` - 다음 개발 순서와 v0.1 범위
4. `docs/API_SPEC.md` - public/internal API 계약
5. `docs/DEMO_FLOW.md` - collection부터 projection rebuild와 public search까지 재현하는 로컬 데모 흐름
6. `docs/CODING_CONVENTIONS.md` - 네이밍, 한글 주석, 테스트 규칙
7. 작업 중인 `docs/superpowers/specs/*`와 `docs/superpowers/plans/*`

에이전트가 개발을 수행하는 세션에서는 루트 `AGENTS.md`를 먼저 읽는다.

## 2. 로컬 실행

검색 흐름까지 확인하려면 PostgreSQL, Elasticsearch, Qdrant, AI 서버가 필요하다.

```bash
docker compose -f infra/docker-compose.yml up -d postgres elasticsearch qdrant ai
```

백엔드는 별도 터미널에서 실행한다.

```bash
cd backend
./gradlew bootRun
```

프론트엔드는 별도 터미널에서 실행한다.

```bash
cd frontend
npm install
npm run dev
```

AI 서버만 테스트할 때는 `ai/.venv`의 Python을 사용한다.

```bash
cd ai
.venv/bin/python -m pytest
```

## 3. 주요 internal operation

백엔드가 실행 중일 때 선택 source collection을 수동으로 실행할 수 있다.

PostgreSQL만 띄운 최소 smoke에서는 검색 projection을 호출하지 않도록 `SIGAK_SEARCH_MODE=KEYWORD`로 백엔드를 실행한다.

```bash
docker compose -f infra/docker-compose.yml up -d postgres
cd backend
SIGAK_SEARCH_MODE=KEYWORD ./gradlew bootRun
```

다른 터미널에서 collection run을 호출한다.

```bash
curl -X POST http://localhost:8080/api/internal/collections/runs \
  -H 'Content-Type: application/json' \
  -d '{"sourceIds":["openai-blog"],"maxArticlesPerSource":3}'
```

터미널에서 한 번 실행하고 종료되는 command runner도 사용할 수 있다.

```bash
cd backend
SIGAK_SEARCH_MODE=KEYWORD ./gradlew bootRun --args='collection-run --sources=github-blog --max=1'
```

Collection run은 PostgreSQL에 article을 저장하지만 Elasticsearch, Qdrant, Neo4j projection을 자동으로 rebuild하지 않는다.
검색 projection은 별도 internal rebuild endpoint로 명시적으로 실행한다.

Collection부터 failure diagnostics, Elasticsearch/Qdrant projection rebuild, public hybrid search까지 한 번에 확인하려면 `docs/DEMO_FLOW.ko.md`를 따른다.

## 4. 검증 명령

| 영역 | 명령 |
| --- | --- |
| Backend full test | `cd backend && ./gradlew test --rerun-tasks` |
| Backend check | `cd backend && ./gradlew check` |
| Frontend test/lint/build | `cd frontend && npm test && npm run lint && npm run build` |
| AI test | `cd ai && .venv/bin/python -m pytest` |
| Markdown/whitespace | `git diff --check` |

돌리지 않은 명령은 통과했다고 쓰지 않는다. 검증하지 않은 것은 `미검증`으로 남긴다.

## 5. 핵심 모듈 책임

| 영역 | 책임 |
| --- | --- |
| `backend/article` | 공개 article list/detail/search 응답 조립 |
| `backend/search/projection` | Elasticsearch keyword projection rebuild |
| `backend/search/vector` | Qdrant vector projection, internal vector search |
| `backend/search/hybrid` | public keyword/vector candidate fusion과 fallback mode 결정 |
| `backend/search/metrics` | public search metric snapshot |
| `backend/collection` | source fetch, normalize, mock enrich, persist |
| `ai` | mock enrichment와 embedding provider |
| `frontend` | article search/detail UI와 API boundary validation |

컨트롤러는 얇게 두고, 비즈니스 판단은 서비스에 둔다. API 응답은 DTO로 노출하고 JPA
entity를 직접 public API로 내보내지 않는다.

## 6. 검색 흐름

`GET /api/articles?query=...`의 현재 흐름은 다음과 같다.

```txt
query
-> Elasticsearch keyword candidates
-> Qdrant vector candidates
-> reciprocal rank fusion
-> PostgreSQL API-ready article reload
-> ArticleResponse list
-> mode-aware search metrics
```

Elasticsearch와 Qdrant는 빠른 후보 생성을 위한 projection store다. 최종 응답은 항상
PostgreSQL에서 다시 읽는다.

## 7. 깨면 안 되는 계약

- Public article API response shape를 바꾸지 않는다.
- PostgreSQL은 source of truth다.
- Elasticsearch, Qdrant, Neo4j는 rebuildable projection store다.
- Projection 실패는 metric에 남기되 public API를 불필요하게 깨지 않는다.
- 한쪽 검색 projection이 실패하면 `KEYWORD_ONLY` 또는 `VECTOR_ONLY`로 degrade한다.
- 두 projection이 모두 실패하면 `POSTGRES_FALLBACK`으로 동작한다.
- Public API에 검색 score나 internal diagnostics를 바로 노출하지 않는다.

## 8. 변경 영향 체크리스트

- API response가 바뀌면 `docs/API_SPEC.md`, frontend Zod schema, controller tests를 함께 본다.
- 검색 흐름이 바뀌면 hybrid search tests, article service tests, metrics tests를 함께 본다.
- 인프라 명령이 바뀌면 `README.md`, `infra/README.md`, 이 문서를 함께 본다.
- AI embedding 계약이 바뀌면 FastAPI schema, Spring Boot embedding client, Qdrant rebuild tests를 함께 본다.
- 설계 고민, 오류, trade-off가 생기면 `docs/blog/topic-queue.md`에 후보를 추가할지 판단한다.

## 9. 리팩토링 원칙

- 다음 기능을 붙일 때 실제로 막히는 경계부터 정리한다.
- 새 추상화는 중복 제거보다 책임 경계를 분명히 할 때만 추가한다.
- RRF ranking, fallback policy, public response shape는 리팩토링 중 바꾸지 않는다.
- 큰 구조 변경보다 작은 커밋과 빠른 테스트를 우선한다.
- 오버엔지니어링을 피하기 위해 의도적으로 미룬 선택은 dev-log나 topic queue에 남긴다.
