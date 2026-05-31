# Search Evaluation Queries

This file is a small baseline query set for comparing keyword, vector, and
hybrid article search behavior. It is not a tuning result yet. The purpose is
to keep future RRF weight, candidate limit, and embedding changes measurable.

## Query Set

| Query | Expected Useful Articles | Current Smoke Result | Notes |
| --- | --- | --- | --- |
| `graph` | `4` | `4, 3, 1, 2, 5` | Graph RAG article should rank highly. Current hybrid result places article 4 first. |
| `security` | `3` | `5, 3, 1, 4, 2` | Security article should appear. Current vector influence brings infrastructure article 5 first, so this is a future tuning candidate. |
| `vector` | `2` | `2, 4, 1, 3, 5` | Vector database article should rank highly. Current hybrid result places article 2 first. |

## Smoke Modes

| Mode | How It Was Triggered | Expected Signal |
| --- | --- | --- |
| `HYBRID` | Elasticsearch and Qdrant both available | `lastSearch.mode = HYBRID` |
| `KEYWORD_ONLY` | Qdrant stopped | `lastSearch.mode = KEYWORD_ONLY`, `vectorFailed = true` |
| `VECTOR_ONLY` | Elasticsearch stopped | `lastSearch.mode = VECTOR_ONLY`, `keywordFailed = true` |
| `POSTGRES_FALLBACK` | Elasticsearch and Qdrant both stopped | `lastSearch.mode = POSTGRES_FALLBACK` |

## Next Evaluation Questions

- Does hybrid improve top-1 or top-3 relevance over keyword-only for portfolio seed data?
- Which query classes are harmed by vector influence, such as broad terms like `security`?
- Should `keywordWeight`, `vectorWeight`, or `rrfK` become environment-specific experiment settings?
- What minimum article count is needed before tuning becomes meaningful?
