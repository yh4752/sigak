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
