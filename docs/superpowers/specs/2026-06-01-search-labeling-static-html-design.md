# 검색 라벨링 정적 HTML 설계

## 1. 목표

Sigak은 keyword search, vector search, hybrid search를 비교할 수 있는 작은 검색 평가셋이 필요하다.
검색 품질을 숫자로 말하려면 먼저 사람이 판단한 query/article 관련도 label이 있어야 한다.

기존 `docs/search-evaluation/queries.md`는 가이드 문서로는 유용하지만, 실제 라벨 입력 도구로 쓰기에는 표가 길고 편집이 불편하다.
따라서 v0.1에서는 서버나 DB 저장 없이 사용할 수 있는 정적 HTML 라벨링 도구를 먼저 만든다.

이 설계의 목표는 다음과 같다.

- 사용자가 article 목록을 보면서 query별 관련도를 편하게 선택할 수 있게 한다.
- label 결과를 JSON으로 export해서 나중에 benchmark runner가 읽을 수 있게 한다.
- 구현은 가볍게 유지하되, article이 20-30개로 늘어나도 UI가 무너지지 않게 한다.
- Markdown 문서는 설명용으로 유지하고, 입력은 HTML에서 처리한다.

## 2. 설계 재검토 결과

결론부터 말하면 정적 HTML + JSON export 방향은 유지한다.
다만 첫 설계에서 보완해야 할 점이 있었다.

### 2.1 큰 matrix UI는 피한다

처음 설계에는 query row 안에 모든 article의 relevance selector를 넣는 방식이 들어 있었다.
article이 5개일 때는 가능하지만, 목표처럼 20-30개가 되면 한 화면에서 가로/세로로 너무 커진다.

따라서 구현은 큰 표보다 **query 중심 page/deck UI**로 한다.
한 번에 하나의 query만 집중해서 보고, 이전/다음 이동과 query 목록 jump로 넘기면서 article 관련도를 선택하는 방식이 더 낫다.

### 2.2 모든 article을 `not_relevant`로 찍게 하지 않는다

검색 평가에서 대부분의 article은 특정 query와 관련이 없다.
사용자가 30개 article 중 28개를 매번 `not_relevant`로 직접 선택해야 한다면 도구가 아니라 노동이 된다.

그래서 기본값은 `unlabeled`로 두고, `reviewed` 상태인 query에서는 **라벨이 없는 article을 benchmark 계산 시 관련 없음으로 취급**한다.
명시적인 `not_relevant`는 헷갈리는 negative example이나 메모가 필요한 경우에만 사용한다.

### 2.3 중간 저장이 필요하다

정적 HTML은 repository 파일에 직접 저장할 수 없다.
그래서 JSON download만 제공하면 브라우저를 닫거나 실수로 새로고침했을 때 작업을 잃기 쉽다.

이를 줄이기 위해 v1 UI는 `localStorage` 자동 저장을 제공한다.
최종 산출물은 여전히 JSON export지만, 작업 중에는 브라우저에 draft를 자동 저장한다.

### 2.4 article 확장 전략은 seed 증가가 아니라 catalog export다

현재 seed article 5개는 smoke test에는 충분하지만 benchmark에는 부족하다.
그렇다고 `V2__seed_article_data.sql`에 가짜 article을 많이 추가하는 것은 좋지 않다.

검색 평가용 article은 controlled collection으로 모은 뒤, 특정 시점의 PostgreSQL 데이터를 catalog JSON으로 export해 고정하는 방식이 낫다.
seed data는 재현 가능한 최소 baseline으로 유지하고, benchmark용 catalog는 별도 artifact로 관리한다.

## 3. 결정

첫 라벨링 도구는 다음 파일로 만든다.

```text
docs/search-evaluation/labeling.html
```

이 파일은 backend, frontend dev server, database 없이 브라우저에서 직접 열 수 있어야 한다.
외부 CDN이나 build step 없이 동작하는 단일 HTML 파일을 우선한다.

라벨 결과는 JSON으로 다운로드한다.
브라우저 작업 중에는 `localStorage`에 draft를 자동 저장한다.
나중에 benchmark runner는 export된 JSON을 입력으로 사용한다.

## 4. 포함 범위

포함한다.

- 한국어 UI
- seed article catalog 내장
- catalog JSON import
- label JSON import/export
- query 추가/삭제
- query별 검색 의도 입력
- query별 memo 입력
- query 상태 선택
  - `needs_user_label`
  - `needs_review`
  - `reviewed`
- article별 relevance 선택
  - `strong`
  - `acceptable`
  - `not_relevant`
  - `unlabeled`
- 브라우저 `localStorage` draft 자동 저장
- export 전 validation warning
- JSON preview

포함하지 않는다.

- DB 저장
- Spring Boot API
- React `/research` route
- 다중 사용자 라벨링
- 로그인/권한
- LLM 자동 라벨링
- benchmark metric 계산
- 검색 API 직접 실행
- repository 파일 직접 쓰기

## 5. 사용자 흐름

