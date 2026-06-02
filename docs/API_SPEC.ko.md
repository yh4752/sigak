# API 명세

[English](API_SPEC.md) | [한국어](API_SPEC.ko.md)

## 목적
이 문서는 Sigak의 현재 MVP API 계약을 설명합니다.

API는 단순하게 유지하되, 이후 search, enrichment, Graph RAG 기능이 추가되어도 응답 모양이 크게 흔들리지 않도록 설계합니다.

## 현재 엔드포인트

```http
GET /api/articles
GET /api/articles?query={query}
GET /api/articles?ids={id1},{id2}
GET /api/articles/{id}
POST /api/internal/collections/runs
GET /api/internal/collections/failure-events
GET /api/internal/search-metrics/articles
POST /api/internal/search-projections/article-vectors/rebuild
POST /api/internal/vector-search/articles
GET /api/internal/search-metrics/article-vectors
GET /v3/api-docs
GET /swagger-ui/index.html
```

검색 결과와 bulk ID 조회 결과는 `GET /api/articles`와 같은 응답 모양을 사용합니다.

## 내부 Collection 수동 재실행 판단표

Collection failure event는 local MVP 운영을 위한 internal diagnostics입니다. 자동 retry, 삭제, acknowledgement, projection rebuild를 실행하지 않습니다. 수동 재실행이 필요한 경우에는 먼저 `failureKind`, `retryable`, `stage`, article hint, backend log를 확인합니다.

| failure kind | 재실행 여부 | 운영자 행동 | 참고 |
| --- | --- | --- | --- |
| `TRANSIENT_FETCH` | 가능 | 강제 실패 설정을 제거하고, network/Docker health 또는 upstream feed 회복을 확인한 뒤 같은 source를 다시 실행합니다. | timeout, connection failure, 5xx, 429를 포함합니다. 중복 article은 skipped로 집계되므로 재실행해도 안전합니다. |
| `SOURCE_FORMAT` | 먼저 조사 | source response, RSS/Atom/arXiv parsing 가정, source registry 설정을 확인한 뒤 재실행합니다. | source 형식이 그대로면 같은 실패가 반복될 가능성이 큽니다. |
| `INVALID_ARTICLE` | 먼저 조사 | `articleExternalId`, `articleUrl`, `articleTitle` hint와 normalization/enrichment validation rule을 확인합니다. | 수집 item이 API-ready article이 되지 못한 경우가 많습니다. |
| `PERSISTENCE` | 먼저 수정 | database health, Flyway 상태, constraint, persistence mapping을 확인하고 수정 후 재실행합니다. | source 문제가 아니라 인프라 또는 schema 문제로 다룹니다. |
| `UNKNOWN` | 먼저 분류 | event message, stage, fingerprint, backend log를 확인하고 필요하면 분류 rule 또는 테스트를 추가합니다. | 새로운 failure mode를 숨기지 않기 위해 보수적으로 처리합니다. |

수동 재실행은 internal endpoint 또는 command runner로 같은 source ID를 다시 실행합니다. Failure diagnostics endpoint는 read-only이며 retry를 시작하지 않습니다.

## 생성 API 문서

백엔드는 `springdoc-openapi`를 사용해 Spring MVC controller와 DTO schema에서 OpenAPI 문서를 생성합니다.

로컬 문서 URL:

```txt
http://localhost:8080/swagger-ui/index.html
http://localhost:8080/v3/api-docs
```

`docs/API_SPEC.md`는 사람이 읽기 쉬운 API 설계 기준 문서입니다. Swagger UI와 `/v3/api-docs`는 백엔드 코드에서 생성되는 실행 가능한 API 문서입니다.

## Article 응답

`GET /api/articles`는 article response JSON 배열을 반환합니다.

`GET /api/articles?ids=1,2,3`은 요청한 ID에 해당하는 API-ready article을 반환합니다. 중복 ID와 없거나 공개 상태가 아닌 ID는 제외하고, 남은 결과는 요청 순서를 유지합니다.

`GET /api/articles/{id}`는 article response 하나를 반환합니다.

