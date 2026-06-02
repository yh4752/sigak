# Retrieval Benchmark Smoke Report

이 report는 public article search API의 현재 검색 모드를 사람이 검토한 label JSON과 비교하는 smoke benchmark 결과다.

## Summary

| Metric | Value |
| --- | ---: |
| Catalog ID | api-ready-2026-06-02 |
| Catalog articles | 6 |
| Evaluated queries | 3 |
| K | 5 |
| Top1 Strong Hit | 0.6666666666666666 |
| Recall@5 | 0.8333333333333334 |
| MRR@5 | 0.8333333333333334 |
| Average LatencyMs | 43.333333333333336 |

## Metric Notes

- Top1 Strong Hit: 첫 번째 결과가 strong article이면 1이고, 아니면 0이다.
- Recall@5: Top5 안에 strong 또는 acceptable article이 얼마나 포함됐는지 본다.
- MRR@5: Top5 안에서 첫 strong article이 얼마나 빨리 등장하는지 본다.
- LatencyMs: public article search API round-trip 시간을 ms 단위로 한 번 측정한 값이다.

## Query Results

| Query | Top1 Strong Hit | Recall@5 | MRR@5 | LatencyMs | Result IDs |
| --- | ---: | ---: | ---: | ---: | --- |
| agent evaluation | 1 | 1 | 1 | 82 | 1, 4, 5, 6, 2, 3 |
| graph rag failure | 0 | 1 | 0.5 | 26 | 2, 4, 5, 6, 1, 3 |
| postgres vector search | 1 | 0.5 | 1 | 22 | 2, 6, 5, 3, 1, 4 |

## Run Conditions

- System: hybrid
- Base URL: http://localhost:8080
- Labels: experiments/datasets/labels/search-labels.api-ready-2026-06-02.2026-06-02.json
- Generated at: 2026-06-02T11:54:10.408Z

## Limits

이 결과는 smoke benchmark다. Query와 article 수가 작기 때문에 검색 품질 개선의 최종 근거가 아니라, qrels -> run -> metrics -> report 파이프라인이 재현 가능하게 동작하는지 확인하는 용도다.
