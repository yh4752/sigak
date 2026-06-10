# Sigak Roadmap

[English](ROADMAP.md) | [한국어](ROADMAP.ko.md)

Last updated: 2026-06-09

This roadmap is the single source for Sigak's product and research execution plan. It combines the previous MVP roadmap, master service roadmap, and research implementation roadmap into two coordinated tracks.

## 1. Roadmap Principles

Sigak has two layers:

- **User-facing service:** article list, article detail, search, AI summary, why-it-matters insight, topics, and related articles.
- **Research dashboard:** an in-service page that visualizes retrieval, RAG, graph-aware insight, fine-tuning, and failure analysis results.

Execution order:

```txt
working service
-> reliable collection/enrichment
-> reproducible experiments
-> research visualization
-> deployment and portfolio packaging
```

Research features should come from the real pipeline, not from decorative charts. Mock mode stays available for local development, while real LLM enrichment or additional local-model modes can be added later.

## 2. Status Legend

- [x] Implemented or documented enough for the current phase
- [ ] Not implemented yet

## 3. Current Foundation

Status: mostly complete

- [x] Project guidance documents
- [x] Initial monorepo structure: `backend/`, `frontend/`, `ai/`, `infra/`
- [x] Kotlin Spring Boot backend
- [x] React, TypeScript, and Vite frontend
- [x] FastAPI mock AI server
- [x] PostgreSQL persistence foundation
- [x] Article list/detail/search API
- [x] Curated seed article data
- [x] Source registry
- [x] RSS/Atom and arXiv collector boundaries
- [x] Collection-to-persistence pipeline with duplicate detection
- [x] Product, API, source policy, ADR, and research strategy docs

Immediate stabilization risks:

- [x] Backend public article APIs should expose only API-ready articles.
- [x] Article detail should clear stale related articles on navigation.
- [x] FastAPI enrichment schema should reject whitespace-only inputs and constrain importance scores.

## 4. Three-Week Portfolio MVP Reset

Status: in progress; the Elasticsearch/Qdrant/hybrid search slice, Neo4j internal graph projection/context lookup, public graph-aware article detail, internal collection trigger, command runner, failure diagnostics lookup, runtime failure sample, local demo flow, static retrieval-labeling UI, smoke-verified catalog export command, retrieval benchmark smoke runner, keyword/vector/strict-hybrid/public comparison runner, graph-aware evaluation runner, and first portfolio README/demo-flow refresh are in place. A 3-query/6-article graph-aware smoke has been run, but real enrichment, larger labeled dataset, research dashboard, and final release packaging remain pending.

Target period: 2026-05-27 to 2026-06-16

The next milestone is Sigak v0.1, a public portfolio MVP that recreates a small but complete AI search and RAG-ready service flow. The goal is not to build a production-grade RAG platform in three weeks. The goal is to connect the full vertical slice clearly enough that a reviewer can run, inspect, and understand the system.

Target demo flow:

```txt
selected source collection
-> PostgreSQL source-of-truth storage
-> Elasticsearch keyword indexing
-> FastAPI real embedding boundary
-> Qdrant vector indexing
-> Neo4j article/topic/relation projection
-> hybrid search with RRF
-> graph-aware article detail
-> local metrics and retrieval benchmark
```

Included in v0.1:

- controlled collection trigger
- indexing rebuild trigger
- Elasticsearch-backed keyword search
- real embedding model mode for semantic retrieval
- Qdrant-backed vector search
- hybrid search using reciprocal rank fusion
- Neo4j graph projection for articles, topics, and article relations
- article detail relationship reasons or related concepts
- indexing and search latency metrics
- small retrieval benchmark with 10-15 labeled queries
- `/research` or report-based metrics view
- portfolio-focused README, ADR, and demo script

Deferred from v0.1:

- full GraphRAG chatbot
- Airflow orchestration
- user accounts and saved articles
- full graph explorer
- large-scale retrieval benchmark
- production observability stack

Milestones:

| Date | Milestone | Exit signal |
| --- | --- | --- |
| 2026-06-02 | Search infrastructure slice | Articles can be indexed into Elasticsearch and Qdrant, then searched through keyword, vector, and hybrid modes. |
| 2026-06-09 | Graph and metrics slice | Neo4j projection, graph-aware detail, indexing metrics, latency metrics, and retrieval benchmark artifacts are reproducible. |
| 2026-06-16 | Sigak v0.1 portfolio MVP | README, ADR, demo script, tests, and release notes are ready for portfolio review. |

## 5. Service Track

### Phase S1: Service MVP Stabilization

Goal: make the current article browsing experience stable enough to demo without the research dashboard.

- [x] Expose only `PUBLISHED` articles with current enrichment in public article list/search/detail APIs.
- [x] Push API-ready filtering into repository/service logic before response mapping.
- [x] Add backend tests for articles without current enrichment.
- [x] Clear `relatedArticles` whenever article detail changes.
- [x] Guard or cancel stale related article fetches.
- [x] Reject whitespace-only enrichment request text in FastAPI.
- [x] Constrain `suggestedImportanceScore` to `0-100`.
- [x] Run backend, frontend, and AI test suites after the fixes.

Exit criteria:

- `/`, `/articles/:id`, and `GET /api/articles` remain stable with persisted data.
- Seed data demo works without a paid API key.
- The known code review findings are fixed.

### Phase S2: End-to-End Collection and Enrichment

Goal: connect source collection, enrichment, persistence, and UI.

Target flow:

```txt
source registry
-> fetch
-> parse
-> normalize
-> enrich
-> persist
-> publish
-> display
```

- [x] Implement source fetch execution for selected RSS/Atom and arXiv sources.
- [x] Add duplicate detection using canonical URL, external source ID, title, source, and published date.
- [ ] Add collection status transitions for discovered, extracted, enriched, published, and failed states.
- [x] Add a persistence writer for collected articles plus enrichment output.
- [x] Add an internal/admin trigger or command runner for source collection.
- [ ] Add a Spring Boot HTTP FastAPI enrichment client behind the current enrichment boundary.
- [x] Keep mock enrichment mode available for local development.
- [x] Document the local backend, frontend checks, PostgreSQL, AI server, collection trigger, diagnostics, projection rebuild, and search metrics flow.

Exit criteria:

- At least one collected article can appear in `/api/articles` and the frontend.
- Raw content and enrichment output are stored separately.
- Local development still works without external API keys.

### Phase S3: Collection Operations and Real AI Enrichment

Goal: make collection executable from a controlled entry point, then allow switching from mock enrichment to FastAPI-backed real enrichment.

- [x] Add a command runner wrapper for controlled collection runs.
- [x] Return fetched, published, skipped, and failed counts for each run.
- [x] Record enough persistent failure information to debug bad feeds or invalid collected articles across runs.
- [x] Add an internal read-only diagnostics endpoint for collection failure events.
- [ ] Add `AI_ENRICHMENT_MODE=mock|openai|local`.
- [ ] Implement an OpenAI or local-model enrichment service in FastAPI.
- [ ] Use structured output for summary, why-it-matters, topics, category, and importance candidate.
- [ ] Record model name, prompt version, latency, and cost metadata.
- [ ] Add invalid-output retry or fallback behavior.
- [ ] Preserve enrichment history instead of overwriting all evidence.

Exit criteria:

- The same article can be enriched in mock mode and real mode.
- Real mode output is schema-valid.
- API keys are read only from environment variables.

### Phase S4: Search Hardening

Goal: move from MVP search toward a public hybrid search slice that demonstrates keyword, vector, and fused retrieval while keeping PostgreSQL as the source of truth.

- [x] Add Elasticsearch-backed keyword indexing and search.
- [x] Add FastAPI embedding boundary for local vector indexing.
- [x] Add a real embedding model mode for the main vector search path.
- [x] Add Qdrant-backed article vector search.
- [x] Add hybrid search with reciprocal rank fusion over Elasticsearch and Qdrant results.
- [x] Keep deterministic embedding mode available as fallback/test mode for reproducible local smoke tests.
- [x] Keep the article response shape stable as implementation changes.

Exit criteria:

- Search behavior remains stable from the frontend perspective.
- Keyword, vector, and hybrid search can be compared from the same query set.
- Search implementation can evolve without changing article cards/detail pages unnecessarily.

