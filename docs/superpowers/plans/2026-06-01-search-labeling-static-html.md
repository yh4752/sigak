# Search Labeling Static HTML Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 검색 평가용 query/article relevance label을 브라우저에서 편하게 입력하고 JSON으로 export할 수 있는 정적 HTML 도구를 만든다.

**Architecture:** `docs/search-evaluation/labeling.html` 단일 파일에 seed catalog, starter queries, UI, 상태 관리, localStorage 저장, JSON import/export, validation을 모두 둔다. 서버, DB, React route, 외부 CDN 없이 `file://`로 열어 동작하게 만들고, 나중에 benchmark runner가 읽을 수 있는 label JSON schema를 유지한다.

**Tech Stack:** HTML, CSS, vanilla JavaScript, browser localStorage, JSON import/export, Node.js verification snippet.

**Execution status (2026-06-01):** Implemented with Subagent-Driven review. Static embedded JSON/script checks and `git diff --check` passed. Later updated with the Claude Design zip artifact to use a query page/deck UI instead of vertically stacked query cards. User-side manual browser smoke verification passed after opening the local HTML directly. Agent-side browser automation remains blocked for local `file://` by policy, so screenshots and downloaded JSON parsing were not reproduced by Codex.

---

## Reference Spec

- `docs/superpowers/specs/2026-06-01-search-labeling-static-html-design.md`
- `docs/search-evaluation/queries.md`

## Files

Create:

- `docs/search-evaluation/labeling.html`

Modify:

- `docs/search-evaluation/queries.md`
- `docs/STATUS.md`
- `docs/STATUS.ko.md`
- `docs/ROADMAP.md`
- `docs/ROADMAP.ko.md`
- `docs/blog/2026-06-01-dev-log.md` (create if missing)
- `docs/blog/topic-queue.md`

No backend, frontend app, or AI server files should change in this plan.

## Implementation Notes

- Keep the page self-contained. Do not add npm dependencies or CDN links.
- UI text should be Korean.
- HTML/CSS/JS can live in one file because this is a local documentation tool, not app runtime code.
- Use `unlabeled` as the default UI state. Do not force the user to mark every unrelated article as `not_relevant`.
- Export only labels whose relevance is not `unlabeled`.
- In benchmark semantics, a `reviewed` query treats omitted article IDs as not relevant.
- Use a localStorage key with project/date scope:

```javascript
const STORAGE_KEY = 'sigak.searchLabeling.v1';
```

## Tasks

### Task 1: Baseline And Existing Context Check

**Files:**
- Read: `docs/superpowers/specs/2026-06-01-search-labeling-static-html-design.md`
- Read: `docs/search-evaluation/queries.md`
- Read: `backend/src/main/resources/db/migration/V2__seed_article_data.sql`

- [ ] **Step 1: Confirm current working tree**

Run:

```bash
git status --short
```

Expected: existing documentation changes may be present. Do not revert unrelated user or prior-session changes.

- [ ] **Step 2: Re-read the design and guide**

Run:

```bash
sed -n '1,260p' docs/superpowers/specs/2026-06-01-search-labeling-static-html-design.md
sed -n '1,260p' docs/search-evaluation/queries.md
```

Expected: design includes query-card UI, `localStorage`, JSON import/export, and article count strategy.

- [ ] **Step 3: Confirm seed article facts**

Run:

```bash
sed -n '1,120p' backend/src/main/resources/db/migration/V2__seed_article_data.sql
```

Expected: seed articles `1-5`, topics, summaries, and relation hints are visible.

### Task 2: Create Static HTML Skeleton And Embedded Data

**Files:**
- Create: `docs/search-evaluation/labeling.html`

- [ ] **Step 1: Create the page skeleton**

Create `docs/search-evaluation/labeling.html` with this structure:

```html
<!doctype html>
<html lang="ko">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Sigak 검색 평가 라벨링</title>
  <style>
    :root {
      --bg: #f7f8fa;
      --surface: #ffffff;
      --ink: #17202a;
      --muted: #5d6878;
      --line: #d8dee8;
      --strong: #186a3b;
      --acceptable: #8a5a00;
      --negative: #a33a3a;
      --accent: #2557a7;
    }

    * {
      box-sizing: border-box;
    }

    body {
      margin: 0;
      background: var(--bg);
      color: var(--ink);
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      line-height: 1.5;
    }

    button,
    input,
    select,
    textarea {
      font: inherit;
    }
  </style>
</head>
<body>
  <header class="page-header">
    <div>
      <p class="eyebrow">Sigak Search Evaluation</p>
      <h1>검색 평가 라벨링</h1>
      <p class="lead">query별로 어떤 article이 strong, acceptable, not relevant인지 판단하고 JSON으로 저장합니다.</p>
    </div>
    <div class="header-actions">
      <button type="button" id="import-labels-button">라벨 JSON 가져오기</button>
      <button type="button" id="import-catalog-button">Catalog JSON 가져오기</button>
      <button type="button" id="export-button" class="primary">라벨 JSON 다운로드</button>
    </div>
  </header>

  <main class="layout">
    <section class="panel" id="guide-panel"></section>
    <section class="panel" id="catalog-panel"></section>
    <section class="panel" id="workspace-panel"></section>
    <section class="panel" id="json-panel"></section>
  </main>

  <input type="file" id="labels-file-input" accept="application/json,.json" hidden>
  <input type="file" id="catalog-file-input" accept="application/json,.json" hidden>

  <script id="seed-catalog" type="application/json"></script>
  <script id="starter-labels" type="application/json"></script>
  <script>
    const STORAGE_KEY = 'sigak.searchLabeling.v1';
  </script>
</body>
</html>
```

