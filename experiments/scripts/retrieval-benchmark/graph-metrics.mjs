export function calculateGraphQueryMetrics({ labeledQuery, graphRunQuery, k }) {
  const positiveArticleIds = uniqueArticleIds([
    ...(labeledQuery.strongArticleIds ?? []),
    ...(labeledQuery.acceptableArticleIds ?? []),
  ], 'positiveArticleIds');
  const positiveSet = new Set(positiveArticleIds);
  const searchRankedArticleIds = validateArticleIds(
    (graphRunQuery.searchRankedArticleIds ?? []).slice(0, k),
    'searchRankedArticleIds'
  );
  const sourceArticleIdSet = new Set(uniqueArticleIds(searchRankedArticleIds, 'searchRankedArticleIds'));
  const searchPositiveHitIds = uniqueArticleIds(
    searchRankedArticleIds.filter((articleId) => positiveSet.has(articleId)),
    'searchPositiveHitIds'
  );
  const searchPositiveHitSet = new Set(searchPositiveHitIds);
  const contexts = validateSourceContexts(graphRunQuery.contexts ?? [], sourceArticleIdSet);
  const completedContexts = contexts.filter((context) => context.status === 'COMPLETED');

  const relatedBaselineHitIds = distinctPositiveRelatedIds({
    contexts,
    positiveSet,
    getRelatedIds: (context) => context.relatedArticleIds ?? [],
  });
  const graphRelatedHitIds = distinctPositiveRelatedIds({
    contexts: completedContexts,
    positiveSet,
    getRelatedIds: (context) => (context.relatedArticleReasons ?? []).map((reason) => reason.articleId),
  });
  const graphReasonedHitIds = distinctPositiveRelatedIds({
    contexts: completedContexts,
    positiveSet,
    getRelatedIds: (context) => (context.relatedArticleReasons ?? [])
      .filter((reason) => hasNonBlankReason(reason))
      .map((reason) => reason.articleId),
  });
  const graphSearchHitRelatedHitCount = graphRelatedHitIds
    .filter((articleId) => searchPositiveHitSet.has(articleId))
    .length;

  return {
    query: labeledQuery.query,
    intent: labeledQuery.intent,
    evaluatedArticleCount: contexts.length,
    positiveArticleIds,
    searchRankedArticleIds,
    searchPositiveHitIds,
    searchMissedPositiveIds: positiveArticleIds.filter((articleId) => !searchPositiveHitSet.has(articleId)),
    relatedBaselineHitCount: relatedBaselineHitIds.length,
    graphRelatedHitCount: graphRelatedHitIds.length,
    graphSearchHitRelatedHitCount,
    graphReasonedHitCount: graphReasonedHitIds.length,
    searchPositiveCoverageAtK: ratioOrNull(searchPositiveHitIds.length, positiveArticleIds.length),
    relatedBaselineCoverageAtK: ratioOrNull(relatedBaselineHitIds.length, positiveArticleIds.length),
    graphContextCoverageAtK: ratioOrNull(graphRelatedHitIds.length, positiveArticleIds.length),
    graphContextCoverageAmongSearchHits: ratioOrNull(graphSearchHitRelatedHitCount, searchPositiveHitIds.length),
    graphReasonedCoverageAtK: ratioOrNull(graphReasonedHitIds.length, positiveArticleIds.length),
    reasonCoverage: ratioOrNull(graphReasonedHitIds.length, graphRelatedHitIds.length),
    topicContextCoverage: ratioOrNull(completedContexts.filter(hasTopicContext).length, completedContexts.length),
    emptyContextCount: completedContexts.filter((context) => context.emptyContext === true).length,
    failedContextCount: contexts.filter((context) => context.status === 'FAILED').length,
    averageGraphLatencyMs: nullableAverage(contexts.map((context) => context.latencyMs)),
  };
}

