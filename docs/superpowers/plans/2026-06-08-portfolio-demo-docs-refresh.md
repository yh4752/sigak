# Portfolio Demo Docs Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refresh Sigak's portfolio-facing README and local demo flow so they describe the current v0.1 collection, projection, hybrid search, graph-aware detail, and smoke-evaluation evidence without depending on new manual labeling.

**Architecture:** This is a documentation-only change. It updates root README and demo-flow docs from the existing source-of-truth documents and verified smoke artifacts, while preserving the current 3-query/6-article smoke baseline and the unreviewed 41-article label draft. It does not change backend, frontend, AI, database, or experiment runner code.

**Tech Stack:** Markdown documentation, existing Docker Compose commands, existing Spring Boot internal endpoints, existing experiment result JSON artifacts.

---

## File Structure

- Modify: `README.md`
  - Refresh the portfolio summary, architecture flow, local run commands, and status wording.
- Modify: `README.ko.md`
  - Keep the Korean companion aligned with the root README.
- Modify: `docs/DEMO_FLOW.md`
  - Add Neo4j graph projection, public graph context, retrieval smoke result, and graph-aware smoke result steps.
- Modify: `docs/DEMO_FLOW.ko.md`
  - Keep the Korean companion aligned with the English demo flow.
- Modify: `docs/STATUS.md`
  - Record that portfolio README and demo flow docs were refreshed without new benchmark claims.
- Modify: `docs/STATUS.ko.md`
  - Keep the Korean status companion aligned.
- Modify: `docs/ROADMAP.md`
  - Mark README/demo packaging progress while keeping label-dependent benchmark expansion pending.
- Modify: `docs/ROADMAP.ko.md`
  - Keep the Korean roadmap companion aligned.
- Create or modify: `docs/blog/2026-06-08-dev-log.md`
  - Record only the documentation work and verification actually run in this session.
- Modify: `docs/blog/topic-queue.md`
  - Add or update a portfolio packaging topic if it meets the writing-guide criteria.

## Task 1: Read Current Sources Of Truth

**Files:**
- Read: `AGENTS.md`
- Read: `docs/STATUS.md`
- Read: `docs/ROADMAP.md`
- Read: `docs/CODING_CONVENTIONS.md`
- Read: `README.md`
- Read: `README.ko.md`
- Read: `docs/DEMO_FLOW.md`
- Read: `docs/DEMO_FLOW.ko.md`
- Read: `experiments/README.md`

- [x] **Step 1: Confirm current working tree**

Run:

```bash
git status --short --branch
```

Expected:

```txt
## codex/catalog-expansion-2026-06-05
?? experiments/datasets/labels/search-labels.api-ready-2026-06-05.draft.json
```

The untracked draft label file belongs to the deferred manual-labeling path and must not be modified by this task.

- [x] **Step 2: Read current project docs**

Run the existing read commands for the files listed above and use only verified, already-recorded smoke values in the docs refresh.

## Task 2: Refresh README Portfolio Packaging

**Files:**
- Modify: `README.md`
- Modify: `README.ko.md`

- [x] **Step 1: Update project positioning**

Replace stale current-status wording with a v0.1 portfolio summary that mentions:

- Spring Boot public API boundary
- FastAPI AI/embedding boundary
- PostgreSQL source of truth
- Elasticsearch, Qdrant, and Neo4j rebuildable projections
- hybrid search with RRF
- graph-aware article detail
- smoke-only retrieval and graph evaluation artifacts

- [x] **Step 2: Add architecture flow**

Add a compact text or Mermaid flow that shows:

```txt
selected source collection
-> PostgreSQL
-> Elasticsearch / Qdrant / Neo4j projections
-> public hybrid search
-> graph-aware article detail
-> experiment reports
```

- [x] **Step 3: Update local run commands**

Use the current services:

```bash
docker compose -f infra/docker-compose.yml up -d --pull never postgres elasticsearch qdrant neo4j ai
```

Include rebuild commands for Elasticsearch, Qdrant, and Neo4j projections.

## Task 3: Refresh Demo Flow

**Files:**
- Modify: `docs/DEMO_FLOW.md`
- Modify: `docs/DEMO_FLOW.ko.md`

- [x] **Step 1: Remove stale Neo4j limitation**

Remove the stale sentence that excludes Neo4j from the demo flow.

- [x] **Step 2: Add Neo4j service prerequisite**

Add `neo4j` to the Docker Compose startup and health-check commands.

- [x] **Step 3: Add graph projection rebuild and public graph context steps**

Add commands:

```bash
curl -X POST http://localhost:8080/api/internal/graph-projections/articles/rebuild
curl http://localhost:8080/api/articles/4/graph-context
```

