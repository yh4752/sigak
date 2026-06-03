# Neo4j Graph Projection Design

날짜: 2026-06-03

## 목표

Sigak의 다음 목표는 PostgreSQL에 저장된 article, topic, article relation metadata를 Neo4j에 재생성 가능한 graph projection으로 옮기고, article detail에서 관계 이유나 관련 concept를 설명할 수 있는 기반을 만드는 것이다.

이번 설계는 A안을 선택한다.

```txt
PostgreSQL source of truth
-> Neo4j article/topic/relation projection
-> internal graph context 조회
-> 이후 public article detail 확장
```

오늘 설계의 핵심은 Neo4j를 새로운 원본 저장소로 만들지 않고, Elasticsearch와 Qdrant처럼 rebuildable projection store로 유지하는 것이다.

## 비목표

- full graph explorer UI를 만들지 않는다.
- GraphRAG chatbot을 만들지 않는다.
- Neo4j 결과로 public search ranking을 바로 바꾸지 않는다.
- PostgreSQL schema를 새로 추가하지 않는다.
- LLM 기반 relation extraction을 이번 범위에 넣지 않는다.
- 수집 pipeline이 collection 직후 Neo4j rebuild를 자동 실행하게 하지 않는다.
- Neo4j에 raw content, embedding vector, API key, prompt 전문 같은 민감하거나 큰 데이터를 저장하지 않는다.
- topic synonym, multilingual normalization, relation quality scoring은 이번 범위에 넣지 않는다.

## 현재 상태

이미 준비된 것:

- `infra/docker-compose.yml`에 Neo4j service가 있다.
- `SearchInfrastructureProperties.Neo4j`와 `neo4jDriver()` bean이 있다.
- PostgreSQL에는 graph-ready metadata가 있다.
  - `articles`
  - `article_topics`
  - `article_relations`
- `ArticleEntity`는 `topics`와 `outgoingRelations`를 가진다.
- `ArticleRelationEntity`는 `relationType`과 `reason`을 가진다.
- public article response는 `relatedArticleIds`만 제공한다.

아직 없는 것:

- PostgreSQL article graph를 Neo4j에 rebuild하는 application-level projection service
- Neo4j graph context 조회 service
- graph projection rebuild API
- article detail에 relation reason 또는 shared topic을 노출하는 API/UI

## 접근안 비교

### A안. Projection-only부터 만들고 internal graph context로 검증

흐름:

```txt
Neo4j projection rebuild
-> internal graph context 조회
-> public article detail 확장
```

장점:

- PostgreSQL source of truth 원칙을 유지한다.
- Neo4j 장애가 public article API에 바로 영향을 주지 않는다.
- graph schema와 Cypher 결과를 internal API로 먼저 검증할 수 있다.
- full graph explorer 없이도 portfolio에 필요한 graph-aware evidence를 만들 수 있다.

단점:

- 첫 단계에서는 화면 변화가 없다.
- projection rebuild와 context 조회 API가 별도로 필요하다.

판단: 이번 작업의 최종 선택이다.

### B안. Projection과 public article detail 확장을 한 번에 구현

장점:

- 사용자 화면에서 graph-aware detail을 빠르게 볼 수 있다.

단점:

- Neo4j projection, Cypher, public DTO, frontend 변경이 한 번에 섞인다.
- graph 장애/fallback 정책을 검증하기 전에 public API가 복잡해진다.

판단: 디버깅 범위가 커져서 보류한다.

### C안. Graph-aware benchmark부터 설계

장점:

- 연구 track과 바로 연결된다.

단점:

- Neo4j projection이 없어서 benchmark 입력과 실패 원인 분리가 어렵다.
- 현재 article catalog가 작아 graph 품질 판단 근거도 약하다.

판단: projection과 graph context가 먼저 필요하다.

## 최종 결정

A안을 선택한다.

첫 구현 단위는 다음 두 가지다.

1. `POST /api/internal/graph-projections/articles/rebuild`
2. `GET /api/internal/graph/articles/{id}/context`

public article detail 확장은 다음 구현 단위로 미룬다. 먼저 internal graph context에서 relation reason, shared topic, count, latency를 확인한 뒤 public API와 frontend에 붙인다.

