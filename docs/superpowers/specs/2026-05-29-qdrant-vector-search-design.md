# Qdrant Article Vector Search Design

날짜: 2026-05-29

## 목표

오늘 작업의 목표는 Sigak article 데이터를 Qdrant에 vector projection으로 저장하고, 내부 API로 semantic vector search를 실행할 수 있게 만드는 것이다.

구현 범위는 다음까지로 제한한다.

- PostgreSQL의 API-ready article을 embedding input으로 변환한다.
- FastAPI embedding endpoint를 호출해 multilingual vector를 만든다.
- Qdrant collection을 생성하고 article vector point를 upsert한다.
- 내부 search endpoint에서 query를 embedding하고 Qdrant를 검색한다.
- Qdrant 결과의 article id 순서를 유지해 PostgreSQL article response를 다시 조회한다.
- embedding, Qdrant search, PostgreSQL article load 시간을 응답 또는 metrics로 확인할 수 있게 한다.

## 비목표

- public `GET /api/articles` 검색 경로에 vector search를 바로 연결하지 않는다.
- Elasticsearch keyword search와 Qdrant vector search를 hybrid ranking으로 합치지 않는다.
- Qdrant payload만으로 사용자 응답을 만들지 않는다.
- Graph RAG 또는 Neo4j 관계 검색을 이번 작업에 포함하지 않는다.
- 대형 embedding model, GPU, 외부 유료 embedding API를 전제로 하지 않는다.

## 용어

### Projection

Projection은 PostgreSQL의 원본 article 데이터를 검색 저장소에 맞게 복사하고 가공해 둔 재생성 가능한 데이터다.

Sigak에서는 PostgreSQL이 source of truth이고, Elasticsearch/Qdrant/Neo4j는 각자의 검색 목적에 맞춘 projection store다.

Qdrant article vector projection은 article의 title, summary, why-it-matters, category, topics 같은 의미 검색용 텍스트를 embedding vector로 바꿔 Qdrant point로 저장한 것이다.

### Public API

Public API는 frontend나 실제 사용자 기능에서 직접 호출할 수 있는 API다. 예를 들어 `GET /api/articles`와 `GET /api/articles/{id}`가 여기에 가깝다.

이번 작업은 public API가 아니라 internal API로 먼저 제공한다. vector search 품질, fallback 정책, keyword search와의 결합 방식을 검증한 뒤 public API에 연결하기 위함이다.

## 접근안 비교

### 접근안 A. Qdrant projection rebuild만 구현

장점:

- 가장 작고 안전하다.
- Qdrant collection 생성과 upsert만 검증하면 된다.

단점:

- 실제 vector search 결과를 확인할 수 없다.
- 오늘 목표인 "Qdrant vector search까지 개발"에는 부족하다.

판단: 범위가 너무 좁다.

### 접근안 B. 내부 vector search까지 구현하고 public API 연결은 보류

장점:

- projection, embedding, vector search, article reload 흐름을 끝까지 검증할 수 있다.
- public API에 섞기 전에 성능 지표와 오류 지점을 관찰할 수 있다.
- 다음 단계에서 hybrid search나 public API 연결로 자연스럽게 확장된다.

단점:

- 내부 API와 metrics DTO가 추가된다.
- frontend에서는 아직 직접 사용할 수 없다.

판단: 오늘 구현안으로 선택한다.

### 접근안 C. public article search에 바로 vector search 연결

장점:

- 사용자 화면과 빠르게 연결할 수 있다.
- 데모 효과가 크다.

단점:

- keyword search, vector search, fallback, ranking merge 정책을 한 번에 결정해야 한다.
- Qdrant 또는 FastAPI 장애가 public API 안정성에 영향을 줄 수 있다.
- MVP 일정에서 search 품질 검증 전 노출 리스크가 크다.

판단: 다음 단계로 미룬다.

## 최종 결정

접근안 B를 선택한다.

오늘은 internal vector search를 완성한다.

```txt
PostgreSQL API-ready articles
-> Spring Boot rebuild service
-> FastAPI embedding API
-> Qdrant article vector collection

Internal vector search request
-> Spring Boot vector search service
-> FastAPI embedding API
-> Qdrant point search
-> PostgreSQL article reload
-> article + score + timing response
```

## API 설계

### Rebuild API

```http
POST /api/internal/search-projections/article-vectors/rebuild
```

역할:

