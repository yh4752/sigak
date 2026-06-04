# Graph-Aware Evaluation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 기존 retrieval benchmark runner에 public graph-context 평가 artifact를 추가해 graph-aware article detail의 설명 가능성을 작게라도 재현 가능하게 측정한다.

**Architecture:** Backend API를 새로 만들지 않는다. Node runner가 기존 public search run의 top-k article을 source article로 삼고, public article detail 및 `GET /api/articles/{id}/graph-context`를 호출해 related baseline, graph reason, topic context, search miss, failure/empty context metric을 생성한다.

**Tech Stack:** Node.js ESM, Node built-in `node:test`, built-in `fetch`, Spring Boot public article API, Neo4j public graph-context companion API, JSON artifact, Markdown report.

---

## Reference Context

- Spec: `docs/superpowers/specs/2026-06-03-graph-aware-evaluation-design.md`
- Existing runner:
  - `experiments/scripts/retrieval-benchmark.mjs`
  - `experiments/scripts/retrieval-benchmark/cli.mjs`
  - `experiments/scripts/retrieval-benchmark/runner.mjs`
  - `experiments/scripts/retrieval-benchmark/search-client.mjs`
  - `experiments/scripts/retrieval-benchmark/metrics.mjs`
  - `experiments/scripts/retrieval-benchmark/report-writer.mjs`
- Existing tests:
  - `experiments/scripts/retrieval-benchmark/cli.test.mjs`
  - `experiments/scripts/retrieval-benchmark/runner.test.mjs`
  - `experiments/scripts/retrieval-benchmark/search-client.test.mjs`
  - `experiments/scripts/retrieval-benchmark/metrics.test.mjs`
  - `experiments/scripts/retrieval-benchmark/report-writer.test.mjs`
- Public API already implemented:
  - `GET /api/articles?query=...`
  - `GET /api/articles/{id}`
  - `GET /api/articles/{id}/graph-context`

## Worktree Guard

- Run `git status --short --branch` before implementation.
- Do not revert user changes.
- This branch currently may contain documentation changes for `2026-06-03` dev-log, status, topic queue, and the graph-aware evaluation spec.
- Do not run `git commit` or `git push` unless the user asks for it. This project rule overrides generic frequent-commit guidance.

## Boundary Rules

- `--include-graph-context` must be optional. Without it, all existing retrieval benchmark behavior and artifact names stay unchanged.
- Graph evaluation uses only public-safe endpoints.
- Do not call `/api/internal/graph/articles/{id}/context` from the runner.
- Do not store internal Neo4j timings, relation type, headers, API keys, environment variable values, raw content, embedding vectors, or stack traces in artifacts.
- Graph metric is article detail explanation evidence, not query-to-article search quality.
- Report must state that latency is public API round-trip, not pure Neo4j query latency.
- Report must state that graph reasons are stored projection reasons, not independently verified factual explanations.
- Current 3-query/6-article label set is smoke-only evidence.

## File Structure

Create:

- `experiments/scripts/retrieval-benchmark/graph-client.mjs`
  - Fetches public article detail and public graph context.
  - Validates only the fields needed by the runner.
  - Converts graph context failures into explicit failed context rows.
- `experiments/scripts/retrieval-benchmark/graph-client.test.mjs`
  - Tests public endpoint calls, validation, empty context, HTTP failure rows, and latency.
- `experiments/scripts/retrieval-benchmark/graph-metrics.mjs`
  - Calculates query-level and summary graph-aware metrics.
- `experiments/scripts/retrieval-benchmark/graph-metrics.test.mjs`
  - Tests search miss, related baseline coverage, graph reason coverage, topic coverage, null denominator behavior, and warnings.
- `experiments/scripts/retrieval-benchmark/graph-report-writer.mjs`
  - Writes `graph-context.runs.json`, `graph-context.metrics.by-query.json`, `graph-context.metrics.summary.json`, and appends a graph-aware section to `report.md` when a retrieval report already exists.
- `experiments/scripts/retrieval-benchmark/graph-report-writer.test.mjs`
  - Tests artifact paths and Markdown report content.
- `experiments/scripts/retrieval-benchmark/graph-runner.mjs`
  - Orchestrates graph context collection from a public run and label set.
- `experiments/scripts/retrieval-benchmark/graph-runner.test.mjs`
  - Tests end-to-end graph artifact generation with fake public run and fake graph client.

Modify:

- `experiments/scripts/retrieval-benchmark/cli.mjs`
  - Add boolean flag `--include-graph-context`.
  - Reject the flag when `--systems` is present but does not include `public`.
- `experiments/scripts/retrieval-benchmark/cli.test.mjs`
  - Add parsing and guard tests.
- `experiments/scripts/retrieval-benchmark/runner.mjs`
  - Invoke graph runner only when `includeGraphContext` is true.
  - Keep existing smoke and comparison paths unchanged otherwise.
- `experiments/scripts/retrieval-benchmark/runner.test.mjs`
  - Add integration tests for graph option in public smoke and comparison modes.
- `experiments/scripts/retrieval-benchmark.mjs`
  - Print graph-aware artifact path when graph evaluation is included.
- `experiments/README.md`
  - Document graph-aware evaluation command, artifacts, and interpretation limits.
- `docs/STATUS.md`, `docs/STATUS.ko.md`
  - Update after verified implementation and smoke.
- `docs/ROADMAP.md`, `docs/ROADMAP.ko.md`
  - Mark S5 graph-aware evaluation only after runner implementation and smoke pass.
- `docs/blog/2026-06-04-dev-log.md`
  - Record verified implementation commands and observed smoke values.
- `docs/blog/topic-queue.md`
  - Update the existing graph-aware evaluation candidate after verification.

## Output Contract

Command:

```bash
node experiments/scripts/retrieval-benchmark.mjs \
  --labels=experiments/datasets/labels/search-labels.api-ready-2026-06-02.2026-06-02.json \
  --base-url=http://localhost:8080 \
  --output-dir=experiments/results/graph/latest \
  --systems=public \
  --include-graph-context \
  --k=5 \
  --limit=20
```

Generated graph files:

```text
experiments/results/graph/latest/
├── runs.public.json
├── metrics.by-query.json
├── metrics.by-system.json
├── metrics.comparison.json
├── graph-context.runs.json
├── graph-context.metrics.by-query.json
├── graph-context.metrics.summary.json
└── report.md
```

`report.md` may include both retrieval comparison and graph-aware sections in the same file if `--systems=public` is used. The graph-specific JSON artifact names must stay prefixed with `graph-context.`.

## Task 1: CLI Flag And Mode Guard

**Files:**
- Modify: `experiments/scripts/retrieval-benchmark/cli.mjs`
- Modify: `experiments/scripts/retrieval-benchmark/cli.test.mjs`

- [ ] **Step 1: Write failing CLI tests**

Add tests:

```js
test('parseBenchmarkArgs reads include graph context flag', () => {
  const args = parseBenchmarkArgs([
    '--labels=labels.json',
    '--base-url=http://localhost:8080',
    '--output-dir=out',
    '--systems=public',
    '--include-graph-context',
  ]);

  assert.equal(args.includeGraphContext, true);
  assert.deepEqual(args.systems, ['public']);
});

test('parseBenchmarkArgs keeps include graph context disabled by default', () => {
  const args = parseBenchmarkArgs([
    '--labels=labels.json',
    '--base-url=http://localhost:8080',
    '--output-dir=out',
  ]);

  assert.equal(args.includeGraphContext, false);
});

test('parseBenchmarkArgs rejects graph context when public system is missing', () => {
  assert.throws(
    () => parseBenchmarkArgs([
      '--labels=labels.json',
      '--base-url=http://localhost:8080',
      '--output-dir=out',
      '--systems=keyword,hybrid',
      '--include-graph-context',
    ]),
    /Option --include-graph-context requires public search results/
  );
});
```

- [ ] **Step 2: Run test to verify failure**

Run:

```bash
node --test experiments/scripts/retrieval-benchmark/cli.test.mjs
```

Expected:

```text
not ok
# Unknown option: --include-graph-context
```

- [ ] **Step 3: Implement boolean flag parsing**

Change `cli.mjs`:

```js
const REQUIRED_OPTIONS = ['labels', 'base-url', 'output-dir'];
const BOOLEAN_OPTIONS = new Set(['include-graph-context']);
const KNOWN_OPTIONS = new Set([...REQUIRED_OPTIONS, 'k', 'systems', 'limit', ...BOOLEAN_OPTIONS]);
const KNOWN_SYSTEMS = new Set(['keyword', 'vector', 'hybrid', 'public']);

export function parseBenchmarkArgs(argv) {
  const options = parseOptions(argv);

  for (const optionName of REQUIRED_OPTIONS) {
    if (!options.has(optionName)) {
      throw new Error(`Missing required option: --${optionName}`);
    }
  }

  const systems = options.has('systems') ? parseSystems(options.get('systems')) : undefined;
  const includeGraphContext = options.has('include-graph-context');
  if (includeGraphContext && systems && !systems.includes('public')) {
    throw new Error('Option --include-graph-context requires public search results. Include public in --systems or omit --systems.');
  }

  const k = options.has('k') ? parsePositiveInteger(options.get('k'), 'k') : 5;
  const limit = options.has('limit') ? parseBoundedInteger(options.get('limit'), 'limit', 1, 100) : 20;

  if (limit < k) {
    throw new Error('Option --limit must be greater than or equal to --k.');
  }

  return {
    labelsPath: options.get('labels'),
    baseUrl: options.get('base-url'),
    outputDir: options.get('output-dir'),
    k,
    limit,
    includeGraphContext,
    ...(systems ? { systems } : {}),
  };
}
```

Update `parseOptions` boolean handling:

```js
function parseOptions(argv) {
  const options = new Map();

  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];

    if (!arg.startsWith('--')) {
      throw new Error(`Invalid option format: ${arg}`);
    }

    const optionText = arg.slice(2);
    const equalsIndex = optionText.indexOf('=');
    const name = equalsIndex >= 0 ? optionText.slice(0, equalsIndex) : optionText;

    if (!KNOWN_OPTIONS.has(name)) {
      throw new Error(`Unknown option: --${name}`);
    }

    if (BOOLEAN_OPTIONS.has(name)) {
      if (equalsIndex >= 0) {
        throw new Error(`Option --${name} must not include a value.`);
      }
      options.set(name, true);
      continue;
    }

    const rawValue = equalsIndex >= 0 ? optionText.slice(equalsIndex + 1) : argv[++index];

    if (rawValue === undefined || rawValue.startsWith('--')) {
      throw new Error(`Option --${name} requires a value.`);
    }

    const value = rawValue.trim();
    if (value.length === 0) {
      throw new Error(`Option --${name} must not be blank.`);
    }

    options.set(name, value);
  }

  return options;
}
```

- [ ] **Step 4: Run CLI tests**

Run:

```bash
node --test experiments/scripts/retrieval-benchmark/cli.test.mjs
```

Expected:

```text
# pass
```

## Task 2: Public Graph Context Client

**Files:**
- Create: `experiments/scripts/retrieval-benchmark/graph-client.mjs`
- Create: `experiments/scripts/retrieval-benchmark/graph-client.test.mjs`

- [ ] **Step 1: Write failing graph client tests**

Add tests:

```js
import test from 'node:test';
import assert from 'node:assert/strict';
import { createGraphContextClient } from './graph-client.mjs';

test('fetchArticleDetail reads related article ids from public detail API', async () => {
  const urls = [];
  const client = createGraphContextClient({
    baseUrl: 'http://localhost:8080/',
    fetchImpl: async (url) => {
      urls.push(String(url));
      return jsonResponse({ id: 4, relatedArticleIds: [1, 5] });
    },
    now: stepClock([10, 20]),
  });

  const result = await client.fetchArticleDetail(4);

  assert.equal(urls[0], 'http://localhost:8080/api/articles/4');
  assert.deepEqual(result.relatedArticleIds, [1, 5]);
});

test('fetchGraphContextRow maps public graph context response', async () => {
  const client = createGraphContextClient({
    baseUrl: 'http://localhost:8080',
    fetchImpl: async () => jsonResponse({
      articleId: 4,
      relatedArticleReasons: [{ articleId: 1, reason: 'Related by graph RAG evaluation.', sharedTopics: ['evaluation'] }],
      topics: [{ name: 'graph rag', displayName: 'Graph RAG', relatedArticleIds: [] }],
    }),
    now: stepClock([100, 127]),
  });

  const row = await client.fetchGraphContextRow(4);

  assert.equal(row.status, 'COMPLETED');
  assert.equal(row.latencyMs, 27);
  assert.deepEqual(row.relatedArticleReasons[0], {
    articleId: 1,
    reason: 'Related by graph RAG evaluation.',
    sharedTopics: ['evaluation'],
  });
  assert.deepEqual(row.topics, ['Graph RAG']);
});

test('fetchGraphContextRow records HTTP failure as failed row', async () => {
  const client = createGraphContextClient({
    baseUrl: 'http://localhost:8080',
    fetchImpl: async () => ({ ok: false, status: 503, json: async () => ({}) }),
    now: stepClock([1, 8]),
  });

  const row = await client.fetchGraphContextRow(4);

  assert.equal(row.status, 'FAILED');
  assert.equal(row.latencyMs, 7);
  assert.match(row.failureReason, /Graph context API failed with status 503/);
});
```

Add helpers at the bottom of `graph-client.test.mjs`:

```js
function stepClock(values) {
  let index = 0;
  return () => values[index++];
}

function jsonResponse(body, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  };
}
```

- [ ] **Step 2: Run test to verify failure**

Run:

```bash
node --test experiments/scripts/retrieval-benchmark/graph-client.test.mjs
```

Expected:

```text
Cannot find module
```

- [ ] **Step 3: Implement graph client**

Create `graph-client.mjs`:

```js
export function createGraphContextClient({ baseUrl, fetchImpl = fetch, now = () => Date.now() }) {
  const normalizedBaseUrl = baseUrl.replace(/\/+$/, '');

  return {
    async fetchArticleDetail(articleId) {
      const response = await fetchImpl(`${normalizedBaseUrl}/api/articles/${articleId}`);
      if (!response.ok) {
        throw new Error(`Article detail API failed with status ${response.status}.`);
      }

      const body = await response.json();
      return {
        articleId: requireInteger(body?.id, 'article.id'),
        relatedArticleIds: requireIntegerArray(body?.relatedArticleIds, 'relatedArticleIds'),
      };
    },

    async fetchGraphContextRow(articleId) {
      const start = now();
      try {
        const response = await fetchImpl(`${normalizedBaseUrl}/api/articles/${articleId}/graph-context`);
        const end = now();
        const latencyMs = Math.max(0, Math.round(end - start));

        if (!response.ok) {
          return failedContextRow({ articleId, latencyMs, failureReason: `Graph context API failed with status ${response.status}.` });
        }

        const body = await response.json();
        return completedContextRow({ articleId, body, latencyMs });
      } catch (error) {
        const end = now();
        return failedContextRow({
          articleId,
          latencyMs: Math.max(0, Math.round(end - start)),
          failureReason: error instanceof Error ? error.message : String(error),
        });
      }
    },
  };
}

function completedContextRow({ articleId, body, latencyMs }) {
  const responseArticleId = requireInteger(body?.articleId, 'graphContext.articleId');
  if (responseArticleId !== articleId) {
    throw new Error(`Graph context response articleId ${responseArticleId} does not match requested articleId ${articleId}.`);
  }

  const relatedArticleReasons = requireArray(body?.relatedArticleReasons, 'relatedArticleReasons').map((reason) => ({
    articleId: requireInteger(reason?.articleId, 'relatedArticleReasons.articleId'),
    reason: requireString(reason?.reason, 'relatedArticleReasons.reason'),
    sharedTopics: requireStringArray(reason?.sharedTopics, 'relatedArticleReasons.sharedTopics'),
  }));
  const topics = requireArray(body?.topics, 'topics').map((topic) => requireString(topic?.displayName, 'topics.displayName'));

  return {
    articleId,
    status: 'COMPLETED',
    latencyMs,
    emptyContext: relatedArticleReasons.length === 0 && topics.length === 0,
    relatedArticleReasons,
    topics,
  };
}

function failedContextRow({ articleId, latencyMs, failureReason }) {
  return {
    articleId,
    status: 'FAILED',
    latencyMs,
    failureReason,
    emptyContext: false,
    relatedArticleReasons: [],
    topics: [],
  };
}
```

Add local validators in the same file:

```js
function requireArray(value, fieldName) {
  if (!Array.isArray(value)) {
    throw new Error(`${fieldName} must be an array.`);
  }
  return value;
}

function requireInteger(value, fieldName) {
  if (!Number.isInteger(value)) {
    throw new Error(`${fieldName} must be an integer.`);
  }
  return value;
}

function requireIntegerArray(value, fieldName) {
  return requireArray(value, fieldName).map((item) => requireInteger(item, fieldName));
}

function requireString(value, fieldName) {
  if (typeof value !== 'string') {
    throw new Error(`${fieldName} must be a string.`);
  }
  return value;
}

function requireStringArray(value, fieldName) {
  return requireArray(value, fieldName).map((item) => requireString(item, fieldName));
}
```

- [ ] **Step 4: Run graph client tests**

Run:

```bash
node --test experiments/scripts/retrieval-benchmark/graph-client.test.mjs
```

Expected:

```text
# pass
```

## Task 3: Graph-Aware Metrics

**Files:**
- Create: `experiments/scripts/retrieval-benchmark/graph-metrics.mjs`
- Create: `experiments/scripts/retrieval-benchmark/graph-metrics.test.mjs`

- [ ] **Step 1: Write failing metric tests**

Add tests:

```js
import test from 'node:test';
import assert from 'node:assert/strict';
import { calculateGraphQueryMetrics, calculateGraphSummaryMetrics } from './graph-metrics.mjs';

test('calculateGraphQueryMetrics separates search miss from graph coverage', () => {
  const metric = calculateGraphQueryMetrics({
    labeledQuery: labeledQuery(),
    graphRunQuery: {
      query: 'graph rag failure',
      searchRankedArticleIds: [4, 2],
      sourceArticleIds: [4, 2],
      contexts: [
        completedContext({ articleId: 4, relatedArticleIds: [1], graphRelatedIds: [1], reason: 'Related by graph RAG.' }),
        completedContext({ articleId: 2, relatedArticleIds: [], graphRelatedIds: [] }),
      ],
    },
    k: 2,
  });

  assert.deepEqual(metric.positiveArticleIds, [4, 1]);
  assert.deepEqual(metric.searchPositiveHitIds, [4]);
  assert.deepEqual(metric.searchMissedPositiveIds, [1]);
  assert.equal(metric.searchPositiveCoverageAtK, 0.5);
  assert.equal(metric.relatedBaselineCoverageAtK, 0.5);
  assert.equal(metric.graphContextCoverageAtK, 0.5);
  assert.equal(metric.graphContextCoverageAmongSearchHits, 0);
  assert.equal(metric.graphReasonedCoverageAtK, 0.5);
});

test('calculateGraphQueryMetrics returns null for denominator-free coverage', () => {
  const metric = calculateGraphQueryMetrics({
    labeledQuery: {
      query: 'orphan query',
      intent: 'No search hit.',
      strongArticleIds: [9],
      acceptableArticleIds: [],
      notRelevantArticleIds: [],
    },
    graphRunQuery: {
      query: 'orphan query',
      searchRankedArticleIds: [1, 2],
      sourceArticleIds: [1, 2],
      contexts: [],
    },
    k: 2,
  });

  assert.equal(metric.graphContextCoverageAmongSearchHits, null);
  assert.equal(metric.reasonCoverage, null);
  assert.equal(metric.topicContextCoverage, null);
});
```

Add helpers at the bottom of `graph-metrics.test.mjs`:

```js
function labeledQuery() {
  return {
    query: 'graph rag failure',
    intent: 'Graph RAG 실패 유형과 평가 기준을 찾는다.',
    strongArticleIds: [4],
    acceptableArticleIds: [1],
    notRelevantArticleIds: [2],
  };
}

function completedContext({ articleId, relatedArticleIds, graphRelatedIds, reason = '' }) {
  return {
    articleId,
    status: 'COMPLETED',
    latencyMs: 10,
    emptyContext: graphRelatedIds.length === 0,
    relatedArticleIds,
    relatedArticleReasons: graphRelatedIds.map((relatedArticleId) => ({
      articleId: relatedArticleId,
      reason,
      sharedTopics: [],
    })),
    topics: [],
  };
}
```

