# 검색 평가용 쿼리 라벨링 가이드

이 문서는 Sigak의 검색 품질을 비교하기 위한 작은 평가 작업지입니다.
Elasticsearch keyword search, Qdrant vector search, hybrid search가 같은 검색어에서 어떤 결과를 내는지 비교하려면 먼저 사람이 판단한 "정답 article"이 필요합니다.

아직 튜닝 결과가 아닙니다.
목적은 RRF weight, candidate limit, embedding model, Neo4j graph context 같은 변경을 할 때 검색 품질이 좋아졌는지 나빠졌는지 숫자로 비교할 수 있게 만드는 것입니다.

## HTML 라벨링 도구

Markdown 표는 기준을 설명하기 위한 문서이고, 실제 입력은 정적 HTML 도구를 우선 사용합니다.

```text
docs/search-evaluation/labeling.html
```

권장 흐름:

```text
labeling.html 열기
-> article catalog 확인
-> query별 label 작성
-> labels JSON 다운로드
-> benchmark runner에서 JSON 사용
```

브라우저 작업 중에는 `localStorage` draft가 저장되지만, 최종 보관은 export한 JSON 파일을 기준으로 합니다.

## Frozen catalog export

Seed article 5개는 도구 smoke check에는 충분하지만 검색 품질 benchmark에는 부족합니다.
더 많은 article을 라벨링하려면 PostgreSQL의 API-ready article을 frozen catalog JSON으로 export한 뒤 HTML 도구에서 가져옵니다.

```bash
cd backend
./gradlew bootRun --args='search-catalog-export --output=../experiments/datasets/raw/articles.catalog.json --limit=50'
```

그 다음 `docs/search-evaluation/labeling.html`을 열고 `카탈로그 JSON 가져오기` 버튼으로 `experiments/datasets/raw/articles.catalog.json`을 선택합니다.

이 catalog는 Elasticsearch, Qdrant, Neo4j가 아니라 PostgreSQL source of truth에서 나온 public/API-ready article 기준입니다.
Projection store는 benchmark 대상 검색 결과를 만들 때 비교하고, 라벨링 catalog의 원천으로는 사용하지 않습니다.

라벨링 화면의 article 목록은 기본적으로 catalog 원래 순서를 사용합니다.
ID 순서가 더 편하면 article 관련도 영역의 정렬 select에서 `ID 낮은 순`을 선택합니다.

## 1. 이 파일에서 해야 하는 일

사용자는 검색어를 보고 각 article이 얼마나 관련 있는지 판단합니다.
Codex는 이 라벨을 바탕으로 나중에 Recall@5, MRR@5, Top1 hit, latency 같은 지표를 계산하는 benchmark runner를 만들 수 있습니다.

오늘 필요한 작업은 다음과 같습니다.

1. 실제 사용자가 검색할 법한 query를 10-15개 적습니다.
2. 각 query에 대해 `strong`, `acceptable`, `not relevant` article을 고릅니다.
3. 왜 그렇게 판단했는지 한 줄 근거를 남깁니다.
4. 애매한 query는 바로 삭제하지 말고 `needs-review`로 남깁니다.

## 2. 라벨 뜻

| 라벨 | 뜻 | 예시 판단 |
| --- | --- | --- |
| `strong` | 이 query에 대해 반드시 상위에 나와야 하는 핵심 정답 article입니다. | `graph rag failure` query라면 Graph RAG 실패 모드를 다루는 article `4`가 strong입니다. |
| `acceptable` | 직접 정답은 아니지만, 같이 읽으면 도움이 되는 article입니다. | `production ai risk` query라면 AI toolchain 보안 article `3`과 agent evaluation article `1`이 acceptable일 수 있습니다. |
| `not relevant` | 검색 의도와 거의 관련이 없거나, 결과에 나와도 도움이 적은 article입니다. | `postgres vector index` query에서 Kubernetes 지원 정책 article `5`는 보통 not relevant입니다. |

라벨링할 때 중요한 점은 "검색어에 단어가 들어갔는가"만 보지 않는 것입니다.
사용자가 그 검색어를 입력한 이유를 상상하고, 그 의도를 만족하는 article인지 판단합니다.

## 3. 현재 article 카탈로그

현재 seed data 기준으로 사용할 수 있는 article은 다음과 같습니다.
나중에 collection으로 article이 늘어나면 이 표도 같이 갱신합니다.

