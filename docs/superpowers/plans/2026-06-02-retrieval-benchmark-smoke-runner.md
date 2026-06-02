# Retrieval Benchmark Smoke Runner Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 사람이 검토한 label JSON을 기준으로 public article search API 결과를 평가하고 `run`, `metrics`, `report` artifact를 생성하는 smoke benchmark runner를 만든다.

**Architecture:** 제품 백엔드에 평가 로직을 넣지 않고 `experiments/scripts/` 아래 Node.js ESM script로 분리한다. Label parsing, metric calculation, API client, runner orchestration, report writing을 작은 모듈로 나눠 백엔드가 꺼져 있어도 핵심 계산을 단위 테스트할 수 있게 한다.

**Tech Stack:** Node.js ESM, Node built-in `node:test`, built-in `fetch`, `fs/promises`, public Spring Boot API `GET /api/articles?query=...`.

**Completion note (2026-06-02):** Subagent-driven implementation, spec review, code quality review, unit tests, and local smoke finished. Node test run passed 30/30. Local smoke used deterministic embedding mode, rebuilt Elasticsearch and Qdrant projections with 6 articles, then generated `experiments/results/retrieval/latest/report.md` for 3 reviewed queries.

---

## Reference Spec

- `docs/superpowers/specs/2026-06-02-retrieval-benchmark-smoke-runner-design.md`
- `experiments/README.md`
- `docs/search-evaluation/queries.md`
- `experiments/datasets/labels/search-labels.api-ready-2026-06-02.2026-06-02.json`
- `docs/CODING_CONVENTIONS.md`

## File Structure

Create:

- `experiments/scripts/retrieval-benchmark.mjs`
  - CLI entrypoint. Parses arguments, runs the benchmark, prints artifact paths.
- `experiments/scripts/retrieval-benchmark/cli.mjs`
  - Parses `--labels`, `--base-url`, `--output-dir`, and optional `--k`.
- `experiments/scripts/retrieval-benchmark/labels.mjs`
  - Reads and validates label JSON. Returns only `reviewed` queries with at least one positive label.
- `experiments/scripts/retrieval-benchmark/metrics.mjs`
  - Calculates Top1 Strong Hit, Recall@k, MRR@k, and summary averages.
- `experiments/scripts/retrieval-benchmark/search-client.mjs`
  - Calls public article search API and extracts ranked article IDs plus latency.
- `experiments/scripts/retrieval-benchmark/report-writer.mjs`
  - Writes `runs.hybrid.json`, `metrics.by-query.json`, `metrics.summary.json`, and `report.md`.
- `experiments/scripts/retrieval-benchmark/runner.mjs`
  - Orchestrates labels -> public API calls -> metrics -> artifacts.
- `experiments/scripts/retrieval-benchmark/cli.test.mjs`
- `experiments/scripts/retrieval-benchmark/labels.test.mjs`
- `experiments/scripts/retrieval-benchmark/metrics.test.mjs`
- `experiments/scripts/retrieval-benchmark/search-client.test.mjs`
- `experiments/scripts/retrieval-benchmark/report-writer.test.mjs`
- `experiments/scripts/retrieval-benchmark/runner.test.mjs`

Modify:

- `experiments/README.md`
  - Add benchmark runner command, output files, and backend prerequisite.
- `docs/search-evaluation/queries.md`
  - Add Korean guide for running the benchmark after downloading label JSON.
- `docs/STATUS.md`
- `docs/STATUS.ko.md`
- `docs/ROADMAP.md`
- `docs/ROADMAP.ko.md`
- `docs/blog/2026-06-02-dev-log.md`
- `docs/blog/topic-queue.md`

Important note:

- Do not commit during implementation unless the user explicitly asks for commit/push.
- Do not modify `.gitignore`; it already has an unrelated user change in the working tree.
- Keep this first runner limited to the current public search API mode. Do not add keyword-only/vector-only comparison in this plan.
- Do not add external Node packages. Use built-in Node.js APIs.

## Output Contract

The runner command:

```bash
node experiments/scripts/retrieval-benchmark.mjs \
  --labels=experiments/datasets/labels/search-labels.api-ready-2026-06-02.2026-06-02.json \
  --base-url=http://localhost:8080 \
  --output-dir=experiments/results/retrieval/latest
```

Generated files:

```text
experiments/results/retrieval/latest/
├── runs.hybrid.json
├── metrics.summary.json
├── metrics.by-query.json
└── report.md
```

## Task 1: Preflight And Context Check

**Files:**
- Read: `AGENTS.md`
- Read: `docs/STATUS.md`
- Read: `docs/ROADMAP.md`
- Read: `docs/superpowers/specs/2026-06-02-retrieval-benchmark-smoke-runner-design.md`
- Read: `docs/CODING_CONVENTIONS.md`
- Read: `experiments/datasets/labels/search-labels.api-ready-2026-06-02.2026-06-02.json`

- [ ] **Step 1: Confirm working tree before editing**

Run:

```bash
git status --short --branch
```

Expected:

```text
## codex/collection-projection-demo-flow...origin/codex/collection-projection-demo-flow
 M .gitignore
?? docs/superpowers/plans/2026-06-02-retrieval-benchmark-smoke-runner.md
?? docs/superpowers/specs/2026-06-02-retrieval-benchmark-smoke-runner-design.md
```

If `.gitignore` is still modified, leave it alone. If the spec/plan files are already tracked by the time execution starts, that is also valid.

