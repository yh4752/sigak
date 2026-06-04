# arXiv Cross-Run Cooldown Implementation Plan

**Issue:** #16

**Goal:** Verify and harden repeated arXiv collection-run cooldown behavior without
stress-testing the real arXiv export API.

## Tasks

- [x] Create GitHub issue #16 before implementation.
- [x] Read `docs/CODING_CONVENTIONS.md`.
- [x] Add RED test for shared cooldown state across fetcher instances.
- [x] Add file-backed arXiv fetch gate and configuration binding.
- [x] Run focused backend tests.
- [x] Update status/dev-log/topic queue with verified facts.
- [x] Run backend full verification gate.
- [ ] Prepare commit, push, PR, and close #16 after review/merge.

## Verification

Focused commands:

```bash
cd backend
./gradlew test --tests com.sigak.collection.service.HttpSourceContentFetcherTest --tests com.sigak.collection.config.CollectionHttpPropertiesTest
```

Finish gate:

```bash
cd backend
./gradlew test
./gradlew check
```

## Notes

- Real arXiv stress smoke is intentionally excluded.
- The local cooldown state file coordinates repeated local JVM processes on the same machine.
- Coordination across multiple machines remains out of scope.
