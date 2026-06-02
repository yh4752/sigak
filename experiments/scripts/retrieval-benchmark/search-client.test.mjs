import test from 'node:test';
import assert from 'node:assert/strict';
import { createArticleSearchClient, createInternalRetrievalRuns } from './search-client.mjs';

test('searchArticles calls public API and extracts ranked article ids', async () => {
  const requestedUrls = [];
  const client = createArticleSearchClient({
    baseUrl: 'http://localhost:8080/',
    fetchImpl: async (url) => {
      requestedUrls.push(String(url));
      return {
        ok: true,
        status: 200,
        json: async () => [{ id: 4 }, { id: 1 }, { id: 2 }],
      };
    },
    now: createStepClock([100, 145]),
  });

  const result = await client.searchArticles('graph rag failure');

  assert.equal(requestedUrls[0], 'http://localhost:8080/api/articles?query=graph+rag+failure');
  assert.deepEqual(result.rankedArticleIds, [4, 1, 2]);
  assert.equal(result.latencyMs, 45);
});

test('searchArticles rejects non-2xx API response', async () => {
  const client = createArticleSearchClient({
    baseUrl: 'http://localhost:8080',
    fetchImpl: async () => ({ ok: false, status: 503, text: async () => 'unavailable' }),
    now: createStepClock([0, 1]),
  });

  await assert.rejects(
    () => client.searchArticles('agent evaluation'),
    /Search API failed with status 503/
  );
});

test('searchArticles rejects non-array responses', async () => {
  const client = createArticleSearchClient({
    baseUrl: 'http://localhost:8080',
    fetchImpl: async () => ({ ok: true, status: 200, json: async () => ({ id: 4 }) }),
    now: createStepClock([0, 1]),
  });

  await assert.rejects(
    () => client.searchArticles('agent evaluation'),
    /Search API response must be an array/
  );
});

test('searchArticles rejects response without numeric ids', async () => {
  const client = createArticleSearchClient({
    baseUrl: 'http://localhost:8080',
    fetchImpl: async () => ({ ok: true, status: 200, json: async () => [{ title: 'missing id' }] }),
    now: createStepClock([0, 1]),
  });

  await assert.rejects(
    () => client.searchArticles('agent evaluation'),
    /Search API response item must include numeric id/
  );
});

test('searchArticles records resolved mode when metrics endpoint is available', async () => {
  const requestedUrls = [];
  const client = createArticleSearchClient({
    baseUrl: 'http://localhost:8080',
    now: createStepClock([100, 125]),
    fetchImpl: async (url) => {
      requestedUrls.push(String(url));

      if (String(url).includes('/api/internal/search-metrics/articles')) {
        return jsonResponse({
          lastSearch: {
            mode: 'VECTOR_ONLY',
            fallbackReason: 'KEYWORD_SEARCH_FAILED',
            staleCandidateCount: 2,
          },
        });
      }

      return jsonResponse([{ id: 1 }, { id: 2 }]);
    },
  });

  const result = await client.searchArticles('graph', { includeMetrics: true });

  assert.deepEqual(result.rankedArticleIds, [1, 2]);
  assert.equal(result.latencyMs, 25);
  assert.equal(result.resolvedMode, 'VECTOR_ONLY');
  assert.equal(result.degraded, true);
  assert.equal(result.failureReason, 'KEYWORD_SEARCH_FAILED');
  assert.equal(result.staleCandidateCount, 2);
  assert.equal(requestedUrls[1], 'http://localhost:8080/api/internal/search-metrics/articles');
});

test('searchArticles omits public metrics metadata when metrics endpoint fails', async () => {
  const client = createArticleSearchClient({
    baseUrl: 'http://localhost:8080',
    now: createStepClock([50, 80]),
    fetchImpl: async (url) => {
      if (String(url).includes('/api/internal/search-metrics/articles')) {
        return { ok: false, status: 404, json: async () => ({}) };
      }

      return jsonResponse([{ id: 9 }]);
    },
  });

  const result = await client.searchArticles('graph', { includeMetrics: true });

  assert.deepEqual(result, {
    rankedArticleIds: [9],
    latencyMs: 30,
  });
});

test('createInternalRetrievalRuns posts strict systems and maps run items', async () => {
  const requests = [];

  const result = await createInternalRetrievalRuns({
    baseUrl: 'http://localhost:8080',
    queries: ['graph'],
    systems: ['hybrid'],
    limit: 20,
    fetchImpl: async (url, options) => {
      requests.push({ url: String(url), body: JSON.parse(options.body) });
      return jsonResponse({
        generatedAt: '2026-06-02T00:00:00Z',
        limit: 20,
        runs: [{
          query: 'graph',
          system: 'HYBRID',
          status: 'COMPLETED',
          rankedArticleIds: [4, 1],
          candidateCount: 2,
          staleCandidateCount: 0,
          failureReason: null,
          degraded: false,
          resolvedMode: 'HYBRID',
          timings: { totalElapsedMs: 12 },
          metadata: { embeddingProvider: 'deterministic', embeddingModelName: 'deterministic', embeddingDimension: 8 },
        }],
      });
    },
  });

  assert.equal(requests[0].url, 'http://localhost:8080/api/internal/search-evaluation/retrieval-runs');
  assert.deepEqual(requests[0].body.systems, ['HYBRID']);
  assert.equal(result.runs[0].system, 'hybrid');
  assert.equal(result.runs[0].latencyMs, 12);
  assert.equal(result.runs[0].latencySource, 'backend_total');
});

test('createInternalRetrievalRuns rejects public system before making request', async () => {
  await assert.rejects(
    () => createInternalRetrievalRuns({
      baseUrl: 'http://localhost:8080',
      queries: ['graph'],
      systems: ['keyword', 'public'],
      limit: 20,
      fetchImpl: async () => {
        throw new Error('should not fetch');
      },
    }),
    /PUBLIC cannot be sent to the internal retrieval evaluation endpoint/
  );
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