- [ ] **Step 2: Add embedded seed catalog JSON**

Populate `<script id="seed-catalog" type="application/json">` with:

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
    },
    {
      "id": 2,
      "title": "PostgreSQL Adds Native Vector Index Improvements",
      "category": "DATA",
      "topics": ["PostgreSQL", "vector search", "database indexing"],
      "summaryKo": "PostgreSQL의 vector index 성능 개선 내용입니다.",
      "whyItMattersKo": "RAG와 검색 기능을 너무 이른 인프라 확장 없이 구현할 수 있는 선택지를 넓힌다."
    },
    {
      "id": 3,
      "title": "Critical Package Registry Attack Targets AI Toolchains",
      "category": "SECURITY",
      "topics": ["supply chain security", "package registry", "AI tooling"],
      "summaryKo": "AI 개발 도구를 설치하는 개발 환경을 노린 package registry 공격입니다.",
      "whyItMattersKo": "AI 개발 환경은 자동화, credential, 빠른 package 변화가 얽혀 공급망 공격의 영향 범위가 커질 수 있다."
    },
    {
      "id": 4,
      "title": "New Research Maps Failure Modes in Graph RAG Systems",
      "category": "CS_RESEARCH",
      "topics": ["Graph RAG", "knowledge graphs", "retrieval quality"],
      "summaryKo": "Graph RAG 시스템의 실패 유형과 평가 기준을 다룬 연구입니다.",
      "whyItMattersKo": "관계 기반 retrieval 실패를 이해해야 더 근거 있는 graph-aware insight를 설계할 수 있다."
    },
    {
      "id": 5,
      "title": "Kubernetes Project Updates Long-Term Support Policy",
      "category": "INFRA_CLOUD",
      "topics": ["Kubernetes", "release policy", "platform operations"],
      "summaryKo": "Kubernetes 장기 지원 정책 변경입니다.",
      "whyItMattersKo": "지원 기간은 production cluster의 upgrade 계획, 보안 태세, 운영 비용에 영향을 준다."
    }
  ]
}
```

- [ ] **Step 3: Add embedded starter labels JSON**

Populate `<script id="starter-labels" type="application/json">` with:

```json
{
  "version": 1,
  "catalogId": "seed-v1",
  "catalogArticleCount": 5,
  "exportedAt": null,
  "queries": [
    {
      "query": "agent evaluation",
      "intent": "LLM agent 평가와 production AI 품질 검증을 찾는다.",
      "status": "reviewed",
      "memo": "article 1이 직접 정답이고, article 4는 평가 기준이라는 점에서 간접 관련이 있다.",
      "labels": [
        {
          "articleId": 1,
          "relevance": "strong",
          "note": "agent evaluation toolkit을 직접 다룬다."
        },
        {
          "articleId": 4,
          "relevance": "acceptable",
          "note": "evaluation criteria와 retrieval 품질 평가 측면에서 간접 관련이 있다."
        }
      ]
    },
    {
      "query": "graph rag failure",
      "intent": "Graph RAG 실패 유형과 평가 기준을 찾는다.",
      "status": "needs_user_label",
      "memo": "",
      "labels": []
    },
    {
      "query": "postgres vector search",
      "intent": "PostgreSQL 기반 vector search나 index 개선을 찾는다.",
      "status": "needs_user_label",
      "memo": "",
      "labels": []
    }
  ]
}
```

- [ ] **Step 4: Run a basic JSON extraction check**

Run:

```bash
node - <<'NODE'
const fs = require('fs');
const html = fs.readFileSync('docs/search-evaluation/labeling.html', 'utf8');
for (const id of ['seed-catalog', 'starter-labels']) {
  const match = html.match(new RegExp(`<script id="${id}" type="application/json">([\\s\\S]*?)</script>`));
  if (!match) throw new Error(`${id} script block missing`);
  JSON.parse(match[1]);
  console.log(`${id}: ok`);
}
NODE
```

Expected:

```text
seed-catalog: ok
starter-labels: ok
```

### Task 3: Add Layout And Readable Styling

**Files:**
- Modify: `docs/search-evaluation/labeling.html`

- [ ] **Step 1: Add page layout CSS**

Extend the `<style>` block with these concrete layout styles:

```css
.page-header {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding: 28px 32px;
  background: var(--surface);
  border-bottom: 1px solid var(--line);
}

.eyebrow {
  margin: 0 0 6px;
  color: var(--accent);
  font-size: 13px;
  font-weight: 700;
  text-transform: uppercase;
}

.lead {
  max-width: 760px;
  margin: 8px 0 0;
  color: var(--muted);
}

.header-actions {
  display: flex;
  flex-wrap: wrap;
  align-content: flex-start;
  justify-content: flex-end;
  gap: 8px;
}

.layout {
  display: grid;
  grid-template-columns: minmax(260px, 340px) minmax(0, 1fr);
  gap: 18px;
  padding: 18px;
}

