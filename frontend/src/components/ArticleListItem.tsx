import { Link } from 'react-router-dom'
import type { Article } from '../api/articles'
import './ArticleListItem.css'

type Props = { article: Article }

export default function ArticleListItem({ article }: Props) {
  const date = new Date(article.publishedAt).toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
  })

  return (
    <Link to={`/articles/${article.id}`} className="article-list-item">
      <div className="article-list-item__main">
        <span className="article-list-item__title">{article.title}</span>
        <span className="article-list-item__tag">{article.primaryCategory}</span>
      </div>
      <span className="article-list-item__meta">{article.source} · {date}</span>
    </Link>
  )
}