```json
{
  "id": 1,
  "title": "OpenAI Releases Agent Evaluation Toolkit",
  "source": "OpenAI",
  "url": "https://example.com/articles/openai-agent-evals",
  "publishedAt": "2026-05-01T09:00:00Z",
  "eventType": "OFFICIAL_ANNOUNCEMENT",
  "primaryCategory": "AI",
  "topics": ["LLM agents", "evaluation", "production AI"],
  "summary": "OpenAI introduced a toolkit for evaluating agent behavior in multi-step workflows.",
  "whyItMatters": "Agent evaluation is becoming a practical requirement as teams move from demos to production workflows.",
  "importanceScore": 88,
  "relatedArticleIds": [3, 5]
}
```

## Article 필드

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `id` | Long | 양수 article identifier입니다. 현재 dataset 안에서 안정적입니다. |
| `title` | String | 비어 있지 않은 표시용 제목입니다. |
| `source` | String | 사람이 읽을 수 있는 비어 있지 않은 출처 이름입니다. |
| `url` | String | 비어 있지 않은 원문 article URL입니다. 저장된 article data에서는 unique입니다. |
| `publishedAt` | String | `2026-05-01T09:00:00Z` 같은 ISO-8601 UTC timestamp입니다. |
| `eventType` | String enum | 기술 이벤트의 유형입니다. |
| `primaryCategory` | String enum | 탐색과 필터링에 사용하는 주 기술 카테고리입니다. |
| `topics` | String array | 세부 기술 개념입니다. 1-8개를 권장합니다. |
| `summary` | String | 과장 없는 짧은 사실 요약입니다. |
| `whyItMatters` | String | 입문자도 이해할 수 있지만 기술적으로 의미 있는 중요도 설명입니다. |
| `importanceScore` | Integer | 0-100 범위의 큐레이션 중요도 점수입니다. |
| `relatedArticleIds` | Long array | 관련 article ID 목록입니다. 비어 있을 수 있으며, 가능하면 존재하는 article을 가리켜야 합니다. |

## Event Type

허용되는 `eventType` 값:

- `NEWS`
- `OFFICIAL_ANNOUNCEMENT`
- `RESEARCH`
- `SECURITY`
- `RELEASE`

## Primary Category

허용되는 `primaryCategory` 값:

- `AI`
- `SECURITY`
- `SOFTWARE_ENGINEERING`
- `BACKEND`
- `FRONTEND`
- `DATA`
- `INFRA_CLOUD`
- `DEVTOOLS`
- `CS_RESEARCH`

## Importance Score

`importanceScore`는 0-100 범위의 정수입니다.

MVP seed data에서는 수동으로 큐레이션합니다.

| 범위 | 수준 | 의미 |
| --- | --- | --- |
| `90-100` | Critical | 기술 생태계에 큰 영향을 줄 수 있는 중대한 보안 이슈, AI/CS 변화, 매우 중요한 공식 발표입니다. |
| `75-89` | High | 많은 개발자나 팀이 알아야 할 중요한 변화입니다. |
| `50-74` | Medium | 특정 기술 독자층에 의미 있는 변화입니다. |
| `0-49` | Low | 기록할 수는 있지만 보통 메인 important feed에 올리지 않을 항목입니다. |

이후 AI enrichment가 점수를 제안할 수 있지만, 최종 저장 점수는 수동 검토 또는 규칙으로 조정될 수 있습니다.

현재 MVP UI에서 `importanceScore`는 ranking과 curation signal로 사용합니다. Article 상세 페이지는 원시 숫자를 노출할 필요가 없습니다. 이후 제품 결정으로 정성 label을 도입하기 전까지 중요도는 `whyItMatters`로 설명합니다.

## 검색 동작

하이브리드 검색:

```http
GET /api/articles?query=rag
```

non-blank `query`가 들어오면 백엔드는 Elasticsearch에서 keyword 후보를 만들고 Qdrant에서 vector 후보를 만듭니다. 두 후보 목록은 reciprocal rank fusion(RRF)으로 합쳐지고, Spring Boot가 PostgreSQL에서 API-ready article response를 다시 조립합니다. 공개 응답 데이터의 source of truth는 PostgreSQL입니다.