export function calculateGraphSummaryMetrics({ catalogId, catalogArticleCount, k, generatedAt, byQueryMetrics }) {
  const totalContextCount = byQueryMetrics.reduce((sum, metric) => sum + metric.evaluatedArticleCount, 0);
  const totalFailedContextCount = byQueryMetrics.reduce((sum, metric) => sum + metric.failedContextCount, 0);
  const totalEmptyContextCount = byQueryMetrics.reduce((sum, metric) => sum + metric.emptyContextCount, 0);

  return {
    catalogId,
    catalogArticleCount,
    evaluatedQueryCount: byQueryMetrics.length,
    k,
    macroSearchPositiveCoverageAtK: nullableAverage(byQueryMetrics.map((metric) => metric.searchPositiveCoverageAtK)),
    macroRelatedBaselineCoverageAtK: nullableAverage(byQueryMetrics.map((metric) => metric.relatedBaselineCoverageAtK)),
    macroGraphContextCoverageAtK: nullableAverage(byQueryMetrics.map((metric) => metric.graphContextCoverageAtK)),
    macroGraphContextCoverageAmongSearchHits: nullableAverage(byQueryMetrics.map((metric) => metric.graphContextCoverageAmongSearchHits)),
    macroGraphReasonedCoverageAtK: nullableAverage(byQueryMetrics.map((metric) => metric.graphReasonedCoverageAtK)),
    macroReasonCoverage: nullableAverage(byQueryMetrics.map((metric) => metric.reasonCoverage)),
    macroTopicContextCoverage: nullableAverage(byQueryMetrics.map((metric) => metric.topicContextCoverage)),
    graphContextFailureRate: ratioOrNull(totalFailedContextCount, totalContextCount),
    emptyContextRate: ratioOrNull(totalEmptyContextCount, totalContextCount),
    averageGraphLatencyMs: nullableAverage(byQueryMetrics.map((metric) => metric.averageGraphLatencyMs)),
    generatedAt,
    warnings: graphWarnings({ catalogArticleCount, evaluatedQueryCount: byQueryMetrics.length }),
  };
}

function distinctPositiveRelatedIds({ contexts, positiveSet, getRelatedIds }) {
  const ids = [];
  for (const context of contexts) {
    for (const articleId of validateArticleIds(getRelatedIds(context), 'context.relatedArticleIds')) {
      if (articleId !== context.articleId && positiveSet.has(articleId)) {
        ids.push(articleId);
      }
    }
  }
  return uniqueArticleIds(ids, 'positiveRelatedArticleIds');
}

function hasTopicContext(context) {
  if ((context.topics ?? []).length > 0) {
    return true;
  }
  return (context.relatedArticleReasons ?? []).some((reason) => (reason.sharedTopics ?? []).length > 0);
}

function hasNonBlankReason(reason) {
  return typeof reason.reason === 'string' && reason.reason.trim().length > 0;
}

function graphWarnings({ catalogArticleCount, evaluatedQueryCount }) {
  const warnings = [];
  if (evaluatedQueryCount < 10) {
    warnings.push('label set is smaller than 10 reviewed queries, so this is a smoke graph evaluation');
  }
  if (catalogArticleCount < 20) {
    warnings.push('catalog is smaller than 20 articles, so graph density is too small for quality claims');
  }
  warnings.push('graph latency is measured as public API round-trip, not pure Neo4j query latency');
  warnings.push('graph reasons are stored projection reasons, not independently verified factual explanations');
  return warnings;
}

function ratioOrNull(numerator, denominator) {
  return denominator === 0 ? null : numerator / denominator;
}

function nullableAverage(values) {
  const numericValues = values.filter((value) => Number.isFinite(value));
  if (numericValues.length === 0) {
    return null;
  }
  return numericValues.reduce((sum, value) => sum + value, 0) / numericValues.length;
}

function validateSourceContexts(contexts, sourceArticleIdSet) {
  for (const context of contexts) {
    validateArticleId(context.articleId, 'context.articleId');
    if (!sourceArticleIdSet.has(context.articleId)) {
      throw new Error(`Graph context articleId ${context.articleId} is outside public search top-k source articles.`);
    }
  }
  return contexts;
}

function validateArticleIds(values, fieldName) {
  if (!Array.isArray(values)) {
    throw new Error(`${fieldName} must be an array of positive integer article IDs.`);
  }
  for (const value of values) {
    validateArticleId(value, fieldName);
  }
  return values;
}

function uniqueArticleIds(values, fieldName) {
  return [...new Set(validateArticleIds(values, fieldName))];
}

function validateArticleId(value, fieldName) {
  if (!Number.isInteger(value) || value < 1) {
    throw new Error(`${fieldName} must contain positive integer article IDs.`);
  }
}