- API-ready article 목록을 PostgreSQL에서 읽는다.
- 각 article을 embedding input text로 변환한다.
- FastAPI embedding endpoint를 호출한다.
- Qdrant collection을 재생성하고 point를 upsert한다.

응답 예시:

```json
{
  "status": "completed",
  "collectionName": "sigak-article-vectors-minilm-v1",
  "indexedCount": 12,
  "embeddingProvider": "local",
  "embeddingModelName": "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
  "embeddingDimension": 384,
  "durationMs": 1420,
  "failedReason": null
}
```

실패 정책:

- rebuild 실패는 PostgreSQL 원본 데이터를 변경하지 않는다.
- 실패 이유는 `failedReason`에 담는다.
- FastAPI embedding 호출 실패, Qdrant 호출 실패, dimension mismatch는 모두 실패로 처리한다.

### Vector Search API

```http
POST /api/internal/vector-search/articles
```

요청:

```json
{
  "query": "AI supply chain security risk",
  "limit": 10
}
```

응답:

```json
{
  "query": "AI supply chain security risk",
  "collectionName": "sigak-article-vectors-minilm-v1",
  "embeddingProvider": "local",
  "embeddingModelName": "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
  "embeddingDimension": 384,
  "results": [
    {
      "score": 0.82,
      "article": {
        "id": 3,
        "title": "Critical Package Registry Attack Targets AI Toolchains",
        "source": "Security Advisory Board",
        "url": "https://example.com/articles/ai-toolchain-package-attack",
        "publishedAt": "2026-05-03T15:45:00Z",
        "eventType": "SECURITY",
        "primaryCategory": "SECURITY",
        "topics": ["supply chain security", "AI tooling"],
        "summary": "A coordinated package registry attack targeted developer environments.",
        "whyItMatters": "AI development stacks combine packages, credentials, and automation.",
        "importanceScore": 93,
        "relatedArticleIds": [1, 5]
      }
    }
  ],
  "timings": {
    "embeddingElapsedMs": 24,
    "qdrantElapsedMs": 8,
    "articleLoadElapsedMs": 5,
    "totalElapsedMs": 37
  }
}
```

요청 검증:

- `query`는 blank를 허용하지 않는다.
- `limit` 기본값은 10이다.
- `limit` 최댓값은 50이다.

### Metrics API

```http
GET /api/internal/search-metrics/article-vectors
```

응답에는 다음 값을 포함한다.

- `totalSearchCount`
- `averageTotalElapsedMs`
- `p50TotalElapsedMs`
- `p95TotalElapsedMs`
- `averageEmbeddingElapsedMs`
- `averageQdrantElapsedMs`
- `averageArticleLoadElapsedMs`
- `lastSearch`

기존 article keyword metrics와 같은 in-memory 방식으로 시작한다. 앱 재시작 시 metric이 초기화되는 것은 MVP 단계에서 허용한다.

## Qdrant Collection 설계

기본 설정:

```yaml
sigak.search.qdrant.url: http://localhost:6333
sigak.search.qdrant.article-collection-name: sigak-article-vectors-minilm-v1
sigak.search.qdrant.distance: Cosine
sigak.search.qdrant.default-limit: 10
sigak.search.qdrant.max-limit: 50
```

Qdrant collection은 같은 collection 안의 vector가 동일한 dimensionality와 distance metric을 가져야 한다. 따라서 collection 이름은 embedding model family/version을 포함한다.

rebuild 정책:

- 오늘은 rebuild 시 collection을 삭제 후 재생성한다.
- 삭제 대상 collection이 없어도 rebuild는 계속 진행한다.
- 새 collection은 첫 embedding response의 `dimension`으로 생성한다.
- 이후 article embedding response의 `dimension`, `modelName`, `provider`가 첫 response와 다르면 rebuild를 실패시킨다.
- write operation은 `wait=true`를 사용해 smoke test에서 색인 직후 검색 가능성을 높인다.

운영 확장 시 재검토:

- public 트래픽에 vector search를 연결하면 delete-and-create 대신 blue-green collection 또는 alias swap을 검토한다.
- 대량 데이터가 늘어나면 batch size, payload index, on-disk vector, optimizer 설정을 별도로 다룬다.

## Qdrant Point 설계

Point id:

- `article.id`를 Qdrant point id로 사용한다.
- Qdrant는 unsigned integer 또는 UUID point id를 지원하므로, 현재 article id와 잘 맞는다.