.panel {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 18px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.panel-header h2 {
  margin: 0;
  font-size: 18px;
}

.panel-header p {
  margin: 4px 0 0;
  color: var(--muted);
}

.guide-grid,
.article-grid,
.query-list {
  display: grid;
  gap: 12px;
}

.article-card,
.query-card {
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 14px;
  background: #fff;
}

.article-id {
  display: inline-block;
  margin-bottom: 6px;
  color: var(--accent);
  font-weight: 800;
}

.meta,
.why {
  color: var(--muted);
}

.query-fields {
  display: grid;
  grid-template-columns: minmax(180px, 1fr) 160px;
  gap: 10px;
  margin-bottom: 12px;
}

.query-fields .wide {
  grid-column: 1 / -1;
}

.relevance-list {
  display: grid;
  gap: 8px;
}

.relevance-row {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) auto;
  gap: 10px;
  align-items: center;
  border-top: 1px solid var(--line);
  padding-top: 8px;
}

.button-group {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.warning-list {
  margin: 0 0 12px;
  color: var(--negative);
}

.json-preview {
  max-height: 360px;
  overflow: auto;
  padding: 12px;
  background: #111827;
  color: #f8fafc;
  border-radius: 8px;
  font-size: 12px;
}

@media (max-width: 900px) {
  .page-header,
  .layout {
    display: block;
  }

  .panel {
    margin-bottom: 14px;
  }

  .query-fields,
  .relevance-row {
    display: block;
  }
}
```

Expected: the page has a restrained documentation-tool layout, not a landing page.

- [ ] **Step 2: Add control styles**

Add styles for inputs, selects, buttons, active relevance states, and warnings:

```css
button {
  border: 1px solid var(--line);
  border-radius: 6px;
  background: #fff;
  color: var(--ink);
  padding: 8px 11px;
  cursor: pointer;
}

button.primary {
  border-color: var(--accent);
  background: var(--accent);
  color: #fff;
}

button.is-strong {
  border-color: var(--strong);
  color: var(--strong);
}

button.is-acceptable {
  border-color: var(--acceptable);
  color: var(--acceptable);
}

button.is-negative {
  border-color: var(--negative);
  color: var(--negative);
}

button[aria-pressed="true"].is-strong {
  background: #e8f5ee;
}

button[aria-pressed="true"].is-acceptable {
  background: #fff3d8;
}

button[aria-pressed="true"].is-negative {
  background: #fdeaea;
}
```

Expected: selected relevance buttons are visible without using loud colors.

### Task 4: Implement State Model And Rendering

**Files:**
- Modify: `docs/search-evaluation/labeling.html`

- [ ] **Step 1: Add state helpers**

Inside the main `<script>` block, add these functions:

```javascript
function readEmbeddedJson(id) {
  const element = document.getElementById(id);
  if (!element) {
    throw new Error(`${id} block is missing`);
  }
  return JSON.parse(element.textContent);
}

function createInitialState() {
  const catalog = readEmbeddedJson('seed-catalog');
  const labels = readEmbeddedJson('starter-labels');
  return {
    catalog,
    queries: labels.queries,
    filters: {
      text: '',
      category: 'ALL',
      labeledOnly: false,
      unlabeledOnly: false
    },
    warnings: []
  };
}

let state = createInitialState();
```

Expected: state starts from embedded seed data.

- [ ] **Step 2: Add label lookup helpers**

Add:

```javascript
function findLabel(query, articleId) {
  return query.labels.find((label) => label.articleId === articleId);
}

function setArticleRelevance(queryIndex, articleId, relevance) {
  const query = state.queries[queryIndex];
  query.labels = query.labels.filter((label) => label.articleId !== articleId);

  if (relevance !== 'unlabeled') {
    query.labels.push({
      articleId,
      relevance,
      note: ''
    });
  }

  persistDraft();
  render();
}

function updateQueryField(queryIndex, field, value) {
  state.queries[queryIndex] = {
    ...state.queries[queryIndex],
    [field]: value
  };
  persistDraft();
  renderPreviewAndWarnings();
}
```

Expected: query fields and relevance values can be changed through state only.

- [ ] **Step 3: Render guide and catalog**

Add:

```javascript
function renderGuide() {
  document.getElementById('guide-panel').innerHTML = `
    <div class="panel-header">
      <h2>라벨 기준</h2>
      <p>query 의도를 기준으로 article의 관련도를 고릅니다.</p>
    </div>
    <div class="guide-grid">
      <div><strong>Strong</strong><p>반드시 상위에 나와야 하는 핵심 정답입니다.</p></div>
      <div><strong>Acceptable</strong><p>직접 정답은 아니지만 같이 읽으면 도움이 됩니다.</p></div>
      <div><strong>Not relevant</strong><p>헷갈릴 수 있어 명시적으로 관련 없다고 표시합니다.</p></div>
      <div><strong>Unlabeled</strong><p>기본값입니다. reviewed query에서는 관련 없음으로 계산합니다.</p></div>
    </div>
  `;
}

function renderCatalog() {
  const articles = getFilteredArticles();
  document.getElementById('catalog-panel').innerHTML = `
    <div class="panel-header">
      <h2>Article Catalog</h2>
      <p>${state.catalog.catalogId} · ${state.catalog.articles.length} articles</p>
    </div>
    <div class="article-grid">
      ${articles.map(renderArticleCard).join('')}
    </div>
  `;
}

function renderArticleCard(article) {
  return `
    <article class="article-card">
      <div class="article-id">#${article.id}</div>
      <h3>${escapeHtml(article.title)}</h3>
      <p class="meta">${escapeHtml(article.category)} · ${article.topics.map(escapeHtml).join(', ')}</p>
      <p>${escapeHtml(article.summaryKo)}</p>
      ${article.whyItMattersKo ? `<p class="why">${escapeHtml(article.whyItMattersKo)}</p>` : ''}
    </article>
  `;
}

function getFilteredArticles(query = null) {
  return state.catalog.articles;
}
```

Expected: guide and article catalog render without user interaction. Task 6 will replace `getFilteredArticles()` with the full filter implementation.

- [ ] **Step 4: Add HTML escaping**

Add:

```javascript
function escapeHtml(value) {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');
}
```

Expected: imported catalog or label text does not break the page HTML.

### Task 5: Implement Query Cards And Relevance Controls

**Files:**
- Modify: `docs/search-evaluation/labeling.html`

- [ ] **Step 1: Render workspace controls**

Add:

```javascript
function renderWorkspace() {
  document.getElementById('workspace-panel').innerHTML = `
    <div class="panel-header">
      <div>
        <h2>Query Labeling</h2>
        <p>${state.queries.length} queries · reviewed ${state.queries.filter((query) => query.status === 'reviewed').length}</p>
      </div>
      <button type="button" id="add-query-button" class="primary">Query 추가</button>
    </div>
    <div class="query-list">
      ${state.queries.map((query, index) => renderQueryCard(query, index)).join('')}
    </div>
  `;

  document.getElementById('add-query-button').addEventListener('click', addQuery);
  bindQueryEvents();
}
```

- [ ] **Step 2: Render each query card**

Add:

```javascript
function renderQueryCard(query, queryIndex) {
  return `
    <article class="query-card" data-query-index="${queryIndex}">
      <div class="query-fields">
        <label>
          Query
          <input type="text" data-field="query" value="${escapeHtml(query.query)}">
        </label>
        <label>
          상태
          <select data-field="status">
            ${renderStatusOption(query.status, 'needs_user_label', '라벨 필요')}
            ${renderStatusOption(query.status, 'needs_review', '검토 필요')}
            ${renderStatusOption(query.status, 'reviewed', '검토 완료')}
          </select>
        </label>
        <label class="wide">
          검색 의도
          <textarea data-field="intent" rows="2">${escapeHtml(query.intent)}</textarea>
        </label>
        <label class="wide">
          메모
          <textarea data-field="memo" rows="2">${escapeHtml(query.memo)}</textarea>
        </label>
      </div>
      <div class="relevance-list">
        ${getFilteredArticles(query).map((article) => renderRelevanceRow(query, queryIndex, article)).join('')}
      </div>
      <button type="button" class="danger remove-query-button">Query 삭제</button>
    </article>
  `;
}

function renderStatusOption(current, value, label) {
  return `<option value="${value}" ${current === value ? 'selected' : ''}>${label}</option>`;
}
```

- [ ] **Step 3: Render relevance buttons**

Add:

```javascript
function renderRelevanceRow(query, queryIndex, article) {
  const label = findLabel(query, article.id);
  const relevance = label ? label.relevance : 'unlabeled';
  return `
    <div class="relevance-row">
      <div>
        <strong>#${article.id}</strong>
        <span>${escapeHtml(article.title)}</span>
      </div>
      <div class="button-group" role="group" aria-label="article ${article.id} relevance">
        ${renderRelevanceButton(queryIndex, article.id, relevance, 'strong', 'Strong', 'is-strong')}
        ${renderRelevanceButton(queryIndex, article.id, relevance, 'acceptable', 'Acceptable', 'is-acceptable')}
        ${renderRelevanceButton(queryIndex, article.id, relevance, 'not_relevant', 'Not relevant', 'is-negative')}
        ${renderRelevanceButton(queryIndex, article.id, relevance, 'unlabeled', 'Clear', '')}
      </div>
    </div>
  `;
}

function renderRelevanceButton(queryIndex, articleId, current, value, label, className) {
  return `
    <button
      type="button"
      class="relevance-button ${className}"
      data-query-index="${queryIndex}"
      data-article-id="${articleId}"
      data-relevance="${value}"
      aria-pressed="${current === value}"
    >${label}</button>
  `;
}
```

- [ ] **Step 4: Bind query and relevance events**

Add:

```javascript
function bindQueryEvents() {
  document.querySelectorAll('.query-card').forEach((card) => {
    const queryIndex = Number(card.dataset.queryIndex);

    card.querySelectorAll('[data-field]').forEach((field) => {
      field.addEventListener('input', (event) => {
        updateQueryField(queryIndex, event.target.dataset.field, event.target.value);
      });
      field.addEventListener('change', (event) => {
        updateQueryField(queryIndex, event.target.dataset.field, event.target.value);
      });
    });

    card.querySelector('.remove-query-button').addEventListener('click', () => {
      removeQuery(queryIndex);
    });
  });

  document.querySelectorAll('.relevance-button').forEach((button) => {
    button.addEventListener('click', () => {
      setArticleRelevance(
        Number(button.dataset.queryIndex),
        Number(button.dataset.articleId),
        button.dataset.relevance
      );
    });
  });
}
```

- [ ] **Step 5: Add query add/remove functions**

Add:

```javascript
function addQuery() {
  state.queries.push({
    query: '',
    intent: '',
    status: 'needs_user_label',
    memo: '',
    labels: []
  });
  persistDraft();
  render();
}

function removeQuery(queryIndex) {
  state.queries.splice(queryIndex, 1);
  persistDraft();
  render();
}
```

Expected: query cards can be added, edited, removed, and labeled.

### Task 6: Add Filtering For Larger Catalogs

**Files:**
- Modify: `docs/search-evaluation/labeling.html`

- [ ] **Step 1: Add filter controls to the catalog panel**

Replace `renderCatalog()` with:

```javascript
function renderCatalog() {
  const articles = getFilteredArticles();
  const categories = ['ALL', ...new Set(state.catalog.articles.map((article) => article.category))];
  document.getElementById('catalog-panel').innerHTML = `
    <div class="panel-header">
      <h2>Article Catalog</h2>
      <p>${state.catalog.catalogId} · ${state.catalog.articles.length} articles · showing ${articles.length}</p>
    </div>
    <div class="filters">
      <input type="search" id="article-filter-text" placeholder="title, category, topic 검색" value="${escapeHtml(state.filters.text)}">
      <select id="article-filter-category">
        ${categories.map((category) => `<option value="${escapeHtml(category)}" ${state.filters.category === category ? 'selected' : ''}>${escapeHtml(category)}</option>`).join('')}
      </select>
      <label><input type="checkbox" id="filter-labeled-only" ${state.filters.labeledOnly ? 'checked' : ''}> labeled only</label>
      <label><input type="checkbox" id="filter-unlabeled-only" ${state.filters.unlabeledOnly ? 'checked' : ''}> unlabeled only</label>
    </div>
    <div class="article-grid">
      ${articles.map(renderArticleCard).join('')}
    </div>
  `;
  bindCatalogFilters();
}
```

- [ ] **Step 2: Implement article filtering**

Add:

```javascript
function getFilteredArticles(query = null) {
  const text = state.filters.text.trim().toLowerCase();
  return state.catalog.articles.filter((article) => {
    const matchesText = !text || [
      article.title,
      article.category,
      article.summaryKo,
      ...(article.topics || [])
    ].join(' ').toLowerCase().includes(text);
    const matchesCategory = state.filters.category === 'ALL' || article.category === state.filters.category;
    const hasAnyLabel = query
      ? Boolean(findLabel(query, article.id))
      : state.queries.some((existingQuery) => findLabel(existingQuery, article.id));
    const matchesLabeled = !state.filters.labeledOnly || hasAnyLabel;
    const matchesUnlabeled = !state.filters.unlabeledOnly || !hasAnyLabel;
    return matchesText && matchesCategory && matchesLabeled && matchesUnlabeled;
  });
}
```

- [ ] **Step 3: Bind filter events**

Add after catalog render:

```javascript
function bindCatalogFilters() {
  document.getElementById('article-filter-text').addEventListener('input', (event) => {
    state.filters.text = event.target.value;
    renderCatalog();
    renderWorkspace();
  });
  document.getElementById('article-filter-category').addEventListener('change', (event) => {
    state.filters.category = event.target.value;
    renderCatalog();
    renderWorkspace();
  });
  document.getElementById('filter-labeled-only').addEventListener('change', (event) => {
    state.filters.labeledOnly = event.target.checked;
    if (event.target.checked) state.filters.unlabeledOnly = false;
    renderCatalog();
    renderWorkspace();
  });
  document.getElementById('filter-unlabeled-only').addEventListener('change', (event) => {
    state.filters.unlabeledOnly = event.target.checked;
    if (event.target.checked) state.filters.labeledOnly = false;
    renderCatalog();
    renderWorkspace();
  });
}
```

Expected: catalog browsing and query-card labeling both stay usable when the article count grows. Text/category filters apply globally. `labeled only` and `unlabeled only` are global in the catalog panel and query-specific inside each query card.

### Task 7: Add Draft Persistence

**Files:**
- Modify: `docs/search-evaluation/labeling.html`

- [ ] **Step 1: Add persistence helpers**

Add:

```javascript
function persistDraft() {
  const draft = {
    catalog: state.catalog,
    queries: state.queries,
    savedAt: new Date().toISOString()
  };
  localStorage.setItem(STORAGE_KEY, JSON.stringify(draft));
}

function loadDraft() {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) return false;
  try {
    const draft = JSON.parse(raw);
    if (!draft.catalog || !Array.isArray(draft.queries)) return false;
    state.catalog = draft.catalog;
    state.queries = draft.queries;
    return true;
  } catch (error) {
    console.warn('Failed to load labeling draft', error);
    return false;
  }
}

