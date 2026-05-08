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

현재 키워드 검색은 저장된 article 필드를 대상으로 백엔드 서비스에서 수행합니다. Elasticsearch, vector search, 외부 AI 연동은 아직 구현하지 않았습니다.

로컬 Vite 프론트엔드 origin인 `http://localhost:5173`, `http://127.0.0.1:5173`은 `/api/**` CORS 요청에 허용됩니다.

## 로컬 실행
필요 조건:
- Java 17
- Docker
- `infra/docker-compose.yml`로 실행하는 PostgreSQL

이 디렉터리에서 데이터베이스를 실행합니다.

```bash
docker compose -f ../infra/docker-compose.yml up -d postgres
```

그다음 백엔드를 실행합니다.

```bash
./gradlew bootRun
```

다음 주소를 엽니다.

```txt
http://localhost:8080/api/articles
http://localhost:8080/api/articles?query=rag
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
