# AI 서버

[English](README.md) | [한국어](README.ko.md)

Sigak AI 서버는 AI/RAG 관련 기능을 위해 FastAPI를 사용합니다.

## 책임
- Article summarization
- 기본 insight generation
- 향후 embedding과 RAG workflow

## 현재 상태
AI 서버는 로컬 개발을 위한 mock enrichment endpoint와 configurable embedding endpoint를 제공합니다. 유료 API key는 필요하지 않습니다.

기본 embedding provider는 local FastEmbed multilingual model입니다. Deterministic embedding은 Spring Boot -> FastAPI -> Qdrant 연결 경계를 검증하기 위해 계속 제공합니다. Semantic 검색 품질을 주장하기 위한 모델은 아닙니다.

Sigak v0.1의 기본 retrieval 경로는 한글, 영어 등 다양한 언어의 기사에 대응할 수 있는 실제 multilingual embedding model을 사용하는 방향으로 잡습니다. Deterministic mode는 재현 가능한 local smoke test를 위한 fallback/test mode로 유지합니다. 첫 real mode는 무거운 PyTorch/CUDA 의존성을 피하면서 ONNX Runtime 기반 local embedding을 제공하는 FastEmbed를 사용합니다.

Embedding 설정:

```txt
SIGAK_EMBEDDING_PROVIDER=local
SIGAK_EMBEDDING_MODEL_NAME=sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2
```

모델 다운로드 없이 빠른 local smoke test만 확인할 때는 `SIGAK_EMBEDDING_PROVIDER=deterministic`을 사용합니다.

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