## Graph Schema

### Nodes

#### `Article`

식별자:

- `articleId`: PostgreSQL article id

Properties:

- `articleId`
- `title`
- `source`
- `url`
- `publishedAt`
- `eventType`
- `primaryCategory`
- `importanceScore`

저장하지 않는 것:

- `summary`
- `whyItMatters`
- raw content
- embedding vector

이유:

- Neo4j는 관계 탐색 projection이고, 최종 사용자 응답은 PostgreSQL에서 다시 읽는다.
- 긴 본문성 필드는 graph 탐색에는 필요하지 않고 projection 크기만 키운다.

#### `Topic`

식별자:

- `name`: 정규화된 topic 이름

Properties:

- `name`
- `displayName`

정규화 규칙:

- `displayName`: PostgreSQL에 저장된 topic의 앞뒤 공백을 제거한 표시용 이름이다. 대소문자와 단어 표기는 원문을 유지한다.
- `name`: trim 후 lowercase

MVP에서는 `"Graph RAG"`와 `"graph rag"`를 같은 topic으로 본다. 더 정교한 synonym normalization은 데이터가 커진 뒤 다룬다.

주의:

- `"Graph RAG"`와 `"knowledge graph"`처럼 의미가 가까운 topic을 같은 node로 합치지는 않는다.
- 한국어/영어 topic을 cross-lingual synonym으로 묶는 것도 이번 범위가 아니다.
- article catalog가 20개 이상으로 늘고 topic 중복/파편화가 실제로 관찰되면 topic normalizer를 별도 설계한다.

### Relationships

#### `(:Article)-[:HAS_TOPIC]->(:Topic)`

Properties:

- `position`

의미:

- article이 어떤 topic을 설명하는지 나타낸다.
- `position`은 기존 article topic 순서를 유지한다.

#### `(:Article)-[:RELATED_TO]->(:Article)`

Properties:

- `relationType`
- `reason`

의미:

- PostgreSQL `article_relations`의 source/target/reason을 Neo4j에 투영한다.
- 방향은 PostgreSQL relation 방향을 그대로 따른다.

MVP에서는 `RelationType.RELATED`만 존재한다. 이후 relation type이 늘어나도 relationship type을 동적으로 만들지 않고, `RELATED_TO` relationship의 `relationType` property로 보관한다.

품질 해석:

- `reason`은 현재 seed/mock enrichment 기반 설명이므로 "검증된 사실"이 아니라 "저장된 relation reason"으로만 해석한다.
- public UI에 붙일 때도 단정형 문구보다 "관련 이유" 수준으로 표현한다.
- 실제 수집 데이터나 LLM relation extraction을 도입하면 accepted/rejected/ambiguous relation 예시를 따로 검토해야 한다.

## Constraint And Rebuild Policy

rebuild 시작 시 constraint를 보장한다.

```cypher
CREATE CONSTRAINT sigak_article_article_id IF NOT EXISTS
FOR (article:Article)
REQUIRE article.articleId IS UNIQUE

CREATE CONSTRAINT sigak_topic_name IF NOT EXISTS
FOR (topic:Topic)
REQUIRE topic.name IS UNIQUE
```

rebuild 정책:

1. PostgreSQL에서 API-ready article을 읽는다.
2. Neo4j의 기존 `Article`, `Topic` projection node를 삭제한다.
3. article node를 생성한다.
4. topic node와 `HAS_TOPIC` edge를 생성한다.
5. relation target도 API-ready article에 포함되는 경우에만 `RELATED_TO` edge를 생성한다.
6. rebuild response에 `rebuiltAt`을 포함해 수동 rebuild 시점을 남긴다.

삭제 Cypher:

```cypher
MATCH (node)
WHERE node:Article OR node:Topic
DETACH DELETE node
```

주의:

- 현재 Neo4j는 Sigak local projection 전용이므로 `Article`, `Topic` label 삭제를 허용한다.
- 미래에 같은 Neo4j database에 다른 graph가 들어오면 `SigakProjection` 같은 namespace label을 추가해야 한다.

자동 rebuild 정책:

