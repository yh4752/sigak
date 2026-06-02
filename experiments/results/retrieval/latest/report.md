# Retrieval Benchmark Smoke Comparison Report

이 report는 strict retrieval system과 public search behavior를 같은 label set으로 나란히 비교한 결과다.

## System Comparison

| System | Completed | Failed | Failure Rate | Degraded Rate | Top1 Strong Hit | Recall@K | MRR@K | Effective Recall@K | Avg LatencyMs |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| keyword | 3 | 0 | 0 | 0 | 1 | 0.6666666666666666 | 1 | 0.6666666666666666 | 21.666666666666668 |
| vector | 3 | 0 | 0 | 0 | 0 | 0.8333333333333334 | 0.17777777777777778 | 0.8333333333333334 | 20.333333333333332 |
| hybrid | 3 | 0 | 0 | 0 | 0.6666666666666666 | 0.8333333333333334 | 0.8333333333333334 | 0.8333333333333334 | 15 |
| public | 3 | 0 | 0 | 0 | 0.6666666666666666 | 0.8333333333333334 | 0.8333333333333334 | 0.8333333333333334 | 18.666666666666668 |

## Best Observed Systems

The phrase "best observed system in this smoke run" is intentional because the current dataset is still small.

- macroTop1StrongHit: keyword
- macroRecallAtK: vector, hybrid, public
- macroMrrAtK: keyword
- effectiveRecallAtK: vector, hybrid, public
- averageLatencyMs: hybrid

## Query Results

| Query | System | Status | Resolved Mode | Top1 Strong Hit | Recall@K | MRR@K | LatencyMs | Result IDs | Failure Reason |
| --- | --- | --- | --- | ---: | ---: | ---: | ---: | --- | --- |
| agent evaluation | keyword | COMPLETED | KEYWORD | 1 | 1 | 1 | 23 | 1, 4 | - |
| graph rag failure | keyword | COMPLETED | KEYWORD | 1 | 0.5 | 1 | 21 | 4, 2 | - |
| postgres vector search | keyword | COMPLETED | KEYWORD | 1 | 0.5 | 1 | 21 | 2 | - |
| agent evaluation | vector | COMPLETED | VECTOR | 0 | 1 | 0.3333333333333333 | 27 | 5, 4, 1, 6, 2, 3 | - |
| graph rag failure | vector | COMPLETED | VECTOR | 0 | 1 | 0.2 | 16 | 5, 2, 6, 1, 4, 3 | - |
| postgres vector search | vector | COMPLETED | VECTOR | 0 | 0.5 | 0 | 18 | 6, 5, 3, 1, 4, 2 | - |
| agent evaluation | hybrid | COMPLETED | HYBRID | 1 | 1 | 1 | 12 | 1, 4, 5, 6, 2, 3 | - |
| graph rag failure | hybrid | COMPLETED | HYBRID | 0 | 1 | 0.5 | 23 | 2, 4, 5, 6, 1, 3 | - |
| postgres vector search | hybrid | COMPLETED | HYBRID | 1 | 0.5 | 1 | 10 | 2, 6, 5, 3, 1, 4 | - |
| agent evaluation | public | COMPLETED | HYBRID | 1 | 1 | 1 | 21 | 1, 4, 5, 6, 2, 3 | - |
| graph rag failure | public | COMPLETED | HYBRID | 0 | 1 | 0.5 | 18 | 2, 4, 5, 6, 1, 3 | - |
| postgres vector search | public | COMPLETED | HYBRID | 1 | 0.5 | 1 | 17 | 2, 6, 5, 3, 1, 4 | - |

## Limitations

- label set is smaller than 10 reviewed queries, so this is a smoke benchmark
- catalog article count is below 20, so ranking difficulty is still low

## Strict HYBRID vs PUBLIC

- Strict HYBRID is an experiment system created by the internal evaluation endpoint.
- Strict HYBRID fails when keyword or vector retrieval fails.
- PUBLIC is user-visible behavior from GET /api/articles and may degrade.
- PUBLIC is not sent to the internal evaluation endpoint.
- PUBLIC degrade metadata is read from the internal last-search metrics endpoint immediately after each sequential public request, so run this benchmark without concurrent search traffic.
