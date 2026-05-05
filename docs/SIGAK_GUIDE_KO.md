# Sigak 프로젝트 종합 가이드 (한글판)

> 주니어 개발자 / 입문자를 위한 풀이 문서
> 원본 문서 위치: `/docs` 폴더
> 최종 업데이트: 2026-05-05

---

## 0. 이 문서를 어떻게 읽으면 좋을까

이 문서는 Sigak 프로젝트의 `docs/` 폴더 안 모든 문서(`PROJECT_CONTEXT.md`, `PRODUCT_PLAN.md`, `API_SPEC.md`, `SOURCE_POLICY.md`, `ROADMAP.md`, ADR 3개, 개발 로그, 디자인 명세)를 **주니어 개발자나 입문자가 한 번에 이해할 수 있도록** 한글로 풀어 쓴 가이드입니다.

원본 문서들은 다소 흩어져 있어 한 번에 큰 그림을 잡기 어렵기 때문에, 이 문서는 **"왜 → 무엇을 → 어떻게"** 순서로 설명합니다.

- 시간이 없다면: **1장(한눈에 보기)**, **4장(아키텍처)**, **12장(로드맵)**, **15장(핵심 정리)** 만 읽어도 큰 그림이 잡힙니다.
- 처음부터 끝까지 읽으면 약 15~20분 정도 걸립니다.
- 익숙하지 않은 용어가 나오면 **6장(개념 풀어쓰기)** 를 사전처럼 활용하세요.

---

## 1. 프로젝트 한눈에 보기

### Sigak이란?

**Sigak**(시각)은 AI, 소프트웨어 개발, 컴퓨터공학(CS) 분야의 **중요한 기술 뉴스만 골라서**, **왜 그 뉴스가 중요한지**와 **어떤 다른 기술/사건과 연결되는지**까지 보여주는 **AI 기반 기술 뉴스 인사이트 플랫폼**입니다.

쉽게 비유하면:

- 일반 RSS 리더 = 모든 글을 다 보여줌 → 정보 과부하
- 일반 뉴스 요약기 = 글 하나하나는 요약해 줌 → 그러나 "이게 왜 중요한지", "다른 사건과 어떻게 연결되는지"는 모름
- **Sigak** = 신뢰할 만한 출처에서 **중요한 것만 골라**, **요약 + 중요한 이유 + 관련 개념과 다른 기사 연결**까지 한 번에 보여줌

### 한 줄 슬로건

> **"중요한 기술 변화를, 맥락과 관계와 함께 설명한다."**
> *(Important technical changes, explained with context and relationships.)*

### 목표 (왜 만드는가)

이 프로젝트는 **2026년 6월 말까지 인턴 지원용 포트폴리오급 MVP**를 만드는 것이 1차 목표입니다. 그래서 화려한 기능보다는 다음을 잘 보여주는 것이 우선입니다.

- 명확한 백엔드 아키텍처 설계
- 현실적인 API 설계
- 간결한 AI 통합
- 로컬에서 한 번에 띄울 수 있는 개발 환경 (Docker Compose)
- 향후 확장(Graph RAG, 시맨틱 검색)을 막지 않는 데이터 모델

### 누가 쓰는가 (타깃 사용자)

- AI / 소프트웨어 엔지니어링 트렌드를 따라가고 싶은 개발자
- AI, CS, 개발에 관심 있는 학생
- 모든 글을 직접 다 읽지 않고도 **"중요한 것만 + 맥락과 함께"** 보고 싶은 사람

---

## 2. 콘텐츠 정책 (어떤 뉴스를 다루는가)

Sigak은 **양보다 질**을 우선합니다. 인터넷의 모든 글을 긁어오는 일반 크롤러가 아닙니다.

### 포함 (Include)

- 주요 회사/연구소/표준 단체/주요 오픈소스 프로젝트의 **공식 발표**
- AI, CS, 소프트웨어 개발 분야의 **신뢰할 만한 뉴스**
- **심각한 보안 이슈** (CVE, 취약점, 사고 보고)
- **영향력 있는 연구 논문** 또는 연구 발표
- 언어/프레임워크/플랫폼/개발 도구의 **주요 릴리즈 노트**

### 제외 (Exclude — MVP 단계에서)

- 일반 개인 기술 블로그 글
- 단순 튜토리얼
- 마케팅성/홍보성 콘텐츠
- 영향이 적은 작은 패치 릴리즈
- 루머나 검증 안 된 커뮤니티 토론
- 같은 글을 그대로 옮긴 중복 글