- MVP에서는 collection 직후 Neo4j rebuild를 자동 실행하지 않는다.
- demo script와 dev-log에는 collection 이후 Elasticsearch, Qdrant, Neo4j rebuild를 수동으로 실행하는 순서를 명시한다.
- article 수집 주기가 잦아지거나 stale graph context가 반복해서 관찰되면 scheduled rebuild 또는 collection 후 선택적 projection rebuild를 별도 단계로 설계한다.
- 자동화를 추가하더라도 PostgreSQL write transaction과 Neo4j rebuild를 하나의 transaction처럼 묶지 않는다. Neo4j는 계속 재생성 가능한 projection으로 둔다.

## Data Read Boundary

Projection rebuild는 `ArticleService.getArticles()`를 그대로 쓰지 않는다.

이유:

- `ArticleResponse`에는 relation reason이 없다.
- graph projection에는 outgoing relation의 `relationType`, `reason`, target article id가 필요하다.
- projection 전용 reader가 entity graph를 읽어 projection document로 변환하는 편이 명확하다.

추가할 reader:

```txt
ArticleGraphProjectionReader
```

역할:

- `ArticleRepository.findApiReadyArticles(ProcessingStatus.PUBLISHED)`로 API-ready article을 읽는다.
- `fetchArticleResponseGraph()`로 enrichment, topics, outgoing relations, target article을 미리 로드한다.
- `ArticleGraphProjectionDocument` 목록으로 변환한다.

Document 예시:

```kotlin
data class ArticleGraphProjectionDocument(
    val articleId: Long,
    val title: String,
    val source: String,
    val url: String,
    val publishedAt: String,
    val eventType: String,
    val primaryCategory: String,
    val importanceScore: Int,
    val topics: List<ArticleGraphTopicDocument>,
    val outgoingRelations: List<ArticleGraphRelationDocument>
)
```

## Backend Components

새 package:

```txt
backend/src/main/kotlin/com/sigak/search/graph/
```

추가할 주요 component:

- `ArticleGraphProjectionDocument`
- `ArticleGraphProjectionReader`
- `ArticleGraphProjectionIndexer`
- `Neo4jArticleGraphProjectionIndexer`
- `ArticleGraphProjectionRebuildService`
- `ArticleGraphProjectionController`
- `ArticleGraphContextService`
- `ArticleGraphContextController`
- `ArticleGraphProjectionRebuildResponse`
- `ArticleGraphContextResponse`

역할:

```txt
Controller
-> Service
-> Reader reads PostgreSQL source data
-> Indexer writes/queries Neo4j
```

`Neo4jArticleGraphProjectionIndexer`는 Neo4j Java Driver만 직접 다룬다. Service와 Controller는 Cypher 문자열을 알지 않게 한다.

## API Contract

### Rebuild API

```http
POST /api/internal/graph-projections/articles/rebuild
```

응답:

```json
{
  "status": "completed",
  "rebuiltAt": "2026-06-03T09:00:00Z",
  "articleNodeCount": 6,
  "topicNodeCount": 18,
  "hasTopicRelationshipCount": 18,
  "relatedToRelationshipCount": 10,
  "durationMs": 120,
  "failedReason": null
}
```

실패 응답:

```json
{
  "status": "failed",
  "rebuiltAt": null,
  "articleNodeCount": 0,
  "topicNodeCount": 0,
  "hasTopicRelationshipCount": 0,
  "relatedToRelationshipCount": 0,
  "durationMs": 35,
  "failedReason": "neo4j connectivity check failed"
}
```

실패 정책:

- PostgreSQL 원본은 변경하지 않는다.
- 실패 이유는 stack trace가 아니라 짧은 message로만 남긴다.
- 실패해도 public article API에는 영향이 없어야 한다.

### Graph Context API

```http
GET /api/internal/graph/articles/{id}/context
```

응답:

```json
{
  "articleId": 4,
  "topics": [
    {
      "name": "graph rag",
      "displayName": "Graph RAG",
      "relatedArticleIds": [1]
    }
  ],
  "relatedArticles": [
    {
      "articleId": 1,
      "title": "OpenAI Releases Agent Evaluation Toolkit",
      "relationType": "RELATED",
      "reason": "Graph RAG evaluation connects to agent and retrieval evaluation.",
      "sharedTopics": []
    }
  ],
  "timings": {
    "neo4jElapsedMs": 8,
    "totalElapsedMs": 8
  }
}
```

