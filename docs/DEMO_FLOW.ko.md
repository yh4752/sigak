# Sigak 로컬 데모 흐름

[English](DEMO_FLOW.md) | [한국어](DEMO_FLOW.ko.md)

마지막 업데이트: 2026-05-31

이 문서는 현재 Sigak v0.1 백엔드 slice를 재현하기 위한 로컬 데모 스크립트다. 선별 source collection, failure diagnostics, Elasticsearch projection, Qdrant projection, public hybrid search가 연결되어 있고 PostgreSQL이 source of truth로 남아 있음을 확인한다.

## 이 흐름이 증명하는 것

```txt
selected source collection
-> PostgreSQL article persistence
-> collection failure diagnostics lookup
-> Elasticsearch keyword projection rebuild
-> Qdrant vector projection rebuild
-> public hybrid search
-> search metrics inspection
```

현재 제한: Neo4j graph projection은 아직 이 흐름에 포함되지 않는다.

## 준비

특별히 다른 디렉터리를 지정하지 않는 한 저장소 루트에서 실행한다.

필요한 서비스:

```bash
docker compose -f infra/docker-compose.yml up -d --pull never postgres elasticsearch qdrant ai
docker compose -f infra/docker-compose.yml ps postgres elasticsearch qdrant ai
```

기대 신호: PostgreSQL, Elasticsearch, Qdrant, AI server가 `healthy` 상태다.

다른 터미널에서 백엔드를 실행한다.

```bash
cd backend
./gradlew bootRun
```

기대 신호: Spring Boot가 `http://localhost:8080`에서 시작된다.

## 1단계. 선택 source collection 실행

```bash
curl -X POST http://localhost:8080/api/internal/collections/runs \
  -H 'Content-Type: application/json' \
  -d '{"sourceIds":["github-blog"],"maxArticlesPerSource":1}'
```

기대 신호:

- `status`는 `COMPLETED` 또는 `PARTIAL`이다.
- `runId`가 반환된다.
- `publishedArticleCount`, `skippedArticleCount`, `failedArticleCount`가 run 결과를 설명한다.
- 중복 article은 failure가 아니라 skipped로 집계된다.

2026-05-31 관측값:

```json
{
  "runId": "947c254a-3b79-4a48-836a-2b0e66884503",
  "status": "COMPLETED",
  "publishedArticleCount": 0,
  "skippedArticleCount": 1,
  "failedArticleCount": 0,
  "skippedArticleIds": [6]
}
```

## 2단계. Failure diagnostics 조회

1단계의 `runId`를 사용한다.

```bash
curl "http://localhost:8080/api/internal/collections/failure-events?sourceId=github-blog&runId=<RUN_ID>&limit=10"
```

기대 신호:

- run에 실패가 있었다면 `events`에 `failureKind`, `retryable`, `message`, 선택적 article hint가 들어간다.
- run이 성공했거나 중복 skip만 있었다면 `returnedCount`가 `0`일 수 있다.

2026-05-31 관측값:

```json
{
  "returnedCount": 0,
  "events": []
}
```

## 3단계. Elasticsearch keyword projection rebuild

```bash
curl -X POST http://localhost:8080/api/internal/search-projections/articles/rebuild
curl http://localhost:9200/sigak-articles-v1/_count
```

기대 신호:

- Rebuild 응답의 `status`가 `completed`다.
- `indexedCount`는 PostgreSQL의 API-ready article 수와 일치한다.
- Elasticsearch `_count`가 `indexedCount`와 일치한다.

2026-05-31 관측값:

```json
{
  "status": "completed",
  "indexName": "sigak-articles-v1",
  "indexedCount": 6,
  "failedReason": null
}
```

Elasticsearch count:

```json
{
  "count": 6
}
```

## 4단계. Qdrant vector projection rebuild

```bash
curl -X POST http://localhost:8080/api/internal/search-projections/article-vectors/rebuild
curl http://localhost:6333/collections/sigak-article-vectors-minilm-v1
```

기대 신호:

- Rebuild 응답의 `status`가 `completed`다.
- Main semantic retrieval path에서는 `embeddingProvider`가 `local`이다.
- `sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2`의 `embeddingDimension`은 `384`다.
- Qdrant collection의 `points_count`가 `indexedCount`와 일치한다.

2026-05-31 관측값:

```json
{
  "status": "completed",
  "collectionName": "sigak-article-vectors-minilm-v1",
  "indexedCount": 6,
  "embeddingProvider": "local",
  "embeddingModelName": "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
  "embeddingDimension": 384,
  "failedReason": null
}
```

Qdrant collection signal:

```json
{
  "status": "green",
  "points_count": 6
}
```

## 5단계. Public hybrid search 실행

```bash
curl "http://localhost:8080/api/articles?query=graph"
curl http://localhost:8080/api/internal/search-metrics/articles
```

기대 신호:

- Public search는 projection store payload가 아니라 article response를 반환한다.
- Elasticsearch와 Qdrant가 모두 사용 가능하면 search metrics의 `lastSearch.mode`가 `HYBRID`다.
- 방금 rebuild한 smoke에서는 `staleCandidateCount`가 `0`이어야 한다.

2026-05-31 관측값:

```json
{
  "totalSearchCount": 1,
  "hybridSearchCount": 1,
  "postgresFallbackSearchCount": 0,
  "lastSearch": {
    "queryLength": 5,
    "resultCount": 6,
    "mode": "HYBRID",
    "keywordCandidateCount": 1,
    "vectorCandidateCount": 6,
    "fusedCandidateCount": 6,
    "staleCandidateCount": 0,
    "fallbackReason": null
  }
}
```

## 정리

백엔드는 `Ctrl+C`로 종료한다.

데모가 끝나면 로컬 인프라를 중지한다.

```bash
docker compose -f infra/docker-compose.yml down
```

로컬 데이터를 의도적으로 삭제하고 싶을 때만 volume reset을 사용한다.

```bash
docker compose -f infra/docker-compose.yml down -v
```

## 문제 해결

- Collection이 duplicate skip을 반환해도 유효한 persistence 신호다. 이미 PostgreSQL에 article이 있다는 뜻이다.
- `events`가 비어 있으면 먼저 collection run response의 `failedArticleCount`, `failedSourceCount`를 확인한다.
- Elasticsearch나 Qdrant rebuild가 실패하면 해당 Docker service health와 log를 확인한다.
- Qdrant rebuild가 AI server 호출 중 실패하면 `http://localhost:8000/health`가 응답하는지 확인한다.
- Projection rebuild는 수동이다. Collection은 Elasticsearch, Qdrant, Neo4j를 자동 rebuild하지 않는다.