> 개인 기술 블로그는 나중에 **품질 기준이 명확해지면** 다시 검토할 수 있습니다.

### 카테고리 (primaryCategory)

기사 하나에는 **하나의 주 카테고리 + 여러 개의 토픽**이 붙습니다.

| 카테고리 | 다루는 내용 |
|---|---|
| `AI` | LLM, Agent, RAG, ML, AI 제품, AI 플랫폼 |
| `SECURITY` | CVE, 취약점, 공급망 보안, AI 보안 |
| `SOFTWARE_ENGINEERING` | 아키텍처, 테스트, 품질, 유지보수, 엔지니어링 관행 |
| `BACKEND` | API, 서버, 분산 시스템, 메시징, JVM/Spring, 백엔드 프레임워크 |
| `FRONTEND` | 웹 플랫폼, 브라우저, UI 프레임워크, 프론트엔드 도구 |
| `DATA` | DB, 검색, 분석, 스토리지, 데이터 엔지니어링 |
| `INFRA_CLOUD` | 클라우드, DevOps, SRE, Kubernetes, 관측성, 배포 |
| `DEVTOOLS` | IDE, 컴파일러, 패키지 매니저, 빌드 툴, 개발자 워크플로우 |
| `CS_RESEARCH` | 알고리즘, 프로그래밍 언어, OS, 시스템 연구, HCI, 이론 |

### 이벤트 타입 (eventType)

기사 하나에 **하나의 이벤트 타입**이 붙습니다.

- `NEWS` — 일반 기술 뉴스
- `OFFICIAL_ANNOUNCEMENT` — 공식 발표
- `RESEARCH` — 중요한 논문/연구 결과
- `RELEASE` — 주요 출시(언어/프레임워크/도구)
- `SECURITY` — 심각한 보안 이슈

향후 추가 가능: `STANDARD` (RFC, PEP, JEP 같은 표준)

---

## 3. 핵심 가치 (요약기 그 이상)

Sigak이 단순 요약기가 아닌 이유는 **4가지 가치**를 동시에 제공하기 때문입니다.

1. **선별 (Selection)** — 중요한 기술 뉴스를 고른다
2. **이유 설명 (Why It Matters)** — 그 뉴스가 왜 중요한지 알려준다
3. **관계 연결 (Relationships)** — 관련 기술 개념, 조직, 사건과 연결한다
4. **그래프 준비 (Graph RAG-ready)** — 위 정보를 향후 Graph RAG 검색에 쓸 수 있는 형태로 저장한다

### 톤(Tone)

설명은 **"입문자에게도 이해되지만 깊이는 잃지 않는다"** 는 원칙을 따릅니다.

- 지나친 hype(과장)와 근거 없는 예측은 피한다
- "무엇이 바뀌었는가"와 "왜 중요한가"를 분명히 한다
- 더 큰 기술 흐름과 연결한다
- **취업 면접용 학습 콘텐츠 톤은 아닙니다** (그건 Sigak의 목표가 아님)

---

## 4. 아키텍처 (서비스 구성)

Sigak은 **3개의 서비스 + 인프라**로 이루어집니다. 책임이 분명하게 나뉘어 있습니다.

```
┌─────────────────────┐
│   React Frontend    │  ← 사용자가 보는 화면
│  (TypeScript+Vite)  │
└──────────┬──────────┘
           │ HTTP(Axios) + Zod 검증
           ▼
┌─────────────────────┐
│  Spring Boot Backend │  ← 메인 API, 비즈니스 로직, 데이터
│      (Kotlin)       │
└──────────┬──────────┘
           │ AI/RAG 작업이 필요할 때만 호출
           ▼
┌─────────────────────┐
│   FastAPI Service   │  ← AI 요약, 임베딩, RAG
│      (Python)       │
└─────────────────────┘

[추가 인프라]
- PostgreSQL/MySQL  : 데이터 저장
- Elasticsearch     : 키워드 검색 (나중)
- Qdrant            : 벡터 검색, Graph RAG (나중)
- Docker Compose    : 위 모두를 한 번에 띄움
```

### 각 서비스의 책임

**Spring Boot 백엔드 (Kotlin)**

