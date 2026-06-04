# Public Graph-Aware Article Detail Design

날짜: 2026-06-03

## 목표

Neo4j article graph projection에서 조회한 relation reason을 public article detail 화면에 보여준다.

이번 작업의 목표는 full graph explorer나 GraphRAG chatbot이 아니라, 사용자가 상세 화면에서 "왜 이 기사가 관련 기사로 추천되는지"를 짧게 이해할 수 있게 만드는 것이다.

```txt
public article detail
-> related article list
-> Neo4j graph context에서 가져온 relation reason 보강
```

## 비목표

- 기존 `ArticleResponse`를 확장하지 않는다.
- `/api/articles` 검색 ranking에 graph 정보를 반영하지 않는다.
- frontend에서 internal API를 직접 호출하지 않는다.
- graph explorer UI를 만들지 않는다.
- GraphRAG chatbot을 만들지 않는다.
- Neo4j timing, relation type 같은 internal diagnostics field를 public 응답에 노출하지 않는다.
- Neo4j 장애를 public article detail 본문 장애로 전파하지 않는다.
- topic synonym, multilingual normalization, relation quality scoring을 추가하지 않는다.

## 현재 상태

이미 준비된 것:

- `POST /api/internal/graph-projections/articles/rebuild`
- `GET /api/internal/graph/articles/{id}/context`
- Neo4j projection에는 `Article`, `Topic`, `HAS_TOPIC`, `RELATED_TO`가 있다.
- `RELATED_TO`에는 `reason`이 저장된다.
- frontend article detail은 `relatedArticleIds`로 related article을 bulk 조회한다.

제약:

- `ArticleResponse`는 list, search, detail, related bulk lookup이 모두 공유한다.
- 따라서 graph-only field를 `ArticleResponse`에 추가하면 상세 화면만이 아니라 검색 카드, list validation, bulk lookup까지 응답 계약이 바뀐다.
- internal graph endpoint는 timings와 relation type을 포함하므로 public frontend가 직접 호출하면 internal/public 경계가 흐려진다.

## 접근안 비교

### A안. 별도 public graph-context API 추가

```http
GET /api/articles/{id}/graph-context
```

장점:

- 기존 `ArticleResponse` shape를 유지한다.
- public 상세 화면만 graph reason을 선택적으로 사용할 수 있다.
- Neo4j 장애나 stale projection이 있어도 article 본문은 유지된다.
- internal endpoint의 diagnostics field를 public으로 노출하지 않는다.
- 이후 graph-aware evaluation이나 frontend UI를 독립적으로 확장하기 쉽다.

단점:

- 상세 화면에서 article, related bulk, graph context를 각각 호출한다.
- frontend에서 related article과 graph reason을 article ID로 합쳐야 한다.

판단: 최종 선택이다. 기존 응답 계약을 흔들지 않으면서 실제 사용자 화면에 graph insight를 붙이는 가장 작은 변경이다.

### B안. 기존 `ArticleResponse`에 graph field 추가

장점:

- detail API 한 번으로 article과 graph 정보를 함께 받을 수 있다.
- frontend join 로직이 줄어든다.

단점:

- list/search/detail/bulk lookup 응답이 모두 바뀐다.
- graph projection 장애가 article service contract와 더 강하게 엮인다.
- 목록 카드에는 필요 없는 필드가 모든 article response에 붙는다.

판단: 현재 MVP 단계에서는 변경 범위가 과하다.

### C안. frontend가 internal graph context endpoint 직접 호출

장점:

- backend public API를 새로 만들지 않아도 된다.

단점:

- internal/public API 경계를 깬다.
- internal diagnostics field가 frontend로 흘러간다.
- 향후 internal endpoint에 인증/권한을 붙일 때 public 사용처를 다시 분리해야 한다.

판단: 제외한다.

## 최종 결정

A안을 선택한다.

추가할 public endpoint:

```http
GET /api/articles/{id}/graph-context
```

이 endpoint는 public article detail을 보강하기 위한 API다. 기존 `/api/articles/{id}`와 `/api/articles?ids=...` 응답은 바꾸지 않는다.

## Public API Contract

요청:

```http
GET /api/articles/{id}/graph-context
```

성공 응답:

```json
{
  "articleId": 4,
  "relatedArticleReasons": [
    {
      "articleId": 1,
      "reason": "Graph RAG evaluation connects to agent and retrieval evaluation.",
      "sharedTopics": []
    },
    {
      "articleId": 5,
      "reason": "Both articles affect reliability planning for technical systems.",
      "sharedTopics": []
    }
  ],
  "topics": [
    {
      "name": "graph rag",
      "displayName": "Graph RAG",
      "relatedArticleIds": []
    }
  ]
}
```

응답 원칙:

- `articleId`는 요청 article ID다.
- `relatedArticleReasons`는 related article별 표시용 reason이다.
- `sharedTopics`는 Neo4j projection에서 계산된 공통 topic display name이다.
- `topics`는 이후 related concept 표시나 graph-aware evaluation에 쓸 수 있는 public-safe topic context다.
- `timings`, `relationType`, Neo4j 내부 오류 메시지는 public 응답에 포함하지 않는다.

## Error And Fallback Policy

### PostgreSQL article이 public article이 아닌 경우

응답:

```http
404 Not Found
```

이유:

- public endpoint는 public article detail을 보강하는 API이므로, PostgreSQL source of truth 기준 API-ready article이 없으면 public resource가 없는 것이다.