- [ ] **Step 2: Re-read the benchmark design**

Run:

```bash
sed -n '1,420p' docs/superpowers/specs/2026-06-02-retrieval-benchmark-smoke-runner-design.md
```

Expected:

- First version evaluates only the current public API search result.
- Metrics are Top1 Strong Hit, Recall@5, MRR@5, and LatencyMs.
- Label JSON `strong` and `acceptable` are positive for Recall.
- MRR uses only `strong`.

- [ ] **Step 3: Confirm the existing label JSON shape**

Run:

```bash
sed -n '1,220p' experiments/datasets/labels/search-labels.api-ready-2026-06-02.2026-06-02.json
```

Expected:

- Top-level `version` is `1`.
- Top-level `catalogId` exists.
- `queries` contains `status`, `query`, `intent`, and `labels`.
- Label relevance values are `strong`, `acceptable`, or `not_relevant`.

## Task 2: CLI Argument Parser

**Files:**
- Create: `experiments/scripts/retrieval-benchmark/cli.mjs`
- Test: `experiments/scripts/retrieval-benchmark/cli.test.mjs`

- [ ] **Step 1: Write the failing CLI parser tests**

Create `experiments/scripts/retrieval-benchmark/cli.test.mjs`:

```js
import test from 'node:test';
import assert from 'node:assert/strict';
import { parseBenchmarkArgs } from './cli.mjs';

test('parseBenchmarkArgs reads required options and defaults k to 5', () => {
  const args = parseBenchmarkArgs([
    '--labels=experiments/datasets/labels/sample.json',
    '--base-url=http://localhost:8080',
    '--output-dir=experiments/results/retrieval/latest',
  ]);

  assert.deepEqual(args, {
    labelsPath: 'experiments/datasets/labels/sample.json',
    baseUrl: 'http://localhost:8080',
    outputDir: 'experiments/results/retrieval/latest',
    k: 5,
  });
});

test('parseBenchmarkArgs reads explicit k', () => {
  const args = parseBenchmarkArgs([
    '--labels=labels.json',
    '--base-url=http://localhost:8080',
    '--output-dir=out',
    '--k=3',
  ]);

  assert.equal(args.k, 3);
});

test('parseBenchmarkArgs rejects missing required options', () => {
  assert.throws(
    () => parseBenchmarkArgs(['--labels=labels.json', '--base-url=http://localhost:8080']),
    /Missing required option: --output-dir/
  );
});

test('parseBenchmarkArgs rejects blank option values', () => {
  assert.throws(
    () => parseBenchmarkArgs(['--labels= ', '--base-url=http://localhost:8080', '--output-dir=out']),
    /Option --labels must not be blank/
  );
});

test('parseBenchmarkArgs rejects invalid k', () => {
  assert.throws(
    () => parseBenchmarkArgs(['--labels=labels.json', '--base-url=http://localhost:8080', '--output-dir=out', '--k=0']),
    /Option --k must be a positive integer/
  );
});
```

- [ ] **Step 2: Run the CLI parser test and verify it fails**

Run:

```bash
node --test experiments/scripts/retrieval-benchmark/cli.test.mjs
```

Expected:

```text
ERR_MODULE_NOT_FOUND
```

- [ ] **Step 3: Implement the CLI parser**

Create `experiments/scripts/retrieval-benchmark/cli.mjs`:

```js
const REQUIRED_OPTIONS = ['labels', 'base-url', 'output-dir'];

export function parseBenchmarkArgs(argv) {
  const options = new Map();

  for (const arg of argv) {
    if (!arg.startsWith('--') || !arg.includes('=')) {
      throw new Error(`Invalid option format: ${arg}`);
    }

    const [rawName, ...rawValueParts] = arg.slice(2).split('=');
    const value = rawValueParts.join('=').trim();

    if (value.length === 0) {
      throw new Error(`Option --${rawName} must not be blank.`);
    }

    options.set(rawName, value);
  }

  for (const requiredOption of REQUIRED_OPTIONS) {
    if (!options.has(requiredOption)) {
      throw new Error(`Missing required option: --${requiredOption}`);
    }
  }

  const k = options.has('k') ? Number(options.get('k')) : 5;

  if (!Number.isInteger(k) || k < 1) {
    throw new Error('Option --k must be a positive integer.');
  }

  return {
    labelsPath: options.get('labels'),
    baseUrl: options.get('base-url'),
    outputDir: options.get('output-dir'),
    k,
  };
}
```

- [ ] **Step 4: Run the CLI parser test and verify it passes**

Run:

```bash
node --test experiments/scripts/retrieval-benchmark/cli.test.mjs
```

Expected:

```text
# pass 5
```

## Task 3: Label Parser And Validation

**Files:**
- Create: `experiments/scripts/retrieval-benchmark/labels.mjs`
- Test: `experiments/scripts/retrieval-benchmark/labels.test.mjs`

- [ ] **Step 1: Write the failing label parser tests**

Create `experiments/scripts/retrieval-benchmark/labels.test.mjs`:

```js
import test from 'node:test';
import assert from 'node:assert/strict';
import { parseLabelDocument } from './labels.mjs';

const baseDocument = {
  version: 1,
  catalogId: 'api-ready-test',
  catalogArticleCount: 6,
  queries: [
    {
      query: 'agent evaluation',
      intent: 'LLM agent 평가를 찾는다.',
      status: 'reviewed',
      labels: [
        { articleId: 1, relevance: 'strong', note: 'direct' },
        { articleId: 4, relevance: 'acceptable', note: 'related' },
        { articleId: 5, relevance: 'not_relevant', note: 'off topic' },
      ],
    },
    {
      query: 'draft query',
      intent: '아직 검토하지 않았다.',
      status: 'needs_label',
      labels: [{ articleId: 2, relevance: 'strong', note: '' }],
    },
  ],
};

test('parseLabelDocument returns only reviewed queries with positive labels', () => {
  const parsed = parseLabelDocument(baseDocument);

  assert.equal(parsed.catalogId, 'api-ready-test');
  assert.equal(parsed.catalogArticleCount, 6);
  assert.equal(parsed.queries.length, 1);
  assert.deepEqual(parsed.queries[0], {
    query: 'agent evaluation',
    intent: 'LLM agent 평가를 찾는다.',
    strongArticleIds: [1],
    acceptableArticleIds: [4],
    notRelevantArticleIds: [5],
  });
});

test('parseLabelDocument rejects unsupported version', () => {
  assert.throws(
    () => parseLabelDocument({ ...baseDocument, version: 2 }),
    /Label JSON version must be 1/
  );
});

test('parseLabelDocument rejects when no reviewed query exists', () => {
  assert.throws(
    () => parseLabelDocument({ ...baseDocument, queries: [{ ...baseDocument.queries[1] }] }),
    /At least one reviewed query is required/
  );
});

test('parseLabelDocument rejects reviewed query without a positive label', () => {
  assert.throws(
    () => parseLabelDocument({
      ...baseDocument,
      queries: [{
        query: 'postgres',
        intent: 'vector search',
        status: 'reviewed',
        labels: [{ articleId: 5, relevance: 'not_relevant', note: '' }],
      }],
    }),
    /Reviewed query must contain at least one strong or acceptable label/
  );
});
```

- [ ] **Step 2: Run the label parser test and verify it fails**

Run:

```bash
node --test experiments/scripts/retrieval-benchmark/labels.test.mjs
```

Expected:

```text
ERR_MODULE_NOT_FOUND
```

- [ ] **Step 3: Implement label parsing and file loading**

Create `experiments/scripts/retrieval-benchmark/labels.mjs`:

```js
import { readFile } from 'node:fs/promises';

const VALID_RELEVANCE = new Set(['strong', 'acceptable', 'not_relevant']);

export async function loadLabelFile(labelsPath) {
  const content = await readFile(labelsPath, 'utf8');
  return parseLabelDocument(JSON.parse(content));
}

export function parseLabelDocument(document) {
  if (document?.version !== 1) {
    throw new Error('Label JSON version must be 1.');
  }

  if (!Array.isArray(document.queries)) {
    throw new Error('Label JSON queries must be an array.');
  }

  const reviewedQueries = document.queries
    .filter((query) => query.status === 'reviewed')
    .map(parseReviewedQuery);

  if (reviewedQueries.length === 0) {
    throw new Error('At least one reviewed query is required.');
  }

  return {
    catalogId: requireNonBlankString(document.catalogId, 'catalogId'),
    catalogArticleCount: requirePositiveInteger(document.catalogArticleCount, 'catalogArticleCount'),
    queries: reviewedQueries,
  };
}

function parseReviewedQuery(query) {
  const labels = Array.isArray(query.labels) ? query.labels : [];
  const strongArticleIds = [];
  const acceptableArticleIds = [];
  const notRelevantArticleIds = [];

  for (const label of labels) {
    const articleId = requirePositiveInteger(label.articleId, 'articleId');
    const relevance = requireNonBlankString(label.relevance, 'relevance');

    if (!VALID_RELEVANCE.has(relevance)) {
      throw new Error(`Unsupported relevance value: ${relevance}`);
    }

    if (relevance === 'strong') {
      strongArticleIds.push(articleId);
    }

    if (relevance === 'acceptable') {
      acceptableArticleIds.push(articleId);
    }

    if (relevance === 'not_relevant') {
      notRelevantArticleIds.push(articleId);
    }
  }

  if (strongArticleIds.length + acceptableArticleIds.length === 0) {
    throw new Error('Reviewed query must contain at least one strong or acceptable label.');
  }

  return {
    query: requireNonBlankString(query.query, 'query'),
    intent: requireNonBlankString(query.intent, 'intent'),
    strongArticleIds: uniqueNumbers(strongArticleIds),
    acceptableArticleIds: uniqueNumbers(acceptableArticleIds),
    notRelevantArticleIds: uniqueNumbers(notRelevantArticleIds),
  };
}

function requireNonBlankString(value, fieldName) {
  if (typeof value !== 'string' || value.trim().length === 0) {
    throw new Error(`${fieldName} must be a non-blank string.`);
  }

  return value.trim();
}

function requirePositiveInteger(value, fieldName) {
  if (!Number.isInteger(value) || value < 1) {
    throw new Error(`${fieldName} must be a positive integer.`);
  }

  return value;
}

function uniqueNumbers(values) {
  return [...new Set(values)];
}
```

- [ ] **Step 4: Run the label parser test and verify it passes**

Run:

```bash
node --test experiments/scripts/retrieval-benchmark/labels.test.mjs
```

Expected:

```text
# pass 4
```

## Task 4: Metric Calculator

**Files:**
- Create: `experiments/scripts/retrieval-benchmark/metrics.mjs`
- Test: `experiments/scripts/retrieval-benchmark/metrics.test.mjs`