- 메인 API의 경계 — 프론트엔드는 거의 항상 Spring Boot만 호출합니다.
- 비즈니스 규칙, 데이터 영속화(persistence), 기사 흐름 조율(orchestration)
- AI/enrichment 작업이 필요할 때만 FastAPI를 부릅니다.
- OpenAPI 문서가 자동 생성되어 `/swagger-ui/index.html`에서 확인 가능

**React 프론트엔드 (TypeScript + Vite)**

- 백엔드 응답을 **Zod**로 검증한 뒤 화면에 표시 (런타임 안전성 확보)
- HTTP 호출은 **Axios**
- MVP에서는 복잡한 상태관리(예: Redux) 사용하지 않음 — Context API로 충분

**FastAPI 서비스 (Python)**

- AI/RAG 전용 (요약, 보강, 임베딩)
- 프론트엔드가 직접 호출하지 않음 — Spring Boot를 통해서만 호출됨
- 외부 LLM API 키가 없어도 **mock**으로 동작 가능 (로컬 개발용)

### 왜 Spring Boot와 FastAPI를 분리했는가?

- Spring Boot는 사용자 대면 API와 비즈니스 로직을 책임짐 — JVM 생태계와 잘 맞음
- FastAPI는 LLM/embedding 라이브러리를 다루기 더 편한 Python 환경에 둠
- 책임이 분리되어 있어, 메인 백엔드를 과도하게 무겁게 만들지 않음

### 왜 Next.js를 안 쓰는가?

- 풀스택 통합은 편하지만, 이번 프로젝트는 **Spring Boot 백엔드 포트폴리오**가 목표
- Spring Boot의 백엔드 책임을 제대로 보여주려면 분리된 구조가 더 적절

---

## 5. 데이터 모델 (Article의 모양)

Sigak의 핵심 데이터는 **Article(기사)** 하나입니다. 모든 API는 같은 응답 모양을 사용합니다.

### Article 응답 예시

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

### 필드 설명

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Long | 양수 식별자, 현재 데이터셋 안에서 유일 |
| `title` | String | 비어 있지 않은 제목 |
| `source` | String | 출처(예: OpenAI, Google) |
| `url` | String | 원문 URL — 영속화 시 unique 보장 |
| `publishedAt` | String | ISO-8601 UTC, 예: `2026-05-01T09:00:00Z` |
| `eventType` | enum | 위에서 본 5가지 중 하나 |
| `primaryCategory` | enum | 위 9가지 중 하나 |
| `topics` | String[] | 1~8개 추천, 더 세부적인 기술 개념 |
| `summary` | String | 사실 위주의 짧은 요약 — hype 금지 |
| `whyItMatters` | String | "왜 중요한가" 입문자도 이해 가능하게 |
| `importanceScore` | Integer | 0~100, MVP 시드 데이터에서는 수동 큐레이션 |
| `relatedArticleIds` | Long[] | 연결된 다른 기사 id 목록(비어 있을 수 있음) |

### 카테고리 vs 토픽 — 왜 둘 다 있나?

- `primaryCategory`는 **넓은 분류** (UI 필터/네비게이션용)
- `topics`는 **구체적인 기술 개념** (검색, 향후 그래프 노드 후보)

예시:

```
primaryCategory: AI
eventType: RESEARCH
topics: ["RAG", "Graph RAG", "knowledge graph", "retrieval"]
```

### 향후 그래프용 데이터 모델 (MVP에는 없음)

```
ConceptNode
- id
- name
- type

ArticleConcept
- articleId
- conceptId
- relationType
- confidence
```

지금은 도입하지 않지만, **나중에 Graph RAG로 확장할 때** 위 구조를 추가할 수 있게 데이터 모델을 미리 잡아 놓았습니다.

### 수집·보강 분리 (Collection vs Enrichment)

이건 좀 더 깊은 이야기인데, 데이터 파이프라인을 만들 때 **원본 데이터**와 **AI가 만든 데이터**를 분리해서 저장합니다.

- `CollectedArticle` — 출처에서 가져온 원본 (sourceName, externalId, rawContent, extractedText 등)
- `ArticleEnrichment` — AI가 보강한 결과 (summary, whyItMatters, suggestedTopics, modelName, promptVersion 등)

이렇게 분리하는 이유는 **AI 모델/프롬프트를 바꿨을 때 원본을 다시 크롤링하지 않고도 재처리(reprocess)** 할 수 있기 때문입니다.

---

## 6. 개념 풀어쓰기 (입문자용 사전)

### Graph RAG

