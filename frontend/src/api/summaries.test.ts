import { beforeEach, expect, it, vi } from 'vitest'
import { articleReadCache } from './articleReadCache'
import { fetchArticle, peekArticle } from './articles'
import { requestArticleSummary, fetchArticleSummary } from './summaries'
import { httpClient } from './httpClient'

vi.mock('./httpClient', () => ({ httpClient: { get: vi.fn(), post: vi.fn() } }))
const article = { id: 26, title: 'Cached article', source: 'Google', url: 'https://blog.google/example',
  publishedAt: '2026-10-30T00:00:00Z', eventType: 'NEWS', primaryCategory: 'AI', topics: [],
  summary: null, whyItMatters: null, importanceScore: 50, relatedArticleIds: [] }
const ready = { articleId: 26, status: 'READY', summary: '본문 요약', whyItMatters: '중요한 이유',
  modelName: 'gpt-5.6-luna', inputScope: 'FULL_TEXT', evidenceQuotes: ['Evidence'], truncated: false, errorCode: null }
beforeEach(() => { articleReadCache.clear(); vi.resetAllMocks(); articleReadCache.details.write('26',article) })

it('reopens a newly generated summary immediately without refetching an old empty article DTO', async () => {
  vi.mocked(httpClient.post).mockResolvedValue({data:ready})
  await requestArticleSummary(26)
  const reopened = await fetchArticle(26)
  expect(reopened.summary).toBe('본문 요약')
  expect(reopened.summaryScope).toBe('FULL_TEXT')
  expect(httpClient.get).not.toHaveBeenCalled()
})
it('read-only ready status also updates the cached reader but pending never replaces it', async () => {
  vi.mocked(httpClient.get).mockResolvedValueOnce({data:ready})
    .mockResolvedValueOnce({data:{...ready,status:'PENDING',summary:null}})
  await fetchArticleSummary(26)
  await fetchArticleSummary(26)
  expect(peekArticle(26)?.summary).toBe('본문 요약')
  expect(httpClient.post).not.toHaveBeenCalled()
})

it('keeps a completed summary when an older article GET finishes later', async () => {
  vi.useFakeTimers()
  vi.advanceTimersByTime(61000)
  let resolveGet!: (value: {data: typeof article}) => void
  vi.mocked(httpClient.get).mockReturnValue(new Promise((resolve) => { resolveGet=resolve }))
  const oldRead = fetchArticle(26)
  vi.mocked(httpClient.post).mockResolvedValue({data:ready})
  await requestArticleSummary(26)
  resolveGet({data:article})
  const result = await oldRead
  expect(peekArticle(26)?.summary).toBe('본문 요약')
  expect(result.summary).toBe('본문 요약')
  vi.useRealTimers()
})

it('preserves a completed summary when a large feed evicts individual cache entries', async () => {
  vi.mocked(httpClient.post).mockResolvedValue({data:ready})
  await requestArticleSummary(26)
  const largeFeed = [article,...Array.from({length:300},(_,index)=>({...article,id:100+index}))]
  vi.mocked(httpClient.get).mockResolvedValue({data:largeFeed})
  const {fetchArticles} = await import('./articles')
  const result = await fetchArticles()
  expect(result[0].summary).toBe('본문 요약')
  expect(peekArticle(26)?.summary).toBe('본문 요약')
})
