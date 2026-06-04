# Catalog Expansion Design

날짜: 2026-06-05

## 목표

Sigak의 retrieval/graph-aware 평가 catalog를 기존 `6`개 article smoke artifact에서 현재 PostgreSQL의 API-ready article 기준 catalog로 확장한다.
이번 단계의 목표는 새 수집 기능을 만드는 것이 아니라, 이미 DB에 존재하는 API-ready article을 frozen catalog artifact로 다시 export하고 라벨링/benchmark 확장의 기준점을 만드는 것이다.

최종 흐름은 다음과 같다.

```txt
현재 PostgreSQL API-ready article
-> 새 frozen catalog JSON export
-> catalog schema/count/ID 검증
-> labeling.html에서 새 catalog import
-> 10-15개 query label 작성
-> retrieval/graph-aware benchmark 재실행
```

## 현재 상태

- 기존 frozen catalog:
  - `experiments/datasets/raw/articles.catalog.json`
  - `catalogId=api-ready-2026-06-02`
  - article count `6`
- 기존 label:
  - `experiments/datasets/labels/search-labels.api-ready-2026-06-02.2026-06-02.json`
  - reviewed query `3`개
- 기존 smoke result:
  - `experiments/results/retrieval/latest/`
  - `experiments/results/graph/latest/`
- 최근 graph-aware smoke에서는 projection rebuild 기준으로 ES/Qdrant/Neo4j가 `26`개 article을 다뤘다.
- catalog export command는 이미 구현되어 있다.

```bash
cd backend
./gradlew bootRun --args='search-catalog-export --output=../experiments/datasets/raw/articles.catalog.json --limit=50'
```

## 비판적 재검토

### A안의 장점

현재 DB의 API-ready article을 재-export하는 방식은 가장 작고 안정적인 다음 단계다.
새 source 수집, real enrichment, relation extraction을 추가하지 않아도 catalog 규모를 키울 수 있다.
또한 export command가 이미 `ArticleService.getArticles(null)`을 사용하므로 public API-ready 기준과 dataset 기준이 갈라질 가능성이 낮다.

### A안의 위험

1. 실제 DB 상태가 기대와 다를 수 있다.
   - 최근 projection smoke에서는 `26`개 article이 확인됐지만, 현재 로컬 DB가 초기화되었거나 다른 상태라면 export 결과가 다시 `6`개 이하일 수 있다.
   - 따라서 "26개로 확장됐다"고 가정하지 않고 export 후 실제 count를 검증해야 한다.

2. 기존 6-article smoke artifact를 덮어쓸 수 있다.
   - 기존 result는 runner와 graph-aware evaluation이 end-to-end로 동작했다는 baseline이다.
   - 같은 파일을 덮어쓰면 이전 label/result와 catalog의 `catalogId`, article set이 어긋날 수 있다.

3. catalogId가 label JSON과 맞지 않으면 benchmark가 오염된다.
   - label JSON은 특정 catalog의 article ID 집합을 전제로 한다.
   - 새 catalog로 label을 만들 때는 새 `catalogId`를 사용하고, 기존 2026-06-02 label과 섞지 않는다.

4. article 수만 늘어도 품질 평가는 자동으로 좋아지지 않는다.
   - query와 relevance label을 사람이 검토해야 metric이 의미를 가진다.
   - catalog 확장은 평가 기반을 만드는 일이지, 검색 품질 결론을 내는 일이 아니다.

5. 현재 catalog는 article-level 평가 artifact다.
   - chunk-level retrieval, RAG generation 평가, concept/relation human review까지 포함하지 않는다.
   - deterministic chunk ID나 DATA_CARD는 후속 작업으로 남긴다.

### 결론

A안을 유지한다.
다만 다음 guardrail을 문서화하고 실행 단계에서 지킨다.

- 기존 `articles.catalog.json`을 바로 덮어쓰지 않는다.
- 새 catalog는 날짜가 들어간 별도 파일로 만든다.
- export 후 실제 article count, duplicate ID, 필수 필드, catalogId를 검증한다.
- 기존 `api-ready-2026-06-02` label/result는 smoke baseline으로 보존한다.
- 새 label은 새 catalogId에서만 작성한다.

## Review Feedback 반영