- **RAG(Retrieval-Augmented Generation)**: LLM이 답변할 때, **관련 문서를 검색해서 같이 보여주고** 답하게 만드는 방식. LLM이 모르는 최신 정보도 답할 수 있게 됨.
- **Graph RAG**: RAG에 **개념 사이의 관계(그래프)** 를 추가한 방식. 단순히 비슷한 문장을 찾는 게 아니라, "이 기사 → 관련 개념 → 다른 기사" 같이 **연결된 정보**를 함께 검색.
- Sigak에서는 1.0.0에서는 **제한된 수준**만 도입 — 기사 간 연결, 토픽, 관련 개념 정도. **전체 그래프 탐색 UI(Obsidian 스타일)** 는 1.0.0 이후로 미룸.

### 시맨틱 검색 (Semantic Search) vs 키워드 검색

- **키워드 검색**: 단어가 일치하는 글을 찾음. (현재 MVP가 이거)
- **시맨틱 검색**: 단어가 다르더라도 **의미가 비슷한** 글을 찾음. 임베딩(벡터)으로 비교.
- Sigak은 **응답 형태(response shape)는 그대로** 두고, 내부 구현만 키워드 → 시맨틱 → 그래프 인지로 발전시킬 예정.

### 임베딩 (Embedding) / 벡터 (Vector)

- 글이나 단어를 **숫자 배열(벡터)** 로 표현한 것. 비슷한 의미는 가까운 위치의 벡터로 변환됨.
- Qdrant 같은 **벡터 DB**에 저장해서 시맨틱 검색에 사용.

### Spring Boot / Kotlin

- **Spring Boot**: 자바 진영의 가장 대중적인 백엔드 프레임워크.
- **Kotlin**: 자바 호환 언어. null 안전, 간결한 문법. Spring과 잘 어울림.

### Vite / Zod

- **Vite**: React용 빠른 프론트엔드 빌드 도구. CRA(Create React App)의 후계 격.
- **Zod**: 런타임에 데이터 형태를 검증하는 TypeScript 라이브러리. 백엔드 응답이 예상과 다르면 즉시 에러로 잡아냄.

### ADR (Architecture Decision Record)

- **아키텍처 의사결정 기록**. "왜 이렇게 결정했는가"를 짧게 남기는 문서.
- Sigak은 `docs/decisions/` 아래에 ADR 3개를 가지고 있음.

### MVP (Minimum Viable Product)

- **최소 기능 제품**. "동작하는 가장 간단한 버전"을 먼저 만드는 전략. 기능을 다 만들기 전에 방향성부터 검증.

### Docker Compose

- 여러 서비스(백엔드, 프론트, DB, 검색엔진 등)를 **YAML 설정 하나로 묶어서** 한 번에 띄우는 도구.

### CVE / 공급망 보안

- **CVE (Common Vulnerabilities and Exposures)**: 공식 등록된 보안 취약점 식별자.
- **공급망 보안 (Supply Chain Security)**: 우리가 쓰는 라이브러리, 패키지, 빌드 도구가 침해당했을 때 일어나는 보안 문제.

---

## 7. API 명세

### 현재 엔드포인트

```
GET /api/articles                    # 전체 기사 목록
GET /api/articles?query={query}      # 키워드 검색 (응답 모양 동일)
GET /api/articles/{id}               # 기사 상세 1건
GET /v3/api-docs                     # 자동 생성된 OpenAPI 문서
GET /swagger-ui/index.html           # 사람이 보는 Swagger UI
```

핵심 원칙:
- **검색 결과의 응답 모양 = 목록 응답 모양**. 내부 구현이 키워드 → 시맨틱 → 그래프로 바뀌어도 프론트는 변경 없음.
- 자동 생성된 OpenAPI 문서는 **실행 가능한 API 문서** 역할, `docs/API_SPEC.md`는 사람이 보는 설계 문서.

### 검색 동작

```
GET /api/articles?query=rag
```

- 대소문자 구분 없음
- 앞뒤 공백 무시
- 검색 대상: `title`, `summary`, `primaryCategory`, `topics`
- 응답 모양: 목록과 동일

향후 시맨틱 검색이나 그래프 검색이 추가되어도 응답 모양은 유지.

### 에러

- 존재하지 않는 id: `404 Not Found`
- MVP에서는 에러 응답 본문(body) 형식을 계약(contract)으로 못 박지 않음.

---

## 8. 중요도 점수 (importanceScore)

`importanceScore`는 **0~100 정수**로, 큐레이션된 중요도를 나타냅니다.

