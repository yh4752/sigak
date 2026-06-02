# Search Catalog Export Design

## Summary

Sigak의 retrieval benchmark는 사람이 판단한 query/article relevance label을 기준으로 keyword, vector, hybrid search를 비교해야 한다.
현재 정적 라벨링 도구는 seed article catalog를 내장하고 있지만, article 5개만으로는 Recall@5, MRR@5, Top1 hit 같은 지표를 포트폴리오 근거로 삼기 어렵다.

이 설계는 PostgreSQL에 저장된 API-ready article을 라벨링 도구가 import할 수 있는 frozen catalog JSON으로 export하는 작은 backend command를 추가한다.
목표는 benchmark runner를 만들기 전에, 더 많은 article을 안정적으로 라벨링할 수 있는 재현 가능한 dataset artifact를 만드는 것이다.

## Current State

- `docs/search-evaluation/labeling.html`은 정적 HTML 라벨링 도구다.
- 라벨링 도구는 `version = 1` catalog JSON을 import할 수 있다.
- 내장 seed catalog는 article `1-5`만 포함한다.
- public article API는 `PUBLISHED` 상태이고 current enrichment가 있는 article만 노출한다.
- `ArticleService.getArticles(null)`은 같은 API-ready article response를 반환한다.
- 검색 projection store인 Elasticsearch, Qdrant, Neo4j는 source of truth가 아니며 catalog export의 기준이 되면 안 된다.
- `collection-run` command runner 패턴이 이미 있어, local command 실행/파싱/출력/exit 테스트 구조를 참고할 수 있다.

## Design Goal

목표:

- PostgreSQL source of truth에서 API-ready article을 읽어 frozen catalog JSON을 생성한다.
- 생성된 JSON은 `docs/search-evaluation/labeling.html`의 catalog import schema와 호환되어야 한다.
- catalog artifact는 `experiments/datasets/raw/` 아래에 저장할 수 있어야 한다.
- 구현은 local development command로 제한하고, public/internal HTTP API를 늘리지 않는다.
- benchmark runner, metric 계산, research UI는 이 설계 범위에 포함하지 않는다.

깨지 말아야 할 계약:

- public article API 응답 shape를 변경하지 않는다.
- 라벨링 도구의 기존 catalog schema를 변경하지 않는다.
- Elasticsearch, Qdrant, Neo4j를 catalog source로 사용하지 않는다.
- raw content 전문이나 private/internal failure data를 catalog에 넣지 않는다.

## Approach Review

### Recommended: Spring Boot command runner

예상 명령:

```bash
cd backend
./gradlew bootRun --args='search-catalog-export --output=../experiments/datasets/raw/articles.catalog.json --limit=50'
```

장점:

- PostgreSQL source of truth와 Spring Boot의 API-ready article 경계를 그대로 사용한다.
- portfolio reviewer가 재현할 수 있는 명령이 명확하다.
- 새 HTTP endpoint, auth 정책, frontend UI 없이 dataset artifact를 만들 수 있다.
- 기존 `collection-run` command runner와 비슷한 테스트 구조를 쓸 수 있다.

단점:

- backend에 command runner 파일이 추가된다.
- command 실행 시 Spring Boot application context가 필요하다.

판단:

- 현재 목적이 "사용자 기능"이 아니라 "평가 artifact 생성"이므로 command runner가 가장 작은 경계다.

### Alternative: Internal HTTP endpoint

예상 형태:

```text
GET /api/internal/search-evaluation/catalog
```

장점:

- `curl`이나 browser로 확인하기 쉽다.
- 나중에 `/research` UI가 생기면 재사용할 수 있다.

단점:

- 지금 필요하지 않은 API surface가 늘어난다.
- internal endpoint 문서, 보안, 운영 경계를 추가로 다뤄야 한다.

판단:

- research dashboard가 실제로 catalog를 HTTP로 읽어야 하는 시점까지 미룬다.

### Alternative: Public API response 변환 script

예상 형태:

```bash
curl http://localhost:8080/api/articles > articles.json
```

장점:

- backend code 변경이 거의 없다.

단점:

- 라벨링 catalog schema로 변환하는 별도 script나 수동 작업이 필요하다.
- catalog ID, generatedAt, source 같은 dataset metadata가 흐려진다.

판단:

- 빠른 임시 확인에는 가능하지만, 포트폴리오용 재현 명령으로는 부족하다.

### Alternative: SQL/psql export

장점:

- 빠르고 backend code가 없다.

단점:

- API-ready filtering, current enrichment, topic 정렬 같은 도메인 규칙을 SQL에 중복한다.
- public API와 catalog의 article 기준이 갈라질 수 있다.