동작 세부사항:
- 앞뒤 공백 무시
- `GET /api/articles`와 같은 응답 모양
- keyword 후보의 Elasticsearch projection 검색 대상:
  - `title`
  - `summary`
  - `topics`
  - `primaryCategory`
  - `whyItMatters`
- vector 후보는 title, summary, why-it-matters, category, topics, event type으로 만든 Qdrant article vector projection을 검색
- 한쪽 projection path만 실패하면 다른 path로 `KEYWORD_ONLY` 또는 `VECTOR_ONLY` 결과를 반환
- 두 projection path가 모두 실패하면 Spring Boot가 PostgreSQL field filtering으로 fallback
- PostgreSQL fallback 검색 대상:
  - `title`
  - `summary`
  - `primaryCategory`
  - `topics`
- fallback 검색은 대소문자를 구분하지 않음
- 검색 metric은 내부에서 기록함
  - query length
  - result count
  - search mode
  - candidate count
  - stale candidate count
  - failure flag와 fallback reason
  - latency breakdown

Graph-aware retrieval은 이후 개선 사항입니다. 백엔드 구현이 발전해도 public search response shape는 안정적으로 유지해야 합니다.

## Bulk Article 조회

프론트엔드는 related article을 bulk로 조회해 article detail 화면에서 related ID 개수만큼 HTTP 요청이 늘어나지 않게 합니다.

```http
GET /api/articles?ids=4,1,4,999
```

동작 세부사항:
- ID는 comma-separated query parameter로 전달할 수 있습니다.
- 중복 ID는 첫 요청 순서를 유지하면서 deduplicate합니다.
- unknown, draft, 비공개 article ID는 응답에서 제외합니다.
- `query`와 `ids`가 함께 있으면 명시적 article reload path인 `ids` 조회를 우선합니다.
- 응답 모양은 list/search와 같은 article response 배열입니다.

## 내부 Article Search Metrics 계약

공개 article response에는 성능 metadata를 넣지 않습니다. 검색 latency와 fallback 동작은 internal endpoint로 확인합니다. 이렇게 하면 사용자 API 계약을 바꾸지 않고도 local 개발과 smoke test에서 검색 상태를 관찰할 수 있습니다.

```http
GET /api/internal/search-metrics/articles
```

예상 응답:

```json
{
  "totalSearchCount": 2,
  "hybridSearchCount": 1,
  "keywordOnlySearchCount": 0,
  "vectorOnlySearchCount": 0,
  "postgresFallbackSearchCount": 1,
  "fallbackRate": 0.5,
  "averageTotalElapsedMs": 30.0,
  "p50TotalElapsedMs": 20,
  "p95TotalElapsedMs": 40,
  "lastSearch": {
    "queryLength": 6,
    "resultCount": 2,
    "mode": "POSTGRES_FALLBACK",
    "keywordCandidateCount": 0,
    "vectorCandidateCount": 0,
    "fusedCandidateCount": 0,
    "staleCandidateCount": 0,
    "keywordFailed": true,
    "vectorFailed": true,
    "fallbackReason": "KEYWORD_SEARCH_FAILED; QDRANT_SEARCH_FAILED",
    "keywordElapsedMs": 2,
    "embeddingElapsedMs": 0,
    "vectorElapsedMs": 2,
    "fusionElapsedMs": 0,
    "articleReloadElapsedMs": 4,
    "totalElapsedMs": 40
  }
}
```

참고:
- metric은 in-memory이며 backend process가 재시작되면 초기화됩니다.
- 원본 query text는 저장하지 않고 query length만 기록합니다.
- 이는 production observability stack이 아니라 MVP local metric boundary입니다.

## 내부 Search Projection 계약

공개 article API는 안정적으로 유지하고, search projection store는 PostgreSQL에서 재생성합니다.

```http
POST /api/internal/search-projections/articles/rebuild
```

예상 응답:

```json
{
  "status": "completed",
  "indexName": "sigak-articles-v1",
  "indexedCount": 5,
  "durationMs": 42,
  "failedReason": null
}
```

Rebuild 작업은 PostgreSQL의 API-ready article을 읽어 Elasticsearch에 색인합니다. PostgreSQL은 source of truth로 유지하고, Elasticsearch는 재생성 가능한 projection store로 둡니다.

