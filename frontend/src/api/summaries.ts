import { z } from 'zod'
import { httpClient } from './httpClient'
import { articleReadCache } from './articleReadCache'

const summarySchema = z.object({
  articleId: z.number(),
  status: z.enum(['NOT_REQUESTED', 'PENDING', 'READY', 'FAILED', 'UNKNOWN', 'BUDGET_EXHAUSTED', 'UNAVAILABLE']),
  summary: z.string().nullable(),
  whyItMatters: z.string().nullable(),
  evidenceQuotes: z.array(z.string()),
  modelName: z.string().nullable(),
  inputScope: z.string().nullable(),
  truncated: z.boolean(),
  errorCode: z.string().nullable(),
  bodyStatus: z.string().nullable().optional(),
})
export type ArticleSummaryResult = z.infer<typeof summarySchema>

function rememberReadySummary(result: ArticleSummaryResult): ArticleSummaryResult {
  if (result.status !== 'READY' || result.inputScope !== 'FULL_TEXT' || !result.summary) return result
  const cached = articleReadCache.details.read(String(result.articleId))
  // 생성 직전의 빈 요약 DTO를 재사용해 재방문 시 다시 기다리는 일을 막는다.
  if (cached) articleReadCache.details.write(String(result.articleId), {
    ...cached, summary: result.summary, whyItMatters: result.whyItMatters,
    summaryScope: result.inputScope, summaryProvider: result.modelName, summaryStatus: 'READY',
  })
  return result
}

export async function requestArticleSummary(id: number): Promise<ArticleSummaryResult> {
  return rememberReadySummary(summarySchema.parse((await httpClient.post(`/api/articles/${id}/summary`)).data))
}

export async function fetchArticleSummary(id: number): Promise<ArticleSummaryResult> {
  return rememberReadySummary(summarySchema.parse((await httpClient.get(`/api/articles/${id}/summary`)).data))
}
