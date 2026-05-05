# Frontend Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 디자인 스펙(`2026-05-05-frontend-design.md`)에 따라 Sigak 프론트엔드를 Mix 1 스타일(세리프 제목 + 인디고 악센트 + 라이트 모드)로 재구현하고, 아티클 디테일 페이지를 신규 추가한다.

**Architecture:** App.tsx를 React Router 기반 라우터로 전환하고, 홈/디테일 화면을 각각 독립 페이지 컴포넌트로 분리한다. NavBar와 ArticleListItem은 공유 컴포넌트로 추출한다. CSS는 CSS 변수 기반 디자인 토큰으로 통일한다.

**Tech Stack:** React 18, TypeScript, Vite, react-router-dom, Vitest, @testing-library/react

---

## 파일 구조

```
frontend/src/
├── index.css                          # 수정 — CSS 변수(디자인 토큰) 추가
├── App.tsx                            # 수정 — BrowserRouter + NavBar + Routes
├── App.css                            # 수정 — 글로벌 리셋만 남김
├── App.test.tsx                       # 수정 — 라우팅 기반 테스트로 교체
├── components/
│   ├── NavBar.tsx                     # 신규 — 로고 + 힌트 텍스트 네비게이션
│   ├── NavBar.css                     # 신규
│   ├── NavBar.test.tsx                # 신규
│   ├── ArticleListItem.tsx            # 신규 — 제목 + 카테고리 태그 + 출처/날짜
│   ├── ArticleListItem.css            # 신규
│   └── ArticleListItem.test.tsx       # 신규
├── pages/
│   ├── HomePage.tsx                   # 신규 — 검색바 + 섹션 + 리스트
│   ├── HomePage.css                   # 신규
│   ├── HomePage.test.tsx              # 신규 (기존 App.test.tsx 내용 이관)
│   ├── ArticleDetailPage.tsx          # 신규 — 제목/바이라인/summary/why/topics/related
│   ├── ArticleDetailPage.css          # 신규
│   └── ArticleDetailPage.test.tsx     # 신규
└── api/
    ├── articles.ts                    # 수정 — fetchArticle(id) 추가
    └── articles.test.ts               # 수정 — fetchArticle 테스트 추가
```

---

## Task 1: CSS 변수(디자인 토큰) 설정

**Files:**
- Modify: `frontend/src/index.css`

- [ ] **Step 1: index.css를 디자인 토큰 기반으로 교체**

```css
/* frontend/src/index.css */
:root {
  --color-base: #ffffff;
  --color-text: #111111;
  --color-text-sub: #374151;
  --color-text-meta: #6b7280;
  --color-text-muted: #9ca3af;
  --color-border: #e5e7eb;
  --color-border-light: #f3f4f6;
  --color-accent: #4f46e5;
  --color-accent-bg: #eef2ff;
  --color-accent-surface: #f8f7ff;
  --color-accent-border: #e0e7ff;
  --font-serif: Georgia, 'Times New Roman', serif;
  --font-sans: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
}

*,
*::before,
*::after {
  box-sizing: border-box;
}

body {
  min-width: 320px;
  min-height: 100vh;
  margin: 0;
  background: var(--color-base);
  color: var(--color-text);
  font-family: var(--font-sans);
  font-synthesis: none;
  text-rendering: optimizeLegibility;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

a {
  color: inherit;
  text-decoration: none;
}

button,
input {
  letter-spacing: 0;
  font: inherit;
}
```

- [ ] **Step 2: 커밋**

```bash
git add frontend/src/index.css
git commit -m "style: add design token CSS variables"
```

---

## Task 2: React Router 설치

**Files:**
- Modify: `frontend/package.json` (자동)

- [ ] **Step 1: react-router-dom 설치**

```bash
cd frontend && npm install react-router-dom
```

- [ ] **Step 2: 설치 확인**

```bash
node -e "require('./node_modules/react-router-dom')" && echo "OK"
```

Expected: `OK`

