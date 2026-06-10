# Catalog Expansion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 현재 PostgreSQL API-ready article을 새 frozen catalog로 export하고, 확장 라벨링/benchmark가 시작 가능한 artifact와 문서 기준을 만든다.

**Architecture:** 새 production code를 추가하지 않는다. 기존 Spring Boot `search-catalog-export` command runner를 사용해 PostgreSQL source-of-truth의 API-ready article을 JSON으로 export하고, Node 검증 script와 labeling.html 수동 import smoke로 artifact를 확인한다. 기존 6-article smoke baseline은 보존하고 확장 결과는 `api-ready-2026-06-05` 계열 파일과 `expanded/` 결과 디렉터리로 분리한다.

**Tech Stack:** Spring Boot Kotlin command runner, PostgreSQL Docker Compose service, Node.js JSON validation script, static HTML labeling tool, Markdown docs.

---

## Reference Spec

- `docs/superpowers/specs/2026-06-05-catalog-expansion-design.md`

## File Structure

- Create: `experiments/datasets/raw/articles.catalog.api-ready-2026-06-05.json`
  - 새 API-ready article frozen catalog artifact다.
- Modify: `experiments/README.md`
  - 기존 6-article smoke catalog와 새 expanded catalog의 차이를 기록한다.
- Modify: `docs/STATUS.md`
  - 새 catalog export와 검증 결과를 영어 status에 기록한다.
- Modify: `docs/STATUS.ko.md`
  - 새 catalog export와 검증 결과를 한국어 status에 기록한다.
- Modify or create: `docs/blog/2026-06-05-dev-log.md`
  - 실제 실행한 명령, 확인한 article count, 미검증 항목을 기록한다.
- Modify: `docs/blog/topic-queue.md`
  - catalog expansion 과정에서 생긴 기술 블로그 후보를 추가한다.
- No change: backend production code
  - 이번 작업은 기존 command runner 실행과 artifact/documentation 정리만 한다.

## Task 1: Preflight And Catalog Export Command Verification

**Files:**
- Read: `backend/src/main/kotlin/com/sigak/search/evaluation/catalog/SearchCatalogExportCommandRunner.kt`
- Read: `backend/src/main/kotlin/com/sigak/search/evaluation/catalog/SearchCatalogFactory.kt`
- Test: `backend/src/test/kotlin/com/sigak/search/evaluation/catalog/*`

- [x] **Step 1: Confirm working tree state**

Run:

```bash
git status --short --branch
```

Expected:

```txt
## main...origin/main
```

The branch may show `[ahead 2]` or a larger ahead count if the design/plan commits have not been pushed yet.
If modified files exist, inspect them before continuing and do not overwrite user work.

- [x] **Step 2: Run focused catalog export tests**

Run:

```bash
cd backend
./gradlew test --tests 'com.sigak.search.evaluation.catalog.*'
```

Expected:

```txt
BUILD SUCCESSFUL
```

- [x] **Step 3: Confirm PostgreSQL is ready**

Run:

```bash
cd /Users/yonghyun/my-projects/sigak
docker compose -f infra/docker-compose.yml up -d --pull never postgres
docker compose -f infra/docker-compose.yml exec -T postgres pg_isready -U sigak -d sigak
```

Expected:

```txt
/var/run/postgresql:5432 - accepting connections
```

## Task 2: Export The Expanded Catalog

**Files:**
- Create: `experiments/datasets/raw/articles.catalog.api-ready-2026-06-05.json`
- Preserve: `experiments/datasets/raw/articles.catalog.json`

- [x] **Step 1: Run search catalog export with a new catalog ID**

Run:

```bash
cd backend
./gradlew bootRun --args='search-catalog-export --output=../experiments/datasets/raw/articles.catalog.api-ready-2026-06-05.json --limit=50 --catalog-id=api-ready-2026-06-05'
```

Expected:

```txt
Search catalog export completed
catalogId=api-ready-2026-06-05
articleCount=41
output=../experiments/datasets/raw/articles.catalog.api-ready-2026-06-05.json
```

