import { z } from 'zod'
import { httpClient } from './httpClient'

const retrievalSystemMetricSchema = z.object({
  name: z.string(),
  attemptedQueryCount: z.number(),
  completedQueryCount: z.number(),
  failedQueryCount: z.number(),
  degradedQueryCount: z.number(),
  macroTop1StrongHit: z.number(),
  macroRecallAtK: z.number(),
  macroMrrAtK: z.number(),
  averageLatencyMs: z.number(),
})

const retrievalSectionSchema = z.object({
  available: z.boolean(),
  unavailableReason: z.string().nullable(),
  catalogId: z.string().nullable(),
  catalogArticleCount: z.number().nullable(),
  evaluatedQueryCount: z.number().nullable(),
  k: z.number().nullable(),
  generatedAt: z.string().nullable(),
  systems: z.array(retrievalSystemMetricSchema),
  warnings: z.array(z.string()),
})

const graphQueryEvidenceSchema = z.object({
  query: z.string(),
  intent: z.string().nullable(),
  searchPositiveCoverageAtK: z.number(),
  relatedBaselineCoverageAtK: z.number(),
  graphContextCoverageAtK: z.number(),
  graphReasonedCoverageAtK: z.number(),
  reasonCoverage: z.number().nullable(),
  topicContextCoverage: z.number().nullable(),
  averageGraphLatencyMs: z.number(),
  searchMissedPositiveIds: z.array(z.number()),
})

const graphSectionSchema = z.object({
  available: z.boolean(),
  unavailableReason: z.string().nullable(),
  catalogId: z.string().nullable(),
  catalogArticleCount: z.number().nullable(),
  evaluatedQueryCount: z.number().nullable(),
  k: z.number().nullable(),
  generatedAt: z.string().nullable(),
  macroSearchPositiveCoverageAtK: z.number().nullable(),
  macroRelatedBaselineCoverageAtK: z.number().nullable(),
  macroGraphContextCoverageAtK: z.number().nullable(),
  macroGraphReasonedCoverageAtK: z.number().nullable(),
  graphContextFailureRate: z.number().nullable(),
  emptyContextRate: z.number().nullable(),
  averageGraphLatencyMs: z.number().nullable(),
  queryEvidence: z.array(graphQueryEvidenceSchema),
  warnings: z.array(z.string()),
})

const expandedCatalogSectionSchema = z.object({
  available: z.boolean(),
  unavailableReason: z.string().nullable(),
  catalogId: z.string().nullable(),
  generatedAt: z.string().nullable(),
  articleCount: z.number().nullable(),
  labelStatus: z.string(),
  benchmarkStatus: z.string(),
  warnings: z.array(z.string()),
})

const researchOverviewSchema = z.object({
  generatedAt: z.string(),
  retrieval: retrievalSectionSchema,
  graph: graphSectionSchema,
  expandedCatalog: expandedCatalogSectionSchema,
  limitations: z.array(z.string()),
})

export type ResearchOverview = z.infer<typeof researchOverviewSchema>
export type RetrievalSystemMetric = z.infer<typeof retrievalSystemMetricSchema>
export type GraphQueryEvidence = z.infer<typeof graphQueryEvidenceSchema>

export async function fetchResearchOverview(): Promise<ResearchOverview> {
  const response = await httpClient.get('/api/research/overview')
  return researchOverviewSchema.parse(response.data)
}
