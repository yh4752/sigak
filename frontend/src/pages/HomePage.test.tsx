import { render, screen, waitFor, act, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import HomePage from './HomePage'
import { fetchArticles, peekArticles } from '../api/articles'

vi.mock('../api/articles', () => ({
  fetchArticles: vi.fn(),
  peekArticles: vi.fn(() => undefined),
  fetchArticle: vi.fn(),
}))

const articles = [
  {
    id: 1,
    title: 'OpenAI Releases Agent Evaluation Toolkit',
    source: 'OpenAI',
    sourceEnabled: true,
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
    sourceEnabled: true,
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
  vi.mocked(peekArticles).mockReturnValue(undefined)
  vi.mocked(fetchArticles).mockReset()
  vi.mocked(fetchArticles).mockResolvedValue(articles)
})

it('shows the article list after loading', async () => {
  render(<MemoryRouter><HomePage /></MemoryRouter>)
  expect(await screen.findByText('OpenAI Releases Agent Evaluation Toolkit')).toBeInTheDocument()
  expect(screen.getByRole('heading', { name: '최신 글' })).toBeInTheDocument()
})

it('shows only real articles from enabled sources on the home feed', async () => {
  vi.mocked(fetchArticles).mockResolvedValue([
    articles[0],
    { ...articles[1], sourceEnabled: false },
    { ...articles[0], id: 3, title: 'Demo article', summaryProvider: 'curated-seed' },
    { ...articles[0], id: 4, title: 'Unknown source state', sourceEnabled: undefined },
  ])
  render(<MemoryRouter><HomePage /></MemoryRouter>)
  expect(await screen.findByText(articles[0].title)).toBeInTheDocument()
  expect(screen.queryByText(articles[1].title)).not.toBeInTheDocument()
  expect(screen.queryByText('Demo article')).not.toBeInTheDocument()
  expect(screen.queryByText('Unknown source state')).not.toBeInTheDocument()
})

it('shows the empty state when all received articles are hidden from home', async () => {
  vi.mocked(fetchArticles).mockResolvedValue([
    { ...articles[0], sourceEnabled: false },
    { ...articles[1], summaryProvider: 'curated-seed' },
  ])
  render(<MemoryRouter><HomePage /></MemoryRouter>)
  expect(await screen.findByText('No articles available yet.')).toBeInTheDocument()
  expect(screen.queryByRole('heading', { name: '최신 글' })).not.toBeInTheDocument()
})

it('keeps archived real search results in relevance order and excludes demos', async () => {
  vi.mocked(fetchArticles).mockResolvedValueOnce(articles).mockResolvedValueOnce([
    { ...articles[0], sourceEnabled: false },
    { ...articles[0], id: 3, title: 'Demo result', summaryProvider: 'curated-seed' },
    articles[1],
  ])
  const user = userEvent.setup()
  render(<MemoryRouter><HomePage /></MemoryRouter>)
  await screen.findByText(articles[0].title)
  await user.type(screen.getByRole('searchbox'), 'archived')
  await user.keyboard('{Enter}')
  const section = await screen.findByRole('region', { name: 'SEARCH RESULTS FOR "archived"' })
  expect(within(section).getAllByRole('link').map((link) => link.textContent)).toEqual([
    expect.stringContaining(articles[0].title), expect.stringContaining(articles[1].title),
  ])
})

it('shows all articles newest first without score-based sections', async () => {
  render(<MemoryRouter><HomePage /></MemoryRouter>)
  const section = await screen.findByRole('region', { name: '최신 글' })
  expect(within(section).getAllByRole('link').map((link) => link.textContent)).toEqual([
    expect.stringContaining(articles[1].title), expect.stringContaining(articles[0].title),
  ])
  expect(screen.queryByText("TODAY'S IMPORTANT NEWS")).not.toBeInTheDocument()
  expect(screen.queryByText('POPULAR NEWS')).not.toBeInTheDocument()
  expect(articles.map((article) => article.id)).toEqual([1, 2])
})

it('orders by actual publication time and uses article ID for ties', async () => {
  vi.mocked(fetchArticles).mockResolvedValue([
    { ...articles[0], id: 3, title: 'Older instant', publishedAt: '2026-11-02T10:00:00+09:00' },
    { ...articles[0], id: 4, title: 'Latest lower ID', publishedAt: '2026-11-02T02:00:00Z' },
    { ...articles[0], id: 5, title: 'Latest higher ID', publishedAt: '2026-11-02T11:00:00+09:00' },
  ])
  render(<MemoryRouter><HomePage /></MemoryRouter>)
  const section = await screen.findByRole('region', { name: '최신 글' })
  expect(within(section).getAllByRole('link').map((link) => link.textContent)).toEqual([
    expect.stringContaining('Latest higher ID'), expect.stringContaining('Latest lower ID'),
    expect.stringContaining('Older instant'),
  ])
})

it('preserves server relevance order when displaying search results', async () => {
  const user = userEvent.setup()
  render(<MemoryRouter><HomePage /></MemoryRouter>)
  await screen.findByText(articles[0].title)
  await user.type(screen.getByRole('searchbox'), 'example')
  await user.keyboard('{Enter}')
  const section = await screen.findByRole('region', { name: 'SEARCH RESULTS FOR "example"' })
  expect(within(section).getAllByRole('link').map((link) => link.textContent)).toEqual([
    expect.stringContaining(articles[0].title), expect.stringContaining(articles[1].title),
  ])
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
  expect(screen.queryByRole('heading', { name: '최신 글' })).not.toBeInTheDocument()
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

it('shows the previous home feed immediately while a refresh is pending', () => {
  vi.mocked(peekArticles).mockReturnValue(articles)
  vi.mocked(fetchArticles).mockReturnValue(new Promise(() => {}))
  render(<MemoryRouter><HomePage /></MemoryRouter>)
  expect(screen.getByText(articles[0].title)).toBeInTheDocument()
  expect(screen.queryByText('Loading articles...')).not.toBeInTheDocument()
})

it('preserves the previous feed when a background refresh fails', async () => {
  vi.mocked(peekArticles).mockReturnValue(articles)
  vi.mocked(fetchArticles).mockRejectedValue(new Error('offline'))
  render(<MemoryRouter><HomePage /></MemoryRouter>)
  expect(await screen.findByRole('alert')).toHaveTextContent('Showing the previous list')
  expect(screen.getByText(articles[0].title)).toBeInTheDocument()
})
it('ignores an earlier response after the user changes the search query', async () => {
  let resolveFirst!: (value: typeof articles) => void
  vi.mocked(fetchArticles).mockReturnValueOnce(new Promise((resolve) => { resolveFirst=resolve }))
    .mockResolvedValueOnce([{...articles[1],title:'New query result'}])
  const user = userEvent.setup()
  render(<MemoryRouter><HomePage /></MemoryRouter>)
  await user.type(screen.getByRole('searchbox'),'new')
  await user.keyboard('{Enter}')
  expect(await screen.findByText('New query result')).toBeInTheDocument()
  await act(async () => { resolveFirst(articles) })
  expect(screen.getByText('New query result')).toBeInTheDocument()
  expect(screen.queryByText(articles[0].title)).not.toBeInTheDocument()
})
