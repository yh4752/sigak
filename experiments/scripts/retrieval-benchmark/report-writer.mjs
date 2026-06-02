import { mkdir, writeFile } from 'node:fs/promises';
import { join } from 'node:path';

export async function writeBenchmarkArtifacts({ outputDir, run, byQueryMetrics, summary }) {
  await mkdir(outputDir, { recursive: true });

  const runPath = join(outputDir, `runs.${run.system}.json`);
  const byQueryPath = join(outputDir, 'metrics.by-query.json');
  const summaryPath = join(outputDir, 'metrics.summary.json');
  const reportPath = join(outputDir, 'report.md');

  await writeJson(runPath, run);
  await writeJson(byQueryPath, byQueryMetrics);
  await writeJson(summaryPath, summary);
  await writeFile(reportPath, buildMarkdownReport({ run, byQueryMetrics, summary }), 'utf8');

  return { runPath, byQueryPath, summaryPath, reportPath };
}

export async function writeComparisonArtifacts({ outputDir, runsBySystem, byQueryMetrics, bySystemMetrics, comparison }) {
  await mkdir(outputDir, { recursive: true });

  const runPaths = {};
  for (const [system, run] of Object.entries(runsBySystem)) {
    const runPath = join(outputDir, `runs.${system}.json`);
    await writeJson(runPath, run);
    runPaths[system] = runPath;
  }

  const byQueryPath = join(outputDir, 'metrics.by-query.json');
  const bySystemPath = join(outputDir, 'metrics.by-system.json');
  const comparisonPath = join(outputDir, 'metrics.comparison.json');
  const reportPath = join(outputDir, 'report.md');

  await writeJson(byQueryPath, byQueryMetrics);
  await writeJson(bySystemPath, bySystemMetrics);
  await writeJson(comparisonPath, comparison);
  await writeFile(
    reportPath,
    buildComparisonMarkdownReport({ byQueryMetrics, bySystemMetrics, comparison }),
    'utf8'
  );

  return { runPaths, byQueryPath, bySystemPath, comparisonPath, reportPath };
}

export function buildMarkdownReport({ run, byQueryMetrics, summary }) {
  const queryRows = byQueryMetrics
    .map((metric) => [
      escapeTable(metric.query),
      metric.top1StrongHit,
      metric.recallAtK,
      metric.mrrAtK,
      metric.latencyMs,
      metric.resultArticleIds.join(', '),
    ].join(' | '))
    .map((row) => `| ${row} |`)
    .join('\n');

  return `# Retrieval Benchmark Smoke Report

이 report는 public article search API의 현재 검색 모드를 사람이 검토한 label JSON과 비교하는 smoke benchmark 결과다.

## Summary

| Metric | Value |
| --- | ---: |
| Catalog ID | ${summary.catalogId} |
| Catalog articles | ${summary.catalogArticleCount} |
| Evaluated queries | ${summary.evaluatedQueryCount} |
| K | ${summary.k} |
| Top1 Strong Hit | ${summary.macroTop1StrongHit} |
| Recall@${summary.k} | ${summary.macroRecallAtK} |
| MRR@${summary.k} | ${summary.macroMrrAtK} |
| Average LatencyMs | ${summary.averageLatencyMs} |

## Metric Notes

- Top1 Strong Hit: 첫 번째 결과가 strong article이면 1이고, 아니면 0이다.
- Recall@${summary.k}: Top${summary.k} 안에 strong 또는 acceptable article이 얼마나 포함됐는지 본다.
- MRR@${summary.k}: Top${summary.k} 안에서 첫 strong article이 얼마나 빨리 등장하는지 본다.
- LatencyMs: public article search API round-trip 시간을 ms 단위로 한 번 측정한 값이다.

## Query Results

| Query | Top1 Strong Hit | Recall@${summary.k} | MRR@${summary.k} | LatencyMs | Result IDs |
| --- | ---: | ---: | ---: | ---: | --- |
${queryRows}

## Run Conditions

- System: ${run.system}
- Base URL: ${run.baseUrl}
- Labels: ${run.labelsPath}
- Generated at: ${summary.generatedAt}

## Limits

이 결과는 smoke benchmark다. Query와 article 수가 작기 때문에 검색 품질 개선의 최종 근거가 아니라, qrels -> run -> metrics -> report 파이프라인이 재현 가능하게 동작하는지 확인하는 용도다.
`;
}

export function buildComparisonMarkdownReport({ byQueryMetrics, bySystemMetrics, comparison }) {
  const systemRows = bySystemMetrics
    .map((metric) => [
      metric.system,
      metric.completedQueryCount,
      metric.failedQueryCount,
      formatMetric(metric.failureRate),
      formatMetric(metric.degradedRate),
      formatMetric(metric.macroTop1StrongHit),
      formatMetric(metric.macroRecallAtK),
      formatMetric(metric.macroMrrAtK),
      formatMetric(metric.effectiveRecallAtK),
      formatMetric(metric.averageLatencyMs),
    ].join(' | '))
    .map((row) => `| ${row} |`)
    .join('\n');

  const queryRows = byQueryMetrics
    .map((metric) => [
      escapeTable(metric.query),
      metric.system,
      metric.status,
      metric.resolvedMode ?? '-',
      formatMetric(metric.top1StrongHit),
      formatMetric(metric.recallAtK),
      formatMetric(metric.mrrAtK),
      formatMetric(metric.latencyMs),
      metric.resultArticleIds.join(', ') || '-',
      metric.failureReason ?? '-',
    ].join(' | '))
    .map((row) => `| ${row} |`)
    .join('\n');

  const bestObservedRows = Object.entries(comparison.bestObservedSystemsByMetric)
    .map(([metricName, systems]) => `- ${metricName}: ${formatSystems(systems)}`)
    .join('\n');
  const warnings = comparison.warnings.map((warning) => `- ${warning}`).join('\n');

  return `# Retrieval Benchmark Smoke Comparison Report

이 report는 strict retrieval system과 public search behavior를 같은 label set으로 나란히 비교한 결과다.

## System Comparison

| System | Completed | Failed | Failure Rate | Degraded Rate | Top1 Strong Hit | Recall@K | MRR@K | Effective Recall@K | Avg LatencyMs |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
${systemRows}

## Best Observed Systems

The phrase "best observed system in this smoke run" is intentional because the current dataset is still small.

${bestObservedRows}

## Query Results

| Query | System | Status | Resolved Mode | Top1 Strong Hit | Recall@K | MRR@K | LatencyMs | Result IDs | Failure Reason |
| --- | --- | --- | --- | ---: | ---: | ---: | ---: | --- | --- |
${queryRows}

## Limitations

${warnings}

## Strict HYBRID vs PUBLIC

- Strict HYBRID is an experiment system created by the internal evaluation endpoint.
- Strict HYBRID fails when keyword or vector retrieval fails.
- PUBLIC is user-visible behavior from GET /api/articles and may degrade.
- PUBLIC is not sent to the internal evaluation endpoint.
- PUBLIC degrade metadata is read from the internal last-search metrics endpoint immediately after each sequential public request, so run this benchmark without concurrent search traffic.
`;
}

async function writeJson(path, value) {
  await writeFile(path, `${JSON.stringify(value, null, 2)}\n`, 'utf8');
}

function escapeTable(value) {
  return String(value).replaceAll('|', '\\|');
}

function formatMetric(value) {
  return Number.isFinite(value) ? String(value) : '-';
}

function formatSystems(systems) {
  return Array.isArray(systems) && systems.length > 0 ? systems.join(', ') : 'n/a';
}