function clearDraft() {
  localStorage.removeItem(STORAGE_KEY);
  state = createInitialState();
  render();
}
```

- [ ] **Step 2: Add reset draft button**

Add a `button` in the header:

```html
<button type="button" id="reset-draft-button">초기화</button>
```

Bind it:

```javascript
document.getElementById('reset-draft-button').addEventListener('click', () => {
  if (confirm('브라우저에 저장된 draft를 지우고 seed 상태로 되돌릴까요?')) {
    clearDraft();
  }
});
```

- [ ] **Step 3: Load draft during initialization**

At the bottom of the script, initialize with:

```javascript
loadDraft();
render();
```

Expected: after editing labels and refreshing the browser, the draft is restored.

### Task 8: Add Validation, Preview, And Export

**Files:**
- Modify: `docs/search-evaluation/labeling.html`

- [ ] **Step 1: Build export payload**

Add:

```javascript
function buildExportPayload() {
  return {
    version: 1,
    catalogId: state.catalog.catalogId,
    catalogArticleCount: state.catalog.articles.length,
    exportedAt: new Date().toISOString(),
    queries: state.queries.map((query) => ({
      query: query.query.trim(),
      intent: query.intent.trim(),
      status: query.status,
      memo: query.memo.trim(),
      labels: query.labels
        .filter((label) => label.relevance !== 'unlabeled')
        .map((label) => ({
          articleId: label.articleId,
          relevance: label.relevance,
          note: label.note || ''
        }))
    }))
  };
}
```

- [ ] **Step 2: Add validation**

Add:

```javascript
function validateState(payload = buildExportPayload()) {
  const warnings = [];
  const articleIds = new Set(state.catalog.articles.map((article) => article.id));
  const seenQueries = new Set();

  payload.queries.forEach((query, index) => {
    const displayIndex = index + 1;
    if (!query.query) {
      warnings.push(`Query ${displayIndex}: query text가 비어 있습니다.`);
    }
    const normalized = query.query.toLowerCase();
    if (normalized && seenQueries.has(normalized)) {
      warnings.push(`Query ${displayIndex}: 같은 query text가 이미 있습니다.`);
    }
    if (normalized) {
      seenQueries.add(normalized);
    }
    const hasUseful = query.labels.some((label) => label.relevance === 'strong' || label.relevance === 'acceptable');
    if (query.status === 'reviewed' && !hasUseful) {
      warnings.push(`Query ${displayIndex}: reviewed 상태지만 strong 또는 acceptable label이 없습니다.`);
    }
    query.labels.forEach((label) => {
      if (!articleIds.has(label.articleId)) {
        warnings.push(`Query ${displayIndex}: article ${label.articleId}는 현재 catalog에 없습니다.`);
      }
    });
  });

  if (state.catalog.articles.length === 0) {
    warnings.push('Catalog에 article이 없습니다.');
  }

  state.warnings = warnings;
  return warnings;
}
```

- [ ] **Step 3: Render preview and warnings**

Add:

```javascript
function renderPreviewAndWarnings() {
  const payload = buildExportPayload();
  const warnings = validateState(payload);
  document.getElementById('json-panel').innerHTML = `
    <div class="panel-header">
      <h2>JSON Preview</h2>
      <p>${warnings.length} warnings</p>
    </div>
    ${warnings.length ? `<ul class="warning-list">${warnings.map((warning) => `<li>${escapeHtml(warning)}</li>`).join('')}</ul>` : '<p>검증 warning이 없습니다.</p>'}
    <pre class="json-preview">${escapeHtml(JSON.stringify(payload, null, 2))}</pre>
  `;
}
```

- [ ] **Step 4: Add export download**

Add:

```javascript
function exportLabels() {
  const payload = buildExportPayload();
  validateState(payload);
  const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = `search-labels.${state.catalog.catalogId}.${new Date().toISOString().slice(0, 10)}.json`;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

document.getElementById('export-button').addEventListener('click', exportLabels);
```

Expected: clicking export downloads a valid labels JSON file.

### Task 9: Add Label And Catalog JSON Import

**Files:**
- Modify: `docs/search-evaluation/labeling.html`

- [ ] **Step 1: Add file reading helper**

Add:

```javascript
function readJsonFile(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      try {
        resolve(JSON.parse(String(reader.result)));
      } catch (error) {
        reject(new Error('JSON 파일을 파싱할 수 없습니다.'));
      }
    };
    reader.onerror = () => reject(new Error('파일을 읽을 수 없습니다.'));
    reader.readAsText(file);
  });
}
```

- [ ] **Step 2: Validate catalog import**

Add:

```javascript
function normalizeCatalog(catalog) {
  if (catalog.version !== 1) {
    throw new Error('지원하지 않는 catalog version입니다.');
  }
  if (!catalog.catalogId || !Array.isArray(catalog.articles) || catalog.articles.length === 0) {
    throw new Error('catalogId와 articles가 필요합니다.');
  }
  catalog.articles.forEach((article) => {
    if (typeof article.id !== 'number' || !article.title || !article.category || !Array.isArray(article.topics) || !article.summaryKo) {
      throw new Error('article에는 id, title, category, topics, summaryKo가 필요합니다.');
    }
  });
  return catalog;
}
```

- [ ] **Step 3: Validate label import**

Add:

```javascript
function normalizeLabels(labels) {
  if (labels.version !== 1) {
    throw new Error('지원하지 않는 label version입니다.');
  }
  if (!labels.catalogId || !Array.isArray(labels.queries)) {
    throw new Error('catalogId와 queries가 필요합니다.');
  }
  const allowedStatuses = new Set(['needs_user_label', 'needs_review', 'reviewed']);
  const allowedRelevance = new Set(['strong', 'acceptable', 'not_relevant']);
  return labels.queries.map((query) => ({
    query: query.query || '',
    intent: query.intent || '',
    status: allowedStatuses.has(query.status) ? query.status : 'needs_user_label',
    memo: query.memo || '',
    labels: Array.isArray(query.labels)
      ? query.labels
          .filter((label) => typeof label.articleId === 'number' && allowedRelevance.has(label.relevance))
          .map((label) => ({
            articleId: label.articleId,
            relevance: label.relevance,
            note: label.note || ''
          }))
      : []
  }));
}
```

- [ ] **Step 4: Bind import buttons**

Add:

```javascript
document.getElementById('import-labels-button').addEventListener('click', () => {
  document.getElementById('labels-file-input').click();
});

