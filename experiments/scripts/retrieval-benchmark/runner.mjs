import { loadLabelFile } from './labels.mjs';
import { calculateQueryMetrics, calculateSummaryMetrics } from './metrics.mjs';
import { writeBenchmarkArtifacts } from './report-writer.mjs';
import { createArticleSearchClient } from './search-client.mjs';

export async function runRetrievalBenchmark({
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
    system: 'hybrid',
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
