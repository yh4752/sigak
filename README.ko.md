# Sigak

[English](README.md) | [한국어](README.ko.md)

Sigak은 AI, 소프트웨어 개발, 컴퓨터 과학 분야의 중요한 기술 뉴스를 선별하고 맥락을 설명하는 AI 기반 기술 뉴스 인사이트 플랫폼입니다.

초기 MVP는 사용자가 중요한 기술 변화를 찾고, 왜 중요한지 이해하며, 관련 개념을 탐색할 수 있게 돕는 데 집중합니다. 구조는 단순하고 배포 가능해야 하며, 향후 AI/RAG 기능을 붙일 수 있도록 열어 둡니다.

## 아키텍처
- `backend/`: Spring Boot 메인 백엔드와 REST API.
- `frontend/`: React + TypeScript + Vite 프론트엔드.
- `ai/`: AI/RAG 관련 기능을 담당하는 FastAPI 서비스.
- `infra/`: Docker Compose와 로컬 개발 인프라.
- `docs/`: 제품 정의, 로드맵, API, 연구 전략, 현재 상태, 아키텍처 결정 기록.

Spring Boot가 기본 API 경계입니다. 프론트엔드는 우선 Spring Boot를 호출하고, Spring Boot가 AI 전용 기능이 필요할 때 FastAPI를 호출합니다.

## MVP 범위
- 뉴스 기사 목록
- 뉴스 기사 상세
- 키워드 검색
- 선택한 기사에 대한 AI 요약
- 중요도와 why-it-matters 인사이트
- Graph RAG 준비가 가능한 기사 메타데이터
- 단순한 프론트엔드 UI
- Docker Compose 기반 로컬 실행 환경

## 현재 상태
프로젝트는 초기 가이드 문서, 최소 모노레포 구조, PostgreSQL 기반 백엔드 기사 API, Swagger/OpenAPI 문서, 첫 번째 프론트엔드 홈/검색/상세 흐름을 갖춘 상태입니다.

선별된 출처 기반 수집 파이프라인은 RSS/Atom과 arXiv 피드를 파싱하고, mock enrichment를 적용하며, 수집 항목을 중복 제거한 뒤 API에 노출 가능한 기사를 원문과 현재 enrichment 기록과 함께 저장합니다. 공개 article API는 `PUBLISHED` 상태이고 current enrichment가 있는 기사만 노출합니다.

```http
GET /api/articles
GET /api/articles?query={query}
GET /api/articles/{id}
```

현재 키워드 검색은 non-blank query에 대해 Elasticsearch projection을 우선 사용하고, Elasticsearch를 사용할 수 없으면 PostgreSQL field filtering으로 fallback합니다. 내부 개발용 Qdrant vector projection rebuild와 semantic search endpoint도 사용할 수 있습니다. 다만 공개 article search에는 아직 vector/hybrid ranking을 연결하지 않았습니다. 프론트엔드는 Axios API client로 백엔드를 호출하고, article 응답은 Zod로 검증합니다. article 상세 화면은 핵심 인사이트 필드를 보여주되 원시 `importanceScore` 숫자는 노출하지 않습니다. 이 점수는 현재 목록 정렬용으로만 사용합니다. FastAPI는 mock enrichment와 configurable embedding provider를 제공합니다. scheduled/admin collection entry point, public vector search, hybrid ranking, FastAPI HTTP enrichment mode, 외부 AI API 연동은 이후 단계로 남겨 두었습니다.

## 로컬 실행
저장소 루트에서 PostgreSQL과 Elasticsearch를 실행합니다.

```bash
docker compose -f infra/docker-compose.yml up -d postgres elasticsearch
```

Embedding/Qdrant 연동 작업을 할 때는 AI 서버도 함께 실행합니다.

```bash
docker compose -f infra/docker-compose.yml up -d qdrant ai
```

백엔드를 실행합니다.

```bash
cd backend
./gradlew bootRun
```

백엔드가 실행된 뒤 다른 터미널에서 article search projection을 rebuild합니다.

```bash
curl -X POST http://localhost:8080/api/internal/search-projections/articles/rebuild
```

내부 Qdrant vector search를 확인하려면 vector projection을 rebuild한 뒤 semantic query를 실행합니다.

```bash
curl -X POST http://localhost:8080/api/internal/search-projections/article-vectors/rebuild
curl -X POST http://localhost:8080/api/internal/vector-search/articles \
  -H 'Content-Type: application/json' \
  -d '{"query":"AI supply chain security risk","limit":10}'
curl http://localhost:8080/api/internal/search-metrics/article-vectors
```

다른 터미널에서 프론트엔드를 실행합니다.

```bash
cd frontend
npm install
npm run dev
```

로컬 URL:

```txt
http://localhost:5173
http://localhost:8080/swagger-ui/index.html
http://localhost:8080/api/articles
```

## 프로젝트 구조
```txt
sigak/
├── ai/
├── backend/
├── docs/
│   ├── PRODUCT.md
│   ├── ROADMAP.md
│   ├── STATUS.md
│   ├── RESEARCH_STRATEGY.md
│   ├── ko/
│   └── decisions/
├── frontend/
├── infra/
│   └── docker-compose.yml
├── AGENTS.md
├── README.md
└── .env.example
```

## 문서
- [문서 인덱스](docs/README.ko.md)
- [제품 정의](docs/PRODUCT.ko.md)
- [로드맵](docs/ROADMAP.ko.md)
- [현재 상태](docs/STATUS.ko.md)
- [연구 전략](docs/RESEARCH_STRATEGY.ko.md)
- [API 명세](docs/API_SPEC.ko.md)
- [출처 정책](docs/SOURCE_POLICY.ko.md)
- [한국어 가이드](docs/ko/GUIDE.md)
- [초기 아키텍처 ADR](docs/decisions/0001-initial-architecture.ko.md)
- [제품 범위와 Graph RAG 전략 ADR](docs/decisions/0002-product-scope-and-graph-rag-strategy.ko.md)