해석:

- `topics`는 현재 article이 연결된 topic과 같은 topic을 공유하는 다른 article id를 보여준다.
- `relatedArticles`는 explicit `RELATED_TO` edge를 기준으로 보여준다.
- public article response는 이 API를 만들 때 변경하지 않는다.

요청 검증:

- `id`는 양수여야 한다.
- Neo4j에 article node가 없으면 `404` 또는 empty context 중 하나를 선택해야 한다.
- 이번 설계에서는 `404`를 선택한다. projection이 비어 있거나 stale한 상황을 숨기지 않기 위해서다.

## Cypher Query Sketch

Rebuild article node:

```cypher
UNWIND $articles AS row
CREATE (:Article {
  articleId: row.articleId,
  title: row.title,
  source: row.source,
  url: row.url,
  publishedAt: row.publishedAt,
  eventType: row.eventType,
  primaryCategory: row.primaryCategory,
  importanceScore: row.importanceScore
})
```

Rebuild topic edges:

```cypher
UNWIND $topics AS row
MATCH (article:Article {articleId: row.articleId})
MERGE (topic:Topic {name: row.name})
ON CREATE SET topic.displayName = row.displayName
MERGE (article)-[relationship:HAS_TOPIC]->(topic)
SET relationship.position = row.position
```

Rebuild related edges:

```cypher
UNWIND $relations AS row
MATCH (source:Article {articleId: row.sourceArticleId})
MATCH (target:Article {articleId: row.targetArticleId})
MERGE (source)-[relationship:RELATED_TO]->(target)
SET relationship.relationType = row.relationType,
    relationship.reason = row.reason
```

Graph context query:

```cypher
MATCH (article:Article {articleId: $articleId})
OPTIONAL MATCH (article)-[relation:RELATED_TO]->(related:Article)
OPTIONAL MATCH (article)-[:HAS_TOPIC]->(topic:Topic)<-[:HAS_TOPIC]-(topicPeer:Article)
RETURN article, collect(distinct relation), collect(distinct related), collect(distinct topic), collect(distinct topicPeer)
```

구현 시에는 응답 mapping이 쉬운 형태로 query를 쪼갤 수 있다. MVP에서는 하나의 거대한 Cypher보다 읽기 쉬운 2-3개 query를 선호한다.

## Frontend/Public API 확장 방향

이번 구현 단위에서는 public API와 frontend를 변경하지 않는다.

다음 단계에서 확장할 후보:

```json
{
  "graphContext": {
    "relatedArticles": [
      {
        "articleId": 1,
        "reason": "Graph RAG evaluation connects to agent and retrieval evaluation.",
        "sharedTopics": ["evaluation"]
      }
    ],
    "relatedConcepts": ["Graph RAG", "knowledge graphs"]
  }
}
```

하지만 public `ArticleResponse`에 바로 nested graph context를 넣을지는 구현 후 다시 판단한다.

후보:

1. `GET /api/articles/{id}/graph-context` public endpoint 추가
2. `GET /api/articles/{id}` 응답에 optional `graphContext` 추가
3. frontend detail이 internal graph context를 호출하지 않고 backend public endpoint만 호출

추천은 1번이다. 기존 article detail response shape를 덜 흔들고, graph 장애 fallback을 분리하기 쉽다.

public 확장 시 장애 처리 원칙:

- article detail 본문은 `GET /api/articles/{id}`로 계속 독립적으로 보여준다.
- graph context는 별도 public endpoint로 불러오고, 실패하면 frontend는 graph section만 숨긴다.
- public graph context endpoint를 만들 때는 Neo4j 장애를 사용자-facing article detail 실패로 전파하지 않는다.
- 장애 여부는 internal metrics, dev-log, smoke result에 남긴다.

## Error And Fallback Policy

Projection rebuild:

- 실패를 response의 `status=failed`로 반환한다.
- PostgreSQL 원본은 변경하지 않는다.
- 실패 사유는 짧은 message만 반환한다.

