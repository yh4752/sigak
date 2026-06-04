export function createGraphContextClient({ baseUrl, fetchImpl = fetch, now = () => Date.now() }) {
  const normalizedBaseUrl = baseUrl.replace(/\/+$/, '');

  return {
    async fetchArticleDetail(articleId) {
      const response = await fetchImpl(`${normalizedBaseUrl}/api/articles/${encodeURIComponent(articleId)}`);

      if (!response.ok) {
        throw new Error(`Article detail API failed with status ${response.status}.`);
      }

      const body = await response.json();
      return mapArticleDetail(body);
    },

    async fetchGraphContextRow(articleId) {
      const start = now();
      let response;

      try {
        response = await fetchImpl(`${normalizedBaseUrl}/api/articles/${encodeURIComponent(articleId)}/graph-context`);
      } catch (error) {
        return createFailedRow({
          articleId,
          latencyMs: calculateLatencyMs(start, now()),
          failureReason: error instanceof Error ? error.message : String(error),
        });
      }

      if (!response.ok) {
        return createFailedRow({
          articleId,
          latencyMs: calculateLatencyMs(start, now()),
          failureReason: `Graph context API failed with status ${response.status}.`,
        });
      }

      let latencyMs;
      try {
        const body = await response.json();
        latencyMs = calculateLatencyMs(start, now());
        return mapGraphContextRow({ requestedArticleId: articleId, body, latencyMs });
      } catch (error) {
        return createFailedRow({
          articleId,
          latencyMs: Number.isFinite(latencyMs) ? latencyMs : calculateLatencyMs(start, now()),
          failureReason: error instanceof Error ? error.message : String(error),
        });
      }
    },
  };
}

function mapArticleDetail(body) {
  if (!Number.isInteger(body?.id)) {
    throw new Error('Article detail API response must include numeric id.');
  }

  if (!Array.isArray(body.relatedArticleIds) || body.relatedArticleIds.some((articleId) => !Number.isInteger(articleId))) {
    throw new Error('Article detail API response must include numeric relatedArticleIds.');
  }

  return {
    articleId: body.id,
    relatedArticleIds: body.relatedArticleIds,
  };
}

function mapGraphContextRow({ requestedArticleId, body, latencyMs }) {
  if (body?.articleId !== requestedArticleId) {
    throw new Error('Graph context API response articleId must match requested articleId.');
  }

  const relatedArticleReasons = mapRelatedArticleReasons(body.relatedArticleReasons);
  const topics = mapTopics(body.topics);

  return {
    articleId: requestedArticleId,
    status: 'COMPLETED',
    latencyMs,
    emptyContext: relatedArticleReasons.length === 0 && topics.length === 0,
    relatedArticleReasons,
    topics,
  };
}

function mapRelatedArticleReasons(value) {
  if (!Array.isArray(value)) {
    throw new Error('Graph context API response must include relatedArticleReasons array.');
  }

  return value.map((item) => {
    if (!Number.isInteger(item?.articleId)) {
      throw new Error('Graph context relatedArticleReasons item must include numeric articleId.');
    }

    if (item.reason !== null && typeof item.reason !== 'string') {
      throw new Error('Graph context relatedArticleReasons item reason must be a string or null.');
    }

    if (!Array.isArray(item.sharedTopics) || item.sharedTopics.some((topic) => typeof topic !== 'string')) {
      throw new Error('Graph context relatedArticleReasons item must include string sharedTopics.');
    }

    return {
      articleId: item.articleId,
      reason: item.reason,
      sharedTopics: item.sharedTopics,
    };
  });
}

function mapTopics(value) {
  if (!Array.isArray(value)) {
    throw new Error('Graph context API response must include topics array.');
  }

  return value.map((item) => {
    if (typeof item?.displayName !== 'string') {
      throw new Error('Graph context topics item must include string displayName.');
    }

    return item.displayName;
  });
}

function createFailedRow({ articleId, latencyMs, failureReason }) {
  return {
    articleId,
    status: 'FAILED',
    latencyMs,
    failureReason,
    emptyContext: false,
    relatedArticleReasons: [],
    topics: [],
  };
}

function calculateLatencyMs(start, end) {
  return Math.max(0, Math.round(end - start));
}
