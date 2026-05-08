# Sigak 제품 문서

[English](PRODUCT.md) | [한국어](PRODUCT.ko.md)

마지막 업데이트: 2026-05-07

## 1. 제품 정의
Sigak은 AI, 소프트웨어 개발, 컴퓨터 과학에 관심 있는 사람을 위한 AI 기반 기술 뉴스 인사이트 플랫폼입니다.

Sigak은 인터넷의 모든 기술 글을 수집하려는 제품이 아닙니다. 중요한 기술 변화에 집중하고, 그 변화가 왜 중요한지 설명합니다.

## 2. 제품 목표
1.0.0의 목표는 다음을 보여주는 포트폴리오급 MVP를 만드는 것입니다.

- 중요한 기술 뉴스 큐레이션
- 명확한 백엔드 아키텍처
- 검색과 article 탐색
- AI-assisted summarization과 insight generation
- Graph RAG 준비가 가능한 data modeling
- 단순하고 배포 가능한 로컬 개발 환경

이 프로젝트는 2026년 6월 말 인턴 지원 시점까지 실용적인 엔지니어링 판단을 보여줄 수 있을 만큼 단단해야 합니다.

## 3. MVP 우선순위

1. 고급 아키텍처를 추가하기 전에 동작하는 제품을 먼저 만든다.
2. Spring Boot를 메인 백엔드와 API 경계로 유지한다.
3. FastAPI는 AI/RAG 관련 기능에만 사용한다.
4. 프론트엔드는 React, TypeScript, Vite를 사용한다.
5. Docker Compose 기반 로컬 개발은 단순하게 유지한다.
6. 향후 enrichment, semantic search, Graph RAG를 위해 원문 article text와 metadata를 보존한다.
7. 프로젝트가 진화할 때 중요한 아키텍처 결정은 문서로 남긴다.

## 4. 대상 사용자
주 사용자는 AI, 소프트웨어 개발, 컴퓨터 과학 분야의 중요한 변화를 모든 출처를 직접 읽지 않고 따라가고 싶은 사람입니다.

예시:
- AI와 software engineering trend를 따라가고 싶은 개발자
- AI, CS, 개발에 관심 있는 학생
- 중요한 기술 사건을 맥락과 함께 이해하고 싶은 사람
- 적지만 의미 있는 기술 업데이트를 원하는 독자

## 5. 핵심 가치
Sigak의 핵심 가치는 단순 요약이 아닙니다. 제품은 다음을 해야 합니다.

1. 중요한 기술 뉴스를 선별한다.
2. 각 항목이 왜 중요한지 설명한다.
3. 각 항목을 관련 기술 개념, 조직, 사건과 연결한다.
4. Graph RAG 기반 search와 insight generation을 위한 데이터 구조를 준비한다.

가장 강한 제품 방향은 다음 문장입니다.

> 중요한 기술 변화를, 맥락과 관계와 함께 설명한다.

## 6. MVP 범위

초기 MVP 범위:

- 뉴스 article 목록
- 뉴스 article 상세
- 키워드 검색
- 선택한 article의 AI 요약
- 중요도와 why-it-matters insight
- 단순한 frontend UI
- Docker Compose 로컬 실행 환경
- 명확한 README
- Graph RAG 준비가 가능한 article metadata
- 제한된 relationship-based insight 또는 graph-backed retrieval

MVP가 안정되기 전까지 다음 범위는 의도적으로 미룹니다.

- 전체 Obsidian-style graph explorer
- 광범위한 multi-source automated collection
- 고급 hybrid search
- 여러 article을 대상으로 하는 broad RAG
- personalized recommendation
- user account
- saved article
- advanced dashboard

## 7. 서비스 책임

프론트엔드는 주로 Spring Boot 백엔드와 통신합니다. Spring Boot는 user-facing API, business rule, persistence, orchestration을 담당합니다. FastAPI는 summarization, enrichment, embedding, RAG workflow 같은 AI 전용 작업을 담당합니다.

Article data는 raw source text와 processed enrichment를 분리해 보존해야 합니다. 이렇게 해야 semantic search, graph relationship, 개선된 LLM prompt를 위해 기존 content를 다시 scraping하지 않고 재처리할 수 있습니다.

Selected-source collection은 MVP 경로에 포함되지만, broad open-web crawling은 포함하지 않습니다. Collection pipeline은 명시적인 source registry에서 시작해 article data를 normalize하고, AI enrichment를 위해 FastAPI를 호출하는 방향입니다.

## 8. 콘텐츠 범위
Sigak은 AI, 개발, 컴퓨터 과학에 집중합니다.

