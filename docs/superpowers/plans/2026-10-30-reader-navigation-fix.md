# Reader navigation latency fix

> Use superpowers:executing-plans in the current feature branch, single agent.

**Goal:** Show previously received home/article content immediately on navigation and explain source-fetch failures.

**Architecture:** Validated public DTOs use bounded tab-memory read caches: fresh60s reuse; stale up to5min display while shared GET refreshes. Lists12keys/details256ids; no persistence/library/paid prefetch. Old query/route responses cannot replace current content. Existing summary deduplication/budget remains unchanged.

**Evidence:** Home remount starts empty/loading and refetches173articles. Local238743bytes/0.224s; external identity28.430s; gzip76571bytes/4.795s. Caddy already compresses. Legora11 is BODY_FETCH_FAILED; current server Java requests return403 even though a host Python request returned200. No bot-control bypass, automatic retry or excerpt fallback.

- [x] Read STATUS/ROADMAP/conventions and reproduce3failing reader tests.
- [x] Add API-cache contract tests; observe RED (cache absent).
- [x] Implement bounded cache and validated list/detail/bulk writes; deduplicate pending GETs.
- [x] Preserve cached feed/detail during refresh, guard query/route races; explain body failure and no added AI cost.
- [x] Run frontend test/lint/build; no backend/AI code changes.
- [x] Deploy web; compare real navigation/requests and verify cost unchanged with existing ready/failed articles only.
- [x] Update STATUS/dev-log/topic queue, commit/push existing PR. Preserve unrelated2drafts. README work deferred.

## Verified outcome

Frontend63/lint0warnings/build passed. Web-only image deployed. Public browser detail1864→80ms,
home1811→50ms; no list/detail GET during the repeated walk. Local/public HTTP7each passed.
Legora server Java403 remains unresolved (host Python200); clearer no-cost message verified,
no requeue/bypass/new paid call. Budget6/120000/unknown1 unchanged. Review found/fixed late GET
and large-batch eviction races with observed RED/GREEN. Cold loads still depend on the network.