document.getElementById('import-catalog-button').addEventListener('click', () => {
  document.getElementById('catalog-file-input').click();
});

document.getElementById('labels-file-input').addEventListener('change', async (event) => {
  const file = event.target.files[0];
  if (!file) return;
  try {
    const labels = await readJsonFile(file);
    if (labels.catalogId !== state.catalog.catalogId) {
      alert(`catalogId가 다릅니다. 현재=${state.catalog.catalogId}, 파일=${labels.catalogId}`);
    }
    state.queries = normalizeLabels(labels);
    persistDraft();
    render();
  } catch (error) {
    alert(error.message);
  } finally {
    event.target.value = '';
  }
});

document.getElementById('catalog-file-input').addEventListener('change', async (event) => {
  const file = event.target.files[0];
  if (!file) return;
  try {
    state.catalog = normalizeCatalog(await readJsonFile(file));
    persistDraft();
    render();
  } catch (error) {
    alert(error.message);
  } finally {
    event.target.value = '';
  }
});
```

Expected: label JSON and catalog JSON can be imported without a server.

### Task 10: Wire Final Render Function

**Files:**
- Modify: `docs/search-evaluation/labeling.html`

- [ ] **Step 1: Add render orchestration**

Add:

```javascript
function render() {
  renderGuide();
  renderCatalog();
  renderWorkspace();
  renderPreviewAndWarnings();
}
```

- [ ] **Step 2: Ensure event bindings happen after DOM exists**

Place all top-level event listener registrations after helper function declarations and before:

```javascript
loadDraft();
render();
```

Expected: page renders and all buttons respond after initial load.

- [ ] **Step 3: Run static JSON and JavaScript syntax checks**

Run:

```bash
node - <<'NODE'
const fs = require('fs');
const childProcess = require('child_process');
const html = fs.readFileSync('docs/search-evaluation/labeling.html', 'utf8');
for (const id of ['seed-catalog', 'starter-labels']) {
  const match = html.match(new RegExp(`<script id="${id}" type="application/json">([\\s\\S]*?)</script>`));
  if (!match) throw new Error(`${id} script block missing`);
  JSON.parse(match[1]);
  console.log(`${id}: ok`);
}
const browserScripts = [...html.matchAll(/<script(?![^>]*application\\/json)[^>]*>([\\s\\S]*?)<\\/script>/g)]
  .map((match) => match[1])
  .join('\\n');