### 포함
- 중요한 AI, 개발, CS 뉴스
- 회사, 연구소, 표준 단체, 주요 오픈소스 프로젝트의 공식 발표
- 심각한 보안 이슈
- 영향력 있는 연구 논문
- 도구, 플랫폼, 언어, 프레임워크의 주요 release

### MVP에서 제외
- 일반 개인 기술 블로그 글
- 단순 tutorial
- 홍보성 content
- 영향이 작은 library update
- rumor 또는 검증되지 않은 community discussion

개인 기술 블로그는 source quality와 selection rule이 명확해진 뒤에만 다시 검토합니다.

## 9. Event Type
각 article 또는 item은 하나의 primary event type을 가져야 합니다.

초기 event type:

- `NEWS`: 일반 기술 뉴스
- `OFFICIAL_ANNOUNCEMENT`: 회사, 연구소, 조직, 프로젝트의 공식 발표
- `RESEARCH`: 중요한 논문 또는 연구 결과
- `SECURITY`: 심각한 보안 이슈, 취약점, exploit, security incident
- `RELEASE`: 주요 제품, 도구, 프레임워크, 언어, 플랫폼 release

나중에 추가할 수 있는 event type:

- `STANDARD`: standard, specification, PEP, JEP, RFC 또는 유사한 변화

MVP에서는 별도 type 필요성이 명확해질 때까지 standard 관련 content를 `OFFICIAL_ANNOUNCEMENT` 또는 `RELEASE`로 분류할 수 있습니다.

## 10. 기술 카테고리
카테고리는 article의 주 기술 분야를 설명합니다. 단순 탐색에 충분할 만큼 넓게 유지하고, 세부 개념은 topic 또는 graph node로 다룹니다.

각 article은 하나의 `primaryCategory`와 여러 `topics`를 가져야 합니다.

초기 category:

- `AI`
- `SECURITY`
- `SOFTWARE_ENGINEERING`
- `BACKEND`
- `FRONTEND`
- `DATA`
- `INFRA_CLOUD`
- `DEVTOOLS`
- `CS_RESEARCH`

### Category Note
- `AI`: LLM, agent, RAG, ML, AI product, AI platform
- `SECURITY`: CVE, vulnerability, supply chain security, AI security
- `SOFTWARE_ENGINEERING`: architecture, testing, quality, maintainability, engineering practice
- `BACKEND`: API, server, distributed system, messaging, JVM/Spring, backend framework
- `FRONTEND`: web platform, browser, UI framework, frontend tooling
- `DATA`: database, search, analytics, storage, data engineering
- `INFRA_CLOUD`: cloud, DevOps, SRE, Kubernetes, observability, deployment
- `DEVTOOLS`: IDE, compiler, package manager, build tool, developer workflow
- `CS_RESEARCH`: algorithm, programming language, operating system, systems research, HCI, theory

## 11. Category와 Topic
Sigak은 넓은 category와 세부 topic을 분리해야 합니다.

예시:

```txt
primaryCategory: AI
eventType: RESEARCH
topics: ["RAG", "Graph RAG", "knowledge graph", "retrieval"]
```

예시:

```txt
primaryCategory: SECURITY
eventType: SECURITY
topics: ["MCP", "remote code execution", "AI supply chain"]
```

이 구조는 UI를 단순하게 유지하면서, 향후 Graph RAG 기능이 더 풍부한 concept와 relationship을 사용할 수 있게 합니다.

## 12. 데이터 전략
Sigak은 혼합 data strategy를 사용합니다.

### MVP 시작
수동으로 큐레이션한 mock data로 제품 구조를 먼저 증명합니다.
- article list
- article detail
- importance
- summary
- why it matters
- primary category
- topics
- related item

### 이후 MVP
공식 기술 출처와 선별 연구 출처를 포함해 selected source에 대한 RSS/API 기반 collection을 추가합니다.

### 중요한 원칙
Semantic search나 Graph RAG가 추가될 때 다시 수집해야만 하는 구조를 피해야 합니다.

원본과 처리된 article data를 분리 저장해, 기존 content를 나중에 다시 처리할 수 있게 합니다.

```txt
DISCOVER -> FETCH -> EXTRACT -> NORMALIZE -> ENRICH_WITH_LLM -> REVIEW_OR_PUBLISH -> INDEX
```

Graph RAG는 같은 content를 다시 scraping하는 방식이 아니라, 저장된 article text와 metadata를 재처리하는 방식으로 추가합니다.

