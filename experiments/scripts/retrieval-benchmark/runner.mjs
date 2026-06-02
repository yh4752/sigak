import { loadLabelFile } from './labels.mjs';
import {
  calculateComparisonMetrics,
  calculateQueryMetrics,
  calculateRunRowMetrics,
  calculateSummaryMetrics,
  calculateSystemSummaryMetrics,
} from './metrics.mjs';
import { writeBenchmarkArtifacts, writeComparisonArtifacts } from './report-writer.mjs';
import { createArticleSearchClient, createInternalRetrievalRuns } from './search-client.mjs';

export async function runRetrievalBenchmark(options) {
  if (!options.systems) {
    return runPublicSmokeBenchmark(options);
  }

  return runSystemComparisonBenchmark(options);
}

async function runPublicSmokeBenchmark({
  labelsPath,
  baseUrl,
  outputDir,
  k,
  searchClient = createArticleSearchClient({ baseUrl }),
  generatedAt = new Date().toISOString(),
}) {
  const labels = await loadLabelFile(labelsPath);
  const runQueries = [];
  const byQueryMetrics = [];

  for (const labeledQuery of labels.queries) {
    const searchResult = await searchClient.searchArticles(labeledQuery.query);

    runQueries.push({
      query: labeledQuery.query,
      rankedArticleIds: searchResult.rankedArticleIds,
      latencyMs: searchResult.latencyMs,
    });

    byQueryMetrics.push(calculateQueryMetrics({
      labeledQuery,
      rankedArticleIds: searchResult.rankedArticleIds,
      latencyMs: searchResult.latencyMs,
      k,
    }));
  }

  const run = {
    labelsPath,
    baseUrl,
    system: 'public',
    k,
    queries: runQueries,
  };

  const summary = calculateSummaryMetrics({
    catalogId: labels.catalogId,
    catalogArticleCount: labels.catalogArticleCount,
    k,
    generatedAt,
    byQueryMetrics,
  });

  const artifacts = await writeBenchmarkArtifacts({ outputDir, run, byQueryMetrics, summary });

  return { run, byQueryMetrics, summary, artifacts };
}

async function runSystemComparisonBenchmark({
  labelsPath,
  baseUrl,
  outputDir,
  k,
  limit = 20,
  systems,
  searchClient = createArticleSearchClient({ baseUrl }),
  evaluationClient = createEvaluationClient(baseUrl),
  generatedAt = new Date().toISOString(),
}) {
  const labels = await loadLabelFile(labelsPath);
  const normalizedSystems = [...new Set(systems.map((system) => String(system).trim().toLowerCase()))];
  const strictSystems = normalizedSystems.filter((system) => system !== 'public');
  const runsBySystem = {};

  // strict system은 backend internal endpoint에서만 생성하고, public은 별도 경로로 수집한다.
  if (strictSystems.length > 0) {
    const internalResult = await evaluationClient.createRuns({
      queries: labels.queries.map((query) => query.query),
      systems: strictSystems,
      limit,
    });
    const internalRunIndex = new Map(
      internalResult.runs.map((run) => [`${run.system}:${run.query}`, run])
    );

    for (const system of strictSystems) {
      runsBySystem[system] = buildRunArtifact({
        labelsPath,
        baseUrl,
        system,
        k,
        limit,
        queries: labels.queries.map((labeledQuery) => {
          const runItem = internalRunIndex.get(`${system}:${labeledQuery.query}`);

          if (!runItem) {
            throw new Error(`Missing internal run for system "${system}" and query "${labeledQuery.query}".`);
          }

          return runItem;
        }),
      });
    }
  }

  if (normalizedSystems.includes('public')) {
    const publicRuns = [];

    for (const labeledQuery of labels.queries) {
      const searchResult = await searchClient.searchArticles(labeledQuery.query, { includeMetrics: true });
      publicRuns.push({
        query: labeledQuery.query,
        system: 'public',
        status: 'COMPLETED',
        rankedArticleIds: searchResult.rankedArticleIds,
        candidateCount: searchResult.rankedArticleIds.length,
        staleCandidateCount: searchResult.staleCandidateCount ?? 0,
        failureReason: searchResult.failureReason ?? null,
        degraded: Boolean(searchResult.degraded),
        resolvedMode: searchResult.resolvedMode ?? null,
        latencyMs: searchResult.latencyMs,
        latencySource: 'client_round_trip',
      });
    }

    runsBySystem.public = buildRunArtifact({
      labelsPath,
      baseUrl,
      system: 'public',
      k,
      limit,
      queries: publicRuns,
    });
  }

  const labelsByQuery = new Map(labels.queries.map((query) => [query.query, query]));
  const runItems = Object.values(runsBySystem).flatMap((run) => run.queries);
  const byQueryMetrics = runItems.map((runItem) => calculateRunRowMetrics({
    labeledQuery: labelsByQuery.get(runItem.query),
    runItem,
    k,
  }));
  const bySystemMetrics = normalizedSystems.map((system) => calculateSystemSummaryMetrics({
    system,
    rows: byQueryMetrics.filter((row) => row.system === system),
  }));
  const comparison = calculateComparisonMetrics({
    catalogId: labels.catalogId,
    catalogArticleCount: labels.catalogArticleCount,
    k,
    generatedAt,
    systems: normalizedSystems,
    byQueryMetrics,
  });
  const artifacts = await writeComparisonArtifacts({
    outputDir,
    runsBySystem,
    byQueryMetrics,
    bySystemMetrics,
    comparison,
  });
  const primarySummary = bySystemMetrics.find((metric) => metric.system === 'public')
    ?? bySystemMetrics.find((metric) => metric.system === normalizedSystems[0]);
  const summary = {
    ...primarySummary,
    catalogId: labels.catalogId,
    catalogArticleCount: labels.catalogArticleCount,
    evaluatedQueryCount: comparison.evaluatedQueryCount,
    k,
    generatedAt,
  };

  return {
    runsBySystem,
    byQueryMetrics,
    bySystemMetrics,
    comparison,
    summary,
    artifacts,
  };
}

function createEvaluationClient(baseUrl) {
  return {
    createRuns: (options) => createInternalRetrievalRuns({ baseUrl, ...options }),
  };
}

function buildRunArtifact({ labelsPath, baseUrl, system, k, limit, queries }) {
  return {
    labelsPath,
    baseUrl,
    system,
    k,
    limit,
    queries,
  };
}
