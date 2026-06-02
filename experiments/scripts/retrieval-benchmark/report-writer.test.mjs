import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import {
  buildComparisonMarkdownReport,
  buildMarkdownReport,
  writeBenchmarkArtifacts,
  writeComparisonArtifacts,
} from './report-writer.mjs';

const run = {
  labelsPath: 'labels.json',
  baseUrl: 'http://localhost:8080',
  system: 'public',
  k: 5,
  queries: [{ query: 'graph rag failure', rankedArticleIds: [4, 1], latencyMs: 42 }],
};

const byQueryMetrics = [{
  query: 'graph rag failure',
  intent: 'Graph RAG 실패 유형과 평가 기준을 찾는다.',
  strongArticleIds: [4],
  acceptableArticleIds: [1],
  resultArticleIds: [4, 1],
  top1StrongHit: 1,
  recallAtK: 1,
  mrrAtK: 1,
  latencyMs: 42,
}];

const summary = {
  catalogId: 'api-ready-test',
  catalogArticleCount: 6,
  evaluatedQueryCount: 1,
  k: 5,
  macroTop1StrongHit: 1,
  macroRecallAtK: 1,
  macroMrrAtK: 1,
  averageLatencyMs: 42,
  generatedAt: '2026-06-02T00:00:00.000Z',
};

const comparisonRunsBySystem = {
  keyword: {
    labelsPath: 'labels.json',
    baseUrl: 'http://localhost:8080',
    system: 'keyword',
    k: 5,
    limit: 20,
    queries: [{ query: 'graph rag failure', system: 'keyword', status: 'COMPLETED', rankedArticleIds: [1, 4], latencyMs: 12 }],
  },
  public: {
    labelsPath: 'labels.json',
    baseUrl: 'http://localhost:8080',
    system: 'public',
    k: 5,
    limit: 20,
    queries: [{ query: 'graph rag failure', system: 'public', status: 'COMPLETED', rankedArticleIds: [4, 1], latencyMs: 25, degraded: true, resolvedMode: 'VECTOR_ONLY', failureReason: 'KEYWORD_SEARCH_FAILED' }],
  },
};

const comparisonByQueryMetrics = [
  {
    query: 'graph rag failure',
    intent: 'Graph RAG 실패 유형과 평가 기준을 찾는다.',
    system: 'keyword',
    status: 'COMPLETED',
    strongArticleIds: [4],
    acceptableArticleIds: [1],
    resultArticleIds: [1, 4],
    top1StrongHit: 0,
    recallAtK: 1,
    mrrAtK: 0.5,
    latencyMs: 12,
    failureReason: null,
    degraded: false,
    resolvedMode: 'KEYWORD',
    staleCandidateCount: 0,
  },
  {
    query: 'graph rag failure',
    intent: 'Graph RAG 실패 유형과 평가 기준을 찾는다.',
    system: 'public',
    status: 'COMPLETED',
    strongArticleIds: [4],
    acceptableArticleIds: [1],
    resultArticleIds: [4, 1],
    top1StrongHit: 1,
    recallAtK: 1,
    mrrAtK: 1,
    latencyMs: 25,
    failureReason: 'KEYWORD_SEARCH_FAILED',
    degraded: true,
    resolvedMode: 'VECTOR_ONLY',
    staleCandidateCount: 0,
  },
];

const comparisonBySystemMetrics = [
  {
    system: 'keyword',
    attemptedQueryCount: 1,
    completedQueryCount: 1,
    failedQueryCount: 0,
    failureRate: 0,
    degradedQueryCount: 0,
    degradedRate: 0,
    failureReasonCounts: {},
    degradedModeCounts: {},
    macroTop1StrongHit: 0,
    macroRecallAtK: 1,
    macroMrrAtK: 0.5,
    effectiveTop1StrongHit: 0,
    effectiveRecallAtK: 1,
    effectiveMrrAtK: 0.5,
    averageLatencyMs: 12,
    totalStaleCandidateCount: 0,
  },
  {
    system: 'public',
    attemptedQueryCount: 1,
    completedQueryCount: 1,
    failedQueryCount: 0,
    failureRate: 0,
    degradedQueryCount: 1,
    degradedRate: 1,
    failureReasonCounts: {},
    degradedModeCounts: { VECTOR_ONLY: 1 },
    macroTop1StrongHit: 1,
    macroRecallAtK: 1,
    macroMrrAtK: 1,
    effectiveTop1StrongHit: 1,
    effectiveRecallAtK: 1,
    effectiveMrrAtK: 1,
    averageLatencyMs: 25,
    totalStaleCandidateCount: 0,
  },
];

