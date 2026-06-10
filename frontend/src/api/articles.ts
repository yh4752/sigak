import { z } from 'zod'
import { httpClient } from './httpClient'

const articleSchema = z.object({
  id: z.number(),
  title: z.string(),
  source: z.string(),
  url: z.string(),
  publishedAt: z.string(),
  eventType: z.string(),
  primaryCategory: z.string(),
  topics: z.array(z.string()),
  summary: z.string(),
  whyItMatters: z.string(),
  importanceScore: z.number(),
  relatedArticleIds: z.array(z.number()),
})

const articleListSchema = z.array(articleSchema)

const articleGraphRelatedReasonSchema = z.object({
  articleId: z.number(),
  reason: z.string().nullable(),
  sharedTopics: z.array(z.string()),
})

const articleGraphTopicSchema = z.object({
  name: z.string(),
  displayName: z.string(),
  relatedArticleIds: z.array(z.number()),
})

const articleGraphContextSchema = z.object({
  articleId: z.number(),
  relatedArticleReasons: z.array(articleGraphRelatedReasonSchema),
  topics: z.array(articleGraphTopicSchema),
})

export type Article = z.infer<typeof articleSchema>
export type ArticleGraphContext = z.infer<typeof articleGraphContextSchema>
export type ArticleGraphRelatedReason = z.infer<typeof articleGraphRelatedReasonSchema>

// API 응답 형태가 바뀌면 화면 전체가 깨질 수 있어 client 경계에서 Zod로 먼저 검증한다.
async function requestArticleList(params?: Record<string, string>): Promise<Article[]> {
  const response = await httpClient.get('/api/articles', { params })
  return articleListSchema.parse(response.data)
}

export async function fetchArticles(query?: string): Promise<Article[]> {
  const trimmedQuery = query?.trim()
  return requestArticleList(trimmedQuery ? { query: trimmedQuery } : undefined)
}

export async function fetchArticle(id: number): Promise<Article> {
  const response = await httpClient.get(`/api/articles/${id}`)
  return articleSchema.parse(response.data)
}

export async function fetchArticleGraphContext(id: number): Promise<ArticleGraphContext> {
  const response = await httpClient.get(`/api/articles/${id}/graph-context`)
  return articleGraphContextSchema.parse(response.data)
}

export async function fetchArticlesByIds(ids: number[]): Promise<Article[]> {
  if (ids.length === 0) {
    return []
  }

  return requestArticleList({ ids: ids.join(',') })
}
