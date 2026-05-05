import { render, screen } from '@testing-library/react'
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