### 등급

| 점수 범위 | 레벨 | 의미 |
|---|---|---|
| 90~100 | Critical | 큰 보안 이슈, 큰 AI/CS 변화, 생태계에 영향 줄 공식 발표 |
| 75~89 | High | 많은 개발자/팀이 알아야 할 변화 |
| 50~74 | Medium | 특정 기술 분야에 의미 있는 변화 |
| 0~49 | Low | 기록은 하지만 메인 피드에는 거의 안 올라감 |

> Sigak은 **선별**이 핵심이므로, MVP 시드 데이터의 대부분은 **75점 이상**입니다.

### 화면에는 노출하지 않음 — 왜?

이건 중요한 의도적 결정입니다.

- 시드 데이터는 **수동 큐레이션**이라, "88/100" 같은 정확한 숫자를 보여주면 **거짓 정밀도(false precision)** 를 줄 수 있음
- 큐레이션 자체의 품질이 신뢰감을 만드는 것이 목표
- 대신 **`whyItMatters` 텍스트**가 중요성을 설명함
- 점수는 내부에서만 사용 — 예: **Popular News 상위 3개** 선별

---

## 9. 데이터 전략 (Mock → 수집 파이프라인)

### 전략 단계

1. **MVP 시작**: 수동 큐레이션된 mock 데이터로 제품의 모양(요약, whyItMatters, 토픽, 관련 기사)을 검증
2. **MVP 후반**: RSS/API 기반의 **선별된 출처**에서 자동 수집 시작
3. **이후**: 저장된 원본을 reprocess해서 임베딩, 컨셉 추출, Graph RAG enrichment 적용

### 처리 흐름 (Phase 5에서 확립)

```
DISCOVER → FETCH → EXTRACT → NORMALIZE → ENRICH_WITH_LLM → REVIEW_OR_PUBLISH → INDEX
```

각 단계 의미:

- **DISCOVER**: 후보 URL이나 외부 ID를 출처에서 발견
- **FETCH**: 피드 항목, API 레코드, 페이지를 가져옴
- **EXTRACT**: 원본에서 제목/저자/canonical URL/발행일/본문 텍스트 추출
- **NORMALIZE**: Sigak 내부 article 모양으로 변환
- **ENRICH_WITH_LLM**: FastAPI에 보내서 summary/whyItMatters/topics 후보 받기
- **REVIEW_OR_PUBLISH**: 즉시 공개할지, 검토 상태로 둘지 결정 (신뢰 출처는 자동 공개)
- **INDEX**: 키워드/벡터/그래프 인덱스에 등록 (1차 구현에서는 선택사항)

### 초기 출처(Source) 후보

- **RSS/Atom**: AI/개발자/엔지니어링/보안/인프라 분야의 공식 블로그
- **arXiv API**: `cs.AI`, `cs.LG`, `cs.CL` 같은 연구 카테고리
- **수동/뉴스레터 임포트**: 깨끗한 피드가 없는 큐레이션 링크

### 의도적으로 제외한 곳

- **Hacker News**: 초기 자동 수집기에서 제외. 이유 — **원본 출처가 아니라 집계자(aggregator)** 이기 때문에 출처 표기(attribution)가 흐려짐. 나중에 발견(discovery)이나 랭킹 신호로는 재검토 가능.

### 핵심 원칙

- **소량의 고품질 > 대량의 저품질**
- 출처 표기는 항상 보이게
- AI가 만든 텍스트는 **출처 진실(source truth)이 아니라 enrichment**로 취급
- 원본 컨텍스트를 충분히 보존해서 나중에 다시 요약/관계 추출 가능하게

---

## 10. 검색 전략

검색은 **단순하게 시작하지만 응답 모양은 미래에도 그대로** 가는 것이 원칙입니다.

| 단계 | 구현 |
|---|---|
| MVP 시작 | 키워드 매칭 (in-memory mock 데이터 필터링) |
| 후속 단계 | DB에 저장된 기사 데이터를 Elasticsearch 키워드 인덱스로 동기화 |
| 그 다음 | 임베딩 기반 시맨틱 검색 (Qdrant) |
| 최종 | 개념·관계를 활용한 그래프 인지 검색 |

응답 형식이 안정적이기 때문에 **프론트엔드는 한 번 만들면 재작성할 일이 거의 없음**.

---

## 11. 프론트엔드 디자인 시스템

프론트엔드는 **Bloomberg / Financial Times 스타일**의 전문적·미니멀한 저널리즘 톤입니다.

