import { beforeEach, describe, expect, it, vi } from 'vitest'
import { peekArticle, peekArticles, fetchArticles, fetchArticle, fetchArticleGraphContext, fetchArticlesByIds } from './articles'
import { articleReadCache } from './articleReadCache'
import { httpClient } from './httpClient'

vi.mock('./httpClient', () => ({
  httpClient: {
    get: vi.fn(),
  },
}))

beforeEach(() => { articleReadCache.clear(); vi.useRealTimers() })

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

const mockGraphContext = {
  articleId: 4,
  relatedArticleReasons: [
    {
      articleId: 1,
      reason: 'Graph RAG evaluation connects to agent and retrieval evaluation.',
      sharedTopics: ['evaluation'],
    },
  ],
  topics: [
    {
      name: 'graph rag',
      displayName: 'Graph RAG',
      relatedArticleIds: [1],
    },
  ],
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

describe('fetchArticlesByIds', () => {
  beforeEach(() => {
    vi.mocked(httpClient.get).mockReset()
  })

  it('requests a bulk article lookup with comma-separated ids and validates the response', async () => {
    vi.mocked(httpClient.get).mockResolvedValue({ data: [mockArticle] })

    const articles = await fetchArticlesByIds([4, 1, 4])

    expect(httpClient.get).toHaveBeenCalledWith('/api/articles', {
      params: { ids: '4,1,4' },
    })
    expect(articles).toEqual([mockArticle])
  })

  it('returns an empty list without an HTTP request when ids are empty', async () => {
    const articles = await fetchArticlesByIds([])

    expect(httpClient.get).not.toHaveBeenCalled()
    expect(articles).toEqual([])
  })
})

describe('fetchArticleGraphContext', () => {
  beforeEach(() => {
    vi.mocked(httpClient.get).mockReset()
  })

  it('fetches public graph context for an article and validates the response', async () => {
    vi.mocked(httpClient.get).mockResolvedValue({ data: mockGraphContext })

    const graphContext = await fetchArticleGraphContext(4)

    expect(httpClient.get).toHaveBeenCalledWith('/api/articles/4/graph-context')
    expect(graphContext).toEqual(mockGraphContext)
  })

  it('rejects invalid graph context responses before they reach the UI', async () => {
    vi.mocked(httpClient.get).mockResolvedValue({
      data: { ...mockGraphContext, relatedArticleReasons: [{ articleId: '1' }] },
    })

    await expect(fetchArticleGraphContext(4)).rejects.toThrow()
  })
})

describe('article navigation cache', () => {
  beforeEach(() => { vi.mocked(httpClient.get).mockReset() })
  it('shares pending GETs and reuses fresh list and detail data without extra HTTP', async () => {
    let resolve!: (value: { data: typeof mockArticle[] }) => void
    vi.mocked(httpClient.get).mockReturnValue(new Promise((done) => { resolve = done }))
    const first = fetchArticles()
    const second = fetchArticles()
    expect(httpClient.get).toHaveBeenCalledTimes(1)
    resolve({data:[mockArticle]})
    expect(await first).toEqual(await second)
    expect(peekArticles()).toEqual([mockArticle])
    expect(peekArticle(1)).toEqual(mockArticle)
    await fetchArticle(1)
    await fetchArticles()
    expect(httpClient.get).toHaveBeenCalledTimes(1)
  })
  it('shows stale data while refreshing after one minute and drops snapshots after five', async () => {
    vi.useFakeTimers()
    vi.mocked(httpClient.get).mockResolvedValue({data:[mockArticle]})
    await fetchArticles('old')
    vi.advanceTimersByTime(61000)
    expect(peekArticles('old')).toEqual([mockArticle])
    vi.mocked(httpClient.get).mockResolvedValue({data:[{...mockArticle,title:'New title'}]})
    await fetchArticles('old')
    expect(peekArticles('old')?.[0].title).toBe('New title')
    expect(httpClient.get).toHaveBeenCalledTimes(2)
    vi.advanceTimersByTime(300001)
    expect(peekArticles('old')).toBeUndefined()
    expect(peekArticle(1)).toBeUndefined()
    vi.useRealTimers()
  })
  it('keeps queries separate and never caches invalid or failed responses', async () => {
    vi.mocked(httpClient.get).mockResolvedValueOnce({data:[{...mockArticle,id:'bad'}]})
    await expect(fetchArticles('broken')).rejects.toThrow()
    expect(peekArticles('broken')).toBeUndefined()
    vi.mocked(httpClient.get).mockResolvedValueOnce({data:[mockArticle]})
    await fetchArticles('  broken  ')
    expect(peekArticles('broken')).toEqual([mockArticle])
    expect(peekArticles('different')).toBeUndefined()
    vi.mocked(httpClient.get).mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce({data:{...mockArticle,id:2}})
    await expect(fetchArticle(2)).rejects.toThrow()
    expect(peekArticle(2)).toBeUndefined()
    await fetchArticle(2)
    expect(httpClient.get).toHaveBeenCalledTimes(4)
  })
  it('bounds cached queries and individual articles', async () => {
    vi.mocked(httpClient.get).mockImplementation(async (url) => {
      if (url === '/api/articles') return {data:[mockArticle]}
      return {data:{...mockArticle,id:Number(String(url).split('/').at(-1))}}
    })
    for(let i=0;i<13;i++) await fetchArticles(`query${i}`)
    expect(peekArticles('query0')).toBeUndefined()
    for(let i=2;i<259;i++) await fetchArticle(i)
    expect(peekArticle(2)).toBeUndefined()
    expect(peekArticle(258)?.id).toBe(258)
  })
})
