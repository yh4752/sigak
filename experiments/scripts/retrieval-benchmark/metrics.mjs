export function calculateQueryMetrics({ labeledQuery, rankedArticleIds, latencyMs, k }) {
  const topKArticleIds = rankedArticleIds.slice(0, k);
  const strongSet = new Set(labeledQuery.strongArticleIds);
  const positiveSet = new Set([...labeledQuery.strongArticleIds, ...labeledQuery.acceptableArticleIds]);
  const foundPositiveCount = new Set(topKArticleIds.filter((articleId) => positiveSet.has(articleId))).size;
  const firstStrongIndex = topKArticleIds.findIndex((articleId) => strongSet.has(articleId));

  return {
    query: labeledQuery.query,
    intent: labeledQuery.intent,
    strongArticleIds: labeledQuery.strongArticleIds,
    acceptableArticleIds: labeledQuery.acceptableArticleIds,
    resultArticleIds: rankedArticleIds,
    top1StrongHit: topKArticleIds.length > 0 && strongSet.has(topKArticleIds[0]) ? 1 : 0,
    recallAtK: foundPositiveCount / positiveSet.size,
    mrrAtK: firstStrongIndex >= 0 ? 1 / (firstStrongIndex + 1) : 0,
    latencyMs,
  };
}

export function calculateSummaryMetrics({ catalogId, catalogArticleCount, k, generatedAt, byQueryMetrics }) {
  return {
    catalogId,
    catalogArticleCount,
    evaluatedQueryCount: byQueryMetrics.length,
    k,
    macroTop1StrongHit: average(byQueryMetrics.map((metric) => metric.top1StrongHit)),
    macroRecallAtK: average(byQueryMetrics.map((metric) => metric.recallAtK)),
    macroMrrAtK: average(byQueryMetrics.map((metric) => metric.mrrAtK)),
    averageLatencyMs: average(byQueryMetrics.map((metric) => metric.latencyMs)),
    generatedAt,
  };
}

function average(values) {
  if (values.length === 0) {
    return 0;
  }

  return values.reduce((sum, value) => sum + value, 0) / values.length;
}
