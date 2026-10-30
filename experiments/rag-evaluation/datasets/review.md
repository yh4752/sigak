# 운영 문서 RAG 평가 질문 검토

이 문서는 AI가 작성한 초안입니다. `DRAFT`는 사람 검토가 끝나지 않았다는 뜻입니다.
질문의 자연스러움, 정답의 정확성, 인용 근거의 충분성, 답변 불가 판정을 확인해 주세요.
질문 표현이나 정답이 바뀌면 datasetVersion을 올리고 이전 평가 결과를 보존합니다.
자동 검사는 사람 검토를 대신하지 않으며, 이 명령은 reviewStatus를 바꾸지 않습니다.

Corpus: `sigak-ops-2026-10-10`, Git: `7e80d35ca2e53b8954224acdd9a93f49620de336`, dirty: `True`

## 원문 해시

- `README.md`: `8c1417cd7bca0fdb852863c2f804c1891c2765c34e6a954a0e5eacc04fb1d4ee`
- `docs/DEMO_FLOW.md`: `65733ddf562965a606ba216da84d5f9949bdf4572a9f7251dde8ded816527d36`
- `docs/API_SPEC.md`: `89a2abc1586e341d501e4aebe3b5710ba07b64255c0054d4ae11395ca04ae2cf`
- `experiments/README.md`: `8d722b13c472ec3c81cf0c7f7b9e252b829dc1541bdbf28d49a06f27d19965fa`
- `backend/README.md`: `c24c2c8db2ff5457f18577d571e5555cc4c73d43ea82fb2bd4d8d98bd9679d41`
- `ai/README.md`: `3cf0ef1ab0a6cf52567e32d21c617dd1b78a985df4684f4944591ed4ea725967`
- `docs/STATUS.md`: `91ba510aef13b9cc8de178f6596248be58b06afc25775c0bd86817be2b9743c9`

## arch-public-boundary

- [ ] 사람이 검토 완료

- **LEXICAL_ALIGNED / ko / DRAFT**: Spring Boot와 FastAPI 중 frontend의 public API boundary는 무엇인가?
- **SEMANTIC_PARAPHRASE / en / DRAFT**: Which service must the browser call, and where are AI-specific requests routed afterward?

예상 답변: Frontend는 Spring Boot만 호출하고 Spring Boot가 embedding·enrichment 같은 AI/RAG 작업에 한해 FastAPI를 호출한다.

근거 `arch-public-boundary-1` — `README.md` / Sigak > Architecture

> Spring Boot is the public API boundary. The frontend calls Spring Boot, and Spring Boot calls FastAPI only for AI/RAG-specific work such as embeddings and on-demand enrichment.

SHA-256: `38c951a913e83b19a20c4a383bb0a732b192b3c01d200d576a5ded838a07ea82`


## arch-source-truth

- [ ] 사람이 검토 완료

- **LEXICAL_ALIGNED / ko / DRAFT**: PostgreSQL과 Elasticsearch·Qdrant·Neo4j의 저장소 역할은 어떻게 다른가?
- **SEMANTIC_PARAPHRASE / en / DRAFT**: If every search index vanished, which database would still hold authoritative article data?

예상 답변: PostgreSQL이 source of truth이고 Elasticsearch, Qdrant, Neo4j는 재생성 가능한 projection store다.

근거 `arch-source-truth-1` — `README.md` / Sigak > Architecture

> PostgreSQL is the source of truth; Elasticsearch, Qdrant, and Neo4j are rebuildable projection stores.

SHA-256: `01253e7e835bfd147d4d0731482b50284ac9288b41a4bf55c18eb01648990cf4`


## arch-experiment-boundary

- [ ] 사람이 검토 완료

- **LEXICAL_ALIGNED / ko / DRAFT**: `experiments/`에는 무엇을 저장하며 제품 API와 어떤 경계를 유지하는가?
- **SEMANTIC_PARAPHRASE / en / DRAFT**: Why are benchmark outputs kept beside the app instead of changing the user-facing search response?

예상 답변: 실험 디렉터리는 재현 가능한 평가 artifact를 보관하고 제품의 공개 응답 계약은 바꾸지 않는다.

