# Sigak Experiments

이 디렉터리는 Sigak의 검색/RAG 평가 artifact를 보관한다.
현재 단계의 목표는 대규모 benchmark가 아니라, 작은 query set과 사람이 검토한 relevance label로 keyword, vector, hybrid search를 재현 가능하게 비교하는 것이다.

## Directory Structure

```text
experiments/
├── datasets/
│   ├── raw/        API-ready article catalog 같은 원천 평가 artifact
│   ├── labels/     사람이 작성한 query/article relevance label JSON
│   └── processed/  benchmark runner가 사용할 가공 dataset
└── results/        benchmark 실행 결과
```

`results/`에는 benchmark runner가 만든 run, metric, report artifact를 저장한다.

## Catalog Export Command

PostgreSQL의 API-ready article을 라벨링 도구가 import할 수 있는 frozen catalog JSON으로 export한다.
명령을 실행하려면 backend가 사용할 PostgreSQL 연결 설정이 준비되어 있어야 한다. 로컬에서는 `infra/docker-compose.yml`의 `postgres` 서비스를 먼저 띄우면 된다.

```bash
cd backend
./gradlew bootRun --args='search-catalog-export --output=../experiments/datasets/raw/articles.catalog.json --limit=50'
```

옵션:

- `--output`: catalog JSON을 저장할 경로다.
- `--limit`: export할 최대 article 수다. 기본값은 `50`이다.
- `--catalog-id`: catalog 식별자다. 생략하면 `api-ready-YYYY-MM-DD` 형태를 사용한다.

Catalog 기준은 Elasticsearch, Qdrant, Neo4j projection store가 아니라 PostgreSQL source of truth에서 공개 API에 노출 가능한 API-ready article이다.
현재 sample artifact는 2026-06-02 local smoke에서 생성한 `experiments/datasets/raw/articles.catalog.json`이며, article 6개를 포함한다.

## Labeling Flow

```text
catalog export
-> docs/search-evaluation/labeling.html 열기
-> 카탈로그 JSON 가져오기
-> query별 relevance label 작성
-> label JSON 다운로드
-> experiments/datasets/labels/ 아래에 보관
```

## Retrieval Benchmark Runner

Label JSON을 만든 뒤 public article search API를 기준으로 smoke benchmark를 실행한다.
이 명령은 backend가 실행 중이고 Elasticsearch/Qdrant projection이 현재 검색 설정에 맞게 준비되어 있다는 전제를 가진다.

```bash
node experiments/scripts/retrieval-benchmark.mjs \
  --labels=experiments/datasets/labels/search-labels.api-ready-2026-06-02.2026-06-02.json \
  --base-url=http://localhost:8080 \
  --output-dir=experiments/results/retrieval/latest
```

생성되는 파일:

- `runs.public.json`: public article API query별 검색 결과 article ID와 latency
- `metrics.by-query.json`: query별 Top1 Strong Hit, Recall@5, MRR@5, latency
- `metrics.summary.json`: 전체 평균 metric
- `report.md`: 사람이 읽기 위한 요약 report

2026-06-02 local smoke에서는 deterministic embedding mode로 Elasticsearch/Qdrant projection을 재생성한 뒤 3개 reviewed query를 평가했다.
결과는 `experiments/results/retrieval/latest/report.md`에 저장됐고, `Top1 Strong Hit=0.6666666666666666`, `Recall@5=0.8333333333333334`, `MRR@5=0.8333333333333334`, `Average LatencyMs=43.333333333333336`였다.
이 값은 검색 품질 결론이 아니라 benchmark runner와 artifact 생성 흐름이 end-to-end로 동작한다는 smoke 근거다.

## Retrieval System Comparison Runner

Keyword, vector, strict hybrid, public search behavior를 같은 label JSON으로 비교하려면 `--systems`를 지정한다.
이 비교는 internal evaluation endpoint를 사용하므로 backend를 실행할 때 `SIGAK_INTERNAL_SEARCH_EVALUATION_ENABLED=true`를 설정해야 한다.

```bash
cd backend
SIGAK_INTERNAL_SEARCH_EVALUATION_ENABLED=true ./gradlew bootRun
```

다른 터미널에서 repository root로 돌아와 runner를 실행한다.

