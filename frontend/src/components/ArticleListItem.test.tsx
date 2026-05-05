import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { expect, it } from 'vitest'
import ArticleListItem from './ArticleListItem'

const article = {
  id: 1,
  title: 'OpenAI Releases GPT-5',
  source: 'OpenAI',
  url: 'https://example.com/gpt5',
  publishedAt: '2026-05-05T09:00:00Z',
  eventType: 'OFFICIAL_ANNOUNCEMENT',
  primaryCategory: 'AI',
  topics: ['LLM', 'tool use'],
  summary: 'OpenAI released GPT-5.',
  whyItMatters: 'Major step in AI capabilities.',
  importanceScore: 95,
  relatedArticleIds: [2],
}

it('renders the article title as a link to the detail page', () => {
  render(<MemoryRouter><ArticleListItem article={article} /></MemoryRouter>)
  const link = screen.getByRole('link')
  expect(link).toHaveAttribute('href', '/articles/1')
  expect(screen.getByText('OpenAI Releases GPT-5')).toBeInTheDocument()
})

it('renders the category tag', () => {
  render(<MemoryRouter><ArticleListItem article={article} /></MemoryRouter>)
  expect(screen.getByText('AI')).toBeInTheDocument()
})

it('renders the source and formatted date', () => {
  render(<MemoryRouter><ArticleListItem article={article} /></MemoryRouter>)
  expect(screen.getByText(/OpenAI · May 5/)).toBeInTheDocument()
})
