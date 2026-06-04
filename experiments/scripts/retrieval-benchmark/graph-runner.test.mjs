import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { runGraphAwareEvaluation } from './graph-runner.mjs';

test('runGraphAwareEvaluation creates graph artifacts from a public run with fake graph client', async () => {
  const workspace = await mkdtemp(join(tmpdir(), 'sigak-graph-runner-'));
  const labelsPath = join(workspace, 'labels.json');
  const outputDir = join(workspace, 'results');
  const labelsContent = JSON.stringify(labelDocument({
    queries: [{
      query: 'graph rag failure',
      intent: 'Graph RAG 실패 유형과 평가 기준을 찾는다.',
      labels: [
        { articleId: 4, relevance: 'strong', note: '' },
        { articleId: 1, relevance: 'acceptable', note: '' },
      ],
    }],
  }));

  await writeFile(labelsPath, labelsContent, 'utf8');

  try {
    const result = await runGraphAwareEvaluation({
      labels: {
        catalogId: 'api-ready-test',
        catalogArticleCount: 6,
        queries: [{
          query: 'graph rag failure',
          intent: 'Graph RAG 실패 유형과 평가 기준을 찾는다.',
          strongArticleIds: [4],
          acceptableArticleIds: [1],
          notRelevantArticleIds: [],
        }],
      },
      labelsPath,
      baseUrl: 'http://localhost:8080',
      outputDir,
      k: 2,
      generatedAt: '2026-06-04T00:00:00.000Z',
      publicRun: publicRun({
        queries: [{
          query: 'graph rag failure',
          rankedArticleIds: [4, 2, 1],
        }],
      }),
      graphClient: fakeGraphClient({
        details: new Map([
          [4, { articleId: 4, relatedArticleIds: [1] }],
          [2, { articleId: 2, relatedArticleIds: [] }],
        ]),
        graphRows: new Map([
          [4, completedGraphRow({
            articleId: 4,
            relatedArticleReasons: [{ articleId: 1, reason: 'Stored projection reason.', sharedTopics: ['Graph RAG'] }],
            topics: ['Graph RAG'],
          })],
          [2, completedGraphRow({ articleId: 2, relatedArticleReasons: [], topics: [] })],
        ]),
      }),
    });

    assert.equal(result.run.labelsPath, labelsPath);
    assert.equal(result.run.labelsSha256, sha256(labelsContent));
    assert.equal(result.run.reasonSource, 'stored_projection_reason');
    assert.equal(result.run.graphContextEndpoint, '/api/articles/{id}/graph-context');
    assert.equal(result.run.sourceArticleSelection, 'public_search_top_k');
    assert.deepEqual(result.run.queries[0], {
      query: 'graph rag failure',
      positiveArticleIds: [4, 1],
      searchRankedArticleIds: [4, 2],
      searchPositiveHitIds: [4],
      searchMissedPositiveIds: [1],
      sourceArticleIds: [4, 2],
      contexts: [
        {
          articleId: 4,
          status: 'COMPLETED',
          latencyMs: 12,
          emptyContext: false,
          relatedArticleReasons: [{ articleId: 1, reason: 'Stored projection reason.', sharedTopics: ['Graph RAG'] }],
          topics: ['Graph RAG'],
          relatedArticleIds: [1],
        },
        {
          articleId: 2,
          status: 'COMPLETED',
          latencyMs: 12,
          emptyContext: true,
          relatedArticleReasons: [],
          topics: [],
          relatedArticleIds: [],
        },
      ],
    });
    assert.equal(result.byQueryMetrics[0].graphContextCoverageAtK, 0.5);
    assert.equal(result.summary.evaluatedQueryCount, 1);
    assert.ok(result.artifacts.runPath.endsWith('graph-context.runs.json'));

    const runArtifact = JSON.parse(await readFile(join(outputDir, 'graph-context.runs.json'), 'utf8'));
    assert.equal(runArtifact.reasonSource, 'stored_projection_reason');
    assert.equal(runArtifact.labelsSha256, sha256(labelsContent));
  } finally {
    await rm(workspace, { recursive: true, force: true });
  }
});

test('runGraphAwareEvaluation throws when a reviewed label query is missing from public run', async () => {
  const workspace = await mkdtemp(join(tmpdir(), 'sigak-graph-runner-'));
  const labelsPath = join(workspace, 'labels.json');

  await writeFile(labelsPath, JSON.stringify(labelDocument({
    queries: [{
      query: 'missing graph query',
      intent: 'Missing public run should be visible.',
      labels: [{ articleId: 1, relevance: 'strong', note: '' }],
    }],
  })), 'utf8');

  try {
    await assert.rejects(
      () => runGraphAwareEvaluation({
        labels: {
          catalogId: 'api-ready-test',
          catalogArticleCount: 6,
          queries: [{
            query: 'missing graph query',
            intent: 'Missing public run should be visible.',
            strongArticleIds: [1],
            acceptableArticleIds: [],
            notRelevantArticleIds: [],
          }],
        },
        labelsPath,
        baseUrl: 'http://localhost:8080',
        outputDir: join(workspace, 'results'),
        k: 2,
        generatedAt: '2026-06-04T00:00:00.000Z',
        publicRun: publicRun({ queries: [{ query: 'other query', rankedArticleIds: [1] }] }),
        graphClient: fakeGraphClient(),
      }),
      /Missing public run for graph-aware query "missing graph query"/
    );
  } finally {
    await rm(workspace, { recursive: true, force: true });
  }
});

