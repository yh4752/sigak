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

export type Article = z.infer<typeof articleSchema>

export async function fetchArticles(query?: string): Promise<Article[]> {
  const trimmedQuery = query?.trim()
  const response = await httpClient.get('/api/articles', {
    params: trimmedQuery ? { query: trimmedQuery } : undefined,
  })

  return articleListSchema.parse(response.data)
}

export async function fetchArticle(id: number): Promise<Article> {
  const response = await httpClient.get(`/api/articles/${id}`)
  return articleSchema.parse(response.data)
}
