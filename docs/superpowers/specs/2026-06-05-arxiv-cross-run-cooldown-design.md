# arXiv Cross-Run Cooldown Design

## Context

PR #15 fixed issue #14 by adding arXiv-specific request spacing and bounded retry/backoff
inside `HttpSourceContentFetcher`. That solved the current 429 failure and passed a
post-cooldown runtime smoke.

The remaining edge is repeated `collection-run` execution. A normal `./gradlew bootRun
--args='collection-run ...'` starts a fresh JVM each time, so an in-memory timestamp cannot
coordinate cooldown across short repeated runs.

## Goal

Keep arXiv export API calls conservative across repeated local command runs without stress
testing the real arXiv API.

## Decision

Use a local file-backed arXiv fetch gate:

- The gate stores the last arXiv request start timestamp in a configurable local state file.
- A `FileChannel` lock protects the read/wait/write/fetch section across local JVM processes.
- The existing fake `Clock` and `SourceFetchDelay` tests verify cooldown behavior without
  sleeping or calling real arXiv.
- Retry/backoff policy remains in `HttpSourceContentFetcher`; the gate only owns request
  spacing and local cross-run coordination.

Default state file:

```txt
${java.io.tmpdir}/sigak/arxiv-export-fetch.cooldown
```

Override:

```txt
SIGAK_COLLECTION_ARXIV_COOLDOWN_STATE_FILE=/tmp/sigak/arxiv-export-fetch.cooldown
```

## Non-Goals

- Do not run real arXiv stress tests.
- Do not introduce a distributed lock or scheduler.
- Do not solve coordination across multiple machines.
- Do not generalize retry/cooldown policy to every source.

## Verification Strategy

- RED/GREEN focused test for two `HttpSourceContentFetcher` instances sharing the same
  cooldown state file.
- Existing tests continue to cover same-process sequential throttling, non-arXiv bypass,
  429 retry, retry limit, and transient timeout retry.
- Config binding test verifies the cooldown state file property.
- Backend `test` and `check` remain the finish gate.
