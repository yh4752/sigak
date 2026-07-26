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

- [x] Verify local Markdown links in the changed documents, run the stale-claim scan, and run `git diff --check`.
- [x] Review the complete documentation-only diff against the fact boundary in the design.
- [x] Record the actual verification results in the design and mark only completed plan tasks.