- [ ] **Step 3: 커밋**

```bash
git add frontend/package.json frontend/package-lock.json
git commit -m "chore: add react-router-dom"
```

---

## Task 3: fetchArticle(id) API 추가

**Files:**
- Modify: `frontend/src/api/articles.ts`
- Modify: `frontend/src/api/articles.test.ts`

- [ ] **Step 1: fetchArticle 테스트 작성**

`frontend/src/api/articles.test.ts`의 기존 `describe('fetchArticles', ...)` 블록 **아래에** 추가:

```typescript
describe('fetchArticle', () => {
  beforeEach(() => {
    vi.mocked(httpClient.get).mockReset()
  })

  it('fetches a single article by id and validates the response', async () => {
    vi.mocked(httpClient.get).mockResolvedValue({ data: mockArticle })

    const article = await fetchArticle(1)

    expect(httpClient.get).toHaveBeenCalledWith('/api/articles/1')
    expect(article).toEqual(mockArticle)
  })

  it('rejects invalid single article responses before they reach the UI', async () => {
    vi.mocked(httpClient.get).mockResolvedValue({
      data: { ...mockArticle, id: 'not-a-number' },
    })

    await expect(fetchArticle(1)).rejects.toThrow()
  })
})
```

import 줄에 `fetchArticle` 추가:
```typescript
import { fetchArticles, fetchArticle } from './articles'
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
cd frontend && npm test -- articles.test.ts
```

Expected: `fetchArticle is not exported` 또는 유사한 에러

- [ ] **Step 3: fetchArticle 구현 추가**

`frontend/src/api/articles.ts` 파일 끝에 추가:

```typescript
export async function fetchArticle(id: number): Promise<Article> {
  const response = await httpClient.get(`/api/articles/${id}`)
  return articleSchema.parse(response.data)
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
cd frontend && npm test -- articles.test.ts
```

Expected: 모든 테스트 PASS

- [ ] **Step 5: 커밋**

```bash
git add frontend/src/api/articles.ts frontend/src/api/articles.test.ts
git commit -m "feat: add fetchArticle API function"
```

---

## Task 4: NavBar 컴포넌트

**Files:**
- Create: `frontend/src/components/NavBar.tsx`
- Create: `frontend/src/components/NavBar.css`
- Create: `frontend/src/components/NavBar.test.tsx`

- [ ] **Step 1: NavBar 테스트 작성**

```typescript
// frontend/src/components/NavBar.test.tsx
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { expect, it } from 'vitest'
import NavBar from './NavBar'

it('renders the SIGAK logo as a link to home', () => {
  render(<MemoryRouter><NavBar /></MemoryRouter>)
  const logo = screen.getByRole('link', { name: 'SIGAK' })
  expect(logo).toBeInTheDocument()
  expect(logo).toHaveAttribute('href', '/')
})

it('renders the category hint text', () => {
  render(<MemoryRouter><NavBar /></MemoryRouter>)
  expect(screen.getByText('AI · Security · Engineering')).toBeInTheDocument()
})
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
cd frontend && npm test -- NavBar.test.tsx
```

Expected: `Cannot find module './NavBar'`

- [ ] **Step 3: NavBar 컴포넌트 구현**

```tsx
// frontend/src/components/NavBar.tsx
import { Link } from 'react-router-dom'
import './NavBar.css'

export default function NavBar() {
  return (
    <nav className="navbar">
      <Link to="/" className="navbar__logo">SIGAK</Link>
      <span className="navbar__hint">AI · Security · Engineering</span>
    </nav>
  )
}
```

```css
/* frontend/src/components/NavBar.css */
.navbar {
  display: flex;
  align-items: center;
  padding: 11px 24px;
  border-bottom: 2px solid var(--color-text);
  background: var(--color-base);
}

.navbar__logo {
  font-family: var(--font-serif);
  font-size: 16px;
  font-weight: 700;
  color: var(--color-text);
  letter-spacing: -0.01em;
}

.navbar__hint {
  margin-left: auto;
  font-size: 11px;
  color: var(--color-text-meta);
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
cd frontend && npm test -- NavBar.test.tsx
```

