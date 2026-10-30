import { render, screen, waitFor, cleanup, act } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import ArticleSummary from './ArticleSummary'
import { requestArticleSummary, fetchArticleSummary } from '../api/summaries'

vi.mock('../api/summaries', () => ({ requestArticleSummary: vi.fn(), fetchArticleSummary: vi.fn() }))
const article = { id: 6, title: 'News', source: 'OpenAI', url: 'https://openai.com/news', publishedAt: '2026-10-30T00:00:00Z',
  eventType: 'NEWS', primaryCategory: 'AI', topics: [], summary: null, whyItMatters: null, importanceScore: 50,
  relatedArticleIds: [], sourceExcerpt: 'Original feed excerpt.', summaryStatus: 'NOT_REQUESTED' }
const ready = { articleId: 6, status: 'READY' as const, summary: '한국어 실제 요약', whyItMatters: '근거에 기반한 설명',
  evidenceQuotes: ['Original feed excerpt.'], modelName: 'gpt-5.6-luna', inputScope: 'FEED_EXCERPT', truncated: false, errorCode: null }
beforeEach(() => { vi.resetAllMocks() })
afterEach(() => { cleanup(); vi.useRealTimers() })

it('shows excerpt immediately while asynchronously requesting a summary', async () => {
  vi.mocked(requestArticleSummary).mockReturnValue(new Promise(() => {}))
  render(<ArticleSummary article={article} />)
  expect(screen.getByText('Original feed excerpt.')).toBeInTheDocument()
  expect(screen.getByRole('status')).toHaveTextContent('요약 생성 중')
  await waitFor(() => expect(requestArticleSummary).toHaveBeenCalledTimes(1))
})
it('renders the generated summary and evidence', async () => {
  vi.mocked(requestArticleSummary).mockResolvedValue(ready)
  render(<ArticleSummary article={article} />)
  expect(await screen.findByText('한국어 실제 요약')).toBeInTheDocument()
  expect(screen.getByText('근거에 기반한 설명')).toBeInTheDocument()
  expect(requestArticleSummary).toHaveBeenCalledTimes(1)
})
it('reuses an already stored summary without a generation request', () => {
  render(<ArticleSummary article={{ ...article, summary: 'Stored summary', whyItMatters: 'Stored insight', summaryStatus: 'READY' }} />)
  expect(screen.getByText('Stored summary')).toBeInTheDocument()
  expect(requestArticleSummary).not.toHaveBeenCalled()
})
it('shows a budget explanation without retrying on rerender', async () => {
  vi.mocked(requestArticleSummary).mockResolvedValue({ ...ready, status: 'BUDGET_EXHAUSTED', summary: null })
  const view = render(<ArticleSummary article={article} />)
  expect(await screen.findByText(/요약 예산 한도/)).toBeInTheDocument()
  view.rerender(<ArticleSummary article={article} />)
  expect(requestArticleSummary).toHaveBeenCalledTimes(1)
})
it('polls only read status and stops after unmount', async () => {
  vi.mocked(requestArticleSummary).mockResolvedValue({ ...ready, status: 'PENDING', summary: null })
  vi.mocked(fetchArticleSummary).mockResolvedValue(ready)
  vi.useFakeTimers()
  const view = render(<ArticleSummary article={article} />)
  await vi.advanceTimersByTimeAsync(2100)
  expect(fetchArticleSummary).toHaveBeenCalledTimes(1)
  expect(requestArticleSummary).toHaveBeenCalledTimes(1)
  view.unmount()
  await vi.advanceTimersByTimeAsync(10000)
  expect(fetchArticleSummary).toHaveBeenCalledTimes(1)
})
it('renders input scope in plain Korean instead of internal field names', async () => {
  vi.mocked(requestArticleSummary).mockResolvedValue({ ...ready, summary: 'FEED_EXCERPT에 근거한 요약입니다.' })
  render(<ArticleSummary article={article} />)
  expect(await screen.findByText('기사 소개글에 근거한 요약입니다.')).toBeInTheDocument()
})

it('upgrades a legacy excerpt on first visit rather than reusing it as full text', async () => {
  vi.mocked(requestArticleSummary).mockResolvedValue({ ...ready, inputScope: 'FULL_TEXT' })
  render(<ArticleSummary article={{ ...article, summary: 'Legacy excerpt', summaryScope: 'FEED_EXCERPT', summaryProvider: 'gpt-5.6-luna' }} />)
  expect(await screen.findByText('한국어 실제 요약')).toBeInTheDocument()
  expect(requestArticleSummary).toHaveBeenCalledTimes(1)
  expect(screen.queryByText('Legacy excerpt')).not.toBeInTheDocument()
  expect(screen.getByText(/본문 기반/)).toBeInTheDocument()
})
it('explains body fetch and cached unsupported state without repeated requests', async () => {
  vi.mocked(requestArticleSummary).mockResolvedValue({ ...ready, summary: null, status: 'PENDING', bodyStatus: 'FETCHING' })
  vi.mocked(fetchArticleSummary).mockResolvedValue({ ...ready, summary: null, status: 'UNAVAILABLE', errorCode: 'BODY_UNSUPPORTED' })
  vi.useFakeTimers()
  const view = render(<ArticleSummary article={article} />)
  await act(async () => { await vi.advanceTimersByTimeAsync(1) })
  expect(screen.getByRole('status')).toHaveTextContent('본문을 가져오는 중')
  await act(async () => { await vi.advanceTimersByTimeAsync(500) })
  expect(screen.getByRole('status')).toHaveTextContent('본문 요약을 지원하지 않는')
  await vi.advanceTimersByTimeAsync(10000)
  expect(requestArticleSummary).toHaveBeenCalledTimes(1)
  expect(fetchArticleSummary).toHaveBeenCalledTimes(1)
  view.unmount()
})

it('reads metadata for a cached full text summary without posting generation', async () => {
  vi.mocked(fetchArticleSummary).mockResolvedValue({ ...ready, inputScope: 'FULL_TEXT' })
  render(<ArticleSummary article={{ ...article, summary: ready.summary, summaryScope: 'FULL_TEXT', summaryProvider: ready.modelName }} />)
  expect(await screen.findByText(/본문 기반/)).toBeInTheDocument()
  expect(fetchArticleSummary).toHaveBeenCalledTimes(1)
  expect(requestArticleSummary).not.toHaveBeenCalled()
})

it('explains that unavailable source text prevents AI generation without a retry loop', async () => {
  vi.mocked(requestArticleSummary).mockResolvedValue({ ...ready, summary: null, status: 'UNAVAILABLE', errorCode: 'BODY_FETCH_FAILED', bodyStatus: 'FAILED' })
  render(<ArticleSummary article={article} />)
  expect(await screen.findByText(/원문 사이트에서 본문을 받지 못해/)).toBeInTheDocument()
  expect(screen.getByText(/이번 요청으로 AI 요약 비용은 발생하지 않았습니다/)).toBeInTheDocument()
  expect(requestArticleSummary).toHaveBeenCalledTimes(1)
  expect(fetchArticleSummary).not.toHaveBeenCalled()
})
