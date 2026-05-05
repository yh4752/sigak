# Project Context

## What Sigak Is
Sigak is an AI-powered technical news insight platform for AI, software development, and computer science.

Sigak helps users find important technical changes, understand why they matter, and see how they connect to related technologies, organizations, research, and events.

## Portfolio Goal
The project is intended to become a portfolio-grade MVP for internship applications by late June 2026. The MVP should demonstrate clear backend architecture, realistic API design, simple AI integration, and a deployable local development setup.

## MVP Priorities
1. Build a working product before adding advanced architecture.
2. Keep Spring Boot as the main backend and API boundary.
3. Use FastAPI only for AI/RAG-related capabilities.
4. Use React + TypeScript + Vite for the frontend.
5. Keep Docker Compose local development simple.
6. Keep article data reusable for future enrichment and Graph RAG.
7. Document major architecture decisions as the project evolves.

## Initial MVP Scope
- News article list
- News article detail
- Keyword search
- AI summary for a selected article
- Importance and "why it matters" insight for selected articles
- Graph RAG-ready article metadata
- Limited relationship-based insight or graph-backed retrieval
- Simple frontend UI
- Docker Compose local setup
- Clear README

## Later Scope
These are intentionally out of scope until the MVP is stable:
- Obsidian-style full graph explorer
- Broad multi-source automated collection
- Advanced hybrid search
- Broad RAG over many articles
- Personalized recommendations
- User accounts
- Saved articles
- Advanced dashboards

## Service Responsibilities
The frontend communicates primarily with the Spring Boot backend. The Spring Boot backend owns user-facing APIs, business rules, persistence, and orchestration. The FastAPI service owns AI-specific tasks such as summarization, enrichment, embedding, and Graph RAG-related workflows.

The first data model should preserve enough raw article text and metadata to support later reprocessing. Graph RAG should be added through enrichment and indexing over saved content, not by scraping the same sources again.
