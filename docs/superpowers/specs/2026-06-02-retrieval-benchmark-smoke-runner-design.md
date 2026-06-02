# Retrieval Benchmark Smoke Runner Design

## Summary

Sigak의 검색 품질 평가는 사람이 만든 relevance label을 기준으로 검색 결과를 재현 가능하게 비교해야 한다.
이번 설계의 목표는 회사/연구실에서 쓰는 정보검색 평가 구조를 작게 가져오되, 혼자 유지할 수 있는 smoke benchmark runner를 만드는 것이다.

첫 버전은 `experiments/datasets/labels/`의 label JSON을 정답지로 사용하고, public article search API의 현재 검색 결과를 평가한다.
처음부터 keyword/vector/hybrid 3-way 비교, 통계 검정, 대규모 label set을 만들지 않는다.
대신 `qrels -> run -> metrics -> report` 구조를 작게 고정해, 나중에 query와 비교 mode가 늘어나도 갈아엎지 않게 한다.

## Current State

- `docs/search-evaluation/labeling.html`은 query/article relevance label을 만들 수 있는 정적 HTML 도구다.
- `experiments/datasets/raw/articles.catalog.json`은 PostgreSQL API-ready article에서 export한 frozen catalog다.
- `experiments/datasets/labels/search-labels.api-ready-2026-06-02.2026-06-02.json`은 첫 3-query smoke label set이다.
- label JSON은 `strong`, `acceptable`, `not_relevant` relevance를 가진다.
- `reviewed` 상태 query만 평가에 사용할 수 있다.
- public search API `GET /api/articles?query=...`는 현재 설정에서 hybrid search 결과를 반환한다.
- backend internal metrics endpoint는 mode/fallback/latency breakdown을 볼 수 있지만, benchmark runner의 첫 버전은 public API round-trip latency를 우선 기록한다.

## Design Goal

목표:

- label JSON을 읽어 `reviewed` query만 평가한다.
- 각 query로 public search API를 호출하고, 반환된 article ID 순서를 run으로 저장한다.
- `Top1 Strong Hit`, `Recall@5`, `MRR@5`, `LatencyMs`를 계산한다.
- 결과를 JSON과 Markdown report로 저장한다.
- report에는 metric 뜻, 실험 조건, 현재 label/article 수의 한계를 함께 기록한다.

깨지 말아야 할 계약:

- public article API 응답 shape를 바꾸지 않는다.
- benchmark를 위해 Elasticsearch, Qdrant, Neo4j projection을 primary data처럼 취급하지 않는다.
- label JSON schema를 갑자기 바꾸지 않는다.
- 3-query smoke 결과를 검색 품질 개선의 최종 근거처럼 과장하지 않는다.

명시적 비범위:

- keyword-only/vector-only/hybrid 3-way 비교 자동화
- nDCG, Precision@k, 통계 검정
- p50/p95 latency 반복 측정
- DB-backed evaluation UI
- Graph-aware retrieval 평가

위 항목들은 runner 구조가 안정화되고 label set이 커진 뒤 확장한다.

## Approach Review

### Recommended: lightweight experiment script

예상 형태:

```bash
node experiments/scripts/retrieval-benchmark.mjs \
  --labels=experiments/datasets/labels/search-labels.api-ready-2026-06-02.2026-06-02.json \
  --base-url=http://localhost:8080 \
  --output-dir=experiments/results/retrieval/latest
```

장점:

- 제품 코드와 실험 코드를 분리할 수 있다.
- label/run/report artifact를 다루기 쉽다.
- 혼자 실행하고 디버깅하기 쉽다.
- Node.js는 이미 HTML/static tooling 검증에 쓰고 있어 추가 환경 부담이 작다.

단점:

- public API를 호출하므로 backend와 필요한 검색 projection이 먼저 떠 있어야 한다.
- 처음에는 현재 public search mode만 평가한다.

판단:

- 현재 목적은 "검색 평가 체계의 첫 재현 가능한 단위"이므로 가벼운 script가 맞다.

### Alternative: Spring Boot command runner

장점:

- internal search service를 직접 호출해 keyword/vector/hybrid run을 더 공정하게 만들 수 있다.
- Kotlin test로 domain logic을 검증하기 쉽다.

단점:

- 첫 smoke benchmark에는 backend 코드 추가 부담이 크다.
- 평가 실험 artifact 생성이 제품 backend에 더 깊게 들어간다.

판단:

- keyword/vector/hybrid 3-way 비교가 필요해지는 다음 단계에서 검토한다.

### Alternative: full research tooling

예:

- Python `pytrec_eval`
- `ir-measures`
- TREC qrels/run text format

장점:

- 연구실 표준과 더 가깝다.
- nDCG, MAP 같은 metric 확장이 쉽다.

단점:

- 현재 3-query/6-article smoke 단계에는 과하다.
- 설치와 포맷 변환 부담이 생긴다.

판단:

- portfolio MVP에서는 개념을 차용하되 직접 계산으로 시작한다.
- label set이 커지고 metric이 많아질 때 도입한다.

## Evaluation Model

### Qrels

`qrels`는 사람이 만든 정답지다.
Sigak에서는 label JSON을 qrels로 본다.

Relevance mapping:

| Label | Grade | Meaning |
| --- | ---: | --- |
| `strong` | 2 | 핵심 정답. 반드시 상위에 나와야 하는 article |
| `acceptable` | 1 | 직접 정답은 아니지만 도움이 되는 article |
| `not_relevant` | 0 | 검색 의도와 관련이 낮은 article |
| omitted in `reviewed` query | 0 | 명시적으로 라벨링하지 않은 article. smoke benchmark에서는 관련 없음으로 취급 |

첫 runner에서는 grade `2`와 `1`을 positive relevance로 보고, `MRR@5`는 grade `2`만 사용한다.

### Run

`run`은 검색 시스템이 반환한 결과 목록이다.
첫 버전은 public API 호출 결과를 그대로 저장한다.

Run item example:

```json
{
  "query": "graph rag failure",
  "rankedArticleIds": [4, 1, 2, 5, 6],
  "latencyMs": 42
}
```

나중에 keyword/vector/hybrid 비교가 추가되면 같은 query에 대해 `system = keyword`, `system = vector`, `system = hybrid` run을 따로 저장한다.

## Metric Definitions

### Top1 Strong Hit

첫 번째 결과가 `strong` article이면 `1`, 아니면 `0`이다.

의미:

- 사용자가 첫 결과만 봤을 때 핵심 정답을 바로 만나는지 확인한다.
- 제품 경험 관점에서 직관적이다.

예:

```text
Strong: #4
Result: #4, #1, #2, #5, #6
Top1 Strong Hit = 1
```

```text
Strong: #4
Result: #1, #4, #2, #5, #6
Top1 Strong Hit = 0
```

### Recall@5

Top5 안에 `strong` 또는 `acceptable` article이 얼마나 포함됐는지 계산한다.

공식:

```text
Recall@5 = Top5 안에서 찾은 positive article 수 / label에 있는 positive article 수
```

Positive article은 `strong + acceptable`이다.

의미:

- 검색 결과가 관련 후보를 놓치지 않는지 본다.
- RAG나 research flow에서는 첫 결과 하나보다 "관련 context를 충분히 모았는가"가 중요할 수 있다.

### MRR@5

Top5 안에서 첫 번째 `strong` article이 몇 번째에 있는지 계산한다.

공식:

```text
MRR@5 = 1 / 첫 strong article rank
```

Top5 안에 `strong` article이 없으면 `0`이다.

의미:

- 핵심 정답이 얼마나 빨리 나오는지 본다.
- Recall@5가 높아도 strong article이 5등에 있으면 사용자 경험은 약할 수 있다.

