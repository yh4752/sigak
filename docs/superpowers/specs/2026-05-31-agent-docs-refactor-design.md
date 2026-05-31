# Agent Docs Refactor Design

## Summary

Sigak now treats `AGENTS.md` as the single source of truth for agent development
rules. This refactor aligns the surrounding documentation with that structure so
future agents can find current state, roadmap, blog rules, and feature plans
without reading duplicated or stale guidance.

The change is documentation-first and behavior-preserving. It should not alter
the hybrid search implementation, public API behavior, or local development
commands except where documentation was outdated.

## Current State

- `AGENTS.md` now defines read order, development lifecycle, Definition of Done,
  session wrap-up rules, and git/security rules.
- `docs/STATUS.md` and `docs/ROADMAP.md` contain hybrid-search progress, but some
  dates and "needs work" wording still read like earlier search stages.
- `CLAUDE.md` still contains duplicated project rules and stale search guidance
  that predates Elasticsearch + Qdrant hybrid search.
- `docs/blog/README.md` delegates to `docs/blog/WRITING_GUIDE.ko.md`, but a few
  references should match the new AGENTS section names.
- `docs/superpowers/README.md` defines the spec/plan directory, but the current
  hybrid-search plan is still uncommitted and should not be accidentally treated
  as fresh design truth without review.

## Design Goal

The refactor should make each document answer one question:

| Question | Source of truth |
| --- | --- |
| How should agents work? | `AGENTS.md` |
| What is implemented now? | `docs/STATUS.md` |
| What comes next? | `docs/ROADMAP.md` |
| How are dev-logs and technical posts written? | `docs/blog/WRITING_GUIDE.ko.md` |
| Where do feature specs and plans live? | `docs/superpowers/README.md` |
| What tool-specific notes does Claude need? | `CLAUDE.md` |

## Scope

In scope:

- Remove or soften duplicated rules in tool-specific docs.
- Fix stale search descriptions that conflict with current hybrid search.
- Update status/roadmap dates and wording where the current implementation has
  advanced.
- Keep Korean companion docs aligned enough that they do not contradict English
  source documents.
- Preserve existing code behavior.

Out of scope:

- Changing public API contracts.
- Reworking hybrid search ranking, fallback, or metrics behavior.
- Adding Neo4j projection or research dashboard work.
- Committing or pushing; that remains a separate user-approved step.

## Verification

- Run `git diff --check`.
- Search for old keyword-only search guidance, obsolete semantic-search deferrals,
  and stale blog-rule references.
- If backend code is changed, run `./gradlew test` from `backend/`.
- Report any unverified areas explicitly.