근거 `arch-experiment-boundary-1` — `README.md` / Sigak > Architecture

> - `experiments/`: retrieval and graph-aware smoke evaluation artifacts.

SHA-256: `00cdde1f3aa5cab80481d946d4d94dc7f0defe4a5f22116534518c17b642c057`

근거 `arch-experiment-boundary-2` — `README.md` / Sigak > Evaluation Artifacts

> Smoke artifacts are committed to show that the evaluation pipeline is reproducible, not to claim final search quality.

SHA-256: `6b48be3929f5cff7a79f76a672dbb6b2ea611a11fde697ddbcb99213a7832c7c`

근거 `arch-experiment-boundary-3` — `docs/API_SPEC.md` / API Spec > Internal Retrieval Benchmark Run Contract

> - public article response shape remains unchanged

SHA-256: `6f1ea7a6257ad6d83b5ad3d981ec72c2b67171e4f98f5cca4104593bf8fbd6bc`


## arch-stable-response

- [ ] 사람이 검토 완료

- **LEXICAL_ALIGNED / ko / DRAFT**: graph-aware retrieval이 추가돼도 public search response shape는 어떻게 해야 하는가?
- **SEMANTIC_PARAPHRASE / en / DRAFT**: Can the retrieval implementation evolve without forcing article-card clients to adopt a new payload?

예상 답변: 내부 검색 구현이 변해도 public article response shape는 안정적으로 유지한다.

근거 `arch-stable-response-1` — `docs/API_SPEC.md` / API Spec > Search Behavior

> Graph-aware retrieval is a later enhancement. The public search response shape should remain stable when the backend implementation evolves.

SHA-256: `f6a3aac6f3c3c4df961cadc5ea9648a9b228ad840b203e9c901eff9c789bb02c`


## proc-start-services

- [ ] 사람이 검토 완료

- **LEXICAL_ALIGNED / ko / DRAFT**: PostgreSQL, Elasticsearch, Qdrant, Neo4j, AI server를 한 번에 시작하는 명령은?
- **SEMANTIC_PARAPHRASE / ko / DRAFT**: 로컬 검색·그래프·임베딩 의존성을 함께 올리려면 어떤 Compose 절차를 실행해야 하는가?

예상 답변: repository root에서 지정된 docker compose up 명령을 실행하고 ps로 healthy 상태를 확인한다.

근거 `proc-start-services-1` — `docs/DEMO_FLOW.md` / Sigak Local Demo Flow > Prerequisites

> docker compose -f infra/docker-compose.yml up -d --pull never postgres elasticsearch qdrant neo4j ai docker compose -f infra/docker-compose.yml ps postgres elasticsearch qdrant neo4j ai

SHA-256: `49c278ffcf67b8f2fa07e110f5a5ed62df1582514c5b141fbc6d42ae90e48fb9`

근거 `proc-start-services-2` — `docs/DEMO_FLOW.md` / Sigak Local Demo Flow > Prerequisites

> Expected signal: PostgreSQL, Elasticsearch, Qdrant, Neo4j, and AI server are `healthy`.

SHA-256: `2565d9394d992486e48de017a134ecb8ee9d5f6722159e3c0f1fe1d0963b2f1a`


## proc-rebuild-all

- [ ] 사람이 검토 완료

- **LEXICAL_ALIGNED / ko / DRAFT**: Elasticsearch, Qdrant, Neo4j projection rebuild endpoint 세 개는?
- **SEMANTIC_PARAPHRASE / en / DRAFT**: After the backend starts, how do I refresh every derived search and graph store?

예상 답변: 세 internal POST endpoint를 순서대로 호출해 keyword, vector, graph projection을 재구축한다.

근거 `proc-rebuild-all-1` — `README.md` / Sigak > Run Locally

> curl -X POST http://localhost:8080/api/internal/search-projections/articles/rebuild curl -X POST http://localhost:8080/api/internal/search-projections/article-vectors/rebuild curl -X POST http://localhost:8080/api/internal/graph-projections/articles/rebuild

SHA-256: `4c27f97bb0c9d7acd14f2a032088e30070d1ef2ae969363f5b0bd1b3956709bc`