로컬 smoke test:

```bash
docker compose -f infra/docker-compose.yml up -d postgres elasticsearch
cd backend
./gradlew bootRun
curl -X POST http://localhost:8080/api/internal/search-projections/articles/rebuild
curl http://localhost:9200/sigak-articles-v1/_count
curl "http://localhost:8080/api/articles?query=graph"
curl http://localhost:8080/api/internal/search-metrics/articles
```

Fallback smoke test:

```bash
docker compose -f infra/docker-compose.yml stop elasticsearch
curl "http://localhost:8080/api/articles?query=graph"
docker compose -f infra/docker-compose.yml up -d elasticsearch
```

Fallback 요청도 matching article을 반환해야 하며, backend log에는 `fallback=true`가 남아야 합니다.

## 내부 Article Vector Projection 계약

Qdrant는 article embedding을 재생성 가능한 projection으로 저장합니다. PostgreSQL은 article 응답 데이터의 source of truth로 유지하고, FastAPI는 embedding provider 경계로 둡니다.

```http
POST /api/internal/search-projections/article-vectors/rebuild
```

예상 응답:

```json
{
  "status": "completed",
  "collectionName": "sigak-article-vectors-minilm-v1",
  "indexedCount": 5,
  "embeddingProvider": "local",
  "embeddingModelName": "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
  "embeddingDimension": 384,
  "durationMs": 1420,
  "failedReason": null
}
```

동작:
- PostgreSQL에서 API-ready article을 읽습니다.
- title, summary, why-it-matters, category, topics, event type으로 embedding input을 만듭니다.
- FastAPI `POST /api/embeddings/text`를 호출합니다.
- 설정된 Qdrant article vector collection을 재생성합니다.
- article ID, vector, debugging payload metadata를 저장합니다.
- 한 번의 rebuild 안에서 embedding provider, model name, dimension이 달라지면 실패합니다.
- embedding vector 길이가 선언된 dimension과 다르면 collection 재생성 전에 실패합니다.

## 내부 Article Vector Search 계약

Internal vector search는 query를 embedding하고, Qdrant를 검색한 뒤, 최종 article response는 PostgreSQL에서 다시 읽습니다. 공개 `GET /api/articles?query=...`는 이제 같은 하위 vector candidate boundary를 hybrid search의 일부로 사용하며, 이 endpoint는 score와 timing detail을 확인하는 diagnostics API로 유지합니다.

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

예상 응답:

