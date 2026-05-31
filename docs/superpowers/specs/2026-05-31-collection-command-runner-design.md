# Collection Command Runner Design

## Goal

Add a small Spring Boot command runner wrapper for controlled collection runs so local operators can execute collection from the terminal without calling the internal HTTP endpoint manually.

## Context

`CollectionRunService` already owns the important business behavior:

- selected source validation
- `maxArticlesPerSource` validation
- sequential source execution
- published/skipped/failed count aggregation
- run status calculation

The command runner must reuse that service instead of creating another collection path.

## Scope

Included:

- `collection-run` command recognized from Spring Boot application arguments.
- `--sources=id1,id2` optional source selector.
- `--max=N` optional article limit per source.
- Human-readable stdout summary with status, source count, article counts, IDs, and failure summaries.
- Non-zero exit code for invalid command arguments or service validation errors.
- Documentation and dev-log updates with verified commands.

Deferred:

- Persistent run history.
- Retry rules.
- Scheduler/background job.
- Projection rebuild chaining.
- Frontend/admin UI.

## Command Contract

```bash
cd backend
SIGAK_SEARCH_MODE=KEYWORD ./gradlew bootRun --args='collection-run --sources=github-blog --max=1'
```

Arguments:

- First positional argument must be `collection-run`.
- `--sources=github-blog,openai-blog` is optional. Missing or blank means all registered sources.
- `--max=1` is optional. Missing delegates to `CollectionRunService` default.
- Unknown options fail fast before collection.
- Invalid numeric `--max` fails fast before collection.

Exit behavior:

- No `collection-run` command: the normal web application starts as before.
- `collection-run` command: run collection once, print summary, then exit.
- Successful or partial run returns process exit code `0`.
- Invalid arguments or service validation errors return process exit code `1`.

## Design

Create a small `collection/runner` package:

- `CollectionRunCommand`
  - parsed command model wrapping `CollectionRunRequest`.
- `CollectionRunCommandParser`
  - converts raw app arguments into `CollectionRunCommand?`.
  - returns `null` when this is not a collection command so normal boot is unaffected.
- `CollectionRunCommandFormatter`
  - converts `CollectionRunResponse` or an error into readable text.
- `CollectionRunCommandRunner`
  - Spring `ApplicationRunner` that delegates to parser, service, formatter, and application exit.

The runner should stay thin. Tests should cover parser/formatter behavior and an application-level runner path without making external feed calls.

## Risks And Trade-Offs

- Calling `exitProcess` directly inside a Spring runner is hard to test. The runner should depend on a small `ApplicationExit` boundary.
- `bootRun --args=...` still initializes the web application context before running. This is acceptable for the MVP because the goal is a simple, reproducible local operator command, not a production-grade batch launcher.
- Runtime smoke should use `SIGAK_SEARCH_MODE=KEYWORD` unless Elasticsearch and Qdrant are intentionally running, because `SIGAK_SEARCH_MODE=postgres` is not a valid mode.