## proc-collection-run

- [ ] 사람이 검토 완료

- **LEXICAL_ALIGNED / ko / DRAFT**: `github-blog` 한 건을 수집하는 internal collection curl 명령은?
- **SEMANTIC_PARAPHRASE / ko / DRAFT**: 선택한 뉴스 소스에서 최대 한 건만 가져와 저장 흐름을 확인하려면 어떤 요청을 보내는가?

예상 답변: `/api/internal/collections/runs`에 sourceIds와 maxArticlesPerSource JSON을 POST한다.

근거 `proc-collection-run-1` — `docs/DEMO_FLOW.md` / Sigak Local Demo Flow > Step 1. Run Selected-Source Collection

> curl -X POST http://localhost:8080/api/internal/collections/runs \ -H 'Content-Type: application/json' \ -d '{"sourceIds":["github-blog"],"maxArticlesPerSource":1}'

SHA-256: `eb4e0d6f2a80dc182a6ae3f030cfd2c308ecab4a7af81dada3a6cfdda59562ce`


## proc-graph-eval

- [ ] 사람이 검토 완료

- **LEXICAL_ALIGNED / ko / DRAFT**: graph-aware evaluation runner에 필요한 `--include-graph-context` 명령은?
- **SEMANTIC_PARAPHRASE / en / DRAFT**: How can I measure explanation evidence on public article detail for the preserved smoke labels?

예상 답변: public system과 graph-context 옵션, label·base URL·output·k·limit를 지정해 Node runner를 실행한다.

근거 `proc-graph-eval-1` — `experiments/README.md` / Sigak Experiments > Graph-Aware Evaluation Runner

> node experiments/scripts/retrieval-benchmark.mjs \ --labels=experiments/datasets/labels/search-labels.api-ready-2026-06-02.2026-06-02.json \ --base-url=http://localhost:8080 \ --output-dir=experiments/results/graph/latest \ --systems=public \ --include-graph-context \ --k=5 \ --limit=20

SHA-256: `d2b9e09e1c700f0fc44facbbbdd803e8e2fdef2e327acf8bf9fd5ce81e8c1828`


## fallback-one-path

- [ ] 사람이 검토 완료

- **LEXICAL_ALIGNED / ko / DRAFT**: Elasticsearch 또는 Qdrant 한쪽만 실패하면 `KEYWORD_ONLY`/`VECTOR_ONLY`로 어떻게 degrade되는가?
- **SEMANTIC_PARAPHRASE / ko / DRAFT**: 검색 후보를 만드는 두 경로 중 하나가 멈추면 서비스는 결과 제공을 어떻게 이어 가는가?

예상 답변: 살아 있는 projection 경로만 사용해 keyword-only 또는 vector-only 결과를 제공한다.

근거 `fallback-one-path-1` — `docs/API_SPEC.md` / API Spec > Search Behavior

> - if one projection path fails, the other path can serve `KEYWORD_ONLY` or `VECTOR_ONLY` results

SHA-256: `f5ba88da3801156c438b16d8e4238fb6b4f3de9637d53d0111f61a53065a62ed`


## fallback-both-paths

- [ ] 사람이 검토 완료

- **LEXICAL_ALIGNED / ko / DRAFT**: Elasticsearch와 Qdrant가 모두 실패하면 PostgreSQL fallback은 어떤 필드를 검색하는가?
- **SEMANTIC_PARAPHRASE / en / DRAFT**: When neither derived retrieval store is available, where does search continue and what article attributes are checked?

예상 답변: PostgreSQL에서 title, sourceExcerpt, primaryCategory, topics를 대소문자 구분 없이 필터링한다.

근거 `fallback-both-paths-1` — `docs/API_SPEC.md` / API Spec > Search Behavior

> - if both projection paths fail, Spring Boot falls back to PostgreSQL field filtering over: - `title` - `sourceExcerpt` - `primaryCategory` - `topics` - fallback search uses case-insensitive keyword matching

SHA-256: `6c32a26b44ec5cf756d45a99f9678e7270e78c8b71327f6f0badfa4322680a02`


## fallback-neo4j

- [ ] 사람이 검토 완료

