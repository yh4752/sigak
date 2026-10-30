import type { Article } from './articles'

export const ARTICLE_FRESH_MS = 60_000
const MAX_PREVIEW_AGE_MS = 5 * 60_000

type Entry<T> = { value: T, receivedAt: number }

// 탭 안에서만 잠시 보관해 복귀 시 재전송을 기다리지 않고, 오래된 뉴스는 갱신한다.
class ReadCache<T> {
  private entries = new Map<string, Entry<T>>()
  private readonly limit: number
  constructor(limit: number) { this.limit = limit }

  read(key: string, maxAge = MAX_PREVIEW_AGE_MS): T | undefined {
    const entry = this.entries.get(key)
    if (!entry) return undefined
    if (Date.now() - entry.receivedAt > MAX_PREVIEW_AGE_MS) {
      this.entries.delete(key)
      return undefined
    }
    return Date.now() - entry.receivedAt <= maxAge ? entry.value : undefined
  }

  write(key: string, value: T) {
    this.entries.delete(key)
    this.entries.set(key, { value, receivedAt: Date.now() })
    while (this.entries.size > this.limit) this.entries.delete(this.entries.keys().next().value!)
  }

  clear() { this.entries.clear() }
}

export const articleReadCache = {
  lists: new ReadCache<Article[]>(12),
  details: new ReadCache<Article>(256),
  pending: new Map<string, Promise<unknown>>(),
  mergeReadySummary(article: Article): Article {
    const cached = this.details.read(String(article.id))
    // 생성 전 시작한 GET이 늦게 도착해도 완성된 본문 요약을 빈 값으로 되돌리지 않는다.
    const value = cached?.summary && cached.summaryScope === 'FULL_TEXT' &&
      article.summaryScope !== 'FULL_TEXT' && cached.url === article.url ? {
        ...article, summary: cached.summary, whyItMatters: cached.whyItMatters,
        summaryScope: cached.summaryScope, summaryProvider: cached.summaryProvider, summaryStatus: cached.summaryStatus,
      } : article
    return value
  },
  rememberArticle(article: Article): Article {
    const value = this.mergeReadySummary(article)
    this.details.write(String(article.id), value)
    return value
  },
  rememberArticles(articles: Article[]): Article[] {
    // 큰 목록의 캐시 교체가 기존 READY 항목을 밀어내기 전에 결과부터 병합한다.
    const merged = articles.map((article) => this.mergeReadySummary(article))
    for (const article of merged.slice(0, 256).reverse()) this.details.write(String(article.id), article)
    return merged
  },
  clear() { this.lists.clear(); this.details.clear(); this.pending.clear() },
}
