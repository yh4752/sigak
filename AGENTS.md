# AGENTS.md

## Project
Sigak is an AI-powered news insight platform.

The goal is to build a portfolio-grade MVP for internship applications by late June 2026.

## Core Direction
Sigak should help users search, summarize, and understand news articles through AI-assisted insight generation.

This project prioritizes:

1. Working MVP
2. Clear backend architecture
3. Practical AI/RAG integration
4. Clean documentation
5. Deployable structure

## Tech Stack

### Main Backend
- Spring Boot
- Java or Kotlin
- REST API
- JPA
- PostgreSQL or MySQL

### AI Server
- FastAPI
- Python
- LLM/RAG-related features
- Embedding/search integration

### Frontend
- React + TypeScript + Vite
- Keep UI simple for MVP
- Do not use Next.js for the initial MVP unless explicitly requested

### Search / Vector
- Elasticsearch for keyword search
- Qdrant for vector search
- Hybrid search can be added after basic MVP

### Infra
- Docker Compose first
- Local development should be easy to run
- Avoid cloud-specific dependencies before MVP

## Architecture Principles
- Spring Boot is the main application backend.
- FastAPI is used only for AI/RAG-related capabilities.
- Frontend communicates primarily with Spring Boot.
- Spring Boot may call FastAPI for AI functions.
- Keep services loosely coupled.
- Avoid overengineering before MVP.

## Development Rules
- Make small, reviewable changes.
- Do not rewrite the whole project unless explicitly requested.
- Do not introduce unnecessary frameworks or complex abstractions.
- Prefer readable code over clever code.
- Follow `docs/CODING_CONVENTIONS.md` before writing or modifying code.
- Explain architectural trade-offs before large structural changes.
- Keep business logic separate from controllers.
- Keep environment-specific values out of source code.

## Code Convention Rules
- Use `docs/CODING_CONVENTIONS.md` as the source of truth for naming, comments, and file organization.
- Conventions are inspired by public large-company style guides, but Sigak-specific rules take precedence.
- Write code comments in Korean.
- Prefer meaningful Korean comments that explain intent, trade-offs, business rules, or non-obvious constraints.
- Avoid comments that simply repeat what the code already says.
- When a requested change conflicts with the convention document, explain the trade-off before implementing.

## Git Rules
Use small PR-sized changes.

Commit message style:
- `feat: add new feature`
- `fix: fix bug`
- `docs: update documentation`
- `refactor: improve structure without behavior change`
- `chore: project setup or maintenance`
- `test: add or update tests`

Do not commit:
- API keys
- tokens
- credentials
- `.env`
- build artifacts
- personal information

## Documentation Rules
Update documentation when adding major functionality.

Important docs:
- `README.md`
- `docs/README.md`
- `docs/PRODUCT.md`
- `docs/API_SPEC.md`
- `docs/CODING_CONVENTIONS.md`
- `docs/ROADMAP.md`
- `docs/STATUS.md`
- `docs/RESEARCH_STRATEGY.md`
- `docs/SOURCE_POLICY.md`
- `docs/decisions/`
- `docs/blog/`

Architecture decisions should be recorded as ADRs under:

```txt
docs/decisions/
```

Example:

```txt
docs/decisions/0001-initial-architecture.md
```

## Daily Development Blog Rules
When asked to write a daily development blog post, create it under:

```txt
docs/blog/YYYY-MM-DD-dev-log.md
```

Use the actual work date for the filename. Write in Korean unless explicitly requested otherwise.

Daily posts should be portfolio-friendly technical notes, not only raw work logs. Emphasize:
- what was built or documented
- why the decisions were made
- what trade-offs were considered
- what was intentionally deferred for MVP focus
- how the work was verified, if applicable

Prefer a clear structure:
- title with date and topic
- summary
- work completed
- key decisions
- implementation notes
- verification
- next steps

Keep the tone practical and reflective. Avoid exaggerating progress or claiming unverified results.

## Testing Rules
Add tests when implementing backend features.

Minimum expectation:
- Service-level tests for core logic
- API tests for important endpoints
- Simple smoke tests for AI server endpoints

Do not skip tests for important business logic.

## MVP Scope
Initial MVP should include:

1. News article list
2. News article detail
3. Keyword search
4. AI summary for a selected article
5. Importance and why-it-matters insight
6. Simple frontend UI
7. Docker Compose local setup
8. Clear README
9. Graph RAG-ready article metadata
10. Limited relationship-based insight or graph-backed retrieval

Do not implement advanced features before the MVP is stable.

Advanced features for later:
- Hybrid search
- RAG over multiple articles
- Personalized recommendations
- Obsidian-style full graph explorer
- User accounts
- Saved articles
- Advanced dashboards

## Frontend Rules
- Use React + TypeScript + Vite.
- Keep components simple.
- Prefer clear folder structure.
- Use API client modules instead of calling fetch directly everywhere.
- Use Axios for frontend HTTP calls.
- Use Zod to validate backend API responses at the API client boundary.
- Avoid complex state management libraries unless necessary.
- Basic CSS or Tailwind is acceptable.

## Backend Rules
- Use layered architecture:
  - controller
  - service
  - repository
  - domain/entity
  - dto
  - config
- Keep controllers thin.
- Put business rules in services.
- Use DTOs for API request/response.
- Do not expose entities directly through APIs.

## AI Server Rules
- Use FastAPI.
- Keep AI endpoints simple.
- Separate:
  - routers
  - services
  - schemas
  - clients
- Use mock AI responses first if external API keys are not available.
- Do not require paid APIs for local development.
- Add `.env.example` for required environment variables.

## Security Rules
- Never commit secrets.
- Use `.env.example`.
- Validate external inputs.
- Avoid exposing stack traces in API responses.
- Keep CORS explicit and minimal.

## Working Style
Before implementing a large feature:

1. Summarize the intended change.
2. List files that will be changed.
3. Make the smallest useful implementation.
4. Update docs if needed.
5. Mention how to run or test the change.
