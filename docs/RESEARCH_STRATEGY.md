# Sigak Research Strategy

[English](RESEARCH_STRATEGY.md) | [한국어](RESEARCH_STRATEGY.ko.md)

Created: 2026-05-07

## 1. Purpose

This document describes how Sigak can grow from a news web application into a research-oriented LLM/NLP portfolio project.

The product direction is: important technical news, explained with context and relationships. The research direction adds a second layer: Sigak should become a system where retrieval, RAG, relationship extraction, evaluation, and small-model experiments can be implemented and measured.

The goal is not to say "this app uses AI." The goal is to leave evidence that the project designed, implemented, and evaluated an LLM/NLP system for understanding technical news.

Research themes:

- retrieval-augmented generation
- technical news summarization
- source-grounded insight generation
- article and concept relationship extraction
- Graph RAG-ready context construction
- LLM output evaluation and self-checking
- small-model fine-tuning experiments

## 2. Target Reviewers

| Reviewer | What the project should demonstrate |
| --- | --- |
| LLM/NLP lab | Paper-inspired problem definition, experiment design, metrics, reproducibility |
| Backend/AI internship | Spring Boot API design, FastAPI AI server, persistence, test coverage |
| Portfolio reviewer | Working product, clear documentation, demoable user flow |
| Research project reviewer | Baselines, ablations, failure analysis, data/model cards |

A strong portfolio needs both: what was built and what was verified.

## 3. Portfolio Thesis

Sigak's thesis:

> Sigak is a research-oriented technical news insight platform that combines retrieval-augmented, graph-aware, and evaluated LLM enrichment for understanding important technical changes.

The thesis has three layers.

1. Product layer:
   - Users search and read important technical news.
   - Each article has a summary, why-it-matters explanation, topics, and related articles.

2. System layer:
   - Spring Boot owns public APIs and persistence.
   - FastAPI owns AI/RAG/research workflows.
   - Raw content and enrichment outputs are stored separately.

3. Research layer:
   - Compare retrieval methods.
   - Compare RAG and no-RAG generation.
   - Evaluate whether graph-aware context improves insight quality.
   - Test whether fine-tuned small models help with classification or relationship extraction.

## 4. Research Questions

The research questions should directly connect to product behavior.

### 4.1 Retrieval

- Which retrieval method is most stable for technical news: BM25, dense retrieval, or hybrid retrieval?
- Do topic/category searches and natural language queries need different retrieval strategies?
- How does retrieval quality affect summary and why-it-matters quality?

### 4.2 Generation

- Are RAG-based summaries more faithful than article-only summaries?
- Does related-article context make why-it-matters output more specific and useful?
- Does adding more context improve quality, or does it introduce noise?

### 4.3 Relationship-Aware Insight

- Does storing article-topic-concept relationships improve related article recommendations?
- Are LLM-generated relation reasons reliable when judged by humans?
- Does graph-aware context produce more explanatory insight than simple top-k retrieval?

### 4.4 Fine-Tuning

- Does fine-tuning a small open-source model improve event type, category, or importance bucket classification over zero-shot prompting?
- Is relation extraction more stable with prompt-only methods or a fine-tuned classifier?
- Can fine-tuning help reduce cost or latency compared with API-based generation for narrow tasks?

### 4.5 Evaluation

- How should faithfulness be measured for LLM output?
- Where do automated evaluation and human evaluation disagree?
- How can failure cases improve prompts, retrieval, and fine-tuning data?

## 5. Paper-Inspired Tracks

Papers should not be used as feature labels. They should inspire small, testable experiments inside Sigak.

### 5.1 RAG Track

Reference:

- Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks

Sigak application:

```txt
article raw content
-> chunking
-> retrieval
-> LLM enrichment
-> structured summary / whyItMatters / topics
```

Comparisons:

- no-RAG generation
- single article context generation
- retrieved chunk context generation
- related article context generation

Expected outputs:

- RAG experiment scripts
- generation result JSON
- factuality and usefulness score tables
- failure analysis

### 5.2 Retrieval Evaluation Track

References:

- Sentence-BERT
- BEIR
- ColBERT

Sigak application:

```txt
BM25 keyword retrieval
vs dense embedding retrieval
vs hybrid retrieval
vs optional reranking
```

Metrics:

- Recall@k
- Precision@k
- MRR
- nDCG
- latency

Implementing ColBERT directly is outside the MVP scope. It can be documented as a later late-interaction or reranking direction. The MVP should first compare BM25, dense, and hybrid retrieval.

### 5.3 Self-Check / Self-RAG-Inspired Track

Reference:

- Self-RAG

Sigak application:

```txt
generated summary
+ retrieved evidence
-> support check
-> unsupported claim detection
-> confidence / warning metadata
```

The MVP should not reproduce full Self-RAG training. It should start with a small self-check layer.

Example output:

```json
{
  "faithfulnessLabel": "SUPPORTED",
  "unsupportedClaims": [],
  "evidenceCoverage": 0.82
}
```

