# Sigak Research Strategy

작성일: 2026-05-07

## 1. 목적

이 문서는 Sigak을 단순한 뉴스 웹 애플리케이션이 아니라 LLM/NLP 연구실에도 제출할 수 있는 연구형 포트폴리오로 발전시키기 위한 전략을 정리한다.

Sigak의 기본 제품 방향은 "중요한 기술 뉴스, 맥락과 관계를 포함해 설명하는 서비스"다. 연구형 포트폴리오 방향에서는 여기에 한 단계 더해, Sigak을 다음 주제를 실험하고 평가할 수 있는 시스템으로 만든다.

- retrieval-augmented generation
- technical news summarization
- source-grounded insight generation
- article and concept relationship extraction
- Graph RAG-ready context construction
- LLM output evaluation and self-checking
- small model fine-tuning experiments

핵심 목표는 "AI 기능을 붙인 웹앱"이 아니라 "기술 뉴스 이해를 위한 LLM/NLP 시스템을 설계하고, 구현하고, 평가했다"는 증거를 남기는 것이다.

## 2. 대상 평가자

이 전략은 다음 평가자를 염두에 둔다.

| 대상 | 보여줘야 할 역량 |
| --- | --- |
| LLM/NLP 연구실 | 논문 기반 문제 정의, 실험 설계, 평가 지표, 재현성 |
| 백엔드/AI 인턴십 | Spring Boot 중심 API 설계, FastAPI AI 서버, persistence, test coverage |
| 포트폴리오 리뷰어 | 실행 가능한 제품, 명확한 문서, 데모 가능한 사용자 흐름 |
| 연구형 프로젝트 리뷰어 | baseline 비교, ablation, failure analysis, data/model card |

상위권 포트폴리오로 보이려면 "무엇을 만들었는가"와 "무엇을 검증했는가"가 함께 있어야 한다.

## 3. 포트폴리오 논지

Sigak의 포트폴리오 논지는 다음 한 문장으로 정리한다.

> Sigak is a research-oriented technical news insight platform that combines retrieval-augmented, graph-aware, and evaluated LLM enrichment for understanding important technical changes.

이를 한국어로 풀면 다음과 같다.

> Sigak은 기술 뉴스의 요약, 중요도 판단, 관계 기반 인사이트 생성을 RAG와 평가 파이프라인으로 검증하는 연구형 뉴스 인사이트 플랫폼이다.

이 논지는 세 가지 층으로 구성된다.

1. 제품 층
   - 사용자는 중요한 기술 뉴스를 검색하고 읽는다.
   - 각 기사에는 요약, why-it-matters, topic, related article이 제공된다.

2. 시스템 층
   - Spring Boot가 public API와 persistence를 담당한다.
   - FastAPI가 AI/RAG/research pipeline을 담당한다.
   - raw content와 enrichment output은 분리 저장된다.

3. 연구 층
   - retrieval method를 비교한다.
   - RAG와 no-RAG 요약을 비교한다.
   - graph-aware context가 insight 품질에 미치는 영향을 평가한다.
   - fine-tuned small model이 분류/관계 추출에 유용한지 실험한다.

## 4. 핵심 연구 질문

Sigak의 연구 질문은 제품 기능과 직접 연결되어야 한다.

### 4.1 Retrieval

- 기술 뉴스 도메인에서 BM25, dense retrieval, hybrid retrieval 중 어떤 방식이 가장 안정적인가?
- topic/category 중심 검색과 자연어 query 검색은 서로 다른 retrieval strategy가 필요한가?
- retrieval quality가 summary 또는 why-it-matters 품질에 어떤 영향을 주는가?

### 4.2 Generation

- RAG context를 넣은 요약은 article-only 요약보다 factuality가 높은가?
- related article context를 추가하면 why-it-matters가 더 구체적이고 유용해지는가?
- context가 많아질수록 품질이 좋아지는가, 아니면 noise가 증가하는가?

### 4.3 Relationship-Aware Insight

