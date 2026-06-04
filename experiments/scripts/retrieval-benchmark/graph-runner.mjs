import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { createGraphContextClient } from './graph-client.mjs';
import { calculateGraphQueryMetrics, calculateGraphSummaryMetrics } from './graph-metrics.mjs';
import { writeGraphArtifacts } from './graph-report-writer.mjs';

const SOURCE_ARTICLE_SELECTION = 'public_search_top_k';
const GRAPH_CONTEXT_ENDPOINT = '/api/articles/{id}/graph-context';
const REASON_SOURCE = 'stored_projection_reason';

export async function runGraphAwareEvaluation({
  labels,
  labelsPath,
  baseUrl,
  outputDir,
  k,
  generatedAt,
  publicRun,
  graphClient = createGraphContextClient({ baseUrl }),
}) {
  const labelsContent = await readFile(labelsPath, 'utf8');
  const labelsSha256 = sha256(labelsContent);
  const publicRunsByQuery = new Map((publicRun.queries ?? []).map((queryRun) => [queryRun.query, queryRun]));
  const runQueries = [];
  const byQueryMetrics = [];

  for (const labeledQuery of labels.queries) {
    const publicRunQuery = publicRunsByQuery.get(labeledQuery.query);

    if (!publicRunQuery) {
      throw new Error(`Missing public run for graph-aware query "${labeledQuery.query}".`);
    }

    const searchRankedArticleIds = (publicRunQuery.rankedArticleIds ?? []).slice(0, k);
    const positiveArticleIds = uniqueArticleIds([
      ...(labeledQuery.strongArticleIds ?? []),
      ...(labeledQuery.acceptableArticleIds ?? []),
    ]);
    const searchPositiveHitIds = uniqueArticleIds(
      searchRankedArticleIds.filter((articleId) => positiveArticleIds.includes(articleId))
    );
    const sourceArticleIds = uniqueArticleIds(searchRankedArticleIds);
    const contexts = [];

    for (const articleId of sourceArticleIds) {
      contexts.push(await fetchContextRow({ graphClient, articleId }));
    }

    const graphRunQuery = {
      query: labeledQuery.query,
      positiveArticleIds,
      searchRankedArticleIds,
      searchPositiveHitIds,
      searchMissedPositiveIds: positiveArticleIds.filter((articleId) => !searchPositiveHitIds.includes(articleId)),
      sourceArticleIds,
      contexts,
    };

    runQueries.push(graphRunQuery);
    byQueryMetrics.push(calculateGraphQueryMetrics({
      labeledQuery,
      graphRunQuery,
      k,
    }));
  }

  const run = {
    labelsPath,
    labelsSha256,
    catalogId: labels.catalogId,
    catalogArticleCount: labels.catalogArticleCount,
    baseUrl,
    k,
    generatedAt,
    sourceArticleSelection: SOURCE_ARTICLE_SELECTION,
    graphContextEndpoint: GRAPH_CONTEXT_ENDPOINT,
    reasonSource: REASON_SOURCE,
    queries: runQueries,
  };
  const summary = calculateGraphSummaryMetrics({
    catalogId: labels.catalogId,
    catalogArticleCount: labels.catalogArticleCount,
    k,
    generatedAt,
    byQueryMetrics,
  });
  const artifacts = await writeGraphArtifacts({ outputDir, run, byQueryMetrics, summary });

  return { run, byQueryMetrics, summary, artifacts };
}

async function fetchContextRow({ graphClient, articleId }) {
  try {
    const detail = await graphClient.fetchArticleDetail(articleId);
    const graphRow = await graphClient.fetchGraphContextRow(articleId);

    return {
      ...graphRow,
      relatedArticleIds: detail.relatedArticleIds,
    };
  } catch (error) {
    return createFailedContextRow({
      articleId,
      failureReason: error instanceof Error ? error.message : String(error),
    });
  }
}

function createFailedContextRow({ articleId, failureReason }) {
  return {
    articleId,
    status: 'FAILED',
    latencyMs: null,
    failureReason,
    emptyContext: false,
    relatedArticleIds: [],
    relatedArticleReasons: [],
    topics: [],
  };
}

function uniqueArticleIds(values) {
  return [...new Set(values)];
}

function sha256(content) {
  return createHash('sha256').update(content, 'utf8').digest('hex');
}
