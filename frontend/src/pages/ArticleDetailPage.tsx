import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { fetchArticle, fetchArticlesByIds } from '../api/articles'
import type { Article } from '../api/articles'
import './ArticleDetailPage.css'

export default function ArticleDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [article, setArticle] = useState<Article | null>(null)
  const [relatedArticles, setRelatedArticles] = useState<{ articleId: number, articles: Article[] } | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState('')

  useEffect(() => {
    if (!id) return
    let isMounted = true

    fetchArticle(Number(id))
      .then((fetchedArticle) => {
        if (isMounted) {
          setArticle(fetchedArticle)
          setIsLoading(false)
          setErrorMessage('')
        }
      })
      .catch(() => {
        if (isMounted) {
          setArticle(null)
          setErrorMessage('Article not found.')
          setIsLoading(false)
        }
      })
    return () => {
      isMounted = false
    }
  }, [id])

  useEffect(() => {
    if (!article) return
    if (article.relatedArticleIds.length === 0) {
      return
    }

    let isCurrent = true
    fetchArticlesByIds(article.relatedArticleIds)
      .then((articles) => {
        if (isCurrent) {
          setRelatedArticles({ articleId: article.id, articles })
        }
      })
      .catch(() => {
        // 관련 기사는 보조 정보이므로 실패해도 상세 본문 화면은 유지한다.
        if (isCurrent) {
          setRelatedArticles({ articleId: article.id, articles: [] })
        }
      })
    return () => {
      isCurrent = false
    }
  }, [article])

  const routeArticleId = id ? Number(id) : null
  const isRouteArticleLoaded = article && article.id === routeArticleId
  const visibleRelatedArticles =
    relatedArticles && article && relatedArticles.articleId === article.id ? relatedArticles.articles : []

  if (errorMessage) {
    return (
      <main className="detail-page">
        <div className="detail-state" role="alert">
          <p className="detail-status">{errorMessage}</p>
          <Link to="/" className="detail-state__link">Back to home</Link>
        </div>
      </main>
    )
  }
  if (isLoading || !isRouteArticleLoaded) {
    return <main className="detail-page"><p className="detail-status" role="status">Loading article...</p></main>
  }
  if (!article) {
    return (
      <main className="detail-page">
        <div className="detail-state" role="alert">
          <p className="detail-status">Article not found.</p>
          <Link to="/" className="detail-state__link">Back to home</Link>
        </div>
      </main>
    )
  }

  const date = new Date(article.publishedAt).toLocaleDateString('en-US', {
    month: 'long', day: 'numeric', year: 'numeric',
  })

  return (
    <main className="detail-page">
      <article className="detail-article">
        <div className="detail-tags">
          <span className="detail-tag detail-tag--category">{article.primaryCategory}</span>
          <span className="detail-tag detail-tag--event">{article.eventType.replace(/_/g, ' ')}</span>
        </div>

        <h1 className="detail-title">{article.title}</h1>

        <div className="detail-byline">
          <span>{article.source}</span>
          <span className="detail-byline__dot">·</span>
          <span>{date}</span>
          <span className="detail-byline__dot">·</span>
          <a href={article.url} target="_blank" rel="noopener noreferrer" className="detail-byline__link">
            Read original ↗
          </a>
        </div>

        <hr className="detail-divider" />

        <section className="detail-section">
          <p className="detail-section__label">SUMMARY</p>
          <p className="detail-section__text">{article.summary}</p>
        </section>

        <section className="detail-section">
          <p className="detail-section__label">WHY IT MATTERS</p>
          <div className="detail-why-box">
            <p className="detail-section__text">{article.whyItMatters}</p>
          </div>
        </section>

        <hr className="detail-divider" />

        <section className="detail-section">
          <p className="detail-section__label">TOPICS</p>
          <div className="detail-topics">
            {article.topics.map((topic) => (
              <span key={topic} className="detail-topic-chip">{topic}</span>
            ))}
          </div>
        </section>

        {visibleRelatedArticles.length > 0 && (
          <>
            <hr className="detail-divider" />
            <section className="detail-section">
              <p className="detail-section__label">RELATED ARTICLES</p>
              <ul className="detail-related">
                {visibleRelatedArticles.map((related) => (
                  <li key={related.id} className="detail-related__item">
                    <Link to={`/articles/${related.id}`} className="detail-related__title">
                      {related.title}
                    </Link>
                    <span className="detail-related__tag">{related.primaryCategory}</span>
                  </li>
                ))}
              </ul>
            </section>
          </>
        )}
      </article>
    </main>
  )
}
