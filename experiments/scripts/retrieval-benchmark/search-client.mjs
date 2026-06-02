const INTERNAL_SYSTEMS = new Set(['keyword', 'vector', 'hybrid']);

export function createArticleSearchClient({ baseUrl, fetchImpl = fetch, now = () => Date.now() }) {
  const normalizedBaseUrl = baseUrl.replace(/\/+$/, '');

  return {
    async searchArticles(query, { includeMetrics = false } = {}) {
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

      const searchResult = {
        rankedArticleIds: body.map(extractArticleId),
        latencyMs: Math.max(0, Math.round(end - start)),
      };

      if (!includeMetrics) {
        return searchResult;
      }

      // 내부 metrics endpoint는 실험용 보조 정보이므로, 없으면 public 결과만 기록한다.
      const metrics = await readArticleSearchMetrics({ baseUrl: normalizedBaseUrl, fetchImpl }).catch(() => undefined);
      const resolvedMode = metrics?.lastSearch?.mode;

      return {
        ...searchResult,
        ...(resolvedMode ? { resolvedMode } : {}),
        ...(resolvedMode ? { degraded: resolvedMode !== 'HYBRID' } : {}),
        ...(metrics?.lastSearch?.fallbackReason ? { failureReason: metrics.lastSearch.fallbackReason } : {}),
        ...(Number.isInteger(metrics?.lastSearch?.staleCandidateCount)
          ? { staleCandidateCount: metrics.lastSearch.staleCandidateCount }
          : {}),
      };
    },
  };
}

export async function createInternalRetrievalRuns({ baseUrl, queries, systems, limit, fetchImpl = fetch }) {
  const normalizedBaseUrl = baseUrl.replace(/\/+$/, '');
  const normalizedSystems = normalizeInternalSystems(systems);
  const response = await fetchImpl(`${normalizedBaseUrl}/api/internal/search-evaluation/retrieval-runs`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      queries,
      systems: normalizedSystems.map((system) => system.toUpperCase()),
      limit,
    }),
  });

  if (!response.ok) {
    throw new Error(`Retrieval evaluation API failed with status ${response.status}.`);
  }

  const body = await response.json();
  if (!Array.isArray(body?.runs)) {
    throw new Error('Retrieval evaluation API response must include runs array.');
  }

  return {
    ...body,
    runs: body.runs.map((run) => ({
      ...run,
      system: typeof run.system === 'string' ? run.system.toLowerCase() : run.system,
      latencyMs: Number.isFinite(run.timings?.totalElapsedMs) ? run.timings.totalElapsedMs : null,
      latencySource: 'backend_total',
    })),
  };
}

async function readArticleSearchMetrics({ baseUrl, fetchImpl }) {
  const response = await fetchImpl(`${baseUrl}/api/internal/search-metrics/articles`);

  if (!response.ok) {
    throw new Error(`Search metrics API failed with status ${response.status}.`);
  }

  return response.json();
}

function normalizeInternalSystems(systems) {
  if (!Array.isArray(systems) || systems.length === 0) {
    throw new Error('Internal retrieval evaluation requires at least one strict system.');
  }

  return systems.map((system) => {
    const normalizedSystem = String(system).trim().toLowerCase();

    if (normalizedSystem === 'public') {
      throw new Error('PUBLIC cannot be sent to the internal retrieval evaluation endpoint.');
    }

    if (!INTERNAL_SYSTEMS.has(normalizedSystem)) {
      throw new Error(`Unknown internal retrieval system: ${normalizedSystem}`);
    }

    return normalizedSystem;
  });
}

function extractArticleId(item) {
  if (!Number.isInteger(item?.id)) {
    throw new Error('Search API response item must include numeric id.');
  }

  return item.id;
}