Vector:

- FastAPI embedding response의 `embedding`을 그대로 사용한다.

Payload:

```json
{
  "articleId": 3,
  "title": "Critical Package Registry Attack Targets AI Toolchains",
  "source": "Security Advisory Board",
  "url": "https://example.com/articles/ai-toolchain-package-attack",
  "publishedAt": "2026-05-03T15:45:00Z",
  "eventType": "SECURITY",
  "primaryCategory": "SECURITY",
  "topics": ["supply chain security", "AI tooling"],
  "importanceScore": 93,
  "relatedArticleIds": [1, 5],
  "embeddingProvider": "local",
  "embeddingModelName": "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
  "embeddingDimension": 384
}
```

Payload는 검색 결과에서 article id와 debugging metadata를 확인하기 위한 보조 데이터다. 사용자 응답의 article 내용은 PostgreSQL에서 다시 읽는다.

## Embedding Input 설계

article vector input text는 다음 필드로 구성한다.

```txt
title
summary
whyItMatters
primaryCategory
topics
eventType
```

제외하는 필드:

- `url`: 의미 검색 품질보다 노이즈가 클 수 있다.
- `source`: 언론사/출처명은 semantic meaning보다 filtering 후보에 가깝다.
- `publishedAt`: 시간 정보는 semantic vector보다 필터/정렬 조건으로 다루는 편이 낫다.
- `importanceScore`: 의미 텍스트가 아니라 ranking 보정 후보로 남긴다.
- `relatedArticleIds`: graph relation projection에서 다룬다.

입력 포맷:

```txt
Title: ...
Summary: ...
Why it matters: ...
Category: ...
Topics: topic1, topic2
Event type: ...
```

한글과 영어 기사는 같은 multilingual FastEmbed model을 사용한다. 언어별 collection을 나누지 않는다.

## Spring Boot 구조

예상 추가/변경 파일:

```txt
backend/src/main/kotlin/com/sigak/search/config/SearchInfrastructureProperties.kt
backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorDocument.kt
backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorTextBuilder.kt
backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorProjectionIndexer.kt
backend/src/main/kotlin/com/sigak/search/vector/QdrantArticleVectorProjectionIndexer.kt
backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorProjectionRebuildService.kt
backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorProjectionController.kt
backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorSearchService.kt
backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorSearchController.kt
backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorSearchRequest.kt
backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorSearchResponse.kt
backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorSearchResult.kt
backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorSearchTimings.kt
backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorSearchMetricsRecorder.kt
backend/src/main/kotlin/com/sigak/search/vector/ArticleVectorSearchMetricsController.kt
```

기존 `search.projection` 패키지는 Elasticsearch keyword projection 중심이라, Qdrant vector search는 `search.vector` 패키지로 분리한다.

서비스 책임:

- `ArticleVectorTextBuilder`: ArticleResponse를 embedding input text로 변환한다.
- `ArticleVectorProjectionRebuildService`: PostgreSQL article 목록을 읽고 embedding/Qdrant upsert 흐름을 조율한다.
- `QdrantArticleVectorProjectionIndexer`: Qdrant collection delete/create/upsert/search REST 호출을 담당한다.
- `ArticleVectorSearchService`: query embedding, Qdrant search, PostgreSQL article reload, metric 기록을 담당한다.
- `ArticleVectorSearchMetricsRecorder`: vector search timing observation을 in-memory로 누적한다.

## Article Reload 정책

Qdrant search 결과는 score 기준으로 정렬되어 온다. Spring Boot는 Qdrant payload의 `articleId` 또는 point id를 읽고 PostgreSQL에서 API-ready article을 다시 조회한다.

응답 정렬 정책:

- Qdrant result order를 유지한다.
- PostgreSQL에 존재하지 않거나 API-ready 상태가 아닌 article은 응답에서 제외한다.
- 제외된 article이 있어도 search 자체는 실패로 보지 않는다. projection store는 stale할 수 있기 때문이다.

필요한 ArticleService 확장:

- 기존 private `findApiReadyArticleResponsesByIds`와 같은 동작을 public/internal service method로 열거나, repository 호출을 vector service에서 중복하지 않는 방향으로 작게 리팩터링한다.
- controller가 entity를 직접 다루지 않도록 유지한다.

## Error Handling