- **LEXICAL_ALIGNED / ko / DRAFT**: Neo4j가 unavailable이면 `/api/articles/{id}/graph-context` 응답은?
- **SEMANTIC_PARAPHRASE / en / DRAFT**: If graph storage is down, does article detail fail or return a degraded companion payload?

예상 답변: article detail을 깨지 않고 같은 응답 shape에 빈 relatedArticleReasons와 topics를 반환한다.

근거 `fallback-neo4j-1` — `docs/API_SPEC.md` / API Spec > Public Article Graph Context

> - Neo4j context is optional projection data; missing or unavailable graph context returns the same response shape with empty `relatedArticleReasons` and `topics`.

SHA-256: `363c2baffc75629d877b4474f04a7bd6bf60c320bfb97280693959dc39abfe91`


## fallback-transient-fetch

- [ ] 사람이 검토 완료

- **LEXICAL_ALIGNED / ko / DRAFT**: `TRANSIENT_FETCH`와 `retryable=true` 실패는 언제 다시 시도해야 하는가?
- **SEMANTIC_PARAPHRASE / ko / DRAFT**: 일시적인 upstream/network 수집 장애가 기록됐을 때 반복 실행 전에 무엇을 확인해야 하는가?

예상 답변: 강제 프록시를 제거하고 네트워크·Docker·upstream 회복을 확인하거나 기다린 뒤 같은 source를 재실행한다.

근거 `fallback-transient-fetch-1` — `docs/DEMO_FLOW.md` / Sigak Local Demo Flow > Troubleshooting

> - For a fetch failure with `failureKind=TRANSIENT_FETCH` and `retryable=true`, restart the backend without the forced bad proxy or wait for the upstream source/network to recover, then rerun the same source through the internal endpoint or command runner.

SHA-256: `c1e9c214723b26f7a0943a552ee4539aa670d7523c264583f9de016d86bd61be`


## api-bulk-order

- [ ] 사람이 검토 완료

- **LEXICAL_ALIGNED / ko / DRAFT**: `GET /api/articles?ids=1,2,3`의 순서와 누락 규칙은?
- **SEMANTIC_PARAPHRASE / en / DRAFT**: How are duplicate, missing, or non-public IDs handled when related articles are loaded in bulk?

예상 답변: 첫 요청 순서를 유지하고 중복은 첫 항목만 남기며 missing/non-public ID는 생략한다.

근거 `api-bulk-order-1` — `docs/API_SPEC.md` / API Spec > Article Response

> `GET /api/articles?ids=1,2,3` returns API-ready articles matching the requested IDs, preserving the requested order after duplicate IDs and missing/non-public IDs are omitted.

SHA-256: `606e5c07632deb94d17b1a879448e966ff7763b1098be6696c322ddb478c5cac`

근거 `api-bulk-order-2` — `docs/API_SPEC.md` / API Spec > Bulk Article Lookup

> - duplicate IDs are deduplicated while preserving first-seen request order - unknown, draft, or otherwise non-public IDs are omitted

SHA-256: `f978b918395977350eb679700dfaf4eaebe406ed887b87510c10b74fbcfab713`


## api-ids-precedence

- [ ] 사람이 검토 완료

- **LEXICAL_ALIGNED / ko / DRAFT**: `query`와 `ids`가 함께 있으면 어느 파라미터가 우선하는가?
- **SEMANTIC_PARAPHRASE / ko / DRAFT**: 검색어와 명시적 article reload 목록을 동시에 보내면 backend는 어느 의도를 선택하는가?

예상 답변: 명시적 reload 경로인 ids 조회가 우선한다.

근거 `api-ids-precedence-1` — `docs/API_SPEC.md` / API Spec > Bulk Article Lookup

> - if `query` and `ids` are both present, `ids` lookup takes precedence because it is an explicit article reload path

SHA-256: `72268ec7d74fe8352d0f669e4f4e9f5ace0201a829e2c4c13edc936304db0a89`


## api-strict-vs-public

- [ ] 사람이 검토 완료

