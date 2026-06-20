# Ops RAG Evaluation Harness Design

날짜: 2026-06-20
개정: 2026-06-20 — full query set과 dense retrieval 평가 편향 통제 보강

## Summary

Sigak에 **운영 문서 기반 RAG 평가 vertical slice**를 추가한다.

이번 작업의 목표는 새 챗봇 화면을 만드는 것이 아니다. Sigak 저장소의 실행 방법,
API 계약, projection rebuild, fallback, 장애 대응 문서를 운영 매뉴얼 corpus로 고정하고,
다음 흐름을 한 번의 재현 가능한 실험으로 연결하는 것이다.

```txt
versioned operations corpus
-> structure-preserving preprocessing
-> fixed-size / heading-aware chunking
-> BM25 / dense / hybrid retrieval
-> optional LLM reranking
-> no-RAG / RAG answer generation
-> deterministic checks + LLM-as-a-judge + human calibration
-> JSON and Markdown artifacts
```

이 설계는 NAVER 인턴 1지망 과제의 운영 매뉴얼 전처리, chunking, retrieval,
reranking, 답변 정확성, 근거 충실성, hallucination 비교 평가 요구에 직접 대응한다.
동시에 평가 task, variant, metric, failure artifact를 분리하는 구조를 통해 2지망
AI 코딩 에이전트 평가 하네스 과제와 연결되는 평가 설계 역량도 보여준다.

## Current State

이미 구현된 기반:

- Elasticsearch keyword retrieval
- FastAPI local embedding boundary
- Qdrant vector retrieval
- RRF 기반 hybrid retrieval
- keyword, vector, strict hybrid, public search 비교 runner
- Recall@5, MRR@5, latency, failure, degraded mode artifact
- Neo4j graph context와 graph-aware evaluation runner
- GitHub Issue, PR, 테스트, runtime smoke를 사용한 개발 기록
- 41개 API-ready article catalog와 12개 draft query

현재 한계:

- 41개 article catalog는 제목, summary, why-it-matters 중심이다.
- catalog의 article별 결합 텍스트는 평균 약 377자이며, 긴 운영 문서와 성격이 다르다.
- 이 catalog로 chunking 전략을 비교하면 대부분의 문서가 하나의 chunk에 들어가므로
  chunk boundary의 효과를 설명하기 어렵다.
- current enrichment는 mock이며 실제 답변 생성 비교가 없다.
- reranking, citation-grounded answer, unsupported claim 판정, LLM judge가 없다.
- 기존 3-query/6-article 결과는 smoke artifact이며 품질 우수성을 주장할 수 없다.

따라서 기존 article retrieval benchmark는 보존하되, 이번 RAG 평가의 corpus로
재사용하지 않는다.

## Problem

Sigak은 검색 인프라와 benchmark runner를 가지고 있지만 다음 질문에는 아직 답하지 못한다.

1. 구조가 있는 운영 문서를 어떤 단위로 나누는 것이 검색에 유리한가?
2. BM25, dense, hybrid retrieval이 질문 유형별로 어떻게 다른가?
3. reranking이 검색 품질을 높이는가, 아니면 latency와 비용만 증가시키는가?
4. 검색된 문맥이 최종 답변에 실제로 사용됐는가?
5. citation이 존재한다는 사실과 citation이 답변을 뒷받침한다는 사실을 어떻게 구분하는가?
6. 문서에 답이 없을 때 모델이 올바르게 답변을 거부하는가?
7. LLM judge와 사람의 판단이 불일치할 때 무엇을 신뢰하고 어떻게 기록하는가?
8. 실험 설정, 모델, prompt, corpus version을 바꿔도 이전 결과를 재현할 수 있는가?

## Goals

- Sigak 운영 문서를 versioned RAG corpus로 만든다.
- chunking 전략과 무관한 evidence-span 기반 평가 dataset을 만든다.
- 서로 다른 24개 answerable information need를 정의하고, 각 intent에
  lexical-aligned와 semantic-paraphrase query를 한 개씩 작성한다.
- answerable query 48개와 unanswerable/adversarial query 12개를 합쳐
  총 60개 manually reviewed query를 만든다.
- lexical, semantic paraphrase, cross-lingual query를 분리해 BM25와 dense
  retrieval의 평가 편향을 통제한다.
- fixed-size와 heading-aware chunking을 비교한다.
- BM25, dense, hybrid retrieval을 같은 query set에서 비교한다.
- hybrid top-N에 LLM reranking을 적용하고 전후 품질, latency, token usage를 비교한다.
- no-RAG와 두 chunking strategy의 hybrid/hybrid+rerank RAG 답변을 비교한다.
- 답변과 citation을 structured output으로 생성한다.
- citation validity와 evidence coverage를 결정론적으로 검사한다.
- LLM judge로 correctness, faithfulness, completeness, unsupported claim을 평가한다.
- 사람 평가 표본과 LLM judge의 일치·불일치를 기록한다.
- 실행 설정과 결과를 JSON과 Markdown artifact로 저장한다.
- 단일 명령으로 같은 corpus와 dataset을 다시 실행할 수 있게 한다.
- 실험의 한계와 실패 사례를 숨기지 않는다.

## Non-Goals

- public chatbot API 또는 새로운 사용자 화면을 만들지 않는다.
- article search API의 ranking 동작을 변경하지 않는다.
- 기존 Elasticsearch, Qdrant index schema를 chunk 단위로 변경하지 않는다.
- full GraphRAG chatbot을 만들지 않는다.
- RAGAS 점수만 붙이고 평가가 끝났다고 판단하지 않는다.
- production-grade prompt registry 또는 experiment platform을 만들지 않는다.
- 수백 개 규모의 대규모 evaluation dataset이나 다른 도메인 corpus를 이번 범위에 넣지 않는다.
- 코딩 에이전트, Kubernetes 이상 탐지, 자동 수정 PR 봇을 이번 구현에 넣지 않는다.
- research dashboard를 이번 작업과 동시에 구현하지 않는다.
- 외부에 공개할 수 없는 군 내부 문서나 데이터를 corpus로 사용하지 않는다.