이번 설계는 다음 다섯 가지 주의사항을 실행 gate로 반영한다.

1. 실제 article 수는 export 결과로만 확정한다.
   - projection smoke의 `26`개는 기대 사례일 뿐이다.
   - catalog export와 JSON 검증이 출력한 `articleCount`를 최종 근거로 삼는다.
2. label과 catalog 불일치를 파일명과 catalogId로 동시에 막는다.
   - 새 catalog, 새 label, 새 benchmark output은 모두 `api-ready-2026-06-05` 계열 이름을 사용한다.
   - 기존 `api-ready-2026-06-02` label은 새 catalog benchmark에 사용하지 않는다.
3. article 수 확장은 metric 품질의 필요조건일 뿐 충분조건이 아니다.
   - `10-15`개 reviewed query와 relevance label을 추가해야 MVP evidence로 해석할 수 있다.
   - 라벨 작성 전 benchmark 재실행은 하지 않는다.
4. 기존 baseline은 보존한다.
   - `experiments/results/*/latest`는 확장 benchmark가 검증되기 전까지 덮어쓰지 않는다.
   - 확장 결과는 `expanded/` 아래에 먼저 생성한다.
5. 이번 범위는 article-level catalog 확장이다.
   - chunk-level dataset, relation human review, DATA_CARD는 후속 단계에서 다룬다.
   - 이번 문서는 그 작업들의 전제 조건인 article-level frozen catalog를 안정화한다.

## Artifact Naming

새 catalog artifact는 아래 이름을 기본값으로 한다.

```txt
experiments/datasets/raw/articles.catalog.api-ready-2026-06-05.json
```

권장 catalog ID:

```txt
api-ready-2026-06-05
```

새 label artifact는 사용자가 라벨링 도구에서 다운로드한 뒤 아래 이름으로 보관한다.

```txt
experiments/datasets/labels/search-labels.api-ready-2026-06-05.2026-06-05.json
```

확장 benchmark 결과는 기존 `latest` smoke와 분리한다.

```txt
experiments/results/retrieval/expanded/
experiments/results/graph/expanded/
```

`latest` alias를 갱신할지는 확장 catalog/label/benchmark가 smoke 검증을 통과한 뒤 결정한다.

## Baseline Compatibility Matrix

catalog, label, result artifact는 같은 `catalogId` 계열끼리만 묶는다.

| Artifact | 기존 smoke baseline | 확장 catalog 작업 |
| --- | --- | --- |
| Catalog ID | `api-ready-2026-06-02` | `api-ready-2026-06-05` |
| Catalog file | `experiments/datasets/raw/articles.catalog.json` | `experiments/datasets/raw/articles.catalog.api-ready-2026-06-05.json` |
| Label file | `experiments/datasets/labels/search-labels.api-ready-2026-06-02.2026-06-02.json` | `experiments/datasets/labels/search-labels.api-ready-2026-06-05.2026-06-05.json` |
| Retrieval result | `experiments/results/retrieval/latest/` | `experiments/results/retrieval/expanded/` |
| Graph result | `experiments/results/graph/latest/` | `experiments/results/graph/expanded/` |
| 해석 | runner smoke baseline | larger-catalog MVP evidence 후보 |

잘못된 조합:

- `api-ready-2026-06-02` label을 `api-ready-2026-06-05` catalog benchmark에 사용하지 않는다.
- `api-ready-2026-06-05` label로 만든 결과를 기존 `latest` baseline이라고 부르지 않는다.
- article count가 늘었다는 이유만으로 기존 3-query label 결과와 직접 품질 비교하지 않는다.

## Scope

### 포함

- 현재 DB의 API-ready article을 새 frozen catalog JSON으로 export한다.
- 새 catalog JSON의 기본 정합성을 검증한다.
- labeling tool에서 import할 수 있는 상태인지 확인한다.
- 사용자와 Codex의 라벨링 역할을 분리한다.
- 후속 benchmark 명령과 결과 저장 위치를 확정한다.

### 제외

- 새 RSS/arXiv source 추가
- collection source 품질 개선
- real enrichment 구현
- chunk-level dataset 생성
- concept/relation human review tool 추가
- 기존 6-article smoke label/result 삭제
- 검색 품질 결론 작성