If `articleCount` is not `41` in a later local run, continue to Task 3 and use the actual JSON count for the scale gate.

- [x] **Step 2: Confirm the old smoke catalog still exists**

Run:

```bash
test -f experiments/datasets/raw/articles.catalog.json
test -f experiments/datasets/raw/articles.catalog.api-ready-2026-06-05.json
```

Expected: both commands exit with code `0`.

- [x] **Step 3: Confirm the old smoke baseline content was not changed**

Run from repository root:

```bash
node -e "
const fs = require('fs');
const catalog = JSON.parse(fs.readFileSync('experiments/datasets/raw/articles.catalog.json', 'utf8'));
const labels = JSON.parse(fs.readFileSync('experiments/datasets/labels/search-labels.api-ready-2026-06-02.2026-06-02.json', 'utf8'));
const reviewed = (labels.queries ?? []).filter((query) => query.status === 'reviewed');
const explicitLabels = (labels.queries ?? []).flatMap((query) => query.labels ?? []);
const invalid = [];
if (catalog.catalogId !== 'api-ready-2026-06-02') invalid.push('old catalogId');
if (!Array.isArray(catalog.articles) || catalog.articles.length !== 6) invalid.push('old catalog article count');
if (labels.catalogId !== 'api-ready-2026-06-02') invalid.push('old label catalogId');
if (labels.catalogArticleCount !== 6) invalid.push('old label catalogArticleCount');
if (reviewed.length !== 3) invalid.push('old reviewed query count');
if (explicitLabels.length !== 12) invalid.push('old explicit label count');
if (invalid.length > 0) {
  console.error('old-baseline-invalid', invalid);
  process.exit(1);
}
console.log(JSON.stringify({
  catalogId: catalog.catalogId,
  articleCount: catalog.articles.length,
  labelCatalogId: labels.catalogId,
  reviewedQueryCount: reviewed.length,
  explicitLabelCount: explicitLabels.length
}, null, 2));
"
```

Expected:

```json
{
  "catalogId": "api-ready-2026-06-02",
  "articleCount": 6,
  "labelCatalogId": "api-ready-2026-06-02",
  "reviewedQueryCount": 3,
  "explicitLabelCount": 12
}
```

- [x] **Step 4: Confirm latest smoke result directories were not touched**

Run:

```bash
git status --short experiments/results/retrieval/latest experiments/results/graph/latest
git diff --name-only -- experiments/results/retrieval/latest experiments/results/graph/latest
```

Expected: both commands print no changed paths.

## Task 3: Validate The Expanded Catalog JSON

**Files:**
- Verify: `experiments/datasets/raw/articles.catalog.api-ready-2026-06-05.json`

- [x] **Step 1: Run JSON schema and duplicate-ID validation**

Run from repository root:

```bash
node -e "
const fs = require('fs');
const path = 'experiments/datasets/raw/articles.catalog.api-ready-2026-06-05.json';
const catalog = JSON.parse(fs.readFileSync(path, 'utf8'));
const ids = catalog.articles.map((article) => article.id);
const duplicateIds = ids.filter((id, index) => ids.indexOf(id) !== index);
const invalid = [];
if (catalog.version !== 1) invalid.push('version');
if (catalog.catalogId !== 'api-ready-2026-06-05') invalid.push('catalogId');
if (catalog.source !== 'postgres-api-ready') invalid.push('source');
if (!Array.isArray(catalog.articles) || catalog.articles.length === 0) invalid.push('articles');
if (duplicateIds.length > 0) invalid.push('duplicateIds=' + [...new Set(duplicateIds)].join(','));
for (const article of catalog.articles) {
  if (!Number.isInteger(article.id) || article.id < 1) invalid.push('invalidId');
  if (typeof article.title !== 'string' || article.title.trim() === '') invalid.push('title');
  if (typeof article.category !== 'string' || article.category.trim() === '') invalid.push('category');
  if (!Array.isArray(article.topics)) invalid.push('topics');
  if (typeof article.summaryKo !== 'string' || article.summaryKo.trim() === '') invalid.push('summaryKo');
}
if (invalid.length > 0) {
  console.error('catalog-invalid', [...new Set(invalid)]);
  process.exit(1);
}
console.log(JSON.stringify({
  catalogId: catalog.catalogId,
  articleCount: catalog.articles.length,
  firstId: ids[0],
  lastId: ids[ids.length - 1]
}, null, 2));
"
```

