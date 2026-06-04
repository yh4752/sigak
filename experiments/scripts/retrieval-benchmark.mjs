#!/usr/bin/env node

import { parseBenchmarkArgs } from './retrieval-benchmark/cli.mjs';
import { runRetrievalBenchmark } from './retrieval-benchmark/runner.mjs';

try {
  const args = parseBenchmarkArgs(process.argv.slice(2));
  const result = await runRetrievalBenchmark(args);

  console.log(`Retrieval benchmark complete: ${result.artifacts.reportPath}`);
  if (result.comparison) {
    console.log(`Queries: ${result.comparison.evaluatedQueryCount}`);
    console.log(`Systems: ${result.bySystemMetrics.map((metric) => metric.system).join(', ')}`);
    console.log('Comparison metrics: metrics.by-system.json and metrics.comparison.json');
  } else {
    console.log(`Queries: ${result.summary.evaluatedQueryCount}`);
    console.log(`Top1 Strong Hit: ${result.summary.macroTop1StrongHit}`);
    console.log(`Recall@${result.summary.k}: ${result.summary.macroRecallAtK}`);
    console.log(`MRR@${result.summary.k}: ${result.summary.macroMrrAtK}`);
    console.log(`Average LatencyMs: ${result.summary.averageLatencyMs}`);
  }

  if (result.graph) {
    console.log(`Graph-aware evaluation complete: ${result.graph.artifacts.reportPath}`);
    console.log(`Graph queries: ${result.graph.summary.evaluatedQueryCount}`);
    console.log(`Graph Context Coverage@${result.graph.summary.k}: ${result.graph.summary.macroGraphContextCoverageAtK}`);
  }
} catch (error) {
  console.error(error instanceof Error ? error.message : String(error));
  process.exitCode = 1;
}
