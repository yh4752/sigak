# Sigak Documentation

[English](README.md) | [한국어](README.ko.md)

Last updated: 2026-05-11

This directory separates portfolio-facing documents from historical development records.

Korean companion documents use the same filename with a `.ko.md` suffix.

## Recommended Reading Order

1. [PRODUCT.md](PRODUCT.md) - product definition, MVP scope, users, content model, and product decisions
2. [ROADMAP.md](ROADMAP.md) - integrated service and research execution roadmap
3. [STATUS.md](STATUS.md) - current implementation status, known risks, and verification notes
4. [API_SPEC.md](API_SPEC.md) - human-readable API contract
5. [CODING_CONVENTIONS.md](CODING_CONVENTIONS.md) - naming, comment, and code organization rules
6. [RESEARCH_STRATEGY.md](RESEARCH_STRATEGY.md) - LLM/NLP portfolio strategy, research questions, and evaluation direction
7. [SOURCE_POLICY.md](SOURCE_POLICY.md) - source quality and collection policy

## Directory Guide

| Path | Purpose |
| --- | --- |
| `PRODUCT.md` | Main product document. Read this to understand what Sigak is and what the MVP includes. |
| `ROADMAP.md` | Living roadmap for service work and research work. |
| `STATUS.md` | Living status report. Update after major fixes, phase completions, or verification changes. |
| `API_SPEC.md` | API design reference. Swagger remains the executable backend API documentation. |
| `CODING_CONVENTIONS.md` | Project-wide naming, Korean comment, and code organization rules. |
| `RESEARCH_STRATEGY.md` | Research portfolio direction, paper-inspired tracks, metrics, and final deliverables. |
| `SOURCE_POLICY.md` | Criteria for included and excluded article sources. |
| `ko/GUIDE.md` | Korean beginner-friendly guide. Keep it aligned when the core docs change. |
| `decisions/` | Architecture Decision Records. These are historical decision logs and should not be rewritten casually. |
| `blog/` | Development logs written as portfolio-friendly technical notes. See `blog/README.md` for the writing guide. |
| `superpowers/` | Design specs and implementation plans created during development. These are working records, not the main reader path. |

## Korean Versions

- [README.ko.md](README.ko.md)
- [PRODUCT.ko.md](PRODUCT.ko.md)
- [ROADMAP.ko.md](ROADMAP.ko.md)
- [STATUS.ko.md](STATUS.ko.md)
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
- Follow `blog/README.md` when writing development logs.
- Keep `ko/GUIDE.md` synchronized enough that a newcomer does not learn stale project structure.