Phase 5는 user-facing AI generation보다 먼저 collection/enrichment foundation을 세우는 단계입니다. 첫 구현은 broad open-web crawling 대신 source registry와 connector-style collector를 사용해야 합니다.

초기 connector 후보:
- 공식 AI, developer, engineering blog를 위한 RSS/Atom connector
- `cs.AI`, `cs.LG`, `cs.CL` 같은 연구 출처를 위한 arXiv API connector
- 안정적인 feed가 없는 curated link를 위한 manual 또는 newsletter import connector

Hacker News는 초기 collector에서 제외합니다. 나중에 discovery 또는 ranking signal로 재검토할 수 있지만, 원문 출처로 취급하면 안 됩니다.

## 13. Graph RAG 방향
Graph RAG는 원하는 1.0.0 방향에 포함되지만, 전체 Obsidian-style graph exploration은 뒤로 미룹니다.

### 1.0.0 집중 범위
- relationship-aware article metadata
- related concept
- related article
- relationship-based insight generation
- 제한된 graph-backed Q&A 또는 explanation
- article detail의 작은 related graph 선택지

### 미루는 범위
전체 Obsidian-style graph explorer는 이후 버전으로 미룹니다.

이유:
- frontend complexity가 높다.
- 유용하려면 충분한 고품질 graph data가 필요하다.
- 부정확한 graph relationship은 신뢰를 낮출 수 있다.
- 제품 가치는 먼저 시각적 새로움이 아니라 insight quality에서 나와야 한다.

## 14. Home 경험
Home 화면은 단순하고 search-centered 해야 합니다.

권장 구조:
- 중앙 search bar
- 오늘의 중요한 뉴스
- 인기 뉴스
- 가벼운 category 또는 topic filter

주 사용자 흐름:

1. 사용자가 Sigak을 연다.
2. 오늘의 중요한 기술 뉴스를 본다.
3. article을 클릭한다.
4. summary, importance, why it matters, topics, related item을 읽는다.
5. 특정 topic이 있으면 검색한다.

우선순위:

1. 오늘의 중요한 뉴스
2. article detail insight
3. search

## 15. Article 상세 경험
Article detail은 Sigak의 핵심 가치가 가장 잘 보여야 하는 곳입니다.

권장 필드:
- title
- source
- original URL
- published date
- event type
- primary category
- short summary
- why it matters
- topics
- related articles
- related concepts
- 선택 사항: 작은 related graph

MVP article detail page에는 원시 `importanceScore`를 표시하지 않습니다. 이 값은 curated list를 위한 내부 ranking signal입니다. 상세 페이지에서는 정확한 숫자보다 `whyItMatters`와, 필요하다면 나중의 정성 label로 중요도를 이해시키는 편이 좋습니다.

## 16. Insight 톤
Sigak은 기술 뉴스를 이해하기 쉽게 설명하되 얕지 않아야 합니다.

톤 원칙:
- 깊은 배경지식이 없는 독자에게도 충분히 친절하다.
- 개발자와 CS 관심 독자에게도 의미 있는 기술 맥락을 제공한다.
- 무엇이 바뀌었는지 설명한다.
- 왜 중요한지 설명한다.
- 더 큰 기술 흐름과 연결한다.
- hype와 근거 없는 예측을 피한다.

제품의 기본 톤은 취업 면접 준비 콘텐츠가 아닙니다.

## 17. Search 방향
Search는 단순하게 시작하되, semantic/graph-based 확장을 염두에 둡니다.

### MVP 시작
- title, summary, topics, primary category 대상 keyword search

### 이후
- embedding 기반 semantic search
- concept와 relationship을 사용하는 graph-aware retrieval
- 최근 기술 흐름에 대한 natural language question

백엔드 search 구현이 발전해도 search UI는 안정적으로 유지해야 합니다.

## 18. 초기 Article Model 방향
최종 schema는 발전할 수 있지만, 초기 mock data도 미래 data model과 닮아 있어야 합니다.

현재 MVP API 계약은 `API_SPEC.md`에 문서화되어 있습니다.

후보 article field:

```txt
id
title
url
source
publishedAt
eventType
primaryCategory
topics
summary
whyItMatters
importanceScore
contentText
processingStatus
relatedArticleIds
relatedConcepts
```

Collection과 enrichment는 source data와 AI output을 분리해야 합니다.

```txt
CollectedArticle
- sourceName
- sourceType
- externalId
- url
- canonicalUrl
- title
- publishedAt
- authorNames
- rawContent
- extractedText
- collectedAt
- extractionStatus

ArticleEnrichment
- summary
- whyItMatters
- suggestedTopics
- suggestedPrimaryCategory
- suggestedImportanceScore
- modelName
- promptVersion
- enrichedAt
- enrichmentStatus
```

