import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import ArticleDetailPage from './ArticleDetailPage'
import { fetchArticle } from '../api/articles'

vi.mock('../api/articles', () => ({
  fetchArticles: vi.fn(),
  fetchArticle: vi.fn(),
}))

const article = {
  id: 1,
  title: 'OpenAI Releases GPT-5 with Extended Reasoning',
  source: 'OpenAI',
  url: 'https://openai.com/gpt5',
  publishedAt: '2026-05-05T09:00:00Z',
  eventType: 'OFFICIAL_ANNOUNCEMENT',
  primaryCategory: 'AI',
  topics: ['GPT-5', 'LLM', 'tool use'],
  summary: 'OpenAI released GPT-5.',
  whyItMatters: 'This marks a shift toward AI agents.',
  importanceScore: 95,
  relatedArticleIds: [],
}

function renderDetailPage(id = '1') {
  return render(
    <MemoryRouter initialEntries={[`/articles/${id}`]}>
      <Routes>
        <Route path="/articles/:id" element={<ArticleDetailPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.mocked(fetchArticle).mockReset()
  vi.mocked(fetchArticle).mockResolvedValue(article)
})

it('renders the article title after loading', async () => {
  renderDetailPage()
  expect(await screen.findByRole('heading', { name: 'OpenAI Releases GPT-5 with Extended Reasoning' })).toBeInTheDocument()
})

it('shows a specific loading message while article detail loads', () => {
  vi.mocked(fetchArticle).mockReturnValue(new Promise(() => {}))

  renderDetailPage()

  expect(screen.getByRole('status')).toHaveTextContent('Loading article...')
})

it('renders the category and event type tags', async () => {
  renderDetailPage()
  expect(await screen.findByText('AI')).toBeInTheDocument()
  expect(screen.getByText('OFFICIAL ANNOUNCEMENT')).toBeInTheDocument()
})

it('renders summary and why it matters sections', async () => {
  renderDetailPage()
  await screen.findByText('OpenAI Releases GPT-5 with Extended Reasoning')
  expect(screen.getByText('SUMMARY')).toBeInTheDocument()
  expect(screen.getByText('OpenAI released GPT-5.')).toBeInTheDocument()
  expect(screen.getByText('WHY IT MATTERS')).toBeInTheDocument()
  expect(screen.getByText('This marks a shift toward AI agents.')).toBeInTheDocument()
})

it('renders topic chips', async () => {
  renderDetailPage()
  await screen.findByText('OpenAI Releases GPT-5 with Extended Reasoning')
  expect(screen.getByText('GPT-5')).toBeInTheDocument()
  expect(screen.getByText('LLM')).toBeInTheDocument()
})

it('renders original article link', async () => {
  renderDetailPage()
  const link = await screen.findByRole('link', { name: /Read original/i })
  expect(link).toHaveAttribute('href', 'https://openai.com/gpt5')
})

it('shows error message when article is not found', async () => {
  vi.mocked(fetchArticle).mockRejectedValue(new Error('Not found'))
  renderDetailPage('999')
  expect(await screen.findByRole('alert')).toHaveTextContent('Article not found.')
  expect(screen.getByRole('link', { name: 'Back to home' })).toHaveAttribute('href', '/')
})

it('clears stale related articles when navigating to an article with no related articles', async () => {
  const articleWithRelated = {
    ...article,
    id: 1,
    title: 'Article With Related Item',
    relatedArticleIds: [3],
  }
  const relatedArticle = {
    ...article,
    id: 3,
    title: 'Old Related Article',
    relatedArticleIds: [],
  }
  vi.mocked(fetchArticle).mockImplementation(async (id) => {
    if (id === 1) return articleWithRelated
    if (id === 3) return relatedArticle
    throw new Error('Not found')
  })

  render(
    <MemoryRouter initialEntries={['/articles/1']}>
      <Routes>
        <Route path="/articles/:id" element={<ArticleDetailPage />} />
        <Route path="/go-standalone" element={<ArticleDetailPage />} />
      </Routes>
    </MemoryRouter>,
  )

  expect(await screen.findByRole('heading', { name: 'Article With Related Item' })).toBeInTheDocument()
  expect(await screen.findByRole('link', { name: 'Old Related Article' })).toBeInTheDocument()

  await userEvent.click(screen.getByRole('link', { name: 'Old Related Article' }))

  expect(await screen.findByRole('heading', { name: 'Old Related Article' })).toBeInTheDocument()
  await waitFor(() => {
    expect(screen.queryByText('RELATED ARTICLES')).not.toBeInTheDocument()
  })
})