## Approach Comparison

### A안. Article catalog를 그대로 사용

흐름:

```txt
41 article summaries
-> chunking
-> existing article retrieval
-> answer generation
```

장점:

- 기존 catalog와 query draft를 재사용할 수 있다.
- 현재 search infrastructure와 가장 직접적으로 연결된다.

단점:

- 문서가 짧아 chunking 비교가 형식적이 된다.
- 일부 seed article은 `example.com` URL과 큐레이션 문장으로 구성돼 있다.
- 운영 매뉴얼 기반 RAG라는 과제 성격과 거리가 있다.

판단:

- 기존 article retrieval benchmark에는 계속 사용한다.
- 이번 운영 문서 RAG 평가 corpus로는 사용하지 않는다.

### B안. 외부 공개 로봇 매뉴얼을 수집

흐름:

```txt
public robot manuals
-> parsing
-> chunking
-> retrieval and generation evaluation
```

장점:

- 지원 과제의 로봇 도메인과 가장 직접적으로 닮았다.
- 긴 PDF, 표, 경고문, 절차 문서를 다룰 수 있다.

단점:

- 라이선스와 재배포 조건을 확인해야 한다.
- PDF parsing과 표 처리에 시간이 소모된다.
- Sigak 프로젝트의 실제 운영 문제와 연결이 약하다.
- 짧은 제출 일정에서 corpus 품질 검토가 불완전할 위험이 있다.

판단:

- 후속 확장 후보로 남긴다.
- 이번 제출 전 범위에는 포함하지 않는다.

### C안. Sigak 운영 문서를 versioned manual corpus로 사용

흐름:

```txt
README / API spec / demo flow / operations docs
-> Markdown structure-preserving preprocessing
-> chunking and retrieval variants
-> grounded operations Q&A evaluation
```

장점:

- 실제로 이해하고 운영해 본 복잡한 도메인을 사용한다.
- heading, code block, command, fallback, troubleshooting 절차가 존재한다.
- 질문과 근거를 직접 검토할 수 있다.
- corpus version을 Git commit으로 고정할 수 있다.
- 프로젝트 안에서 재현 가능한 공개 실험이 된다.
- 지원 과제와 포트폴리오 설명이 자연스럽게 연결된다.

단점:

- 로봇 도메인 자체는 아니다.
- Markdown 중심이므로 PDF 표·이미지 전처리 능력까지 보여주지는 못한다.

판단:

- 최종 선택이다.
- 로봇 도메인 적용은 자기소개서 수행 계획에서 확장 방향으로 설명한다.

## Selected Architecture

실험은 `experiments/rag-evaluation/` 아래의 독립 Python package와 CLI로 구현한다.

```txt
repository Markdown files
-> corpus snapshot builder
-> normalized document model
-> chunking variants
-> in-memory retrieval variants
-> OpenAI reranker / generator / judge clients
-> deterministic and model-based metrics
-> artifact writer
```

제품 API 경계는 변경하지 않는다.

- Spring Boot public API: 변경 없음
- Elasticsearch/Qdrant/Neo4j projection: 변경 없음
- React frontend: 변경 없음
- FastAPI embedding endpoint: dense embedding provider로 재사용 가능
- OpenAI API: reranking, generation, judge에만 사용

실험을 제품 서비스와 분리하는 이유:

- 15시간 범위에서 search index migration을 피한다.
- 기존 article search regression 위험을 만들지 않는다.
- 실험 variant를 빠르게 교체할 수 있다.
- 완료 후 검증된 구성만 제품 경계로 옮길 수 있다.

## Directory Design

예상 구조:

```txt
experiments/rag-evaluation/
├── README.md
├── datasets/
│   ├── corpus-manifest.json
│   └── ops-questions.json
├── prompts/
│   ├── reranker-v1.md
│   ├── generator-v1.md
│   └── judge-v1.md
├── rag_evaluation/
│   ├── __init__.py
│   ├── cli.py
│   ├── models.py
│   ├── corpus.py
│   ├── chunking.py
│   ├── embeddings.py
│   ├── retrieval.py
│   ├── reranking.py
│   ├── generation.py
│   ├── judging.py
│   ├── metrics.py
│   └── artifacts.py
├── tests/
└── results/
    └── latest/
        ├── run-manifest.json
        ├── corpus.snapshot.json
        ├── chunks.fixed.json
        ├── chunks.heading.json
        ├── retrieval.runs.json
        ├── retrieval.metrics.json
        ├── generation.runs.json
        ├── generation.metrics.json
        ├── human-review.json
        ├── failures.json
        └── report.md
```

`results/latest/`는 재현 명령으로 다시 만들 수 있어야 한다.
장기 비교용 timestamp 디렉터리는 이번 범위에서 제외하고, 첫 구현은
`latest/`와 artifact 내부 `runId` metadata로 제한한다.

## Corpus Contract

### Allowed Documents

첫 corpus는 다음 공개 문서의 명시적 allowlist로 구성한다.

- `README.md`
- `docs/DEMO_FLOW.md`
- `docs/API_SPEC.md`
- `experiments/README.md`
- `backend/README.md`
- `ai/README.md`
- `docs/STATUS.md`의 운영, fallback, verification 관련 section

`docs/STATUS.md` 전체를 무조건 포함하지 않는다.
긴 verification table과 과거 기록이 질문에 불필요한 noise가 되면 section allowlist로 제한한다.

### Corpus Snapshot

각 실행은 다음 metadata를 기록한다.

```json
{
  "corpusId": "sigak-ops-2026-06-20",
  "gitCommit": "full-commit-sha",
  "generatedAt": "2026-06-20T00:00:00Z",
  "documents": [
    {
      "documentPath": "docs/DEMO_FLOW.md",
      "sha256": "...",
      "includedSections": ["Local Demo", "Fallback Verification"]
    }
  ]
}
```