- [ ] **Step 1: Write the failing metric tests**

Create `experiments/scripts/retrieval-benchmark/metrics.test.mjs`:

```js
import test from 'node:test';
import assert from 'node:assert/strict';
import { calculateQueryMetrics, calculateSummaryMetrics } from './metrics.mjs';

const labeledQuery = {
  query: 'graph rag failure',
  intent: 'Graph RAG 실패 유형과 평가 기준을 찾는다.',
  strongArticleIds: [4],
  acceptableArticleIds: [1],
  notRelevantArticleIds: [5],
};

test('calculateQueryMetrics returns top1 strong hit when first result is strong', () => {
  const metrics = calculateQueryMetrics({ labeledQuery, rankedArticleIds: [4, 1, 2, 5, 6], latencyMs: 42, k: 5 });

  assert.equal(metrics.top1StrongHit, 1);
  assert.equal(metrics.recallAtK, 1);
  assert.equal(metrics.mrrAtK, 1);
});

test('calculateQueryMetrics separates recall and MRR', () => {
  const metrics = calculateQueryMetrics({ labeledQuery, rankedArticleIds: [1, 2, 4, 5, 6], latencyMs: 30, k: 5 });

  assert.equal(metrics.top1StrongHit, 0);
  assert.equal(metrics.recallAtK, 1);
  assert.equal(metrics.mrrAtK, 1 / 3);
});

test('calculateQueryMetrics returns zero MRR when strong article is missing from top k', () => {
  const metrics = calculateQueryMetrics({ labeledQuery, rankedArticleIds: [1, 2, 3, 5, 6], latencyMs: 30, k: 5 });

  assert.equal(metrics.top1StrongHit, 0);
  assert.equal(metrics.recallAtK, 0.5);
  assert.equal(metrics.mrrAtK, 0);
});

test('calculateSummaryMetrics averages query metrics', () => {
  const summary = calculateSummaryMetrics({
    catalogId: 'api-ready-test',
    catalogArticleCount: 6,
    k: 5,
    generatedAt: '2026-06-02T00:00:00.000Z',
    byQueryMetrics: [
      { top1StrongHit: 1, recallAtK: 1, mrrAtK: 1, latencyMs: 40 },
      { top1StrongHit: 0, recallAtK: 0.5, mrrAtK: 0.5, latencyMs: 60 },
    ],
  });

  assert.deepEqual(summary, {
    catalogId: 'api-ready-test',
    catalogArticleCount: 6,
    evaluatedQueryCount: 2,
    k: 5,
    macroTop1StrongHit: 0.5,
    macroRecallAtK: 0.75,
    macroMrrAtK: 0.75,
    averageLatencyMs: 50,
    generatedAt: '2026-06-02T00:00:00.000Z',
  });
});
```

- [ ] **Step 2: Run the metric test and verify it fails**

Run:

```bash
node --test experiments/scripts/retrieval-benchmark/metrics.test.mjs
```

Expected:

```text
ERR_MODULE_NOT_FOUND
```

- [ ] **Step 3: Implement metric calculation**

Create `experiments/scripts/retrieval-benchmark/metrics.mjs`:

```js
export function calculateQueryMetrics({ labeledQuery, rankedArticleIds, latencyMs, k }) {
  const topKArticleIds = rankedArticleIds.slice(0, k);
  const strongSet = new Set(labeledQuery.strongArticleIds);
  const positiveSet = new Set([...labeledQuery.strongArticleIds, ...labeledQuery.acceptableArticleIds]);
  const foundPositiveCount = topKArticleIds.filter((articleId) => positiveSet.has(articleId)).length;
  const firstStrongIndex = topKArticleIds.findIndex((articleId) => strongSet.has(articleId));

  return {
    query: labeledQuery.query,
    intent: labeledQuery.intent,
    strongArticleIds: labeledQuery.strongArticleIds,
    acceptableArticleIds: labeledQuery.acceptableArticleIds,
    resultArticleIds: rankedArticleIds,
    top1StrongHit: topKArticleIds.length > 0 && strongSet.has(topKArticleIds[0]) ? 1 : 0,
    recallAtK: roundMetric(foundPositiveCount / positiveSet.size),
    mrrAtK: firstStrongIndex >= 0 ? roundMetric(1 / (firstStrongIndex + 1)) : 0,
    latencyMs,
  };
}

export function calculateSummaryMetrics({ catalogId, catalogArticleCount, k, generatedAt, byQueryMetrics }) {
  return {
    catalogId,
    catalogArticleCount,
    evaluatedQueryCount: byQueryMetrics.length,
    k,
    macroTop1StrongHit: average(byQueryMetrics.map((metric) => metric.top1StrongHit)),
    macroRecallAtK: average(byQueryMetrics.map((metric) => metric.recallAtK)),
    macroMrrAtK: average(byQueryMetrics.map((metric) => metric.mrrAtK)),
    averageLatencyMs: average(byQueryMetrics.map((metric) => metric.latencyMs)),
    generatedAt,
  };
}

function average(values) {
  if (values.length === 0) {
    return 0;
  }

  return roundMetric(values.reduce((sum, value) => sum + value, 0) / values.length);
}

function roundMetric(value) {
  return Number(value.toFixed(6));
}
```

- [ ] **Step 4: Run the metric test and verify it passes**

Run:

```bash
node --test experiments/scripts/retrieval-benchmark/metrics.test.mjs
```

