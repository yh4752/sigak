import { useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import './App.css'
import { fetchArticles } from './api/articles'
import type { Article } from './api/articles'

function App() {
  const [articles, setArticles] = useState<Article[]>([])
  const [searchInput, setSearchInput] = useState('')
  const [activeQuery, setActiveQuery] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState('')

  useEffect(() => {
    let isMounted = true

    async function loadArticles() {
      setIsLoading(true)
      setErrorMessage('')

      try {
        const nextArticles = activeQuery ? await fetchArticles(activeQuery) : await fetchArticles()

        if (isMounted) {
          setArticles(nextArticles)
        }
      } catch {
        if (isMounted) {
          setErrorMessage('Unable to load articles right now.')
          setArticles([])
        }
      } finally {
        if (isMounted) {
          setIsLoading(false)
        }
      }
    }

    loadArticles()

    return () => {
      isMounted = false
    }
  }, [activeQuery])

  const popularArticles = useMemo(
    () => [...articles].sort((left, right) => right.importanceScore - left.importanceScore).slice(0, 3),
    [articles],
  )

  function handleSearchSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setActiveQuery(searchInput.trim())
  }

  return (
    <main className="app-shell">
      <section className="search-hero" aria-labelledby="home-title">
        <p className="eyebrow">Technical news insight</p>
        <h1 id="home-title">Sigak</h1>
        <form className="search-form" onSubmit={handleSearchSubmit}>
          <label htmlFor="article-search">Search articles</label>
          <div className="search-control">
            <input
              id="article-search"
              type="search"
              value={searchInput}
              onChange={(event) => setSearchInput(event.target.value)}
              placeholder="Search AI, security, Graph RAG..."
            />
            <button type="submit">Search</button>
          </div>
        </form>
      </section>

      {errorMessage ? <p className="status-message">{errorMessage}</p> : null}
      {isLoading ? <p className="status-message">Loading articles...</p> : null}

      {!isLoading && !errorMessage ? (
        <section className="content-grid" aria-label="Article feeds">
          <ArticleSection title="Today" articles={articles} />
          <ArticleSection title="Popular News" articles={popularArticles} />
        </section>
      ) : null}
    </main>
  )
}

type ArticleSectionProps = {
  title: string
  articles: Article[]
}

function ArticleSection({ title, articles }: ArticleSectionProps) {
  return (
    <section className="article-section" aria-labelledby={`${title.replaceAll(' ', '-').toLowerCase()}-title`}>
      <div className="section-header">
        <h2 id={`${title.replaceAll(' ', '-').toLowerCase()}-title`}>{title}</h2>
        <span>{articles.length}</span>
      </div>
      <div className="article-list">
        {articles.map((article) => (
          <article className="article-card" key={article.id}>
            <div className="article-meta">
              <span>{article.primaryCategory}</span>
              <span>{article.source}</span>
              <span>{article.importanceScore}</span>
            </div>
            <h3>{article.title}</h3>
            <p>{article.summary}</p>
            <div className="topic-row">
              {article.topics.slice(0, 3).map((topic) => (
                <span key={topic}>{topic}</span>
              ))}
            </div>
          </article>
        ))}
      </div>
    </section>
  )
}

export default App