Observed in the 2026-06-05 local export:

```json
{
  "catalogId": "api-ready-2026-06-05",
  "articleCount": 41,
  "firstId": 3,
  "lastId": 16
}
```

- [x] **Step 2: Apply dataset scale gate**

Use the `articleCount` printed in Step 1.

Expected gate for the current path:

```txt
articleCount >= 20
```

If the value is `1-19`, skip Tasks 4-5 and still complete Tasks 6-8 by documenting the low-count result, marking labeling and benchmark work as `미검증`, and recording that collection/source curation is needed. If the value is `0`, inspect PostgreSQL seed/migration and API-ready filtering before doing anything else.

## Task 4: Prepare Labeling Handoff

**Files:**
- Use: `docs/search-evaluation/labeling.html`
- Use: `experiments/datasets/raw/articles.catalog.api-ready-2026-06-05.json`

- [ ] **Step 1: Ask the user to import the new catalog in labeling.html**

Instruction to user:

```txt
docs/search-evaluation/labeling.html을 브라우저에서 열고,
카탈로그 JSON 가져오기 버튼으로
experiments/datasets/raw/articles.catalog.api-ready-2026-06-05.json
파일을 선택해주세요.
```

Expected user confirmation:

```txt
새 catalog article count가 화면에 표시되고, category filter와 article sort가 동작한다.
```

- [x] **Step 2: Create query candidates for the expanded catalog**

Draft this candidate set for user review:

```txt
graph rag
vector search
agent evaluation
retrieval quality
production ai evaluation
supply chain attack
database indexing
kubernetes operations
AI security
LLM evaluation
그래프 RAG
벡터 검색
AI 보안
```

Expected:

```txt
The user selects or edits 10-15 queries before labeling.
```

Do not run retrieval or graph benchmark before the user creates a new label JSON.

Task 4 status: query candidates are prepared; actual browser import and confirmation are deferred to the user after this session.

## Task 5: Validate The User-Created Label JSON

**Files:**
- Verify: `experiments/datasets/labels/search-labels.api-ready-2026-06-05.2026-06-05.json`

- [ ] **Step 1: Wait until the user saves the label JSON**

Expected file path:

```txt
experiments/datasets/labels/search-labels.api-ready-2026-06-05.2026-06-05.json
```

- [ ] **Step 2: Run catalog-label consistency validation**

Run from repository root after the label file exists:

```bash
node -e "
const fs = require('fs');
const catalog = JSON.parse(fs.readFileSync('experiments/datasets/raw/articles.catalog.api-ready-2026-06-05.json', 'utf8'));
const labels = JSON.parse(fs.readFileSync('experiments/datasets/labels/search-labels.api-ready-2026-06-05.2026-06-05.json', 'utf8'));
const articleIds = new Set(catalog.articles.map((article) => article.id));
const invalid = [];
if (labels.catalogId !== catalog.catalogId) invalid.push('catalogId mismatch');
if (labels.catalogArticleCount !== catalog.articles.length) invalid.push('catalogArticleCount mismatch');
const reviewed = (labels.queries ?? []).filter((query) => query.status === 'reviewed');
if (reviewed.length < 10) invalid.push('reviewed query count below 10');
for (const query of reviewed) {
  const positives = (query.labels ?? []).filter((label) => label.relevance === 'strong' || label.relevance === 'acceptable');
  if (positives.length === 0) invalid.push('missing positive label for query=' + query.query);
  for (const label of query.labels ?? []) {
    if (!articleIds.has(label.articleId)) invalid.push('unknown articleId=' + label.articleId);
  }
}
if (invalid.length > 0) {
  console.error('labels-invalid', [...new Set(invalid)]);
  process.exit(1);
}
console.log(JSON.stringify({
  catalogId: labels.catalogId,
  catalogArticleCount: labels.catalogArticleCount,
  reviewedQueryCount: reviewed.length
}, null, 2));
"
```