### article ID가 0 이하인 경우

응답:

```http
400 Bad Request
```

### Neo4j context가 없는 경우

응답:

```json
{
  "articleId": 4,
  "relatedArticleReasons": [],
  "topics": []
}
```

이유:

- Neo4j는 projection store다. Projection이 아직 rebuild되지 않았거나 stale이어도 public article 본문은 PostgreSQL 기준으로 존재할 수 있다.
- 사용자는 상세 본문을 계속 볼 수 있어야 하므로, graph context missing은 public endpoint에서 empty context로 degrade한다.

### Neo4j 조회가 실패한 경우

응답:

```json
{
  "articleId": 4,
  "relatedArticleReasons": [],
  "topics": []
}
```

이유:

- relation reason은 보조 정보다.
- public detail 화면은 graph reason이 없어도 유지되어야 한다.
- 내부 진단은 internal graph endpoint와 backend log에서 확인한다.

## Backend Design

### DTO

새 public DTO를 만든다.

```txt
ArticlePublicGraphContextResponse
ArticlePublicRelatedArticleReasonResponse
ArticlePublicGraphTopicResponse
```

`ArticleGraphContextResponse`를 그대로 public으로 노출하지 않는다. Internal DTO에는 timings가 있고, 앞으로 diagnostics field가 더 추가될 수 있기 때문이다.

### Service

새 service를 둔다.

```txt
ArticlePublicGraphContextService
```

책임:

1. article ID가 positive인지 검증한다.
2. PostgreSQL source of truth 기준 API-ready article 존재 여부를 확인한다.
3. `ArticleGraphContextService` 또는 `ArticleGraphProjectionIndexer`를 통해 graph context를 조회한다.
4. Neo4j missing/failure는 empty public context로 degrade한다.
5. public-safe field만 DTO로 매핑한다.

PostgreSQL 확인은 `ArticleRepository.findApiReadyWithSourceById(id, ProcessingStatus.PUBLISHED)`를 재사용한다. Article detail 응답 전체를 다시 만들 필요는 없지만, public resource 존재 여부는 같은 API-ready 기준을 써야 한다.

### Controller

`ArticleController`에 endpoint를 추가한다.

```txt
GET /api/articles/{id}/graph-context
```

이유:

- public article detail 보강 API이므로 `/api/articles` public boundary 아래에 두는 것이 자연스럽다.
- internal graph controller는 diagnostics용으로 유지한다.

## Frontend Design

### API Client

`frontend/src/api/articles.ts`에 public graph context schema와 client function을 추가한다.

```txt
fetchArticleGraphContext(articleId: number): Promise<ArticleGraphContext>
```

Zod validation은 기존 article client 경계에서 수행한다.

### Detail Page

`ArticleDetailPage`는 세 가지 데이터를 다룬다.

1. article detail
2. related article bulk response
3. graph context

렌더링 원칙:

- article 본문 loading/error는 기존과 동일하다.
- related article bulk 조회 실패는 기존처럼 상세 본문을 깨지 않는다.
- graph context 조회 실패도 상세 본문을 깨지 않는다.
- related article item에 matching reason이 있으면 title 아래에 reason을 표시한다.
- reason이 없으면 기존 related article title/category만 보여준다.
- shared topic은 있으면 작은 보조 text로 표시한다.

표시 문구:

```txt
Related reason
Shared topics: Graph RAG, evaluation
```

영어 UI를 유지한다. 현재 frontend 상세 화면의 주요 label이 영어이기 때문이다.

## Testing Strategy

Backend:

- public graph context service가 API-ready article이 없으면 not found를 반환하는지 테스트한다.
- Neo4j context missing/failure가 empty public context로 degrade되는지 테스트한다.
- public response에 timings와 relationType이 없는지 controller test로 확인한다.
- 기존 `ArticleResponse` schema가 바뀌지 않았는지 article controller/OpenAPI test로 확인한다.

Frontend:

- API client가 graph context response를 Zod로 검증하는지 테스트한다.
- detail page가 related article reason을 표시하는지 테스트한다.
- graph context fetch 실패 시 article detail과 related article은 유지되는지 테스트한다.
- graph context가 비어 있으면 기존 related article UI가 그대로 동작하는지 테스트한다.

Smoke:

- Neo4j projection rebuild 후 `GET /api/articles/4/graph-context`가 related article reason을 반환하는지 확인한다.
- Neo4j가 멈췄거나 projection이 비어 있을 때 public detail 본문이 유지되는지 확인한다.

## Documentation

업데이트할 문서:

- `docs/API_SPEC.md`
- `docs/API_SPEC.ko.md`
- `docs/STATUS.md`
- `docs/STATUS.ko.md`
- `docs/ROADMAP.md`
- `docs/ROADMAP.ko.md`
- dev-log와 topic queue

## 남는 리스크

- 현재 relation reason은 seed/mock 기반이다. UI에서는 "검증된 causal explanation"이 아니라 "관련 이유"로 표현해야 한다.
- graph context는 rebuildable projection이므로 stale할 수 있다. Public endpoint는 empty degrade를 제공하지만, stale reason 자체를 감지하지는 않는다.
- article 수가 늘어나면 topic context가 길어질 수 있다. 이번 UI는 related article reason 중심으로 제한하고, topic graph UI는 별도 단계로 둔다.