### Phase S5: Limited Graph-Aware Insight

Goal: make article relationships useful through a small Neo4j projection before building a full graph explorer.

- [x] Add graph-ready article metadata fields.
- [x] Add related article ID metadata to curated articles.
- [x] Project articles and topics into Neo4j.
- [x] Store or project article-topic relationships.
- [x] Store article-article relation reasons.
- [x] Show relation reasons or related concepts on article detail.
- [x] Evaluate graph-aware context against simpler retrieval baselines.

Exit criteria:

- Article detail explains why related articles or concepts matter.
- Graph work remains limited, useful, and evidence-backed.

### Phase S6: Research Dashboard MVP

Goal: add a `/research` page inside the product to visualize real experiment results.

Initial route:

```txt
/research
```

Later routes:

```txt
/research/retrieval
/research/rag
/research/graph
/research/fine-tuning
/research/failures
```

Initial backend API candidates:

```txt
GET /api/research/overview
GET /api/research/retrieval-summary
GET /api/research/rag-summary
GET /api/research/failure-cases
```

- [ ] Read initial dashboard data from `experiments/results/*.json`.
- [ ] Show dataset summary, retrieval benchmark, RAG evaluation, cost/latency, and failure cases.
- [ ] Use tables and charts, not exaggerated claims.
- [ ] Keep the dashboard visually distinct from the article reading flow.

Exit criteria:

- `/research` displays real experiment outputs.
- Failure cases are visible, not hidden.
- The dashboard strengthens the product story instead of replacing the product.

### Phase S7: Local Development, Deployment, and Polish

Goal: make Sigak easy to run, review, and deploy.

- [x] Add root-level local environment example values.
- [x] Add backend and frontend local run instructions.
- [x] Add PostgreSQL Docker Compose setup.
- [ ] Expand Docker Compose for backend, frontend, AI server, and database as needed.
- [ ] Document service-specific environment variables.
- [ ] Decide deployment target.
- [x] Add a local collection-to-projection demo script.
- [x] Add a compact architecture diagram to the root README.
- [ ] Prepare final README for portfolio review.

Exit criteria:

- A reviewer can run the project from README instructions.
- The service and research dashboard are demo-ready.

## 6. Research Track

### Phase R1: Research Dataset and Labels

Goal: create a small, reproducible dataset from Sigak's service data.

- [x] Prepare a static HTML labeling tool for query/article relevance labels.
- [x] Create `experiments/README.md`.
- [x] Export API-ready PostgreSQL articles to frozen catalog JSON for the labeling flow; local smoke generated `experiments/datasets/raw/articles.catalog.json` with 6 articles.
- [ ] Add deterministic chunking with stable chunk IDs.
- [x] Create `experiments/datasets/raw/`.
- [x] Create `experiments/datasets/processed/`.
- [x] Create `experiments/datasets/labels/`.
- [ ] Create 30-50 manually reviewed evaluation examples.
- [ ] Write `docs/research/DATA_CARD.md`.

Exit criteria:

- Dataset can be regenerated.
- Source policy and limitations are documented.

### Phase R2: Retrieval Benchmark

Goal: compare retrieval methods for technical news understanding.

- [ ] Create a query set from labeled examples.
- [ ] Implement keyword or BM25 baseline.
- [ ] Implement dense retrieval baseline.
- [ ] Implement hybrid retrieval baseline after keyword/dense baselines exist.
- [ ] Compute Recall@k, Precision@k, MRR, nDCG, and latency.
- [ ] Save outputs under `experiments/results/retrieval/`.
- [ ] Write `experiments/reports/retrieval-benchmark.md`.

Exit criteria:

- At least two retrieval baselines are compared.
- The report explains quality, latency, and complexity trade-offs.

### Phase R3: RAG Evaluation

Goal: evaluate whether retrieval improves summary and why-it-matters generation.

- [ ] Define structured generation output schema.
- [ ] Compare no-RAG generation, article-only context, retrieved-context RAG, and related-article context.
- [ ] Evaluate faithfulness, relevance, clarity, evidence coverage, latency, and cost.
- [ ] Save raw outputs and evaluation scores.
- [ ] Write `experiments/reports/rag-evaluation.md`.