const comparison = {
  catalogId: 'api-ready-test',
  catalogArticleCount: 6,
  evaluatedQueryCount: 1,
  k: 5,
  generatedAt: '2026-06-02T00:00:00.000Z',
  systems: comparisonBySystemMetrics,
  bestObservedSystemsByMetric: {
    macroTop1StrongHit: ['public'],
    macroRecallAtK: ['keyword', 'public'],
    macroMrrAtK: ['public'],
    effectiveRecallAtK: ['keyword', 'public'],
    averageLatencyMs: ['keyword'],
  },
  warnings: [
    'label set is smaller than 10 reviewed queries, so this is a smoke benchmark',
    'catalog article count is below 20, so ranking difficulty is still low',
    'public has degraded query runs',
  ],
};

test('writeBenchmarkArtifacts writes run, metrics, and markdown report', async () => {
  const outputDir = await mkdtemp(join(tmpdir(), 'sigak-benchmark-'));

  try {
    const artifacts = await writeBenchmarkArtifacts({ outputDir, run, byQueryMetrics, summary });

    assert.equal(artifacts.runPath, join(outputDir, 'runs.public.json'));
    assert.equal(artifacts.byQueryPath, join(outputDir, 'metrics.by-query.json'));
    assert.equal(artifacts.summaryPath, join(outputDir, 'metrics.summary.json'));
    assert.equal(artifacts.reportPath, join(outputDir, 'report.md'));
    assert.match(await readFile(join(outputDir, 'runs.public.json'), 'utf8'), /graph rag failure/);
    assert.match(await readFile(join(outputDir, 'metrics.summary.json'), 'utf8'), /macroRecallAtK/);
    assert.match(await readFile(join(outputDir, 'metrics.by-query.json'), 'utf8'), /top1StrongHit/);
    assert.match(await readFile(join(outputDir, 'report.md'), 'utf8'), /Top1 Strong Hit/);
  } finally {
    await rm(outputDir, { recursive: true, force: true });
  }
});

test('buildMarkdownReport explains smoke benchmark metrics and limits', () => {
  const markdown = buildMarkdownReport({ run, byQueryMetrics, summary });

  assert.match(markdown, /smoke benchmark/);
  assert.match(markdown, /Top1 Strong Hit/);
  assert.match(markdown, /Recall@5/);
  assert.match(markdown, /MRR@5/);
  assert.match(markdown, /LatencyMs/);
});

test('writeComparisonArtifacts writes per-system runs, comparison metrics, and markdown report', async () => {
  const outputDir = await mkdtemp(join(tmpdir(), 'sigak-benchmark-'));

  try {
    const artifacts = await writeComparisonArtifacts({
      outputDir,
      runsBySystem: comparisonRunsBySystem,
      byQueryMetrics: comparisonByQueryMetrics,
      bySystemMetrics: comparisonBySystemMetrics,
      comparison,
    });

    assert.equal(artifacts.runPaths.keyword.endsWith('runs.keyword.json'), true);
    assert.equal(artifacts.bySystemPath.endsWith('metrics.by-system.json'), true);
    assert.equal(artifacts.comparisonPath.endsWith('metrics.comparison.json'), true);
    assert.match(await readFile(join(outputDir, 'runs.public.json'), 'utf8'), /VECTOR_ONLY/);
    assert.match(await readFile(join(outputDir, 'metrics.comparison.json'), 'utf8'), /bestObservedSystemsByMetric/);
    assert.match(await readFile(join(outputDir, 'report.md'), 'utf8'), /Strict HYBRID/);
  } finally {
    await rm(outputDir, { recursive: true, force: true });
  }
});

test('buildComparisonMarkdownReport explains strict hybrid and public behavior', () => {
  const markdown = buildComparisonMarkdownReport({
    byQueryMetrics: comparisonByQueryMetrics,
    bySystemMetrics: comparisonBySystemMetrics,
    comparison,
  });

  assert.match(markdown, /best observed system in this smoke run/);
  assert.match(markdown, /Failure Rate/);
  assert.match(markdown, /Degraded Rate/);
  assert.match(markdown, /Strict HYBRID/);
  assert.match(markdown, /PUBLIC is user-visible/);
  assert.match(markdown, /PUBLIC is not sent to the internal evaluation endpoint/);
  assert.match(markdown, /label set is smaller than 10 reviewed queries/);
});