Expected: 2 tests PASS

- [ ] **Step 5: 커밋**

```bash
git add frontend/src/components/NavBar.tsx frontend/src/components/NavBar.css frontend/src/components/NavBar.test.tsx
git commit -m "feat: add NavBar component"
```

---

## Task 5: ArticleListItem 컴포넌트

**Files:**
- Create: `frontend/src/components/ArticleListItem.tsx`
- Create: `frontend/src/components/ArticleListItem.css`
- Create: `frontend/src/components/ArticleListItem.test.tsx`

- [ ] **Step 1: ArticleListItem 테스트 작성**

```typescript
// frontend/src/components/ArticleListItem.test.tsx
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { expect, it } from 'vitest'
import ArticleListItem from './ArticleListItem'

const article = {
  id: 1,
  title: 'OpenAI Releases GPT-5',
  source: 'OpenAI',
  url: 'https://example.com/gpt5',
  publishedAt: '2026-05-05T09:00:00Z',
  eventType: 'OFFICIAL_ANNOUNCEMENT',
  primaryCategory: 'AI',
  topics: ['LLM', 'tool use'],
  summary: 'OpenAI released GPT-5.',
  whyItMatters: 'Major step in AI capabilities.',
  importanceScore: 95,
  relatedArticleIds: [2],
}

it('renders the article title as a link to the detail page', () => {
  render(<MemoryRouter><ArticleListItem article={article} /></MemoryRouter>)
  const link = screen.getByRole('link')
  expect(link).toHaveAttribute('href', '/articles/1')
  expect(screen.getByText('OpenAI Releases GPT-5')).toBeInTheDocument()
})

it('renders the category tag', () => {
  render(<MemoryRouter><ArticleListItem article={article} /></MemoryRouter>)
  expect(screen.getByText('AI')).toBeInTheDocument()
})

it('renders the source and formatted date', () => {
  render(<MemoryRouter><ArticleListItem article={article} /></MemoryRouter>)
  expect(screen.getByText(/OpenAI/)).toBeInTheDocument()
  expect(screen.getByText(/May 5/)).toBeInTheDocument()
})
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
cd frontend && npm test -- ArticleListItem.test.tsx
```

Expected: `Cannot find module './ArticleListItem'`

- [ ] **Step 3: ArticleListItem 컴포넌트 구현**

```tsx
// frontend/src/components/ArticleListItem.tsx
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
```

```css
/* frontend/src/components/ArticleListItem.css */
.article-list-item {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 14px;
  padding: 10px 0;
  border-bottom: 1px solid var(--color-border-light);
  color: inherit;
}

.article-list-item:last-child {
  border-bottom: none;
}

.article-list-item:hover .article-list-item__title {
  color: var(--color-accent);
}

.article-list-item__main {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.article-list-item__title {
  font-family: var(--font-serif);
  font-size: 14px;
  line-height: 1.5;
  color: var(--color-text);
  font-weight: 400;
  transition: color 0.15s;
}

.article-list-item__tag {
  display: inline-block;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--color-accent);
  background: var(--color-accent-bg);
  padding: 2px 7px;
  border-radius: 3px;
  width: fit-content;
}

.article-list-item__meta {
  font-size: 11px;
  color: var(--color-text-muted);
  white-space: nowrap;
  flex-shrink: 0;
  margin-top: 2px;
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
cd frontend && npm test -- ArticleListItem.test.tsx
```

Expected: 3 tests PASS

- [ ] **Step 5: 커밋**

```bash
git add frontend/src/components/ArticleListItem.tsx frontend/src/components/ArticleListItem.css frontend/src/components/ArticleListItem.test.tsx
git commit -m "feat: add ArticleListItem component"
```

---

## Task 6: HomePage 구현