```text
docs/search-evaluation/labeling.html 열기
-> article catalog 확인
-> query 작성 또는 starter query 수정
-> query별로 strong / acceptable / not_relevant 선택
-> 애매한 판단은 memo에 기록
-> reviewed 상태로 바꿔 검토 완료 표시
-> label JSON 다운로드
-> 나중에 benchmark runner에서 JSON 사용
```

중간에 브라우저를 닫아도 같은 브라우저에서는 draft를 복구할 수 있어야 한다.
다른 환경으로 옮길 때는 export한 JSON을 import해서 이어간다.

## 6. UI 설계

### 6.1 전체 구조

정적 HTML은 다음 영역으로 구성한다.

1. Header
   - 제목: `검색 평가 라벨링`
   - 현재 catalog ID
   - article 수
   - query 수
   - export/import/reset 버튼

2. Label guide
   - `strong`, `acceptable`, `not_relevant`, `unlabeled` 의미
   - 짧은 예시 하나

3. Article catalog
   - article ID, 제목, 카테고리, topic, 한국어 요약
   - 20-30개 article도 읽을 수 있도록 현재 query page 안의 스크롤 가능한 relevance list로 보여준다.
   - article ID를 크게 보여준다.

4. Query labeling workspace
   - 한 번에 하나의 query page만 표시
   - 이전/다음 버튼
   - 현재 위치 표시: `검색어 3 / 12`
   - 왼쪽 또는 상단의 compact query 목록
   - query jump select
   - 현재 page 안에 query, intent, status, memo
   - 현재 page 안에서 article별 relevance 선택
   - query를 추가하면 새 page로 이동하고, query를 삭제하면 인접 page로 이동한다.

5. JSON preview / validation
   - 접을 수 있는 하단 dock
   - export될 JSON 미리보기
   - warning 목록

### 6.2 Query page/deck 방식

큰 matrix table이나 여러 query card를 세로로 쌓는 방식 대신 query page/deck을 사용한다.

각 query page는 다음 정보를 가진다.

- query text
- search intent
- status
- memo
- article relevance controls
- 이전/다음 이동 control
- query jump control

article relevance control은 다음 형태를 우선한다.

```text
Article 4 | Graph RAG... | [Strong] [Acceptable] [Not relevant] [Clear]
```

`Clear`는 해당 article을 `unlabeled`로 되돌린다.

왼쪽 query 목록은 전체 query 수, 검토 완료 수, query별 label 수와 상태 점을 보여준다.
Desktop에서는 왼쪽 목록 + 오른쪽 현재 query page로 배치하고, 좁은 화면에서는 목록과 page를 세로로 쌓는다.

### 6.3 20-30개 article 대응

article이 늘어날 때를 고려해 다음 기능을 둔다.

- article title/category/topic text search
- category filter
- `labeled only` toggle
- `unlabeled only` toggle
- relevance list 내부 스크롤
- JSON preview/validation dock 접기

이 기능들은 서버 없이 브라우저 메모리에서만 동작한다.
목표는 완전한 admin UI가 아니라, 사람이 30개 정도의 article을 덜 피곤하게 검토하는 것이다.

## 7. 데이터 모델

### 7.1 Article catalog JSON

v1 HTML은 seed catalog를 내장한다.
추후 catalog import를 통해 20-30개 article catalog를 불러올 수 있어야 한다.

```json
{
  "version": 1,
  "catalogId": "seed-v1",
  "generatedAt": "2026-06-01T00:00:00Z",
  "source": "seed-migration",
  "articles": [
    {
      "id": 1,
      "title": "OpenAI Releases Agent Evaluation Toolkit",
      "category": "AI",
      "topics": ["LLM agents", "evaluation", "production AI"],
      "summaryKo": "OpenAI가 multi-step agent workflow를 평가하기 위한 도구를 공개했다는 내용입니다.",
      "whyItMattersKo": "production AI에서 agent 평가가 중요해지고 있음을 보여준다."
    }
  ]
}
```

필수 field:

- `id`
- `title`
- `category`
- `topics`
- `summaryKo`

선택 field:

- `whyItMattersKo`
- `publishedAt`
- `source`
- `url`

### 7.2 Label export JSON

라벨 export는 future benchmark runner가 읽기 쉬워야 한다.

```json
{
  "version": 1,
  "catalogId": "seed-v1",
  "catalogArticleCount": 5,
  "exportedAt": "2026-06-01T00:00:00Z",
  "queries": [
    {
      "query": "graph rag failure",
      "intent": "Graph RAG가 어떤 상황에서 실패하는지 알고 싶다.",
      "status": "reviewed",
      "memo": "Graph RAG 실패 유형을 직접 다루는 article을 strong으로 둔다.",
      "labels": [
        {
          "articleId": 4,
          "relevance": "strong",
          "note": "Graph RAG failure modes를 직접 다룬다."
        },
        {
          "articleId": 1,
          "relevance": "acceptable",
          "note": "evaluation 관점에서 간접 관련이 있다."
        },
        {
          "articleId": 5,
          "relevance": "not_relevant",
          "note": "헷갈릴 수 있지만 Kubernetes support policy는 이 query와 직접 관련이 약하다."
        }
      ]
    }
  ]
}
```

