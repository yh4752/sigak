# API 명세

[English](API_SPEC.md) | [한국어](API_SPEC.ko.md)

## 목적
이 문서는 Sigak의 현재 MVP API 계약을 설명합니다.

API는 단순하게 유지하되, 이후 search, enrichment, Graph RAG 기능이 추가되어도 응답 모양이 크게 흔들리지 않도록 설계합니다.

## 현재 엔드포인트

```http
GET /api/articles
GET /api/articles?query={query}
GET /api/articles/{id}
GET /api/internal/search-metrics/articles
GET /v3/api-docs
GET /swagger-ui/index.html
```

검색 결과는 `GET /api/articles`와 같은 응답 모양을 사용합니다.

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

키워드 검색:

```http
GET /api/articles?query=rag
```

non-blank `query`가 들어오면 백엔드는 Elasticsearch를 우선 keyword search projection으로 사용합니다. Elasticsearch는 article ID 후보만 반환하고, Spring Boot가 PostgreSQL에서 API-ready article response를 다시 조립합니다. 공개 응답 데이터의 source of truth는 PostgreSQL입니다.

동작 세부사항:
- 앞뒤 공백 무시
- `GET /api/articles`와 같은 응답 모양
- Elasticsearch projection 검색 대상:
  - `title`
  - `summary`
  - `topics`
  - `primaryCategory`
  - `whyItMatters`
- Elasticsearch를 사용할 수 없으면 Spring Boot가 PostgreSQL field filtering으로 fallback
- PostgreSQL fallback 검색 대상:
  - `title`
  - `summary`
  - `primaryCategory`
  - `topics`
- fallback 검색은 대소문자를 구분하지 않음
- 검색 metric은 내부에서 기록함
  - query length
  - result count
  - fallback 여부
  - elapsed time

Semantic search와 graph-aware retrieval은 이후 개선 사항입니다. 백엔드 구현이 발전해도 search response shape는 안정적으로 유지해야 합니다.

## 내부 Article Search Metrics 계약

공개 article response에는 성능 metadata를 넣지 않습니다. 검색 latency와 fallback 동작은 internal endpoint로 확인합니다. 이렇게 하면 사용자 API 계약을 바꾸지 않고도 local 개발과 smoke test에서 검색 상태를 관찰할 수 있습니다.

```http
GET /api/internal/search-metrics/articles
```

예상 응답:

```json
{
  "totalSearchCount": 2,
  "elasticsearchSearchCount": 1,
  "fallbackSearchCount": 1,
  "fallbackRate": 0.5,
  "averageElapsedMs": 20.0,
  "p50ElapsedMs": 10,
  "p95ElapsedMs": 30,
  "lastSearch": {
    "queryLength": 6,
    "resultCount": 2,
    "fallback": true,
    "elapsedMs": 30
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

## 오류 동작

알 수 없는 article ID는 `404 Not Found`를 반환합니다.

```http
GET /api/articles/999
```

```http
HTTP/1.1 404 Not Found
```

정확한 오류 응답 body는 현재 MVP 계약에 포함하지 않습니다.

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
  "suggestedImportanceScore": 70
}
```
