import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { join } from 'node:path';

export async function writeGraphArtifacts({ outputDir, run, byQueryMetrics, summary }) {
  await mkdir(outputDir, { recursive: true });

  const runPath = join(outputDir, 'graph-context.runs.json');
  const byQueryPath = join(outputDir, 'graph-context.metrics.by-query.json');
  const summaryPath = join(outputDir, 'graph-context.metrics.summary.json');
  const reportPath = join(outputDir, 'report.md');

  await writeJson(runPath, run);
  await writeJson(byQueryPath, byQueryMetrics);
  await writeJson(summaryPath, summary);
  await appendOrWriteReport(reportPath, buildGraphMarkdownReport({ run, byQueryMetrics, summary }));

  return { runPath, byQueryPath, summaryPath, reportPath };
}

export function buildGraphMarkdownReport({ run, byQueryMetrics, summary }) {
  const warningRows = (summary.warnings ?? []).map((warning) => `- ${warning}`).join('\n');
  const queryRows = byQueryMetrics
    .map((metric) => [
      escapeTable(metric.query),
      formatMetric(metric.searchPositiveCoverageAtK),
      formatMetric(metric.graphContextCoverageAtK),
      formatMetric(metric.graphContextCoverageAmongSearchHits),
      formatMetric(metric.graphReasonedCoverageAtK),
      formatMetric(metric.reasonCoverage),
      formatMetric(metric.topicContextCoverage),
      formatIds(metric.searchMissedPositiveIds),
      formatMetric(metric.averageGraphLatencyMs),
    ].join(' | '))
    .map((row) => `| ${row} |`)
    .join('\n');

  return `# Graph-Aware Evaluation Smoke Report

This report evaluates graph-aware article detail context collected from public article APIs.
The graph evaluation is article detail explanation evidence, not query ranking.

## Summary Metrics

| Metric | Value |
| --- | ---: |
| Catalog ID | ${summary.catalogId} |
| Catalog articles | ${summary.catalogArticleCount} |
| Evaluated queries | ${summary.evaluatedQueryCount} |
| K | ${summary.k} |
| Search Positive Coverage@K | ${formatMetric(summary.macroSearchPositiveCoverageAtK)} |
| Related Baseline Coverage@K | ${formatMetric(summary.macroRelatedBaselineCoverageAtK)} |
| Graph Context Coverage@K | ${formatMetric(summary.macroGraphContextCoverageAtK)} |
| Graph Context Coverage Among Search Hits | ${formatMetric(summary.macroGraphContextCoverageAmongSearchHits)} |
| Graph Reasoned Coverage@K | ${formatMetric(summary.macroGraphReasonedCoverageAtK)} |
| Reason Coverage | ${formatMetric(summary.macroReasonCoverage)} |
| Topic Context Coverage | ${formatMetric(summary.macroTopicContextCoverage)} |
| Graph Context Failure Rate | ${formatMetric(summary.graphContextFailureRate)} |
| Empty Context Rate | ${formatMetric(summary.emptyContextRate)} |
| Average Graph LatencyMs | ${formatMetric(summary.averageGraphLatencyMs)} |

## Query Results

| Query | Search Positive Coverage@K | Graph Context Coverage@K | Graph Coverage Among Search Hits | Graph Reasoned Coverage@K | Reason Coverage | Topic Context Coverage | Search Missed Positive IDs | Avg Graph LatencyMs |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- | ---: |
${queryRows}

## Interpretation

- Search miss means a positive article was not present in the public search top-k, so graph context may never have a detail-page chance to explain it.
- Related baseline coverage comes from article detail related IDs, while graph context coverage comes from public graph-context reasons.
- Graph reasons are stored projection reasons, not independently verified factual explanations.
- This graph evaluation is article detail explanation evidence, not query ranking.

## Run Conditions

- Base URL: ${run.baseUrl}
- Labels: ${run.labelsPath}
- Labels SHA-256: ${run.labelsSha256}
- Source article selection: ${run.sourceArticleSelection}
- Graph context endpoint: ${run.graphContextEndpoint}
- Reason source: ${run.reasonSource}
- Generated at: ${run.generatedAt ?? summary.generatedAt}
- Latency is public API round-trip, not pure Neo4j query latency.

## Limits And Warnings

${warningRows}

The current smoke/small dataset limitations mean these artifacts verify that the graph-aware evaluation pipeline runs, not that graph quality or search quality has been proven.
`;
}

async function appendOrWriteReport(reportPath, graphReport) {
  const existingReport = await readExistingReport(reportPath);
  const content = existingReport === null ? graphReport : `${trimEndNewline(existingReport)}\n\n---\n\n${graphReport}`;
  await writeFile(reportPath, content, 'utf8');
}

async function readExistingReport(reportPath) {
  try {
    return await readFile(reportPath, 'utf8');
  } catch (error) {
    if (error?.code === 'ENOENT') {
      return null;
    }
    throw error;
  }
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

function formatIds(value) {
  return Array.isArray(value) && value.length > 0 ? value.join(', ') : '-';
}

function trimEndNewline(value) {
  return value.replace(/\n+$/, '');
}