```json
{
  "query": "AI supply chain security risk",
  "collectionName": "sigak-article-vectors-minilm-v1",
  "embeddingProvider": "local",
  "embeddingModelName": "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
  "embeddingDimension": 384,
  "results": [
    {
      "score": 0.91,
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

동작:
- `query`는 trim하며 blank query는 거절합니다.
- `limit`은 설정된 Qdrant 기본값을 사용하고, 설정된 최댓값으로 제한합니다.
- Qdrant는 article ID와 score를 반환하고, Spring Boot는 최종 article response를 PostgreSQL에서 다시 읽습니다.
- PostgreSQL에서 더 이상 API-ready로 노출되지 않는 stale Qdrant hit은 응답에서 제외합니다.
- 중복 Qdrant article hit이 있으면 ranking 순서상 첫 score를 유지합니다.

## 내부 Article Vector Search Metrics 계약

```http
GET /api/internal/search-metrics/article-vectors
```

예상 응답:

```json
{
  "totalSearchCount": 2,
  "averageTotalElapsedMs": 15.0,
  "p50TotalElapsedMs": 10,
  "p95TotalElapsedMs": 20,
  "averageEmbeddingElapsedMs": 6.0,
  "averageQdrantElapsedMs": 5.0,
  "averageArticleLoadElapsedMs": 4.0,
  "lastSearch": {
    "queryLength": 12,
    "resultCount": 3,
    "embeddingElapsedMs": 8,
    "qdrantElapsedMs": 7,
    "articleLoadElapsedMs": 5,
    "totalElapsedMs": 20
  }
}
```

참고:
- metric은 in-memory이며 backend process가 재시작되면 초기화됩니다.
- 성공한 vector search만 기록합니다.
- 원본 query text는 저장하지 않고 query length만 기록합니다.

## 오류 동작

알 수 없는 article ID는 `404 Not Found`를 반환합니다.

```http
GET /api/articles/999
```

```http
HTTP/1.1 404 Not Found
```

정확한 오류 응답 body는 현재 MVP 계약에 포함하지 않습니다.

## 내부 Embedding 계약

AI 서버는 vector projection 개발을 위한 embedding endpoint를 제공합니다. 현재 구현은 deterministic test mode와 local multilingual FastEmbed mode를 모두 지원하므로, 유료 API key 없이 Spring Boot -> FastAPI -> Qdrant 연결을 재현 가능하게 테스트하면서 실제 semantic retrieval 경로도 사용할 수 있습니다.

v0.1의 기본 retrieval 경로는 한글, 영어 등 다양한 언어의 기사에 대응할 수 있는 실제 multilingual embedding model을 사용하는 방향으로 잡습니다. Deterministic embedding은 fallback/test mode로 유용하지만 semantic search 품질의 근거로 사용하지 않습니다. 첫 real model 경로는 무거운 PyTorch/CUDA 의존성을 피하면서 ONNX Runtime 기반 local embedding을 제공하는 FastEmbed를 사용합니다.

```http
POST /api/embeddings/text
```

요청:

```json
{
  "text": "Graph RAG improves relationship-aware retrieval."
}
```

Deterministic 응답 예시:

```json
{
  "provider": "deterministic",
  "modelName": "sigak-deterministic-hash-v1",
  "dimension": 8,
  "embedding": [0.123456, -0.234567, 0.345678, -0.456789, 0.567891, -0.678912, 0.789123, -0.891234]
}
```

Local model 응답 예시:

```json
{
  "provider": "local",
  "modelName": "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
  "dimension": 384,
  "embedding": [0.012345, -0.023456, 0.034567]
}
```

동작 세부사항:
- 같은 text는 항상 같은 vector를 반환합니다.
- whitespace-only text는 거절합니다.
- deterministic mode는 MVP local smoke test를 위해 작은 8차원 vector를 사용합니다.
- local model mode는 설정된 model dimension을 사용합니다.
- Spring Boot는 internal embedding client boundary를 통해 이 endpoint를 호출합니다.
- Qdrant projection code는 이 endpoint를 교체 가능한 embedding boundary로 다뤄야 합니다.
- real embedding mode는 색인 projection metadata에 model/provider name과 vector dimension을 기록해야 합니다.

Spring Boot 설정:

```txt
SIGAK_AI_SERVER_URL=http://localhost:8000
SIGAK_EMBEDDING_PROVIDER=local
SIGAK_EMBEDDING_MODEL_NAME=sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2
```

## 내부 AI Enrichment 계약

공개 article API는 안정적으로 유지합니다. 내부적으로는 수집된 article을 AI enrichment 전에 정규화합니다.

현재 백엔드는 로컬 mock enrichment에 같은 request/response shape를 사용합니다. Spring Boot가 FastAPI와 HTTP로 연결될 때 의도한 내부 계약은 다음과 같습니다.

```http
POST /api/enrichment/article
```

```json
{
  "title": "Evaluating Retrieval Agents",
  "source": "arXiv cs.AI",
  "url": "http://arxiv.org/abs/2605.00001v1",
  "publishedAt": "2026-05-05T00:00:00Z",
  "topics": ["CS_RESEARCH"],
  "rawContent": "We study retrieval agents in technical knowledge workflows."
}
```

AI service는 enrichment candidate를 반환합니다.

```json
{
  "summary": "Evaluating Retrieval Agents discusses We study retrieval agents in technical knowledge workflows.",
  "whyItMatters": "This matters because arXiv cs.AI is connected to CS_RESEARCH and may affect how technical teams understand the topic.",
  "suggestedTopics": ["CS_RESEARCH"],
  "suggestedPrimaryCategory": "CS_RESEARCH",
  "suggestedImportanceScore": 70,
  "modelName": "mock-enrichment"
}
```
