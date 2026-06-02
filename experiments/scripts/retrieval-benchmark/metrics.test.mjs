import test from 'node:test';
import assert from 'node:assert/strict';
import {
  calculateComparisonMetrics,
  calculateQueryMetrics,
  calculateRunRowMetrics,
  calculateSummaryMetrics,
  calculateSystemSummaryMetrics,
} from './metrics.mjs';

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

test('calculateQueryMetrics respects custom k', () => {
  const metrics = calculateQueryMetrics({ labeledQuery, rankedArticleIds: [1, 2, 4], latencyMs: 30, k: 2 });

  assert.equal(metrics.recallAtK, 0.5);
  assert.equal(metrics.mrrAtK, 0);
});

test('calculateQueryMetrics counts duplicate result ids once for recall', () => {
  const metrics = calculateQueryMetrics({ labeledQuery, rankedArticleIds: [1, 1, 2, 3, 6], latencyMs: 30, k: 5 });

  assert.equal(metrics.recallAtK, 0.5);
});

test('calculateSummaryMetrics averages query metrics and preserves k', () => {
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

test('calculateRunRowMetrics preserves completed system metadata', () => {
  const metrics = calculateRunRowMetrics({
    labeledQuery,
    runItem: {
      query: 'graph rag failure',
      system: 'hybrid',
      status: 'COMPLETED',
      rankedArticleIds: [4, 1, 2],
      latencyMs: 20,
      failureReason: null,
      degraded: false,
      resolvedMode: 'HYBRID',
      staleCandidateCount: 0,
    },
    k: 5,
  });

  assert.equal(metrics.system, 'hybrid');
  assert.equal(metrics.status, 'COMPLETED');
  assert.equal(metrics.top1StrongHit, 1);
  assert.equal(metrics.resolvedMode, 'HYBRID');
});

test('calculateRunRowMetrics returns null quality metrics for failed rows', () => {
  const metrics = calculateRunRowMetrics({
    labeledQuery,
    runItem: {
      query: 'graph rag failure',
      system: 'hybrid',
      status: 'FAILED',
      rankedArticleIds: [],
      latencyMs: null,
      failureReason: 'QDRANT_SEARCH_FAILED',
      degraded: false,
      resolvedMode: null,
      staleCandidateCount: 0,
    },
    k: 5,
  });

  assert.equal(metrics.status, 'FAILED');
  assert.equal(metrics.top1StrongHit, null);
  assert.equal(metrics.recallAtK, null);
  assert.equal(metrics.mrrAtK, null);
  assert.equal(metrics.failureReason, 'QDRANT_SEARCH_FAILED');
});

test('calculateSystemSummaryMetrics separates macro and effective metrics', () => {
  const rows = [
    { system: 'hybrid', status: 'COMPLETED', top1StrongHit: 1, recallAtK: 1, mrrAtK: 1, latencyMs: 20, staleCandidateCount: 0, degraded: false, resolvedMode: 'HYBRID' },
    { system: 'hybrid', status: 'FAILED', top1StrongHit: null, recallAtK: null, mrrAtK: null, latencyMs: null, staleCandidateCount: 0, degraded: false, resolvedMode: null, failureReason: 'QDRANT_SEARCH_FAILED' },
  ];

  const summary = calculateSystemSummaryMetrics({ system: 'hybrid', rows });

  assert.equal(summary.attemptedQueryCount, 2);
  assert.equal(summary.completedQueryCount, 1);
  assert.equal(summary.failedQueryCount, 1);
  assert.equal(summary.failureRate, 0.5);
  assert.equal(summary.macroRecallAtK, 1);
  assert.equal(summary.effectiveRecallAtK, 0.5);
  assert.deepEqual(summary.failureReasonCounts, { QDRANT_SEARCH_FAILED: 1 });
});

test('calculateSystemSummaryMetrics records degraded public runs separately', () => {
  const summary = calculateSystemSummaryMetrics({
    system: 'public',
    rows: [
      { system: 'public', status: 'COMPLETED', top1StrongHit: 0, recallAtK: 0.5, mrrAtK: 0, latencyMs: 25, staleCandidateCount: 0, degraded: true, resolvedMode: 'VECTOR_ONLY', failureReason: 'KEYWORD_SEARCH_FAILED' },
    ],
  });

  assert.equal(summary.degradedQueryCount, 1);
  assert.equal(summary.degradedRate, 1);
  assert.deepEqual(summary.degradedModeCounts, { VECTOR_ONLY: 1 });
});

test('calculateComparisonMetrics aggregates per-system summaries and warnings', () => {
  const comparison = calculateComparisonMetrics({
    catalogId: 'api-ready-test',
    catalogArticleCount: 6,
    k: 5,
    generatedAt: '2026-06-02T00:00:00.000Z',
    systems: ['keyword', 'public'],
    byQueryMetrics: [
      { query: 'graph rag failure', system: 'keyword', status: 'COMPLETED', top1StrongHit: 0, recallAtK: 1, mrrAtK: 0, latencyMs: 10, degraded: false, resolvedMode: 'KEYWORD', staleCandidateCount: 0 },
      { query: 'graph rag failure', system: 'public', status: 'COMPLETED', top1StrongHit: 1, recallAtK: 1, mrrAtK: 1, latencyMs: 25, degraded: true, resolvedMode: 'VECTOR_ONLY', staleCandidateCount: 0, failureReason: 'KEYWORD_SEARCH_FAILED' },
    ],
  });

  assert.equal(comparison.systems.length, 2);
  assert.deepEqual(comparison.bestObservedSystemsByMetric.macroRecallAtK, ['keyword', 'public']);
  assert.match(comparison.warnings.join('\n'), /label set is smaller than 10 reviewed queries/);
  assert.match(comparison.warnings.join('\n'), /public has degraded query runs/);
});
