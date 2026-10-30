import { z } from 'zod'
import { httpClient } from './httpClient'
import { articleReadCache, ARTICLE_FRESH_MS } from './articleReadCache'

const articleSchema = z.object({
  id: z.number(),
  title: z.string(),
  source: z.string(),
  url: z.string(),
  publishedAt: z.string(),
  eventType: z.string(),
  primaryCategory: z.string(),
  topics: z.array(z.string()),
  summary: z.string().nullable(),
  whyItMatters: z.string().nullable(),
  importanceScore: z.number(),
  relatedArticleIds: z.array(z.number()),
  sourceExcerpt: z.string().optional(),
  summaryStatus: z.string().optional(),
  summaryProvider: z.string().nullable().optional(),
  summaryScope: z.string().nullable().optional(),
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

export function peekArticles(query?: string): Article[] | undefined {
  return articleReadCache.lists.read(query?.trim() ?? '')
}

export function peekArticle(id: number): Article | undefined {
  return articleReadCache.details.read(String(id))
}

function shareRead<T>(key: string, load: () => Promise<T>): Promise<T> {
  const existing = articleReadCache.pending.get(key)
  if (existing) return existing as Promise<T>
  const request = load().finally(() => {
    if (articleReadCache.pending.get(key) === request) articleReadCache.pending.delete(key)
  })
  articleReadCache.pending.set(key, request)
  return request
}

export async function fetchArticles(query?: string): Promise<Article[]> {
  const normalized = query?.trim() ?? ''
  const cached = articleReadCache.lists.read(normalized, ARTICLE_FRESH_MS)
  if (cached !== undefined) return cached
  return shareRead(`list:${normalized}`, async () => {
    const response = await httpClient.get('/api/articles', {
      params: normalized ? { query: normalized } : undefined,
    })
    // 목록과 상세의 DTO가 같으므로 제목·소개글은 클릭 즉시 표시한다. AI 호출은 하지 않는다.
    const articles = articleReadCache.rememberArticles(articleListSchema.parse(response.data))
    articleReadCache.lists.write(normalized, articles)
    return articles
  })
}

export async function fetchArticle(id: number): Promise<Article> {
  const cached = articleReadCache.details.read(String(id), ARTICLE_FRESH_MS)
  if (cached !== undefined) return cached
  return shareRead(`article:${id}`, async () => {
    const response = await httpClient.get(`/api/articles/${id}`)
    const article = articleSchema.parse(response.data)
    return articleReadCache.rememberArticle(article)
  })
}

export async function fetchArticleGraphContext(id: number): Promise<ArticleGraphContext> {
  const response = await httpClient.get(`/api/articles/${id}/graph-context`)
  return articleGraphContextSchema.parse(response.data)
}

export async function fetchArticlesByIds(ids: number[]): Promise<Article[]> {
  if (ids.length === 0) {
    return []
  }

  const response = await httpClient.get('/api/articles', {
    params: { ids: ids.join(',') },
  })

  return articleReadCache.rememberArticles(articleListSchema.parse(response.data))
}
