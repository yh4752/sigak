# Sigak Documentation

[English](README.md) | [한국어](README.ko.md)

Last updated: 2026-05-31

This directory separates portfolio-facing documents from historical development records.

Korean companion documents use the same filename with a `.ko.md` suffix.

> Agents: development *rules* live in the repo-root `AGENTS.md` (single source of truth),
> not in this directory. This directory holds product, status, and reference docs.

## Recommended Reading Order

1. [PRODUCT.md](PRODUCT.md) - product definition, MVP scope, users, content model, and product decisions
2. [ROADMAP.md](ROADMAP.md) - integrated service and research execution roadmap
3. [STATUS.md](STATUS.md) - current implementation status, known risks, and verification notes
4. [ONBOARDING.ko.md](ONBOARDING.ko.md) - Korean new-developer handoff guide
5. [DEMO_FLOW.md](DEMO_FLOW.md) - reproducible local collection-to-search demo script
6. [API_SPEC.md](API_SPEC.md) - human-readable API contract
7. [CODING_CONVENTIONS.md](CODING_CONVENTIONS.md) - naming, comment, and code organization rules
8. [RESEARCH_STRATEGY.md](RESEARCH_STRATEGY.md) - LLM/NLP portfolio strategy, research questions, and evaluation direction
9. [SOURCE_POLICY.md](SOURCE_POLICY.md) - source quality and collection policy

## Directory Guide

| Path | Purpose |
| --- | --- |
| `PRODUCT.md` | Main product document. Read this to understand what Sigak is and what the MVP includes. |
| `ROADMAP.md` | Living roadmap for service work and research work. |
| `STATUS.md` | Living status report. Update after major fixes, phase completions, or verification changes. |
| `ONBOARDING.ko.md` | Korean new-developer handoff guide for first-day reading, local run commands, module responsibilities, and change-impact checks. |
| `DEMO_FLOW.md` | Reproducible local demo script for collection, failure diagnostics, projection rebuilds, and public hybrid search. |
| `API_SPEC.md` | API design reference. Swagger remains the executable backend API documentation. |
| `CODING_CONVENTIONS.md` | Project-wide naming, Korean comment, and code organization rules. |
| `RESEARCH_STRATEGY.md` | Research portfolio direction, paper-inspired tracks, metrics, and final deliverables. |
| `SOURCE_POLICY.md` | Criteria for included and excluded article sources. |
| `ko/GUIDE.md` | Korean beginner-friendly guide. Keep it aligned when the core docs change. |
| `decisions/` | Architecture Decision Records. These are historical decision logs and should not be rewritten casually. |
| `blog/` | Daily dev-logs and topic-based technical posts. `blog/WRITING_GUIDE.ko.md` is the single source of truth for writing them; `blog/topic-queue.md` holds blog candidates. |
| `superpowers/` | Per-feature design specs and implementation plans. See `superpowers/README.md` for the spec/plan format and workflow. |

## Korean Versions

- [README.ko.md](README.ko.md)
- [PRODUCT.ko.md](PRODUCT.ko.md)
- [ROADMAP.ko.md](ROADMAP.ko.md)
- [STATUS.ko.md](STATUS.ko.md)
- [DEMO_FLOW.ko.md](DEMO_FLOW.ko.md)
- [API_SPEC.ko.md](API_SPEC.ko.md)
- [CODING_CONVENTIONS.ko.md](CODING_CONVENTIONS.ko.md)
- [RESEARCH_STRATEGY.ko.md](RESEARCH_STRATEGY.ko.md)
- [SOURCE_POLICY.ko.md](SOURCE_POLICY.ko.md)

## Maintenance Rules

- Update `STATUS.md` when the current implementation state changes.
- Update `ROADMAP.md` when the next execution order or phase status changes.
- Update `PRODUCT.md` when product scope, user experience, or content policy changes.
- Update `API_SPEC.md` when backend contracts change.
- Update `CODING_CONVENTIONS.md` when naming, comments, or code organization rules change.
- Add an ADR under `decisions/` for major architectural decisions.
- Follow `blog/WRITING_GUIDE.ko.md` when writing development logs or technical posts.
- Keep `ko/GUIDE.md` synchronized enough that a newcomer does not learn stale project structure.
