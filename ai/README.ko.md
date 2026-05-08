# AI 서버

[English](README.md) | [한국어](README.ko.md)

Sigak AI 서버는 AI/RAG 관련 기능을 위해 FastAPI를 사용합니다.

## 책임
- Article summarization
- 기본 insight generation
- 향후 embedding과 RAG workflow

## 현재 상태
AI 서버는 로컬 개발을 위한 mock enrichment endpoint를 제공합니다. 유료 API key는 필요하지 않습니다.

## 로컬 실행

```bash
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

## 테스트

```bash
pytest
```

## 엔드포인트

```http
GET /health
POST /api/enrichment/article
```