- **LEXICAL_ALIGNED / ko / DRAFT**: strict `HYBRID`와 `PUBLIC` benchmark의 실패·degrade 규칙 차이는?
- **SEMANTIC_PARAPHRASE / en / DRAFT**: Why can the experiment-mode fusion run fail while the user-visible search still returns results?

예상 답변: strict HYBRID는 한 경로 실패도 실패로 기록하고, PUBLIC은 keyword/vector/PostgreSQL fallback으로 degrade할 수 있다.

근거 `api-strict-vs-public-1` — `docs/API_SPEC.md` / API Spec > Internal Retrieval Benchmark Run Contract

> - strict `HYBRID` fails if keyword or vector retrieval fails; it does not degrade - public search may degrade to `KEYWORD_ONLY`, `VECTOR_ONLY`, or `POSTGRES_FALLBACK`

SHA-256: `49b8d196a60f5602b4420978730082fef33d9fa721b46fa19067504fb2bb4e53`


## api-embedding-contract

- [ ] 사람이 검토 완료

- **LEXICAL_ALIGNED / ko / DRAFT**: `POST /api/embeddings/text` 응답의 provider, modelName, dimension, embedding 필드는?
- **SEMANTIC_PARAPHRASE / ko / DRAFT**: 의미 벡터 서비스가 모델 교체 가능한 내부 경계임을 확인하려면 어떤 metadata를 읽어야 하는가?

예상 답변: provider, modelName, dimension, embedding을 반환하며 whitespace-only 입력은 거부한다.

근거 `api-embedding-contract-1` — `docs/API_SPEC.md` / API Spec > Internal Embedding Contract

> { "provider": "local", "modelName": "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2", "dimension": 384, "embedding": [0.012345, -0.023456, 0.034567] }

SHA-256: `e59e8d70ef0bfbac48c76f703748a955b0b76aec694b4154756e4bbba08cf748`

근거 `api-embedding-contract-2` — `docs/API_SPEC.md` / API Spec > Internal Embedding Contract

> - whitespace-only text is rejected

SHA-256: `a622fe706ddee837279a0a6ed9e9adca8392d8ee2d7eb3137de0ef46f798fd71`


## config-internal-eval

- [ ] 사람이 검토 완료

- **LEXICAL_ALIGNED / ko / DRAFT**: internal retrieval evaluation endpoint의 기본 활성화 상태와 opt-in 환경 변수는?
- **SEMANTIC_PARAPHRASE / en / DRAFT**: How do I deliberately enable the local-only comparison API that is disabled during normal startup?

예상 답변: 기본은 disabled이며 `SIGAK_INTERNAL_SEARCH_EVALUATION_ENABLED=true`로 로컬 실행에서 opt-in한다.

근거 `config-internal-eval-1` — `docs/API_SPEC.md` / API Spec > Internal Retrieval Benchmark Run Contract

> - endpoint exposure is controlled by `sigak.internal.search-evaluation.enabled`; the default is disabled and local comparison runs should opt in with `SIGAK_INTERNAL_SEARCH_EVALUATION_ENABLED=true`

SHA-256: `7e66175127f2f5beffd5aca71984ce9725302c96a0fad3ef12e35bd0725ff6b1`


## config-deterministic-limit

- [ ] 사람이 검토 완료

- **LEXICAL_ALIGNED / ko / DRAFT**: deterministic embedding을 semantic quality 근거로 쓰면 안 되는 이유는?
- **SEMANTIC_PARAPHRASE / en / DRAFT**: Why is the tiny repeatable vector mode suitable for wiring checks but not retrieval-quality claims?

예상 답변: deterministic 8차원 벡터는 wiring smoke용이고 실제 다국어 semantic 평가는 local multilingual model을 사용해야 한다.

근거 `config-deterministic-limit-1` — `docs/API_SPEC.md` / API Spec > Internal Embedding Contract

> - deterministic mode uses a small 8-dimensional vector for MVP local smoke tests

SHA-256: `3322ef007c7b88679b1820113e61e25e110b1ae8eeea8914ab109e14814f06f2`

근거 `config-deterministic-limit-2` — `ai/README.md` / AI Server > Current Status

> The default embedding provider is a local FastEmbed multilingual model. Deterministic embedding remains available to validate Spring Boot -> FastAPI -> Qdrant wiring. It is not a semantic quality claim.

