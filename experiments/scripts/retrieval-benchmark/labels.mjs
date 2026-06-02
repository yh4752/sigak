import { readFile } from 'node:fs/promises';

const VALID_QUERY_STATUSES = new Set(['needs_user_label', 'needs_label', 'needs_review', 'reviewed']);
const VALID_RELEVANCE = new Set(['strong', 'acceptable', 'not_relevant']);

export async function loadLabelFile(labelsPath) {
  const content = await readFile(labelsPath, 'utf8');
  return parseLabelDocument(JSON.parse(content));
}

export function parseLabelDocument(document) {
  if (document?.version !== 1) {
    throw new Error('Label JSON version must be 1.');
  }

  const catalogId = requireNonBlankString(document.catalogId, 'catalogId');
  const catalogArticleCount = requirePositiveInteger(document.catalogArticleCount, 'catalogArticleCount');

  if (!Array.isArray(document.queries)) {
    throw new Error('Label JSON queries must be an array.');
  }

  for (const query of document.queries) {
    if (!VALID_QUERY_STATUSES.has(query?.status)) {
      throw new Error(`Unsupported query status: ${query?.status}`);
    }
  }

  const queries = document.queries
    .filter((query) => query.status === 'reviewed')
    .map(parseReviewedQuery);

  if (queries.length === 0) {
    throw new Error('At least one reviewed query is required.');
  }

  return { catalogId, catalogArticleCount, queries };
}

function parseReviewedQuery(query) {
  const labels = Array.isArray(query.labels) ? query.labels : [];
  const strongArticleIds = [];
  const acceptableArticleIds = [];
  const notRelevantArticleIds = [];

  for (const label of labels) {
    const articleId = requirePositiveInteger(label?.articleId, 'articleId');
    const relevance = requireNonBlankString(label?.relevance, 'relevance');

    if (!VALID_RELEVANCE.has(relevance)) {
      throw new Error(`Unsupported relevance value: ${relevance}`);
    }

    if (relevance === 'strong') {
      strongArticleIds.push(articleId);
    }

    if (relevance === 'acceptable') {
      acceptableArticleIds.push(articleId);
    }

    if (relevance === 'not_relevant') {
      notRelevantArticleIds.push(articleId);
    }
  }

  if (strongArticleIds.length + acceptableArticleIds.length === 0) {
    throw new Error('Reviewed query must contain at least one strong or acceptable label.');
  }

  return {
    query: requireNonBlankString(query.query, 'query'),
    intent: requireNonBlankString(query.intent, 'intent'),
    strongArticleIds: uniqueNumbers(strongArticleIds),
    acceptableArticleIds: uniqueNumbers(acceptableArticleIds),
    notRelevantArticleIds: uniqueNumbers(notRelevantArticleIds),
  };
}

function requireNonBlankString(value, fieldName) {
  if (typeof value !== 'string' || value.trim().length === 0) {
    throw new Error(`${fieldName} must be a non-blank string.`);
  }

  return value.trim();
}

function requirePositiveInteger(value, fieldName) {
  if (!Number.isInteger(value) || value < 1) {
    throw new Error(`${fieldName} must be a positive integer.`);
  }

  return value;
}

function uniqueNumbers(values) {
  return [...new Set(values)];
}