## Data Contract

새 catalog는 기존 `version=1` schema를 유지한다.

```json
{
  "version": 1,
  "catalogId": "api-ready-2026-06-05",
  "generatedAt": "2026-06-05T00:00:00Z",
  "source": "postgres-api-ready",
  "articles": [
    {
      "id": 1,
      "title": "OpenAI Releases Agent Evaluation Toolkit",
      "category": "AI",
      "topics": ["LLM agents", "evaluation"],
      "summaryKo": "OpenAI introduced a toolkit for evaluating agent behavior.",
      "whyItMattersKo": "Agent evaluation matters for production reliability.",
      "publishedAt": "2026-05-01T10:00:00Z",
      "source": "OpenAI",
      "url": "https://example.com/article"
    }
  ]
}
```

검증할 필드:

- `version`은 `1`이다.
- `catalogId`는 `api-ready-2026-06-05`다.
- `source`는 `postgres-api-ready`다.
- `articles.length`는 실제 export 결과를 기록한다.
- `articles[].id`는 positive integer이고 중복이 없다.
- `title`, `category`, `summaryKo`는 non-blank string이다.
- `topics`는 string array다.

## Dataset Scale Gate

export 후 article count에 따라 다음 행동을 다르게 한다.

| 실제 article count | 판정 | 다음 행동 |
| --- | --- | --- |
| `0` | 실패 | PostgreSQL seed/migration 또는 API-ready filtering 상태를 먼저 확인한다. |
| `1-19` | 확장 부족 | catalog는 남기되, benchmark 라벨링으로 넘어가기 전에 collection/source curation을 계획한다. |
| `20-29` | MVP 최소 후보 | `10-15`개 query label을 작성하고 retrieval/graph benchmark를 재실행할 수 있다. |
| `30+` | 더 나은 MVP 후보 | query 유형별 라벨을 조금 더 균형 있게 만들 수 있다. |

이 gate는 품질 주장 gate가 아니다.
품질 주장은 label 수, query 다양성, benchmark 결과, failure/degrade율을 함께 본 뒤에만 다룬다.

## Execution Flow

### 1. PostgreSQL 상태 확인

로컬 PostgreSQL이 실행 중이어야 한다.

```bash
docker compose -f infra/docker-compose.yml up -d --pull never postgres
docker compose -f infra/docker-compose.yml exec -T postgres pg_isready -U sigak -d sigak
```

기대:

```txt
/var/run/postgresql:5432 - accepting connections
```

### 2. 새 catalog export

```bash
cd backend
./gradlew bootRun --args='search-catalog-export --output=../experiments/datasets/raw/articles.catalog.api-ready-2026-06-05.json --limit=50 --catalog-id=api-ready-2026-06-05'
```

2026-06-05 로컬 실행 결과:

```txt
Search catalog export completed
catalogId=api-ready-2026-06-05
articleCount=41
output=../experiments/datasets/raw/articles.catalog.api-ready-2026-06-05.json
```

이전 projection smoke의 `26`개는 기대 사례였고, 이번 catalog export의 실제 관측값은 `41`개다.
향후 로컬 DB 상태가 달라지면 실행 결과의 숫자를 그대로 기록한다.
실제 article count가 `20` 미만이면 확장 catalog로는 부족하다고 기록하고, 추가 수집/source curation을 다음 작업으로 넘긴다.

### 3. JSON 정합성 검증

repository root에서 실행한다.

