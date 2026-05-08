# Sigak Roadmap

[English](ROADMAP.md) | [한국어](ROADMAP.ko.md)

Last updated: 2026-05-08

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

Research features should come from the real pipeline, not from decorative charts. Mock mode stays available for local development, while real LLM or local-model modes can be added later.

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

## 4. Service Track

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
- [ ] Add an internal/admin trigger or command runner for source collection.
- [ ] Add a Spring Boot HTTP FastAPI enrichment client behind the current enrichment boundary.
- [x] Keep mock enrichment mode available for local development.
- [ ] Document the full backend, frontend, PostgreSQL, AI server, and collection trigger flow.

Exit criteria:

- At least one collected article can appear in `/api/articles` and the frontend.
- Raw content and enrichment output are stored separately.
- Local development still works without external API keys.

### Phase S3: Collection Operations and Real AI Enrichment

Goal: make collection executable from a controlled entry point, then allow switching from mock enrichment to FastAPI-backed real enrichment.

- [ ] Add a controlled internal/admin collection trigger or command runner.
- [ ] Return fetched, published, skipped, and failed counts for each run.
- [ ] Record enough failure information to debug bad feeds or invalid collected articles.
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

Goal: move from MVP search toward scalable search only when the data volume requires it.

- [ ] Move keyword filtering from in-memory service logic to database queries.
- [ ] Add Elasticsearch-backed keyword search when indexing is useful.
- [ ] Add embeddings and Qdrant only after dataset and retrieval baselines exist.
- [ ] Keep the article response shape stable as implementation changes.

Exit criteria:

- Search behavior remains stable from the frontend perspective.
- Search implementation can evolve without changing article cards/detail pages.

### Phase S5: Limited Graph-Aware Insight

Goal: make article relationships useful before building a full graph explorer.

- [x] Add graph-ready article metadata fields.
- [x] Add related article ID metadata to curated articles.
- [ ] Add explicit concept and relationship data.
- [ ] Store article-concept relationships with type and confidence.
- [ ] Store article-article relation reasons.
- [ ] Show relation reasons or related concepts on article detail.
- [ ] Evaluate graph-aware context against simpler retrieval baselines.

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
- [ ] Add architecture diagram and demo script.
- [ ] Prepare final README for portfolio review.

Exit criteria:

- A reviewer can run the project from README instructions.
- The service and research dashboard are demo-ready.

## 5. Research Track

### Phase R1: Research Dataset and Labels

Goal: create a small, reproducible dataset from Sigak's service data.

- [ ] Create `experiments/README.md`.
- [ ] Export article records from PostgreSQL to JSONL.
- [ ] Add deterministic chunking with stable chunk IDs.
- [ ] Create `experiments/datasets/raw/`.
- [ ] Create `experiments/datasets/processed/`.
- [ ] Create `experiments/datasets/labels/`.
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

## 6. Recommended Execution Order

```txt
S1 Service MVP Stabilization
-> S2 End-to-End Collection and Enrichment
-> S3 Real AI Enrichment
-> R1 Research Dataset and Labels
-> R2 Retrieval Benchmark
-> R3 RAG Evaluation
-> S6 Research Dashboard MVP
-> S5/R4 Graph-Aware Insight
-> R5 Fine-Tuning Experiment
-> S7/R6 Deployment and Portfolio Packaging
```

Important dependencies:

- The research dashboard needs experiment outputs before it becomes meaningful.
- Fine-tuning needs a labeled dataset and baselines first.
- Graph-aware insight should be compared against retrieval baselines.
- Elasticsearch and Qdrant should follow real search and retrieval needs, not precede them.

## 7. Non-Goals Before MVP Stability

- user accounts
- saved articles
- personalized recommendations
- full graph explorer
- full GraphRAG clone
- large model full fine-tuning
- broad crawler
- complex multi-agent orchestration
- production-grade monitoring platform

## 8. Current Next Work

1. Fix the current code review findings:
   - API-ready article filtering
   - stale related article state
   - FastAPI enrichment validation

2. Verify service stability:
   - backend tests
   - frontend tests, lint, and build
   - AI pytest

3. Add the smallest controlled collection execution path:
   - internal/admin trigger or command runner
   - selected source execution
   - fetched/published/skipped/failed result summary
   - failure reason capture

4. Add the FastAPI HTTP enrichment mode behind the existing enrichment boundary:
   - keep mock mode as the default local path
   - call FastAPI only when configured
   - validate and persist schema-safe enrichment output