fs.writeFileSync('/tmp/sigak-search-labeling.js', browserScripts);
childProcess.execFileSync(process.execPath, ['--check', '/tmp/sigak-search-labeling.js'], { stdio: 'inherit' });
if (!html.includes('function render()')) throw new Error('render function missing');
if (!html.includes('function exportLabels()')) throw new Error('exportLabels function missing');
if (!html.includes('function normalizeCatalog(')) throw new Error('normalizeCatalog function missing');
console.log('static checks: ok');
NODE
```

Expected:

```text
seed-catalog: ok
starter-labels: ok
static checks: ok
```

### Task 11: Update Evaluation Guide

**Files:**
- Modify: `docs/search-evaluation/queries.md`

- [ ] **Step 1: Add a short HTML tool note near the top**

Add a section after the introduction:

````markdown
## HTML 라벨링 도구

Markdown 표는 기준을 설명하기 위한 문서이고, 실제 입력은 정적 HTML 도구를 우선 사용합니다.

```text
docs/search-evaluation/labeling.html
```

권장 흐름:

```text
labeling.html 열기
-> article catalog 확인
-> query별 label 작성
-> labels JSON 다운로드
-> benchmark runner에서 JSON 사용
```

브라우저 작업 중에는 `localStorage` draft가 저장되지만, 최종 보관은 export한 JSON 파일을 기준으로 합니다.
````

Expected: `queries.md` remains the guide, while `labeling.html` is the input tool.

### Task 12: Browser And Export Verification

**Files:**
- No source file modifications expected in this task.

**Manual verification note (2026-06-01):** 사용자가 실제 브라우저에서 `labeling.html`을 열어 동작을 확인했고 정상 동작한다고 보고했다. Codex in-app browser 자동화는 local `file://` 접근 정책으로 차단되어 screenshot, click automation, downloaded JSON parse는 Codex가 직접 재현하지 못했다.