**Files:**
- Create: `frontend/src/pages/HomePage.tsx`
- Create: `frontend/src/pages/HomePage.css`
- Create: `frontend/src/pages/HomePage.test.tsx`

- [ ] **Step 1: HomePage 테스트 작성**

```typescript
// frontend/src/pages/HomePage.test.tsx
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import HomePage from './HomePage'
import { fetchArticles } from '../api/articles'

vi.mock('../api/articles', () => ({
  fetchArticles: vi.fn(),
  fetchArticle: vi.fn(),
}))

const articles = [
  {
    id: 1,
    title: 'OpenAI Releases Agent Evaluation Toolkit',
    source: 'OpenAI',
    url: 'https://example.com/1',
    publishedAt: '2026-05-01T09:00:00Z',
    eventType: 'OFFICIAL_ANNOUNCEMENT',
    primaryCategory: 'AI',
    topics: ['LLM agents', 'evaluation'],
    summary: 'OpenAI introduced a toolkit.',
    whyItMatters: 'Agent evaluation is practical.',
    importanceScore: 88,
    relatedArticleIds: [],
  },
  {
    id: 2,
    title: 'New Research Maps Failure Modes in Graph RAG',
    source: 'arXiv',
    url: 'https://example.com/2',
    publishedAt: '2026-05-04T08:20:00Z',
    eventType: 'RESEARCH',
    primaryCategory: 'CS_RESEARCH',
    topics: ['Graph RAG', 'retrieval'],
    summary: 'Researchers categorized failure modes.',
    whyItMatters: 'Helps design better retrieval.',
    importanceScore: 86,
    relatedArticleIds: [],
  },
]

beforeEach(() => {
  vi.mocked(fetchArticles).mockReset()
  vi.mocked(fetchArticles).mockResolvedValue(articles)
})

it('shows the article list after loading', async () => {
  render(<MemoryRouter><HomePage /></MemoryRouter>)
  expect(await screen.findByText('OpenAI Releases Agent Evaluation Toolkit')).toBeInTheDocument()
  expect(screen.getByText("TODAY'S IMPORTANT NEWS")).toBeInTheDocument()
})

it('shows Popular News section with highest importance articles', async () => {
  render(<MemoryRouter><HomePage /></MemoryRouter>)
  expect(await screen.findByText('POPULAR NEWS')).toBeInTheDocument()
})

it('submits search query to fetchArticles on form submit', async () => {
  const user = userEvent.setup()
  render(<MemoryRouter><HomePage /></MemoryRouter>)
  await screen.findByText('OpenAI Releases Agent Evaluation Toolkit')
  await user.type(screen.getByRole('searchbox'), 'graph rag')
  await user.keyboard('{Enter}')
  await waitFor(() => {
    expect(fetchArticles).toHaveBeenLastCalledWith('graph rag')
  })
})

it('shows search result section header when query is active', async () => {
  const user = userEvent.setup()
  render(<MemoryRouter><HomePage /></MemoryRouter>)
  await screen.findByText('OpenAI Releases Agent Evaluation Toolkit')
  await user.type(screen.getByRole('searchbox'), 'rag')
  await user.keyboard('{Enter}')
  expect(await screen.findByText('SEARCH RESULTS FOR "rag"')).toBeInTheDocument()
})

it('shows no results message when search returns empty', async () => {
  vi.mocked(fetchArticles).mockResolvedValueOnce([]).mockResolvedValueOnce([])
  const user = userEvent.setup()
  render(<MemoryRouter><HomePage /></MemoryRouter>)
  await user.type(await screen.findByRole('searchbox'), 'xyz')
  await user.keyboard('{Enter}')
  expect(await screen.findByText('No results found.')).toBeInTheDocument()
})
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
cd frontend && npm test -- HomePage.test.tsx
```

Expected: `Cannot find module './HomePage'`

- [ ] **Step 3: HomePage 구현**