### 색상 시스템

| 역할 | 값 | 용도 |
|---|---|---|
| 베이스 | `#FFFFFF` | 페이지 배경 |
| 텍스트 | `#111111` | 제목, 주요 텍스트 |
| 서브 텍스트 | `#374151` | 본문 |
| 메타 텍스트 | `#6B7280`, `#9CA3AF` | 출처, 날짜, 라벨 |
| 보더 | `#E5E7EB`, `#F3F4F6` | 구분선 |
| 악센트 | `#4F46E5` (인디고) | 링크, 카테고리 태그 |
| 악센트 배경 | `#EEF2FF` | 카테고리 태그 배경 |
| 악센트 서피스 | `#F8F7FF` | Why It Matters 박스 배경 |

### 타이포그래피

- **Georgia (serif)**: 기사 제목과 본문 — 신뢰감과 읽기 편안함
- **-apple-system / sans-serif**: UI 라벨/메타/태그 — 현대적이고 기능적
- **인디고 악센트** `#4F46E5`: 링크, 태그, 강조

### 화면 구성

- **NavBar**: 로고 `SIGAK` + `AI · Security · Engineering` 힌트, 디테일에서는 `← Back to News`로 교체
- **HomePage**: 검색바 → Today's Important News → Popular News (importanceScore 상위 3개)
- **ArticleDetailPage**: 카테고리 + 이벤트 태그 → 제목(24px Georgia) → 출처/날짜 → SUMMARY → WHY IT MATTERS(왼쪽 인디고 보더 박스) → TOPICS 칩 → RELATED ARTICLES

### React Router

```
/              → HomePage
/articles/:id  → ArticleDetailPage
```

- 관련 기사는 `Promise.all`로 병렬 패칭
- API 호출은 `src/api/`에 분리, Zod로 응답 검증
- 빈 상태 / 로딩 / 실패 상태도 모두 명시적으로 처리 (실패 시 Retry, Back to home 등)

---

## 12. 로드맵 (Phase별 상태)

> ✅ = 완료, ⬜ = 미완료

### Phase 0: 프로젝트 가이드 (완료 ✅)
- 가이드 문서 작성, 초기 아키텍처 ADR, MVP 범위/Non-goal 정의

### Phase 1: 최소 저장소 구조 (완료 ✅)
- `backend/`, `frontend/`, `ai/`, `infra/` 추가
- README, `.env.example`, `.gitignore`, Docker Compose placeholder

### Phase 2: 백엔드 MVP 기반 (완료 ✅)
- Kotlin Spring Boot, mock 기사 목록/상세 API, 큐레이션 시드 데이터
- 키워드 검색, OpenAPI/Swagger UI

### Phase 3: 검색 MVP (대부분 완료, 일부 ⬜)
- ✅ 키워드 검색 동작 안정화
- ⬜ Elasticsearch 기반 검색 (실제 인덱싱이 필요해질 때)

### Phase 4: 프론트엔드 MVP 기반 (완료 ✅)
- React + TS + Vite, React Router, Axios+Zod, 홈/상세 화면, 로딩/빈/에러 상태 모두 처리

### Phase 5: 수집 + LLM enrichment 기반 (완료 ✅)
- 출처 레지스트리와 RSS/Atom·arXiv 수집 경계
- DISCOVER→FETCH→EXTRACT→NORMALIZE→ENRICH_WITH_LLM→REVIEW_OR_PUBLISH→INDEX 흐름 정의
- 수집 원본과 AI enrichment 결과를 분리하는 모델 방향
- FastAPI mock enrichment와 Spring Boot 내부 enrichment contract
- RSS content extraction, blank content fallback, publishedAt ISO-8601 정규화

### Phase 6: 영속화 MVP (미완 ⬜)
- RDB 도입, JPA 엔티티/리포지토리, 원본 텍스트와 enrichment 결과 보존
- duplicate detection과 article status 저장
- DB를 source of truth로 두고 이후 검색 인덱싱의 기반 마련

### Phase 7: 수집 파이프라인 강화 (미완 ⬜)
- 몇 시간 단위 scheduled collection
- source별 fetch 실행, 신규 기사만 저장, 실패 재시도와 관측성
- 필요하면 이후 별도 단계에서 Elasticsearch indexing/search 전환

