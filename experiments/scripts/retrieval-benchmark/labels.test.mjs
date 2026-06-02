import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { loadLabelFile, parseLabelDocument } from './labels.mjs';

const baseDocument = {
  version: 1,
  catalogId: 'api-ready-test',
  catalogArticleCount: 6,
  queries: [
    {
      query: 'agent evaluation',
      intent: 'LLM agent 평가를 찾는다.',
      status: 'reviewed',
      labels: [
        { articleId: 1, relevance: 'strong', note: 'direct' },
        { articleId: 4, relevance: 'acceptable', note: 'related' },
        { articleId: 5, relevance: 'not_relevant', note: 'off topic' },
      ],
    },
    {
      query: 'draft query',
      intent: '아직 검토하지 않았다.',
      status: 'needs_label',
      labels: [{ articleId: 2, relevance: 'strong', note: '' }],
    },
  ],
};

test('parseLabelDocument returns only reviewed queries with positive labels', () => {
  const parsed = parseLabelDocument(baseDocument);

  assert.equal(parsed.catalogId, 'api-ready-test');
  assert.equal(parsed.catalogArticleCount, 6);
  assert.equal(parsed.queries.length, 1);
  assert.deepEqual(parsed.queries[0], {
    query: 'agent evaluation',
    intent: 'LLM agent 평가를 찾는다.',
    strongArticleIds: [1],
    acceptableArticleIds: [4],
    notRelevantArticleIds: [5],
  });
});

test('parseLabelDocument accepts current labeling tool draft statuses', () => {
  const parsed = parseLabelDocument({
    ...baseDocument,
    queries: [
      baseDocument.queries[0],
      {
        query: 'draft query',
        intent: '사용자 라벨링 대기 상태다.',
        status: 'needs_user_label',
        labels: [],
      },
      {
        query: 'review query',
        intent: '검토가 필요한 상태다.',
        status: 'needs_review',
        labels: [{ articleId: 2, relevance: 'strong', note: '' }],
      },
    ],
  });

  assert.equal(parsed.queries.length, 1);
  assert.equal(parsed.queries[0].query, 'agent evaluation');
});

test('parseLabelDocument rejects unsupported version', () => {
  assert.throws(
    () => parseLabelDocument({ ...baseDocument, version: 2 }),
    /Label JSON version must be 1/
  );
});

test('parseLabelDocument rejects missing catalog metadata', () => {
  assert.throws(
    () => parseLabelDocument({ ...baseDocument, catalogId: '' }),
    /catalogId must be a non-blank string/
  );

  assert.throws(
    () => parseLabelDocument({ ...baseDocument, catalogArticleCount: 0 }),
    /catalogArticleCount must be a positive integer/
  );
});

test('parseLabelDocument rejects when no reviewed query exists', () => {
  assert.throws(
    () => parseLabelDocument({ ...baseDocument, queries: [{ ...baseDocument.queries[1] }] }),
    /At least one reviewed query is required/
  );
});

test('parseLabelDocument rejects unsupported query status before filtering reviewed queries', () => {
  assert.throws(
    () => parseLabelDocument({
      ...baseDocument,
      queries: [
        baseDocument.queries[0],
        {
          query: 'typo status',
          intent: 'status typo should not be ignored.',
          status: 'review',
          labels: [{ articleId: 2, relevance: 'strong', note: '' }],
        },
      ],
    }),
    /Unsupported query status: review/
  );
});

test('parseLabelDocument rejects reviewed query without a positive label', () => {
  assert.throws(
    () => parseLabelDocument({
      ...baseDocument,
      queries: [{
        query: 'postgres',
        intent: 'vector search',
        status: 'reviewed',
        labels: [{ articleId: 5, relevance: 'not_relevant', note: '' }],
      }],
    }),
    /Reviewed query must contain at least one strong or acceptable label/
  );
});

test('parseLabelDocument rejects unsupported relevance values', () => {
  assert.throws(
    () => parseLabelDocument({
      ...baseDocument,
      queries: [{
        query: 'postgres',
        intent: 'vector search',
        status: 'reviewed',
        labels: [{ articleId: 5, relevance: 'maybe', note: '' }],
      }],
    }),
    /Unsupported relevance value: maybe/
  );
});

test('loadLabelFile reads JSON label documents from disk', async () => {
  const workspace = await mkdtemp(join(tmpdir(), 'sigak-labels-'));
  const labelsPath = join(workspace, 'labels.json');

  try {
    await writeFile(labelsPath, JSON.stringify(baseDocument), 'utf8');

    const parsed = await loadLabelFile(labelsPath);

    assert.equal(parsed.catalogId, 'api-ready-test');
    assert.equal(parsed.queries.length, 1);
  } finally {
    await rm(workspace, { recursive: true, force: true });
  }
});