```tsx
// frontend/src/pages/HomePage.tsx
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
          <ArticleSection
            title={`Search results for "${activeQuery}"`}
            articles={articles}
            emptyMessage="No results found."
          />
        ) : (
          <>
            <ArticleSection title="Today's Important News" articles={articles} />
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
```

```css
/* frontend/src/pages/HomePage.css */
.home-page {
  width: min(800px, calc(100% - 48px));
  margin: 0 auto;
  padding: 40px 0 64px;
}

.home-search {
  display: flex;
  justify-content: center;
  margin-bottom: 40px;
}

.home-search__input {
  width: 280px;
  padding: 8px 16px;
  border: 1.5px solid var(--color-accent-border);
  border-radius: 20px;
  background: var(--color-accent-surface);
  color: var(--color-text);
  font-size: 13px;
  outline: none;
}

.home-search__input::placeholder {
  color: var(--color-text-muted);
}

.home-search__input:focus {
  border-color: var(--color-accent);
}

.home-status {
  text-align: center;
  color: var(--color-text-meta);
  font-size: 13px;
}

.home-article-section {
  margin-bottom: 40px;
}

.home-section-header {
  margin: 0 0 10px;
  font-family: var(--font-sans);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.1em;
  color: var(--color-text-meta);
  padding-bottom: 8px;
  border-bottom: 1px solid var(--color-border);
}

.home-article-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.home-empty {
  font-size: 13px;
  color: var(--color-text-meta);
  margin: 16px 0;
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
cd frontend && npm test -- HomePage.test.tsx
```

Expected: 5 tests PASS

- [ ] **Step 5: 커밋**

```bash
git add frontend/src/pages/HomePage.tsx frontend/src/pages/HomePage.css frontend/src/pages/HomePage.test.tsx
git commit -m "feat: add HomePage with search and article sections"
```

---

## Task 7: ArticleDetailPage 구현

**Files:**
- Create: `frontend/src/pages/ArticleDetailPage.tsx`
- Create: `frontend/src/pages/ArticleDetailPage.css`
- Create: `frontend/src/pages/ArticleDetailPage.test.tsx`

- [ ] **Step 1: ArticleDetailPage 테스트 작성**

```typescript
// frontend/src/pages/ArticleDetailPage.test.tsx
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import ArticleDetailPage from './ArticleDetailPage'
import { fetchArticle } from '../api/articles'

vi.mock('../api/articles', () => ({
  fetchArticles: vi.fn(),
  fetchArticle: vi.fn(),
}))

const article = {
  id: 1,
  title: 'OpenAI Releases GPT-5 with Extended Reasoning',
  source: 'OpenAI',
  url: 'https://openai.com/gpt5',
  publishedAt: '2026-05-05T09:00:00Z',
  eventType: 'OFFICIAL_ANNOUNCEMENT',
  primaryCategory: 'AI',
  topics: ['GPT-5', 'LLM', 'tool use'],
  summary: 'OpenAI released GPT-5.',
  whyItMatters: 'This marks a shift toward AI agents.',
  importanceScore: 95,
  relatedArticleIds: [],
}

function renderDetailPage(id = '1') {
  return render(
    <MemoryRouter initialEntries={[`/articles/${id}`]}>
      <Routes>
        <Route path="/articles/:id" element={<ArticleDetailPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.mocked(fetchArticle).mockReset()
  vi.mocked(fetchArticle).mockResolvedValue(article)
})

it('renders the article title after loading', async () => {
  renderDetailPage()
  expect(await screen.findByRole('heading', { name: 'OpenAI Releases GPT-5 with Extended Reasoning' })).toBeInTheDocument()
})

it('renders the category and event type tags', async () => {
  renderDetailPage()
  expect(await screen.findByText('AI')).toBeInTheDocument()
  expect(screen.getByText('OFFICIAL ANNOUNCEMENT')).toBeInTheDocument()
})

it('renders summary and why it matters sections', async () => {
  renderDetailPage()
  await screen.findByText('OpenAI Releases GPT-5 with Extended Reasoning')
  expect(screen.getByText('SUMMARY')).toBeInTheDocument()
  expect(screen.getByText('OpenAI released GPT-5.')).toBeInTheDocument()
  expect(screen.getByText('WHY IT MATTERS')).toBeInTheDocument()
  expect(screen.getByText('This marks a shift toward AI agents.')).toBeInTheDocument()
})

it('renders topic chips', async () => {
  renderDetailPage()
  await screen.findByText('OpenAI Releases GPT-5 with Extended Reasoning')
  expect(screen.getByText('GPT-5')).toBeInTheDocument()
  expect(screen.getByText('LLM')).toBeInTheDocument()
})

it('renders original article link', async () => {
  renderDetailPage()
  const link = await screen.findByRole('link', { name: /Read original/i })
  expect(link).toHaveAttribute('href', 'https://openai.com/gpt5')
})

it('shows error message when article is not found', async () => {
  vi.mocked(fetchArticle).mockRejectedValue(new Error('Not found'))
  renderDetailPage('999')
  expect(await screen.findByText('Article not found.')).toBeInTheDocument()
})
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
cd frontend && npm test -- ArticleDetailPage.test.tsx
```