```bash
node -e "
const fs = require('fs');
const path = 'experiments/datasets/raw/articles.catalog.api-ready-2026-06-05.json';
const catalog = JSON.parse(fs.readFileSync(path, 'utf8'));
const ids = catalog.articles.map((article) => article.id);
const duplicateIds = ids.filter((id, index) => ids.indexOf(id) !== index);
const invalid = [];
if (catalog.version !== 1) invalid.push('version');
if (catalog.catalogId !== 'api-ready-2026-06-05') invalid.push('catalogId');
if (catalog.source !== 'postgres-api-ready') invalid.push('source');
if (!Array.isArray(catalog.articles) || catalog.articles.length === 0) invalid.push('articles');
if (duplicateIds.length > 0) invalid.push('duplicateIds=' + [...new Set(duplicateIds)].join(','));
for (const article of catalog.articles) {
  if (!Number.isInteger(article.id) || article.id < 1) invalid.push('invalidId');
  if (typeof article.title !== 'string' || article.title.trim() === '') invalid.push('title');
  if (typeof article.category !== 'string' || article.category.trim() === '') invalid.push('category');
  if (!Array.isArray(article.topics)) invalid.push('topics');
  if (typeof article.summaryKo !== 'string' || article.summaryKo.trim() === '') invalid.push('summaryKo');
}
if (invalid.length > 0) {
  console.error('catalog-invalid', [...new Set(invalid)]);
  process.exit(1);
}
console.log(JSON.stringify({
  catalogId: catalog.catalogId,
  articleCount: catalog.articles.length,
  firstId: ids[0],
  lastId: ids[ids.length - 1]
}, null, 2));
"
```

출력 형태:

```json
{
  "catalogId": "api-ready-2026-06-05",
  "articleCount": 41,
  "firstId": 3,
  "lastId": 16
}
```

`articleCount`, `firstId`, `lastId`는 command 실행 후 출력된 실제 값으로 기록한다.

### 4. Labeling tool import smoke

브라우저에서 다음 파일을 연다.

```txt
docs/search-evaluation/labeling.html
```

작업:

- `카탈로그 JSON 가져오기`로 새 catalog 파일을 선택한다.
- 화면 상단 또는 article count 표시가 새 catalog count와 일치하는지 확인한다.
- category filter와 article sort가 정상 동작하는지 확인한다.
- 아직 label을 작성하지 않아도 된다.

이 단계는 사용자 수동 확인이 필요하다.

### 5. Query 후보 준비

확장 catalog 기준으로 우선 `10-15`개 query를 만든다.
query는 너무 일반적인 단어만 쓰지 않고, 실제 검색 의도를 드러내야 한다.

권장 query 유형:

- topic query: `graph rag`, `vector search`, `agent evaluation`
- category query: `security`, `infrastructure`, `database`
- mixed intent query: `production ai evaluation`, `retrieval quality`, `supply chain attack`
- Korean query smoke: `그래프 RAG`, `벡터 검색`, `AI 보안`

처음에는 `10-15`개면 충분하다.
`30-50`개 label은 v0.1 이후 portfolio comparison 단계에서 늘린다.

### 6. Label 작성 gate

새 label JSON은 다음 조건을 만족해야 benchmark 입력으로 사용할 수 있다.

- `catalogId`가 `api-ready-2026-06-05`다.
- `catalogArticleCount`가 새 catalog의 article count와 같다.
- reviewed query가 최소 `10`개다.
- 각 reviewed query는 `strong` 또는 `acceptable` article을 최소 하나 이상 가진다.
- label에 등장하는 모든 `articleId`가 새 catalog에 존재한다.
- 애매한 query는 `검토 필요` 상태로 남기고 benchmark 입력에서 제외한다.

라벨을 늘리는 작업은 사용자의 도메인 판단이 들어가야 한다.
Codex는 query 후보, consistency check, benchmark 실행을 맡지만 relevance 판정 자체를 자동으로 확정하지 않는다.

## Role Split

### Codex가 맡는 일

- 새 catalog export 명령 실행
- JSON schema/count/duplicate 검증
- 새 artifact 파일명과 catalogId 관리
- labeling.html import smoke를 위한 체크리스트 제공
- 사용자가 저장한 label JSON의 catalog consistency 검증
- retrieval/graph-aware benchmark 재실행
- 결과를 `STATUS`, `ROADMAP`, dev-log, topic queue에 기록

### 사용자가 맡는 일

- labeling.html에서 새 catalog가 보기 좋은지 수동 확인
- query 후보 중 실제 평가할 query 선택
- 각 query에 대해 strong/acceptable/not relevant label 판단
- 다운로드한 label JSON을 `experiments/datasets/labels/` 아래에 저장
- 애매한 label 기준을 Codex와 함께 조정

## Future Expansion Boundary

이번 작업 이후 확장할 수 있는 주제는 다음과 같이 분리한다.

1. 추가 article 확보
   - collection/source curation으로 catalog를 `30-50`개 이상으로 키운다.
   - 이때 failure diagnostics와 duplicate skip 결과도 함께 기록한다.