| ID | 제목 | 카테고리 | 주요 topic | 한국어 요약 |
| --- | --- | --- | --- | --- |
| `1` | OpenAI Releases Agent Evaluation Toolkit | AI | LLM agents, evaluation, production AI | OpenAI가 multi-step agent workflow를 평가하기 위한 도구를 공개했다는 내용입니다. production AI와 agent 평가에 관련 있습니다. |
| `2` | PostgreSQL Adds Native Vector Index Improvements | DATA | PostgreSQL, vector search, database indexing | PostgreSQL의 vector index 성능 개선 내용입니다. RAG, vector search, database indexing과 관련 있습니다. |
| `3` | Critical Package Registry Attack Targets AI Toolchains | SECURITY | supply chain security, package registry, AI tooling | AI 개발 도구를 설치하는 개발 환경을 노린 package registry 공격입니다. 공급망 보안과 AI toolchain 위험에 관련 있습니다. |
| `4` | New Research Maps Failure Modes in Graph RAG Systems | CS_RESEARCH | Graph RAG, knowledge graphs, retrieval quality | Graph RAG 시스템의 실패 유형과 평가 기준을 다룬 연구입니다. graph, RAG, retrieval quality에 관련 있습니다. |
| `5` | Kubernetes Project Updates Long-Term Support Policy | INFRA_CLOUD | Kubernetes, release policy, platform operations | Kubernetes 장기 지원 정책 변경입니다. 운영, 인프라, production platform 관리에 관련 있습니다. |

최근 collection demo 이후 article `6`이 로컬 DB에 생긴 적이 있지만, seed migration에는 포함되어 있지 않습니다.
benchmark label은 먼저 seed article `1-5` 기준으로 시작하고, 재현 가능한 export가 생긴 뒤 collected article까지 확장합니다.

## 4. 라벨링 예시

아래 예시는 이미 smoke check에서 사용한 query입니다.
이 예시는 "어떤 식으로 판단을 적으면 되는지"를 보여주기 위한 시작점입니다.

| Query | 의도 | Strong | Acceptable | Not relevant 예시 | 판단 근거 | 상태 |
| --- | --- | --- | --- | --- | --- | --- |
| `graph` | Graph RAG나 knowledge graph 기반 검색 관련 article을 찾고 싶다. | `4` | `1`, `2` | `5` | article `4`가 Graph RAG 실패 모드를 직접 다룬다. article `1`은 evaluation, article `2`는 retrieval/vector 기반과 간접 관련이 있다. | `reviewed` |
| `security` | AI 개발 또는 운영 환경의 보안 위험을 찾고 싶다. | `3` | `5`, `1` | `2` | article `3`이 package registry 공격을 직접 다룬다. article `5`는 운영 리스크, article `1`은 production AI workflow와 간접 관련이 있다. | `reviewed` |
| `vector` | vector search나 vector index 관련 article을 찾고 싶다. | `2` | `4`, `1` | `5` | article `2`가 PostgreSQL vector index 개선을 직접 다룬다. article `4`는 retrieval quality, article `1`은 AI evaluation과 간접 관련이 있다. | `reviewed` |

## 5. 현재 smoke result

아래 결과는 검색 튜닝 결과가 아니라, 현재 hybrid search가 어떤 순서로 결과를 냈는지 기록한 것입니다.
라벨과 검색 결과를 비교하면 "어떤 query에서 hybrid가 잘하거나 못하는지"를 나중에 판단할 수 있습니다.

| Query | 기대되는 핵심 article | 현재 hybrid smoke result | 해석 |
| --- | --- | --- | --- |
| `graph` | `4` | `4, 3, 1, 2, 5` | article `4`가 1등이라 현재 결과가 좋습니다. |
| `security` | `3` | `5, 3, 1, 4, 2` | article `3`이 나오긴 하지만 1등은 article `5`입니다. vector 영향이 너무 강한지 나중에 확인할 후보입니다. |
| `vector` | `2` | `2, 4, 1, 3, 5` | article `2`가 1등이라 현재 결과가 좋습니다. |

## 6. 내가 채우면 되는 라벨링 작업표

아래 표를 채우면 됩니다.
처음부터 완벽할 필요는 없습니다.
판단이 애매하면 `메모`에 왜 애매한지 적고 `상태`를 `needs-review`로 두면 됩니다.

추천 query 예시는 "현재 article 1-5 중에서 실제로 찾고 싶은 주제"를 기준으로 잡았습니다.
마음에 들지 않는 query는 바꿔도 됩니다.