Expected: `Cannot find module './ArticleDetailPage'`

- [ ] **Step 3: ArticleDetailPage 구현**

```tsx
// frontend/src/pages/ArticleDetailPage.tsx
import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { fetchArticle } from '../api/articles'
import type { Article } from '../api/articles'
import './ArticleDetailPage.css'

export default function ArticleDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [article, setArticle] = useState<Article | null>(null)
  const [relatedArticles, setRelatedArticles] = useState<Article[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState('')

  useEffect(() => {
    if (!id) return
    let isMounted = true
    setIsLoading(true)
    setErrorMessage('')
    fetchArticle(Number(id))
      .then((data) => { if (isMounted) setArticle(data) })
      .catch(() => { if (isMounted) setErrorMessage('Article not found.') })
      .finally(() => { if (isMounted) setIsLoading(false) })
    return () => { isMounted = false }
  }, [id])

  useEffect(() => {
    if (!article || article.relatedArticleIds.length === 0) return
    Promise.all(article.relatedArticleIds.map(fetchArticle))
      .then(setRelatedArticles)
      .catch(() => {})
  }, [article])

  if (isLoading) {
    return <main className="detail-page"><p className="detail-status">Loading...</p></main>
  }
  if (errorMessage || !article) {
    return <main className="detail-page"><p className="detail-status">{errorMessage || 'Article not found.'}</p></main>
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

        {relatedArticles.length > 0 && (
          <>
            <hr className="detail-divider" />
            <section className="detail-section">
              <p className="detail-section__label">RELATED ARTICLES</p>
              <ul className="detail-related">
                {relatedArticles.map((related) => (
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
```