- [ ] **Step 1: Open the static file in a browser**

Open:

```text
file:///Users/yonghyun/my-projects/sigak/docs/search-evaluation/labeling.html
```

Expected:

- Header displays `검색 평가 라벨링`.
- Article catalog shows 5 seed articles.
- Starter queries are visible.

- [ ] **Step 2: Verify edit and draft restore**

Manual steps:

1. Add a new query with text `production ai risk`.
2. Set intent to `production AI 운영 위험을 찾는다.`
3. Mark article `3` as `Strong`.
4. Mark article `1` as `Acceptable`.
5. Refresh the page.

Expected: the new query and labels remain visible after refresh.

- [ ] **Step 3: Verify JSON export**

Manual steps:

1. Click `라벨 JSON 다운로드`.
2. Save the downloaded JSON locally.
3. Move the downloaded file to a deterministic local path:

```bash
mv "$HOME/Downloads/search-labels.seed-v1.2026-06-01.json" /tmp/search-labels.seed-v1.2026-06-01.json
```

4. Parse it with Node:

```bash
node -e "const fs=require('fs'); const data=JSON.parse(fs.readFileSync('/tmp/search-labels.seed-v1.2026-06-01.json','utf8')); console.log(data.catalogId, data.queries.length)"
```

Expected: output starts with `seed-v1` and query count is at least `4`.