2. chunk-level dataset
   - article-level retrieval benchmark가 안정화된 뒤 deterministic chunk ID를 도입한다.
   - chunk 단위 Recall/nDCG는 별도 dataset version으로 다룬다.
3. relation human review
   - graph-aware detail의 relation reason 품질을 사람이 검토하는 label set을 별도로 만든다.
   - 현재 search relevance label과 섞지 않는다.
4. research packaging
   - `docs/research/DATA_CARD.md`, graph-aware insight report, README result table은 확장 catalog와 label이 검증된 뒤 작성한다.

후속 확장은 모두 같은 원칙을 따른다.
PostgreSQL은 source of truth이고, Elasticsearch/Qdrant/Neo4j는 rebuildable projection store다.

## Benchmark 연결

새 label JSON이 생기면 retrieval comparison을 실행한다.

```bash
node experiments/scripts/retrieval-benchmark.mjs \
  --labels=experiments/datasets/labels/search-labels.api-ready-2026-06-05.2026-06-05.json \
  --base-url=http://localhost:8080 \
  --output-dir=experiments/results/retrieval/expanded \
  --systems=keyword,vector,hybrid,public \
  --k=5 \
  --limit=20
```

Graph-aware evaluation도 별도 output directory에 실행한다.

```bash
node experiments/scripts/retrieval-benchmark.mjs \
  --labels=experiments/datasets/labels/search-labels.api-ready-2026-06-05.2026-06-05.json \
  --base-url=http://localhost:8080 \
  --output-dir=experiments/results/graph/expanded \
  --systems=public \
  --include-graph-context \
  --k=5 \
  --limit=20
```

이 benchmark는 labeling이 끝난 뒤 실행한다.
catalog export 직후에는 실행하지 않는다.

## Acceptance Criteria

이번 catalog expansion 준비가 완료됐다고 말하려면 다음이 필요하다.

- 새 catalog JSON이 `experiments/datasets/raw/articles.catalog.api-ready-2026-06-05.json`에 생성됐다.
- 새 catalog의 `catalogId`가 `api-ready-2026-06-05`다.
- JSON 정합성 검증이 통과했다.
- 실제 article count가 기록됐다.
- 기존 `api-ready-2026-06-02` catalog/label/result를 삭제하거나 덮어쓰지 않았다.
- labeling.html에서 새 catalog import가 수동 확인됐다.
- 이후 label JSON과 benchmark output 위치가 확정됐다.

## Failure Handling

- export 결과가 0개면 command 실패로 보고 PostgreSQL seed/migration 상태부터 확인한다.
- export 결과가 20개 미만이면 현재 DB가 확장 catalog로 부족하다고 기록하고, 추가 collection/source curation 설계로 넘어간다.
- JSON 검증이 실패하면 label 작업을 시작하지 않는다.
- labeling.html import가 실패하면 catalog schema mismatch를 먼저 수정한다.
- 기존 label JSON의 catalogId가 새 catalogId와 다르면 benchmark에 사용하지 않는다.

## Documentation Updates

실행 후 다음 문서를 갱신한다.

- `docs/STATUS.md`
  - 새 catalog count와 검증 결과
- `docs/STATUS.ko.md`
  - 한국어 동일 요약
- `experiments/README.md`
  - 새 확장 catalog와 기존 smoke baseline의 차이
- `docs/blog/2026-06-05-dev-log.md`
  - 실제 실행한 명령과 검증 결과
- `docs/blog/topic-queue.md`
  - "작은 smoke catalog에서 확장 catalog로 넘어가는 평가 데이터 관리" 주제 후보

`docs/ROADMAP.md`는 scope나 다음 단계가 바뀐 경우에만 갱신한다.
이번 작업은 기존 roadmap의 "larger catalog and labels" 항목을 실행하는 것이므로, 단순 실행만으로는 roadmap 구조를 바꾸지 않는다.

## 다음 단계

1. implementation plan 작성
2. 새 catalog export 실행
3. JSON 정합성 검증
4. 사용자 labeling.html import 확인
5. label query 후보 작성
6. 사용자가 label JSON 저장
7. benchmark 재실행 및 report 갱신