```css
/* frontend/src/pages/ArticleDetailPage.css */
.detail-page {
  width: min(680px, calc(100% - 48px));
  margin: 0 auto;
  padding: 40px 0 80px;
}

.detail-status {
  text-align: center;
  color: var(--color-text-meta);
  font-size: 13px;
  margin-top: 60px;
}

.detail-article {
  display: flex;
  flex-direction: column;
}

.detail-tags {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
}

.detail-tag {
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.04em;
  padding: 2px 8px;
  border-radius: 3px;
}

.detail-tag--category {
  color: var(--color-accent);
  background: var(--color-accent-bg);
}

.detail-tag--event {
  color: var(--color-text-meta);
  background: var(--color-border-light);
}

.detail-title {
  font-family: var(--font-serif);
  font-size: 26px;
  font-weight: 700;
  line-height: 1.3;
  color: var(--color-text);
  letter-spacing: -0.01em;
  margin: 0 0 14px;
}

.detail-byline {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--color-text-meta);
  margin-bottom: 20px;
}

.detail-byline__dot {
  color: var(--color-border);
}

.detail-byline__link {
  color: var(--color-accent);
}

.detail-divider {
  border: none;
  border-top: 1px solid var(--color-border);
  margin: 20px 0;
}

.detail-section {
  margin-bottom: 4px;
}

.detail-section__label {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.1em;
  color: var(--color-text-muted);
  margin: 0 0 8px;
}

.detail-section__text {
  font-family: var(--font-serif);
  font-size: 14px;
  line-height: 1.75;
  color: var(--color-text-sub);
  margin: 0;
}

.detail-why-box {
  border-left: 3px solid var(--color-accent);
  background: var(--color-accent-surface);
  border-radius: 0 4px 4px 0;
  padding: 14px 16px;
}

.detail-why-box .detail-section__text {
  color: #1e1b4b;
}

.detail-topics {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.detail-topic-chip {
  font-size: 11px;
  color: var(--color-text-sub);
  background: var(--color-border-light);
  border: 1px solid var(--color-border);
  padding: 3px 10px;
  border-radius: 12px;
}

.detail-related {
  list-style: none;
  margin: 0;
  padding: 0;
}

.detail-related__item {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  padding: 9px 0;
  border-bottom: 1px solid var(--color-border-light);
}

.detail-related__item:last-child {
  border-bottom: none;
}

.detail-related__title {
  font-family: var(--font-serif);
  font-size: 13px;
  color: var(--color-text-sub);
  line-height: 1.4;
}

.detail-related__title:hover {
  color: var(--color-accent);
}

.detail-related__tag {
  font-size: 10px;
  font-weight: 600;
  color: var(--color-accent);
  background: var(--color-accent-bg);
  padding: 2px 7px;
  border-radius: 3px;
  white-space: nowrap;
  flex-shrink: 0;
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
cd frontend && npm test -- ArticleDetailPage.test.tsx
```

Expected: 6 tests PASS

- [ ] **Step 5: 커밋**

```bash
git add frontend/src/pages/ArticleDetailPage.tsx frontend/src/pages/ArticleDetailPage.css frontend/src/pages/ArticleDetailPage.test.tsx
git commit -m "feat: add ArticleDetailPage"
```

---

## Task 8: App.tsx 라우터로 교체 + 기존 스타일 정리

**Files:**
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.css`
- Modify: `frontend/src/App.test.tsx`

- [ ] **Step 1: App.test.tsx를 라우팅 기반으로 교체**

```typescript
// frontend/src/App.test.tsx
import { render, screen } from '@testing-library/react'
import { expect, it } from 'vitest'
import App from './App'

it('renders the NavBar logo', async () => {
  render(<App />)
  expect(await screen.findByRole('link', { name: 'SIGAK' })).toBeInTheDocument()
})
```

- [ ] **Step 2: App.tsx를 라우터 진입점으로 교체**

```tsx
// frontend/src/App.tsx
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import NavBar from './components/NavBar'
import HomePage from './pages/HomePage'
import ArticleDetailPage from './pages/ArticleDetailPage'

export default function App() {
  return (
    <BrowserRouter>
      <NavBar />
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/articles/:id" element={<ArticleDetailPage />} />
      </Routes>
    </BrowserRouter>
  )
}
```

- [ ] **Step 3: App.css를 빈 파일로 교체 (모든 스타일은 각 컴포넌트로 이동)**

```css
/* frontend/src/App.css */
/* Styles live in component CSS files. */
```

- [ ] **Step 4: 전체 테스트 통과 확인**

```bash
cd frontend && npm test
```

Expected: 모든 테스트 PASS

- [ ] **Step 5: lint + build 확인**

```bash
cd frontend && npm run lint && npm run build
```

Expected: 에러 없음

- [ ] **Step 6: 최종 커밋**

```bash
git add frontend/src/App.tsx frontend/src/App.css frontend/src/App.test.tsx
git commit -m "feat: wire up React Router and redesign complete"
```
