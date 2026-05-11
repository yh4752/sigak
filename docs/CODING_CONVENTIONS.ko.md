# 코딩 컨벤션

[English](CODING_CONVENTIONS.md) | [한국어](CODING_CONVENTIONS.ko.md)

마지막 업데이트: 2026-05-11

Sigak은 대형 엔지니어링 조직의 공개 스타일 가이드를 참고하되, 프로젝트 규모에 맞게 작고 명확한 규칙만 적용합니다. 목표는 모든 규칙을 그대로 따라 하는 것이 아니라, 일관성과 가독성을 높이는 것입니다.

## 우선순위

규칙이 충돌하는 것처럼 보이면 다음 순서를 따릅니다.

1. 같은 모듈의 기존 스타일
2. 이 문서의 규칙
3. formatter와 linter 결과
4. 공개 언어 스타일 가이드
5. 개인 취향

## 공통 원칙

- 영리한 코드보다 읽기 쉬운 코드를 우선합니다.
- 구현 세부사항보다 도메인 의미를 드러내는 이름을 사용합니다.
- 함수는 하나의 책임에 집중합니다.
- controller와 router는 얇게 유지합니다.
- 비즈니스 규칙은 service에 둡니다.
- 도메인이나 언어에서 널리 쓰이는 약어가 아니면 약어를 피합니다.
- 명확한 이유 없이 새 아키텍처 패턴을 도입하지 않습니다.

## 주석 정책

Sigak에서 주석은 중요합니다. 다만 주석은 코드가 이미 말하는 내용을 반복하지 않고, 의도, 비즈니스 규칙, 트레이드오프, 비직관적 제약을 설명해야 합니다.

### 언어

- 코드 주석은 한글로 작성합니다.
- API 필드명, 클래스명, 함수명, 기술 식별자는 영어로 유지합니다.
- 외부 에러 메시지, API 필드, 프로토콜 용어, 번역하면 의미가 흐려지는 라이브러리 개념을 인용할 때만 주석 안에서 영어를 사용합니다.

### 좋은 주석

좋은 주석은 코드가 왜 존재하는지, 왜 그런 선택을 했는지 설명합니다.

```kotlin
// 뉴스 원문이 비어 있어도 수집 상태를 추적해야 하므로 Article은 먼저 저장한다.
```

```python
# 외부 LLM 없이도 로컬 개발이 가능하도록 기본 응답은 mock 서비스에서 생성한다.
```

```ts
// API 응답 형태가 바뀌면 화면 전체가 깨질 수 있어 client 경계에서 먼저 검증한다.
```

### 피해야 할 주석

코드를 그대로 설명하는 주석은 피합니다.

```kotlin
// id로 기사를 찾는다.
val article = articleRepository.findById(id)
```

명확한 함수명이나 파일 구조로 표현할 수 있는 내용을 구역 주석으로 반복하지 않습니다.

```ts
// 함수 시작
function loadArticles() {
  // ...
}
```

### 주석이 필요한 경우

다음 코드에는 한글 주석을 추가합니다.

- 비직관적인 비즈니스 규칙
- 임시 MVP 결정이나 의도적인 제한
- 정확성, 속도, 단순함 사이의 트레이드오프
- 라이브러리, 프레임워크, 인프라 동작 우회
- 미래 유지보수자가 헷갈릴 수 있는 AI/RAG 동작
- 정보가 손실되거나 의미가 바뀌는 데이터 매핑

임시 제한을 설명하는 주석에는 이유와 재검토 조건을 함께 적습니다.

```python
# MVP 단계에서는 외부 API 비용을 피하기 위해 mock 요약을 사용한다.
# 실제 LLM 연동이 추가되면 이 서비스는 provider interface 뒤로 이동한다.
```

## 백엔드 컨벤션

백엔드는 Kotlin과 Spring Boot를 사용합니다.

### 네이밍

- Controller: `ArticleController`
- Service: `ArticleService`
- Repository: `ArticleRepository`
- Entity: `Article`, `Source`처럼 단수 명사
- DTO: `ArticleSearchRequest`, `ArticleSummaryResponse`
- Config class: `CorsConfig`, `OpenApiConfig`
- Test class: `ArticleServiceTest`, `ArticleControllerTest`

### 구조

- controller는 얇게 유지하고 비즈니스 로직은 service에 위임합니다.
- JPA entity를 API 응답으로 직접 노출하지 않습니다.
- request/response DTO를 명시적으로 둡니다.
- persistence 관련 결정은 controller 밖에 둡니다.
- constructor injection을 선호합니다.

## AI 서버 컨벤션

AI 서버는 FastAPI와 Python을 사용합니다.

### 네이밍

- 파일과 모듈: `snake_case.py`
- 함수와 변수: `snake_case`
- 클래스와 Pydantic schema: `PascalCase`
- Router: `enrichment.py`, `article_summary.py`
- Service: `mock_enrichment_service.py`, `summary_service.py`

### 구조

- FastAPI router는 얇게 유지합니다.
- AI/RAG 동작은 service에 둡니다.
- request/response model은 `schemas`에 둡니다.
- 외부 provider client가 추가되면 `clients`에 둡니다.
- 로컬 개발을 위해 mock AI 동작은 유지합니다.

## 프론트엔드 컨벤션

프론트엔드는 React, TypeScript, Vite, Axios, Zod를 사용합니다.

### 네이밍

- Component: `ArticleListItem`처럼 `PascalCase`
- Component file: `ArticleListItem.tsx`
- Hook: `useArticleSearch`
- API module: `articles.ts`, `httpClient.ts`
- 변수와 함수: `camelCase`
- 상수: `UPPER_SNAKE_CASE`
- Type과 schema: `ArticleSummaryResponse`, `ArticleSchema`
- Test file: `ArticleListItem.test.tsx`, `articles.test.ts`

### 구조

- API 호출은 API client module 안에 둡니다.
- backend 응답은 API client 경계에서 Zod로 검증합니다.
- component는 렌더링과 사용자 상호작용에 집중하도록 작게 유지합니다.
- workflow가 요구하지 않으면 복잡한 상태 관리 라이브러리를 도입하지 않습니다.

## 테스트 네이밍

- 테스트 이름은 관찰 가능한 동작을 설명합니다.
- 구현 세부사항보다 behavior 중심 이름을 선호합니다.
- backend와 AI server 테스트는 가독성이 좋아진다면 자연어 스타일을 사용할 수 있습니다.

## 리뷰 체크리스트

코드 변경을 마치기 전에 다음을 확인합니다.

- 이름이 주변 파일과 일관적인가?
- 비직관적 의도와 트레이드오프에 한글 주석이 있는가?
- 주석이 what이 아니라 why를 설명하는가?
- controller, router, service, DTO, schema 경계가 분리되어 있는가?
- frontend API 응답은 client 경계에서 검증되는가?
- 환경별 값이 source code 밖에 있는가?
- 가능한 경우 관련 formatter, linter, test 명령을 실행했는가?