working tree가 dirty인 경우 실행을 금지하지는 않지만 다음을 기록한다.

- `workingTreeDirty=true`
- 포함 파일의 content hash

포트폴리오용 최종 artifact는 clean commit에서 생성한다.

## Preprocessing Contract

Markdown parser는 다음 정보를 보존한다.

- document path
- heading hierarchy
- paragraph
- bullet/numbered list
- fenced code block
- command line
- normalized start/end offset

정규화 규칙:

- line ending을 `\n`으로 통일한다.
- heading text 앞뒤 공백을 제거한다.
- paragraph 내부 연속 공백은 하나로 줄인다.
- code block 내부 whitespace는 보존한다.
- URL, environment variable, endpoint, command는 변경하지 않는다.
- 한국어와 영어를 번역하지 않는다.

문서 원문과 normalized text의 offset mapping이 완전히 동일하지 않을 수 있으므로,
평가 evidence는 raw byte offset에만 의존하지 않는다.

## Evaluation Dataset Contract

dataset은 두 층으로 나눈다.

| 층 | 규모 | 목적 |
| --- | ---: | --- |
| smoke fixture | 3 query | parser, chunker, runner, artifact wiring 검증 |
| full comparison set | 60 query | chunking, retrieval, generation variant 비교 |

12개 안팎의 pilot query는 구현 중 오류를 찾는 데 사용할 수 있지만, 최종 비교
artifact와 자기소개서 품질 주장의 근거로 사용하지 않는다.

### Full Comparison Set

answerable information need 24개를 먼저 정의한다.

| intent 유형 | intent 수 |
| --- | ---: |
| architecture rationale | 4 |
| command/procedure | 4 |
| failure/fallback | 4 |
| API/contract | 4 |
| configuration/security | 4 |
| cross-section synthesis | 4 |

각 intent는 같은 expected answer와 gold evidence를 공유하는 query 두 개를 가진다.

1. `LEXICAL_ALIGNED`
   - 문서에 등장하는 identifier, endpoint, mode, command와 같은 핵심 표현을 사용한다.
   - BM25가 exact match 장점을 보여줄 수 있는 자연스러운 질문이다.
2. `SEMANTIC_PARAPHRASE`
   - 의미는 같지만 문서 표현을 기계적으로 복사하지 않는다.
   - primary keyword를 억지로 숨기는 문장이 아니라 실제 사용자가 말할 법한 표현이어야 한다.
   - 24개 가운데 최소 8개는 영어 또는 한·영 cross-lingual query로 작성한다.

이렇게 answerable query 48개를 만든다.

별도로 unanswerable/adversarial query 12개를 만든다.

| unanswerable 유형 | query 수 |
| --- | ---: |
| 문서에 없는 수치·SLA 요구 | 4 |
| 잘못된 전제를 포함한 질문 | 4 |
| Sigak 운영 범위를 벗어난 질문 | 4 |

총 dataset 규모:

```txt
24 answerable intents × 2 surface forms = 48 answerable queries
+ 12 unanswerable/adversarial queries
= 60 reviewed queries
```

### Paired Query Design

lexical query와 semantic query를 독립 표본처럼 취급하지 않는다.
두 query는 같은 `intentId`와 evidence를 공유하므로 통계 해석의 기본 단위는
query row가 아니라 24개 answerable intent cluster다.

이 설계의 목적:

- keyword 중심 운영 질문만 사용해 BM25에 유리해지는 편향을 줄인다.
- semantic query만 의도적으로 만들어 dense retrieval에 유리해지는 반대 편향도 줄인다.
- 같은 정보 요구에서 표현만 달라졌을 때 retrieval system의 민감도를 비교한다.
- overall 평균 뒤에 숨는 lexical/semantic 성능 차이를 드러낸다.

query naturalness와 편향을 확인하기 위해 각 row에 다음 값을 기록한다.

- `surfaceForm`
- `language`
- `expectedResponseLanguage`
- `evidenceShape`
- `measuredLexicalOverlap`
- `naturalnessReview`
- `reviewStatus`

`measuredLexicalOverlap`은 query와 gold evidence 사이의 tokenizer 기준 overlap으로
계산한다. `surfaceForm`은 작성 의도이고 measured overlap은 실제 관측값이다.
둘이 모순되면 query를 다시 검토한다.

query schema:

```json
{
  "intentId": "fallback-qdrant",
  "queryId": "fallback-qdrant-lexical-ko",
  "query": "Qdrant가 중단되면 public search는 어떻게 동작하는가?",
  "language": "ko",
  "expectedResponseLanguage": "ko",
  "surfaceForm": "LEXICAL_ALIGNED",
  "queryType": "failure_fallback",
  "evidenceShape": "SINGLE_SECTION",
  "answerable": true,
  "expectedAnswer": "Keyword-only mode로 degrade된다.",
  "evidence": [
    {
      "evidenceId": "qdrant-keyword-only-fallback",
      "documentPath": "docs/STATUS.md",
      "headingPath": [
        "Stabilization Fixes",
        "Hybrid public search with PostgreSQL fallback"
      ],
      "quote": "If one projection path fails, the service degrades to KEYWORD_ONLY or VECTOR_ONLY.",
      "quoteSha256": "..."
    }
  ],
  "measuredLexicalOverlap": 0.42,
  "naturalnessReview": "NATURAL",
  "reviewStatus": "REVIEWED",
  "notes": "Qdrant만 실패한 상황과 두 projection이 모두 실패한 상황을 구분한다."
}
```

같은 intent의 semantic pair:

```json
{
  "intentId": "fallback-qdrant",
  "queryId": "fallback-qdrant-semantic-ko",
  "query": "의미 기반 검색 저장소를 사용할 수 없을 때 서비스가 계속 검색 결과를 제공하는 방식은 무엇인가?",
  "language": "ko",
  "expectedResponseLanguage": "ko",
  "surfaceForm": "SEMANTIC_PARAPHRASE",
  "queryType": "failure_fallback",
  "evidenceShape": "SINGLE_SECTION",
  "answerable": true,
  "expectedAnswer": "Keyword-only mode로 degrade된다.",
  "evidence": [
    {
      "evidenceId": "qdrant-keyword-only-fallback",
      "documentPath": "docs/STATUS.md",
      "headingPath": [
        "Stabilization Fixes",
        "Hybrid public search with PostgreSQL fallback"
      ],
      "quote": "If one projection path fails, the service degrades to KEYWORD_ONLY or VECTOR_ONLY.",
      "quoteSha256": "..."
    }
  ],
  "measuredLexicalOverlap": 0.09,
  "naturalnessReview": "NATURAL",
  "reviewStatus": "REVIEWED",
  "notes": "Qdrant라는 고유명사를 사용하지 않고 같은 information need를 표현한다."
}
```

unanswerable query:

```json
{
  "intentId": "missing-production-sla",
  "queryId": "ops-011",
  "query": "Sigak의 production SLA는 몇 퍼센트인가?",
  "language": "ko",
  "expectedResponseLanguage": "ko",
  "surfaceForm": "ADVERSARIAL_UNANSWERABLE",
  "queryType": "unanswerable",
  "evidenceShape": null,
  "answerable": false,
  "expectedAnswer": null,
  "evidence": [],
  "measuredLexicalOverlap": null,
  "naturalnessReview": "NATURAL",
  "reviewStatus": "REVIEWED",
  "notes": "문서에 없는 수치를 생성하지 않고 답변 불가를 표시해야 한다."
}
```

### Dataset Construction Guardrails

- LLM은 query 초안을 만들 수 있지만 gold evidence, expected answer, answerability,
  naturalness는 사람이 검토한다.
- dense가 잘하는 사례만 골라 넣거나 BM25가 잘하는 identifier query만 반복하지 않는다.
- 같은 intent pair는 동일한 expected answer와 evidence set을 사용한다.
- semantic paraphrase가 부자연스럽거나 원래 intent를 바꾸면 dataset에서 제외한다.
- query 작성 이후 retrieval 결과를 보고 label이나 표현을 바꾸지 않는다.
- query 또는 label을 변경할 때는 dataset version을 올리고 이전 artifact를 보존한다.

### Chunking-Independent Gold Evidence

정답을 chunk ID로 직접 저장하지 않는다.

이유:

- chunking 전략마다 chunk ID와 경계가 달라진다.
- 특정 chunker의 결과를 gold label로 사용하면 비교가 편향된다.

대신 `documentPath + headingPath + normalized quote + quoteSha256`를 gold evidence로 둔다.
각 chunker는 생성한 chunk가 evidence quote를 얼마나 포함하는지 계산해 relevance grade를 만든다.

relevance grade:

- `2`: query의 expected evidence를 직접 포함한다.
- `1`: expected evidence의 일부 또는 같은 section의 보조 설명을 포함한다.
- `0`: 관련 근거를 포함하지 않는다.

`grade=1`의 자동 판정은 과대평가될 수 있으므로 첫 dataset에서는 사람이 검토한
explicit supporting span만 사용한다.

## Chunking Variants

### Fixed-Size Chunking

목적:

- 단순하고 재현 가능한 baseline을 만든다.

초기 설정:

- target: 500 tokens
- max: 500 tokens
- overlap: 100 tokens
- code block을 분할할 수 있으나 metadata로 분할 여부를 기록한다.

### Heading-Aware Chunking

목적:

- 운영 문서의 절차, 명령, fallback 설명 구조를 보존한다.

규칙:

- heading hierarchy를 chunk metadata에 포함한다.
- 같은 heading 아래 paragraph, list, code block을 우선 묶는다.
- code block과 바로 앞 설명 문장을 가능한 한 같은 chunk에 둔다.
- fixed-size와 같은 500-token budget을 사용한다.
- 500 tokens를 넘으면 paragraph 또는 list item 경계에서 분할하고 최대 100-token
  boundary overlap을 허용한다.
- 분할 후에도 heading hierarchy를 복제한다.

두 전략은 같은 tokenizer와 최대 token budget을 사용한다.
chunk count, 평균/p95 token count, duplicated-token ratio를 함께 기록해 heading-aware
전략이 단순히 더 많은 chunk를 만든 효과를 구조 보존 효과로 오해하지 않게 한다.

### Stable Chunk ID

chunk ID는 실행 순번만 사용하지 않는다.

```txt
{strategy}:{documentPathHash}:{headingPathHash}:{contentHashPrefix}
```

같은 corpus와 설정에서는 같은 chunk ID가 생성돼야 한다.

chunk metadata:

```json
{
  "chunkId": "heading:ab12:cd34:ef567890",
  "strategy": "heading",
  "documentPath": "docs/DEMO_FLOW.md",
  "headingPath": ["Local Demo", "Fallback Verification"],
  "content": "...",
  "tokenCount": 436,
  "containsCodeBlock": true,
  "contentSha256": "..."
}
```

## Retrieval Variants

### BM25

- in-memory corpus를 사용한다.
- 한국어 plain text를 단순 whitespace로만 분리하지 않는다.
- technical mixed tokenizer는 endpoint, environment variable, mode, command,
  영문 identifier를 atomic token으로 보존하고 한글 구간은 형태소 단위로 분리한다.
- 같은 tokenizer를 query와 chunk에 적용한다.
- tokenizer와 parameter를 run manifest에 기록한다.
- BM25 `k1`, `b`는 full run 전에 고정한다.

### Dense