규칙:

- `strong`: query의 핵심 정답이다.
- `acceptable`: 직접 정답은 아니지만 결과에 포함되면 도움이 된다.
- `not_relevant`: 헷갈릴 수 있어 명시적으로 관련 없다고 표시한 article이다.
- `unlabeled`: export하지 않는다.
- `reviewed` query에서 export되지 않은 article은 benchmark 계산 시 관련 없음으로 취급한다.
- `reviewed` query는 최소 하나 이상의 `strong` 또는 `acceptable` label을 가져야 한다.

## 8. Article 수 확장 전략

현재 article 5개는 너무 적다.
검색 평가용으로는 최소 20개, v0.1 포트폴리오 기준으로는 30개 전후가 적절하다.

목표:

```text
최소 catalog: 20 articles
권장 v0.1 catalog: 약 30 articles
확장 catalog: 40-50 articles
```

수집 방식:

```text
controlled collection 실행
-> PostgreSQL에 PUBLISHED article 저장
-> API-ready article만 catalog JSON으로 export
-> labeling.html에서 catalog import
-> 사람이 label 작성
-> labels JSON export
-> benchmark runner 실행
```

중요한 원칙:

- seed SQL을 억지로 늘리지 않는다.
- 출처 정책에 맞는 source에서 가져온 article을 사용한다.
- 한 카테고리에만 몰리지 않게 한다.
- 평가 catalog는 특정 시점에 freeze한다.
- benchmark 결과는 freeze된 catalog ID와 label JSON을 기준으로 기록한다.

권장 분포:

| 영역 | 목표 개수 |
| --- | ---: |
| AI / LLM / agent | 5-7 |
| data / vector search / database | 4-6 |
| security / supply chain | 4-6 |
| infra / cloud / Kubernetes | 4-6 |
| CS research / Graph RAG / retrieval | 5-7 |

## 9. Validation 규칙

export 전에 warning을 보여준다.
warning이 있어도 draft export는 가능하게 한다.

검증 항목:

- query text가 비어 있으면 warning
- `reviewed` query에 `strong` 또는 `acceptable`이 없으면 warning
- 같은 query text가 중복되면 warning
- label의 article ID가 catalog에 없으면 warning
- catalog ID와 label JSON의 catalog ID가 다르면 warning
- `needs_user_label` query가 많으면 warning

강하게 막아야 하는 경우:

- JSON import가 파싱되지 않는 경우
- catalog에 article이 하나도 없는 경우
- label JSON schema version이 지원되지 않는 경우

## 10. Benchmark 연동

이 HTML은 benchmark를 실행하지 않는다.
다만 benchmark runner가 읽을 수 있는 label JSON을 만드는 것이 목적이다.

초기 benchmark runner는 다음을 계산한다.

- `Top1 hit`
- `Recall@5`
- `MRR@5`
- mode별 latency

채점 방향:

- `strong`은 primary relevance로 본다.
- `acceptable`은 secondary relevance로 본다.
- `not_relevant`와 export되지 않은 article은 관련 없음으로 본다.
- 단, query 상태가 `reviewed`인 항목만 benchmark 계산에 사용한다.

정확한 계산식은 runner 구현 시 별도 문서와 테스트로 고정한다.

## 11. 검증 계획

정적 HTML 구현 후 다음을 확인한다.

- 브라우저에서 `docs/search-evaluation/labeling.html`을 열 수 있다.
- 내장 seed article이 렌더링된다.
- query를 추가할 수 있다.
- relevance label을 선택할 수 있다.
- 새로고침 후 localStorage draft가 복구된다.
- JSON export 결과가 유효한 JSON이다.
- export한 JSON을 다시 import하면 같은 상태가 복구된다.
- catalog JSON import가 동작한다.
- validation warning이 표시된다.
- `git diff --check`가 통과한다.

나중에 benchmark runner를 만들 때는 다음 테스트를 추가한다.

- label JSON parser test
- metric calculation unit test
- seed catalog smoke benchmark

## 12. 비목표

이번 작업은 다음을 하지 않는다.

- production research dashboard 구현
- DB-backed labeling system 구현
- 여러 사람이 동시에 라벨링하는 workflow 구현
- LLM으로 relevance를 자동 판단
- RRF weight tuning
- 검색 품질 향상 주장

검색 품질 향상은 label과 benchmark 결과가 생긴 뒤에만 말한다.

## 13. 다음 구현 계획 범위

설계가 승인되면 구현 계획은 다음 단위로 쪼갠다.

1. `docs/search-evaluation/labeling.html` 생성
2. seed article catalog와 starter query 내장
3. query card UI 구현
4. relevance 선택, memo, status 입력 구현
5. localStorage draft 저장/복구 구현
6. label JSON export/import 구현
7. catalog JSON import 구현
8. validation warning 구현
9. 브라우저 수동 검증과 JSON parse 검증
10. dev-log와 topic queue 갱신