- article-topic-concept 관계를 저장하면 관련 기사 추천 품질이 좋아지는가?
- LLM이 생성한 relation reason은 사람이 평가했을 때 신뢰할 만한가?
- graph-aware context는 단순 top-k retrieval보다 더 설명력 있는 insight를 만드는가?

### 4.4 Fine-Tuning

- small open-source model을 fine-tuning하면 event type, category, importance bucket 분류에서 zero-shot prompt보다 좋아지는가?
- relation extraction은 prompt-only 방식과 fine-tuned classifier 중 어느 쪽이 더 안정적인가?
- fine-tuning이 비용과 latency 측면에서 OpenAI API 기반 generation을 보완할 수 있는가?

### 4.5 Evaluation

- LLM output의 faithfulness를 어떤 방식으로 측정할 수 있는가?
- automated evaluation과 human evaluation은 어느 지점에서 불일치하는가?
- failure case를 수집하면 prompt, retrieval, fine-tuning data 개선에 어떻게 반영할 수 있는가?

## 5. 논문 기반 개발 트랙

논문은 기능 이름으로 붙이는 것이 아니라, Sigak 문제에 맞게 작게 재현하고 평가해야 한다.

### 5.1 RAG Track

참고 논문:

- Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks

Sigak 적용:

```txt
article raw content
-> chunking
-> retrieval
-> LLM enrichment
-> structured summary / whyItMatters / topics
```

비교 실험:

- no-RAG generation
- single article context generation
- retrieved chunk context generation
- related article context generation

기대 산출물:

- RAG 실험 스크립트
- generation 결과 JSON
- factuality 및 usefulness 평가표
- failure analysis

### 5.2 Retrieval Evaluation Track

참고 논문 및 benchmark:

- Sentence-BERT
- BEIR
- ColBERT

Sigak 적용:

```txt
BM25 keyword retrieval
vs dense embedding retrieval
vs hybrid retrieval
vs optional reranking
```

평가 지표:

- Recall@k
- Precision@k
- MRR
- nDCG
- latency

처음부터 ColBERT를 직접 구현하는 것은 MVP 범위 밖이다. 대신 ColBERT는 late-interaction 또는 reranking 개선 방향으로 문서화하고, MVP에서는 BM25, dense, hybrid 비교를 먼저 구현한다.

### 5.3 Self-Check / Self-RAG-Inspired Track

참고 논문:

- Self-RAG

Sigak 적용:

```txt
generated summary
+ retrieved evidence
-> support check
-> unsupported claim detection
-> confidence / warning metadata
```

완전한 Self-RAG 학습을 재현하지 않는다. MVP에서는 self-check layer로 작게 적용한다.

예상 output:

```json
{
  "faithfulnessLabel": "SUPPORTED",
  "unsupportedClaims": [],
  "evidenceCoverage": 0.82
}
```

이 트랙은 연구실 포트폴리오에서 중요하다. LLM 시스템의 품질을 "잘 생성한다"가 아니라 "근거를 기준으로 검증한다"로 보여줄 수 있기 때문이다.

### 5.4 GraphRAG-Inspired Track

참고:

- Microsoft GraphRAG

Sigak 적용:

```txt
article
-> topics / concepts / entities
-> article-concept edges
-> article-article relation edges
-> graph-aware context selection
-> relationship-aware whyItMatters
```

MVP에서 하지 않을 것:

- full GraphRAG clone
- full graph explorer UI
- broad global summarization over massive corpora

MVP에서 할 것:

- concept extraction
- related article relation reason
- article detail에서 relationship insight 노출
- graph-ready schema와 evaluation dataset 준비

### 5.5 Fine-Tuning Track

참고 논문 및 도구:

- LoRA
- QLoRA
- Hugging Face TRL SFTTrainer
- OpenAI model optimization / fine-tuning docs

Sigak에 적합한 fine-tuning task:

| Task | Input | Output |
| --- | --- | --- |
| Event classification | title, source, raw content | `NEWS`, `RESEARCH`, `SECURITY`, `RELEASE`, `OFFICIAL_ANNOUNCEMENT` |
| Category classification | title, summary, topics | `AI`, `DATA`, `SECURITY`, etc. |
| Importance bucket | article metadata and text | `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` |
| Relation extraction | article pair | relation type and reason |
| Factuality check | evidence plus generated summary | supported / partially supported / unsupported |