- [ ] **Step 4: Verify import restore**

Manual steps:

1. Click `초기화`.
2. Import the downloaded labels JSON.

Expected: the exported query and labels are restored.

### Task 13: Documentation And Session Record

**Files:**
- Modify: `docs/STATUS.md`
- Modify: `docs/STATUS.ko.md`
- Modify: `docs/ROADMAP.md`
- Modify: `docs/ROADMAP.ko.md`
- Create/modify: `docs/blog/2026-06-01-dev-log.md`
- Modify: `docs/blog/topic-queue.md`

- [ ] **Step 1: Update status documents**

Update `docs/STATUS.md` and `docs/STATUS.ko.md` to mention:

- A static HTML labeling tool exists for retrieval benchmark preparation.
- It exports labels JSON.
- It still uses seed catalog until a larger frozen catalog is exported.

- [ ] **Step 2: Update roadmap documents**

Update `docs/ROADMAP.md` and `docs/ROADMAP.ko.md` current next work to reflect:

- Labeling UI is prepared.
- Next benchmark step is catalog export from collected/API-ready articles and human labeling.
- Benchmark runner is still pending.

- [ ] **Step 3: Write June 1 dev-log entry**

Create or update `docs/blog/2026-06-01-dev-log.md` with:

- why Markdown was not enough for labeling
- why static HTML was chosen over React/DB-backed UI
- why `unlabeled` defaults to not relevant for reviewed queries
- what was verified
- what remains unverified

- [ ] **Step 4: Update topic queue**

Add or update a candidate topic:

```markdown
## [candidate] 검색 평가 라벨링을 Markdown에서 정적 HTML로 옮긴 이유

- 관련 작업: `docs/search-evaluation/labeling.html`
- 핵심 질문:
  - 검색 품질 평가는 왜 사람이 만든 relevance label에서 시작해야 하는가?
  - 작은 포트폴리오 프로젝트에서 DB-backed labeling UI 대신 정적 HTML을 선택한 이유는 무엇인가?
  - `unlabeled`를 기본값으로 두면 라벨링 부담과 benchmark 의미가 어떻게 달라지는가?
- 상태: candidate, 실제 라벨링과 benchmark runner 구현 후 ready-to-write 검토
```

### Task 14: Final Verification

**Files:**
- No new source changes expected beyond prior tasks.

- [ ] **Step 1: Run whitespace check**

Run:

```bash
git diff --check
```

Expected: no output and exit code `0`.

- [ ] **Step 2: Run static embedded JSON and JavaScript syntax check**

Run:

```bash
node - <<'NODE'
const fs = require('fs');
const childProcess = require('child_process');
const html = fs.readFileSync('docs/search-evaluation/labeling.html', 'utf8');
for (const id of ['seed-catalog', 'starter-labels']) {
  const match = html.match(new RegExp(`<script id="${id}" type="application/json">([\\s\\S]*?)</script>`));
  if (!match) throw new Error(`${id} script block missing`);
  JSON.parse(match[1]);
}
const browserScripts = [...html.matchAll(/<script(?![^>]*application\\/json)[^>]*>([\\s\\S]*?)<\\/script>/g)]
  .map((match) => match[1])
  .join('\\n');
fs.writeFileSync('/tmp/sigak-search-labeling.js', browserScripts);
childProcess.execFileSync(process.execPath, ['--check', '/tmp/sigak-search-labeling.js'], { stdio: 'inherit' });
console.log('embedded JSON and JS syntax ok');
NODE
```

Expected:

```text
embedded JSON and JS syntax ok
```

- [ ] **Step 3: Run final status check**

Run:

```bash
git status --short
```

Expected: the new HTML, plan, spec, guide, status, roadmap, dev-log, and topic queue changes are visible. No generated download file should be staged or committed.

- [ ] **Step 4: Report verification**

Report:

- Browser/static HTML manual checks performed or left unverified.
- `git diff --check` result.
- Embedded JSON parse and JavaScript syntax check result.
- Backend/frontend/AI tests not run because this plan changes docs/static evaluation tooling only.
