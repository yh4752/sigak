import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchResearchOverview } from './research'
import { httpClient } from './httpClient'

vi.mock('./httpClient', () => ({
  httpClient: {
    get: vi.fn(),
  },
}))

const mockOverview = {
  generatedAt: '2026-06-12T00:00:00Z',
  retrieval: {
    available: true,
    unavailableReason: null,
    catalogId: 'api-ready-2026-06-02',
    catalogArticleCount: 6,
    evaluatedQueryCount: 3,
    k: 5,
    generatedAt: '2026-06-02T15:06:29.546Z',
    systems: [
      {
        name: 'hybrid',
        attemptedQueryCount: 3,
        completedQueryCount: 3,
        failedQueryCount: 0,
        degradedQueryCount: 0,
        macroTop1StrongHit: 0.6667,
        macroRecallAtK: 0.8333,
        macroMrrAtK: 0.8333,
        averageLatencyMs: 15,
      },
    ],
    warnings: ['smoke benchmark'],
  },
  graph: {
    available: true,
    unavailableReason: null,
    catalogId: 'api-ready-2026-06-02',
    catalogArticleCount: 6,
    evaluatedQueryCount: 3,
    k: 5,
    generatedAt: '2026-06-04T03:21:51.802Z',
    macroSearchPositiveCoverageAtK: 0.6667,
    macroRelatedBaselineCoverageAtK: 0.3333,
    macroGraphContextCoverageAtK: 0.3333,
    macroGraphReasonedCoverageAtK: 0.3333,
    graphContextFailureRate: 0,
    emptyContextRate: 0,
    averageGraphLatencyMs: 33.53,
    queryEvidence: [
      {
        query: 'graph rag failure',
        intent: 'Find graph RAG failure analysis.',
        searchPositiveCoverageAtK: 0.5,
        relatedBaselineCoverageAtK: 0,
        graphContextCoverageAtK: 0.5,
        graphReasonedCoverageAtK: 0.5,
        reasonCoverage: 1,
        topicContextCoverage: 1,
        averageGraphLatencyMs: 8.8,
        searchMissedPositiveIds: [1],
      },
    ],
    warnings: ['graph latency is measured as public API round-trip'],
  },
  expandedCatalog: {
    available: true,
    unavailableReason: null,
    catalogId: 'api-ready-2026-06-05',
    generatedAt: '2026-06-04T15:45:30.641155Z',
    articleCount: 41,
    labelStatus: 'draft_only',
    benchmarkStatus: 'blocked_until_reviewed_labels',
    warnings: ['Reviewed labels are required before quality claims use the expanded catalog.'],
  },
  limitations: ['Current retrieval and graph metrics are smoke evidence, not broad quality claims.'],
}

describe('fetchResearchOverview', () => {
  beforeEach(() => {
    vi.mocked(httpClient.get).mockReset()
  })

  it('requests the research overview and validates the response', async () => {
    vi.mocked(httpClient.get).mockResolvedValue({ data: mockOverview })

    const overview = await fetchResearchOverview()

    expect(httpClient.get).toHaveBeenCalledWith('/api/research/overview')
    expect(overview).toEqual(mockOverview)
  })

  it('rejects invalid research overview responses before they reach the UI', async () => {
    vi.mocked(httpClient.get).mockResolvedValue({
      data: {
        ...mockOverview,
        retrieval: { ...mockOverview.retrieval, systems: [{ name: 'hybrid' }] },
      },
    })

    await expect(fetchResearchOverview()).rejects.toThrow()
  })
})
