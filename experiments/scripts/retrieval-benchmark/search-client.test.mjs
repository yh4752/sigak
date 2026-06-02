import test from 'node:test';
import assert from 'node:assert/strict';
import { createArticleSearchClient } from './search-client.mjs';

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

function createStepClock(values) {
  let index = 0;
  return () => values[index++];
}