This track matters because it shows that the LLM system is evaluated against evidence, not only generated text.

### 5.4 GraphRAG-Inspired Track

Reference:

- Microsoft GraphRAG

Sigak application:

```txt
article
-> topics / concepts / entities
-> article-concept edges
-> article-article relation edges
-> graph-aware context selection
-> relationship-aware whyItMatters
```

Out of scope for MVP:

- full GraphRAG clone
- full graph explorer UI
- broad global summarization over large corpora

In scope for MVP direction:

- concept extraction
- related article relation reasons
- relationship insight on article detail
- graph-ready schema and evaluation dataset

### 5.5 Fine-Tuning Track

References and tools:

- LoRA
- QLoRA
- Hugging Face TRL SFTTrainer
- OpenAI model optimization / fine-tuning docs

Good first fine-tuning tasks for Sigak:

| Task | Input | Output |
| --- | --- | --- |
| Event classification | title, source, raw content | `NEWS`, `RESEARCH`, `SECURITY`, `RELEASE`, `OFFICIAL_ANNOUNCEMENT` |
| Category classification | title, summary, topics | `AI`, `DATA`, `SECURITY`, etc. |
| Importance bucket | article metadata and text | `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` |
| Relation extraction | article pair | relation type and reason |
| Factuality check | evidence plus generated summary | supported / partially supported / unsupported |

Recommended direction:

- Keep the product stable with OpenAI API mode or mock mode.
- Keep research experiments separate with open-source small models and LoRA/QLoRA.
- Start with classification, relation, or factuality tasks before generation fine-tuning.

Why:

- Small datasets can still support meaningful experiments.
- Metrics are clearer.
- A clear task and baseline comparison is stronger than simply "running training."

### 5.6 DSPy-Style Optimization Track

Reference:

- DSPy

Possible Sigak experiment:

```txt
manual prompt
vs few-shot prompt
vs metric-optimized prompt/program
```

This track is optional. It becomes useful after retrieval, RAG, and fine-tuning experiments are stable. The important idea is to optimize LLM pipelines with metrics instead of intuition alone.

## 6. System Architecture Direction

The research-oriented architecture should separate the product system and the experiment system while sharing the same data.

```txt
frontend
  -> Spring Boot public API
      -> PostgreSQL
      -> FastAPI AI server
          -> OpenAI API or local model
          -> retrieval / evaluation / experiment modules
```

### 6.1 Spring Boot

Responsibilities:

- public REST API
- article list/detail/search API
- persistence orchestration
- API-ready article filtering
- collection pipeline orchestration
- FastAPI calls
- DTO validation and business rules

Spring Boot should stay the stable product API boundary.

### 6.2 FastAPI

Responsibilities:

- LLM enrichment
- embedding generation
- retrieval experiment logic
- graph-aware context construction
- self-check / factuality evaluation
- fine-tuning inference endpoint

FastAPI is the place where AI/RAG/research code can evolve quickly.

### 6.3 PostgreSQL

Responsibilities:

- source metadata
- article metadata
- raw content
- enrichment history
- topics
- article relations
- future concept/entity tables
- evaluation labels and result metadata

The current direction of separating raw content and enrichment output should remain.

### 6.4 Retrieval Stores

Phased introduction:

1. PostgreSQL / in-memory baseline
2. Elasticsearch or OpenSearch for BM25
3. Qdrant for dense vectors
4. hybrid retrieval layer
5. optional reranking

Elasticsearch and Qdrant should come after MVP stabilization. The goal is to compare retrieval quality, not to attach as many tools as possible.

### 6.5 Experiments Directory

Research code and results should be separated from product code.

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

This structure improves reproducibility and portfolio readability.

## 7. Evaluation Plan

A strong portfolio needs evaluation. Sigak evaluation should cover retrieval, generation, system metrics, and human review.

### 7.1 Retrieval Metrics

- Recall@k
- Precision@k
- MRR
- nDCG
- latency

Compare:

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

RAG evaluation frameworks such as RAGAS can be referenced, but Sigak should also define small metrics that fit the technical news domain.

### 7.3 Human Evaluation

Human review criteria:

| Criterion | Question |
| --- | --- |
| Factuality | Does the output contradict the source article? |
| Usefulness | Does it help a technical reader understand the context? |
| Specificity | Is it specific to the article, not generic commentary? |
| Clarity | Is it beginner-friendly without being shallow? |
| Relation quality | Are related articles or concepts actually relevant? |

The initial evaluation set does not need to be large. Start with 30-50 articles, then expand to 100-300.

### 7.4 System Metrics

- API latency
- enrichment latency
- OpenAI API cost
- local model inference cost
- failure rate
- invalid JSON rate
- retry count

Even a research portfolio becomes more convincing when it includes operational system metrics.

### 7.5 Failure Analysis

Failure cases to record:

- retrieval miss
- irrelevant context included
- unsupported generated claim
- vague why-it-matters
- wrong category
- wrong relation
- duplicate article
- malformed structured output

