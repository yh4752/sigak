import { useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { fetchArticles, peekArticles } from '../api/articles'
import type { Article } from '../api/articles'
import ArticleListItem from '../components/ArticleListItem'
import './HomePage.css'

const EMPTY_ARTICLES: Article[] = []

export default function HomePage() {
  const [searchInput, setSearchInput] = useState('')
  const [activeQuery, setActiveQuery] = useState('')
  const [retryVersion, setRetryVersion] = useState(0)
  const [feed, setFeed] = useState<{ query: string, articles: Article[] | null, error: string }>(
    () => ({ query: '', articles: peekArticles() ?? null, error: '' }),
  )
  const visibleFeed = feed.query === activeQuery ? feed : {
    query: activeQuery, articles: peekArticles(activeQuery) ?? null, error: '',
  }
  const articles = visibleFeed.articles ?? EMPTY_ARTICLES
  const errorMessage = visibleFeed.error
  const isLoading = visibleFeed.articles === null && !errorMessage

  useEffect(() => {
    let current = true
    const cached = peekArticles(activeQuery)
    fetchArticles(activeQuery || undefined)
      .then((articles) => {
        if (current) setFeed({ query: activeQuery, articles, error: '' })
      })
      .catch(() => {
        if (current) setFeed({ query: activeQuery, articles: cached ?? null,
          error: cached ? 'Unable to refresh articles. Showing the previous list.' : 'Unable to load articles.' })
      })
    // 이전 검색이나 떠난 화면의 느린 응답으로 현재 목록을 덮어쓰지 않는다.
    return () => { current = false }
  }, [activeQuery, retryVersion])

  function retry() {
    setFeed((previous) => ({ ...previous, error: '' }))
    setRetryVersion((version) => version + 1)
  }

  const realArticles = useMemo(
    () => articles.filter((article) => article.summaryProvider !== 'curated-seed'),
    [articles],
  )
  // 수집 중지된 글은 검색으로 다시 읽을 수 있게 보관하고, 홈에는 활성 출처만 표시한다.
  // filter의 복사본을 정렬해 공유 캐시와 검색 관련도 순서를 보존한다.
  const latestArticles = useMemo(
    () => realArticles.filter((article) => article.sourceEnabled === true)
      .sort((a, b) => Date.parse(b.publishedAt) - Date.parse(a.publishedAt) || b.id - a.id),
    [realArticles],
  )

  function handleSearchSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setActiveQuery(searchInput.trim())
  }

  return (
    <main className="home-page">
      <div className="home-search">
        <form onSubmit={handleSearchSubmit}>
          <input
            type="search"
            className="home-search__input"
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            placeholder="Search AI, security, engineering..."
            aria-label="Search articles"
          />
        </form>
      </div>

      {errorMessage && (
        <div className="home-state" role="alert">
          <p className="home-status">{errorMessage}</p>
          <button type="button" className="home-state__button" onClick={retry}>
            Retry
          </button>
        </div>
      )}
      {isLoading && <p className="home-status" role="status">Loading articles...</p>}

      {visibleFeed.articles !== null && (
        activeQuery ? (
          <ArticleSection
            id="article-search-results"
            title={`SEARCH RESULTS FOR "${activeQuery}"`}
            articles={realArticles}
            emptyMessage="No results found."
            preserveTitleCase
          />
        ) : latestArticles.length === 0 ? (
          <p className="home-empty home-empty--page">No articles available yet.</p>
        ) : (
          <ArticleSection id="latest-articles" title="최신 글" articles={latestArticles} />
        )
      )}
    </main>
  )
}

type ArticleSectionProps = {
  id: string
  title: string
  articles: Article[]
  emptyMessage?: string
  preserveTitleCase?: boolean
}

function ArticleSection({ id, title, articles, emptyMessage = 'No articles.', preserveTitleCase = false }: ArticleSectionProps) {
  const headerText = preserveTitleCase ? title : title.toUpperCase()
  return (
    <section className="home-article-section" aria-labelledby={id}>
      <h2 id={id} className="home-section-header">{headerText}</h2>
      {articles.length === 0 ? (
        <p className="home-empty">{emptyMessage}</p>
      ) : (
        <ul className="home-article-list">
          {articles.map((article) => (
            <li key={article.id}>
              <ArticleListItem article={article} />
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