Expected signal:

- rebuild `status` is `completed`
- graph context returns `relatedArticleReasons` and `topics`
- when Neo4j is unavailable, public article detail degrades to empty graph context according to the existing recorded smoke

- [x] **Step 4: Add smoke benchmark artifact inspection steps**

Add commands that inspect already committed result artifacts without requiring new labels:

```bash
node -e "const s=require('./experiments/results/retrieval/latest/metrics.comparison.json'); console.log(JSON.stringify({catalogId:s.catalogId,evaluatedQueryCount:s.evaluatedQueryCount,systems:s.systems.map((system)=>system.system),warnings:s.warnings}, null, 2))"
node -e "const s=require('./experiments/results/graph/latest/graph-context.metrics.summary.json'); console.log(JSON.stringify({catalogId:s.catalogId,evaluatedQueryCount:s.evaluatedQueryCount,macroGraphContextCoverageAtK:s.macroGraphContextCoverageAtK,warnings:s.warnings}, null, 2))"
```

Expected signal:

- both artifacts are explicitly smoke-only
- no expanded benchmark is claimed without the missing `api-ready-2026-06-05` reviewed label file

## Task 4: Update Status, Roadmap, And Dev Log

**Files:**
- Modify: `docs/STATUS.md`
- Modify: `docs/STATUS.ko.md`
- Modify: `docs/ROADMAP.md`
- Modify: `docs/ROADMAP.ko.md`
- Create or modify: `docs/blog/2026-06-08-dev-log.md`
- Modify: `docs/blog/topic-queue.md`

- [x] **Step 1: Update status docs**

Record that portfolio README/demo-flow docs now describe the current graph-aware local demo. Do not mark expanded labels or expanded benchmarks as complete.

- [x] **Step 2: Update roadmap docs**

Mark README/demo script polish as progressed, while keeping architecture diagram, deployment target, research dashboard, real enrichment, and expanded labeled benchmark work pending.

- [x] **Step 3: Read writing guide and write dev log**

Run:

```bash
sed -n '1,240p' docs/blog/WRITING_GUIDE.ko.md
```

Then create `docs/blog/2026-06-08-dev-log.md` in Korean with:

- completed documentation changes
- verified commands
- unverified runtime smoke
- label-dependent work explicitly deferred

- [x] **Step 4: Update topic queue**

Add a candidate only if the session has enough concrete material: demo-flow freshness, smoke-only claims, label-dependent benchmark boundaries, and portfolio packaging.

## Task 5: Verify Documentation Changes

**Files:**
- Verify all modified Markdown files.

- [x] **Step 1: Check changed file list**

Run:

```bash
git status --short
```

Expected modified paths are the docs listed in this plan plus this plan file. The existing untracked label draft may still be present.

- [x] **Step 2: Run whitespace check**

Run:

```bash
git diff --check
```

Expected: no output and exit code `0`.

- [x] **Step 3: Verify referenced files exist**

Run:

```bash
test -f experiments/results/retrieval/latest/metrics.comparison.json
test -f experiments/results/graph/latest/graph-context.metrics.summary.json
test -f experiments/datasets/raw/articles.catalog.api-ready-2026-06-05.json
test ! -f experiments/datasets/labels/search-labels.api-ready-2026-06-05.2026-06-05.json
```

Expected: all commands exit with code `0`.

- [x] **Step 4: Inspect smoke artifact summaries**

Run:

```bash
node -e "const s=require('./experiments/results/retrieval/latest/metrics.comparison.json'); console.log(JSON.stringify({catalogId:s.catalogId,evaluatedQueryCount:s.evaluatedQueryCount,systems:s.systems.map((system)=>system.system),warnings:s.warnings}, null, 2))"
node -e "const s=require('./experiments/results/graph/latest/graph-context.metrics.summary.json'); console.log(JSON.stringify({catalogId:s.catalogId,evaluatedQueryCount:s.evaluatedQueryCount,macroGraphContextCoverageAtK:s.macroGraphContextCoverageAtK,warnings:s.warnings}, null, 2))"
```

Expected:

- retrieval artifact uses `catalogId=api-ready-2026-06-02`
- graph artifact uses `catalogId=api-ready-2026-06-02`
- both report `evaluatedQueryCount=3`
- warnings describe smoke-only limitations

## Stop Conditions

- Do not edit `experiments/datasets/labels/search-labels.api-ready-2026-06-05.draft.json`.
- Do not create fake labels or mark expanded benchmark work complete.
- Do not overwrite `experiments/results/retrieval/latest/` or `experiments/results/graph/latest/`.
- Do not claim newly executed runtime smoke unless it is actually run in this session.