권장 방향:

- 제품 기능은 OpenAI API 또는 mock mode로 안정적으로 유지한다.
- 연구 실험은 open-source small model + LoRA/QLoRA로 분리한다.
- 생성 모델 fine-tuning보다 classification/relation/factuality task를 먼저 한다.

이유:

- 작은 데이터셋으로도 실험 가능하다.
- 평가 지표가 명확하다.
- 연구실 포트폴리오에서 "학습을 돌렸다"보다 "명확한 task와 baseline을 비교했다"가 더 강하다.

### 5.6 DSPy-Style Optimization Track

참고:

- DSPy

Sigak 적용 가능성:

```txt
manual prompt
vs few-shot prompt
vs metric-optimized prompt/program
```

이 트랙은 필수는 아니다. retrieval/RAG/fine-tuning 실험이 안정화된 뒤 추가하면 좋다. 다만 "prompt를 감으로 고친다"가 아니라 metric 기반으로 LLM pipeline을 최적화한다는 방향은 연구형 포트폴리오와 잘 맞는다.

## 6. 시스템 아키텍처 방향

Sigak의 research-oriented architecture는 제품 시스템과 실험 시스템을 분리하되, 같은 데이터를 공유하는 구조가 좋다.

```txt
frontend
  -> Spring Boot public API
      -> PostgreSQL
      -> FastAPI AI server
          -> OpenAI API or local model
          -> retrieval / evaluation / experiment modules
```

### 6.1 Spring Boot

역할:

- public REST API
- article list/detail/search API
- persistence orchestration
- API-ready article filtering
- collection pipeline orchestration
- FastAPI 호출
- DTO validation and business rules

Spring Boot는 사용자가 직접 호출하는 안정적인 제품 API를 담당한다.

### 6.2 FastAPI

역할:

- LLM enrichment
- embedding generation
- retrieval experiment logic
- graph-aware context construction
- self-check / factuality evaluation
- fine-tuning inference endpoint

FastAPI는 AI/RAG/research code가 빠르게 변할 수 있는 공간이다.

### 6.3 PostgreSQL

역할:

- source metadata
- article metadata
- raw content
- enrichment history
- topics
- article relations
- future concept/entity tables
- evaluation labels and results metadata

raw content와 enrichment output을 분리하는 현재 방향은 유지한다.

### 6.4 Retrieval Stores

단계적 도입:

1. PostgreSQL / in-memory baseline
2. Elasticsearch or OpenSearch for BM25
3. Qdrant for dense vectors
4. hybrid retrieval layer
5. optional reranking

Elasticsearch와 Qdrant는 MVP 안정화 후 도입한다. 검색 실험의 목적은 "도구를 많이 붙이는 것"이 아니라 retrieval quality를 비교하는 것이다.

### 6.5 Experiments Directory

연구 코드와 결과는 제품 코드와 분리한다.

```txt
experiments/
  datasets/
    raw/
    processed/
    labels/
  retrieval/
    bm25/
    dense/
    hybrid/
  rag/
    prompts/
    runs/
  fine_tuning/
    datasets/
    scripts/
    adapters/
    results/
  evaluation/
    scripts/
    metrics/
    human_eval/
  reports/
```

이 구조는 재현성과 포트폴리오 가독성을 높인다.

## 7. 평가 계획

상위권 포트폴리오는 평가가 있어야 한다. Sigak의 평가는 retrieval, generation, system, human evaluation으로 나눈다.

### 7.1 Retrieval Metrics

- Recall@k
- Precision@k
- MRR
- nDCG
- latency

비교 대상:

- keyword / BM25
- dense retrieval
- hybrid retrieval
- graph-aware retrieval
- optional reranker

### 7.2 Generation Metrics

- faithfulness
- relevance
- clarity
- evidence coverage
- unsupported claim count
- format validity

RAGAS 같은 RAG evaluation framework는 참고하되, Sigak 도메인에 맞는 작고 명확한 metric을 함께 정의한다.