### LatencyMs

query별 public API 호출에 걸린 시간을 ms 단위로 기록한다.

첫 버전:

- query마다 1회 측정
- report에는 평균 latency와 query별 latency를 기록

다음 확장:

- warm-up 후 반복 실행
- p50, p95 계산
- backend internal metrics의 `totalElapsedMs`, `keywordElapsedMs`, `embeddingElapsedMs`, `vectorElapsedMs`와 비교

## Output Artifacts

첫 버전은 아래 파일을 생성한다.

```text
experiments/results/retrieval/latest/
├── runs.hybrid.json
├── metrics.summary.json
├── metrics.by-query.json
└── report.md
```

`runs.hybrid.json`:

- 입력 label file path
- base URL
- evaluated query 목록
- query별 ranked article IDs
- query별 latency

`metrics.by-query.json`:

- query
- intent
- strong article IDs
- acceptable article IDs
- result IDs
- top1StrongHit
- recallAt5
- mrrAt5
- latencyMs

`metrics.summary.json`:

- evaluated query count
- catalog ID
- macro average Top1 Strong Hit
- macro average Recall@5
- macro average MRR@5
- average latencyMs
- generatedAt

`report.md`:

- 실험 목적
- label set 크기와 catalog 크기
- metric 설명
- summary table
- query별 result table
- 한계와 다음 단계

## Runner Contract

필수 옵션:

- `--labels=<path>`
- `--base-url=<url>`
- `--output-dir=<path>`

선택 옵션:

- `--k=<number>`
  - 기본값: `5`
  - 첫 버전은 `5`만 문서에서 사용한다.

실패 조건:

- label JSON을 읽을 수 없다.
- label version이 `1`이 아니다.
- `reviewed` query가 없다.
- query에 `strong` 또는 `acceptable` label이 없다.
- public API 호출이 실패한다.
- API 응답이 article ID를 포함하지 않는다.
- output directory 생성 또는 파일 쓰기에 실패한다.

## Blog Notes

기술 블로그에는 다음 내용을 반드시 포함한다.

- 검색 품질을 말하려면 먼저 사람이 만든 정답지(qrels)가 필요하다는 점
- `strong`, `acceptable`, `not_relevant`를 metric 계산에 어떻게 사용했는지
- `Top1 Strong Hit`, `Recall@5`, `MRR@5`, `LatencyMs`가 각각 보는 관점
- 왜 `Recall@5`와 `MRR@5`가 서로 다른 질문에 답하는지
- 현재 3-query/6-article 결과는 smoke benchmark이며, 품질 개선을 주장하기에는 부족하다는 한계
- keyword/vector/hybrid 비교는 fallback smoke로 대체하지 않고, 다음 단계에서 공정한 run 생성 방식으로 확장한다는 점

블로그용 한 줄 요약:

```text
처음부터 거대한 평가 시스템을 만들지 않고, qrels/run/metrics/report 구조를 작게 가져와 사람이 만든 label JSON 기준으로 검색 결과를 재현 가능하게 평가했다.
```

## Verification Plan

구현 후 최소 검증:

- label JSON parser unit test
- metric calculator unit test
  - Top1 hit success/failure
  - Recall@5 partial/full/empty
  - MRR@5 rank 1/rank 2/not found
- report writer test
- local smoke:
  - backend 실행
  - `node experiments/scripts/retrieval-benchmark.mjs ...`
  - `experiments/results/retrieval/latest/report.md` 생성 확인

이번 설계 문서 자체의 검증:

- 구현 범위가 first smoke runner로 제한돼 있는지 확인한다.
- keyword/vector/hybrid 3-way 비교를 첫 단계로 요구하지 않는지 확인한다.
- metric 정의가 label semantics와 충돌하지 않는지 확인한다.
- 블로그에 들어갈 설명 포인트가 남아 있는지 확인한다.