### Phase 8: 제한된 Graph RAG 인사이트 (일부 완료)
- ✅ 그래프 친화적 메타데이터, ✅ 관련 기사 ID
- ⬜ 명시적 컨셉/관계, 관계 인지 인사이트, 제한된 그래프 검색
- Qdrant는 임베딩이 필요해질 때 도입 후보

### Phase 9: 로컬 개발 + 마무리 (대부분 완료)
- ✅ 로컬 환경 예제값, README 정합성
- ⬜ Docker Compose 확장, 서비스별 `.env.example`

### 더 나중에
- Elasticsearch / Qdrant / 하이브리드 검색
- Obsidian 스타일 그래프 탐색기
- 다중 기사 RAG, 개인화 추천, 사용자 계정/저장 기사

---

## 13. 주요 의사결정 (ADR 요약)

### ADR 0001 — 초기 아키텍처

**결정**: Spring Boot 메인 백엔드 + FastAPI는 AI/RAG 전용 + React+TS+Vite 프론트엔드 + Docker Compose 로컬 + Next.js는 사용 안 함.

**이유**:
- 메인 백엔드의 API 경계가 한 곳으로 명확
- AI 작업은 Python 생태계에 두는 것이 편함
- Elasticsearch/Qdrant는 필요해질 때 도입

**검토했다 안 한 대안**:
- 단일 FastAPI 백엔드: 간단하지만 Spring Boot 포트폴리오 목표와 안 맞음
- Next.js 풀스택: 빠르지만 백엔드 분리 목표와 충돌
- Elasticsearch/Qdrant 즉시 도입: 너무 이른 인프라

### ADR 0002 — 제품 범위와 Graph RAG 전략

**결정**: 1.0.0은 **"중요한 기술 변화를 맥락과 관계로 설명"**. Graph RAG는 **제한된 수준** (관계형 메타데이터, 관련 기사, 관계 기반 인사이트, 제한된 graph-backed 검색).

**미루는 것**:
- Obsidian 스타일 전체 그래프 탐색기 (UI 비용 큼, 데이터 품질 부족 시 신뢰 떨어짐)
- 광범위 자동 크롤링
- 개인화 추천, 저장 기사, 사용자 계정

### ADR 0003 — 수집과 enrichment 파이프라인

**결정**: Phase 5는 **명시적 출처 레지스트리 + 커넥터 방식**. 일반 웹 크롤러는 만들지 않음.

**이유**:
- 출처 품질 관리가 자동화 확장보다 먼저
- LLM 결과를 **enrichment 레이어**로 취급, 원본은 보존
- 향후 시맨틱 검색/Graph RAG가 원본을 다시 안 긁어도 되도록 분리 저장

**검토했다 안 한 대안**:
- FastAPI AI 요약 먼저: 진짜 원본 없이 가치 약함
- 일반 오픈웹 크롤러: 범위는 넓지만 위험과 attribution 문제
- Hacker News 초기 출처 사용: API는 쉽지만 집계자라 출처 흐려짐

---

## 14. 개발 워크플로우 (빌드/실행)

### 백엔드

```bash
cd backend
./gradlew bootRun         # http://localhost:8080
./gradlew test             # 전체 테스트
./gradlew check            # lint/format 포함
```

