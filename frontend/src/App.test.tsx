import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, it, vi } from 'vitest'
import App from './App'
import { fetchArticles } from './api/articles'

vi.mock('./api/articles', () => ({
  fetchArticles: vi.fn(),
}))

const articles = [
  {
    id: 1,
    title: 'OpenAI Releases Agent Evaluation Toolkit',
    source: 'OpenAI',
    url: 'https://example.com/articles/openai-agent-evals',
    publishedAt: '2026-05-01T09:00:00Z',
    eventType: 'OFFICIAL_ANNOUNCEMENT',
    primaryCategory: 'AI',
    topics: ['LLM agents', 'evaluation', 'production AI'],
    summary: 'OpenAI introduced a toolkit for evaluating agent behavior in multi-step workflows.',
    whyItMatters: 'Agent evaluation is becoming a practical requirement as teams move from demos to production workflows.',
    importanceScore: 88,
    relatedArticleIds: [3, 5],
  },
  {
    id: 4,
    title: 'New Research Maps Failure Modes in Graph RAG Systems',
    source: 'arXiv',
    url: 'https://example.com/articles/graph-rag-failure-modes',
    publishedAt: '2026-05-04T08:20:00Z',
    eventType: 'RESEARCH',
    primaryCategory: 'CS_RESEARCH',
    topics: ['Graph RAG', 'knowledge graphs', 'retrieval quality'],
    summary: 'Researchers categorized common failure modes in Graph RAG systems and proposed evaluation criteria.',
    whyItMatters: 'Understanding graph retrieval failures helps teams design relationship-aware insight features with better evidence quality.',
    importanceScore: 86,
    relatedArticleIds: [1, 5],
  },
]

beforeEach(() => {
  vi.mocked(fetchArticles).mockReset()
  vi.mocked(fetchArticles).mockResolvedValue(articles)
})

it('loads and renders the initial article feed', async () => {
  render(<App />)

  expect(await screen.findByRole('heading', { name: 'Sigak' })).toBeInTheDocument()
  expect(await screen.findAllByText('OpenAI Releases Agent Evaluation Toolkit')).toHaveLength(2)
  expect(screen.getByText('Today')).toBeInTheDocument()
  expect(screen.getByText('Popular News')).toBeInTheDocument()
  expect(fetchArticles).toHaveBeenCalledWith()
})

it('searches articles through the API client when the form is submitted', async () => {
  const user = userEvent.setup()

  render(<App />)

  await screen.findAllByText('OpenAI Releases Agent Evaluation Toolkit')
  await user.type(screen.getByLabelText('Search articles'), 'graph rag')
  await user.click(screen.getByRole('button', { name: 'Search' }))

  await waitFor(() => {
    expect(fetchArticles).toHaveBeenLastCalledWith('graph rag')
  })
})