Failure cases are evidence, not embarrassment. Strong portfolios show limitations and improvement paths honestly.

## 8. Final Deliverables

### 8.1 Product Demo

- local run guide
- article list/search/detail flow
- AI summary and why-it-matters
- related articles or concepts
- 2-3 minute demo video

### 8.2 Research Report

Example location:

```txt
docs/research/SIGAK_RESEARCH_REPORT.md
```

Suggested sections:

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

Example location:

```txt
experiments/results/
```

Contents:

- raw model outputs
- retrieval run results
- evaluation scores
- prompt versions
- model versions
- run metadata

### 8.4 Data Card

Example location:

```txt
docs/research/DATA_CARD.md
```

Contents:

- source list
- collection criteria
- exclusion rules
- labeling process
- known bias
- copyright and attribution notes
- data refresh policy

### 8.5 Model / Pipeline Card

Example location:

```txt
docs/research/MODEL_CARD.md
```

Contents:

- model/provider
- prompt versions
- retrieval configuration
- fine-tuning configuration
- intended use
- limitations
- failure modes

## 9. Phase Priorities

### Phase R0: Stabilize Current MVP

Goal:

- Stabilize the current product foundation.

Tasks:

- API-ready article filtering
- related article stale state fix
- FastAPI input validation
- keep current tests passing

### Phase R1: End-to-End Collection and Enrichment

Goal:

```txt
source -> collect -> normalize -> enrich -> persist -> display
```

Tasks:

- real source fetch boundary
- duplicate detection
- persistence writer
- mock and real enrichment mode
- Spring Boot to FastAPI HTTP integration

### Phase R2: Research Dataset and Labels

Goal:

- Create a small gold dataset for experiments.

Tasks:

- 100-300 article dataset
- 30-50 manually reviewed evaluation examples
- category/event labels
- relevant article IDs
- summary/whyItMatters reference examples
- evidence span labels if feasible

### Phase R3: Retrieval Benchmark

Goal:

- Compare baselines for technical news retrieval.

Tasks:

- BM25 baseline
- dense embedding baseline
- hybrid retrieval
- Recall@k / MRR / nDCG report
- failure analysis

### Phase R4: RAG and Graph-Aware Generation

Goal:

- Test whether RAG and graph-aware context improve generation quality.

Tasks:

- no-RAG vs RAG comparison
- related article context experiment
- concept/relation extraction
- graph-aware context selection
- faithfulness/usefulness evaluation

### Phase R5: Fine-Tuning Experiment

Goal:

- Test whether small-model fine-tuning helps specific NLP tasks.

Recommended tasks:

- event type classification
- primary category classification
- importance bucket classification
- relation type classification
- factuality classification

Implementation:

- zero-shot baseline
- few-shot baseline
- small-model LoRA/QLoRA fine-tuning
- evaluation table
- cost/latency comparison

### Phase R6: Portfolio Packaging

Goal:

- Make the project read as a research-oriented portfolio.

Tasks:

- add research thesis near the top of README
- add architecture diagram
- add experiment result table
- write research report
- write data card and model card
- create demo video

## 10. Non-Goals

Before MVP stability, do not build:

- broad open-web crawler
- full GraphRAG clone
- full Obsidian-style graph explorer
- large-model full fine-tuning
- production-scale scheduler
- personalized recommendation
- user accounts
- advanced dashboard
- premature multi-agent architecture

The strength of this project should come from connecting product, data, model, and evaluation end to end in a narrow but meaningful scope.

## 11. Success Criteria

### Product Criteria

- The project is easy to run locally.
- Article list/search/detail flows work reliably.
- AI-generated summary and why-it-matters outputs are stored and displayed.
- Related articles or concepts are visible to users.

### Research Criteria

- At least two retrieval baselines are compared.
- No-RAG and RAG generation are compared.
- There is a graph-aware context experiment.
- There is at least one fine-tuning or small-model experiment.
- Results tables and failure analysis are included.

### Engineering Criteria

- Backend, frontend, and AI server have clear responsibilities.
- Tests cover key APIs and AI endpoints.
- Raw content, enrichment, and relation metadata are stored separately.
- `.env.example` and local run guides are clear.

### Documentation Criteria

- README explains both the product and research direction.
- ADRs record major technical decisions.
- The research report makes experiments reproducible.
- Data and model/pipeline cards include limitations and ethical considerations.

## 12. Conclusion

Sigak's differentiator is not "a news summarizer." The differentiator is a system that connects RAG, retrieval evaluation, relationship-aware insight, fine-tuning experiments, and LLM evaluation inside a focused technical news domain.

Recommended order:

```txt
stable MVP
-> end-to-end collection/enrichment
-> evaluation dataset
-> retrieval benchmark
-> RAG and graph-aware generation
-> fine-tuning experiment
-> research report and portfolio packaging
```

If this order is followed, Sigak can read as a productized LLM/NLP research system rather than an ordinary CRUD project.

## 13. References

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
