# Sigak Technical-News Automation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Single-agent implementation; one independent final read-only review is permitted by the review skill.

**Goal:** Automatically collect Sigak's approved technical-news feeds and refresh actual searchable data every ten minutes.

**Architecture:** Conditionally enabled Spring fixed-delay job → existing collection service → PostgreSQL → existing keyword/vector/graph projection services. Immutable in-memory operator diagnostics accompany persistent articles and existing failure events. Keep news-rag unchanged.

**Tech Stack:** Kotlin/Spring Boot scheduling/config binding, JUnit/Mockito, PostgreSQL, existing Docker Compose and local FastEmbed.

## Task 1: Automation service contract

Files: `backend/src/main/kotlin/com/sigak/collection/{config,dto,service,runner,controller}/` and matching tests.

- [x] Write failing tests for validated default-disabled configuration, a completed collect/rebuild cycle, duplicate-only cycles and retries after partial indexing.
- [x] Implement small settings, status DTO and automation service around existing services. Use a nonblocking in-process guard and finally release it; record sanitized errors.
- [x] Add failure/recovery, independent index failure, overlap and timestamp tests; verify focused tests pass.

## Task 2: Scheduling and operator diagnostics

Files: automation config/job/controller, `backend/src/main/resources/application.yml`, `.env.example`.

- [x] Write conditional-bean/binding/controller tests and observe expected failures.
- [x] Register `@EnableScheduling` and the conditional scheduled job with property-based ISO-8601 fixed/initial delays; validate configured source IDs.
- [x] Expose internal status through a thin GET controller; leave public API responses unchanged.
- [x] Verify focused tests and backend full `./gradlew test check`.

## Task 3: Deployment and real smoke

Files: `infra/deploy/compose.yml`, `infra/deploy/.env.example`, `backend/Dockerfile`, `infra/deploy/README.md`.

- [x] Enable automation only in deployment by default; add bounded settings and a persistent backend-state volume for arXiv cooldown.
- [x] Validate Compose without logging resolved passwords; build the backend image and replace only the deployment backend. Check Docker readiness without touching news-rag.
- [x] Observe scheduled live collection, actual counts/dates, all three projection outcomes, a repeat cycle with duplicate skips, and public search metrics.
- [x] Run the edge HTTP smoke and confirm internal automation status is denied externally.

## Task 4: Review, record, publish

- [x] Complete independent read-only review and address substantive findings with regression tests.
- [x] Update STATUS/ROADMAP in both languages, API spec, README, today's dev-log and existing topic queue candidate with measured facts.
- [x] Revalidate affected evidence/fixtures, check diffs and secrets exclusions, commit and push the authorized task branch; update the existing draft PR description.
- [x] Report implemented, currently observed and unverified long-term behavior separately.

## Execution evidence

- Backend full test/check:235 passed with0 failures/errors/skips; independent read-only review found no substantive unresolved issue.
- Image built and running backend replaced. Temporary60-second RSS verification:50 new, then0 new/50 duplicate skips. Production restored to600 seconds, all6 sources:23 new/57 skipped, then a real second scheduled cycle600 seconds later:0 new/80 skipped, all successful.
- Actual database78=73 real+5 seed; Elasticsearch78,Qdrant78, latest article present in HYBRID search with stale0/no fallback and fresh graph context.
- Public HTTP smoke6 passed through the renewed temporary HTTPS link; internal status denied. Old temporary link expired on provider side and was renewed without exposing private services.
- RAG dataset version2 reflects changed product semantics; version1 archived, all60 stillDRAFT. Fixtures/evidence validation passed. No paid AI calls, news-rag mutations or historical backfill.
- Long-term unattended uptime, permanent hosting, distributed locking/full run history and real enrichment remain outside this completed task.
