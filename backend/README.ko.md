# 백엔드

[English](README.md) | [한국어](README.ko.md)

Sigak의 메인 애플리케이션 백엔드는 Kotlin 기반 Spring Boot입니다.

## 책임
- 프론트엔드를 위한 공개 REST API 제공
- 비즈니스 로직 처리
- 기사 데이터 영속화
- AI 전용 기능이 필요할 때 AI 서버 호출

## 현재 상태
백엔드는 PostgreSQL 기반 뉴스 article API를 제공합니다.

```http
GET /api/articles
GET /api/articles?query={query}
GET /api/articles/{id}
```

Article 응답에는 `eventType`, `primaryCategory`, `topics`, `summary`, `whyItMatters`, `importanceScore`, `relatedArticleIds` 같은 제품 기획 필드가 포함됩니다.

공개 검색은 non-blank query에 대해 Elasticsearch keyword 후보와 Qdrant vector 후보를 reciprocal rank fusion으로 결합하고, 최종 응답은 PostgreSQL에서 다시 읽습니다. 두 projection path가 모두 실패하면 PostgreSQL field filtering으로 fallback합니다. 내부 개발용 vector search endpoint는 score와 timing detail을 확인하는 diagnostics API로 유지합니다.

백엔드에는 로컬 검색 인프라 readiness 경계가 추가되어 있습니다.

```http
POST /api/internal/collections/runs
GET /api/internal/collections/failure-events
GET /api/internal/search-infrastructure/health
POST /api/internal/search-projections/articles/rebuild
POST /api/internal/search-projections/article-vectors/rebuild
POST /api/internal/vector-search/articles
GET /api/internal/search-metrics/article-vectors
```

Collection endpoint는 선택 source를 실행하고, failure diagnostics endpoint는 저장된 실패 근거를 조회합니다. Health endpoint는 Elasticsearch, Qdrant, Neo4j에 접근할 수 있는지 확인합니다. Elasticsearch rebuild endpoint는 API-ready PostgreSQL article을 설정된 article index에 색인합니다. Qdrant vector rebuild endpoint는 FastAPI embedding을 통해 API-ready article을 vector로 만들고, 설정된 article vector collection을 재생성한 뒤 article ID와 debugging payload metadata를 저장합니다.

로컬 Vite 프론트엔드 origin인 `http://localhost:5173`, `http://127.0.0.1:5173`은 `/api/**` CORS 요청에 허용됩니다.

## 로컬 실행
필요 조건:
- Java 17
- Docker
- `infra/docker-compose.yml`로 실행하는 PostgreSQL
- 선택적으로 `infra/docker-compose.yml`로 실행하는 검색 인프라

이 디렉터리에서 데이터베이스를 실행합니다.

```bash
docker compose -f ../infra/docker-compose.yml up -d postgres
```

로컬 search projection store까지 확인하려면 검색 인프라도 함께 실행합니다.

```bash
docker compose -f ../infra/docker-compose.yml up -d elasticsearch qdrant neo4j
```

FastAPI embedding 또는 Qdrant projection 연동을 작업할 때는 AI 서버도 함께 실행합니다.

```bash
docker compose -f ../infra/docker-compose.yml up -d ai
```

그다음 백엔드를 실행합니다.

```bash
./gradlew bootRun
```

다음 주소를 엽니다.

```txt
http://localhost:8080/api/articles
http://localhost:8080/api/articles?query=rag
http://localhost:8080/api/internal/search-infrastructure/health
```

Elasticsearch article projection을 재생성합니다.

```bash
curl -X POST http://localhost:8080/api/internal/search-projections/articles/rebuild
```

내부 Qdrant article vector projection을 재생성하고 검색합니다.

```bash
curl -X POST http://localhost:8080/api/internal/search-projections/article-vectors/rebuild
curl -X POST http://localhost:8080/api/internal/vector-search/articles \
  -H 'Content-Type: application/json' \
  -d '{"query":"AI supply chain security risk","limit":10}'
curl http://localhost:8080/api/internal/search-metrics/article-vectors
```

로컬 인프라 환경값:

```txt
SIGAK_ELASTICSEARCH_URL=http://localhost:9200
SIGAK_ELASTICSEARCH_ARTICLE_INDEX=sigak-articles-v1
SIGAK_AI_SERVER_URL=http://localhost:8000
SIGAK_EMBEDDING_PROVIDER=local
SIGAK_EMBEDDING_MODEL_NAME=sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2
SIGAK_QDRANT_URL=http://localhost:6333
SIGAK_QDRANT_ARTICLE_COLLECTION=sigak-article-vectors-minilm-v1
SIGAK_QDRANT_DISTANCE=Cosine
SIGAK_QDRANT_DEFAULT_LIMIT=10
SIGAK_QDRANT_MAX_LIMIT=50
SIGAK_NEO4J_URI=bolt://localhost:7687
SIGAK_NEO4J_USERNAME=neo4j
SIGAK_NEO4J_PASSWORD=sigak-neo4j-password
```

## API 문서
백엔드를 실행하면 로컬에서 Swagger UI를 확인할 수 있습니다.

```txt
http://localhost:8080/swagger-ui/index.html
```

생성된 OpenAPI JSON은 다음 주소에서 확인할 수 있습니다.

```txt
http://localhost:8080/v3/api-docs
```

## 테스트
이 디렉터리에서 실행합니다.

```bash
./gradlew test
```

## 수집 파이프라인

백엔드는 source registry, source fetch 실행, collector parsing, normalization, mock enrichment, persistence를 담당합니다.

현재 수집 기능:
- 선별된 공식 기술 출처에 대한 RSS/Atom source registry entry
- 선별된 연구 카테고리에 대한 arXiv API source registry entry
- 로컬 XML fixture를 사용하는 parser test
- 로컬 mock enrichment client
- XML을 가져와 article을 파싱하고, enrichment한 뒤 persisted article record로 publish하는 source execution service
- canonical URL, source external ID, source/title/published date 기반 duplicate detection
- 수집 원문과 현재 enrichment output의 분리 저장

기본 로컬 경로는 여전히 mock enrichment를 사용하므로, 백엔드 개발에는 유료 AI API key가 필요하지 않습니다. 수집된 article은 `/api/articles`가 사용하는 동일한 persisted article model로 publish되며, 공개 article API 응답 모양은 안정적으로 유지됩니다.
