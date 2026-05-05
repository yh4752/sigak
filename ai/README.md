# AI Server

The Sigak AI server uses FastAPI for AI/RAG-related capabilities.

## Responsibilities
- Article summarization
- Basic insight generation
- Future embedding and RAG workflows

## Current Status
The AI server provides a mock enrichment endpoint for local development. It does not require paid API keys.

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
```
