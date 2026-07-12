import { render, screen } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import App from './App'
import { fetchArticles } from './api/articles'
import { fetchResearchOverview } from './api/research'

vi.mock('./api/articles', () => ({
  fetchArticles: vi.fn(),
  fetchArticle: vi.fn(),
  fetchArticleGraphContext: vi.fn(),
  fetchArticlesByIds: vi.fn(),
}))

vi.mock('./api/research', () => ({
  fetchResearchOverview: vi.fn(),
}))

const mockResearchOverview = {
  generatedAt: '2026-06-12T00:00:00Z',
  retrieval: {
    available: false,
    unavailableReason: 'Artifact not found: results/retrieval/latest/metrics.comparison.json',
    catalogId: null,
    catalogArticleCount: null,
    evaluatedQueryCount: null,
    k: null,
    generatedAt: null,
    systems: [],
    warnings: [],
  },
  graph: {
    available: false,
    unavailableReason: 'Artifact not found: results/graph/latest/graph-context.metrics.summary.json',
    catalogId: null,
    catalogArticleCount: null,
    evaluatedQueryCount: null,
    k: null,
    generatedAt: null,
    macroSearchPositiveCoverageAtK: null,
    macroRelatedBaselineCoverageAtK: null,
    macroGraphContextCoverageAtK: null,
    macroGraphReasonedCoverageAtK: null,
    graphContextFailureRate: null,
    emptyContextRate: null,
    averageGraphLatencyMs: null,
    queryEvidence: [],
    warnings: [],
  },
  expandedCatalog: {
    available: false,
    unavailableReason: 'Artifact not found: datasets/raw/articles.catalog.api-ready-2026-06-05.json',
    catalogId: null,
    generatedAt: null,
    articleCount: null,
    labelStatus: 'draft_only',
    benchmarkStatus: 'blocked_until_reviewed_labels',
    warnings: [],
  },
  limitations: ['Current retrieval and graph metrics are smoke evidence, not broad quality claims.'],
}

beforeEach(() => {
  window.history.pushState({}, '', '/')
  vi.mocked(fetchArticles).mockReset()
  vi.mocked(fetchArticles).mockResolvedValue([])
  vi.mocked(fetchResearchOverview).mockReset()
  vi.mocked(fetchResearchOverview).mockResolvedValue(mockResearchOverview)
})

it('renders the NavBar logo', async () => {
  render(<App />)
  expect(await screen.findByRole('link', { name: 'SIGAK' })).toBeInTheDocument()
})

it('renders a Research navigation link', async () => {
  render(<App />)
  expect(await screen.findByRole('link', { name: 'Research' })).toHaveAttribute('href', '/research')
})

it('renders the research dashboard route', async () => {
  window.history.pushState({}, '', '/research')

  render(<App />)

  expect(await screen.findByRole('heading', { name: 'Research Evidence' })).toBeInTheDocument()
})
