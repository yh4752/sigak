# Refactor And Onboarding Search Design

## Summary

This work starts the broader Sigak refactoring effort with the smallest useful
slice: backend public search boundaries plus new-developer onboarding
documentation. The goal is to make the next MVP features, especially collection
trigger observability, Neo4j projection, graph-aware article detail, and retrieval
benchmarking, easier to add without changing public behavior.

This is a behavior-preserving refactor. It should improve naming, responsibility
boundaries, test readability, and handoff documentation, but it must not change
the public article API response shape, hybrid search ranking policy, fallback
policy, or current local development assumptions.

## Current State

The current `main` branch has:

- Public article search using Elasticsearch keyword candidates, Qdrant vector
  candidates, reciprocal rank fusion, PostgreSQL reload, and PostgreSQL fallback.
- Search metrics that record mode, candidate counts, stale candidate count,
  failure flags, fallback reason, and latency breakdowns.
- Documentation split across `README.md`, `docs/README.md`, `docs/STATUS.md`,
  `docs/ROADMAP.md`, `docs/API_SPEC.md`, and `docs/ko/GUIDE.md`.
- Agent-facing rules consolidated in `AGENTS.md`.

The biggest refactoring pressure is in the backend search path:

- `ArticlePublicSearchService` owns candidate collection, degraded-mode
  resolution, fusion, fallback reason mapping, and timing assembly.
- `ArticleService` owns API-ready article loading, PostgreSQL fallback filtering,
  search-result reload, response mapping, and public search metric recording.
- Tests cover the behavior, but test setup is becoming verbose as metrics and
  fallback modes grow.

The biggest handoff documentation gap is not lack of documentation, but lack of
a single "first day" guide for a new developer. A new developer can find product
and roadmap docs, but not one concise path that answers how to run the system,
which module owns which responsibility, and which contracts must not be broken
while refactoring.

## Design Goals

### Refactoring Goals

- Make the backend search path easier to read before adding Neo4j projection and
  benchmark logic.
- Keep public behavior unchanged.
- Improve test readability where it directly supports safe refactoring.
- Prefer private helper extraction, small value objects, or existing-package
  collaborators over broad new abstractions.
- Keep the refactor reviewable as small commits.

### Onboarding Goals

- Add a single Korean onboarding handoff document for a new developer.
- Explain the current system through execution flow and module responsibility,
  not through broad architecture theory.
- Show the first-day read order, local run path, verification commands, and
  change-impact checklist.
- Explain the key invariant: PostgreSQL is the source of truth; Elasticsearch,
  Qdrant, and future Neo4j are rebuildable projection stores.
- Explain how refactoring findings and blog-worthy trade-offs should be added to
  `docs/blog/topic-queue.md`.

## Non-Goals

- Do not implement collection trigger, Neo4j projection, graph-aware detail, or
  benchmark scoring in this refactor.
- Do not tune RRF, candidate limits, weights, search ranking, or fallback policy.
- Do not expose scores or search diagnostics through the public article API.
- Do not create a large architecture-document hierarchy. One onboarding document
  is enough for this slice.
- Do not introduce a new architectural style, framework, event bus, CQRS layer,
  or generic orchestration framework.

## Behavior Contracts To Preserve

- `GET /api/articles` returns the same `List<ArticleResponse>` shape.
- `GET /api/articles?query=...` returns article responses, not search diagnostic
  DTOs.
- Blank or missing query still returns API-ready articles from PostgreSQL.
- Non-blank query still uses the public search path.
- Projection hits are candidate IDs only; final article responses are reloaded
  from PostgreSQL.
- If one projection path fails, search degrades to `KEYWORD_ONLY` or
  `VECTOR_ONLY`.
- If both projection paths fail, search uses `POSTGRES_FALLBACK`.
- Stale candidate IDs are omitted after PostgreSQL reload and recorded in
  metrics.
- Empty candidate lists are not treated as infrastructure failure.

## Proposed Refactoring Boundaries

### 1. Public Search Orchestration

`ArticlePublicSearchService` should remain the public-search coordinator, but its
internal steps should become easier to follow:

```txt
normalize query
-> collect keyword/vector attempts
-> resolve public search mode
-> select article IDs for that mode
-> assemble timing/failure metadata
```

The first pass should avoid introducing many classes. Extract private methods or
small package-private value objects only when they make the sequence easier to
test or read.

### 2. Article Response Loading

`ArticleService` should keep owning public article use cases, but search-specific
loading should become clearer:

```txt
public query search result
-> PostgreSQL fallback filter OR candidate ID reload
-> stale candidate count
-> metric observation
```

If a helper is extracted, it should be domain-specific, such as "load API-ready
articles by candidate IDs", not a generic repository wrapper.

### 3. Test Helpers

Tests may gain narrowly scoped factory/helper functions when repeated metric or
search-result setup obscures behavior. Avoid production abstractions whose only
purpose is to make tests shorter.

### 4. Onboarding Document

Create `docs/ONBOARDING.ko.md` and link it from `docs/README.md` and
`docs/README.ko.md`. Keep it concise and action-oriented:

- First-day reading order.
- Local run commands.
- Verification commands by service.
- Module responsibility map.
- Public API vs internal API.
- Search flow walkthrough.
- Source-of-truth/projection-store rule.
- Refactoring change-impact checklist.
- Blog topic queue rule for design trade-offs, errors, and avoided
  overengineering.

## Blog Topic Queue Rule

During each refactoring task, update `docs/blog/topic-queue.md` when at least
two of these are true:

- A responsibility boundary changed.
- An abstraction was intentionally avoided to prevent overengineering.
- A test helper or fixture was added to preserve behavior.
- A bug, confusing name, stale document, or risky coupling was found.
- A source-of-truth/projection-store, fallback, metric, or rebuild trade-off was
  clarified.
- A new developer handoff lesson emerged.

The queue entry should be factual and should mention verification evidence. If
no suitable topic appears, the task should explicitly report that no topic was
added.

## Verification Strategy

For the first execution slice:

- Run focused backend tests for search orchestration, article service, and search
  metrics while iterating.
- Run full backend tests before completion:
  `cd backend && ./gradlew test --rerun-tasks`.
- Run backend check before completion:
  `cd backend && ./gradlew check`.
- Run `git diff --check`.
- For documentation changes, search for stale or contradictory wording in
  onboarding, docs index, and API/search docs.

No local Docker smoke is required unless the refactor touches runtime behavior
around Elasticsearch, Qdrant, FastAPI, or endpoint contracts.