```bash
node experiments/scripts/retrieval-benchmark.mjs \
  --labels=experiments/datasets/labels/search-labels.api-ready-2026-06-02.2026-06-02.json \
  --base-url=http://localhost:8080 \
  --output-dir=experiments/results/retrieval/latest \
  --systems=keyword,vector,hybrid,public \
  --k=5 \
  --limit=20
```

생성되는 파일:

- `runs.keyword.json`: internal strict keyword run
- `runs.vector.json`: internal strict vector run
- `runs.hybrid.json`: internal strict hybrid run
- `runs.public.json`: 기존 public article API run
- `metrics.by-query.json`: query/system별 품질, 실패, degrade metric
- `metrics.by-system.json`: system별 macro metric, effective metric, 실패율, degrade율
- `metrics.comparison.json`: system 비교 요약과 작은 label set 경고
- `report.md`: strict system과 public behavior의 차이를 설명하는 비교 report

중요한 구분:

- `keyword`, `vector`, `hybrid`는 `POST /api/internal/search-evaluation/retrieval-runs`에서 생성한다.
- `public`은 internal evaluation endpoint로 보내지 않는다.
- `public`은 기존 `GET /api/articles?query=...` 결과를 기록한다.
- strict `hybrid`는 keyword 또는 vector 중 하나라도 실패하면 실패로 기록하고 degrade하지 않는다.
- public search는 사용자 경험을 위해 `KEYWORD_ONLY`, `VECTOR_ONLY`, `POSTGRES_FALLBACK`으로 degrade할 수 있다.
- 따라서 strict `hybrid`와 `public` 결과가 다를 수 있으며, 이는 오류가 아니라 의도된 비교 기준이다.
- `public`의 mode/fallback metadata는 public API 호출 직후 internal last-search metrics endpoint에서 읽는다. 로컬 runner를 단독으로 실행하는 전제에서는 유용하지만, 동시에 다른 검색 요청이 들어오는 환경에서는 부정확할 수 있다.

현재 label set은 아직 작다.
비교 report의 수치는 검색 품질 결론이 아니라, keyword/vector/hybrid/public 결과를 같은 artifact 구조로 비교할 수 있다는 smoke 근거로 먼저 해석한다.

## Graph-Aware Evaluation Runner

Public article detail의 graph context가 related article baseline보다 어떤 설명 정보를 더 주는지 확인하려면 `--include-graph-context`를 사용한다.
이 runner는 검색 성능을 새로 평가하는 도구가 아니라, public search top-k에서 들어간 article detail이 graph reason/topic으로 관련 맥락을 얼마나 설명하는지 보는 smoke evaluation이다.

```bash
node experiments/scripts/retrieval-benchmark.mjs \
  --labels=experiments/datasets/labels/search-labels.api-ready-2026-06-02.2026-06-02.json \
  --base-url=http://localhost:8080 \
  --output-dir=experiments/results/graph/latest \
  --systems=public \
  --include-graph-context \
  --k=5 \
  --limit=20
```

생성되는 graph artifact:

- `graph-context.runs.json`: public search top-k source article, public detail baseline, graph context row
- `graph-context.metrics.by-query.json`: query별 search miss, related baseline coverage, graph reason coverage, topic coverage
- `graph-context.metrics.summary.json`: macro graph metric, failure/empty context rate, warning
- `report.md`: 기존 retrieval comparison report 뒤에 graph-aware evaluation section을 append

주의:

- 이 평가는 검색 성능 평가가 아니라 article detail의 설명 가능성 평가다.
- 현재 3-query/6-article label set은 smoke only다.
- `averageGraphLatencyMs`는 public API round-trip이고 순수 Neo4j query latency가 아니다.
- graph reason은 stored projection reason이며 독립적으로 검증된 factual explanation이 아니다.

2026-06-04 local smoke에서는 ES `26`개 article, Qdrant `26`개 vector, Neo4j `26`개 article node와 `10`개 `RELATED_TO` relationship을 rebuild한 뒤 3개 query를 평가했다.
Graph Context Coverage@5와 Graph Reasoned Coverage@5는 각각 `0.3333333333333333`이었고, graph context failure rate와 empty context rate는 모두 `0`이었다.
이 값은 graph-aware evaluation runner와 artifact 생성 흐름이 재현 가능하게 동작한다는 smoke 근거이며, graph 품질 결론은 더 큰 label set 이후에만 다룬다.
