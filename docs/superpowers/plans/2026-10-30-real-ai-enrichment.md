# On-demand AI Article Summary Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans task-by-task. One independent AI-provider task may use superpowers:subagent-driven-development; keep at most one child, give it only this task's context. Reuse the current codex feature checkout and preserve unrelated untracked documentation. User approved implementation on 2026-10-30; do not add another approval checkpoint.

**Goal:** Show articles immediately and asynchronously generate a shared Korean summary on first detail entry, spending at most the approved $5 total.

**Architecture:** Spring owns PostgreSQL article/job/budget state. A database-backed worker invokes private FastAPI outside transactions; results are persisted and reused. Read-only requests never generate summaries, and a narrowly exposed POST starts a single shared job. The application stops at $4 conservative charges, preserving $1 headroom.

**Tech Stack:** Kotlin/Spring Boot/JdbcTemplate/JPA/Flyway, FastAPI/httpx/Pydantic, React/TypeScript/Zod/Vitest, Docker Compose/Caddy.

## Reference Spec

`docs/superpowers/specs/2026-10-30-real-ai-enrichment-design.md` (approved on-demand revision).

## Task 1: Article publication independent of summaries

Files: article repository/service/DTO/response graph fetch, collection pipeline/persistence,
search projection and vector document mapping; corresponding article/collection/search tests.

- [x] Change the existing published-without-enrichment test to expect a readable article with null summary; add raw excerpt search coverage. Run focused test and observe failure.
- [x] Add `sourceExcerpt: String`, nullable `summary/whyItMatters` and provenance/status defaults to ArticleResponse. The boundary is `article.processingStatus == PUBLISHED`; enrichment is optional.
- [x] Publish collected raw data without invoking enrichment. Keep explicit mock enrichment available for development tests. New article score is 50, category/topics come from source metadata.
- [x] Keep projection text independent of generated results using `summary = sourceExcerpt`, `whyItMatters = ""` in projection factories. Keep local embedding provider.
- [x] Run article/collection/projection tests, update tests that encoded the superseded public-summary requirement.

Contract test shape:
```kotlin
val article = articleService.getArticle(publishedWithoutEnrichmentId)
assertNotNull(article)
assertNull(article.summary)
```

## Task 2: Private real provider

Files: `ai/app/config.py`, `ai/app/routers/enrichment.py`, `ai/app/schemas/enrichment.py`,
new `ai/app/services/openai_enrichment_service.py`, `ai/tests/test_openai_enrichment.py`.

- [x] Write MockTransport fixture tests for structured response, usage, timeout, provider errors, malformed output, quote validation and input/output caps. Observe RED before implementation.
- [x] Implement direct `httpx.Client` POST to the official Responses endpoint with no retries, fixed `gpt-5.6-luna`, `reasoning.effort=none`, `max_output_tokens=2048`, strict JSON schema, no tools, `store=false`. This avoids adding SDK dependencies for one endpoint.
- [x] Preserve mock response shape in default mock mode; real mode returns promptVersion/inputScope/evidenceQuotes/usage/providerResponseId metadata. Bound complete serialized payload to 16 KiB; truncate article text deterministically and mark it.
- [x] Return sanitized 503/502/504 failure codes, never provider bodies or keys. Require nonblank configured secret only in real mode.
- [x] Run `cd ai && .venv/bin/python -m pytest`.

## Task 3: Durable on-demand worker and budget

Files: `V4__on_demand_summaries.sql`, new `article/summary/` config, DTO, service,
repository/client/controller/worker; `application.yml`; integration tests.

- [x] Write PostgreSQL integration tests: repeated/concurrent POST creates one job; GET never calls provider; budget reservation survives new service instance; 10 pilot/20 daily/200 total attempt limits; timeout never resubmits. Observe RED.
- [x] Add one budget row and unique article/inputHash/prompt/model jobs. Use DB transactions and row locks, not process-only locks. Reserve 20,000 USD micros per dispatch and never refund; hard application ceiling 4,000,000 micros. Clamp configuration so a typo cannot increase this authorization.
- [x] POST validates public article, non-seed raw content, existing real result and existing job before queueing. Empty raw input/seed returns unavailable without a paid attempt. Enforce request window and same-origin checks.
- [x] Worker claims a queued job and reserves budget atomically. Mark DISPATCHED before HTTP. On success validate provider metadata, record enrichment/current history and set READY atomically. On unobserved failure mark UNKNOWN and retain reservation; no retry from page refresh.
- [x] Recover abandoned DISPATCHED jobs as UNKNOWN after lease expiry, never QUEUED. Persist circuit stop for authentication/model/usage violations. Expose internal budget/status GET only.
- [x] Verify focused tests then backend `./gradlew test check`.

## Task 4: Detail progressive rendering

Files: `frontend/src/api/articles.ts`, new summary API module/hook or component,
`ArticleDetailPage.tsx`, related tests/CSS.

- [x] Write tests for immediate article rendering plus pending summary, completed summary, READY reuse without POST, navigation cleanup and failure/budget states. Observe RED.
- [x] Extend Zod nullable/provenance contract. Add explicit summary POST client and read-only status GET client.
- [x] On detail entry request only if NOT_REQUESTED; bounded polling while PENDING. Cancel stale updates on route change, do not POST from related/list/search fetches or rerender on poll. Render live status accessibly and keep source link available.
- [x] Run `npm test`, `npm run lint`, `npm run build`.

## Task 5: Deployment, real pilot, records

Files: Compose/env examples, Caddyfile/smoke, API spec, STATUS/ROADMAP, dev-log/topic queue.

- [x] Add private AI secret/mode and backend explicit worker settings; default local mock and deployment disabled until smoke ready. Keep key out of frontend and Git.
- [x] Allow only exact numeric-article `/summary` POST and GET route; maintain denial for every other write/internal route. Add HTTP smoke checks for the revised contract.
- [x] Source-build and start affected services without deleting data. Verify article without summary appears in list/search and detail GET does not reserve budget.
- [x] With key safely configured, issue a real detail request and observe PENDING→READY, token/cost records and stored result reused by repeated/concurrent requests. Stay within maximum ten pilot attempts; do not bulk backfill.
- [x] Run full relevant gates and local/public HTTP smoke, inspect browser progressive rendering. Record actual counts and exceptions without inventing costs/quality claims.
- [x] Self-review spec compliance and code quality; update docs/log/topic queue and commit only task files. Existing PR #20 may be updated under the user's prior repository authorization; do not merge main.


## Execution record (2026-10-30)

All tasks completed. Backend249 tests/check, frontend47tests/lint/build, AI55tests,
RAG70fixtures, dataset evidence validation and local/external HTTP7tests each passed.
Two real excerpt summaries cost an observed token estimate$0.000677, with$0.04 conservative
charges; concurrent repeat and restart did not add attempts. Active rollout was separately
and explicitly approved after auto-review requested clarification. Final snapshot:143real
articles+5seed,2real summaries; all projections148 and HYBRID/no stale/fallback.
Key expiry2026-11-29, source-excerpt coverage, account-wide billing and permanent-hosting
limitations remain documented. No broad backfill/full RAG evaluation was executed.
