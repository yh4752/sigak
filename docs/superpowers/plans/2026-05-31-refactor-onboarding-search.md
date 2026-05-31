# Refactor And Onboarding Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve current behavior while making the backend search path easier to extend and adding a Korean onboarding handoff document for new developers.

**Architecture:** Keep Spring Boot's current layered architecture. Refactor within existing backend search/article service boundaries using small private helpers or narrow value objects before introducing new collaborators. Add one onboarding document and link it from the docs index; do not create a broad documentation hierarchy.

**Tech Stack:** Kotlin, Spring Boot, JUnit, Mockito, Markdown documentation.

---

## Reference Spec

- `docs/superpowers/specs/2026-05-31-refactor-onboarding-search-design.md`

## File Structure

Create:

- `docs/ONBOARDING.ko.md`
  - New-developer handoff guide: first-day read order, local run path, module map,
    search flow, invariants, verification commands, change-impact checklist, and
    blog topic queue routine.

Modify:

- `docs/README.md`
  - Link the onboarding document in the recommended reading order and directory
    guide.
- `docs/README.ko.md`
  - Link the onboarding document in the Korean documentation index.
- `backend/src/main/kotlin/com/sigak/search/hybrid/ArticlePublicSearchService.kt`
  - Clarify public search orchestration without changing behavior.
- `backend/src/main/kotlin/com/sigak/article/service/ArticleService.kt`
  - Clarify public search reload and metric recording flow without changing
    response mapping.
- `backend/src/test/kotlin/com/sigak/search/hybrid/ArticlePublicSearchServiceTest.kt`
  - Add or refine test helpers only if they make behavior easier to read.
- `backend/src/test/kotlin/com/sigak/article/service/ArticleServiceTest.kt`
  - Add or refine test helpers only if needed for the refactor.
- `docs/blog/topic-queue.md`
  - Add or update a blog topic only if the task reveals a concrete writing
    candidate under the spec's queue rule.

## Invariants

- Do not change public article API response shape.
- Do not change RRF ranking, candidate limits, weights, or fallback policy.
- Do not expose search scores or diagnostics from public article APIs.
- Do not add Neo4j, collection trigger, benchmark scoring, or real enrichment in
  this plan.
- Do not introduce generic orchestration frameworks or broad architecture
  patterns.

## Tasks

### Task 1: Baseline Verification

**Files:**
- Read: `docs/superpowers/specs/2026-05-31-refactor-onboarding-search-design.md`
- Read: `backend/src/main/kotlin/com/sigak/search/hybrid/ArticlePublicSearchService.kt`
- Read: `backend/src/main/kotlin/com/sigak/article/service/ArticleService.kt`

- [ ] **Step 1: Verify clean branch state**

Run:

```bash
git status --short --branch
```

Expected: branch `codex/refactor-onboarding-search` with no uncommitted changes.

- [ ] **Step 2: Run focused baseline tests**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.search.hybrid.ArticlePublicSearchServiceTest \
  --tests com.sigak.article.service.ArticleServiceTest \
  --tests com.sigak.search.metrics.ArticleSearchMetricsRecorderTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Record refactor constraints**

Before changing code, note in the working summary that public API shape, RRF
ranking, fallback policy, and metric semantics are behavior-preserving
constraints.

### Task 2: Add New-Developer Onboarding Guide

**Files:**
- Create: `docs/ONBOARDING.ko.md`
- Modify: `docs/README.md`
- Modify: `docs/README.ko.md`

- [ ] **Step 1: Create onboarding guide**

Create `docs/ONBOARDING.ko.md` with these sections:

```md
# Sigak 신규 개발자 온보딩

## 1. 첫날 읽기 순서
1. `README.md`
2. `docs/STATUS.md`
3. `docs/ROADMAP.md`
4. `docs/API_SPEC.md`
5. `docs/CODING_CONVENTIONS.md`
6. 작업 중인 `docs/superpowers/specs/*`와 `docs/superpowers/plans/*`

## 2. 로컬 실행
- 인프라: `docker compose -f infra/docker-compose.yml up -d postgres elasticsearch qdrant ai`
- 백엔드: `cd backend && ./gradlew bootRun`
- 프론트엔드: `cd frontend && npm run dev`
- AI 서버 단독 테스트: `cd ai && .venv/bin/python -m pytest`

## 3. 검증 명령
- Backend: `cd backend && ./gradlew test --rerun-tasks`
- Backend check: `cd backend && ./gradlew check`
- Frontend: `cd frontend && npm test && npm run lint && npm run build`
- AI: `cd ai && .venv/bin/python -m pytest`

## 4. 핵심 모듈 책임
| 영역 | 책임 |
| --- | --- |
| `backend/article` | 공개 article list/detail/search 응답 조립 |
| `backend/search/projection` | Elasticsearch keyword projection rebuild |
| `backend/search/vector` | Qdrant vector projection, internal vector search |
| `backend/search/hybrid` | public keyword/vector candidate fusion과 fallback mode 결정 |
| `backend/search/metrics` | public search metric snapshot |
| `backend/collection` | source fetch, normalize, mock enrich, persist |
| `ai` | mock enrichment와 embedding provider |
| `frontend` | article search/detail UI와 API boundary validation |

## 5. 검색 흐름
`GET /api/articles?query=...`는 Elasticsearch keyword 후보와 Qdrant vector 후보를 만들고, RRF로 article ID를 합친 뒤 PostgreSQL에서 최종 article response를 다시 읽는다.

## 6. 깨면 안 되는 계약
- Public article API response shape를 바꾸지 않는다.
- PostgreSQL은 source of truth다.
- Elasticsearch, Qdrant, Neo4j는 rebuildable projection store다.
- Projection 실패는 metric에 남기되 public API를 불필요하게 깨지 않는다.

## 7. 변경 영향 체크리스트
- API response가 바뀌면 `docs/API_SPEC.md`, frontend Zod schema, controller tests를 함께 본다.
- 검색 흐름이 바뀌면 search service tests, article service tests, metrics tests를 함께 본다.
- 인프라 명령이 바뀌면 `README.md`, `infra/README.md`, `docs/ONBOARDING.ko.md`를 함께 본다.
- 설계 고민이나 오류가 생기면 `docs/blog/topic-queue.md`에 후보를 추가할지 판단한다.
```

- [ ] **Step 2: Link onboarding from docs indexes**

Add `ONBOARDING.ko.md` to the recommended reading order and directory guide in
both docs index files. In English docs, describe it as a Korean new-developer
handoff guide.

- [ ] **Step 3: Verify documentation wording**

Run:

```bash
rg -n "ONBOARDING|신규 개발자|source of truth|projection store" docs/README.md docs/README.ko.md docs/ONBOARDING.ko.md
```

Expected: onboarding links and key invariants are present.

### Task 3: Refactor Public Search Orchestration Readability

**Files:**
- Modify: `backend/src/main/kotlin/com/sigak/search/hybrid/ArticlePublicSearchService.kt`
- Test: `backend/src/test/kotlin/com/sigak/search/hybrid/ArticlePublicSearchServiceTest.kt`

- [ ] **Step 1: Run current public search tests**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.search.hybrid.ArticlePublicSearchServiceTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Refactor without changing behavior**

Inside `ArticlePublicSearchService`, make the orchestration sequence easier to
read using private helpers with domain names such as:

```kotlin
private fun searchKeywordCandidates(query: String): SearchAttempt<List<ArticleSearchCandidate>>
private fun searchVectorCandidates(query: String): SearchAttempt<ArticleVectorCandidateSearchResult>
private fun selectArticleIds(
    mode: ArticlePublicSearchMode,
    keywordCandidates: List<ArticleSearchCandidate>,
    vectorCandidates: List<ArticleSearchCandidate>,
    fusedCandidates: List<ArticleSearchCandidate>
): List<Long>
```

Keep the existing mode resolution and fallback reason semantics:

```kotlin
KEYWORD_SEARCH_FAILED
VECTOR_SEARCH_FAILED
EMBEDDING_FAILED
QDRANT_SEARCH_FAILED
```

Do not create new production classes unless the file remains harder to read
after private extraction.

- [ ] **Step 3: Run public search tests again**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.search.hybrid.ArticlePublicSearchServiceTest
```

Expected: `BUILD SUCCESSFUL`.

### Task 4: Refactor ArticleService Search Reload Flow

**Files:**
- Modify: `backend/src/main/kotlin/com/sigak/article/service/ArticleService.kt`
- Test: `backend/src/test/kotlin/com/sigak/article/service/ArticleServiceTest.kt`
- Test: `backend/src/test/kotlin/com/sigak/search/metrics/ArticleSearchMetricsRecorderTest.kt`

- [ ] **Step 1: Run focused article service tests**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.article.service.ArticleServiceTest \
  --tests com.sigak.search.metrics.ArticleSearchMetricsRecorderTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Clarify search reload and metric recording**

In `ArticleService`, keep public methods unchanged, but make these concepts
clearer through private helpers or local value objects:

```txt
search result -> response load result -> stale candidate count -> metric observation
```

Acceptable helper names include:

```kotlin
private fun loadResponsesForSearchResult(query: String, searchResult: ArticlePublicSearchResult): Measured<List<ArticleResponse>>
private fun staleCandidateCount(searchResult: ArticlePublicSearchResult, responses: List<ArticleResponse>): Int
private fun recordSearchObservation(...)
```

Do not move response mapping out of `ArticleService` in this slice unless the
tests show a concrete need. That larger extraction can be a later refactor.

- [ ] **Step 3: Run article and metrics tests again**

Run:

```bash
cd backend
./gradlew test --tests com.sigak.article.service.ArticleServiceTest \
  --tests com.sigak.search.metrics.ArticleSearchMetricsRecorderTest
```

Expected: `BUILD SUCCESSFUL`.

### Task 5: Blog Topic Queue Check

**Files:**
- Modify if needed: `docs/blog/topic-queue.md`

- [ ] **Step 1: Evaluate queue criteria**

Check whether at least two criteria from the spec's "Blog Topic Queue Rule" were
met. If yes, add or update a candidate. If no, report that no queue update was
needed.

- [ ] **Step 2: Keep queue factual**

If adding a topic, include:

```md
## [candidate] 리팩토링으로 검색 경계를 정리한 이유
- 날짜: 2026-05-31
- 관련 작업:
- 관련 파일:
- 감지 이유:
- 글의 핵심 질문:
- 검증 근거:
- 추천 글 유형:
- 상태: candidate
```

Use only commands actually run in this task as verification evidence.

### Task 6: Final Verification

**Files:**
- Verify repository state.

- [ ] **Step 1: Run full backend tests**

Run:

```bash
cd backend
./gradlew test --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run backend check**

Run:

```bash
cd backend
./gradlew check
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Check Markdown and whitespace**

Run:

```bash
git diff --check
```

Expected: no output and exit code 0.

- [ ] **Step 4: Summarize behavior preservation**

Report:

- API shape changed: no
- Ranking/fallback policy changed: no
- Tests run and result
- Onboarding docs updated
- Blog topic queue updated or intentionally unchanged