Expected:

```text
# pass 4
```

## Task 5: Public Search Client

**Files:**
- Create: `experiments/scripts/retrieval-benchmark/search-client.mjs`
- Test: `experiments/scripts/retrieval-benchmark/search-client.test.mjs`

- [ ] **Step 1: Write the failing search client tests**

Create `experiments/scripts/retrieval-benchmark/search-client.test.mjs`:

```js
import test from 'node:test';
import assert from 'node:assert/strict';
import { createArticleSearchClient } from './search-client.mjs';

test('searchArticles calls public API and extracts ranked article ids', async () => {
  const requestedUrls = [];
  const client = createArticleSearchClient({
    baseUrl: 'http://localhost:8080',
    fetchImpl: async (url) => {
      requestedUrls.push(String(url));
      return {
        ok: true,
        status: 200,
        json: async () => [{ id: 4 }, { id: 1 }, { id: 2 }],
      };
    },
    now: createStepClock([100, 145]),
  });

  const result = await client.searchArticles('graph rag failure');

  assert.equal(requestedUrls[0], 'http://localhost:8080/api/articles?query=graph+rag+failure');
  assert.deepEqual(result.rankedArticleIds, [4, 1, 2]);
  assert.equal(result.latencyMs, 45);
});

test('searchArticles rejects non-2xx API response', async () => {
  const client = createArticleSearchClient({
    baseUrl: 'http://localhost:8080',
    fetchImpl: async () => ({ ok: false, status: 503, text: async () => 'unavailable' }),
    now: createStepClock([0, 1]),
  });

  await assert.rejects(
    () => client.searchArticles('agent evaluation'),
    /Search API failed with status 503/
  );
});

test('searchArticles rejects response without numeric ids', async () => {
  const client = createArticleSearchClient({
    baseUrl: 'http://localhost:8080',
    fetchImpl: async () => ({ ok: true, status: 200, json: async () => [{ title: 'missing id' }] }),
    now: createStepClock([0, 1]),
  });

  await assert.rejects(
    () => client.searchArticles('agent evaluation'),
    /Search API response item must include numeric id/
  );
});

function createStepClock(values) {
  let index = 0;
  return () => values[index++];
}
```

- [ ] **Step 2: Run the search client test and verify it fails**

Run:

```bash
node --test experiments/scripts/retrieval-benchmark/search-client.test.mjs
```

Expected:

```text
ERR_MODULE_NOT_FOUND
```

- [ ] **Step 3: Implement the public search client**

Create `experiments/scripts/retrieval-benchmark/search-client.mjs`:

```js
export function createArticleSearchClient({ baseUrl, fetchImpl = fetch, now = () => Date.now() }) {
  const normalizedBaseUrl = baseUrl.replace(/\/+$/, '');

  return {
    async searchArticles(query) {
      const url = new URL(`${normalizedBaseUrl}/api/articles`);
      url.searchParams.set('query', query);

      const start = now();
      const response = await fetchImpl(url);
      const end = now();

      if (!response.ok) {
        throw new Error(`Search API failed with status ${response.status}.`);
      }

      const body = await response.json();

      if (!Array.isArray(body)) {
        throw new Error('Search API response must be an array.');
      }

      return {
        rankedArticleIds: body.map(extractArticleId),
        latencyMs: Math.max(0, Math.round(end - start)),
      };
    },
  };
}

function extractArticleId(item) {
  if (!Number.isInteger(item?.id)) {
    throw new Error('Search API response item must include numeric id.');
  }

  return item.id;
}
```

- [ ] **Step 4: Run the search client test and verify it passes**

Run:

```bash
node --test experiments/scripts/retrieval-benchmark/search-client.test.mjs
```

Expected:

```text
# pass 3
```

## Task 6: Report Writer

**Files:**
- Create: `experiments/scripts/retrieval-benchmark/report-writer.mjs`
- Test: `experiments/scripts/retrieval-benchmark/report-writer.test.mjs`

- [ ] **Step 1: Write the failing report writer tests**

Create `experiments/scripts/retrieval-benchmark/report-writer.test.mjs`:

```js
import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile, rm } from 'node:fs/promises';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { writeBenchmarkArtifacts } from './report-writer.mjs';

test('writeBenchmarkArtifacts writes run, metrics, and markdown report', async () => {
  const outputDir = await mkdtemp(join(tmpdir(), 'sigak-benchmark-'));

  try {
    const artifacts = await writeBenchmarkArtifacts({
      outputDir,
      run: {
        labelsPath: 'labels.json',
        baseUrl: 'http://localhost:8080',
        system: 'hybrid',
        k: 5,
        queries: [{ query: 'graph rag failure', rankedArticleIds: [4, 1], latencyMs: 42 }],
      },
      byQueryMetrics: [{
        query: 'graph rag failure',
        intent: 'Graph RAG 실패 유형과 평가 기준을 찾는다.',
        strongArticleIds: [4],
        acceptableArticleIds: [1],
        resultArticleIds: [4, 1],
        top1StrongHit: 1,
        recallAtK: 1,
        mrrAtK: 1,
        latencyMs: 42,
      }],
      summary: {
        catalogId: 'api-ready-test',
        catalogArticleCount: 6,
        evaluatedQueryCount: 1,
        k: 5,
        macroTop1StrongHit: 1,
        macroRecallAtK: 1,
        macroMrrAtK: 1,
        averageLatencyMs: 42,
        generatedAt: '2026-06-02T00:00:00.000Z',
      },
    });

    assert.equal(artifacts.reportPath, join(outputDir, 'report.md'));
    assert.match(await readFile(join(outputDir, 'runs.hybrid.json'), 'utf8'), /graph rag failure/);
    assert.match(await readFile(join(outputDir, 'metrics.summary.json'), 'utf8'), /macroRecallAtK/);
    assert.match(await readFile(join(outputDir, 'report.md'), 'utf8'), /Top1 Strong Hit/);
  } finally {
    await rm(outputDir, { recursive: true, force: true });
  }
});
```