판단:

- PostgreSQL은 source of truth이지만, export 기준은 Spring Boot domain boundary를 통과하는 편이 안전하다.

## Architecture

새 command는 `search-catalog-export`라는 독립 실행 단위로 둔다.
Spring Boot application이 시작될 때 command가 있으면 catalog를 export하고 exit code를 반환한다.
명령이 없으면 아무 일도 하지 않는다.

권장 구성:

```text
search/evaluation/catalog
├── SearchCatalogExportCommand
├── SearchCatalogExportCommandParser
├── SearchCatalogExportCommandRunner
├── SearchCatalogExportCommandFormatter
├── SearchCatalogExportService
├── SearchCatalogExportJsonWriter
└── dto/model classes
```

서비스 책임:

- `SearchCatalogExportCommandParser`
  - `search-catalog-export` 명령 여부를 판단한다.
  - `--output=...`, `--limit=...`, `--catalog-id=...` 옵션을 파싱한다.
- `SearchCatalogExportService`
  - `ArticleService.getArticles(null)`을 호출해 API-ready article response를 가져온다.
  - limit을 적용한다.
  - catalog JSON model로 변환한다.
- `SearchCatalogExportJsonWriter`
  - parent directory를 생성한다.
  - JSON을 pretty format으로 쓴다.
- `SearchCatalogExportCommandRunner`
  - parser/service/writer/formatter를 연결한다.
  - 성공하면 exit `0`, 실패하면 exit `1`을 반환한다.

`ArticleRepository`에 새 조회를 추가하지 않는 것을 우선한다.
`ArticleService.getArticles(null)`이 이미 public API-ready article response를 반환하므로, catalog export는 그 응답을 dataset schema로 변환하는 역할만 맡는다.

## Command Contract

필수 옵션:

- `--output=<path>`

선택 옵션:

- `--limit=<number>`
  - 기본값: `50`
  - 허용 범위: `1` 이상
- `--catalog-id=<id>`
  - 기본값: `api-ready-YYYY-MM-DD`
  - 공백이면 실패한다.

명령 예시:

```bash
cd backend
./gradlew bootRun --args='search-catalog-export --output=../experiments/datasets/raw/articles.catalog.json'
```

```bash
cd backend
./gradlew bootRun --args='search-catalog-export --output=../experiments/datasets/raw/articles.catalog.json --limit=30 --catalog-id=api-ready-2026-06-02'
```

성공 출력 예시:

```text
Search catalog export completed
catalogId=api-ready-2026-06-02
articleCount=30
output=../experiments/datasets/raw/articles.catalog.json
```

실패 조건:

- `--output`이 없다.
- `--limit`이 숫자가 아니다.
- `--limit`이 `1`보다 작다.
- `--catalog-id`가 공백이다.
- API-ready article이 0개다.
- output file write에 실패한다.

## Catalog JSON Contract

라벨링 도구와 호환되는 version `1` schema를 유지한다.

```json
{
  "version": 1,
  "catalogId": "api-ready-2026-06-02",
  "generatedAt": "2026-06-02T00:00:00Z",
  "source": "postgres-api-ready",
  "articles": [
    {
      "id": 1,
      "title": "OpenAI Releases Agent Evaluation Toolkit",
      "category": "AI",
      "topics": ["LLM agents", "evaluation", "production AI"],
      "summaryKo": "OpenAI가 multi-step agent workflow를 평가하기 위한 도구를 공개했다는 내용입니다.",
      "whyItMattersKo": "production AI에서 agent 평가가 중요해지고 있음을 보여준다.",
      "publishedAt": "2026-05-01T09:00:00Z",
      "source": "OpenAI",
      "url": "https://example.com/articles/openai-agent-evals"
    }
  ]
}
```

필수 field:

- `version`
- `catalogId`
- `generatedAt`
- `source`
- `articles`
- `articles[].id`
- `articles[].title`
- `articles[].category`
- `articles[].topics`
- `articles[].summaryKo`

선택 field:

- `articles[].whyItMattersKo`
- `articles[].publishedAt`
- `articles[].source`
- `articles[].url`

`summaryKo`, `whyItMattersKo` field 이름은 실제 값이 항상 한국어라는 보장이 아니라, 현재 라벨링 도구가 기대하는 display field 이름이다.
이번 설계에서는 라벨링 도구 schema drift를 피하기 위해 기존 이름을 유지한다.

## Data Rules

포함 기준:

- public article API에 노출 가능한 article
- `processingStatus = PUBLISHED`
- current enrichment가 있는 article
- source, topic, summary, why-it-matters가 response로 변환 가능한 article