SHA-256: `c112ea5149f28aaf68d82d7fbdd962bace5b2e6991e9c5bcc720053f24c005f3`


## config-no-secret-metadata

- [ ] 사람이 검토 완료

- **LEXICAL_ALIGNED / ko / DRAFT**: retrieval benchmark response metadata에 API key·header·raw vector를 포함해도 되는가?
- **SEMANTIC_PARAPHRASE / en / DRAFT**: Which operational details must never leak into experiment metadata even though model name and dimension are allowed?

예상 답변: provider/model/dimension 외에 API key, header, env, URL, raw vector, credential, stack trace를 노출하지 않는다.

근거 `config-no-secret-metadata-1` — `docs/API_SPEC.md` / API Spec > Internal Retrieval Benchmark Run Contract

> - response metadata may include embedding provider, model name, and dimension only; it must not expose API keys, request headers, environment variables, service URLs, raw vectors, credentials, or stack traces

SHA-256: `01d97ec05da330e58a6ddc3150631e4fb93553b994b413fe42d2d7c281a9b0ab`


## config-arxiv-cooldown

- [ ] 사람이 검토 완료

- **LEXICAL_ALIGNED / ko / DRAFT**: arXiv cross-run cooldown state file과 override 환경 변수는?
- **SEMANTIC_PARAPHRASE / ko / DRAFT**: 짧은 JVM 재실행 사이에도 요청 간격을 공유하는 로컬 장치는 무엇이며 분산 환경까지 보장하는가?

예상 답변: 기본 tmp state file과 `SIGAK_COLLECTION_ARXIV_COOLDOWN_STATE_FILE` override를 사용하며 single-machine guard다.

근거 `config-arxiv-cooldown-1` — `docs/STATUS.md` / Sigak Status > 4. Stabilization Fixes > 4.10 arXiv repeated command-run cooldown

> The default state file is `${java.io.tmpdir}/sigak/arxiv-export-fetch.cooldown`, with `SIGAK_COLLECTION_ARXIV_COOLDOWN_STATE_FILE` available for local override. This is a single-machine guard; distributed coordination across multiple machines remains out of scope.

SHA-256: `bf7cd4bc5e79f07a338d1de6884f0ad4f8091f17b0af6fcf6db81c43a3dc2ac4`


## synth-full-flow

- [ ] 사람이 검토 완료

- **LEXICAL_ALIGNED / ko / DRAFT**: collection부터 smoke artifact inspection까지 local demo 전체 순서는?
- **SEMANTIC_PARAPHRASE / en / DRAFT**: Summarize the reproducible path from selected news sources to grounded search and graph evidence.

예상 답변: 수집→PostgreSQL→failure diagnostics→ES/Qdrant/Neo4j rebuild→public search/detail→metrics→artifact inspection 순서다.

근거 `synth-full-flow-1` — `docs/DEMO_FLOW.md` / Sigak Local Demo Flow > What This Flow Proves

> selected source collection -> PostgreSQL article persistence -> collection failure diagnostics lookup -> forced failure event diagnostics sample -> Elasticsearch keyword projection rebuild -> Qdrant vector projection rebuild -> Neo4j graph projection rebuild -> public hybrid search -> public graph-aware article detail -> internal vector search metrics -> search metrics inspection -> retrieval and graph-aware smoke artifact inspection

SHA-256: `4f4f025121dd4d55c50a5d0f0c1235ff10cac85636c6bd07b369aecae932139b`

근거 `synth-full-flow-2` — `docs/DEMO_FLOW.md` / Sigak Local Demo Flow > Step 1. Run Selected-Source Collection

> - Duplicate articles are counted as skipped, not failed.

SHA-256: `be1ac08996854d32a84bc091f804e7c5e569cdc0eab8702e76930d579f9234b1`


## synth-manual-projection

- [ ] 사람이 검토 완료

- **LEXICAL_ALIGNED / ko / DRAFT**: 자동 수집을 끈 상태에서 수동 collection을 실행한 뒤 projection을 갱신하려면 어떤 후속 단계가 필요한가?
- **SEMANTIC_PARAPHRASE / ko / DRAFT**: 수동으로 뉴스를 저장했는데 검색에 반영되지 않는 경우, 운영자가 따로 호출해야 하는 작업은 무엇인가?

