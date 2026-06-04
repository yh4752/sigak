import test from 'node:test';
import assert from 'node:assert/strict';
import { createGraphContextClient } from './graph-client.mjs';

test('fetchArticleDetail calls public detail API and extracts relation ids', async () => {
  const requestedUrls = [];
  const client = createGraphContextClient({
    baseUrl: 'http://localhost:8080///',
    fetchImpl: async (url) => {
      requestedUrls.push(String(url));
      return jsonResponse({
        id: 4,
        title: 'Graph-aware evaluation',
        relatedArticleIds: [1, 5],
      });
    },
  });

  const result = await client.fetchArticleDetail(4);

  assert.equal(requestedUrls[0], 'http://localhost:8080/api/articles/4');
  assert.deepEqual(result, {
    articleId: 4,
    relatedArticleIds: [1, 5],
  });
});

test('fetchGraphContextRow maps public graph context fields', async () => {
  const client = createGraphContextClient({
    baseUrl: 'http://localhost:8080',
    fetchImpl: async () => jsonResponse({
      articleId: 4,
      relatedArticleReasons: [
        {
          articleId: 1,
          reason: 'Graph RAG evaluation connects to retrieval evaluation.',
          sharedTopics: ['evaluation', 'Graph RAG'],
        },
      ],
      topics: [
        { name: 'graph rag', displayName: 'Graph RAG', relatedArticleIds: [1] },
        { name: 'evaluation', displayName: 'Evaluation', relatedArticleIds: [1, 5] },
      ],
    }),
    now: createStepClock([100, 136]),
  });

  const row = await client.fetchGraphContextRow(4);

  assert.deepEqual(row, {
    articleId: 4,
    status: 'COMPLETED',
    latencyMs: 36,
    emptyContext: false,
    relatedArticleReasons: [
      {
        articleId: 1,
        reason: 'Graph RAG evaluation connects to retrieval evaluation.',
        sharedTopics: ['evaluation', 'Graph RAG'],
      },
    ],
    topics: ['Graph RAG', 'Evaluation'],
  });
});

test('fetchGraphContextRow records non-2xx responses as failed rows', async () => {
  const client = createGraphContextClient({
    baseUrl: 'http://localhost:8080',
    fetchImpl: async () => ({ ok: false, status: 503, json: async () => ({}) }),
    now: createStepClock([200, 249]),
  });

  const row = await client.fetchGraphContextRow(4);

  assert.equal(row.articleId, 4);
  assert.equal(row.status, 'FAILED');
  assert.equal(row.latencyMs, 49);
  assert.match(row.failureReason, /Graph context API failed with status 503/);
  assert.equal(row.emptyContext, false);
  assert.deepEqual(row.relatedArticleReasons, []);
  assert.deepEqual(row.topics, []);
});

test('fetchGraphContextRow records fetch errors as failed rows', async () => {
  const client = createGraphContextClient({
    baseUrl: 'http://localhost:8080',
    fetchImpl: async () => {
      throw new Error('network unavailable');
    },
    now: createStepClock([20, 31]),
  });

  const row = await client.fetchGraphContextRow(4);

  assert.equal(row.status, 'FAILED');
  assert.equal(row.latencyMs, 11);
  assert.match(row.failureReason, /network unavailable/);
});

test('fetchGraphContextRow records invalid JSON parsing as failed rows', async () => {
  const client = createGraphContextClient({
    baseUrl: 'http://localhost:8080',
    fetchImpl: async () => ({
      ok: true,
      status: 200,
      json: async () => {
        throw new Error('invalid json');
      },
    }),
    now: createStepClock([40, 52]),
  });

  const row = await client.fetchGraphContextRow(4);

  assert.equal(row.status, 'FAILED');
  assert.equal(row.latencyMs, 12);
  assert.match(row.failureReason, /invalid json/);
});

test('fetchGraphContextRow maps empty public context as completed empty row', async () => {
  const client = createGraphContextClient({
    baseUrl: 'http://localhost:8080',
    fetchImpl: async () => jsonResponse({
      articleId: 4,
      relatedArticleReasons: [],
      topics: [],
    }),
    now: createStepClock([0, 7]),
  });

  const row = await client.fetchGraphContextRow(4);

  assert.deepEqual(row, {
    articleId: 4,
    status: 'COMPLETED',
    latencyMs: 7,
    emptyContext: true,
    relatedArticleReasons: [],
    topics: [],
  });
});

test('fetchGraphContextRow records mismatched response article id as failed row', async () => {
  const client = createGraphContextClient({
    baseUrl: 'http://localhost:8080',
    fetchImpl: async () => jsonResponse({
      articleId: 5,
      relatedArticleReasons: [],
      topics: [],
    }),
    now: createStepClock([0, 1]),
  });

  const row = await client.fetchGraphContextRow(4);

  assert.equal(row.articleId, 4);
  assert.equal(row.status, 'FAILED');
  assert.equal(row.latencyMs, 1);
  assert.match(row.failureReason, /Graph context API response articleId must match requested articleId/);
});

function createStepClock(values) {
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
