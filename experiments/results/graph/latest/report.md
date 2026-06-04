# Retrieval Benchmark Smoke Comparison Report

이 report는 strict retrieval system과 public search behavior를 같은 label set으로 나란히 비교한 결과다.

## System Comparison

| System | Completed | Failed | Failure Rate | Degraded Rate | Top1 Strong Hit | Recall@K | MRR@K | Effective Recall@K | Avg LatencyMs |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| public | 3 | 0 | 0 | 0 | 1 | 0.6666666666666666 | 1 | 0.6666666666666666 | 137.33333333333334 |

## Best Observed Systems

The phrase "best observed system in this smoke run" is intentional because the current dataset is still small.

- macroTop1StrongHit: public
- macroRecallAtK: public
- macroMrrAtK: public
- effectiveRecallAtK: public
- averageLatencyMs: public

## Query Results

| Query | System | Status | Resolved Mode | Top1 Strong Hit | Recall@K | MRR@K | LatencyMs | Result IDs | Failure Reason |
| --- | --- | --- | --- | ---: | ---: | ---: | ---: | --- | --- |
| agent evaluation | public | COMPLETED | HYBRID | 1 | 1 | 1 | 301 | 1, 18, 4, 11, 7, 8, 3, 9, 10, 15, 13, 20, 23, 5, 24, 22, 21, 26, 25, 6 | - |
| graph rag failure | public | COMPLETED | HYBRID | 1 | 0.5 | 1 | 71 | 4, 23, 2, 20, 19, 22, 25, 24, 14, 10, 21, 13, 26, 12, 17, 3, 8, 18, 16, 5 | - |
| postgres vector search | public | COMPLETED | HYBRID | 1 | 0.5 | 1 | 40 | 2, 17, 26, 18, 16, 21, 24, 22, 4, 25, 20, 12, 14, 19, 13, 15, 3, 9, 8, 5 | - |

## Limitations

- label set is smaller than 10 reviewed queries, so this is a smoke benchmark
- catalog article count is below 20, so ranking difficulty is still low

## Strict HYBRID vs PUBLIC

- Strict HYBRID is an experiment system created by the internal evaluation endpoint.
- Strict HYBRID fails when keyword or vector retrieval fails.
- PUBLIC is user-visible behavior from GET /api/articles and may degrade.
- PUBLIC is not sent to the internal evaluation endpoint.
- PUBLIC degrade metadata is read from the internal last-search metrics endpoint immediately after each sequential public request, so run this benchmark without concurrent search traffic.

---

# Graph-Aware Evaluation Smoke Report

This report evaluates graph-aware article detail context collected from public article APIs.
The graph evaluation is article detail explanation evidence, not query ranking.

## Summary Metrics

| Metric | Value |
| --- | ---: |
| Catalog ID | api-ready-2026-06-02 |
| Catalog articles | 6 |
| Evaluated queries | 3 |
| K | 5 |
| Search Positive Coverage@K | 0.6666666666666666 |
| Related Baseline Coverage@K | 0.3333333333333333 |
| Graph Context Coverage@K | 0.3333333333333333 |
| Graph Context Coverage Among Search Hits | 0.16666666666666666 |
| Graph Reasoned Coverage@K | 0.3333333333333333 |
| Reason Coverage | 1 |
| Topic Context Coverage | 1 |
| Graph Context Failure Rate | 0 |
| Empty Context Rate | 0 |
| Average Graph LatencyMs | 33.53333333333333 |

## Query Results

| Query | Search Positive Coverage@K | Graph Context Coverage@K | Graph Coverage Among Search Hits | Graph Reasoned Coverage@K | Reason Coverage | Topic Context Coverage | Search Missed Positive IDs | Avg Graph LatencyMs |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- | ---: |
| agent evaluation | 1 | 0.5 | 0.5 | 0.5 | 1 | 1 | - | 80.6 |
| graph rag failure | 0.5 | 0.5 | 0 | 0.5 | 1 | 1 | 1 | 8.8 |
| postgres vector search | 0.5 | 0 | 0 | 0 | - | 1 | 4 | 11.2 |

## Interpretation

- Search miss means a positive article was not present in the public search top-k, so graph context may never have a detail-page chance to explain it.
- Related baseline coverage comes from article detail related IDs, while graph context coverage comes from public graph-context reasons.
- Graph reasons are stored projection reasons, not independently verified factual explanations.
- This graph evaluation is article detail explanation evidence, not query ranking.

## Run Conditions

- Base URL: http://localhost:8080
- Labels: experiments/datasets/labels/search-labels.api-ready-2026-06-02.2026-06-02.json
- Labels SHA-256: 310412b724ba08ea68ed1d02da0a014b03cadbdc34172311bb4af72128d5cfcd
- Source article selection: public_search_top_k
- Graph context endpoint: /api/articles/{id}/graph-context
- Reason source: stored_projection_reason
- Generated at: 2026-06-04T03:21:51.802Z
- Latency is public API round-trip, not pure Neo4j query latency.

## Limits And Warnings

- label set is smaller than 10 reviewed queries, so this is a smoke graph evaluation
- catalog is smaller than 20 articles, so graph density is too small for quality claims
- graph latency is measured as public API round-trip, not pure Neo4j query latency
- graph reasons are stored projection reasons, not independently verified factual explanations

The current smoke/small dataset limitations mean these artifacts verify that the graph-aware evaluation pipeline runs, not that graph quality or search quality has been proven.
