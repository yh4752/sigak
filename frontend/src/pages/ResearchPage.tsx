import { useCallback, useEffect, useMemo, useState } from 'react'
import { fetchResearchOverview } from '../api/research'
import type { GraphQueryEvidence, ResearchOverview, RetrievalSystemMetric } from '../api/research'
import './ResearchPage.css'

export default function ResearchPage() {
  const [overview, setOverview] = useState<ResearchOverview | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState('')

  const loadOverview = useCallback(async () => {
    setIsLoading(true)
    setErrorMessage('')
    try {
      setOverview(await fetchResearchOverview())
    } catch {
      setOverview(null)
      setErrorMessage('Unable to load research metrics.')
    } finally {
      setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    let isCurrent = true

    fetchResearchOverview()
      .then((fetchedOverview) => {
        if (isCurrent) {
          setOverview(fetchedOverview)
          setErrorMessage('')
        }
      })
      .catch(() => {
        if (isCurrent) {
          setOverview(null)
          setErrorMessage('Unable to load research metrics.')
        }
      })
      .finally(() => {
        if (isCurrent) {
          setIsLoading(false)
        }
      })

    return () => {
      isCurrent = false
    }
  }, [])

  return (
    <main className="research-page">
      <header className="research-hero">
        <p className="research-eyebrow">Portfolio Evidence</p>
        <h1>Research Evidence</h1>
        <p className="research-hero__copy">
          Hybrid retrieval, graph context, and reproducible smoke metrics from Sigak's local experiment artifacts.
        </p>
      </header>

      {isLoading && <p className="research-status" role="status">Loading research metrics...</p>}

      {errorMessage && (
        <div className="research-state" role="alert">
          <p>{errorMessage}</p>
          <button type="button" onClick={loadOverview}>Retry</button>
        </div>
      )}

      {!isLoading && !errorMessage && overview && <ResearchDashboard overview={overview} />}
    </main>
  )
}

function ResearchDashboard({ overview }: { overview: ResearchOverview }) {
  const scopeItems = [
    ['Generated', formatDate(overview.generatedAt)],
    ['Retrieval Catalog', overview.retrieval.catalogId ?? 'Unavailable'],
    ['Graph Catalog', overview.graph.catalogId ?? 'Unavailable'],
    ['Expanded Catalog', overview.expandedCatalog.catalogId ?? 'Unavailable'],
  ]

  return (
    <>
      <section className="research-scope" aria-label="Research artifact scope">
        {scopeItems.map(([label, value]) => (
          <div key={label} className="research-scope__item">
            <span>{label}</span>
            <strong>{value}</strong>
          </div>
        ))}
      </section>
      <PortfolioSignals />
      <RetrievalSection overview={overview} />
      <GraphSection overview={overview} />
      <CatalogSection overview={overview} />
      <Limitations limitations={overview.limitations} />
    </>
  )
}

function PortfolioSignals() {
  const signals = [
    ['Hybrid Retrieval', 'Keyword and vector candidates are compared through one dashboard boundary.'],
    ['Vector Search', 'Qdrant-backed semantic search is visible beside keyword and public modes.'],
    ['Graph Context', 'Article relationship evidence is shown with coverage and missed positives.'],
    ['Reproducible Metrics', 'Smoke artifacts and current limits stay visible in the same view.'],
  ]

  return (
    <section className="research-section" aria-labelledby="research-signals-title">
      <div className="research-section__header">
        <h2 id="research-signals-title">System Capabilities</h2>
      </div>
      <div className="research-signal-grid">
        {signals.map(([label, body]) => (
          <div key={label} className="research-card">
            <span className="research-card__label">Signal</span>
            <strong>{label}</strong>
            <p>{body}</p>
          </div>
        ))}
      </div>
    </section>
  )
}

function RetrievalSection({ overview }: { overview: ResearchOverview }) {
  const { retrieval } = overview
  const bestRecall = useMemo(
    () => Math.max(...retrieval.systems.map((system) => system.macroRecallAtK), Number.NEGATIVE_INFINITY),
    [retrieval.systems],
  )

  return (
    <section className="research-section" aria-labelledby="retrieval-benchmark-title">
      <div className="research-section__header">
        <div>
          <h2 id="retrieval-benchmark-title">Retrieval Benchmark</h2>
          <p className="research-section__description">
            Keyword, vector, hybrid, and public search evidence from the current smoke run.
          </p>
        </div>
        <span className="research-section__meta">
          {retrieval.evaluatedQueryCount ?? 'n/a'} queries · K={retrieval.k ?? 'n/a'}
        </span>
      </div>

      {!retrieval.available ? (
        <UnavailableState
          title="Retrieval artifact unavailable"
          reason={retrieval.unavailableReason}
        />
      ) : (
        <>
          <div className="research-table-wrap">
            <table className="research-table">
              <thead>
                <tr>
                  <th scope="col">System</th>
                  <th scope="col">Recall@K</th>
                  <th scope="col">MRR@K</th>
                  <th scope="col">Top-1</th>
                  <th scope="col">Latency</th>
                  <th scope="col">Failed</th>
                  <th scope="col">Degraded</th>
                </tr>
              </thead>
              <tbody>
                {retrieval.systems.map((system) => (
                  <RetrievalSystemRow
                    key={system.name}
                    system={system}
                    highlightRecall={system.macroRecallAtK === bestRecall}
                  />
                ))}
              </tbody>
            </table>
          </div>
          <WarningList warnings={retrieval.warnings} />
        </>
      )}
    </section>
  )
}

function RetrievalSystemRow({
  system,
  highlightRecall,
}: {
  system: RetrievalSystemMetric
  highlightRecall: boolean
}) {
  return (
    <tr>
      <td><strong>{system.name}</strong></td>
      <td className={highlightRecall ? 'research-table__best' : undefined}>{formatPercent(system.macroRecallAtK)}</td>
      <td>{formatPercent(system.macroMrrAtK)}</td>
      <td>{formatPercent(system.macroTop1StrongHit)}</td>
      <td>{formatMs(system.averageLatencyMs)}</td>
      <td>{system.failedQueryCount}</td>
      <td>{system.degradedQueryCount}</td>
    </tr>
  )
}

function GraphSection({ overview }: { overview: ResearchOverview }) {
  const { graph } = overview

  return (
    <section className="research-section" aria-labelledby="graph-insight-title">
      <div className="research-section__header">
        <div>
          <h2 id="graph-insight-title">Graph-Aware Insight</h2>
          <p className="research-section__description">
            Query-level evidence for where relationship context helps and where search still misses.
          </p>
        </div>
        <span className="research-section__meta">
          {graph.evaluatedQueryCount ?? 'n/a'} queries · {graph.catalogArticleCount ?? 'n/a'} articles
        </span>
      </div>

      {!graph.available ? (
        <UnavailableState
          title="Graph artifact unavailable"
          reason={graph.unavailableReason}
        />
      ) : (
        <>
          <div className="research-metric-grid">
            <MetricCard label="Graph Context Coverage" value={formatPercent(graph.macroGraphContextCoverageAtK)} />
            <MetricCard label="Reasoned Coverage" value={formatPercent(graph.macroGraphReasonedCoverageAtK)} />
            <MetricCard label="Failure Rate" value={formatPercent(graph.graphContextFailureRate)} />
            <MetricCard label="Graph Latency" value={formatMs(graph.averageGraphLatencyMs)} />
          </div>
          <GraphEvidenceTable evidence={graph.queryEvidence} />
          <WarningList warnings={graph.warnings} />
        </>
      )}
    </section>
  )
}

function MetricCard({ label, value }: { label: string, value: string }) {
  return (
    <div className="research-card research-card--metric">
      <span className="research-card__label">{label}</span>
      <strong>{value}</strong>
    </div>
  )
}

function GraphEvidenceTable({ evidence }: { evidence: GraphQueryEvidence[] }) {
  if (evidence.length === 0) {
    return <p className="research-empty">No query-level graph evidence is available yet.</p>
  }

  return (
    <div className="research-table-wrap research-table-wrap--spaced">
      <table className="research-table research-table--evidence">
        <thead>
          <tr>
            <th scope="col">Query</th>
            <th scope="col">Search</th>
            <th scope="col">Graph</th>
            <th scope="col">Reason</th>
            <th scope="col">Missed IDs</th>
            <th scope="col">Latency</th>
          </tr>
        </thead>
        <tbody>
          {evidence.map((item) => (
            <tr key={`${item.query}-${item.intent ?? ''}`}>
              <td>
                <strong>{item.query}</strong>
                {item.intent && <span>{item.intent}</span>}
              </td>
              <td>{formatPercent(item.searchPositiveCoverageAtK)}</td>
              <td>{formatPercent(item.graphContextCoverageAtK)}</td>
              <td>{formatPercent(item.reasonCoverage)}</td>
              <td>{item.searchMissedPositiveIds.length > 0 ? item.searchMissedPositiveIds.join(', ') : 'none'}</td>
              <td>{formatMs(item.averageGraphLatencyMs)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function CatalogSection({ overview }: { overview: ResearchOverview }) {
  const { expandedCatalog } = overview

  return (
    <section className="research-section" aria-labelledby="catalog-status-title">
      <div className="research-section__header">
        <div>
          <h2 id="catalog-status-title">Expanded Catalog And Limits</h2>
          <p className="research-section__description">
            The broader article catalog is tracked separately from benchmark-ready labels.
          </p>
        </div>
        <span className="research-section__meta">{formatDate(expandedCatalog.generatedAt)}</span>
      </div>

      {!expandedCatalog.available ? (
        <UnavailableState
          title="Expanded catalog unavailable"
          reason={expandedCatalog.unavailableReason}
        />
      ) : (
        <>
          <div className="research-catalog">
            <div>
              <span className="research-card__label">Catalog</span>
              <strong>{expandedCatalog.catalogId ?? 'Unavailable'}</strong>
            </div>
            <div>
              <span className="research-card__label">Scope</span>
              <strong>{expandedCatalog.articleCount ?? 'n/a'} articles</strong>
            </div>
            <div>
              <span className="research-card__label">Labels</span>
              <strong>{formatStatus(expandedCatalog.labelStatus)}</strong>
            </div>
            <div>
              <span className="research-card__label">Benchmark</span>
              <strong>{formatStatus(expandedCatalog.benchmarkStatus)}</strong>
            </div>
          </div>
          <div className="research-next-steps">
            <p>
              Without reviewed labels, this catalog is useful for UI and pipeline smoke checks.
              After labels are reviewed, it can support broader retrieval quality claims.
            </p>
          </div>
          <WarningList warnings={expandedCatalog.warnings} />
        </>
      )}
    </section>
  )
}

function Limitations({ limitations }: { limitations: string[] }) {
  if (limitations.length === 0) {
    return null
  }

  return (
    <section className="research-section" aria-labelledby="research-limitations-title">
      <div className="research-section__header">
        <h2 id="research-limitations-title">Current Limits</h2>
      </div>
      <ul className="research-limitations">
        {limitations.map((limitation) => (
          <li key={limitation}>{limitation}</li>
        ))}
      </ul>
    </section>
  )
}

function UnavailableState({ title, reason }: { title: string, reason: string | null }) {
  return (
    <div className="research-unavailable">
      <strong>{title}</strong>
      {reason && <p>{reason}</p>}
    </div>
  )
}

function WarningList({ warnings }: { warnings: string[] }) {
  if (warnings.length === 0) {
    return null
  }

  return (
    <ul className="research-warning">
      {warnings.map((warning) => (
        <li key={warning}>{warning}</li>
      ))}
    </ul>
  )
}

function formatPercent(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return 'n/a'
  }
  return `${Math.round(value * 100)}%`
}

function formatMs(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return 'n/a'
  }
  return `${value.toFixed(value >= 10 ? 1 : 2)} ms`
}

function formatDate(value: string | null | undefined): string {
  if (!value) {
    return 'Unavailable'
  }

  return new Intl.DateTimeFormat('en', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  }).format(new Date(value))
}

function formatStatus(value: string): string {
  return value
    .split('_')
    .filter(Boolean)
    .map((part) => part[0].toUpperCase() + part.slice(1))
    .join(' ')
}
