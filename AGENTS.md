# AGENTS.md

This is the **single source of truth for how agents (Codex, Claude, etc.) develop Sigak.**
If any other file conflicts with this one on *rules*, this file wins. Tool-specific files
(e.g. `CLAUDE.md`) only add tool-specific details and otherwise defer here.

---

## 0. Read order (start every session here)

Read only what the task needs, in this order:

1. **This file** — rules, lifecycle, definition of done.
2. **`docs/STATUS.md`** — current state, what is done, known risks. *(authoritative for "where are we now")*
3. **`docs/ROADMAP.md`** — what comes next and why. *(authoritative for "what to do next")*
4. **The task's spec & plan** under `docs/superpowers/specs/` and `docs/superpowers/plans/`, if one exists.
5. **`docs/CODING_CONVENTIONS.md`** — before writing or changing any code.
6. Domain docs as needed: `docs/PRODUCT.md`, `docs/API_SPEC.md`, `docs/decisions/`.

Where each kind of information lives (do not duplicate it into this file):

| Need | Source of truth |
| --- | --- |
| Stable rules / how to work | `AGENTS.md` (this file) |
| Current state, progress, risks | `docs/STATUS.md` |
| What to build next, scope, phases | `docs/ROADMAP.md` |
| Why a structural decision was made | `docs/decisions/` (ADRs) |
| Per-feature design & task plan | `docs/superpowers/` (see its `README.md`) |
| Naming, comments, structure | `docs/CODING_CONVENTIONS.md` |
| API contract | `docs/API_SPEC.md` |
| Daily log + blog | `docs/blog/WRITING_GUIDE.ko.md` |

> MVP scope and feature status are **not** kept here. They live in `STATUS.md` and `ROADMAP.md`
> because they change. Read them for current scope before assuming a feature is in or out.

---

## 1. Project

Sigak is an AI-powered technical news insight platform (AI / software / CS).
Goal: a portfolio-grade MVP for internship applications by late June 2026.

Priorities, in order: (1) working MVP, (2) clear backend architecture,
(3) practical AI/RAG integration, (4) clean documentation, (5) deployable structure.

---

## 2. Architecture (stable)

- **Spring Boot (Kotlin)** is the main application backend and the only API boundary the frontend talks to.
- **FastAPI (Python)** is used *only* for AI/RAG capabilities; Spring Boot calls it, the frontend never does.
- **React + TypeScript + Vite** frontend; validate backend responses with Zod at the API client boundary. No Next.js for MVP.
- **PostgreSQL is the source of truth.** Elasticsearch (keyword), Qdrant (vector), and Neo4j (graph) are **rebuildable projection stores**, never primary data.
- **Docker Compose first.** Keep local dev easy; avoid cloud-specific dependencies before MVP.

Principles: keep services loosely coupled, controllers/routers thin, business rules in services,
DTOs for API I/O (never expose entities), no new architectural patterns without a clear reason,
no overengineering before MVP. Explain trade-offs before large structural changes.

Layered backend: `controller → service → repository → domain/entity`, plus `dto` and `config`.
FastAPI layers: `routers / services / schemas / clients`; mock AI behavior must always work without paid APIs (`.env.example` for required vars).

---

## 3. Development lifecycle

Follow this flow for any non-trivial feature. Each step has a home directory.

```txt
1. Decide    -> if it's a structural decision, record an ADR in docs/decisions/
2. Spec      -> write/read the design in docs/superpowers/specs/
3. Plan      -> break it into checkboxed tasks in docs/superpowers/plans/
4. Implement -> smallest useful, reviewable change; follow CODING_CONVENTIONS.md
5. Verify    -> run the Definition of Done gate (section 4). Do not skip.
6. Record    -> update docs/STATUS.md, then write the session wrap-up (section 6)
```

For executing a plan task-by-task, use the `superpowers:subagent-driven-development`
(or `superpowers:executing-plans`) sub-skill, as the plan files instruct.

Working style before a large feature: summarize the intended change, list files to be
changed, make the smallest useful implementation, then verify.

---

## 4. Definition of Done (verification gate)

A change is **not done** until the relevant commands below have actually been run and pass.
Never claim success for a command you did not run (same rule as the dev-log: report
unverified items as `미검증`).

Backend (`cd backend`):
```bash
./gradlew test          # required for any backend change
./gradlew test --tests <ClassName>   # focused run while iterating
./gradlew check         # format/static checks before finishing
```

Frontend (`cd frontend`):
```bash
npm test                # vitest run — required for any frontend change
npm run lint            # eslint
npm run build           # tsc -b && vite build — must pass before done
```

AI server (`cd ai`):
```bash
.venv/bin/python -m pytest        # required for any AI server change
```

Cross-service / search changes: run the relevant local smoke check (projection rebuild,
`/api/articles?query=...`, internal search/metrics endpoints) and record the actual
observed values (counts, mode, fallback). Add tests for important business logic — never skip
service-level tests for core features, API tests for important endpoints, or AI smoke tests.

Also confirm the `docs/CODING_CONVENTIONS.md` review checklist items before finishing.

---

## 5. Coding rules (summary; full set in CODING_CONVENTIONS.md)

- Prefer readable code over clever code; names describe domain meaning.
- Keep controllers/routers thin, business rules in services, DTOs for API I/O.
- Frontend: API calls in client modules (Axios), Zod validation at the boundary, simple components, no heavy state libs unless needed.
- **Write code comments in Korean**; keep identifiers/API fields in English. Comment *why* (intent, business rule, trade-off, workaround), not *what*.
- Follow `docs/CODING_CONVENTIONS.md` for naming, structure, and the pre-finish review checklist. If a request conflicts with it, explain the trade-off before implementing.

---

## 6. Session wrap-up (close the loop every session)

When a development session ends, do all of the following:

1. **Update `docs/STATUS.md`** if state, progress, or risks changed (refresh "Last updated").
2. **Update `docs/ROADMAP.md`** if scope or next steps shifted.
3. **Update relevant domain docs** (`API_SPEC.md`, ADRs, etc.) for major functionality.
4. **Write the dev-log** under `docs/blog/YYYY-MM-DD-dev-log.md` per
   `docs/blog/WRITING_GUIDE.ko.md` — using only session-verified facts.
5. **Run the topic-queue routine** in that guide and update `docs/blog/topic-queue.md`.
6. **Report** to the user what was verified vs. left `미검증`.

### Daily blog rules (detail)

`docs/blog/WRITING_GUIDE.ko.md` is the single source of truth for both the daily dev-log
and the topic-based technical posts. Read it before writing. Non-negotiables:
use only facts from the session you actually ran; never mark an unrun test as passed;
invent no numbers; separate completed from deferred work; explain *why* for key decisions;
update the topic-queue when 2+ criteria match. Write in Korean unless asked otherwise.

---

## 7. Git, security, and secrets

Small PR-sized commits. Commit message style:
`feat:` / `fix:` / `docs:` / `refactor:` / `chore:` / `test:`.

Never commit: API keys, tokens, credentials, `.env`, build artifacts, personal data.
Use `.env.example`. Validate external inputs. Don't expose stack traces in API responses.
Keep CORS explicit and minimal. Keep environment-specific values out of source code.
