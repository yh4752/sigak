import { useCallback, useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { fetchArticles } from '../api/articles'
import type { Article } from '../api/articles'
import ArticleListItem from '../components/ArticleListItem'
import './HomePage.css'

export default function HomePage() {
  const [articles, setArticles] = useState<Article[]>([])
  const [searchInput, setSearchInput] = useState('')
  const [activeQuery, setActiveQuery] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState('')

  const loadArticles = useCallback(async () => {
    setIsLoading(true)
    setErrorMessage('')
    try {
      const fetchedArticles = await fetchArticles(activeQuery || undefined)
      setArticles(fetchedArticles)
    } catch {
      setErrorMessage('Unable to load articles.')
      setArticles([])
    } finally {
      setIsLoading(false)
    }
  }, [activeQuery])

  useEffect(() => {
    // setState를 effect에서 직접 호출하면 react-hooks/set-state-in-effect에 걸리므로 async wrapper를 유지한다.
    const load = async () => {
      await loadArticles()
    }

    void load()
  }, [loadArticles])

  const popularArticles = useMemo(
    () => [...articles].sort((a, b) => b.importanceScore - a.importanceScore).slice(0, 3),
    [articles],
  )

  const popularIds = useMemo(
    () => new Set(popularArticles.map((a) => a.id)),
    [popularArticles],
  )

  const featuredArticles = useMemo(
    () => articles.filter((a) => !popularIds.has(a.id)),
    [articles, popularIds],
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
          <button type="button" className="home-state__button" onClick={loadArticles}>
            Retry
          </button>
        </div>
      )}
      {isLoading && <p className="home-status" role="status">Loading articles...</p>}

      {!isLoading && !errorMessage && (
        activeQuery ? (
          <ArticleSection
            title={`SEARCH RESULTS FOR "${activeQuery}"`}
            articles={articles}
            emptyMessage="No results found."
            preserveTitleCase
          />
        ) : articles.length === 0 ? (
          <p className="home-empty home-empty--page">No articles available yet.</p>
        ) : (
          <>
            <ArticleSection title="Today's Important News" articles={featuredArticles} />
            <ArticleSection title="Popular News" articles={popularArticles} />
          </>
        )
      )}
    </main>
  )
}

type ArticleSectionProps = {
  title: string
  articles: Article[]
  emptyMessage?: string
  preserveTitleCase?: boolean
}

function ArticleSection({ title, articles, emptyMessage = 'No articles.', preserveTitleCase = false }: ArticleSectionProps) {
  const headerId = title.toLowerCase().replace(/[^a-z0-9]+/g, '-')
  const headerText = preserveTitleCase ? title : title.toUpperCase()
  return (
    <section className="home-article-section" aria-labelledby={headerId}>
      <h2 id={headerId} className="home-section-header">{headerText}</h2>
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