| Query | 검색 의도 | Strong article IDs | Acceptable article IDs | Not relevant article IDs | 메모 | 상태 |
| --- | --- | --- | --- | --- | --- | --- |
| `agent evaluation` | LLM agent 평가와 production AI 품질 검증을 찾는다. | `1` | `4` | `5` | article `1`이 직접 정답이다. article `4`는 평가 기준이라는 점에서 간접 관련이 있다. | `reviewed` |
| `production ai risk` | production AI를 운영할 때 생기는 위험이나 검증 이슈를 찾는다. |  |  |  |  | `needs_user_label` |
| `supply chain attack ai tools` | AI toolchain과 package registry 보안 사고를 찾는다. |  |  |  |  | `needs_user_label` |
| `postgres vector search` | PostgreSQL 기반 vector search나 index 개선을 찾는다. |  |  |  |  | `needs_user_label` |
| `rag search quality` | RAG나 retrieval 품질 평가 방법을 찾는다. |  |  |  |  | `needs_user_label` |
| `graph rag failure` | Graph RAG 실패 유형과 평가 기준을 찾는다. |  |  |  |  | `needs_user_label` |
| `kubernetes support policy` | Kubernetes 장기 지원 정책이나 운영 계획을 찾는다. |  |  |  |  | `needs_user_label` |
| `platform operations` | production platform 운영과 인프라 의사결정을 찾는다. |  |  |  |  | `needs_user_label` |
| `database indexing for rag` | RAG 시스템을 위한 database indexing이나 retrieval infra를 찾는다. |  |  |  |  | `needs_user_label` |
| `ai tooling security` | AI 개발 도구와 관련된 보안 위험을 찾는다. |  |  |  |  | `needs_user_label` |
| `knowledge graph retrieval` | knowledge graph와 retrieval을 함께 다루는 article을 찾는다. |  |  |  |  | `needs_user_label` |
| `release policy infrastructure` | release policy가 운영 계획에 미치는 영향을 찾는다. |  |  |  |  | `needs_user_label` |

## 7. 작성 예시

아래처럼 적으면 됩니다.

| Query | 검색 의도 | Strong article IDs | Acceptable article IDs | Not relevant article IDs | 메모 | 상태 |
| --- | --- | --- | --- | --- | --- | --- |
| `graph rag failure` | Graph RAG가 어떤 상황에서 실패하는지 알고 싶다. | `4` | `1`, `2` | `5` | article `4`가 실패 유형을 직접 설명한다. article `1`은 evaluation이라는 점에서 간접 관련, article `2`는 retrieval infra라 약하게 관련 있다. | `reviewed` |

이 예시에서 article `5`를 `not relevant`로 둔 이유는 Kubernetes 지원 정책이 Graph RAG 실패 유형을 직접 설명하지 않기 때문입니다.
다만 query가 `production reliability`라면 article `5`는 acceptable 또는 strong이 될 수 있습니다.
즉, article 자체가 좋고 나쁨이 아니라 "query 의도와 맞는가"를 판단합니다.

## 8. Benchmark runner 실행

라벨 JSON을 `experiments/datasets/labels/`에 넣은 뒤 아래 명령으로 검색 평가 report를 만든다.
이 단계는 HTML 도구에서 라벨을 만드는 작업과 별개로, 현재 public search API 결과가 사람이 만든 정답지와 얼마나 맞는지 확인한다.

```bash
node experiments/scripts/retrieval-benchmark.mjs \
  --labels=experiments/datasets/labels/search-labels.api-ready-2026-06-02.2026-06-02.json \
  --base-url=http://localhost:8080 \
  --output-dir=experiments/results/retrieval/latest
```

결과는 `experiments/results/retrieval/latest/report.md`에서 확인한다.

- `Top1 Strong Hit`: 첫 번째 결과가 strong article인지 확인한다.
- `Recall@5`: Top5 안에 strong 또는 acceptable article이 얼마나 들어왔는지 확인한다.
- `MRR@5`: Top5 안에서 첫 strong article이 몇 번째에 등장했는지 확인한다.
- `LatencyMs`: public search API 요청 1회에 걸린 시간을 기록한다.

2026-06-02 local smoke에서는 deterministic embedding mode로 Elasticsearch/Qdrant projection을 재생성한 뒤 3개 reviewed query를 평가했다.
결과는 `Top1 Strong Hit=0.6666666666666666`, `Recall@5=0.8333333333333334`, `MRR@5=0.8333333333333334`, `Average LatencyMs=43.333333333333336`였다.
현재 label 수가 작고 deterministic embedding mode를 사용했으므로, 이 report는 검색 품질의 최종 결론이 아니라 평가 파이프라인이 재현 가능하게 작동하는지 확인하는 smoke 결과로 해석한다.