예상 답변: 자동 수집이 비활성인 수동 실행은 projection을 갱신하지 않으므로 Elasticsearch·Qdrant·Neo4j rebuild endpoint를 별도 호출한다.

근거 `synth-manual-projection-1` — `docs/DEMO_FLOW.md` / Sigak Local Demo Flow > Troubleshooting

> - With automation disabled, a direct manual collection run does not rebuild Elasticsearch, Qdrant, or Neo4j; call their rebuild endpoints explicitly.

SHA-256: `06893467ee0662883b5f8ca708dc20190fb942a1198024931932093e136d5f73`

근거 `synth-manual-projection-2` — `README.md` / Sigak > Run Locally

> After the backend starts, rebuild the projections from another terminal:

SHA-256: `73289388078a4b6c1bb6694642f83ee012ba410ab32574e601b5d59f62218a47`


## synth-qdrant-ai

- [ ] 사람이 검토 완료

- **LEXICAL_ALIGNED / ko / DRAFT**: Qdrant rebuild가 AI server와 multilingual embedding 설정에 의존하는 방식은?
- **SEMANTIC_PARAPHRASE / en / DRAFT**: Which service must be healthy before vector projection rebuild, and what kind of model should back quality evaluation?

예상 답변: AI server embedding endpoint가 reachable해야 하며 실제 평가는 multilingual local model을 사용한다.

근거 `synth-qdrant-ai-1` — `docs/DEMO_FLOW.md` / Sigak Local Demo Flow > Step 4. Rebuild Qdrant Vector Projection

> - `embeddingProvider` is `local` for the main semantic retrieval path.

SHA-256: `4a6a5e370cf53ef4ffb4c8881f5f8b2c5fe45713ddac988a85a9bb61a98db6bc`

근거 `synth-qdrant-ai-2` — `docs/DEMO_FLOW.md` / Sigak Local Demo Flow > Troubleshooting

> - If Qdrant rebuild fails while calling the AI server, confirm `http://localhost:8000/health` is reachable.

SHA-256: `0ced447bedabb5fed4ce4c1484662151cdf937f754235f50e84b70821556d7be`

근거 `synth-qdrant-ai-3` — `ai/README.md` / AI Server > Current Status

> For Sigak v0.1, the preferred retrieval path should use a real multilingual embedding model because article sources may include Korean, English, and other languages.

SHA-256: `159cba92cb908a3742636de557cb3ddc11f17561cc52ec1c53d2acdf49171288`


## synth-smoke-claim

- [ ] 사람이 검토 완료

- **LEXICAL_ALIGNED / ko / DRAFT**: 3-query smoke artifact와 41-article expanded catalog의 주장 가능 범위는?
- **SEMANTIC_PARAPHRASE / en / DRAFT**: Why can the committed mini benchmark prove reproducibility but not yet support broad ranking-quality claims?

예상 답변: 3-query 결과는 wiring 재현성만 보여 주며 41-article catalog는 reviewed labels 전까지 품질 주장을 보류한다.

근거 `synth-smoke-claim-1` — `README.md` / Sigak > Current Status

> The current evaluation artifacts are intentionally smoke-sized. The latest committed retrieval comparison and graph-aware evaluation use the preserved `api-ready-2026-06-02` catalog with 3 reviewed queries. A larger 41-article catalog exists at `experiments/datasets/raw/articles.catalog.api-ready-2026-06-05.json`, but expanded benchmark claims are deferred until reviewed labels exist for that catalog.

SHA-256: `eefc3d6a485bc2af0ac68b3703fde70291bf31ba5a084622e50ab0d70bd47320`

근거 `synth-smoke-claim-2` — `README.md` / Sigak > Evaluation Artifacts

> Smoke artifacts are committed to show that the evaluation pipeline is reproducible, not to claim final search quality.

SHA-256: `6b48be3929f5cff7a79f76a672dbb6b2ea611a11fde697ddbcb99213a7832c7c`


## missing-production-sla

