# Documentation Freshness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring the restart, onboarding, roadmap, and portfolio entry documents in line with current committed Sigak code and verified state.

**Architecture:** `STATUS` is the verified current-state source, `ROADMAP` is the next-work source, and the README/onboarding documents are navigation layers. The initial `/research` dashboard is implemented; its recorded backend full-gate and runtime-smoke limitations remain explicit.

**Tech Stack:** Markdown, Git history, Spring Boot and React module structure.

---

### Task 1: Refresh Portfolio and Restart Entry Points

**Files:**
- Modify: `README.md`
- Modify: `docs/README.md`
- Modify: `docs/ONBOARDING.ko.md`

- [x] Add `/research` and `GET /api/research/overview` to the root README as a read-only evidence view over committed retrieval comparison, graph-aware smoke, and catalog artifacts. Preserve the smoke-only quality limitation.
- [x] Set `docs/README.md` to `Last updated: 2026-07-26` and add the returning-developer sequence: `ONBOARDING.ko.md -> STATUS.ko.md -> ROADMAP.ko.md -> Ops RAG evaluation design and plan`. Label `docs/ko/GUIDE.md` as a conceptual primer, not the current-state source.
- [x] In `docs/ONBOARDING.ko.md`, include `neo4j` in the full infrastructure command; add `backend/research` as the committed-artifact reader; list `/research` as an implemented read-only evidence view; and point the final reading step to the Ops RAG design and plan.
- [x] Run `rg -n "/research|api/research/overview|neo4j|smoke" README.md docs/README.md docs/ONBOARDING.ko.md`.

### Task 2: Correct the Korean Conceptual Guide

**Files:**
- Modify: `docs/ko/GUIDE.md`

- [x] Set the guide update date to `2026-07-26` and state that `STATUS.ko.md` and `ROADMAP.ko.md` are authoritative for current state and next work.
- [x] Replace Neo4j-planned wording with its current rebuildable article/topic/relation projection boundary. Keep raw content in PostgreSQL.
- [x] Add the implemented `/research` evidence dashboard, smoke-artifact limitation, and planned operations-document RAG evaluation harness without inventing quality, latency, or generation results.
- [x] Fail if `rg -n "Neo4j.*예정|graph projection 예정|2026-05-07" docs/ko/GUIDE.md` finds an obsolete current-state claim.

### Task 3: Align Status and Roadmap Language

**Files:**
- Modify: `docs/STATUS.md`
- Modify: `docs/STATUS.ko.md`
- Modify: `docs/ROADMAP.md`
- Modify: `docs/ROADMAP.ko.md`

- [x] Set all update dates to `2026-07-26`.
- [x] Keep the dashboard implemented, focused backend tests/frontend full gate passed, and backend full gate/runtime boot smoke unverified because of recorded Docker/PostgreSQL prerequisites. Name the Ops RAG harness as the next implementation task.
- [x] State that the June v0.1 milestone dates are original targets, not proof that every exit criterion passed. Keep RAG/failure/latency/token/cost dashboard expansion unchecked until corresponding verified artifacts exist.

### Task 4: Verify Documentation Integrity

**Files:**
- Modify: `docs/superpowers/specs/2026-07-26-documentation-freshness-design.md`
- Modify: `docs/superpowers/plans/2026-07-26-documentation-freshness.md`

- [x] Verify local Markdown links in the eight user-facing entry/status/roadmap documents, run the stale-claim scan, and run `git diff --check`.
- [x] Review the unfiltered `aae2242..HEAD` documentation-only diff across all 10 changed documentation files against the fact boundary in the design.
- [x] Record the distinct eight-document link-scan scope and 10-document full-diff scope in the design, then mark only completed plan tasks.

### Task 5: Align Korean Entry-Point Guides and Reverify

**Files:**
- Modify: `README.ko.md`
- Modify: `docs/README.ko.md`
- Modify: `docs/superpowers/specs/2026-07-26-documentation-freshness-design.md`
- Modify: `docs/superpowers/plans/2026-07-26-documentation-freshness.md`

- [x] Add `/research` and `GET /api/research/overview` to `README.ko.md` as a read-only view over committed retrieval comparison, graph-aware smoke, and expanded catalog artifacts. State that the browser does not run experiments or rebuild projections, and retain the smoke-only quality limitation.
- [x] Set `docs/README.ko.md` to `2026-07-26`, then add the Korean returning-developer sequence: `ONBOARDING.ko.md -> STATUS.ko.md -> ROADMAP.ko.md -> Ops RAG evaluation harness design -> plan`. Identify `ko/GUIDE.md` as a conceptual primer and `STATUS.ko.md`/`ROADMAP.ko.md` as the authoritative current-state/next-work documents.
- [x] Re-run the local Markdown link check across 10 user-facing documents, the stale-current-state claim scan including the Korean companions, the unfiltered 12-document `aae2242..HEAD` diff review, and `git diff --check aae2242..HEAD`.
- [x] Record the actual 10-document link-scan and 12-document full-diff outcomes in the design, then complete Task 5.

### Task 6: Record the Documentation Maintenance Session

**Files:**
- Add: `docs/blog/2026-07-26-dev-log.md`
- Modify: `docs/blog/topic-queue.md`

- [x] Write a fact-based dev-log that distinguishes this session's documentation checks from the previously recorded application test and runtime-smoke results.
- [x] Update the existing single-source-of-truth documentation topic with the freshness-check evidence instead of creating a duplicate blog candidate.

### Task 7: Verify the Main Merge in a Clean Worktree

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/package-lock.json`
- Modify: `docs/STATUS.md`
- Modify: `docs/STATUS.ko.md`
- Modify: `docs/blog/2026-07-26-dev-log.md`
- Modify: `docs/blog/topic-queue.md`

- [x] Reproduce the clean-worktree `npm ci` failure, then regenerate only the dependency lockfile and verify a clean install.
- [x] Update Axios to `1.18.1`, run the frontend test/lint/build gate, and record the actual result.
- [x] Run focused research backend tests; record Docker/Testcontainers as the reason the full backend `test` and `check` gates remain blocked.
- [x] Record the remaining React Router RSC audit finding as a dependency-review risk rather than a passing audit.
