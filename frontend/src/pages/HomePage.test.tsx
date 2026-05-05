import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import HomePage from './HomePage'
import { fetchArticles } from '../api/articles'

vi.mock('../api/articles', () => ({
  fetchArticles: vi.fn(),
  fetchArticle: vi.fn(),
}))

const articles = [
  {
    id: 1,
    title: 'OpenAI Releases Agent Evaluation Toolkit',
    source: 'OpenAI',
    url: 'https://example.com/1',
    publishedAt: '2026-05-01T09:00:00Z',
    eventType: 'OFFICIAL_ANNOUNCEMENT',
    primaryCategory: 'AI',
    topics: ['LLM agents', 'evaluation'],
    summary: 'OpenAI introduced a toolkit.',
    whyItMatters: 'Agent evaluation is practical.',
    importanceScore: 88,
    relatedArticleIds: [],
  },
  {
    id: 2,
    title: 'New Research Maps Failure Modes in Graph RAG',
    source: 'arXiv',
    url: 'https://example.com/2',
    publishedAt: '2026-05-04T08:20:00Z',
    eventType: 'RESEARCH',
    primaryCategory: 'CS_RESEARCH',
    topics: ['Graph RAG', 'retrieval'],
    summary: 'Researchers categorized failure modes.',
    whyItMatters: 'Helps design better retrieval.',
    importanceScore: 86,
    relatedArticleIds: [],
  },
]

beforeEach(() => {
  vi.mocked(fetchArticles).mockReset()
  vi.mocked(fetchArticles).mockResolvedValue(articles)
})

it('shows the article list after loading', async () => {
  render(<MemoryRouter><HomePage /></MemoryRouter>)
  expect(await screen.findByText('OpenAI Releases Agent Evaluation Toolkit')).toBeInTheDocument()
  expect(screen.getByText("TODAY'S IMPORTANT NEWS")).toBeInTheDocument()
})

it('shows Popular News section with highest importance articles', async () => {
  render(<MemoryRouter><HomePage /></MemoryRouter>)
  expect(await screen.findByText('POPULAR NEWS')).toBeInTheDocument()
})

it('submits search query to fetchArticles on form submit', async () => {
  const user = userEvent.setup()
  render(<MemoryRouter><HomePage /></MemoryRouter>)
  await screen.findByText('OpenAI Releases Agent Evaluation Toolkit')
  await user.type(screen.getByRole('searchbox'), 'graph rag')
  await user.keyboard('{Enter}')
  await waitFor(() => {
    expect(fetchArticles).toHaveBeenLastCalledWith('graph rag')
  })
})

it('shows search result section header when query is active', async () => {
  const user = userEvent.setup()
  render(<MemoryRouter><HomePage /></MemoryRouter>)
  await screen.findByText('OpenAI Releases Agent Evaluation Toolkit')
  await user.type(screen.getByRole('searchbox'), 'rag')
  await user.keyboard('{Enter}')
  expect(await screen.findByText('SEARCH RESULTS FOR "rag"')).toBeInTheDocument()
})

it('shows no results message when search returns empty', async () => {
  vi.mocked(fetchArticles).mockResolvedValueOnce([]).mockResolvedValueOnce([])
  const user = userEvent.setup()
  render(<MemoryRouter><HomePage /></MemoryRouter>)
  await user.type(await screen.findByRole('searchbox'), 'xyz')
  await user.keyboard('{Enter}')
  expect(await screen.findByText('No results found.')).toBeInTheDocument()
})

it('shows one empty state when the home feed has no articles', async () => {
  vi.mocked(fetchArticles).mockResolvedValueOnce([])

  render(<MemoryRouter><HomePage /></MemoryRouter>)

  expect(await screen.findByText('No articles available yet.')).toBeInTheDocument()
  expect(screen.queryByText("TODAY'S IMPORTANT NEWS")).not.toBeInTheDocument()
  expect(screen.queryByText('POPULAR NEWS')).not.toBeInTheDocument()
})

it('lets the user retry when the home feed fails to load', async () => {
  vi.mocked(fetchArticles)
    .mockRejectedValueOnce(new Error('Network error'))
    .mockResolvedValueOnce(articles)

  const user = userEvent.setup()
  render(<MemoryRouter><HomePage /></MemoryRouter>)

  expect(await screen.findByRole('alert')).toHaveTextContent('Unable to load articles.')
  await user.click(screen.getByRole('button', { name: 'Retry' }))

  expect(await screen.findByText('OpenAI Releases Agent Evaluation Toolkit')).toBeInTheDocument()
})
