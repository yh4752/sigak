# Coding Conventions

[English](CODING_CONVENTIONS.md) | [한국어](CODING_CONVENTIONS.ko.md)

Last updated: 2026-05-11

Sigak uses a small, explicit convention set inspired by public style guides from
large engineering organizations. The goal is consistency and readability, not
blindly copying every rule from a large company.

## Priority

When conventions appear to conflict, follow this order:

1. Existing style in the same module
2. Rules in this document
3. Formatter and linter output
4. Public language style guides
5. Personal preference

## General Principles

- Prefer readable code over clever code.
- Use names that describe domain meaning, not implementation accidents.
- Keep functions focused on one responsibility.
- Keep controllers and routers thin.
- Put business rules in services.
- Avoid abbreviations unless they are common in the domain or language.
- Do not introduce new architectural patterns without a clear reason.

## Comment Policy

Comments are important in this project, but they must be useful. Sigak comments
should explain intent, business rules, trade-offs, or non-obvious constraints.

### Language

- Write code comments in Korean.
- Keep API field names, class names, function names, and technical identifiers in
  English.
- Use English only in comments when quoting an external error message, API field,
  protocol term, or library concept that should not be translated.

### Good Comments

Use comments to explain why the code exists or why a choice was made.

```kotlin
// 뉴스 원문이 비어 있어도 수집 상태를 추적해야 하므로 Article은 먼저 저장한다.
```

```python
# 외부 LLM 없이도 로컬 개발이 가능하도록 기본 응답은 mock 서비스에서 생성한다.
```

```ts
// API 응답 형태가 바뀌면 화면 전체가 깨질 수 있어 client 경계에서 먼저 검증한다.
```

### Comments To Avoid

Avoid comments that only repeat the code.

```kotlin
// id로 기사를 찾는다.
val article = articleRepository.findById(id)
```

Avoid noisy section comments when clear function names or file structure would be
better.

```ts
// 함수 시작
function loadArticles() {
  // ...
}
```

### Required Comment Cases

Add a Korean comment when code includes:

- A non-obvious business rule
- A temporary MVP decision or intentional limitation
- A trade-off between correctness, speed, and simplicity
- A workaround for library, framework, or infrastructure behavior
- AI/RAG behavior that may surprise future maintainers
- Data mapping that loses information or changes meaning

When a comment describes a temporary limitation, include the reason and the
condition for revisiting it.

```python
# MVP 단계에서는 외부 API 비용을 피하기 위해 mock 요약을 사용한다.
# 실제 LLM 연동이 추가되면 이 서비스는 provider interface 뒤로 이동한다.
```

## Backend Conventions

The backend currently uses Kotlin with Spring Boot.

### Naming

- Controllers: `ArticleController`
- Services: `ArticleService`
- Repositories: `ArticleRepository`
- Entities: singular nouns, such as `Article` or `Source`
- DTOs: `ArticleSearchRequest`, `ArticleSummaryResponse`
- Config classes: `CorsConfig`, `OpenApiConfig`
- Test classes: `ArticleServiceTest`, `ArticleControllerTest`

### Structure

- Keep controllers thin and delegate business logic to services.
- Do not expose JPA entities directly through API responses.
- Keep request and response DTOs explicit.
- Keep persistence-specific decisions out of controllers.
- Prefer constructor injection.

## AI Server Conventions

The AI server uses FastAPI and Python.

### Naming

- Files and modules: `snake_case.py`
- Functions and variables: `snake_case`
- Classes and Pydantic schemas: `PascalCase`
- Routers: `enrichment.py`, `article_summary.py`
- Services: `mock_enrichment_service.py`, `summary_service.py`

### Structure

- Keep FastAPI routers thin.
- Put AI/RAG behavior in services.
- Put request and response models in `schemas`.
- Keep external provider clients in `clients` when they are added.
- Mock AI behavior must remain available for local development.

## Frontend Conventions

The frontend uses React, TypeScript, Vite, Axios, and Zod.

### Naming

- Components: `PascalCase`, such as `ArticleListItem`
- Component files: `ArticleListItem.tsx`
- Hooks: `useArticleSearch`
- API modules: `articles.ts`, `httpClient.ts`
- Variables and functions: `camelCase`
- Constants: `UPPER_SNAKE_CASE`
- Types and schemas: `ArticleSummaryResponse`, `ArticleSchema`
- Test files: `ArticleListItem.test.tsx`, `articles.test.ts`

### Structure

- Keep API calls inside API client modules.
- Validate backend responses with Zod at the API client boundary.
- Keep components small and focused on rendering and user interaction.
- Avoid complex state management libraries unless the workflow demands them.

## Test Naming

- Test names should describe observable behavior.
- Prefer behavior-oriented names over implementation details.
- Backend and AI server tests may use natural language style if it improves
  readability.

Examples:

```kotlin
fun `returns article detail with AI insight fields`() {
  // ...
}
```

```python
def test_enrichment_returns_mock_summary_without_external_api():
    ...
```

```ts
it('renders article title and source name', () => {
  // ...
})
```

## Review Checklist

Before finishing a code change, check:

- Are names consistent with nearby files?
- Are Korean comments used for non-obvious intent and trade-offs?
- Are comments explaining why, not restating what?
- Are controllers, routers, services, DTOs, and schemas separated clearly?
- Are frontend API responses validated at the client boundary?
- Are environment-specific values kept out of source code?
- Did the relevant formatter, linter, or test command run when practical?
