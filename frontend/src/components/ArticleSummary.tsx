import { useEffect, useState } from 'react'
import type { Article } from '../api/articles'
import { requestArticleSummary, fetchArticleSummary } from '../api/summaries'
import type { ArticleSummaryResult } from '../api/summaries'

const messages: Record<string, string> = {
  FAILED: '요약을 만들지 못했습니다. 원문 링크에서 기사를 확인할 수 있습니다.',
  UNKNOWN: '요약 처리 결과를 확인하지 못했습니다. 중복 비용을 막기 위해 다시 요청하지 않습니다.',
  BUDGET_EXHAUSTED: '요약 예산 한도에 도달했습니다. 원문 기사는 계속 확인할 수 있습니다.',
  UNAVAILABLE: '이 기사의 AI 요약은 현재 제공되지 않습니다. 원문 링크를 이용해 주세요.',
}

function readerText(text: string | null | undefined): string {
  return (text ?? '').replace(/FEED_EXCERPT/g, '기사 소개글').replace(/ABSTRACT/g, '논문 초록')
}

export default function ArticleSummary({ article }: { article: Article }) {
  const stored = Boolean(article.summary)
  const [result, setResult] = useState<ArticleSummaryResult | null>(null)
  const [timedOut, setTimedOut] = useState(false)

  useEffect(() => {
    if (stored) {
      if (!article.summaryProvider?.startsWith('gpt-')) return
      let active = true
      fetchArticleSummary(article.id).then((value) => { if (active) setResult(value) }).catch(() => {})
      return () => { active = false }
    }
    let active = true
    let timer: ReturnType<typeof setTimeout> | undefined
    let polls = 0
    const update = (value: ArticleSummaryResult) => {
      if (!active) return
      setResult(value)
      if (value.status === 'PENDING') {
        if (polls >= 30) { setTimedOut(true); return }
        timer = setTimeout(() => {
          polls++
          fetchArticleSummary(article.id).then(update).catch(failed)
        }, 2000)
      }
    }
    const failed = () => {
      if (active) setResult({ articleId: article.id, status: 'UNKNOWN', summary: null, whyItMatters: null,
        evidenceQuotes: [], modelName: null, inputScope: null, truncated: false, errorCode: null })
    }
    // 목록·관련 기사 조회와 분리해 실제 상세 화면을 연 때에만 생성 요청을 보낸다.
    requestArticleSummary(article.id).then(update).catch(failed)
    return () => { active = false; clearTimeout(timer) }
  }, [article.id, article.summaryProvider, stored])

  const summary = stored ? article.summary : result?.summary
  const insight = stored ? article.whyItMatters : result?.whyItMatters
  const pending = !stored && (!result || result.status === 'PENDING')
  const provider = result?.modelName ?? article.summaryProvider
  const scope = result?.inputScope

  return (
    <>
      {article.sourceExcerpt && (
        <section className="detail-section">
          <p className="detail-section__label">SOURCE EXCERPT</p>
          <p className="detail-section__text">{article.sourceExcerpt}</p>
        </section>
      )}
      <section className="detail-section" aria-live="polite" aria-busy={pending}>
        <p className="detail-section__label">SUMMARY</p>
        {pending ? <p role="status" className="detail-section__text">
          {timedOut ? '요약이 아직 처리 중입니다. 나중에 다시 방문하면 저장된 결과를 확인할 수 있습니다.' : '요약 생성 중… 기사는 바로 읽고, 요약은 준비되는 대로 보여드립니다.'}
        </p> : summary ? <p className="detail-section__text">{readerText(summary)}</p> :
          <p role="status" className="detail-section__text">{messages[result?.status ?? 'UNAVAILABLE']}</p>}
        {provider && <p className="detail-summary-note">
          {provider === 'curated-seed' ? '샘플 기사 · 예시 요약' : `AI 요약 · ${provider}`}
          {scope && ` · ${scope === 'ABSTRACT' ? '논문 초록 기반' : '수집된 소개글 기반'}`}
          {result?.truncated && ' · 입력 일부 사용'}
        </p>}
      </section>
      {insight && <section className="detail-section">
        <p className="detail-section__label">WHY IT MATTERS</p>
        <div className="detail-why-box"><p className="detail-section__text">{readerText(insight)}</p></div>
      </section>}
      {!!result?.evidenceQuotes.length && <section className="detail-section">
        <p className="detail-section__label">SOURCE EVIDENCE</p>
        {result.evidenceQuotes.map((quote) => <blockquote className="detail-evidence" key={quote}>{quote}</blockquote>)}
      </section>}
    </>
  )
}
