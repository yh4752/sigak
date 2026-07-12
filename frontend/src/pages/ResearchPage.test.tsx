import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ResearchPage from './ResearchPage'
import { fetchResearchOverview } from '../api/research'

vi.mock('../api/research', () => ({
  fetchResearchOverview: vi.fn(),
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
        name: 'keyword',
        attemptedQueryCount: 3,
        completedQueryCount: 3,
        failedQueryCount: 0,
        degradedQueryCount: 0,
        macroTop1StrongHit: 1,
        macroRecallAtK: 0.6667,
        macroMrrAtK: 1,
        averageLatencyMs: 21.67,
      },
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
    warnings: ['label set is smaller than 10 reviewed queries'],
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

describe('ResearchPage', () => {
  beforeEach(() => {
    vi.mocked(fetchResearchOverview).mockReset()
  })

  it('renders portfolio research metrics from the overview API', async () => {
    vi.mocked(fetchResearchOverview).mockResolvedValue(mockOverview)

    render(<ResearchPage />)

    expect(screen.getByRole('status')).toHaveTextContent('Loading research metrics...')
    expect(await screen.findByRole('heading', { name: 'Research Evidence' })).toBeInTheDocument()
    expect(screen.getByText('Hybrid Retrieval')).toBeInTheDocument()
    expect(screen.getByText('keyword')).toBeInTheDocument()
    expect(screen.getByText('hybrid')).toBeInTheDocument()
    expect(screen.getByText('graph rag failure')).toBeInTheDocument()
    expect(screen.getByText('41 articles')).toBeInTheDocument()
    expect(screen.getByText('Current retrieval and graph metrics are smoke evidence, not broad quality claims.')).toBeInTheDocument()
  })

  it('renders section unavailable states without hiding the rest of the dashboard', async () => {
    vi.mocked(fetchResearchOverview).mockResolvedValue({
      ...mockOverview,
      retrieval: {
        ...mockOverview.retrieval,
        available: false,
        unavailableReason: 'Artifact not found: results/retrieval/latest/metrics.comparison.json',
        systems: [],
      },
    })

    render(<ResearchPage />)

    expect(await screen.findByText('Retrieval artifact unavailable')).toBeInTheDocument()
    expect(screen.getByText('Artifact not found: results/retrieval/latest/metrics.comparison.json')).toBeInTheDocument()
    expect(screen.getByText('Graph Context')).toBeInTheDocument()
  })

  it('renders a retry action when the overview API fails', async () => {
    vi.mocked(fetchResearchOverview).mockRejectedValue(new Error('network'))

    render(<ResearchPage />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to load research metrics.')
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument()
  })
})