정렬 기준:

- `ArticleService.getArticles(null)`이 반환하는 현재 public list 순서를 따른다.
- 현재 repository 기준으로 importance score desc, publishedAt desc, id asc 순서가 된다.

제외 기준:

- raw content 전문
- collection failure event
- processing 중간 상태 article
- current enrichment가 없는 article
- Elasticsearch/Qdrant/Neo4j projection payload
- benchmark label이나 검색 결과

## Experiments Directory

catalog export 구현과 함께 최소 실험 디렉터리 구조를 만든다.

```text
experiments/
├── README.md
└── datasets/
    ├── raw/
    ├── labels/
    └── processed/
```

`experiments/README.md`에는 다음을 기록한다.

- catalog export 명령
- 라벨링 도구 import 흐름
- label JSON 저장 위치
- benchmark runner는 아직 pending임

초기 export artifact는 다음 경로를 우선한다.

```text
experiments/datasets/raw/articles.catalog.json
```

실제 catalog JSON을 commit할지는 구현 시점에 article 내용과 재현성을 보고 판단한다.
source article이 외부 URL 기반으로 수집된 경우, 전문 raw content 없이 public response 수준의 metadata와 summary만 포함한다.

## Testing And Verification

구현 시 필요한 테스트:

- parser test
  - command가 아니면 `null`
  - `--output` 필수
  - `--limit` 기본값과 override
  - invalid limit 실패
  - blank catalog ID 실패
- service test
  - ArticleService response가 catalog article field로 변환된다.
  - limit이 적용된다.
  - article이 0개면 실패한다.
- writer test
  - parent directory를 생성한다.
  - JSON file을 쓴다.
- runner test
  - 성공 시 output line과 exit `0`
  - parser/service/write 실패 시 error line과 exit `1`

구현 후 smoke verification:

```bash
cd backend
./gradlew test --tests 'com.sigak.search.evaluation.catalog.*'
./gradlew test
./gradlew check
```

실제 export smoke:

```bash
cd backend
./gradlew bootRun --args='search-catalog-export --output=../experiments/datasets/raw/articles.catalog.json --limit=50'
```

JSON 확인:

```bash
cd ..
node -e "const fs=require('fs'); const c=JSON.parse(fs.readFileSync('experiments/datasets/raw/articles.catalog.json','utf8')); if(c.version!==1||!Array.isArray(c.articles)||c.articles.length===0) process.exit(1); console.log(c.catalogId, c.articles.length)"
```

라벨링 도구 import는 사용자가 브라우저에서 확인하거나, local file browser automation 정책이 허용되는 환경에서만 자동 확인한다.
현재 Codex in-app browser는 local `file://` 접근 자동화가 차단된 적이 있으므로, 자동 검증으로 과장하지 않는다.

## Trade-offs

이 설계가 얻는 것:

- API-ready article 기준을 중복 구현하지 않는다.
- 라벨링 도구와 benchmark runner 사이에 고정된 dataset artifact가 생긴다.
- 검색 품질 metric을 만들기 전에 사람이 검토할 article pool을 확보한다.
- public/internal API surface를 늘리지 않는다.

이 설계가 미루는 것:

- benchmark runner
- Recall@5, MRR@5, Top1 hit 계산
- `/research` 화면
- DB-backed labeling workflow
- LLM 자동 라벨링
- catalog 생성 스케줄링

주요 리스크:

- collected article 수가 부족하면 catalog가 여전히 작을 수 있다.
- `summaryKo` field 이름과 실제 summary 언어가 어긋날 수 있다.
- `ArticleService.getArticles(null)`에 public list 정렬 정책이 바뀌면 catalog 순서도 바뀐다.

대응:

- catalog JSON에는 `catalogId`, `generatedAt`, `source`를 남기고, command output에는 `articleCount`를 출력해 특정 시점 artifact로 다룬다.
- 라벨링 도구 schema와 benchmark runner schema를 같은 version `1` 계약으로 유지한다.
- article 수가 부족하면 먼저 controlled collection을 실행하고 projection rebuild와 별개로 catalog를 다시 export한다.

## Out Of Scope

- Neo4j projection
- graph-aware article detail
- retrieval benchmark runner
- metric report generation
- search tuning
- endpoint-based catalog download
- frontend research dashboard
- automatic catalog refresh
- multi-user labeling
- authentication or authorization

## Next Step

이 spec이 승인되면 `docs/superpowers/plans/2026-06-02-search-catalog-export.md`에 구현 계획을 작성한다.
구현은 TDD로 parser/service/writer/runner 단위 테스트를 먼저 만들고, 그 다음 command runner를 연결한다.
