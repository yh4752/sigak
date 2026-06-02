#!/usr/bin/env node

import { parseBenchmarkArgs } from './retrieval-benchmark/cli.mjs';
import { runRetrievalBenchmark } from './retrieval-benchmark/runner.mjs';

try {
  const args = parseBenchmarkArgs(process.argv.slice(2));
  const result = await runRetrievalBenchmark(args);

  console.log(`Retrieval benchmark complete: ${result.artifacts.reportPath}`);
  console.log(`Queries: ${result.summary.evaluatedQueryCount}`);
  console.log(`Top1 Strong Hit: ${result.summary.macroTop1StrongHit}`);
  console.log(`Recall@${result.summary.k}: ${result.summary.macroRecallAtK}`);
  console.log(`MRR@${result.summary.k}: ${result.summary.macroMrrAtK}`);
  console.log(`Average LatencyMs: ${result.summary.averageLatencyMs}`);
} catch (error) {
  console.error(error instanceof Error ? error.message : String(error));
  process.exitCode = 1;
}