- [ ] 사람이 검토 완료

- **ADVERSARIAL_UNANSWERABLE / ko / DRAFT**: Sigak의 production SLA는 몇 퍼센트인가?

예상 답변: 문서만으로 답변할 수 없음. 잘못된 전제이면 이를 바로잡아야 함.


## missing-max-qps

- [ ] 사람이 검토 완료

- **ADVERSARIAL_UNANSWERABLE / ko / DRAFT**: public article search가 보장하는 최대 초당 요청 수는 얼마인가?

예상 답변: 문서만으로 답변할 수 없음. 잘못된 전제이면 이를 바로잡아야 함.


## missing-rto

- [ ] 사람이 검토 완료

- **ADVERSARIAL_UNANSWERABLE / ko / DRAFT**: Elasticsearch 전체 장애 시 공식 RTO는 몇 분인가?

예상 답변: 문서만으로 답변할 수 없음. 잘못된 전제이면 이를 바로잡아야 함.


## missing-monthly-uptime

- [ ] 사람이 검토 완료

- **ADVERSARIAL_UNANSWERABLE / ko / DRAFT**: Neo4j graph context의 월간 가용성 목표는 얼마인가?

예상 답변: 문서만으로 답변할 수 없음. 잘못된 전제이면 이를 바로잡아야 함.


## false-frontend-fastapi

- [ ] 사람이 검토 완료

- **ADVERSARIAL_UNANSWERABLE / ko / DRAFT**: frontend가 FastAPI의 embedding endpoint를 직접 호출하는 코드를 설명해 줘.

예상 답변: 문서만으로 답변할 수 없음. 잘못된 전제이면 이를 바로잡아야 함.


## false-neo4j-truth

- [ ] 사람이 검토 완료

- **ADVERSARIAL_UNANSWERABLE / ko / DRAFT**: Neo4j가 원본 기사와 요약의 source of truth인 이유는 무엇인가?

예상 답변: 문서만으로 답변할 수 없음. 잘못된 전제이면 이를 바로잡아야 함.


## missing-automation-uptime

- [ ] 사람이 검토 완료

- **ADVERSARIAL_UNANSWERABLE / ko / DRAFT**: Sigak 자동 수집의 최근 30일 실제 가동률은 몇 퍼센트인가?

예상 답변: 문서만으로 답변할 수 없음. 잘못된 전제이면 이를 바로잡아야 함.


## false-deterministic-quality

- [ ] 사람이 검토 완료

- **ADVERSARIAL_UNANSWERABLE / ko / DRAFT**: deterministic 8차원 embedding이 multilingual model보다 검색 품질이 높다는 결과는?

예상 답변: 문서만으로 답변할 수 없음. 잘못된 전제이면 이를 바로잡아야 함.


## out-k8s-autoscale

- [ ] 사람이 검토 완료

- **ADVERSARIAL_UNANSWERABLE / ko / DRAFT**: Kubernetes HPA가 Sigak backend pod를 확장하는 기준을 알려 줘.

예상 답변: 문서만으로 답변할 수 없음. 잘못된 전제이면 이를 바로잡아야 함.


## out-password-reset

- [ ] 사람이 검토 완료

- **ADVERSARIAL_UNANSWERABLE / ko / DRAFT**: 사용자 비밀번호 재설정 API와 이메일 토큰 만료 시간을 알려 줘.

예상 답변: 문서만으로 답변할 수 없음. 잘못된 전제이면 이를 바로잡아야 함.


## out-aws-region

- [ ] 사람이 검토 완료

- **ADVERSARIAL_UNANSWERABLE / ko / DRAFT**: 운영 AWS 배포 리전과 multi-AZ 토폴로지를 알려 줘.

예상 답변: 문서만으로 답변할 수 없음. 잘못된 전제이면 이를 바로잡아야 함.


## out-robot-estop

- [ ] 사람이 검토 완료

- **ADVERSARIAL_UNANSWERABLE / ko / DRAFT**: 로봇 actuator emergency stop 명령과 안전 PLC 회로를 알려 줘.

예상 답변: 문서만으로 답변할 수 없음. 잘못된 전제이면 이를 바로잡아야 함.

