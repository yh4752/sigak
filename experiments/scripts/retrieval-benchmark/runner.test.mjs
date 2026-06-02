import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { runRetrievalBenchmark } from './runner.mjs';

test('runRetrievalBenchmark evaluates reviewed queries and writes artifacts', async () => {
  const workspace = await mkdtemp(join(tmpdir(), 'sigak-runner-'));
  const labelsPath = join(workspace, 'labels.json');
  const outputDir = join(workspace, 'results');
  const searchedQueries = [];

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
        searchArticles: async (query) => {
          searchedQueries.push(query);
          return { rankedArticleIds: [4, 1, 2], latencyMs: 20 };
        },
      },
      generatedAt: '2026-06-02T00:00:00.000Z',
    });

    assert.deepEqual(searchedQueries, ['graph rag failure']);
    assert.equal(result.run.system, 'hybrid');
    assert.equal(result.summary.evaluatedQueryCount, 1);
    assert.equal(result.summary.macroTop1StrongHit, 1);
    assert.match(await readFile(join(outputDir, 'report.md'), 'utf8'), /graph rag failure/);
  } finally {
    await rm(workspace, { recursive: true, force: true });
  }
});
