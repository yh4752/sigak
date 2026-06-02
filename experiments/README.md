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

`results/`는 benchmark runner가 생긴 뒤 만든다.

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

현재 benchmark runner는 아직 없다.
label JSON을 만든 뒤 keyword/vector/hybrid 검색 결과를 비교하는 runner를 추가한다.