Exit criteria:

- No-RAG and RAG variants are compared honestly.
- The report identifies when retrieval helps and when it hurts.

### Phase R4: Graph-Aware Insight Experiment

Goal: test whether concept and relationship context improves insight quality.

- [ ] Add concept extraction schema.
- [ ] Generate article-concept edges for dataset articles.
- [ ] Generate article-article relation candidates.
- [ ] Add a small human-reviewed relation set.
- [x] Add a public graph-aware smoke evaluation runner that records search misses, related baseline coverage, graph reason coverage, topic coverage, and public round-trip latency.
- [ ] Compare graph-aware context against top-k retrieval context.
- [ ] Write `experiments/reports/graph-aware-insight.md`.

Exit criteria:

- Relationship outputs include type, reason, and confidence where possible.
- The report includes accepted, rejected, and ambiguous relation examples.

### Phase R5: Fine-Tuning Experiment

Goal: use fine-tuning as a controlled NLP experiment, not a decorative feature.

Recommended first tasks:

- event type classification
- primary category classification
- importance bucket classification

- [ ] Create train/eval JSONL for one classification task.
- [ ] Add zero-shot and few-shot prompt baselines.
- [ ] Add LoRA or QLoRA training for a small open-source model.
- [ ] Evaluate accuracy, macro F1, format error rate, cost, and latency.
- [ ] Save adapter metadata without committing model weights.
- [ ] Write `experiments/reports/fine-tuning-classification.md`.

Exit criteria:

- At least one fine-tuning experiment is reproducible.
- The report states honestly whether fine-tuning helped.

### Phase R6: Portfolio Research Packaging

Goal: package the research contribution so LLM/NLP reviewers can understand it quickly.

- [ ] Write `docs/research/SIGAK_RESEARCH_REPORT.md`.
- [ ] Write `docs/research/MODEL_CARD.md`.
- [ ] Update `docs/research/DATA_CARD.md`.
- [ ] Add experiment result table to README.
- [ ] Add reproduction commands for at least one experiment.
- [ ] Add limitations and future work.

Exit criteria:

- A reviewer can understand the product and research contribution in under five minutes.
- A technical reviewer can reproduce at least one experiment.

## 7. Recommended Execution Order

```txt
Day 1: reset v0.1 scope and documentation
-> Week 1: compose, collection trigger, indexing, keyword/vector/hybrid search
-> Week 2: Neo4j projection, graph-aware detail, metrics, retrieval benchmark
-> Week 3: research view, README, ADR, demo script, tests, release notes
```

Important dependencies:

- PostgreSQL remains the source of truth; Elasticsearch, Qdrant, and Neo4j are rebuildable projections.
- Collection and indexing triggers should exist before frontend or research UI work depends on new data.
- Hybrid search should be benchmarked against keyword and vector modes before claiming quality improvement.
- Graph-aware insight should stay limited to relationship reasons or related concepts in v0.1.

## 8. Non-Goals Before Sigak v0.1

- user accounts
- saved articles
- personalized recommendations
- full graph explorer
- full GraphRAG clone
- large model full fine-tuning
- broad crawler
- complex multi-agent orchestration
- production-grade monitoring platform

## 9. Current Next Work

1. Harden controlled collection execution:
   - keep manual retry guidance current as failure kinds evolve
   - keep runtime failure samples current as collector behavior changes

2. Add retrieval benchmark and portfolio metrics:
   - create a labeled query set with `docs/search-evaluation/labeling.html`
   - use the existing `api-ready-2026-06-05` 41-article expanded catalog for the next reviewed label handoff
   - expand the first smoke artifact with a 10-15 query reviewed label set before making larger benchmark claims
   - rerun keyword/vector/strict-hybrid/public comparison on the expanded catalog after labels are reviewed
   - final release notes, ADR updates, and README polish after label-dependent benchmark work

3. Expand graph-aware evaluation evidence:
   - increase the current 3-query/6-article smoke set before making quality claims
   - compare public graph context against simpler related article and retrieval baselines on a larger dataset
   - document when graph reasons help the article detail experience
   - keep relation quality claims limited to verified examples
