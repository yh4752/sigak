import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchArticles, fetchArticle } from './articles'
import { httpClient } from './httpClient'

vi.mock('./httpClient', () => ({
  httpClient: {
    get: vi.fn(),
  },
}))

const mockArticle = {
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
}

describe('fetchArticles', () => {
  beforeEach(() => {
    vi.mocked(httpClient.get).mockReset()
  })

  it('requests articles through the axios client and validates the response', async () => {
    vi.mocked(httpClient.get).mockResolvedValue({ data: [mockArticle] })

    const articles = await fetchArticles('rag')

    expect(httpClient.get).toHaveBeenCalledWith('/api/articles', {
      params: { query: 'rag' },
    })
    expect(articles).toEqual([mockArticle])
  })

  it('omits the query parameter when the query is blank', async () => {
    vi.mocked(httpClient.get).mockResolvedValue({ data: [mockArticle] })

    await fetchArticles('   ')

    expect(httpClient.get).toHaveBeenCalledWith('/api/articles', {
      params: undefined,
    })
  })

  it('rejects invalid article responses before they reach the UI', async () => {
    vi.mocked(httpClient.get).mockResolvedValue({
      data: [{ ...mockArticle, importanceScore: 'high' }],
    })

    await expect(fetchArticles()).rejects.toThrow()
  })
})

describe('fetchArticle', () => {
  beforeEach(() => {
    vi.mocked(httpClient.get).mockReset()
  })

  it('fetches a single article by id and validates the response', async () => {
    vi.mocked(httpClient.get).mockResolvedValue({ data: mockArticle })

    const article = await fetchArticle(1)

    expect(httpClient.get).toHaveBeenCalledWith('/api/articles/1')
    expect(article).toEqual(mockArticle)
  })

  it('rejects invalid single article responses before they reach the UI', async () => {
    vi.mocked(httpClient.get).mockResolvedValue({
      data: { ...mockArticle, id: 'not-a-number' },
    })

    await expect(fetchArticle(1)).rejects.toThrow()
  })
})