- 기존 FastAPI embedding boundary를 재사용한다.
- 한국어, 영어, cross-lingual query를 처리할 수 있는 multilingual embedding model을 사용한다.
- document chunk와 query embedding을 구분해야 하는 model이면 prefix 정책을 기록한다.
- embedding은 corpus ID, model, chunk hash 기준으로 cache한다.
- embedding dimension과 vector normalization 여부를 검증한다.
- cosine similarity로 ranking한다.
- full run 전에 paraphrase positive와 hard negative로 구성된 작은 sanity fixture를 통과해야 한다.

Dense의 장점을 보여주기 위해 semantic query만 따로 선별하지 않는다.
overall, lexical, semantic, cross-lingual 결과를 모두 함께 보고한다.

### Hybrid

- BM25와 dense candidate를 RRF로 결합한다.
- 기존 Sigak의 RRF 설계와 같은 개념을 사용하되, 실험 package는 독립 구현한다.
- RRF `k`와 candidate limit을 manifest에 기록한다.

### Factorial Comparison Matrix

chunking 효과와 retrieval 효과를 섞지 않도록 다음 조합을 모두 실행한다.

| Chunking | Retrieval |
| --- | --- |
| fixed-size | BM25 |
| fixed-size | dense |
| fixed-size | hybrid |
| fixed-size | hybrid + rerank |
| heading-aware | BM25 |
| heading-aware | dense |
| heading-aware | hybrid |
| heading-aware | hybrid + rerank |

chunking 차이는 같은 retrieval system 안에서 비교한다.
retrieval system 차이는 같은 chunking strategy 안에서 비교한다.
가장 높은 단일 조합만 보고 나머지를 숨기지 않는다.

### Retrieval Metrics

- primary metric: nDCG@5
- Recall@5
- MRR@5
- evidence coverage@5
- average latency
- p95 latency
- failed query count

metric 해석:

- Recall@5: relevant evidence를 포함한 chunk를 top 5에서 찾았는가?
- MRR@5: 첫 direct evidence chunk가 얼마나 빨리 등장하는가?
- nDCG@5: direct evidence와 supporting evidence의 순서 품질은 어떠한가?
- evidence coverage@5: query의 expected evidence span 중 몇 개가 top 5에 포함되는가?

### Retrieval Result Slices

다음 slice를 모두 생성한다.

- overall answerable queries
- `LEXICAL_ALIGNED`
- `SEMANTIC_PARAPHRASE`
- Korean
- English/cross-lingual
- query type별
- single-section evidence
- cross-section evidence

BM25와 dense의 평가는 overall 한 줄 순위로 끝내지 않는다.

예상 가능하지만 사전에 결론으로 고정하지 않는 패턴:

- BM25는 identifier와 exact keyword가 있는 lexical query에 강할 수 있다.
- dense는 표현이 다른 semantic/cross-lingual query에 강할 수 있다.
- hybrid는 두 유형 사이의 variance를 줄일 수 있다.

실제 결과가 이 예상과 다르면 결과를 그대로 보고한다.

### Paired Statistical Analysis

chunking과 retrieval system 비교는 같은 intent에서 나온 paired result를 사용한다.

- query-level 평균과 intent-level 평균을 모두 기록한다.
- lexical/semantic pair 두 개를 하나의 `intentId` cluster로 묶는다.
- primary metric인 nDCG@5 delta에 대해 intent-cluster bootstrap 95% confidence interval을 계산한다.
- 10,000회 bootstrap resample을 기본값으로 사용하고 seed를 manifest에 기록한다.
- win/tie/loss intent count도 함께 보고한다.
- query 48개를 서로 독립인 `n=48`로 해석하지 않는다. 유효한 answerable 비교 단위는
  24개 intent다.
- unanswerable query는 retrieval ranking superiority 계산에서 제외하고 generation의
  refusal 평가에 사용한다.

heading-aware가 fixed-size보다 우수하다고 표현하기 위한 최소 조건:

1. 60개 full dataset이 모두 reviewed 상태다.
2. 모든 chunking/retrieval 조합이 동일 dataset에서 실행됐다.
3. primary metric과 slice metric을 사전에 고정했다.
4. intent-level nDCG@5 delta와 95% confidence interval을 보고했다.
5. 반대 방향의 slice와 regression case를 숨기지 않았다.

confidence interval이 0을 포함하면 “명확한 우위가 관찰되지 않았다”고 작성한다.
0을 포함하지 않더라도 “Sigak 운영 문서와 이 query set에서 관찰된 차이”로 제한하고,
일반적인 chunking 우월성으로 확장하지 않는다.

## Reranking Contract

reranker는 hybrid top 10 chunk를 입력으로 받는다.

입력:

- query
- candidate chunk ID
- heading path
- content

출력:

```json
{
  "orderedChunkIds": [
    "heading:...",
    "fixed:..."
  ],
  "reasonByChunkId": {
    "heading:...": "The chunk directly explains Qdrant-only fallback."
  }
}
```

검증:

- 입력에 없는 chunk ID를 반환하면 invalid output이다.
- 중복 ID를 제거한다.
- 누락된 candidate는 원래 순서의 뒤에 붙인다.
- invalid output은 한 번만 retry한다.
- retry도 실패하면 original hybrid ranking으로 fallback한다.
- fallback 여부를 artifact에 기록한다.

비교:

- hybrid
- hybrid + rerank

reranking이 Recall@5 또는 nDCG@5를 개선했다고 미리 가정하지 않는다.
latency와 token usage 증가도 함께 보고한다.

## Generation Contract

retrieval 평가가 완료된 뒤 다섯 variant를 실행한다.

1. `NO_RAG`
2. `FIXED_HYBRID_RAG`
3. `FIXED_HYBRID_RERANK_RAG`
4. `HEADING_HYBRID_RAG`
5. `HEADING_HYBRID_RERANK_RAG`

모든 variant는 같은 generator model, temperature, answer schema를 사용한다.
RAG variant의 차이는 chunking, retrieval context order뿐이다.

이 조합을 모두 실행하는 이유:

- retrieval metric에서 관찰된 chunking 차이가 answer quality에도 이어지는지 확인한다.
- heading-aware가 retrieval에서는 좋아도 더 긴 noise context 때문에 generation에서
  불리할 가능성을 확인한다.
- full evaluation 결과를 본 뒤 가장 좋은 조합만 생성 실험에 선택하는 evaluation
  leakage를 피한다.

structured output:

```json
{
  "answerable": true,
  "answer": "Qdrant만 사용할 수 없으면 keyword-only mode로 degrade됩니다.",
  "citations": [
    {
      "chunkId": "heading:ab12:cd34:ef567890",
      "evidenceIds": ["qdrant-keyword-only-fallback"]
    }
  ]
}
```

규칙:

- context에 없는 사실을 추측하지 않는다.
- 답변할 수 없으면 `answerable=false`로 반환한다.
- `expectedResponseLanguage`로 지정된 언어로 답변한다.
- citation은 제공된 chunk ID만 사용할 수 있다.
- 네 RAG variant의 answerable answer는 최소 한 개의 citation을 가져야 한다.
- `NO_RAG`는 동일한 schema를 사용하되 `citations=[]`로 반환하며, citation metric의
  비교 대상이 아니라 hallucination과 answer correctness baseline으로 평가한다.

## Deterministic Evaluation

LLM judge와 무관하게 다음을 검사한다.

- schema validity
- answerable/citation constraint
- citation chunk existence
- citation candidate membership
- cited chunk가 declared evidence ID를 포함하는지
- expected evidence coverage
- answerable query refusal 여부
- unanswerable query refusal 여부
- duplicate citation
- latency
- input/output token usage
- API failure와 retry count

cost는 모델 pricing을 source code에 고정하지 않는다.

- raw token usage는 항상 기록한다.
- 비용을 계산할 경우 run manifest에 입력·출력 token 단가와 기준 날짜를 기록한다.
- 단가가 없으면 `estimatedCostUsd=null`로 둔다.

## LLM-as-a-Judge Contract

judge는 generator와 다른 model configuration을 사용한다.

입력:

- query
- expected answer
- expected evidence
- generated answer
- cited chunk contents

judge에는 variant 이름과 retrieval system 이름을 전달하지 않는다.

평가 schema:

```json
{
  "correctness": 0,
  "faithfulness": 0,
  "completeness": 0,
  "citationSupport": 0,
  "unsupportedClaims": [
    {
      "claim": "...",
      "reason": "No cited evidence supports this claim."
    }
  ],
  "verdict": "SUPPORTED",
  "rationale": "..."
}
```

score 범위는 `0-4`로 고정한다.

verdict:

- `SUPPORTED`
- `PARTIALLY_SUPPORTED`
- `UNSUPPORTED`
- `CORRECT_REFUSAL`
- `INCORRECT_REFUSAL`

편향 완화:

- variant 이름을 숨긴다.
- evaluation input 순서를 query별로 섞는다.
- generator와 judge model config를 분리한다.
- judge prompt version을 기록한다.
- judge 출력도 structured schema로 검증한다.

judge 점수는 단독 ground truth로 사용하지 않는다.

## Human Calibration

최소 10개 query를 선택하고 다섯 generation variant를 모두 사람이 검토한다.
따라서 최소 50개 answer review를 만든다.

선정 기준:

- lexical-aligned query 2개
- semantic-paraphrase query 2개
- English/cross-lingual query 2개
- retrieval miss 또는 rerank regression 1개
- answerable query의 잘못된 refusal 1개
- unanswerable query 2개

사람 평가 항목:

- correctness
- faithfulness
- completeness
- refusal correctness
- unsupported claim 여부

calibration artifact:

```json
{
  "reviewer": "project-owner",
  "reviewedAt": "2026-06-20T00:00:00Z",
  "items": [],
  "agreement": {
    "exactVerdictAgreement": 0.0,
    "meanAbsoluteScoreDifference": 0.0
  },
  "disagreements": []
}
```

50개 review도 전체 judge 성능을 일반화하기에는 작다.
목적은 lexical/semantic/refusal 유형에서 judge의 명백한 오판과 rubric ambiguity를
찾는 것이다.

## Failure Taxonomy

실패는 최소 다음 유형으로 분리한다.

- `CORPUS_PARSE_FAILURE`
- `EVIDENCE_NOT_FOUND`
- `EMBEDDING_FAILURE`
- `RETRIEVAL_MISS`
- `RERANK_INVALID_OUTPUT`
- `RERANK_REGRESSION`
- `GENERATION_SCHEMA_FAILURE`
- `INVALID_CITATION`
- `UNSUPPORTED_CLAIM`
- `OVER_REFUSAL`
- `UNDER_REFUSAL`
- `JUDGE_SCHEMA_FAILURE`
- `JUDGE_DISAGREEMENT`

각 failure row:

```json
{
  "queryId": "ops-001",
  "variant": "HEADING_HYBRID_RERANK_RAG",
  "failureType": "RERANK_REGRESSION",
  "evidence": {
    "beforeRank": 1,
    "afterRank": 4
  },
  "notes": "..."
}
```

## Run Manifest

모든 artifact는 같은 `runId`를 가진다.

```json
{
  "runId": "2026-06-20T12-00-00Z",
  "gitCommit": "...",
  "workingTreeDirty": false,
  "corpusId": "sigak-ops-2026-06-20",
  "datasetVersion": 1,
  "dataset": {
    "answerableIntentCount": 24,
    "answerableQueryCount": 48,
    "unanswerableQueryCount": 12,
    "bootstrapSeed": 20260620,
    "bootstrapResamples": 10000
  },
  "chunking": {
    "fixed": {"targetTokens": 500, "overlapTokens": 100},
    "heading": {"maxTokens": 500, "maxBoundaryOverlapTokens": 100}
  },
  "retrieval": {
    "systems": ["bm25", "dense", "hybrid", "hybrid_rerank"],
    "topK": 5,
    "candidateLimit": 10
  },
  "models": {
    "embedding": "...",
    "reranker": "...",
    "generator": "...",
    "judge": "..."
  },
  "promptVersions": {
    "reranker": "v1",
    "generator": "v1",
    "judge": "v1"
  }
}
```