Internal graph context:

- Neo4j unavailable이면 internal API는 `503` 또는 실패 response를 반환한다.
- article node가 없으면 `404`로 반환한다.
- public article detail은 아직 Neo4j에 의존하지 않으므로 영향을 받지 않는다.

Public graph-aware detail 단계:

- Neo4j가 실패해도 article detail 기본 내용은 보여야 한다.
- graph context만 비우거나 fallback message 없이 생략한다.
- 장애 신호는 internal metrics 또는 dev-log smoke에 남긴다.

민감 데이터 정책:

- Neo4j에는 관계 탐색에 필요한 최소 metadata만 저장한다.
- raw content, summary 전문, why-it-matters 전문, embedding vector는 Neo4j에 저장하지 않는다.
- 향후 Graph RAG context에 긴 본문이나 embedding evidence가 필요해지면 PostgreSQL/Qdrant에서 권한이 통제된 경로로 다시 읽는다.
- Neo4j는 context selection을 돕는 graph index 역할로 유지한다.

## Metrics And Verification

Projection rebuild response에 포함할 값:

- `rebuiltAt`
- `articleNodeCount`
- `topicNodeCount`
- `hasTopicRelationshipCount`
- `relatedToRelationshipCount`
- `durationMs`
- `failedReason`

Graph context smoke에서 확인할 값:

- article `4`의 topics에 `Graph RAG`, `knowledge graphs`, `retrieval quality`가 있는지
- article `4`의 related article reason이 반환되는지
- Neo4j Browser 또는 `cypher-shell`에서 node/relationship count가 response와 맞는지

추후 metrics endpoint 후보:

```http
GET /api/internal/search-metrics/article-graph
```

이번 첫 구현에서는 별도 metrics endpoint를 만들지 않고 rebuild/context 응답의 count와 timing으로 충분히 검증한다.

## Test Plan

Backend focused tests:

- `ArticleGraphProjectionReaderTest`
  - API-ready article만 projection document로 변환한다.
  - topic order와 relation reason을 보존한다.
  - relation target이 API-ready article이 아니면 relation projection 대상에서 제외한다.

- `ArticleGraphProjectionRebuildServiceTest`
  - empty article set이면 Neo4j projection을 비우고 completed response를 반환한다.
  - rebuild 성공 시 article/topic/relation count를 반환한다.
  - indexer failure는 failed response로 변환하고 PostgreSQL write를 하지 않는다.

- `ArticleGraphProjectionControllerTest`
  - rebuild endpoint가 service response를 반환한다.

- `ArticleGraphContextServiceTest`
  - related article reason과 shared topic을 반환한다.
  - missing article은 not found로 처리한다.

- `ArticleGraphContextControllerTest`
  - internal context endpoint의 response shape와 404를 검증한다.

Indexer tests:

- Neo4j driver 직접 integration test는 첫 구현에서는 과하게 만들지 않는다.
- `Neo4jArticleGraphProjectionIndexer`는 mock/fake driver보다 service-level test와 local smoke로 검증한다.
- 필요하면 Testcontainers Neo4j를 다음 단계에서 도입한다.

Verification gate:

```bash
cd backend
./gradlew test --tests 'com.sigak.search.graph.*'
./gradlew test
./gradlew check
```

Local smoke:

```bash
docker compose -f infra/docker-compose.yml up -d --pull never postgres neo4j
cd backend
./gradlew bootRun
```

다른 터미널:

```bash
curl -sS -X POST http://localhost:8080/api/internal/graph-projections/articles/rebuild
curl -sS http://localhost:8080/api/internal/graph/articles/4/context
```

기대:

- rebuild response의 article count가 API-ready article 수와 같다.
- topic relationship count가 article topic 수와 같다.
- related relationship count가 API-ready target relation 수와 같다.
- article `4` context에 relation reason이 포함된다.
- rebuild response에 `rebuiltAt`이 포함된다.

## Documentation Plan

구현 후 갱신할 문서:

- `docs/API_SPEC.md`
- `docs/API_SPEC.ko.md`
- `docs/STATUS.md`
- `docs/STATUS.ko.md`
- `docs/ROADMAP.md`
- `docs/ROADMAP.ko.md`
- `experiments/README.md`는 graph-aware benchmark와 연결될 때 갱신한다.
- `docs/blog/2026-06-03-dev-log.md`
- `docs/blog/topic-queue.md`

블로그 후보:

- `PostgreSQL source of truth와 Neo4j projection store를 분리한 이유`
- `Graph RAG를 챗봇이 아니라 article detail의 relation reason부터 시작한 이유`

## Trade-offs

얻는 것:

- Neo4j를 원본 저장소로 만들지 않고도 graph-aware feature를 검증할 수 있다.
- public article detail을 깨지 않고 graph projection을 먼저 검증할 수 있다.
- relation reason을 product UI와 research evaluation 양쪽에서 재사용할 수 있다.

미루는 것:

- graph explorer
- graph-aware public search ranking
- LLM relation extraction
- relation quality human review
- Testcontainers Neo4j 기반 integration test

## Risks

### 작은 데이터셋

현재 article 수가 작으면 graph가 빈약해 보일 수 있다. 이 문제는 다른 대화에서 진행할 article catalog 확장과 병렬로 해결한다.

### Stale Projection

PostgreSQL article이 추가된 뒤 Neo4j rebuild를 하지 않으면 graph context가 오래될 수 있다. MVP에서는 수동 rebuild로 시작하고, demo script에서 collection 이후 ES/Qdrant/Neo4j rebuild 순서를 명시한다.

후속 판단 기준:

- collection 후 graph context missing article이 반복된다.
- demo script에서 수동 rebuild 누락이 자주 발생한다.
- article catalog가 커져 projection rebuild 시간이 사용 흐름을 방해한다.

이 중 하나가 실제로 관찰되면 scheduled rebuild 또는 projection rebuild orchestration을 별도 작업으로 추가한다.

### Relation Quality

현재 relation reason은 seed/mock enrichment 기반이다. 실제 relation extraction 품질을 주장하지 않는다. 첫 목표는 graph projection과 context 조회가 재현 가능하다는 것을 보여주는 것이다.

후속 판단 기준:

- 실제 수집 article의 relation reason이 사람이 보기에 부정확하거나 모호하다.
- graph-aware detail이 단순 related article list보다 더 나은 설명을 제공한다는 주장을 하고 싶다.
- relation type이 `RELATED` 하나로는 부족해진다.

이때 human-reviewed relation set과 accepted/rejected/ambiguous 예시를 추가한다.

### Topic Fragmentation

대소문자 정규화만으로는 topic fragmentation을 완전히 막을 수 없다. 하지만 현재 article 수에서는 synonym dictionary나 multilingual topic clustering이 더 큰 복잡도를 만든다.

후속 판단 기준:

- 같은 개념이 3개 이상 표기로 반복된다.
- 한국어/영어 topic이 섞이면서 graph context가 분리된다.
- query label 확장 중 topic 표현 차이가 retrieval/graph 평가를 방해한다.

이때 topic normalization policy와 synonym dictionary를 별도 설계한다.

### Public API Coupling

Neo4j를 public detail에 바로 넣으면 장애 영향이 커진다. 그래서 internal context API로 먼저 검증하고 public 확장은 다음 단계로 둔다.

## 구현 순서 제안

1. Graph projection document/reader 설계와 테스트
2. Neo4j projection indexer interface와 fake indexer 기반 service 테스트
3. Rebuild service/controller 구현
4. Graph context query service/controller 구현
5. API docs와 status/roadmap 갱신
6. Local smoke로 Neo4j node/relationship count 확인
7. 다음 단계에서 public article detail graph context 설계

## 완료 기준

- `POST /api/internal/graph-projections/articles/rebuild`가 article/topic/relation count를 반환한다.
- Neo4j에 `Article`, `Topic`, `HAS_TOPIC`, `RELATED_TO` projection이 생성된다.
- `GET /api/internal/graph/articles/{id}/context`가 relation reason과 topic context를 반환한다.
- PostgreSQL은 source of truth로 유지된다.
- public article response shape는 이번 단계에서 변하지 않는다.
- local smoke 결과가 dev-log와 status에 기록된다.