- [ ] **Step 2: Run test to verify failure**

Run:

```bash
node --test experiments/scripts/retrieval-benchmark/graph-metrics.test.mjs
```

Expected:

```text
Cannot find module
```

- [ ] **Step 3: Implement query metrics**

Create `graph-metrics.mjs`:

```js
export function calculateGraphQueryMetrics({ labeledQuery, graphRunQuery, k }) {
  const positiveArticleIds = uniqueNumbers([...labeledQuery.strongArticleIds, ...labeledQuery.acceptableArticleIds]);
  const positiveSet = new Set(positiveArticleIds);
  const searchRankedArticleIds = graphRunQuery.searchRankedArticleIds.slice(0, k);
  const searchPositiveHitIds = searchRankedArticleIds.filter((articleId) => positiveSet.has(articleId));
  const searchPositiveHitSet = new Set(searchPositiveHitIds);
  const sourceContexts = graphRunQuery.contexts ?? [];

  const relatedBaselineHitIds = distinctPositiveRelatedIds({
    contexts: sourceContexts,
    positiveSet,
    getRelatedIds: (context) => context.relatedArticleIds ?? [],
  });
  const graphRelatedHitIds = distinctPositiveRelatedIds({
    contexts: sourceContexts.filter((context) => context.status === 'COMPLETED'),
    positiveSet,
    getRelatedIds: (context) => context.relatedArticleReasons.map((reason) => reason.articleId),
  });
  const graphReasonedHitIds = distinctPositiveRelatedIds({
    contexts: sourceContexts.filter((context) => context.status === 'COMPLETED'),
    positiveSet,
    getRelatedIds: (context) => context.relatedArticleReasons
      .filter((reason) => reason.reason.trim().length > 0)
      .map((reason) => reason.articleId),
  });
  const completedContexts = sourceContexts.filter((context) => context.status === 'COMPLETED');
  const graphSearchHitRelatedHitCount = graphRelatedHitIds.filter((articleId) => searchPositiveHitSet.has(articleId)).length;

  return {
    query: labeledQuery.query,
    intent: labeledQuery.intent,
    evaluatedArticleCount: sourceContexts.length,
    positiveArticleIds,
    searchRankedArticleIds,
    searchPositiveHitIds,
    searchMissedPositiveIds: positiveArticleIds.filter((articleId) => !searchPositiveHitSet.has(articleId)),
    relatedBaselineHitCount: relatedBaselineHitIds.length,
    graphRelatedHitCount: graphRelatedHitIds.length,
    graphSearchHitRelatedHitCount,
    graphReasonedHitCount: graphReasonedHitIds.length,
    searchPositiveCoverageAtK: ratioOrNull(searchPositiveHitIds.length, positiveArticleIds.length),
    relatedBaselineCoverageAtK: ratioOrNull(relatedBaselineHitIds.length, positiveArticleIds.length),
    graphContextCoverageAtK: ratioOrNull(graphRelatedHitIds.length, positiveArticleIds.length),
    graphContextCoverageAmongSearchHits: ratioOrNull(graphSearchHitRelatedHitCount, searchPositiveHitIds.length),
    graphReasonedCoverageAtK: ratioOrNull(graphReasonedHitIds.length, positiveArticleIds.length),
    reasonCoverage: ratioOrNull(graphReasonedHitIds.length, graphRelatedHitIds.length),
    topicContextCoverage: ratioOrNull(
      completedContexts.filter(hasTopicContext).length,
      completedContexts.length
    ),
    emptyContextCount: completedContexts.filter((context) => context.emptyContext).length,
    failedContextCount: sourceContexts.filter((context) => context.status === 'FAILED').length,
    averageGraphLatencyMs: nullableAverage(sourceContexts.map((context) => context.latencyMs)),
  };
}
```

Add helper functions:

```js
export function calculateGraphSummaryMetrics({ catalogId, catalogArticleCount, k, generatedAt, byQueryMetrics }) {
  const totalContextCount = byQueryMetrics.reduce((sum, metric) => sum + metric.evaluatedArticleCount, 0);
  const totalFailedContextCount = byQueryMetrics.reduce((sum, metric) => sum + metric.failedContextCount, 0);
  const totalEmptyContextCount = byQueryMetrics.reduce((sum, metric) => sum + metric.emptyContextCount, 0);

  return {
    catalogId,
    catalogArticleCount,
    evaluatedQueryCount: byQueryMetrics.length,
    k,
    macroSearchPositiveCoverageAtK: nullableAverage(byQueryMetrics.map((metric) => metric.searchPositiveCoverageAtK)),
    macroRelatedBaselineCoverageAtK: nullableAverage(byQueryMetrics.map((metric) => metric.relatedBaselineCoverageAtK)),
    macroGraphContextCoverageAtK: nullableAverage(byQueryMetrics.map((metric) => metric.graphContextCoverageAtK)),
    macroGraphContextCoverageAmongSearchHits: nullableAverage(byQueryMetrics.map((metric) => metric.graphContextCoverageAmongSearchHits)),
    macroGraphReasonedCoverageAtK: nullableAverage(byQueryMetrics.map((metric) => metric.graphReasonedCoverageAtK)),
    macroReasonCoverage: nullableAverage(byQueryMetrics.map((metric) => metric.reasonCoverage)),
    macroTopicContextCoverage: nullableAverage(byQueryMetrics.map((metric) => metric.topicContextCoverage)),
    graphContextFailureRate: ratioOrNull(totalFailedContextCount, totalContextCount),
    emptyContextRate: ratioOrNull(totalEmptyContextCount, totalContextCount),
    averageGraphLatencyMs: nullableAverage(byQueryMetrics.map((metric) => metric.averageGraphLatencyMs)),
    generatedAt,
    warnings: graphWarnings({ catalogArticleCount, evaluatedQueryCount: byQueryMetrics.length }),
  };
}

function graphWarnings({ catalogArticleCount, evaluatedQueryCount }) {
  const warnings = [];
  if (evaluatedQueryCount < 10) {
    warnings.push('label set is smaller than 10 reviewed queries, so this is a smoke graph evaluation');
  }
  if (catalogArticleCount < 20) {
    warnings.push('catalog is smaller than 20 articles, so graph density is too small for quality claims');
  }
  warnings.push('graph latency is measured as public API round-trip, not pure Neo4j query latency');
  warnings.push('graph reasons are stored projection reasons, not independently verified factual explanations');
  return warnings;
}
```

Add metric helpers:

```js
function distinctPositiveRelatedIds({ contexts, positiveSet, getRelatedIds }) {
  const ids = [];
  for (const context of contexts) {
    for (const articleId of getRelatedIds(context)) {
      if (articleId !== context.articleId && positiveSet.has(articleId)) {
        ids.push(articleId);
      }
    }
  }
  return uniqueNumbers(ids);
}

function hasTopicContext(context) {
  if (context.topics.length > 0) {
    return true;
  }
  return context.relatedArticleReasons.some((reason) => reason.sharedTopics.length > 0);
}

function ratioOrNull(numerator, denominator) {
  return denominator === 0 ? null : numerator / denominator;
}

function nullableAverage(values) {
  const numericValues = values.filter((value) => Number.isFinite(value));
  if (numericValues.length === 0) {
    return null;
  }
  return numericValues.reduce((sum, value) => sum + value, 0) / numericValues.length;
}

function uniqueNumbers(values) {
  return [...new Set(values)];
}
```

- [ ] **Step 4: Run graph metrics tests**

Run:

```bash
node --test experiments/scripts/retrieval-benchmark/graph-metrics.test.mjs
```

Expected:

```text
# pass
```

## Task 4: Graph Artifact Writer

**Files:**
- Create: `experiments/scripts/retrieval-benchmark/graph-report-writer.mjs`
- Create: `experiments/scripts/retrieval-benchmark/graph-report-writer.test.mjs`

- [ ] **Step 1: Write failing writer tests**

Add tests:

```js
import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { buildGraphMarkdownReport, writeGraphArtifacts } from './graph-report-writer.mjs';

test('writeGraphArtifacts writes graph run, metrics, summary, and report', async () => {
  const outputDir = await mkdtemp(join(tmpdir(), 'sigak-graph-'));
  try {
    await writeFile(join(outputDir, 'report.md'), '# Existing Retrieval Report\n', 'utf8');
    const artifacts = await writeGraphArtifacts({
      outputDir,
      run: sampleRun(),
      byQueryMetrics: [sampleMetric()],
      summary: sampleSummary(),
    });

    assert.equal(artifacts.runPath, join(outputDir, 'graph-context.runs.json'));
    assert.equal(artifacts.byQueryPath, join(outputDir, 'graph-context.metrics.by-query.json'));
    assert.equal(artifacts.summaryPath, join(outputDir, 'graph-context.metrics.summary.json'));
    assert.match(await readFile(artifacts.reportPath, 'utf8'), /Existing Retrieval Report/);
    assert.match(await readFile(artifacts.reportPath, 'utf8'), /Graph-Aware Evaluation Smoke Report/);
  } finally {
    await rm(outputDir, { recursive: true, force: true });
  }
});

test('buildGraphMarkdownReport explains search miss and reason provenance', () => {
  const report = buildGraphMarkdownReport({
    run: sampleRun(),
    byQueryMetrics: [sampleMetric()],
    summary: sampleSummary(),
  });

  assert.match(report, /search miss/);
  assert.match(report, /stored projection reasons/);
  assert.match(report, /public API round-trip/);
});
```

Add helpers at the bottom of `graph-report-writer.test.mjs`:

```js
function sampleRun() {
  return {
    labelsPath: 'labels.json',
    labelsSha256: 'abc123',
    catalogId: 'api-ready-test',
    catalogArticleCount: 6,
    baseUrl: 'http://localhost:8080',
    k: 5,
    generatedAt: '2026-06-04T00:00:00.000Z',
    sourceArticleSelection: 'public_search_top_k',
    graphContextEndpoint: '/api/articles/{id}/graph-context',
    reasonSource: 'stored_projection_reason',
    queries: [],
  };
}

function sampleMetric() {
  return {
    query: 'graph rag failure',
    searchPositiveCoverageAtK: 1,
    relatedBaselineCoverageAtK: 0.5,
    graphContextCoverageAtK: 0.5,
    graphReasonedCoverageAtK: 0.5,
    searchMissedPositiveIds: [],
    averageGraphLatencyMs: 18,
  };
}

function sampleSummary() {
  return {
    catalogId: 'api-ready-test',
    catalogArticleCount: 6,
    evaluatedQueryCount: 1,
    k: 5,
    macroSearchPositiveCoverageAtK: 1,
    macroRelatedBaselineCoverageAtK: 0.5,
    macroGraphContextCoverageAtK: 0.5,
    macroGraphReasonedCoverageAtK: 0.5,
    macroReasonCoverage: 1,
    macroTopicContextCoverage: 1,
    graphContextFailureRate: 0,
    emptyContextRate: 0,
    averageGraphLatencyMs: 18,
    generatedAt: '2026-06-04T00:00:00.000Z',
    warnings: [
      'label set is smaller than 10 reviewed queries, so this is a smoke graph evaluation',
      'graph latency is measured as public API round-trip, not pure Neo4j query latency',
      'graph reasons are stored projection reasons, not independently verified factual explanations',
    ],
  };
}
```

- [ ] **Step 2: Run test to verify failure**

Run:

```bash
node --test experiments/scripts/retrieval-benchmark/graph-report-writer.test.mjs
```

Expected:

```text
Cannot find module
```

- [ ] **Step 3: Implement graph artifact writer**

Create `graph-report-writer.mjs`:

```js
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { join } from 'node:path';

export async function writeGraphArtifacts({ outputDir, run, byQueryMetrics, summary }) {
  await mkdir(outputDir, { recursive: true });

  const runPath = join(outputDir, 'graph-context.runs.json');
  const byQueryPath = join(outputDir, 'graph-context.metrics.by-query.json');
  const summaryPath = join(outputDir, 'graph-context.metrics.summary.json');
  const reportPath = join(outputDir, 'report.md');

  await writeJson(runPath, run);
  await writeJson(byQueryPath, byQueryMetrics);
  await writeJson(summaryPath, summary);
  await appendGraphReport(reportPath, buildGraphMarkdownReport({ run, byQueryMetrics, summary }));

  return { runPath, byQueryPath, summaryPath, reportPath };
}
```

Add append helper:

```js
async function appendGraphReport(reportPath, graphMarkdown) {
  const existingReport = await readFile(reportPath, 'utf8').catch(() => '');
  const separator = existingReport.trim().length > 0 ? '\n\n---\n\n' : '';
  await writeFile(reportPath, `${existingReport}${separator}${graphMarkdown}`, 'utf8');
}

async function writeJson(path, value) {
  await writeFile(path, `${JSON.stringify(value, null, 2)}\n`, 'utf8');
}
```

Add report builder:

```js
export function buildGraphMarkdownReport({ run, byQueryMetrics, summary }) {
  const warningRows = summary.warnings.map((warning) => `- ${warning}`).join('\n');
  const queryRows = byQueryMetrics
    .map((metric) => [
      escapeTable(metric.query),
      formatMetric(metric.searchPositiveCoverageAtK),
      formatMetric(metric.relatedBaselineCoverageAtK),
      formatMetric(metric.graphContextCoverageAtK),
      formatMetric(metric.graphReasonedCoverageAtK),
      metric.searchMissedPositiveIds.join(', ') || '-',
      formatMetric(metric.averageGraphLatencyMs),
    ].join(' | '))
    .map((row) => `| ${row} |`)
    .join('\n');

  return `# Graph-Aware Evaluation Smoke Report

