import { mkdir, writeFile } from 'node:fs/promises';
import { join } from 'node:path';

export async function writeBenchmarkArtifacts({ outputDir, run, byQueryMetrics, summary }) {
  await mkdir(outputDir, { recursive: true });

  const runPath = join(outputDir, 'runs.hybrid.json');
  const byQueryPath = join(outputDir, 'metrics.by-query.json');
  const summaryPath = join(outputDir, 'metrics.summary.json');
  const reportPath = join(outputDir, 'report.md');

  await writeJson(runPath, run);
  await writeJson(byQueryPath, byQueryMetrics);
  await writeJson(summaryPath, summary);
  await writeFile(reportPath, buildMarkdownReport({ run, byQueryMetrics, summary }), 'utf8');

  return { runPath, byQueryPath, summaryPath, reportPath };
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

async function writeJson(path, value) {
  await writeFile(path, `${JSON.stringify(value, null, 2)}\n`, 'utf8');
}

function escapeTable(value) {
  return String(value).replaceAll('|', '\\|');
}