API key와 실제 secret은 artifact에 기록하지 않는다.

## CLI Contract

첫 공개 명령:

```bash
cd experiments/rag-evaluation
python -m rag_evaluation.cli run \
  --dataset datasets/ops-questions.json \
  --output results/latest
```

선택적 단계 실행:

```bash
python -m rag_evaluation.cli build-corpus ...
python -m rag_evaluation.cli evaluate-retrieval ...
python -m rag_evaluation.cli evaluate-generation ...
python -m rag_evaluation.cli build-report ...
```

최종 README에는 한 번에 실행하는 명령을 먼저 제공한다.

## Environment Configuration

예상 환경 변수:

```txt
OPENAI_API_KEY
SIGAK_AI_BASE_URL
SIGAK_RAG_EMBEDDING_MODEL
SIGAK_RAG_RERANKER_MODEL
SIGAK_RAG_GENERATOR_MODEL
SIGAK_RAG_JUDGE_MODEL
```

규칙:

- `.env`는 commit하지 않는다.
- `.env.example`에는 변수명과 안전한 예시만 추가한다.
- OpenAI API가 없어도 corpus, chunking, deterministic retrieval test는 실행할 수 있게 한다.
- generation/judge가 필요한 full run은 missing key를 명확한 오류로 종료한다.

## Error Handling

- 한 query의 OpenAI 호출 실패가 전체 artifact를 잃게 해서는 안 된다.
- query별 status를 `COMPLETED`, `FAILED`, `SKIPPED`로 기록한다.
- retry는 transient failure에 한해 bounded하게 적용한다.
- schema failure는 prompt/model failure와 분리한다.
- partial run도 report를 생성하되 failure count와 effective metric을 표시한다.
- 실패 query를 metric에서 조용히 제외하지 않는다.

## Testing Strategy

### Unit Tests

- Markdown heading path 추출
- code block 보존
- fixed chunk overlap
- heading-aware max token split
- stable chunk ID
- evidence quote matching
- lexical/semantic pair가 같은 evidence와 expected answer를 공유하는지 검증
- technical mixed tokenizer가 identifier와 한글 형태소를 보존하는지 검증
- measured lexical overlap 계산
- BM25 ranking
- cosine ranking
- RRF fusion
- Recall/MRR/nDCG/evidence coverage
- slice aggregation
- intent-cluster bootstrap confidence interval과 deterministic seed
- reranker invalid ID normalization
- citation validation
- refusal classification
- failure taxonomy mapping

### Contract Tests

- embedding endpoint response parsing
- reranker structured output parsing
- generator structured output parsing
- judge structured output parsing
- missing API key error

외부 API contract test는 mock response를 사용한다.

### Smoke Test

small fixture corpus와 3개 query로 다음을 확인한다.

- 두 chunking strategy 생성
- 세 retrieval baseline 실행
- artifact 생성
- Markdown report 생성

### Full Evaluation Verification

최종 portfolio artifact 생성 전:

- corpus snapshot hash 확인
- 24개 answerable intent, paired answerable query 48개, unanswerable query 12개 확인
- 총 60개 query의 `reviewStatus=REVIEWED` 확인
- lexical/semantic pair의 evidence와 expected answer 일치 확인
- semantic/cross-lingual query naturalness와 measured overlap 확인
- all configured variant 실행
- intent-cluster bootstrap confidence interval과 win/tie/loss artifact 확인
- human review 50개 이상 확인
- report limitation section 확인
- secret scan

## Metrics Interpretation Gates

| 조건 | 허용 표현 |
| --- | --- |
| 3-query fixture | runner wiring smoke만 확인 |
| 12-query pilot | metric과 failure schema의 진단만 수행 |
| 60-query full set | 제한된 Sigak 운영 문서에서 variant 비교 |
| 24 paired answerable intents | lexical/semantic robustness와 paired delta 분석 |
| human review 50개 이상 | chunking/generation variant별 judge disagreement 사례 분석 |
| 단일 corpus | 다른 운영 도메인으로 일반화 금지 |

금지 표현:

- “최적의 chunking을 찾았다”
- “hallucination을 제거했다”
- “RAG 정확도를 증명했다”
- “LLM judge가 사람 평가를 대체한다”

허용 표현 예시:

- “24개 information need의 lexical/semantic pair와 12개 unanswerable query로
  60개 평가셋을 구성했다.”
- “Sigak 운영 문서 평가셋에서 heading-aware chunking의 intent-level nDCG@5
  차이를 paired bootstrap confidence interval과 함께 측정했다.”
- “Dense retrieval은 semantic slice에서, BM25는 lexical slice에서 서로 다른
  경향을 보였으며 overall 결과와 함께 보고했다.”
- “reranking은 nDCG@5를 개선했지만 평균 latency와 token usage가 증가했다.”
- “LLM judge와 사람 평가가 불일치한 사례를 별도 failure로 기록했다.”

실제 수치가 나온 뒤에만 개선 또는 저하를 작성한다.

## Portfolio And Self-Introduction Evidence Contract

자기소개서 완료형 주장과 필요한 artifact를 연결한다.

| 주장 | 필요한 증거 |
| --- | --- |
| chunking을 비교했다 | chunk config, chunk artifact, retrieval metric |
| retrieval 방식을 비교했다 | 동일 query set의 system comparison |
| BM25와 dense를 공정하게 비교했다 | paired lexical/semantic intent, tokenizer/model metadata, slice result |
| heading-aware가 더 높게 관찰됐다 | intent-level delta, bootstrap CI, win/tie/loss, regression slice |
| reranking을 적용했다 | before/after ranks, latency, token usage |
| hallucination을 평가했다 | unanswerable query, unsupported claim artifact |
| LLM-as-a-judge를 사용했다 | judge prompt/model version, structured score |
| judge 한계를 검토했다 | human review와 disagreement artifact |
| 실험을 재현 가능하게 기록했다 | run manifest, CLI, GitHub Issue/PR |
| 영어 자료와 협업을 준비했다 | English README summary 또는 report section |

