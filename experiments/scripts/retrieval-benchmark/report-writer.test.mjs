import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { buildMarkdownReport, writeBenchmarkArtifacts } from './report-writer.mjs';

const run = {
  labelsPath: 'labels.json',
  baseUrl: 'http://localhost:8080',
  system: 'hybrid',
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

test('writeBenchmarkArtifacts writes run, metrics, and markdown report', async () => {
  const outputDir = await mkdtemp(join(tmpdir(), 'sigak-benchmark-'));

  try {
    const artifacts = await writeBenchmarkArtifacts({ outputDir, run, byQueryMetrics, summary });

    assert.equal(artifacts.runPath, join(outputDir, 'runs.hybrid.json'));
    assert.equal(artifacts.byQueryPath, join(outputDir, 'metrics.by-query.json'));
    assert.equal(artifacts.summaryPath, join(outputDir, 'metrics.summary.json'));
    assert.equal(artifacts.reportPath, join(outputDir, 'report.md'));
    assert.match(await readFile(join(outputDir, 'runs.hybrid.json'), 'utf8'), /graph rag failure/);
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