- [ ] **Step 2: Run the report writer test and verify it fails**

Run:

```bash
node --test experiments/scripts/retrieval-benchmark/report-writer.test.mjs
```

Expected:

```text
ERR_MODULE_NOT_FOUND
```

- [ ] **Step 3: Implement JSON and Markdown artifact writer**

Create `experiments/scripts/retrieval-benchmark/report-writer.mjs`:

```js
import { mkdir, writeFile } from 'node:fs/promises';
import { join } from 'node:path';

export async function writeBenchmarkArtifacts({ outputDir, run, byQueryMetrics, summary }) {
  await mkdir(outputDir, { recursive: true });

  const runPath = join(outputDir, 'runs.hybrid.json');
  const byQueryPath = join(outputDir, 'metrics.by-query.json');
  const summaryPath = join(outputDir, 'metrics.summary.json');
  const reportPath = join(outputDir, 'report.md');

  await writeJson(runPath, run);
  await writeJson(byQueryPath, byQueryMetrics);
  await writeJson(summaryPath, summary);
  await writeFile(reportPath, buildMarkdownReport({ run, byQueryMetrics, summary }), 'utf8');

  return { runPath, byQueryPath, summaryPath, reportPath };
}

export function buildMarkdownReport({ run, byQueryMetrics, summary }) {
  const queryRows = byQueryMetrics
    .map((metric) => `| ${escapeTable(metric.query)} | ${metric.top1StrongHit} | ${metric.recallAtK} | ${metric.mrrAtK} | ${metric.latencyMs} | ${metric.resultArticleIds.join(', ')} |`)
    .join('\n');

  return `# Retrieval Benchmark Smoke Report

## Summary

| Metric | Value |
| --- | ---: |
| Catalog ID | ${summary.catalogId} |
| Catalog articles | ${summary.catalogArticleCount} |
| Evaluated queries | ${summary.evaluatedQueryCount} |
| K | ${summary.k} |
| Top1 Strong Hit | ${summary.macroTop1StrongHit} |
| Recall@${summary.k} | ${summary.macroRecallAtK} |
| MRR@${summary.k} | ${summary.macroMrrAtK} |
| Average latencyMs | ${summary.averageLatencyMs} |

## Metric Notes

- Top1 Strong Hit: 첫 번째 결과가 strong article이면 1이다.
- Recall@${summary.k}: Top${summary.k} 안에 strong 또는 acceptable article이 얼마나 포함됐는지 본다.
- MRR@${summary.k}: Top${summary.k} 안에서 첫 strong article이 얼마나 빨리 등장하는지 본다.
- LatencyMs: public article search API round-trip 시간을 한 번 측정한 값이다.

## Query Results

| Query | Top1 Strong Hit | Recall@${summary.k} | MRR@${summary.k} | LatencyMs | Result IDs |
| --- | ---: | ---: | ---: | ---: | --- |
${queryRows}

## Run Conditions

- System: ${run.system}
- Base URL: ${run.baseUrl}
- Labels: ${run.labelsPath}
- Generated at: ${summary.generatedAt}

## Limits

이 결과는 smoke benchmark다. Query와 article 수가 작기 때문에 검색 품질 개선의 최종 근거가 아니라, 평가 파이프라인이 재현 가능하게 동작하는지 확인하는 용도다.
`;
}

async function writeJson(path, value) {
  await writeFile(path, `${JSON.stringify(value, null, 2)}\n`, 'utf8');
}

function escapeTable(value) {
  return String(value).replaceAll('|', '\\|');
}
```

- [ ] **Step 4: Run the report writer test and verify it passes**

Run:

```bash
node --test experiments/scripts/retrieval-benchmark/report-writer.test.mjs
```

Expected:

```text
# pass 1
```

## Task 7: Runner Orchestration And CLI Entrypoint

**Files:**
- Create: `experiments/scripts/retrieval-benchmark/runner.mjs`
- Create: `experiments/scripts/retrieval-benchmark.mjs`
- Test: `experiments/scripts/retrieval-benchmark/runner.test.mjs`

- [ ] **Step 1: Write the failing runner test**

Create `experiments/scripts/retrieval-benchmark/runner.test.mjs`:

```js
import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { runRetrievalBenchmark } from './runner.mjs';

test('runRetrievalBenchmark evaluates reviewed queries and writes artifacts', async () => {
  const workspace = await mkdtemp(join(tmpdir(), 'sigak-runner-'));
  const labelsPath = join(workspace, 'labels.json');
  const outputDir = join(workspace, 'results');

  await writeFile(labelsPath, JSON.stringify({
    version: 1,
    catalogId: 'api-ready-test',
    catalogArticleCount: 6,
    queries: [{
      query: 'graph rag failure',
      intent: 'Graph RAG 실패 유형과 평가 기준을 찾는다.',
      status: 'reviewed',
      labels: [
        { articleId: 4, relevance: 'strong', note: '' },
        { articleId: 1, relevance: 'acceptable', note: '' },
      ],
    }],
  }), 'utf8');

  try {
    const result = await runRetrievalBenchmark({
      labelsPath,
      baseUrl: 'http://localhost:8080',
      outputDir,
      k: 5,
      searchClient: {
        searchArticles: async () => ({ rankedArticleIds: [4, 1, 2], latencyMs: 20 }),
      },
      generatedAt: '2026-06-02T00:00:00.000Z',
    });

    assert.equal(result.summary.evaluatedQueryCount, 1);
    assert.equal(result.summary.macroTop1StrongHit, 1);
    assert.match(await readFile(join(outputDir, 'report.md'), 'utf8'), /graph rag failure/);
  } finally {
    await rm(workspace, { recursive: true, force: true });
  }
});
```

- [ ] **Step 2: Run the runner test and verify it fails**

Run:

```bash
node --test experiments/scripts/retrieval-benchmark/runner.test.mjs
```

Expected:

```text
ERR_MODULE_NOT_FOUND
```

- [ ] **Step 3: Implement runner orchestration**

Create `experiments/scripts/retrieval-benchmark/runner.mjs`:

```js
import { loadLabelFile } from './labels.mjs';
import { calculateQueryMetrics, calculateSummaryMetrics } from './metrics.mjs';
import { createArticleSearchClient } from './search-client.mjs';
import { writeBenchmarkArtifacts } from './report-writer.mjs';

export async function runRetrievalBenchmark({
  labelsPath,
  baseUrl,
  outputDir,
  k,
  searchClient = createArticleSearchClient({ baseUrl }),
  generatedAt = new Date().toISOString(),
}) {
  const labels = await loadLabelFile(labelsPath);
  const runQueries = [];
  const byQueryMetrics = [];

  for (const labeledQuery of labels.queries) {
    const searchResult = await searchClient.searchArticles(labeledQuery.query);

    runQueries.push({
      query: labeledQuery.query,
      rankedArticleIds: searchResult.rankedArticleIds,
      latencyMs: searchResult.latencyMs,
    });

    byQueryMetrics.push(calculateQueryMetrics({
      labeledQuery,
      rankedArticleIds: searchResult.rankedArticleIds,
      latencyMs: searchResult.latencyMs,
      k,
    }));
  }

  const run = {
    labelsPath,
    baseUrl,
    system: 'hybrid',
    k,
    queries: runQueries,
  };

  const summary = calculateSummaryMetrics({
    catalogId: labels.catalogId,
    catalogArticleCount: labels.catalogArticleCount,
    k,
    generatedAt,
    byQueryMetrics,
  });

  const artifacts = await writeBenchmarkArtifacts({ outputDir, run, byQueryMetrics, summary });

  return { run, byQueryMetrics, summary, artifacts };
}
```

- [ ] **Step 4: Implement CLI entrypoint**

Create `experiments/scripts/retrieval-benchmark.mjs`:

```js
#!/usr/bin/env node

import { parseBenchmarkArgs } from './retrieval-benchmark/cli.mjs';
import { runRetrievalBenchmark } from './retrieval-benchmark/runner.mjs';

try {
  const args = parseBenchmarkArgs(process.argv.slice(2));
  const result = await runRetrievalBenchmark(args);

  console.log(`Retrieval benchmark complete: ${result.artifacts.reportPath}`);
  console.log(`Queries: ${result.summary.evaluatedQueryCount}`);
  console.log(`Top1 Strong Hit: ${result.summary.macroTop1StrongHit}`);
  console.log(`Recall@${result.summary.k}: ${result.summary.macroRecallAtK}`);
  console.log(`MRR@${result.summary.k}: ${result.summary.macroMrrAtK}`);
  console.log(`Average latencyMs: ${result.summary.averageLatencyMs}`);
} catch (error) {
  console.error(error instanceof Error ? error.message : String(error));
  process.exitCode = 1;
}
```

- [ ] **Step 5: Run the runner test and verify it passes**

Run:

```bash
node --test experiments/scripts/retrieval-benchmark/runner.test.mjs
```

Expected:

```text
# pass 1
```

## Task 8: Documentation Updates

**Files:**
- Modify: `experiments/README.md`
- Modify: `docs/search-evaluation/queries.md`
- Modify: `docs/STATUS.md`
- Modify: `docs/STATUS.ko.md`
- Modify: `docs/ROADMAP.md`
- Modify: `docs/ROADMAP.ko.md`
- Modify: `docs/blog/2026-06-02-dev-log.md`
- Modify: `docs/blog/topic-queue.md`

- [ ] **Step 1: Update experiments README**

Modify `experiments/README.md` to replace the sentence saying the benchmark runner does not exist with:

```md
## Retrieval Benchmark Runner

Label JSON을 만든 뒤 public article search API를 기준으로 smoke benchmark를 실행한다.
이 명령은 backend가 실행 중이고 Elasticsearch/Qdrant projection이 현재 검색 설정에 맞게 준비되어 있다는 전제를 가진다.

```bash
node experiments/scripts/retrieval-benchmark.mjs \
  --labels=experiments/datasets/labels/search-labels.api-ready-2026-06-02.2026-06-02.json \
  --base-url=http://localhost:8080 \
  --output-dir=experiments/results/retrieval/latest