실험이 완료되지 않은 주장은 수행 계획형으로만 작성한다.

## Relationship To The Second-Choice Assignment

이번 구현은 coding agent benchmark를 만들지 않는다.
대신 다음 평가 원칙을 재사용할 수 있게 설계한다.

| Ops RAG evaluation | Coding agent evaluation |
| --- | --- |
| query dataset | issue/PR task dataset |
| corpus snapshot | repository base commit |
| retrieval/generation variant | agent/scaffold variant |
| evidence coverage | FAIL_TO_PASS coverage |
| no regression check | PASS_TO_PASS tests |
| run manifest | agent run configuration |
| failure taxonomy | patch/build/test failure taxonomy |
| human calibration | task/test validity review |

2지망 자기소개서에는 이 공통 평가 사고와 기존 Sigak PR/Issue 검증 경험을 사용한다.
구현하지 않은 Kubernetes anomaly detection과 auto-fix bot은 계획형으로만 작성한다.

## Relationship To The Research Dashboard

기존 research dashboard 설계는 폐기하지 않는다.

순서만 변경한다.

```txt
RAG evaluation artifacts
-> artifact review
-> research overview API
-> /research visualization
```

실제 RAG artifact 없이 dashboard를 먼저 구현하면 과거 smoke 결과만 예쁘게 보여주는
페이지가 될 위험이 있다. 따라서 dashboard 구현은 이번 평가 artifact가 완성된 뒤 재개한다.

## Security And Privacy

- 군 경력 확인서, 군번, 서명, 직인, 내부 시스템 상세를 repository에 추가하지 않는다.
- corpus는 공개 repository 문서만 사용한다.
- OpenAI 요청에는 secret, credential, 개인 정보가 포함되지 않게 corpus allowlist를 검토한다.
- `.env`, API key, raw request header를 artifact에 저장하지 않는다.
- prompt와 response artifact를 commit하기 전 secret scan을 실행한다.

## Implementation Scope And Time Budget

15시간 우선순위:

| 구간 | 목표 시간 |
| --- | ---: |
| corpus와 24 intent/60 query 검토 | 4시간 |
| parser, chunking, evidence mapping | 2시간 |
| BM25, dense, hybrid, paired metrics | 2시간 |
| reranker | 1.5시간 |
| generation, judge, deterministic checks | 1.5시간 |
| full run과 50개 human review | 2.5시간 |
| report, README, 자기소개서 근거 정리 | 1.5시간 |

시간이 부족할 때 제거 순서:

1. report HTML 또는 dashboard
2. 두 번째 reranker model
3. 추가 chunking strategy
4. generation variant의 추가 prompt 실험

제거하면 안 되는 것:

- manually reviewed evidence
- 24개 paired answerable intent와 12개 unanswerable query
- lexical/semantic/cross-lingual slice
- fixed와 heading-aware baseline
- BM25, dense, hybrid 비교
- rerank 전후 artifact
- intent-cluster bootstrap confidence interval
- citation deterministic check
- unanswerable query
- human calibration
- limitation report

## Success Criteria

- 공개 운영 문서 corpus가 Git commit과 hash로 고정된다.
- 24개 answerable intent와 lexical/semantic pair 48개가 같은 expected answer와
  사람이 검토한 evidence를 공유한다.
- unanswerable/adversarial query 12개를 포함해 총 60개 query가 reviewed 상태다.
- 최소 8개의 semantic query가 영어 또는 cross-lingual 표현을 사용한다.
- 최소 2개 chunking strategy가 stable chunk ID를 생성한다.
- BM25, dense, hybrid, hybrid+rerank 결과가 같은 query set에서 비교된다.
- overall, lexical, semantic, cross-lingual slice가 모두 생성된다.
- Recall@5, MRR@5, nDCG@5, evidence coverage, latency가 생성된다.
- nDCG@5 paired delta의 intent-cluster bootstrap 95% confidence interval과
  win/tie/loss count가 생성된다.
- no-RAG와 fixed/heading chunking의 hybrid/hybrid+rerank RAG 답변이 생성된다.
- structured citation과 deterministic citation 검증이 동작한다.
- answerable/unanswerable query가 모두 평가된다.
- LLM judge output과 최소 50개 human review가 저장된다.
- disagreement와 failure case가 report에 표시된다.
- 한 명령으로 full evaluation을 재실행할 수 있다.
- 관련 테스트와 smoke가 실제로 통과한다.
- API key와 민감 정보가 commit되지 않는다.
- README 또는 report에 영어 요약이 포함된다.

## Deferred Work

- public operations chatbot
- PDF/table/image manual parser
- public robot manual corpus
- chunk-level Elasticsearch/Qdrant projection
- graph-aware answer generation
- multi-turn conversation evaluation
- judge model ensemble
- statistical significance test
- CI scheduled evaluation
- coding agent benchmark harness
- Kubernetes anomaly detection
- auto-fix draft PR bot
- `/research` dashboard implementation

## Final Decision

Sigak의 다음 구현은 research dashboard가 아니라 운영 문서 RAG 평가 하네스다.

핵심 순서는 다음과 같다.

```txt
evidence dataset quality
-> deterministic chunking and retrieval baselines
-> reranking
-> grounded generation
-> deterministic checks
-> calibrated LLM judge
-> honest report
-> research dashboard
```

이 순서는 지원 과제의 핵심 역량을 직접 증명하면서 기존 Sigak product architecture를
불필요하게 흔들지 않는 가장 작은 완성 범위다.