test('runGraphAwareEvaluation deduplicates sourceArticleIds before fetching graph context', async () => {
  const workspace = await mkdtemp(join(tmpdir(), 'sigak-graph-runner-'));
  const labelsPath = join(workspace, 'labels.json');
  const calls = [];

  await writeFile(labelsPath, JSON.stringify(labelDocument({
    queries: [{
      query: 'dedupe source articles',
      intent: 'Duplicate public search results should be fetched once.',
      labels: [{ articleId: 4, relevance: 'strong', note: '' }],
    }],
  })), 'utf8');

  try {
    await runGraphAwareEvaluation({
      labels: {
        catalogId: 'api-ready-test',
        catalogArticleCount: 6,
        queries: [{
          query: 'dedupe source articles',
          intent: 'Duplicate public search results should be fetched once.',
          strongArticleIds: [4],
          acceptableArticleIds: [],
          notRelevantArticleIds: [],
        }],
      },
      labelsPath,
      baseUrl: 'http://localhost:8080',
      outputDir: join(workspace, 'results'),
      k: 4,
      generatedAt: '2026-06-04T00:00:00.000Z',
      publicRun: publicRun({ queries: [{ query: 'dedupe source articles', rankedArticleIds: [4, 4, 1, 4] }] }),
      graphClient: {
        async fetchArticleDetail(articleId) {
          calls.push(['detail', articleId]);
          return { articleId, relatedArticleIds: [] };
        },
        async fetchGraphContextRow(articleId) {
          calls.push(['graph', articleId]);
          return completedGraphRow({ articleId, relatedArticleReasons: [], topics: [] });
        },
      },
    });

    assert.deepEqual(calls, [
      ['detail', 4],
      ['graph', 4],
      ['detail', 1],
      ['graph', 1],
    ]);
  } finally {
    await rm(workspace, { recursive: true, force: true });
  }
});

test('runGraphAwareEvaluation records failed context row when article detail fetch throws', async () => {
  const workspace = await mkdtemp(join(tmpdir(), 'sigak-graph-runner-'));
  const labelsPath = join(workspace, 'labels.json');
  const graphCalls = [];

  await writeFile(labelsPath, JSON.stringify(labelDocument({
    queries: [{
      query: 'detail failure',
      intent: 'One failed detail fetch should not stop the run.',
      labels: [{ articleId: 4, relevance: 'strong', note: '' }],
    }],
  })), 'utf8');

  try {
    const result = await runGraphAwareEvaluation({
      labels: {
        catalogId: 'api-ready-test',
        catalogArticleCount: 6,
        queries: [{
          query: 'detail failure',
          intent: 'One failed detail fetch should not stop the run.',
          strongArticleIds: [4],
          acceptableArticleIds: [],
          notRelevantArticleIds: [],
        }],
      },
      labelsPath,
      baseUrl: 'http://localhost:8080',
      outputDir: join(workspace, 'results'),
      k: 2,
      generatedAt: '2026-06-04T00:00:00.000Z',
      publicRun: publicRun({ queries: [{ query: 'detail failure', rankedArticleIds: [4, 2] }] }),
      graphClient: {
        async fetchArticleDetail(articleId) {
          if (articleId === 2) {
            throw new Error('Article detail exploded.');
          }
          return { articleId, relatedArticleIds: [] };
        },
        async fetchGraphContextRow(articleId) {
          graphCalls.push(articleId);
          return completedGraphRow({ articleId, relatedArticleReasons: [], topics: [] });
        },
      },
    });

    assert.deepEqual(graphCalls, [4]);
    assert.deepEqual(result.run.queries[0].contexts[1], {
      articleId: 2,
      status: 'FAILED',
      latencyMs: null,
      failureReason: 'Article detail exploded.',
      emptyContext: false,
      relatedArticleIds: [],
      relatedArticleReasons: [],
      topics: [],
    });
    assert.equal(result.byQueryMetrics[0].failedContextCount, 1);
  } finally {
    await rm(workspace, { recursive: true, force: true });
  }
});

function labelDocument({ queries }) {
  return {
    version: 1,
    catalogId: 'api-ready-test',
    catalogArticleCount: 6,
    queries: queries.map((query) => ({
      status: 'reviewed',
      ...query,
    })),
  };
}

function publicRun({ queries }) {
  return {
    labelsPath: '/tmp/public-labels.json',
    baseUrl: 'http://localhost:8080',
    system: 'public',
    k: 5,
    queries,
  };
}

function fakeGraphClient({ details = new Map(), graphRows = new Map() } = {}) {
  return {
    async fetchArticleDetail(articleId) {
      return details.get(articleId) ?? { articleId, relatedArticleIds: [] };
    },
    async fetchGraphContextRow(articleId) {
      return graphRows.get(articleId) ?? completedGraphRow({ articleId, relatedArticleReasons: [], topics: [] });
    },
  };
}

function completedGraphRow({ articleId, relatedArticleReasons, topics }) {
  return {
    articleId,
    status: 'COMPLETED',
    latencyMs: 12,
    emptyContext: relatedArticleReasons.length === 0 && topics.length === 0,
    relatedArticleReasons,
    topics,
  };
}

function sha256(content) {
  return createHash('sha256').update(content, 'utf8').digest('hex');
}