확인할 URL:
- API 예: `http://localhost:8080/api/articles`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`

### 프론트엔드

```bash
cd frontend
npm install
npm run dev               # http://localhost:5173
npm test                  # Vitest
npm run lint
npm run build
```

### 풀 스택 (Docker Compose)

```bash
cd infra
docker-compose up
```

### 새 기능 추가 흐름 (예: Article 필드 추가)

1. 도메인 엔티티와 JPA 매핑 업데이트
2. DTO와 API 응답에 추가
3. `docs/API_SPEC.md` 업데이트
4. Swagger UI로 검증
5. 프론트엔드 Zod 스키마와 컴포넌트 업데이트

### 새 백엔드 엔드포인트

1. 요청/응답 DTO 작성
2. 서비스 레이어에 비즈니스 로직 추가
3. 컨트롤러 메서드 추가
4. JUnit 테스트 (서비스 레벨 테스트 우선)
5. Swagger UI에서 검증

### 새 프론트엔드 페이지/컴포넌트

1. `src/components/` 또는 `src/pages/` 추가
2. API 호출용 Zod 스키마 작성
3. `src/api/`의 클라이언트 사용
4. 응답 검증 후 렌더
5. `npm test`로 검증

### Git 커밋 메시지 스타일

```
feat: 새 기능
fix: 버그 수정
docs: 문서 업데이트
refactor: 동작 변경 없는 구조 개선
chore: 프로젝트 셋업/유지보수
test: 테스트 추가/수정
```

> 절대 커밋 금지: API 키, 토큰, `.env`, 빌드 산출물, 개인정보

---

## 15. 핵심 정리 (15초 컷)

읽을 시간이 없는 사람을 위한 가장 압축된 요약입니다.

1. **Sigak = AI/개발/CS 분야의 중요한 기술 뉴스만 골라, 요약 + 왜 중요한지 + 관련 개념을 보여주는 인사이트 플랫폼**
2. **목표 = 2026년 6월 말까지 인턴 지원용 포트폴리오 MVP**
3. **아키텍처 = Spring Boot(Kotlin, 메인) + FastAPI(Python, AI 전용) + React(TS+Vite, 프론트), Docker Compose로 로컬 통합**
4. **데이터 = 수동 큐레이션 mock → 선별 RSS/API → 원본 보존 + AI enrichment 분리**
5. **검색 = 키워드 → (이후) 시맨틱 → (이후) 그래프 인지. 응답 모양은 처음부터 미래에도 그대로**
6. **Graph RAG = 1.0.0은 제한된 수준만, 전체 그래프 UI는 1.0.0 이후로**
7. **importanceScore = 0~100, 사용자에게 숫자로 노출 안 함, 내부 정렬·큐레이션 신호로만**
8. **출처 정책 = 양보다 질, 개인 블로그/튜토리얼/홍보성 글/Hacker News 등은 초기 제외**
9. **프론트엔드 톤 = Bloomberg/FT 스타일, Georgia serif, 인디고 악센트, importance 숫자는 숨김**
10. **현재 완료된 단계 = Phase 0~5 기반 완료, 다음 큰 작업은 persistence foundation과 scheduled collection**

---

## 16. 부록 — 자주 묻는 질문 (FAQ)

**Q. 왜 처음부터 DB를 안 붙이고 mock 데이터를 쓰나요?**
A. 제품의 핵심 흐름이 검증되기 전에 DB를 붙이면 변경 비용이 큽니다. mock으로 API 계약·검색·UI 연동을 먼저 검증한 뒤, mock이 한계를 보일 때 DB를 붙입니다.

**Q. 왜 Elasticsearch와 Qdrant를 미루나요?**
A. 둘 다 중요하지만, 먼저 DB에 안정적인 article ID, 원본 텍스트, enrichment 결과가 저장되어야 합니다. DB를 source of truth로 둔 뒤 Elasticsearch는 검색용 인덱스로 붙이는 편이 구조가 더 단단합니다.

**Q. 왜 importanceScore를 화면에 안 보여주나요?**
A. 점수는 수동 큐레이션이라 정확한 숫자처럼 보이는 것이 오히려 신뢰를 해칠 수 있습니다. `whyItMatters`로 이유를 설명하는 편이 더 정직합니다. 점수는 내부 랭킹용으로만 사용.

**Q. 프론트엔드에 Zod가 꼭 필요한가요?**
A. TypeScript 타입은 컴파일 타임 보장이고, 런타임에 실제 응답이 다를 수 있습니다. Zod는 백엔드 응답이 문서와 어긋나면 컴포넌트에 들어가기 전에 즉시 잡아 줍니다.

**Q. Hacker News는 왜 빼나요?**
A. Hacker News는 **원본 출처가 아니라 집계자**입니다. 출처 표기가 흐려지고, 다른 곳의 글이 많기 때문에 1차 자동 수집기에서는 제외합니다. 나중에 발견·랭킹 신호로는 재검토 가능합니다.

**Q. 풀 그래프 탐색 UI는 언제 나오나요?**
A. 1.0.0 이후로 연기. 이유는 (1) 프론트엔드 복잡도, (2) 좋은 그래프 데이터가 충분히 쌓여야 가치 있음, (3) 빈약한 그래프는 오히려 신뢰를 해침.

---

> **이 문서는 `/docs` 폴더의 모든 문서를 한글로 풀어 정리한 것입니다.**
> 더 자세한 원본은 `PROJECT_CONTEXT.md`, `PRODUCT_PLAN.md`, `API_SPEC.md`, `SOURCE_POLICY.md`, `ROADMAP.md`, `decisions/0001~0003`, `blog/2026-05-05-dev-log.md`, `superpowers/specs/*.md`를 참고하세요.
