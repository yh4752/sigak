# Full-text summaries and latency implementation plan

> **For agentic workers:** Use superpowers:executing-plans task-by-task. One independent AI-provider task may use superpowers:subagent-driven-development; retain at most one child and provide only its task context. Reuse the current codex feature branch. The user approved the linked written design and ordinary-article-first scope on2026-10-30; do not add another design/budget approval gate.

**Goal:** Summarize complete supported news bodies on demand, preserve the existing cumulative budget and reduce avoidable first-visit waits.

**Architecture:** Spring owns free article-text preparation and persisted body states, plus the existing paid job/budget. Private FastAPI validates one complete bounded request, reuses HTTP connections and generates a compact grounded result. The browser displays source/processing state immediately, polls faster and upgrades legacy excerpt summaries only when visited.

**Tech Stack:** Kotlin/Spring/JdbcTemplate/Flyway/jsoup, FastAPI/httpx/Pydantic, React/Zod/Vitest, Docker Compose/Caddy.

## Reference spec

`docs/superpowers/specs/2026-10-30-fulltext-summary-latency-design.md`.

## Task1 — Body extraction and durable free preparation

Files: new `article/fulltext/` contracts/fetcher/extractor/store/worker,
`V5__article_full_text.sql`, `backend/build.gradle`, collection persistence, tests/fixtures.

- [x] Write tests for correct root selection (OpenAI/Google article, GitHub section.post__content), noise removal and tail preservation. Expected GitHub author bio is absent while body conclusion remains. Run focused tests and observe RED.
- [x] Add jsoup1.23.2 (official download verified), a bounded HTTPS fetch port and source-specific extractors. Reject unknown/private addresses, cross-source redirects, >2MiB responses and unsupported content. Keep typed failure codes and no paid client dependency.
- [x] Persist `article_full_texts(article_id,status,text,final_url,content_hash,extractor_version,fetched_at,fetch_duration_ms,error_code,requested_summary)`; claim free jobs with SKIP LOCKED and one bounded worker. States PENDING/FETCHING/READY/FAILED/UNSUPPORTED/TOO_LONG. Recover abandoned FETCHING as FAILED without sending AI requests.
- [x] On new supported publication enqueue free extraction only. On first detail request enqueue missing old article only and mark requested_summary; no old-catalog bulk work. Unsupported arXiv returns explanatory state without paid generation. Failed text gets a cached outcome, not a request loop.
- [x] Focused tests cover simultaneous clicks, cache reuse, no AI dispatch before complete text and preserved existing raw excerpt.

Contract examples:
```kotlin
data class FullTextResult(val text: String, val finalUrl: String, val durationMs: Long)
fun interface ArticleBodyFetcher { fun fetch(url: String): FullTextResult }
assertTrue(extractor.extract(url, html).contains("Closing paragraph"))
```

## Task2 — Complete provider input and persistent connections

Owned files: AI schemas/service/router/main and AI tests; independent child may implement this task.

- [x] Add FULL_TEXT scope and v2 prompt. Write a test where text exceeding64KiB must fail before transport with no prefix submission; observe RED.
- [x] Validate full serialized input<=64KiB and output<=2048; do not truncate any new FULL_TEXT request. Keep mock endpoint exact compatibility.
- [x] Use `prompt_cache_options={mode:explicit}` without breakpoints for one-off content. Reject unexpected cache writes and usage outside input<=70000/output<=2048; retain auth/model/billing503 circuit behavior and no retries.
- [x] Reuse lifecycle-managed httpx client/connection pool. Tests prove shared client and shutdown close, sanitized failures and zero network on preflight rejection.
- [x] Run all AI pytest. Do not make paid calls in this independent task.

## Task3 — Full-text eligibility, upgrade and immediate dispatch

Files: SummaryJobStore, SummaryContracts, ArticleSummaryService/SummaryConfig,
ArticleService/DTO, integration tests.

- [x] Write a regression: legacy READY excerpt plus prepared full text creates one new v2 job only after POST; read-only GET does not upgrade. Add no-source/too-long/unsupported tests asserting attemptCount unchanged. Observe RED.
- [x] Keep existing jobs and budget row untouched. Use only READY complete text for new FULL_TEXT inputs, v2 prompt and content hash. Queue missing text without charging; when a requested free body finishes, create the single shared summary job.
- [x] Cache by article/version/input; return only valid FULL_TEXT as the new default ready result. Keep old enrichment history until successful v2 replacement; no bulk regeneration. Curated seeds remain free READY examples.
- [x] Increase validated input usage ceiling to70000 while keeping $0.02 reservation/$4/200/20 caps. FULL_TEXT preflight length failure must happen before reservation; provider metadata exceeding bounds stops further dispatch.
- [x] Publish a wake-up event after job transaction commit. A coalesced single executor processes jobs immediately; scheduled2s wake-up remains recovery. Add lifecycle shutdown and a test for event-driven first start without duplicate dispatch.
- [x] Run full backend test/check.

## Task4 — Reader state and timing

Files: frontend article/summary Zod contracts, ArticleSummary component/tests.

- [x] Test legacy excerpt cannot suppress full-body POST, full-text cached result uses GET only, and unsupported/too-long/fetch failure states never repeat POST on polling. Observe RED.
- [x] Include bodyStatus/summaryScope provenance; distinguish source fetching versus AI generation. Preserve source links and accessible live state.
- [x] Poll every500ms up to the same60s overall bound; cancel on navigation. Label FULL_TEXT as 本文-based in Korean, never as excerpt. Keep generation count bounded by server dedup.
- [x] Run frontend test/lint/build.

## Task5 — Deployment and real validation

Files: Compose/env config, source images, API/status/roadmap/spec/log/topic queue, existing PR20.

- [x] Verify budget arithmetic:70000*0.20/1M+2048*1.20/1M=$0.0164576 < $0.02. Retain existing charged amount and active rollout authorization; never reset money/counters.
- [x] Build affected images. Pause paid worker during cross-version replacement, start compatible AI/backend/web and re-enable the existing active mode after readiness. Free preparation may proceed without paid calls.
- [x] Run ordinary source-body extraction on actual samples and confirm tail/metadata/provenance. Use only a few real clicked upgrades within the same budget and record body/queue/AI/client timings, usage, shared result/restart preservation.
- [x] Run local/public HTTP smoke, verify original-input search and existing origin restrictions. If temporary tunnel renews, update exact public origin before claiming external POST success.
- [x] Independently review spec compliance then quality, resolve findings. Update docs/dev-log/topic queue and relevant RAG evidence when public contracts change; preserve history/DRAFT status.
- [x] Commit/push only task files, update existing PR20, preserve unrelated untracked docs and leave main unmerged.

## Execution record (2026-10-30)

Backend277 test/check/bootJar, frontend50/lint/build, AI69, RAG70, final local/public HTTP7each passed.
Live complete bodies were unchanged in provider input. Google26/GitHub47 READY, GitHub46 UNKNOWN
kept charged and never retried. OpenAI live403 caused no paid dispatch; no bypass was attempted.
Only GitHub46 free fetch was manually resumed after the192.0.66.2 address-policy fix.
Current charges120000micros/attempts6/unknown1 preserved across restart; no counters reset.
Source-built AI/web images passed; clean backend source build failed Maven DNS. A verified host
bootJar was packaged in the same runtime Docker stage. Clean backend Docker rebuild remains
unverified; this deployment limitation is recorded in STATUS/dev-log. Temporary URL renewed with
exact public-origin configuration. Broad quality, arXiv full papers and permanent hosting deferred.
