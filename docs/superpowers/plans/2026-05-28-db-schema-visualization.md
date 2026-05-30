# DB Schema Visualization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a reproducible one-off SchemaSpy workflow for visualizing the PostgreSQL schema.

**Architecture:** Add `db-schema` as a Docker Compose service behind the `tools` profile so it only runs on demand. Generate local HTML output under `outputs/db-schema/` and document the workflow in both infra READMEs.

**Tech Stack:** Docker Compose, PostgreSQL, SchemaSpy.

---

### Task 1: Add SchemaSpy Tooling

**Files:**
- Modify: `infra/docker-compose.yml`
- Modify: `.gitignore`

- [ ] **Step 1: Add a `db-schema` service to the Compose file**

Add a SchemaSpy service with `profiles: ["tools"]`, `depends_on.postgres.condition: service_healthy`, PostgreSQL connection arguments, `platform: linux/amd64` for the current SchemaSpy image, and a bind mount from `../outputs/db-schema` to `/output`.

- [ ] **Step 2: Ignore local generated outputs**

Add `outputs/` to `.gitignore` so generated ERD files and spreadsheet exports remain local artifacts.

- [ ] **Step 3: Validate Compose configuration**

Run:

```bash
docker compose -f infra/docker-compose.yml config
```

Expected: Compose config renders successfully and includes `db-schema` only as a profiled service.

### Task 2: Document Usage

**Files:**
- Modify: `infra/README.md`
- Modify: `infra/README.ko.md`

- [ ] **Step 1: Add the service to the infra service table**

Document `db-schema` as an on-demand SchemaSpy ERD generator.

- [ ] **Step 2: Add usage instructions**

Document:

```bash
docker compose -f infra/docker-compose.yml up -d postgres
docker compose -f infra/docker-compose.yml --profile tools run --rm db-schema
```

Expected output path:

```txt
outputs/db-schema/index.html
```

### Task 3: Verify the Workflow

**Files:**
- No source files modified.

- [ ] **Step 1: Run SchemaSpy**

Run:

```bash
docker compose -f infra/docker-compose.yml --profile tools run --rm db-schema
```

Expected: command exits successfully and creates `outputs/db-schema/index.html`.

- [ ] **Step 2: Check generated artifact**

Run:

```bash
test -f outputs/db-schema/index.html
```

Expected: exit code `0`.

- [ ] **Step 3: Check Git status**

Run:

```bash
git status --short
```

Expected: source changes are visible, generated `outputs/` files are ignored.
