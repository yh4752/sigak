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
  return (text ?? '').replace(/FULL_TEXT/g, '기사 본문').replace(/FEED_EXCERPT/g, '기사 소개글').replace(/ABSTRACT/g, '논문 초록')
}

export default function ArticleSummary({ article }: { article: Article }) {
  const stored = Boolean(article.summary) && article.summaryScope !== 'FEED_EXCERPT'
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
        if (polls >= 120) { setTimedOut(true); return }
        timer = setTimeout(() => {
          polls++
          fetchArticleSummary(article.id).then(update).catch(failed)
        }, 500)
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
  const provider = result?.modelName ?? (stored ? article.summaryProvider : null)
  const scope = result?.inputScope ?? (stored ? article.summaryScope : null)
  const bodyMessage = result?.errorCode === 'BODY_UNSUPPORTED'
    ? '아직 본문 요약을 지원하지 않는 출처입니다. 원문 링크에서 확인해 주세요.'
    : result?.errorCode === 'BODY_TOO_LONG'
      ? '본문이 요약 길이 제한을 넘었습니다. 비용 없이 원문 링크를 제공합니다.'
      : result?.errorCode?.startsWith('BODY_')
        ? '원문 사이트에서 본문을 받지 못해 AI 요약을 제공할 수 없습니다. 이번 요청으로 AI 요약 비용은 발생하지 않았습니다. 원문 링크에서 기사를 확인해 주세요.' : null
  const fetching = result?.bodyStatus === 'PENDING' || result?.bodyStatus === 'FETCHING'

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
          {timedOut ? '요약이 아직 처리 중입니다. 나중에 다시 방문하면 저장된 결과를 확인할 수 있습니다.' : fetching ? '기사 본문을 가져오는 중… 준비되면 요약을 이어서 만듭니다.' : '요약 생성 중… 기사는 바로 읽고, 요약은 준비되는 대로 보여드립니다.'}
        </p> : summary ? <p className="detail-section__text">{readerText(summary)}</p> :
          <p role="status" className="detail-section__text">{bodyMessage ?? messages[result?.status ?? 'UNAVAILABLE']}</p>}
        {provider && <p className="detail-summary-note">
          {provider === 'curated-seed' ? '샘플 기사 · 예시 요약' : `AI 요약 · ${provider}`}
          {scope && ` · ${scope === 'FULL_TEXT' ? '본문 기반' : scope === 'ABSTRACT' ? '논문 초록 기반' : '수집된 소개글 기반'}`}
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
