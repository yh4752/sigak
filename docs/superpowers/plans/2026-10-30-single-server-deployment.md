# Single-server Sigak Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Use a single agent for implementation.

**Goal:** Make the real app locally accessible and produce a verified single-server deployment package.

**Architecture:** Caddy serves the Vite build and routes exact public reads to Spring Boot. PostgreSQL and rebuildable search/AI services remain private on the Compose network. Deployment volumes are separate from development volumes.

**Tech Stack:** React/Vite, Kotlin/JDK 17, FastAPI, Docker Compose, Caddy 2, Python standard-library HTTP smoke tests.

### Task 1: Current-state verification and recovery

- [x] Inspect git status and fetch origin without changing the working tree. Compare `git rev-list --count origin/main..HEAD` and the remote branch list.
- [x] Run frontend test/lint/build, backend `./gradlew test check --rerun-tasks`, AI pytest and evaluation pytest. Investigate network-only failures by rerunning with allowed network access.
- [x] Start existing local Compose services, backend and frontend; retain current data volumes.
- [x] Verify actual local article and research responses and open the page.

### Task 2: Public-origin contract before implementation

Files: `infra/deploy/smoke_test.py`.

- [x] Write a Python unittest HTTP smoke covering SPA deep routes, real article list/search/detail, research, internal API denial and write-method denial.
- [x] Run against the existing Vite origin; observe failure because `/api/articles` is HTML rather than the real API at that origin.

### Task 3: Container and edge configuration

Files: `.dockerignore`, `backend/Dockerfile`, `frontend/Dockerfile`, `infra/deploy/Caddyfile`, `infra/deploy/compose.yml`, `infra/deploy/.env.example`.

- [x] Create multi-stage JDK 17 backend and Node 24/Caddy frontend builds. Copy only source inputs and committed research artifacts; exclude local environments and build caches.
- [x] Create required-secret Compose configuration with isolated volumes, backend service URLs and a sole published Caddy service.
- [x] Configure exact public API GET/HEAD routes, reject internal/unknown APIs and unsupported methods, and support SPA deep links. Use an ordered Caddy route to prevent fallback bypass.
- [x] Verify missing-secret failure and `docker compose --env-file <local ignored env> -f infra/deploy/compose.yml config --quiet` success without logging secret values.
- [x] Build images and start the deployment on local port 8088. Run `python3 infra/deploy/smoke_test.py http://127.0.0.1:8088`; fix observed failures before claiming readiness.

### Task 4: Verification and handoff

Files: `infra/deploy/README.md`, `README.md`, `README.ko.md`, `docs/STATUS.md`, `docs/STATUS.ko.md`, `docs/ROADMAP.md`, `docs/ROADMAP.ko.md`, `docs/blog/2026-10-30-dev-log.md`, `docs/blog/topic-queue.md`.

- [x] Document the tested startup, public ports/domain switch, operator-only rebuild command, persistent volumes, safe stop/restart and public-hosting limits.
- [x] Record today's verified results and separate local access from unprovisioned Internet hosting.
- [x] Re-run changed-module gates where necessary, check diffs and review the deployment surface.
- [x] Preserve unrelated untracked files; prepare reviewable repository commits and clearly report remote publication status.

## Execution evidence — 2026-10-30

- Host gates: backend 218/frontend 41/AI 8/RAG 70 passed. Linux peer lock entries repaired; final source Docker builds succeeded after replacing unsupported ARM JDK Alpine images with Jammy.
- Real deployment edge: six HTTP tests passed after source builds; final repeat took 0.801s. Browser search/detail/research navigation was verified.
- Existing development database: 41 articles, local multilingual embedding dimension 384, HYBRID graph query returned 20. Deployment database: five seed articles, successful ES/vector/graph rebuild, HYBRID graph query returned five.
- Caddy config validates; only web ports publish on loopback by default. Missing credentials rejected; .env ignored and never printed.
- Independent read-only deployment review: no substantive unresolved issue.
- A temporary Cloudflare HTTPS preview passed all six HTTP tests in 6.791s. Permanent Internet host, paid account, custom DNS/TLS and automatic collection scheduling remain outside the completed local deployment work.
