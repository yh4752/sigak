# Sigak technical-news collection automation

## Approved direction

The user selected automatic execution of Sigak's technical-news sources, rather than synchronizing news-rag's 경향신문 dataset. The news-rag worker, storage and images are outside the mutation scope. Reuse the existing source registry, collection pipeline, duplicate handling and failure recording in Sigak.

## Execution and boundaries

A single Spring Boot backend instance runs a conditionally enabled fixed-delay job. Development and tests default disabled. Deployment defaults enabled with a 30-second initial delay and ten-minute delay after completion. Configured limits are 1–20 articles/source/cycle and a minimum 60-second interval. Source IDs are validated at startup when enabled. Existing arXiv rate limiting and bounded retries remain in effect; its cooldown file persists on a deployment backend-state volume.

The job calls CollectionRunService, which persists new articles and deduplicates previously stored identities in PostgreSQL. New content triggers the existing keyword/vector/graph rebuild services sequentially. On process startup projections are considered pending, repairing a restart between persistence and indexing. A failed projection remains pending and is retried on the next cycle even if that cycle discovers no new articles. A single failed projection must not prevent the other stores from refreshing. These stores remain rebuildable projections; collection is not rolled back when a projection fails.

An in-process guard rejects an overlapping automation cycle. The supported deployment has one backend instance; cross-replica scheduling/locking, distributed queues, a complete collection-run history table and source-record update/delete synchronization are not introduced. Operators must not run multiple enabled scheduler instances or manual projection rebuilds concurrently with automation.

## Diagnostics

A read-only internal GET `/api/internal/collections/automation` reports enabled/state, interval, attempts, start/finish/last-full-success/last-published timestamps, next eligible attempt, counts from the last collection and per-store refresh results. Failure types contain sanitized class/type identifiers, not raw remote payloads or credentials. Snapshot diagnostics reset on restart; article records and source failure events persist. Caddy continues denying internal routes, so this endpoint is operator-only.

Scheduled execution catches exceptions so a single failed cycle does not stop future scheduling. Partial collection or projection failures must be reported as partial/failed rather than completed. Local mock enrichment remains labeled as such; automation does not introduce paid model calls or claim generated summaries are production AI output.

## Acceptance evidence

- Meaningful service tests cover disabled mode, settings limits, source validation, persistence/indexing order, independent projection errors, pending retries, collection exceptions, overlap prevention and timestamps/counts.
- Tests confirm job registration is conditional, scheduling metadata binds the configured ISO-8601 durations, the internal diagnostics DTO is exposed, and deployment does not publish this endpoint.
- Run backend full test and check, existing deployment HTTP smoke and actual collection/projection/search smoke.
- Update the running deployment image; observe current real articles beyond five seed records, duplicate skip on repeat, actual HYBRID search without stale IDs, and automatic repeated execution with timestamps.
- Do not claim unattended long-term reliability from a short local smoke. The local computer/Docker must remain running, and permanent hosting remains unprovisioned.
