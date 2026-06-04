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
        completedContext({ articleId: 4, relatedArticleIds: [1], graphRelatedReasons: [{ articleId: 1, reason: 'Related by graph RAG.' }] }),
        completedContext({ articleId: 2, relatedArticleIds: [], graphRelatedReasons: [] }),
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

test('calculateGraphQueryMetrics counts only non-blank graph reasons as reasoned coverage', () => {
  const metric = calculateGraphQueryMetrics({
    labeledQuery: {
      query: 'reason quality',
      intent: 'Reason coverage should ignore blank reasons.',
      strongArticleIds: [1, 3],
      acceptableArticleIds: [],
      notRelevantArticleIds: [],
    },
    graphRunQuery: {
      query: 'reason quality',
      searchRankedArticleIds: [2],
      sourceArticleIds: [2],
      contexts: [
        completedContext({
          articleId: 2,
          relatedArticleIds: [],
          graphRelatedReasons: [
            { articleId: 1, reason: null },
            { articleId: 3, reason: '  Shared evaluation topic.  ' },
          ],
        }),
      ],
    },
    k: 1,
  });

  assert.equal(metric.graphRelatedHitCount, 2);
  assert.equal(metric.graphReasonedHitCount, 1);
  assert.equal(metric.graphReasonedCoverageAtK, 0.5);
  assert.equal(metric.reasonCoverage, 0.5);
});

test('calculateGraphQueryMetrics ignores self-links for related baseline and graph hits', () => {
  const metric = calculateGraphQueryMetrics({
    labeledQuery: {
      query: 'self links',
      intent: 'Self-links should not inflate graph coverage.',
      strongArticleIds: [4],
      acceptableArticleIds: [1],
      notRelevantArticleIds: [],
    },
    graphRunQuery: {
      query: 'self links',
      searchRankedArticleIds: [4],
      sourceArticleIds: [4],
      contexts: [
        completedContext({
          articleId: 4,
          relatedArticleIds: [4, 1],
          graphRelatedReasons: [
            { articleId: 4, reason: 'Self reason.' },
            { articleId: 1, reason: 'Related reason.' },
          ],
        }),
      ],
    },
    k: 1,
  });

  assert.equal(metric.relatedBaselineHitCount, 1);
  assert.equal(metric.graphRelatedHitCount, 1);
  assert.equal(metric.graphReasonedHitCount, 1);
  assert.equal(metric.relatedBaselineCoverageAtK, 0.5);
  assert.equal(metric.graphContextCoverageAtK, 0.5);
});

test('calculateGraphQueryMetrics rejects contexts outside public search top k', () => {
  assert.throws(
    () => calculateGraphQueryMetrics({
      labeledQuery: {
        query: 'outside top k',
        intent: 'Context outside top-k should not change @K metrics.',
        strongArticleIds: [1],
        acceptableArticleIds: [],
        notRelevantArticleIds: [],
      },
      graphRunQuery: {
        query: 'outside top k',
        searchRankedArticleIds: [4],
        sourceArticleIds: [4, 99],
        contexts: [
          completedContext({ articleId: 4, relatedArticleIds: [], graphRelatedReasons: [] }),
          completedContext({ articleId: 99, relatedArticleIds: [1], graphRelatedReasons: [{ articleId: 1, reason: 'Outside top-k.' }] }),
        ],
      },
      k: 1,
    }),
    /outside public search top-k/
  );
});

test('calculateGraphQueryMetrics rejects invalid article ids instead of dropping them', () => {
  assert.throws(
    () => calculateGraphQueryMetrics({
      labeledQuery: {
        query: 'invalid ids',
        intent: 'Invalid ids should be visible as bad input.',
        strongArticleIds: [Number.NaN],
        acceptableArticleIds: [],
        notRelevantArticleIds: [],
      },
      graphRunQuery: {
        query: 'invalid ids',
        searchRankedArticleIds: [1],
        sourceArticleIds: [1],
        contexts: [],
      },
      k: 1,
    }),
    /positiveArticleIds/
  );
});

test('calculateGraphSummaryMetrics macro-averages metrics and reports rates, latency, and warnings', () => {
  const summary = calculateGraphSummaryMetrics({
    catalogId: 'api-ready-test',
    catalogArticleCount: 6,
    k: 2,
    generatedAt: '2026-06-04T00:00:00.000Z',
    byQueryMetrics: [
      {
        evaluatedArticleCount: 2,
        failedContextCount: 1,
        emptyContextCount: 1,
        searchPositiveCoverageAtK: 0.5,
        relatedBaselineCoverageAtK: 0.5,
        graphContextCoverageAtK: 0.5,
        graphContextCoverageAmongSearchHits: 0,
        graphReasonedCoverageAtK: 0.5,
        reasonCoverage: 1,
        topicContextCoverage: null,
        averageGraphLatencyMs: 20,
      },
    ],
  });

  assert.deepEqual(summary, {
    catalogId: 'api-ready-test',
    catalogArticleCount: 6,
    evaluatedQueryCount: 1,
    k: 2,
    macroSearchPositiveCoverageAtK: 0.5,
    macroRelatedBaselineCoverageAtK: 0.5,
    macroGraphContextCoverageAtK: 0.5,
    macroGraphContextCoverageAmongSearchHits: 0,
    macroGraphReasonedCoverageAtK: 0.5,
    macroReasonCoverage: 1,
    macroTopicContextCoverage: null,
    graphContextFailureRate: 0.5,
    emptyContextRate: 0.5,
    averageGraphLatencyMs: 20,
    generatedAt: '2026-06-04T00:00:00.000Z',
    warnings: [
      'label set is smaller than 10 reviewed queries, so this is a smoke graph evaluation',
      'catalog is smaller than 20 articles, so graph density is too small for quality claims',
      'graph latency is measured as public API round-trip, not pure Neo4j query latency',
      'graph reasons are stored projection reasons, not independently verified factual explanations',
    ],
  });
});

function labeledQuery() {
  return {
    query: 'graph rag failure',
    intent: 'Graph RAG 실패 유형과 평가 기준을 찾는다.',
    strongArticleIds: [4],
    acceptableArticleIds: [1],
    notRelevantArticleIds: [2],
  };
}

function completedContext({ articleId, relatedArticleIds, graphRelatedReasons, topics = [] }) {
  return {
    articleId,
    status: 'COMPLETED',
    latencyMs: 10,
    emptyContext: graphRelatedReasons.length === 0,
    relatedArticleIds,
    relatedArticleReasons: graphRelatedReasons.map((reason) => ({
      articleId: reason.articleId,
      reason: reason.reason,
      sharedTopics: reason.sharedTopics ?? [],
    })),
    topics,
  };
}
