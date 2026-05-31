# Agent Docs Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align Sigak's agent-facing documentation with the new `AGENTS.md` source-of-truth structure while preserving current application behavior.

**Architecture:** Keep stable agent rules in `AGENTS.md`, moving changing state into `docs/STATUS.md`, execution order into `docs/ROADMAP.md`, blog guidance into `docs/blog/WRITING_GUIDE.ko.md`, and feature design/plan guidance into `docs/superpowers/README.md`. `CLAUDE.md` should contain only Claude-specific operational notes and links to the authoritative docs.

**Tech Stack:** Markdown documentation, Git diff checks, existing Spring Boot/Kotlin verification if backend code changes.

---

## Reference Spec

- `docs/superpowers/specs/2026-05-31-agent-docs-refactor-design.md`

## File Structure

- Modify: `CLAUDE.md` — remove stale duplicated rules and update current search/infra notes.
- Modify: `docs/README.md` and `docs/README.ko.md` — keep documentation index aligned with source-of-truth split.
- Modify: `docs/blog/README.md` — point to the writing guide and new AGENTS wrap-up section.
- Modify: `docs/STATUS.md` and `docs/STATUS.ko.md` — update current hybrid-search status wording and date.
- Modify: `docs/ROADMAP.md` and `docs/ROADMAP.ko.md` — update current phase status and date.
- Modify if needed: `docs/superpowers/README.md` — clarify spec/plan ownership without duplicating AGENTS rules.
- Do not modify application code in this documentation refactor unless a stale comment or documentation-only string is found.

## Tasks

### Task 1: Classify and protect existing work

- [x] Run `git status --short --branch`.
- [x] Run `git diff --stat`.
- [x] Identify three change groups: hybrid search implementation, agent/documentation refactor, and session blog/status updates.
- [x] Keep the uncommitted hybrid-search plan file out of any final commit unless it has been reviewed for freshness.

### Task 2: Update tool-specific guidance

- [x] Edit `CLAUDE.md` so it clearly defers rules to `AGENTS.md`.
- [x] Replace stale keyword-only search guidance with current hybrid search behavior.
- [x] Replace stale persistence/processing notes with current PostgreSQL and projection-store wording.

### Task 3: Align documentation indexes

- [x] Review `docs/README.md`, `docs/README.ko.md`, `docs/blog/README.md`, and `docs/superpowers/README.md`.
- [x] Remove duplicated policy text where a source-of-truth document already owns it.
- [x] Fix references to old section names, especially blog and session wrap-up rules.

### Task 4: Refresh status and roadmap wording

- [x] Update `docs/STATUS.md` and `docs/STATUS.ko.md` dates and hybrid-search wording.
- [x] Update `docs/ROADMAP.md` and `docs/ROADMAP.ko.md` dates and current phase wording.
- [x] Keep future work such as Neo4j projection and research dashboard explicitly pending.

### Task 5: Verify the refactor

- [x] Run stale-phrase search for old search, blog-rule, and persistence wording across `AGENTS.md`, `CLAUDE.md`, `docs`, `README.md`, and `backend/README.md`.
- [x] Run `git diff --check`.
- [x] If application code was modified during this refactor, run `cd backend && ./gradlew test`.
- [x] Summarize verified and unverified items for the user.
