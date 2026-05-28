# AI 서버

[English](README.md) | [한국어](README.ko.md)

Sigak AI 서버는 AI/RAG 관련 기능을 위해 FastAPI를 사용합니다.

## 책임
- Article summarization
- 기본 insight generation
- 향후 embedding과 RAG workflow

## 현재 상태
AI 서버는 로컬 개발을 위한 mock enrichment endpoint와 deterministic embedding endpoint를 제공합니다. 유료 API key는 필요하지 않습니다.

Deterministic embedding endpoint는 Spring Boot -> FastAPI -> Qdrant 연결 경계를 검증하기 위한 것입니다. Semantic 검색 품질을 주장하기 위한 모델은 아닙니다.

Sigak v0.1의 기본 retrieval 경로는 실제 embedding model을 사용하는 방향으로 잡습니다. Deterministic mode는 재현 가능한 local smoke test를 위한 fallback/test mode로 유지합니다. 첫 real mode는 프로젝트 제약상 외부 embedding API가 더 실용적인 경우가 아니라면 local sentence-transformers compatible embedding model을 우선합니다.

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
POST /api/embeddings/text
```