이 report는 public search top-k에서 사용자가 들어갈 수 있는 article detail이 graph context로 얼마나 설명 가능한 관련 맥락을 제공하는지 보는 smoke evaluation 결과다.

## Summary

| Metric | Value |
| --- | ---: |
| Catalog ID | ${summary.catalogId} |
| Catalog articles | ${summary.catalogArticleCount} |
| Evaluated queries | ${summary.evaluatedQueryCount} |
| K | ${summary.k} |
| Search Positive Coverage@${summary.k} | ${formatMetric(summary.macroSearchPositiveCoverageAtK)} |
| Related Baseline Coverage@${summary.k} | ${formatMetric(summary.macroRelatedBaselineCoverageAtK)} |
| Graph Context Coverage@${summary.k} | ${formatMetric(summary.macroGraphContextCoverageAtK)} |
| Graph Reasoned Coverage@${summary.k} | ${formatMetric(summary.macroGraphReasonedCoverageAtK)} |
| Reason Coverage | ${formatMetric(summary.macroReasonCoverage)} |
| Topic Context Coverage | ${formatMetric(summary.macroTopicContextCoverage)} |
| Graph Context Failure Rate | ${formatMetric(summary.graphContextFailureRate)} |
| Empty Context Rate | ${formatMetric(summary.emptyContextRate)} |
| Average Graph LatencyMs | ${formatMetric(summary.averageGraphLatencyMs)} |

## Query Results

| Query | Search Positive Coverage | Related Baseline Coverage | Graph Context Coverage | Graph Reasoned Coverage | Search Missed Positive IDs | Avg Graph LatencyMs |
| --- | ---: | ---: | ---: | ---: | --- | ---: |
${queryRows}

## Interpretation

- Graph-aware detail is evaluated as article-to-article explanation, not query-to-article search ranking.
- Search miss matters: if public search does not surface a positive article, the user has less chance to enter a detail page that can explain it.
- Reasons are stored projection reasons. They are not independently verified factual explanations.
- Latency is measured as public API round-trip, not pure Neo4j query latency.

## Run Conditions

- Base URL: ${run.baseUrl}
- Labels: ${run.labelsPath}
- Labels SHA-256: ${run.labelsSha256}
- Source article selection: ${run.sourceArticleSelection}
- Graph context endpoint: ${run.graphContextEndpoint}
- Reason source: ${run.reasonSource}
- Generated at: ${run.generatedAt}

## Limits

${warningRows}
`;
}
```

- [ ] **Step 4: Run graph writer tests**

Run:

```bash
node --test experiments/scripts/retrieval-benchmark/graph-report-writer.test.mjs
```

Expected:

```text
# pass
```

## Task 5: Graph Runner Orchestration

**Files:**
- Create: `experiments/scripts/retrieval-benchmark/graph-runner.mjs`
- Create: `experiments/scripts/retrieval-benchmark/graph-runner.test.mjs`

- [ ] **Step 1: Write failing graph runner tests**

Add tests:

```js
import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { runGraphAwareEvaluation } from './graph-runner.mjs';

test('runGraphAwareEvaluation creates graph artifacts from public run', async () => {
  const outputDir = await mkdtemp(join(tmpdir(), 'sigak-graph-runner-'));
  const labelsPath = join(outputDir, 'labels.json');
  await writeFile(labelsPath, JSON.stringify({ version: 1 }), 'utf8');

  try {
    const result = await runGraphAwareEvaluation({
      labels: {
        catalogId: 'api-ready-test',
        catalogArticleCount: 6,
        queries: [labeledQuery()],
      },
      labelsPath,
      baseUrl: 'http://localhost:8080',
      outputDir,
      k: 5,
      generatedAt: '2026-06-04T00:00:00.000Z',
      publicRun: {
        queries: [{ query: 'graph rag failure', rankedArticleIds: [4, 2, 1], latencyMs: 20 }],
      },
      graphClient: fakeGraphClient(),
    });

    assert.equal(result.summary.evaluatedQueryCount, 1);
    assert.equal(result.byQueryMetrics[0].graphContextCoverageAtK, 0.5);
    assert.match(await readFile(join(outputDir, 'graph-context.runs.json'), 'utf8'), /stored_projection_reason/);
  } finally {
    await rm(outputDir, { recursive: true, force: true });
  }
});
```

Add helpers at the bottom of `graph-runner.test.mjs`:

```js
function labeledQuery() {
  return {
    query: 'graph rag failure',
    intent: 'Graph RAG 실패 유형과 평가 기준을 찾는다.',
    strongArticleIds: [4],
    acceptableArticleIds: [1],
    notRelevantArticleIds: [2],
  };
}

function fakeGraphClient() {
  return {
    fetchArticleDetail: async (articleId) => ({
      articleId,
      relatedArticleIds: articleId === 4 ? [1] : [],
    }),
    fetchGraphContextRow: async (articleId) => ({
      articleId,
      status: 'COMPLETED',
      latencyMs: 10,
      emptyContext: articleId !== 4,
      relatedArticleReasons: articleId === 4
        ? [{ articleId: 1, reason: 'Related by graph RAG evaluation.', sharedTopics: [] }]
        : [],
      topics: articleId === 4 ? ['Graph RAG'] : [],
    }),
  };
}
```

- [ ] **Step 2: Run test to verify failure**

Run:

```bash
node --test experiments/scripts/retrieval-benchmark/graph-runner.test.mjs
```

Expected:

```text
Cannot find module
```

- [ ] **Step 3: Implement graph runner**

Create `graph-runner.mjs`:

```js
import { readFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { createGraphContextClient } from './graph-client.mjs';
import { calculateGraphQueryMetrics, calculateGraphSummaryMetrics } from './graph-metrics.mjs';
import { writeGraphArtifacts } from './graph-report-writer.mjs';

const SOURCE_ARTICLE_SELECTION = 'public_search_top_k';
const GRAPH_CONTEXT_ENDPOINT = '/api/articles/{id}/graph-context';
const REASON_SOURCE = 'stored_projection_reason';

export async function runGraphAwareEvaluation({
  labels,
  labelsPath,
  baseUrl,
  outputDir,
  k,
  generatedAt,
  publicRun,
  graphClient = createGraphContextClient({ baseUrl }),
}) {
  const labelsSha256 = await sha256File(labelsPath);
  const publicRunByQuery = new Map(publicRun.queries.map((query) => [query.query, query]));
  const queries = [];

  for (const labeledQuery of labels.queries) {
    const publicQueryRun = publicRunByQuery.get(labeledQuery.query);
    if (!publicQueryRun) {
      throw new Error(`Missing public run for graph-aware query "${labeledQuery.query}".`);
    }

    queries.push(await buildGraphRunQuery({ labeledQuery, publicQueryRun, k, graphClient }));
  }

  const run = {
    labelsPath,
    labelsSha256,
    catalogId: labels.catalogId,
    catalogArticleCount: labels.catalogArticleCount,
    baseUrl,
    k,
    generatedAt,
    sourceArticleSelection: SOURCE_ARTICLE_SELECTION,
    graphContextEndpoint: GRAPH_CONTEXT_ENDPOINT,
    reasonSource: REASON_SOURCE,
    queries,
  };
  const byQueryMetrics = labels.queries.map((labeledQuery) => calculateGraphQueryMetrics({
    labeledQuery,
    graphRunQuery: queries.find((query) => query.query === labeledQuery.query),
    k,
  }));
  const summary = calculateGraphSummaryMetrics({
    catalogId: labels.catalogId,
    catalogArticleCount: labels.catalogArticleCount,
    k,
    generatedAt,
    byQueryMetrics,
  });
  const artifacts = await writeGraphArtifacts({ outputDir, run, byQueryMetrics, summary });

  return { run, byQueryMetrics, summary, artifacts };
}
```

Add query builder:

```js
async function buildGraphRunQuery({ labeledQuery, publicQueryRun, k, graphClient }) {
  const searchRankedArticleIds = publicQueryRun.rankedArticleIds.slice(0, k);
  const sourceArticleIds = [...new Set(searchRankedArticleIds)];
  const contexts = [];

  for (const articleId of sourceArticleIds) {
    try {
      const detail = await graphClient.fetchArticleDetail(articleId);
      const graphContext = await graphClient.fetchGraphContextRow(articleId);
      contexts.push({
        ...graphContext,
        relatedArticleIds: detail.relatedArticleIds,
      });
    } catch (error) {
      contexts.push({
        articleId,
        status: 'FAILED',
        latencyMs: null,
        failureReason: error instanceof Error ? error.message : String(error),
        emptyContext: false,
        relatedArticleIds: [],
        relatedArticleReasons: [],
        topics: [],
      });
    }
  }

  return {
    query: labeledQuery.query,
    positiveArticleIds: [...new Set([...labeledQuery.strongArticleIds, ...labeledQuery.acceptableArticleIds])],
    searchRankedArticleIds,
    sourceArticleIds,
    contexts,
  };
}

async function sha256File(path) {
  const content = await readFile(path);
  return createHash('sha256').update(content).digest('hex');
}
```

- [ ] **Step 4: Run graph runner tests**

Run:

```bash
node --test experiments/scripts/retrieval-benchmark/graph-runner.test.mjs
```

Expected:

```text
# pass
```

## Task 6: Integrate Graph Runner With Existing Runner

**Files:**
- Modify: `experiments/scripts/retrieval-benchmark/runner.mjs`
- Modify: `experiments/scripts/retrieval-benchmark/runner.test.mjs`
- Modify: `experiments/scripts/retrieval-benchmark.mjs`

- [ ] **Step 1: Write failing integration tests**

Add runner tests:

```js
test('runRetrievalBenchmark adds graph artifacts in public smoke mode when requested', async () => {
  const workspace = await mkdtemp(join(tmpdir(), 'sigak-runner-'));
  const labelsPath = join(workspace, 'labels.json');
  const outputDir = join(workspace, 'results');

  await writeFile(labelsPath, JSON.stringify(labelDocument()), 'utf8');

  try {
    const result = await runRetrievalBenchmark({
      labelsPath,
      baseUrl: 'http://localhost:8080',
      outputDir,
      k: 5,
      includeGraphContext: true,
      searchClient: { searchArticles: async () => ({ rankedArticleIds: [4, 1], latencyMs: 20 }) },
      graphClient: fakeGraphClient(),
      generatedAt: '2026-06-04T00:00:00.000Z',
    });

    assert.equal(result.graph.summary.evaluatedQueryCount, 1);
    assert.match(await readFile(join(outputDir, 'graph-context.metrics.summary.json'), 'utf8'), /macroGraphContextCoverageAtK/);
  } finally {
    await rm(workspace, { recursive: true, force: true });
  }
});

test('runRetrievalBenchmark requires public run when graph context is requested in comparison mode', async () => {
  await assert.rejects(
    () => runRetrievalBenchmark({
      labelsPath: 'labels.json',
      baseUrl: 'http://localhost:8080',
      outputDir: 'out',
      k: 5,
      systems: ['keyword'],
      includeGraphContext: true,
    }),
    /Graph-aware evaluation requires a public run/
  );
});
```

Add helpers or reuse existing helpers in `runner.test.mjs`:

```js
function labelDocument() {
  return {
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
  };
}

function fakeGraphClient() {
  return {
    fetchArticleDetail: async (articleId) => ({ articleId, relatedArticleIds: articleId === 4 ? [1] : [] }),
    fetchGraphContextRow: async (articleId) => ({
      articleId,
      status: 'COMPLETED',
      latencyMs: 10,
      emptyContext: articleId !== 4,
      relatedArticleReasons: articleId === 4
        ? [{ articleId: 1, reason: 'Related by graph RAG evaluation.', sharedTopics: [] }]
        : [],
      topics: articleId === 4 ? ['Graph RAG'] : [],
    }),
  };
}
```

- [ ] **Step 2: Run runner tests to verify failure**

Run:

```bash
node --test experiments/scripts/retrieval-benchmark/runner.test.mjs
```

Expected:

```text
not ok
# graph is undefined
```

- [ ] **Step 3: Integrate graph runner**

In `runner.mjs`, import:

```js
import { runGraphAwareEvaluation } from './graph-runner.mjs';
```

Pass `includeGraphContext = false` and `graphClient` through both runner modes.

In public smoke mode after public artifacts are written:

```js
const graph = includeGraphContext
  ? await runGraphAwareEvaluation({
    labels,
    labelsPath,
    baseUrl,
    outputDir,
    k,
    generatedAt,
    publicRun: run,
    graphClient,
  })
  : undefined;

return { run, byQueryMetrics, summary, artifacts, ...(graph ? { graph } : {}) };
```

In comparison mode after `runsBySystem.public` is built:

```js
if (includeGraphContext && !runsBySystem.public) {
  throw new Error('Graph-aware evaluation requires a public run. Include public in --systems or omit --systems.');
}

const graph = includeGraphContext
  ? await runGraphAwareEvaluation({
    labels,
    labelsPath,
    baseUrl,
    outputDir,
    k,
    generatedAt,
    publicRun: runsBySystem.public,
    graphClient,
  })
  : undefined;
```

Return `graph` when present.

- [ ] **Step 4: Update CLI entrypoint output**

In `retrieval-benchmark.mjs`, add:

```js
if (result.graph) {
  console.log(`Graph-aware evaluation complete: ${result.graph.artifacts.reportPath}`);
  console.log(`Graph queries: ${result.graph.summary.evaluatedQueryCount}`);
  console.log(`Graph Context Coverage@${result.graph.summary.k}: ${result.graph.summary.macroGraphContextCoverageAtK}`);
}
```

- [ ] **Step 5: Run focused Node tests**

Run:

```bash
node --test experiments/scripts/retrieval-benchmark/cli.test.mjs \
  experiments/scripts/retrieval-benchmark/runner.test.mjs \
  experiments/scripts/retrieval-benchmark/graph-client.test.mjs \
  experiments/scripts/retrieval-benchmark/graph-metrics.test.mjs \
  experiments/scripts/retrieval-benchmark/graph-report-writer.test.mjs \
  experiments/scripts/retrieval-benchmark/graph-runner.test.mjs
```

Expected:

```text
# pass
```

## Task 7: Documentation And Status Updates

**Files:**
- Modify: `experiments/README.md`
- Modify: `docs/STATUS.md`
- Modify: `docs/STATUS.ko.md`
- Modify: `docs/ROADMAP.md`
- Modify: `docs/ROADMAP.ko.md`
- Modify: `docs/blog/2026-06-04-dev-log.md`
- Modify: `docs/blog/topic-queue.md`

- [ ] **Step 1: Update experiments README**

Add a section after Retrieval System Comparison Runner:

````md
## Graph-Aware Evaluation Runner

Public article detail의 graph context가 related article baseline보다 어떤 설명 정보를 더 주는지 확인하려면 `--include-graph-context`를 사용한다.

```bash
node experiments/scripts/retrieval-benchmark.mjs \
  --labels=experiments/datasets/labels/search-labels.api-ready-2026-06-02.2026-06-02.json \
  --base-url=http://localhost:8080 \
  --output-dir=experiments/results/graph/latest \
  --systems=public \
  --include-graph-context \
  --k=5 \
  --limit=20
```

생성되는 graph artifact:

- `graph-context.runs.json`
- `graph-context.metrics.by-query.json`
- `graph-context.metrics.summary.json`
- `report.md`

주의:

- 이 평가는 검색 성능 평가가 아니라 article detail의 설명 가능성 평가다.
- 현재 3-query/6-article label set은 smoke only다.
- `averageGraphLatencyMs`는 public API round-trip이고 순수 Neo4j query latency가 아니다.
- graph reason은 stored projection reason이며 독립적으로 검증된 factual explanation이 아니다.
````

- [ ] **Step 2: Update STATUS and ROADMAP only after verification**

After tests and smoke pass, update:

- `docs/STATUS.md`
- `docs/STATUS.ko.md`
- `docs/ROADMAP.md`
- `docs/ROADMAP.ko.md`

Use wording that does not over-claim quality:

```md
Graph-aware evaluation runner is implemented and smoke-verified with the current 3-query/6-article label set. The report records search misses, related baseline coverage, graph reason coverage, topic coverage, public round-trip latency, and small-dataset warnings.
```

- [ ] **Step 3: Update dev-log and topic queue**

Create or update `docs/blog/2026-06-04-dev-log.md` with actual commands and observed output from this implementation session.

Update `docs/blog/topic-queue.md` existing candidate:

```md
## [candidate] Graph-aware detail을 검색 성능이 아니라 설명 가능성으로 평가해야 하는 이유
```

Add verified commands and smoke values only after running them.

## Task 8: Full Verification And Smoke

**Files:**
- No direct source edits unless a failure is found.

- [ ] **Step 1: Run full Node benchmark test suite**

Run:

```bash
node --test experiments/scripts/retrieval-benchmark/*.test.mjs
```

Expected:

```text
# pass
```

- [ ] **Step 2: Run repository whitespace check**

Run:

```bash
git diff --check
```

Expected: no output.

- [ ] **Step 3: Run graph-aware local smoke**

Start required services and backend in separate terminals or controlled sessions:

```bash
docker compose -f infra/docker-compose.yml up -d --pull never postgres elasticsearch qdrant neo4j ai
cd backend
SIGAK_INTERNAL_SEARCH_EVALUATION_ENABLED=true ./gradlew bootRun
```

Rebuild projections:

```bash
curl -sS -m 30 -X POST http://localhost:8080/api/internal/search-projections/articles/rebuild
curl -sS -m 30 -X POST http://localhost:8080/api/internal/search-projections/article-vectors/rebuild
curl -sS -m 30 -X POST http://localhost:8080/api/internal/graph-projections/articles/rebuild
```

Run graph-aware benchmark:

```bash
node experiments/scripts/retrieval-benchmark.mjs \
  --labels=experiments/datasets/labels/search-labels.api-ready-2026-06-02.2026-06-02.json \
  --base-url=http://localhost:8080 \
  --output-dir=experiments/results/graph/latest \
  --systems=public \
  --include-graph-context \
  --k=5 \
  --limit=20
```

Expected:

```text
Retrieval benchmark complete: experiments/results/graph/latest/report.md
Graph-aware evaluation complete: experiments/results/graph/latest/report.md
Graph queries: 3
```

Record the actual values printed for `Graph Context Coverage@5`, generated artifact paths, and graph summary warnings in `docs/STATUS.md`, `docs/STATUS.ko.md`, and `docs/blog/2026-06-04-dev-log.md`.

- [ ] **Step 4: Inspect graph artifacts**

Run:

```bash
node -e "const s=require('./experiments/results/graph/latest/graph-context.metrics.summary.json'); console.log(JSON.stringify({queries:s.evaluatedQueryCount,warnings:s.warnings,coverage:s.macroGraphContextCoverageAtK}, null, 2))"
test -f experiments/results/graph/latest/graph-context.runs.json
test -f experiments/results/graph/latest/graph-context.metrics.by-query.json
test -f experiments/results/graph/latest/graph-context.metrics.summary.json
test -f experiments/results/graph/latest/report.md
```

Expected:

```text
# JSON summary printed
# test commands exit 0
```

- [ ] **Step 5: Stop services started for smoke**

Stop only services or processes started in this task. Do not shut down unrelated user services.

## Self-Review Checklist

- Spec coverage:
  - `--include-graph-context` CLI option: Task 1
  - public graph context client: Task 2
  - search miss and graph coverage metrics: Task 3
  - graph artifacts and report: Task 4
  - runner integration with public run: Task 5 and Task 6
  - docs/status/dev-log/topic queue: Task 7
  - smoke verification: Task 8
- Placeholder scan:
  - This plan avoids unresolved placeholder markers and defines exact file paths, functions, tests, commands, and expected outcomes.
- Type consistency:
  - `includeGraphContext`, `graphContextCoverageAmongSearchHits`, `searchMissedPositiveIds`, `reasonSource`, `labelsSha256`, and artifact filenames match the design document.
- Scope check:
  - No backend API, frontend UI, dashboard, LLM judge, internal graph endpoint, or pure Neo4j query latency measurement is included in this implementation unit.