```

생성되는 파일:

- `runs.hybrid.json`: query별 검색 결과 article ID와 latency
- `metrics.by-query.json`: query별 Top1 Strong Hit, Recall@5, MRR@5, latency
- `metrics.summary.json`: 전체 평균 metric
- `report.md`: 사람이 읽기 위한 요약 report

현재 runner는 smoke benchmark용이다. Keyword-only/vector-only/hybrid 비교와 고급 IR metric은 label set과 실행 계약이 안정된 뒤 확장한다.
```

- [ ] **Step 2: Update Korean labeling guide**

Modify `docs/search-evaluation/queries.md` with a new section:

```md
## 6. 라벨 JSON을 평가에 사용하는 방법

라벨 JSON을 `experiments/datasets/labels/`에 넣은 뒤 아래 명령으로 검색 평가 report를 만든다.
이 단계는 HTML 도구에서 라벨을 만드는 작업과 별개로, 현재 public search API 결과가 사람이 만든 정답지와 얼마나 맞는지 확인한다.

```bash
node experiments/scripts/retrieval-benchmark.mjs \
  --labels=experiments/datasets/labels/search-labels.api-ready-2026-06-02.2026-06-02.json \
  --base-url=http://localhost:8080 \
  --output-dir=experiments/results/retrieval/latest
```

결과는 `experiments/results/retrieval/latest/report.md`에서 확인한다.

- `Top1 Strong Hit`: 첫 번째 결과가 strong article인지 확인한다.
- `Recall@5`: Top5 안에 strong 또는 acceptable article이 얼마나 들어왔는지 확인한다.
- `MRR@5`: Top5 안에서 첫 strong article이 몇 번째에 등장했는지 확인한다.
- `LatencyMs`: public search API 요청 1회에 걸린 시간을 기록한다.

현재 label 수가 작으므로 report는 검색 품질의 최종 결론이 아니라, 평가 파이프라인이 재현 가능하게 작동하는지 확인하는 smoke 결과로 해석한다.
```

- [ ] **Step 3: Update status and roadmap**

Update `docs/STATUS.md` and `docs/STATUS.ko.md`:

- "retrieval benchmark labeling can start" wording becomes "retrieval benchmark smoke runner exists".
- Mention generated artifacts only after the local smoke command has actually been run.

Update `docs/ROADMAP.md` and `docs/ROADMAP.ko.md`:

- Mark "benchmark runner" as implemented after tests and smoke pass.
- Keep "larger labeled dataset" pending.

- [ ] **Step 4: Update dev-log and topic queue**

Update `docs/blog/2026-06-02-dev-log.md`:

- Record that benchmark runner design and implementation were completed.
- Record exact verification commands that were run.
- Record local smoke result values from `metrics.summary.json` only if the command was executed successfully.

Update `docs/blog/topic-queue.md`:

- Add or strengthen a topic candidate about `qrels/run/metrics/report` for solo-friendly retrieval evaluation.
- Include Top1 Strong Hit, Recall@5, MRR@5, and LatencyMs as blog explanation points.

## Task 9: Verification

**Files:**
- Read: all files created or modified in this plan

- [ ] **Step 1: Run all benchmark runner unit tests**

Run:

```bash
node --test experiments/scripts/retrieval-benchmark/*.test.mjs
```

Expected:

```text
# fail 0
```

- [ ] **Step 2: Run whitespace/static diff check**

Run:

```bash
git diff --check
```

Expected:

```text
```

- [ ] **Step 3: Run local smoke only when backend is running**

Precondition:

- Spring Boot backend is reachable at `http://localhost:8080`.
- Current local search projection is ready enough for `/api/articles?query=...`.

Run:

```bash
node experiments/scripts/retrieval-benchmark.mjs \
  --labels=experiments/datasets/labels/search-labels.api-ready-2026-06-02.2026-06-02.json \
  --base-url=http://localhost:8080 \
  --output-dir=experiments/results/retrieval/latest
```

Expected:

```text
Retrieval benchmark complete: experiments/results/retrieval/latest/report.md
Queries: 3
```

If backend is not running, record this step as `미검증` instead of claiming benchmark smoke success.

- [ ] **Step 4: Inspect generated artifacts after local smoke**

Run:

```bash
sed -n '1,220p' experiments/results/retrieval/latest/report.md
cat experiments/results/retrieval/latest/metrics.summary.json
```

Expected:

- `report.md` contains the metric explanation section.
- `metrics.summary.json` contains `evaluatedQueryCount`, `macroTop1StrongHit`, `macroRecallAtK`, `macroMrrAtK`, and `averageLatencyMs`.

- [ ] **Step 5: Confirm final working tree scope**

Run:

```bash
git status --short
```

Expected:

- New benchmark runner files are visible.
- Documentation files updated by this plan are visible.
- `.gitignore` remains an unrelated pre-existing modification if it was present before the plan execution.

## Self-Review Checklist

- [ ] The runner uses label JSON as qrels and public API response as run.
- [ ] Only `reviewed` queries are evaluated.
- [ ] `strong + acceptable` are positive for Recall@k.
- [ ] MRR@k uses only `strong`.
- [ ] `LatencyMs` is public API round-trip latency, not an internal backend breakdown.
- [ ] Generated files match `runs.hybrid.json`, `metrics.by-query.json`, `metrics.summary.json`, and `report.md`.
- [ ] No external Node packages were added.
- [ ] Documentation explains that this is a smoke benchmark with a small label set.
- [ ] Verification output separates passed commands from `미검증` smoke checks.
