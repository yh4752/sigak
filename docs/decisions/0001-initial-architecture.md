# 0001: Initial Architecture

## Status
Accepted

## Date
2026-05-05

## Context
Sigak needs to become a portfolio-grade MVP by late June 2026. The architecture should show practical backend design and AI integration without delaying the first working product.

## Decision
Use Spring Boot as the main application backend, FastAPI only for AI/RAG-related capabilities, and React + TypeScript + Vite for the frontend.

The frontend will communicate primarily with Spring Boot. Spring Boot may call FastAPI when a feature requires AI-specific work such as summarization, embedding, or future RAG behavior.

Use Docker Compose for local development. Plan for Elasticsearch and Qdrant, but do not implement them until the MVP needs search and vector retrieval.

Do not use Next.js for the initial MVP.

## Consequences
- The main backend has one clear API boundary.
- AI concerns stay isolated in a Python service where LLM and embedding libraries are easier to use.
- The frontend remains simple and fast to develop.
- The project avoids cloud-specific dependencies before the MVP.
- Search and vector infrastructure can be added later without blocking early features.

## Alternatives Considered
- Single FastAPI backend: simpler at first, but weaker fit for the stated Spring Boot backend portfolio goal.
- Next.js full-stack app: convenient for product speed, but conflicts with the initial MVP direction.
- Implementing Elasticsearch and Qdrant immediately: useful later, but too much infrastructure before the article workflow is stable.