## 9. Search mode 설명

기본 runner는 public article search API의 현재 검색 모드를 평가한다.
공정하게 keyword/vector/hybrid/public을 나란히 비교하려면 `--systems` 옵션을 붙인 comparison runner를 사용한다.

| Mode | 언제 발생하는가 | 기대하는 확인 신호 |
| --- | --- | --- |
| `HYBRID` | Elasticsearch와 Qdrant가 모두 사용 가능할 때 | `lastSearch.mode = HYBRID` |
| `KEYWORD_ONLY` | Qdrant가 꺼져 있거나 vector search가 실패했을 때 | `lastSearch.mode = KEYWORD_ONLY`, `vectorFailed = true` |
| `VECTOR_ONLY` | Elasticsearch가 꺼져 있거나 keyword search가 실패했을 때 | `lastSearch.mode = VECTOR_ONLY`, `keywordFailed = true` |
| `POSTGRES_FALLBACK` | Elasticsearch와 Qdrant가 모두 실패했을 때 | `lastSearch.mode = POSTGRES_FALLBACK` |

Comparison runner 명령:

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

Comparison runner의 system 뜻:

| System | 어디서 결과를 만드는가 | 실패/Degrade 해석 |
| --- | --- | --- |
| `keyword` | internal evaluation endpoint가 Elasticsearch keyword 후보만 사용 | keyword search 자체가 실패하면 해당 run은 `FAILED` |
| `vector` | internal evaluation endpoint가 Qdrant vector 후보만 사용 | embedding 또는 Qdrant search가 실패하면 해당 run은 `FAILED` |
| `hybrid` | internal evaluation endpoint가 keyword와 vector 후보를 RRF로 결합 | keyword/vector 중 하나라도 실패하면 strict `HYBRID` run은 `FAILED`; 다른 path로 degrade하지 않음 |
| `public` | 기존 `GET /api/articles?query=...` 호출 | 사용자 경험용 결과라 `KEYWORD_ONLY`, `VECTOR_ONLY`, `POSTGRES_FALLBACK`으로 degrade할 수 있음 |

헷갈리기 쉬운 점:

- `hybrid`와 `public`은 같은 것이 아니다.
- `hybrid`는 실험용 strict system이다.
- `public`은 실제 사용자가 보게 되는 API 동작이다.
- `public`은 절대 `/api/internal/search-evaluation/retrieval-runs`에 보내지 않는다.
- report에서 strict `hybrid`가 실패하고 `public`이 성공할 수 있다. 이 경우 public fallback이 사용자 경험을 지킨 것이며, strict hybrid 품질과는 따로 해석한다.
- `public`의 mode/fallback metadata는 public API 호출 직후 internal last-search metrics를 읽어 기록한다. 따라서 비교 runner는 로컬에서 단독으로 실행하고, 동시에 다른 검색 요청을 보내지 않는 것이 좋다.

## 10. Benchmark 지표

| 지표 | 쉬운 설명 | 왜 필요한가 |
| --- | --- | --- |
| `Top1 Strong Hit` | 1등 결과가 strong article이면 성공 | 사용자가 첫 결과만 볼 때 품질을 보여줍니다. |
| `Recall@5` | 상위 5개 안에 strong/acceptable article이 얼마나 들어갔는지 | 검색 결과 목록이 정답 후보를 놓치지 않는지 봅니다. |
| `MRR@5` | 첫 strong article이 몇 번째에 나왔는지 | 정답이 빨리 나올수록 점수가 높습니다. |
| `LatencyMs` | 검색에 걸린 시간 | 품질이 좋아도 너무 느리면 제품 경험이 나빠집니다. |

정확한 계산 결과는 runner가 생성하는 `metrics.summary.json`, `metrics.by-query.json`, `report.md`에 기록된다.

## 11. 다음 평가 질문

- Hybrid search가 keyword-only보다 top-1 또는 top-3 관련성을 실제로 개선하는가?
- `security`처럼 넓은 query에서 vector search 영향이 오히려 결과를 흐리는가?
- `keywordWeight`, `vectorWeight`, `rrfK`를 실험 설정으로 분리해야 하는가?
- article 수가 몇 개 이상 되어야 튜닝 결과를 믿을 수 있는가?
- Neo4j graph projection을 붙였을 때 article detail 관계 설명은 검색 품질 평가와 어떻게 연결되는가?
