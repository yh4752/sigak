import { useEffect, useMemo, useState } from 'react'
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

  useEffect(() => {
    let isMounted = true
    async function load() {
      setIsLoading(true)
      setErrorMessage('')
      try {
        const data = await fetchArticles(activeQuery || undefined)
        if (isMounted) setArticles(data)
      } catch {
        if (isMounted) {
          setErrorMessage('Unable to load articles.')
          setArticles([])
        }
      } finally {
        if (isMounted) setIsLoading(false)
      }
    }
    load()
    return () => { isMounted = false }
  }, [activeQuery])

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

      {errorMessage && <p className="home-status">{errorMessage}</p>}
      {isLoading && <p className="home-status">Loading...</p>}

      {!isLoading && !errorMessage && (
        activeQuery ? (
          <SearchResultsSection
            query={activeQuery}
            articles={articles}
            emptyMessage="No results found."
          />
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
}

function ArticleSection({ title, articles, emptyMessage = 'No articles.' }: ArticleSectionProps) {
  const headerId = title.toLowerCase().replace(/[^a-z0-9]+/g, '-')
  return (
    <section className="home-article-section" aria-labelledby={headerId}>
      <h2 id={headerId} className="home-section-header">{title.toUpperCase()}</h2>
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

type SearchResultsSectionProps = {
  query: string
  articles: Article[]
  emptyMessage?: string
}

function SearchResultsSection({ query, articles, emptyMessage = 'No results found.' }: SearchResultsSectionProps) {
  const headerId = `search-results-for-${query.replace(/[^a-z0-9]+/g, '-')}`
  const headerText = `SEARCH RESULTS FOR "${query}"`
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
