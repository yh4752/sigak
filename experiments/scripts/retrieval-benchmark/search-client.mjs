export function createArticleSearchClient({ baseUrl, fetchImpl = fetch, now = () => Date.now() }) {
  const normalizedBaseUrl = baseUrl.replace(/\/+$/, '');

  return {
    async searchArticles(query) {
      const url = new URL(`${normalizedBaseUrl}/api/articles`);
      url.searchParams.set('query', query);

      const start = now();
      const response = await fetchImpl(url);

      if (!response.ok) {
        throw new Error(`Search API failed with status ${response.status}.`);
      }

      const body = await response.json();
      const end = now();

      if (!Array.isArray(body)) {
        throw new Error('Search API response must be an array.');
      }

      return {
        rankedArticleIds: body.map(extractArticleId),
        latencyMs: Math.max(0, Math.round(end - start)),
      };
    },
  };
}

function extractArticleId(item) {
  if (!Number.isInteger(item?.id)) {
    throw new Error('Search API response item must include numeric id.');
  }

  return item.id;
}