Expected:

```json
{
  "catalogId": "api-ready-2026-06-05",
  "catalogArticleCount": 41,
  "reviewedQueryCount": 10
}
```

If the reviewed query count is greater than `10`, record the actual value.
The browser UI displays this state as `검토 완료`, but the exported JSON stores it as `reviewed`.

Task 5 status: deferred because `experiments/datasets/labels/search-labels.api-ready-2026-06-05.2026-06-05.json` does not exist yet.

## Task 6: Update Docs After Catalog Export

**Files:**
- Modify: `experiments/README.md`
- Modify: `docs/STATUS.md`
- Modify: `docs/STATUS.ko.md`

- [x] **Step 1: Update experiments README with expanded catalog note**

Modify `experiments/README.md` in the Catalog Export Command section. Add this paragraph after the sentence that says the 2026-06-02 sample artifact contains 6 articles:

```md
2026-06-05에는 같은 command로 `experiments/datasets/raw/articles.catalog.api-ready-2026-06-05.json` 확장 catalog를 별도 생성한다.
기존 `articles.catalog.json`과 `api-ready-2026-06-02` label/result는 smoke baseline으로 보존하고, 확장 라벨링과 benchmark는 `api-ready-2026-06-05` 계열 파일과 `experiments/results/*/expanded/` 아래에서 먼저 검증한다.
```

After export, append the actual article count to the paragraph in Korean. For the 2026-06-05 run, use this final sentence:

```md
로컬 검증 결과 article count는 `41`이었다.
```

- [x] **Step 2: Update STATUS.md**

In `docs/STATUS.md`, update the relevant search infra/docs sections to mention the expanded catalog artifact. Include the actual values from Task 3:

```md
- The expanded API-ready search catalog artifact `experiments/datasets/raw/articles.catalog.api-ready-2026-06-05.json` was generated with `catalogId=api-ready-2026-06-05` and article count `41`; the 6-article `api-ready-2026-06-02` smoke catalog remains preserved as the baseline.
```

If the article count is not `41` in a later local run, replace only the number with the verified Task 3 value.

- [x] **Step 3: Update STATUS.ko.md**

In `docs/STATUS.ko.md`, add the Korean equivalent with the actual Task 3 count:

```md
- 확장 API-ready 검색 catalog artifact `experiments/datasets/raw/articles.catalog.api-ready-2026-06-05.json`를 `catalogId=api-ready-2026-06-05`, article count `41`로 생성했다. 기존 6개 article 기준 `api-ready-2026-06-02` smoke catalog는 baseline으로 보존한다.
```

If the article count is not `41` in a later local run, replace only the number with the verified Task 3 value.

## Task 7: Session Wrap-Up Docs

**Files:**
- Create or modify: `docs/blog/2026-06-05-dev-log.md`
- Modify: `docs/blog/topic-queue.md`

- [x] **Step 1: Read the blog writing guide**

Run:

```bash
sed -n '1,240p' docs/blog/WRITING_GUIDE.ko.md
```

Expected: guide content is printed. Follow it for the dev-log and topic queue.

- [x] **Step 2: Write the June 5 dev-log**

Create or update `docs/blog/2026-06-05-dev-log.md` with:

```md
---
title: "2026-06-05 Dev Log"
date: 2026-06-05
---

# 2026-06-05 Dev Log

## 오늘 한 일

- `api-ready-2026-06-05` 확장 catalog 설계를 확정했다.
- 기존 6개 article smoke baseline을 보존하고, 새 catalog/label/result artifact를 분리하기로 했다.
- `search-catalog-export` command로 확장 catalog를 생성했다.
- JSON 검증으로 catalogId, article count, 중복 ID, 필수 필드를 확인했다.

## 검증

- `./gradlew test --tests 'com.sigak.search.evaluation.catalog.*'`: 성공
- PostgreSQL `pg_isready`: 성공
- `search-catalog-export`: 성공, article count `41`
- catalog JSON validation: 성공

## 미검증

- 새 label JSON 작성은 아직 사용자의 수동 라벨링 이후 진행한다.
- retrieval/graph benchmark 재실행은 새 label JSON 작성 이후 진행한다.

## 설계 판단

- 기존 `latest` smoke result를 덮어쓰지 않고 `expanded/` 경로를 먼저 사용한다.
- article 수 확장만으로 품질 주장을 하지 않고, 최소 10개 reviewed query label 이후 benchmark를 실행한다.
```

If any verification command did not run or produced a different result, change the dev-log to report the actual status and mark it `미검증`.

- [x] **Step 3: Update topic queue**

Append a candidate topic to `docs/blog/topic-queue.md`:

```md
## [candidate] 작은 smoke catalog에서 확장 평가 catalog로 넘어가는 기준

- 날짜: 2026-06-05
- 관련 작업: API-ready article frozen catalog 확장, baseline artifact 보존, label/catalog compatibility gate 정리
- 관련 파일:
  - `docs/superpowers/specs/2026-06-05-catalog-expansion-design.md`
  - `docs/superpowers/plans/2026-06-05-catalog-expansion.md`
  - `experiments/datasets/raw/articles.catalog.api-ready-2026-06-05.json`
- 감지 이유:
  - 6개 article smoke catalog와 확장 catalog를 분리했다.
  - catalogId와 label JSON 불일치를 benchmark 오염 위험으로 다뤘다.
  - article 수와 label 수를 별도 gate로 나눴다.
- 글의 핵심 질문:
  - 검색 품질 평가에서 catalog 크기와 label 수는 각각 어떤 의미를 가지는가?
  - 기존 smoke result를 보존하는 것이 왜 중요한가?
  - label/catalog mismatch는 어떻게 benchmark 신뢰성을 깨뜨리는가?
- 상태: candidate
```

## Task 8: Final Verification And Commit

**Files:**
- Verify all changed files from Tasks 2, 6, and 7.

- [x] **Step 1: Run backend catalog focused tests again**

Run:

```bash
cd backend
./gradlew test --tests 'com.sigak.search.evaluation.catalog.*'
```

Expected:

```txt
BUILD SUCCESSFUL
```

- [x] **Step 2: Run whitespace check**

Run from repository root:

```bash
git diff --check
```

Expected: no output and exit code `0`.

- [x] **Step 3: Inspect git diff**

Run:

```bash
git status --short
git diff --stat
```

Expected changed files include only:

```txt
experiments/datasets/raw/articles.catalog.api-ready-2026-06-05.json
experiments/README.md
docs/STATUS.md
docs/STATUS.ko.md
docs/blog/2026-06-05-dev-log.md
docs/blog/topic-queue.md
docs/superpowers/specs/2026-06-05-catalog-expansion-design.md
```

The plan file itself may also appear if this plan has not yet been committed.

- [ ] **Step 4: Commit catalog expansion artifacts and docs**

Run:

```bash
git add experiments/datasets/raw/articles.catalog.api-ready-2026-06-05.json experiments/README.md docs/STATUS.md docs/STATUS.ko.md docs/blog/2026-06-05-dev-log.md docs/blog/topic-queue.md
git commit -m "docs: add expanded search catalog artifact"
```

If the implementation plan file is still uncommitted, include it in a separate docs commit before this artifact commit.
Step 4 remains unchecked until the commit that includes this plan update is actually created.

## Stop Conditions

- Do not overwrite `experiments/datasets/raw/articles.catalog.json`.
- Do not overwrite `experiments/results/retrieval/latest/` or `experiments/results/graph/latest/`.
- Do not run retrieval/graph benchmark until a new `api-ready-2026-06-05` label JSON exists.
- Do not claim search or graph quality improvement from catalog export alone.
- If export article count is below `20`, stop after documentation and plan additional collection/source curation.
