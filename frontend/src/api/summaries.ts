import { z } from 'zod'
import { httpClient } from './httpClient'

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

export async function requestArticleSummary(id: number): Promise<ArticleSummaryResult> {
  return summarySchema.parse((await httpClient.post(`/api/articles/${id}/summary`)).data)
}

export async function fetchArticleSummary(id: number): Promise<ArticleSummaryResult> {
  return summarySchema.parse((await httpClient.get(`/api/articles/${id}/summary`)).data)
}