- blank query: 400 response로 거절한다.
- invalid limit: 400 response로 거절한다.
- FastAPI embedding failure: internal vector search는 실패한다. public API가 아니므로 fallback을 붙이지 않는다.
- Qdrant unavailable: internal vector search는 실패한다.
- rebuild 중 article별 embedding dimension/model/provider mismatch: rebuild 실패로 처리한다.
- stale Qdrant result: PostgreSQL reload 단계에서 제외한다.

## 성능 지표

오늘 기록할 timing:

- `embeddingElapsedMs`: query embedding 호출 시간
- `qdrantElapsedMs`: Qdrant point search 호출 시간
- `articleLoadElapsedMs`: PostgreSQL article reload 시간
- `totalElapsedMs`: vector search 전체 시간

rebuild timing:

- 전체 rebuild duration만 응답에 포함한다.
- article별 embedding 시간, batch별 upsert 시간은 필요해지면 다음 단계에서 추가한다.

성능 판단 기준:

- local MVP에서는 수치 자체보다 병목 구간을 분리해서 볼 수 있는지가 중요하다.
- public API 연결 전에는 embedding latency와 Qdrant latency를 분리해서 확인해야 한다.

## 테스트 전략

TDD로 진행한다.

추가할 주요 테스트:

- `ArticleVectorTextBuilderTest`: title, summary, why-it-matters, category, topics, event type이 안정적인 input text로 합쳐지는지 검증한다.
- `QdrantArticleVectorProjectionIndexerTest`: collection delete/create/upsert/search REST 요청 body와 URI를 검증한다.
- `ArticleVectorProjectionRebuildServiceTest`: article을 embedding하고 Qdrant document로 넘기는지, provider/model/dimension mismatch를 실패 처리하는지 검증한다.
- `ArticleVectorSearchServiceTest`: query embedding, Qdrant result id 순서 유지, PostgreSQL article reload, timing 응답 생성을 검증한다.
- `ArticleVectorSearchControllerTest`: request validation과 response mapping을 검증한다.
- `ArticleVectorSearchMetricsRecorderTest`: 평균, p50, p95, lastSearch 계산을 검증한다.

검증 명령:

```bash
./gradlew test
```

가능하면 구현 후 smoke test:

```bash
docker compose -f infra/docker-compose.yml up -d postgres elasticsearch qdrant ai
```

```bash
curl -X POST http://localhost:8080/api/internal/search-projections/article-vectors/rebuild
```

```bash
curl -X POST http://localhost:8080/api/internal/vector-search/articles \
  -H 'Content-Type: application/json' \
  -d '{"query":"AI supply chain security risk","limit":10}'
```

## 문서 업데이트 대상

구현 시 다음 문서를 갱신한다.

- `README.md`
- `README.ko.md`
- `backend/README.md`
- `backend/README.ko.md`
- `docs/API_SPEC.md`
- `docs/API_SPEC.ko.md`
- `docs/STATUS.md`
- `docs/STATUS.ko.md`
- `docs/blog/topic-queue.md`

## 다음 단계

오늘 구현 이후 다음 순서:

1. 내부 vector search 결과를 sample query set으로 확인한다.
2. keyword search와 vector search의 결과 차이를 기록한다.
3. public API 연결 방식 또는 hybrid ranking 설계를 별도 문서로 작성한다.
4. Neo4j article relation projection을 설계한다.

## 근거 자료

- Qdrant collection은 같은 collection 내 vector의 dimensionality와 metric을 맞춰야 하며, collection 생성 시 vector `size`와 `distance`를 설정한다.
  - https://qdrant.tech/documentation/concepts/collections/
- Qdrant create collection REST API는 `PUT /collections/:collection_name`에 `vectors.size`, `vectors.distance`를 전달한다.
  - https://api.qdrant.tech/api-reference/collections/create-collection
- Qdrant delete collection REST API는 `DELETE /collections/:collection_name`을 사용한다.
  - https://api.qdrant.tech/api-reference/collections/delete-collection
- Qdrant upsert points REST API는 `PUT /collections/:collection_name/points`를 사용하며, 기존 point id가 있으면 overwrite한다.
  - https://api.qdrant.tech/api-reference/points/upsert-points
- Sigak local Qdrant image는 `infra/docker-compose.yml` 기준 `qdrant/qdrant:v1.12.4`이므로, search API는 v1.12의 `POST /collections/:collection_name/points/search`를 사용한다.
  - https://api.qdrant.tech/v-1-12-x/api-reference/search/points
