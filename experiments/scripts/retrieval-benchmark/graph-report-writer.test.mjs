import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { buildGraphMarkdownReport, writeGraphArtifacts } from './graph-report-writer.mjs';

const run = {
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
  queries: [
    {
      query: 'graph|rag failure',
      searchRankedArticleIds: [4, 2],
      sourceArticleIds: [4, 2],
      contexts: [
        {
          articleId: 4,
          status: 'COMPLETED',
          latencyMs: 27,
          emptyContext: false,
          relatedArticleIds: [1],
          relatedArticleReasons: [
            {
              articleId: 1,
              reason: 'Stored projection reason about Graph RAG evaluation.',
              sharedTopics: ['Graph RAG'],
            },
          ],
          topics: ['Graph RAG'],
        },
        {
          articleId: 2,
          status: 'FAILED',
          latencyMs: 13,
          failureReason: 'Graph context API failed with status 503.',
          emptyContext: false,
          relatedArticleIds: [],
          relatedArticleReasons: [],
          topics: [],
        },
      ],
    },
  ],
};

const byQueryMetrics = [
  {
    query: 'graph|rag failure',
    intent: 'Graph-aware explanation smoke.',
    evaluatedArticleCount: 2,
    positiveArticleIds: [4, 1],
    searchRankedArticleIds: [4, 2],
    searchPositiveHitIds: [4],
    searchMissedPositiveIds: [1],
    relatedBaselineHitCount: 1,
    graphRelatedHitCount: 1,
    graphReasonedHitCount: 1,
    searchPositiveCoverageAtK: 0.5,
    relatedBaselineCoverageAtK: 0.5,
    graphContextCoverageAtK: 0.5,
    graphContextCoverageAmongSearchHits: 0,
    graphReasonedCoverageAtK: 0.5,
    reasonCoverage: 1,
    topicContextCoverage: 0.5,
    emptyContextCount: 0,
    failedContextCount: 1,
    averageGraphLatencyMs: 20,
  },
];

const summary = {
  catalogId: 'api-ready-test',
  catalogArticleCount: 6,
  evaluatedQueryCount: 1,
  k: 5,
  macroSearchPositiveCoverageAtK: 0.5,
  macroRelatedBaselineCoverageAtK: 0.5,
  macroGraphContextCoverageAtK: 0.5,
  macroGraphContextCoverageAmongSearchHits: 0,
  macroGraphReasonedCoverageAtK: 0.5,
  macroReasonCoverage: 1,
  macroTopicContextCoverage: 0.5,
  graphContextFailureRate: 0.5,
  emptyContextRate: 0,
  averageGraphLatencyMs: 20,
  generatedAt: '2026-06-04T00:00:00.000Z',
  warnings: [
    'label set is smaller than 10 reviewed queries, so this is a smoke graph evaluation',
    'catalog is smaller than 20 articles, so graph density is too small for quality claims',
    'graph latency is measured as public API round-trip, not pure Neo4j query latency',
    'graph reasons are stored projection reasons, not independently verified factual explanations',
  ],
};

test('writeGraphArtifacts writes graph run, metrics, summary, and report files', async () => {
  const outputDir = await mkdtemp(join(tmpdir(), 'sigak-graph-report-'));

  try {
    const artifacts = await writeGraphArtifacts({ outputDir, run, byQueryMetrics, summary });

    assert.equal(artifacts.runPath, join(outputDir, 'graph-context.runs.json'));
    assert.equal(artifacts.byQueryPath, join(outputDir, 'graph-context.metrics.by-query.json'));
    assert.equal(artifacts.summaryPath, join(outputDir, 'graph-context.metrics.summary.json'));
    assert.equal(artifacts.reportPath, join(outputDir, 'report.md'));
    assert.match(await readFile(artifacts.runPath, 'utf8'), /"reasonSource": "stored_projection_reason",\n/);
    assert.match(await readFile(artifacts.byQueryPath, 'utf8'), /"searchMissedPositiveIds"/);
    assert.match(await readFile(artifacts.summaryPath, 'utf8'), /"macroGraphContextCoverageAtK"/);
    assert.match(await readFile(artifacts.reportPath, 'utf8'), /# Graph-Aware Evaluation Smoke Report/);
  } finally {
    await rm(outputDir, { recursive: true, force: true });
  }
});

test('writeGraphArtifacts appends graph report when report already exists', async () => {
  const outputDir = await mkdtemp(join(tmpdir(), 'sigak-graph-report-'));
  const reportPath = join(outputDir, 'report.md');

  try {
    await writeFile(reportPath, '# Retrieval Benchmark Smoke Report\n\nExisting retrieval content.\n', 'utf8');

    await writeGraphArtifacts({ outputDir, run, byQueryMetrics, summary });

    const report = await readFile(reportPath, 'utf8');
    assert.match(report, /^# Retrieval Benchmark Smoke Report/);
    assert.match(report, /Existing retrieval content/);
    assert.match(report, /\n\n---\n\n# Graph-Aware Evaluation Smoke Report/);
  } finally {
    await rm(outputDir, { recursive: true, force: true });
  }
});

test('buildGraphMarkdownReport explains graph evaluation interpretation and limits', () => {
  const markdown = buildGraphMarkdownReport({ run, byQueryMetrics, summary });

  assert.match(markdown, /# Graph-Aware Evaluation Smoke Report/);
  assert.match(markdown, /search miss/i);
  assert.match(markdown, /stored projection reasons/i);
  assert.match(markdown, /public API round-trip/i);
  assert.match(markdown, /abc123/);
  assert.match(markdown, /public_search_top_k/);
  assert.match(markdown, /\/api\/articles\/\{id\}\/graph-context/);
  assert.match(markdown, /stored_projection_reason/);
  assert.match(markdown, /2026-06-04T00:00:00.000Z/);
  assert.match(markdown, /smoke graph evaluation/i);
  assert.match(markdown, /graph evaluation is article detail explanation evidence, not query ranking/i);
  assert.match(markdown, /small/i);
  assert.match(markdown, /dataset/i);
});

test('buildGraphMarkdownReport escapes pipe characters in query table text', () => {
  const markdown = buildGraphMarkdownReport({ run, byQueryMetrics, summary });

  assert.match(markdown, /\| graph\\\|rag failure \|/);
});