### 7.3 Human Evaluation

사람 평가 기준:

| Criterion | Question |
| --- | --- |
| Factuality | source article과 모순되지 않는가? |
| Usefulness | 기술 독자가 실제로 맥락을 이해하는 데 도움이 되는가? |
| Specificity | 일반론이 아니라 기사와 관련된 구체적 설명인가? |
| Clarity | 초심자도 읽을 수 있지만 얕지 않은가? |
| Relation quality | 관련 기사/개념 연결이 타당한가? |

평가 규모는 처음부터 클 필요 없다. 30-50개 article에 대해 작은 human eval set을 만들고, 이후 100-300개로 확장한다.

### 7.4 System Metrics

- API latency
- enrichment latency
- OpenAI API cost
- local model inference cost
- failure rate
- invalid JSON rate
- retry count

연구형 포트폴리오라도 실제 시스템 운영 지표를 보여주면 엔지니어링 설득력이 커진다.

### 7.5 Failure Analysis

반드시 기록할 failure case:

- retrieval miss
- irrelevant context included
- unsupported generated claim
- vague why-it-matters
- wrong category
- wrong relation
- duplicate article
- malformed structured output

실패 사례는 부끄러운 것이 아니라 연구 포트폴리오의 증거다. 상위권 포트폴리오는 좋은 결과뿐 아니라 한계와 개선 방향을 정직하게 보여준다.

## 8. 최종 산출물

최종 포트폴리오에는 다음 산출물이 포함되어야 한다.

### 8.1 Product Demo

- local run guide
- article list/search/detail flow
- AI summary and why-it-matters
- related articles or concepts
- 2-3분 demo video

### 8.2 Research Report

위치 예시:

```txt
docs/research/SIGAK_RESEARCH_REPORT.md
```

구성:

- abstract
- problem definition
- related work
- system architecture
- dataset
- methods
- experiments
- results
- failure analysis
- limitations
- future work

### 8.3 Experiment Results

위치 예시:

```txt
experiments/results/
```

포함 내용:

- raw model outputs
- retrieval run results
- evaluation scores
- prompt versions
- model versions
- run metadata

### 8.4 Data Card

위치 예시:

```txt
docs/research/DATA_CARD.md
```

포함 내용:

- source list
- collection criteria
- exclusion rules
- labeling process
- known bias
- copyright and attribution notes
- data refresh policy

### 8.5 Model / Pipeline Card

위치 예시:

```txt
docs/research/MODEL_CARD.md
```

포함 내용:

- model/provider
- prompt versions
- retrieval configuration
- fine-tuning configuration
- intended use
- limitations
- failure modes

## 9. 단계별 우선순위

### Phase R0: Stabilize Current MVP

목표:

- 현재 제품 기반을 안정화한다.

작업:

- API-ready article filtering
- related article stale state fix
- FastAPI input validation
- current tests 유지

### Phase R1: End-to-End Collection and Enrichment

목표:

```txt
source -> collect -> normalize -> enrich -> persist -> display
```

작업:

- real source fetch boundary
- duplicate detection
- persistence writer
- mock and real enrichment mode
- Spring Boot to FastAPI HTTP integration

### Phase R2: Research Dataset and Labels

목표:

- 실험 가능한 작은 gold dataset을 만든다.

작업:

- 100-300 article dataset
- 30-50 manually reviewed evaluation examples
- category/event labels
- relevant article IDs
- summary/whyItMatters reference examples
- evidence span labels if feasible

### Phase R3: Retrieval Benchmark

목표:

- technical news retrieval에서 baseline을 비교한다.

작업:

- BM25 baseline
- dense embedding baseline
- hybrid retrieval
- Recall@k / MRR / nDCG report
- failure analysis

### Phase R4: RAG and Graph-Aware Generation

목표:

- RAG와 graph-aware context가 generation 품질을 개선하는지 검증한다.

작업:

- no-RAG vs RAG comparison
- related article context experiment
- concept/relation extraction
- graph-aware context selection
- faithfulness/usefulness evaluation

### Phase R5: Fine-Tuning Experiment

목표:

- small model fine-tuning이 특정 NLP task에서 유용한지 검증한다.

권장 task:

- event type classification
- primary category classification
- importance bucket classification
- relation type classification
- factuality classification

작업:

- baseline zero-shot
- baseline few-shot
- small model LoRA/QLoRA fine-tuning
- evaluation table
- cost/latency comparison

### Phase R6: Portfolio Packaging

목표:

- 연구형 포트폴리오로 읽히게 정리한다.

작업:

- README 상단에 research thesis 추가
- architecture diagram 추가
- experiment result table 추가
- research report 작성
- data card / model card 작성
- demo video 제작

## 10. 비목표

상위권 포트폴리오를 목표로 하더라도 다음은 MVP 이전에 하지 않는다.

- broad open-web crawler
- full GraphRAG clone
- full Obsidian-style graph explorer
- large model full fine-tuning
- production-scale scheduler
- personalized recommendation
- user accounts
- advanced dashboard
- premature multi-agent architecture

이 프로젝트의 강점은 큰 시스템을 흉내 내는 것이 아니라, 작은 범위에서 제품-데이터-모델-평가를 끝까지 연결하는 것이다.

## 11. 성공 기준

Sigak이 연구형 상위권 포트폴리오로 보이려면 다음 기준을 만족해야 한다.

### Product Criteria

- 로컬에서 쉽게 실행된다.
- article list/search/detail 흐름이 안정적으로 동작한다.
- AI-generated summary와 why-it-matters가 저장되고 표시된다.
- related article 또는 related concept가 사용자에게 보인다.

### Research Criteria

- 최소 2개 이상의 retrieval baseline을 비교한다.
- no-RAG와 RAG generation을 비교한다.
- graph-aware context 실험이 있다.
- fine-tuning 또는 small model experiment가 하나 이상 있다.
- 결과표와 failure analysis가 있다.

### Engineering Criteria

- backend, frontend, ai server가 역할별로 분리되어 있다.
- tests가 핵심 API와 AI endpoint를 검증한다.
- raw content, enrichment, relation metadata가 분리 저장된다.
- `.env.example`과 local run guide가 명확하다.

### Documentation Criteria

- README가 제품과 연구 방향을 동시에 설명한다.
- ADR이 주요 기술 결정을 기록한다.
- research report가 실험 과정을 재현 가능하게 설명한다.
- data card와 model/pipeline card가 한계와 윤리적 고려를 포함한다.

## 12. 결론

Sigak의 차별점은 "뉴스를 요약하는 앱"이 아니다. 진짜 차별점은 기술 뉴스라는 좁고 의미 있는 도메인에서 RAG, retrieval evaluation, relationship-aware insight, fine-tuning experiment, LLM evaluation을 한 시스템 안에 연결하는 것이다.

가장 좋은 개발 순서는 다음과 같다.

```txt
stable MVP
-> end-to-end collection/enrichment
-> evaluation dataset
-> retrieval benchmark
-> RAG and graph-aware generation
-> fine-tuning experiment
-> research report and portfolio packaging
```

이 순서를 따르면 Sigak은 취업용 CRUD 프로젝트가 아니라, 제품화된 LLM/NLP 연구 시스템으로 보일 수 있다.

## 13. 참고 자료

- Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks: https://arxiv.org/abs/2005.11401
- Sentence-BERT: https://arxiv.org/abs/1908.10084
- BEIR: https://arxiv.org/abs/2104.08663
- ColBERT: https://arxiv.org/abs/2004.12832
- Self-RAG: https://arxiv.org/abs/2310.11511
- Microsoft GraphRAG: https://www.microsoft.com/en-us/research/project/graphrag/
- RAGAS: https://arxiv.org/abs/2309.15217
- LoRA: https://arxiv.org/abs/2106.09685
- QLoRA: https://arxiv.org/abs/2305.14314
- Hugging Face TRL SFTTrainer: https://huggingface.co/docs/trl/sft_trainer
- OpenAI model optimization docs: https://developers.openai.com/api/docs/guides/model-optimization
- DSPy: https://arxiv.org/abs/2310.03714
