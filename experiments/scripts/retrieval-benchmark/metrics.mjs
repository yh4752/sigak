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

export function calculateRunRowMetrics({ labeledQuery, runItem, k }) {
  if (runItem.status === 'FAILED') {
    return {
      query: labeledQuery.query,
      intent: labeledQuery.intent,
      system: runItem.system,
      status: 'FAILED',
      strongArticleIds: labeledQuery.strongArticleIds,
      acceptableArticleIds: labeledQuery.acceptableArticleIds,
      resultArticleIds: [],
      top1StrongHit: null,
      recallAtK: null,
      mrrAtK: null,
      latencyMs: null,
      failureReason: runItem.failureReason ?? null,
      degraded: Boolean(runItem.degraded),
      resolvedMode: runItem.resolvedMode ?? null,
      staleCandidateCount: runItem.staleCandidateCount ?? 0,
    };
  }

  return {
    ...calculateQueryMetrics({
      labeledQuery,
      rankedArticleIds: runItem.rankedArticleIds,
      latencyMs: runItem.latencyMs,
      k,
    }),
    system: runItem.system,
    status: 'COMPLETED',
    failureReason: runItem.failureReason ?? null,
    degraded: Boolean(runItem.degraded),
    resolvedMode: runItem.resolvedMode ?? null,
    staleCandidateCount: runItem.staleCandidateCount ?? 0,
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

export function calculateSystemSummaryMetrics({ system, rows }) {
  const completedRows = rows.filter((row) => row.status === 'COMPLETED');
  const failedRows = rows.filter((row) => row.status === 'FAILED');
  const degradedRows = rows.filter((row) => row.degraded);

  return {
    system,
    attemptedQueryCount: rows.length,
    completedQueryCount: completedRows.length,
    failedQueryCount: failedRows.length,
    failureRate: ratio(failedRows.length, rows.length),
    degradedQueryCount: degradedRows.length,
    degradedRate: ratio(degradedRows.length, rows.length),
    failureReasonCounts: countBy(failedRows.map((row) => row.failureReason).filter(Boolean)),
    degradedModeCounts: countBy(degradedRows.map((row) => row.resolvedMode).filter(Boolean)),
    macroTop1StrongHit: nullableAverage(completedRows.map((row) => row.top1StrongHit)),
    macroRecallAtK: nullableAverage(completedRows.map((row) => row.recallAtK)),
    macroMrrAtK: nullableAverage(completedRows.map((row) => row.mrrAtK)),
    effectiveTop1StrongHit: average(rows.map((row) => row.status === 'COMPLETED' ? row.top1StrongHit : 0)),
    effectiveRecallAtK: average(rows.map((row) => row.status === 'COMPLETED' ? row.recallAtK : 0)),
    effectiveMrrAtK: average(rows.map((row) => row.status === 'COMPLETED' ? row.mrrAtK : 0)),
    averageLatencyMs: nullableAverage(completedRows.map((row) => row.latencyMs)),
    totalStaleCandidateCount: rows.reduce((sum, row) => sum + (row.staleCandidateCount ?? 0), 0),
  };
}

export function calculateComparisonMetrics({ catalogId, catalogArticleCount, k, generatedAt, systems, byQueryMetrics }) {
  const bySystem = systems.map((system) => calculateSystemSummaryMetrics({
    system,
    rows: byQueryMetrics.filter((row) => row.system === system),
  }));
  const evaluatedQueryCount = new Set(byQueryMetrics.map((row) => row.query)).size;

  return {
    catalogId,
    catalogArticleCount,
    evaluatedQueryCount,
    k,
    generatedAt,
    systems: bySystem,
    bestObservedSystemsByMetric: bestObservedSystems(bySystem),
    warnings: comparisonWarnings({ catalogArticleCount, evaluatedQueryCount, bySystem }),
  };
}

function average(values) {
  if (values.length === 0) {
    return 0;
  }

  return values.reduce((sum, value) => sum + value, 0) / values.length;
}

function nullableAverage(values) {
  const numericValues = values.filter((value) => Number.isFinite(value));
  return numericValues.length === 0 ? null : average(numericValues);
}

function ratio(numerator, denominator) {
  return denominator === 0 ? 0 : numerator / denominator;
}

function countBy(values) {
  return values.reduce((counts, value) => {
    counts[value] = (counts[value] ?? 0) + 1;
    return counts;
  }, {});
}

function bestObservedSystems(bySystem) {
  return {
    macroTop1StrongHit: bestSystemsBy(bySystem, 'macroTop1StrongHit'),
    macroRecallAtK: bestSystemsBy(bySystem, 'macroRecallAtK'),
    macroMrrAtK: bestSystemsBy(bySystem, 'macroMrrAtK'),
    effectiveRecallAtK: bestSystemsBy(bySystem, 'effectiveRecallAtK'),
    averageLatencyMs: bestSystemsBy(bySystem, 'averageLatencyMs', 'ascending'),
  };
}

function bestSystemsBy(bySystem, metricName, direction = 'descending') {
  const rows = bySystem.filter((row) => Number.isFinite(row[metricName]));
  if (rows.length === 0) {
    return [];
  }

  const bestValue = direction === 'ascending'
    ? Math.min(...rows.map((row) => row[metricName]))
    : Math.max(...rows.map((row) => row[metricName]));

  return rows
    .filter((row) => row[metricName] === bestValue)
    .map((row) => row.system);
}

function comparisonWarnings({ catalogArticleCount, evaluatedQueryCount, bySystem }) {
  const warnings = [];

  if (evaluatedQueryCount < 10) {
    warnings.push('label set is smaller than 10 reviewed queries, so this is a smoke benchmark');
  }

  if (catalogArticleCount < 20) {
    warnings.push('catalog article count is below 20, so ranking difficulty is still low');
  }

  for (const system of bySystem) {
    if (system.failedQueryCount > 0) {
      warnings.push(`${system.system} has failed query runs`);
    }

    if (system.degradedQueryCount > 0) {
      warnings.push(`${system.system} has degraded query runs`);
    }
  }

  return warnings;
}
