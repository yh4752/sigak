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
    limit: 20,
    includeGraphContext: false,
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
  assert.equal(args.limit, 20);
});

test('parseBenchmarkArgs reads systems and limit for comparison mode', () => {
  const args = parseBenchmarkArgs([
    '--labels=labels.json',
    '--base-url=http://localhost:8080',
    '--output-dir=out',
    '--systems=keyword,vector,hybrid,public,HYBRID',
    '--k=5',
    '--limit=20',
  ]);

  assert.deepEqual(args.systems, ['keyword', 'vector', 'hybrid', 'public']);
  assert.equal(args.limit, 20);
});

test('parseBenchmarkArgs keeps systems undefined for existing smoke mode', () => {
  const args = parseBenchmarkArgs([
    '--labels=labels.json',
    '--base-url=http://localhost:8080',
    '--output-dir=out',
  ]);

  assert.equal(args.systems, undefined);
  assert.equal(args.limit, 20);
});

test('parseBenchmarkArgs reads include graph context for public comparison mode', () => {
  const args = parseBenchmarkArgs([
    '--labels=labels.json',
    '--base-url=http://localhost:8080',
    '--output-dir=out',
    '--systems=public',
    '--include-graph-context',
  ]);

  assert.equal(args.includeGraphContext, true);
});

test('parseBenchmarkArgs keeps options after include graph context flag', () => {
  const args = parseBenchmarkArgs([
    '--labels=labels.json',
    '--base-url=http://localhost:8080',
    '--output-dir=out',
    '--include-graph-context',
    '--systems=public',
    '--k=3',
  ]);

  assert.equal(args.includeGraphContext, true);
  assert.deepEqual(args.systems, ['public']);
  assert.equal(args.k, 3);
});

test('parseBenchmarkArgs defaults include graph context to false', () => {
  const args = parseBenchmarkArgs([
    '--labels=labels.json',
    '--base-url=http://localhost:8080',
    '--output-dir=out',
  ]);

  assert.equal(args.includeGraphContext, false);
});

test('parseBenchmarkArgs rejects include graph context value', () => {
  assert.throws(
    () => parseBenchmarkArgs([
      '--labels=labels.json',
      '--base-url=http://localhost:8080',
      '--output-dir=out',
      '--include-graph-context=true',
    ]),
    /Option --include-graph-context must not include a value/
  );
});

test('parseBenchmarkArgs rejects include graph context without public results', () => {
  assert.throws(
    () => parseBenchmarkArgs([
      '--labels=labels.json',
      '--base-url=http://localhost:8080',
      '--output-dir=out',
      '--systems=keyword,hybrid',
      '--include-graph-context',
    ]),
    /Option --include-graph-context requires public search results/
  );
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

test('parseBenchmarkArgs rejects unknown systems', () => {
  assert.throws(
    () => parseBenchmarkArgs([
      '--labels=labels.json',
      '--base-url=http://localhost:8080',
      '--output-dir=out',
      '--systems=hybrid,graph',
    ]),
    /Option --systems contains unknown value: graph/
  );
});

test('parseBenchmarkArgs rejects blank systems entries', () => {
  assert.throws(
    () => parseBenchmarkArgs([
      '--labels=labels.json',
      '--base-url=http://localhost:8080',
      '--output-dir=out',
      '--systems= , ',
    ]),
    /Option --systems must include at least one system/
  );
});

test('parseBenchmarkArgs rejects mixed blank systems entries', () => {
  assert.throws(
    () => parseBenchmarkArgs([
      '--labels=labels.json',
      '--base-url=http://localhost:8080',
      '--output-dir=out',
      '--systems=keyword, ,public',
    ]),
    /Option --systems must not include blank values/
  );
});

test('parseBenchmarkArgs rejects invalid limit', () => {
  assert.throws(
    () => parseBenchmarkArgs([
      '--labels=labels.json',
      '--base-url=http://localhost:8080',
      '--output-dir=out',
      '--limit=101',
    ]),
    /Option --limit must be an integer between 1 and 100/
  );
});

test('parseBenchmarkArgs rejects limit smaller than k', () => {
  assert.throws(
    () => parseBenchmarkArgs([
      '--labels=labels.json',
      '--base-url=http://localhost:8080',
      '--output-dir=out',
      '--k=5',
      '--limit=3',
    ]),
    /Option --limit must be greater than or equal to --k/
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

test('CLI entrypoint runs graph-aware public benchmark and prints graph summary', async () => {
  const workspace = await mkdtemp(join(tmpdir(), 'sigak-cli-graph-'));
  const labelsPath = join(workspace, 'labels.json');
  const outputDir = join(workspace, 'results');
  const server = createServer((request, response) => {
    response.writeHead(200, { 'Content-Type': 'application/json' });

    if (request.url === '/api/articles?query=graph+rag+failure') {
      response.end(JSON.stringify([{ id: 4 }, { id: 1 }]));
      return;
    }

    if (request.url === '/api/internal/search-metrics/articles') {
      response.end(JSON.stringify({ lastSearch: { mode: 'HYBRID', staleCandidateCount: 0 } }));
      return;
    }

    if (request.url === '/api/articles/4') {
      response.end(JSON.stringify({ id: 4, relatedArticleIds: [1] }));
      return;
    }

    if (request.url === '/api/articles/1') {
      response.end(JSON.stringify({ id: 1, relatedArticleIds: [] }));
      return;
    }

    if (request.url === '/api/articles/4/graph-context') {
      response.end(JSON.stringify({
        articleId: 4,
        relatedArticleReasons: [{ articleId: 1, reason: 'Stored graph relation.', sharedTopics: [] }],
        topics: [{ name: 'graph rag', displayName: 'Graph RAG', relatedArticleIds: [1] }],
      }));
      return;
    }

    if (request.url === '/api/articles/1/graph-context') {
      response.end(JSON.stringify({ articleId: 1, relatedArticleReasons: [], topics: [] }));
      return;
    }

    response.statusCode = 404;
    response.end(JSON.stringify({ message: 'not found' }));
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
      '--systems=public',
      '--include-graph-context',
    ], { cwd: resolve('.') });

    assert.match(stdout, /Graph-aware evaluation complete:/);
    assert.match(stdout, /Graph queries: 1/);
    assert.match(stdout, /Graph Context Coverage@5: 0.5/);
    assert.match(await readFile(join(outputDir, 'graph-context.metrics.summary.json'), 'utf8'), /macroGraphContextCoverageAtK/);
  } finally {
    await new Promise((resolveClose) => server.close(resolveClose));
    await rm(workspace, { recursive: true, force: true });
  }
});
