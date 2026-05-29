# AI Server

[English](README.md) | [한국어](README.ko.md)

The Sigak AI server uses FastAPI for AI/RAG-related capabilities.

## Responsibilities
- Article summarization
- Basic insight generation
- Future embedding and RAG workflows

## Current Status
The AI server provides mock enrichment and configurable embedding endpoints for local development. It does not require paid API keys.

The default embedding provider is a local FastEmbed multilingual model. Deterministic embedding remains available to validate Spring Boot -> FastAPI -> Qdrant wiring. It is not a semantic quality claim.

For Sigak v0.1, the preferred retrieval path should use a real multilingual embedding model because article sources may include Korean, English, and other languages. The deterministic mode should remain only as a fallback/test mode for reproducible local smoke tests. The first real mode uses FastEmbed because it provides local ONNX Runtime-based embeddings without pulling heavy PyTorch/CUDA dependencies.

Embedding settings:

```txt
SIGAK_EMBEDDING_PROVIDER=local
SIGAK_EMBEDDING_MODEL_NAME=sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2
```

Use `SIGAK_EMBEDDING_PROVIDER=deterministic` only for fast local smoke tests that should not download a model.

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
