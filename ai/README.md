# AI Server

[English](README.md) | [한국어](README.ko.md)

The Sigak AI server uses FastAPI for AI/RAG-related capabilities.

## Responsibilities
- Article summarization
- Basic insight generation
- Future embedding and RAG workflows

## Current Status
The AI server provides mock enrichment and deterministic embedding endpoints for local development. It does not require paid API keys.

The deterministic embedding endpoint is intended to validate Spring Boot -> FastAPI -> Qdrant wiring. It is not a semantic quality claim.

For Sigak v0.1, the preferred retrieval path should use a real embedding model. The deterministic mode should remain only as a fallback/test mode for reproducible local smoke tests. The first real mode should be a local sentence-transformers-compatible embedding model unless project constraints make an external embedding API more practical.

## Local Run

```bash
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

## Tests

```bash
pytest
```

## Endpoints

```http
GET /health
POST /api/enrichment/article
POST /api/embeddings/text
```
