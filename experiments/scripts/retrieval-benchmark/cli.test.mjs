import test from 'node:test';
import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { createServer } from 'node:http';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { promisify } from 'node:util';
import { parseBenchmarkArgs } from './cli.mjs';

const execFileAsync = promisify(execFile);
const entrypointPath = resolve('experiments/scripts/retrieval-benchmark.mjs');

test('parseBenchmarkArgs reads required options and defaults k to 5', () => {
  const args = parseBenchmarkArgs([
    '--labels=experiments/datasets/labels/sample.json',
    '--base-url=http://localhost:8080',
    '--output-dir=experiments/results/retrieval/latest',
  ]);

  assert.deepEqual(args, {
    labelsPath: 'experiments/datasets/labels/sample.json',
    baseUrl: 'http://localhost:8080',
    outputDir: 'experiments/results/retrieval/latest',
    k: 5,
  });
});

test('parseBenchmarkArgs reads explicit k', () => {
  const args = parseBenchmarkArgs([
    '--labels=labels.json',
    '--base-url=http://localhost:8080',
    '--output-dir=out',
    '--k=3',
  ]);

  assert.equal(args.k, 3);
});

test('parseBenchmarkArgs rejects missing required options', () => {
  assert.throws(
    () => parseBenchmarkArgs(['--labels=labels.json', '--base-url=http://localhost:8080']),
    /Missing required option: --output-dir/
  );
});

test('parseBenchmarkArgs rejects blank option values', () => {
  assert.throws(
    () => parseBenchmarkArgs(['--labels= ', '--base-url=http://localhost:8080', '--output-dir=out']),
    /Option --labels must not be blank/
  );
});

test('parseBenchmarkArgs rejects invalid k', () => {
  assert.throws(
    () => parseBenchmarkArgs(['--labels=labels.json', '--base-url=http://localhost:8080', '--output-dir=out', '--k=0']),
    /Option --k must be a positive integer/
  );
});

test('parseBenchmarkArgs rejects unsafe integer k', () => {
  assert.throws(
    () => parseBenchmarkArgs([
      '--labels=labels.json',
      '--base-url=http://localhost:8080',
      '--output-dir=out',
      `--k=${'9'.repeat(400)}`,
    ]),
    /Option --k must be a positive integer/
  );
});

test('CLI entrypoint exits nonzero and prints parser errors', async () => {
  await assert.rejects(
    () => execFileAsync(process.execPath, [entrypointPath], { cwd: resolve('.') }),
    (error) => {
      assert.equal(error.code, 1);
      assert.match(error.stderr, /Missing required option: --labels/);
      return true;
    }
  );
});

test('CLI entrypoint runs benchmark and prints summary metrics', async () => {
  const workspace = await mkdtemp(join(tmpdir(), 'sigak-cli-'));
  const labelsPath = join(workspace, 'labels.json');
  const outputDir = join(workspace, 'results');
  const server = createServer((request, response) => {
    assert.equal(request.url, '/api/articles?query=graph+rag+failure');
    response.writeHead(200, { 'Content-Type': 'application/json' });
    response.end(JSON.stringify([{ id: 4 }, { id: 1 }, { id: 2 }]));
  });

  await writeFile(labelsPath, JSON.stringify({
    version: 1,
    catalogId: 'api-ready-test',
    catalogArticleCount: 6,
    queries: [{
      query: 'graph rag failure',
      intent: 'Graph RAG 실패 유형과 평가 기준을 찾는다.',
      status: 'reviewed',
      labels: [
        { articleId: 4, relevance: 'strong', note: '' },
        { articleId: 1, relevance: 'acceptable', note: '' },
      ],
    }],
  }), 'utf8');

  try {
    await new Promise((resolveListening) => server.listen(0, '127.0.0.1', resolveListening));
    const { port } = server.address();
    const { stdout } = await execFileAsync(process.execPath, [
      entrypointPath,
      `--labels=${labelsPath}`,
      `--base-url=http://127.0.0.1:${port}`,
      `--output-dir=${outputDir}`,
    ], { cwd: resolve('.') });

    assert.match(stdout, /Retrieval benchmark complete:/);
    assert.match(stdout, /Queries: 1/);
    assert.match(stdout, /Top1 Strong Hit: 1/);
    assert.match(stdout, /Recall@5: 1/);
    assert.match(stdout, /MRR@5: 1/);
    assert.match(await readFile(join(outputDir, 'report.md'), 'utf8'), /smoke benchmark/);
  } finally {
    await new Promise((resolveClose) => server.close(resolveClose));
    await rm(workspace, { recursive: true, force: true });
  }
});