향후 graph field 후보:

```txt
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

## 19. Importance Score
`importanceScore`는 article의 큐레이션 중요도를 나타내는 0-100 정수입니다.

MVP seed data에서는 `importanceScore`를 수동으로 부여합니다. 이후 AI enrichment가 점수를 제안할 수 있지만, 최종 저장 점수는 수동 검토 또는 규칙으로 조정될 수 있습니다.

현재 이 점수는 ranking과 curation signal이며, user-facing detail field로 필수 노출하지 않습니다. MVP가 curated mock data를 사용하는 동안 점수를 지나치게 정밀한 숫자처럼 보이게 하지 않기 위해서입니다.

| 범위 | 수준 | 의미 |
| --- | --- | --- |
| `90-100` | Critical | 중대한 보안 이슈, AI/CS의 큰 변화, 기술 생태계에 영향을 줄 수 있는 매우 중요한 공식 발표입니다. |
| `75-89` | High | 많은 개발자나 팀이 알아야 하는 중요한 변화입니다. |
| `50-74` | Medium | 특정 기술 독자층에 의미 있는 변화입니다. |
| `0-49` | Low | 기록할 수는 있지만 보통 main important feed에 나타나지 않아야 하는 항목입니다. |

Sigak은 양보다 선별에 집중하므로 대부분의 MVP seed article은 `75+`가 적절합니다.

## 20. Source Selection 요약
Sigak은 광범위한 자동화보다 높은 source quality를 먼저 유지해야 합니다.

포함할 출처 예:
- 회사, 연구소, 표준 단체, 주요 오픈소스 프로젝트의 공식 발표
- 신뢰할 만한 AI, CS, 소프트웨어 개발 뉴스 출처
- security advisory, CVE record, incident report
- 영향력 있는 연구 논문 또는 연구 발표
- 주요 open source release note

제외할 출처 예:
- 일반 개인 블로그 글
- 단순 tutorial
- 홍보성 content
- 영향이 작은 patch release
- rumor 또는 검증되지 않은 community discussion

Seed data는 여러 event type과 category를 포함하고, `summary`, `whyItMatters`, `topics`, related item을 갖추며, 향후 재처리를 위한 충분한 source metadata를 보존해야 합니다.

전체 draft policy는 `SOURCE_POLICY.md`를 봅니다.

## 21. 결정된 계획
- Graph RAG는 1.0.0에서 제한된 relationship insight 또는 graph-backed retrieval 수준으로만 포함합니다.
- Full Obsidian-style graph exploration은 1.0.0 이후로 미룹니다.
- Article은 하나의 `primaryCategory`와 여러 `topics`를 사용합니다.
- 광범위한 automated collection을 만들기 전에 source selection policy를 먼저 작성해야 합니다.
- MVP data는 curated seed data에서 시작해 selected RSS/API collection으로 이동합니다.
- `importanceScore`는 MVP seed data에서 0-100 수동 큐레이션 점수를 사용하며, detail page 원시 숫자 노출보다 ranking에 사용합니다.
- 현재 article list/detail API 모양은 `API_SPEC.md`에 문서화되어 있습니다.
- Search result는 `GET /api/articles`와 같은 article response shape를 사용해야 합니다.

## 22. 열린 계획 영역
구현이 너무 커지기 전에 다음 영역은 추가 제품 결정이 필요합니다.

- Graph RAG enrichment pipeline boundary
- 정확한 1.0.0 acceptance criteria

## 23. 현재 결정 요약
- 대상 도메인: AI, 개발, 컴퓨터 과학
- 핵심 가치: 중요한 뉴스 선별 + 맥락과 관계
- Data strategy: curated data 먼저, RSS/API collection 이후
- Graph RAG: 1.0.0에서는 제한된 relationship-based insight, full graph explorer는 연기
- Home UX: centered search bar + 오늘의 중요 뉴스 + 인기 뉴스
- Insight tone: 입문자에게 친절하지만 기술적으로 의미 있게
- Event type: `NEWS`, `OFFICIAL_ANNOUNCEMENT`, `RESEARCH`, `SECURITY`, `RELEASE`
- Category: `AI`, `SECURITY`, `SOFTWARE_ENGINEERING`, `BACKEND`, `FRONTEND`, `DATA`, `INFRA_CLOUD`, `DEVTOOLS`, `CS_RESEARCH`
- Article classification: 하나의 `primaryCategory` + 여러 `topics`
- Article API: list/detail response shape는 `API_SPEC.md`에 문서화
