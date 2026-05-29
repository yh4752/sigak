# Real Embedding Provider Design

날짜: 2026-05-28

## 목표

Sigak v0.1의 vector search는 실제 embedding model을 기본 경로로 사용한다. 현재 deterministic embedding은 Spring Boot -> FastAPI -> Qdrant 연결을 검증하기 위한 fallback/test mode로 유지한다.

이 설계의 목표는 다음 Qdrant projection 작업 전에 embedding provider 경계를 명확히 하는 것이다.

- semantic retrieval 품질을 주장할 수 있는 실제 embedding model mode를 추가한다.
- 유료 API key 없이 로컬 실행 가능한 기본 경로를 유지한다.
- provider를 교체해도 Spring Boot와 Qdrant projection 코드를 크게 바꾸지 않게 한다.
- model name, provider, dimension을 projection metadata로 남겨 나중에 benchmark와 재색인을 설명할 수 있게 한다.

## 비목표

- 이번 설계에서는 Qdrant upsert 구현을 포함하지 않는다.
- 이번 설계에서는 hybrid search나 RRF를 구현하지 않는다.
- 이번 설계에서는 모델 품질을 확정하지 않는다. v0.1에서는 작은 labeled query set으로 keyword, vector, hybrid 결과를 비교한다.
- 이번 설계에서는 대형 embedding model이나 GPU 전제 배포를 선택하지 않는다.

## 접근안 비교

### 접근안 A. deterministic embedding만 유지

장점:

- 가장 빠르고 테스트가 쉽다.
- 외부 의존성과 모델 다운로드가 없다.
- 같은 입력에 항상 같은 vector를 반환한다.

단점:

- semantic similarity를 표현하지 못한다.
- Qdrant 연결 smoke test 외에는 검색 품질 근거가 되기 어렵다.
- 포트폴리오에서 vector search 품질을 설명하기 어렵다.

판단: fallback/test mode로만 유지한다.

### 접근안 B. 외부 embedding API를 기본으로 사용

장점:

- 모델 품질과 운영형 구조를 설명하기 좋다.
- FastAPI provider만 교체하면 Spring Boot contract는 유지할 수 있다.

단점:

- API key와 비용이 필요하다.
- 로컬 재현성이 약해진다.
- 3주 v0.1 일정에서 credential, rate limit, 장애 처리가 추가 부담이 된다.

판단: 나중 대안으로 둔다. v0.1 기본 경로로는 무겁다.

### 접근안 C. local sentence-transformers provider를 기본으로 사용

장점:

- 유료 API key 없이 실제 semantic embedding을 만들 수 있다.
- FastAPI가 Python 기반이라 embedding library를 다루기 쉽다.
- local benchmark에서 keyword/vector/hybrid 결과를 재현할 수 있다.
- deterministic provider와 같은 API contract를 공유할 수 있다.

단점:

- 모델 다운로드와 cold start 시간이 생긴다.
- Docker image가 커질 수 있다.
- Qdrant collection dimension이 provider에 따라 달라지므로 dimension 검증이 필요하다.

판단: 실제 model 경로라는 방향은 맞지만, Docker image 크기와 의존성 리스크 때문에 직접 구현안으로는 보류한다.

### 접근안 D. local FastEmbed multilingual provider를 기본으로 사용

장점:

- 유료 API key 없이 실제 semantic embedding을 만들 수 있다.
- Qdrant가 관리하는 경량 embedding library라 이후 Qdrant projection과 자연스럽게 연결된다.
- ONNX Runtime 기반이라 PyTorch/CUDA 대용량 의존성을 피할 수 있다.
- multilingual model 중 384차원 후보를 고르면 local MVP의 메모리와 색인 비용을 낮추기 좋다.

단점:

- 지원 model 목록 안에서 선택해야 하므로 sentence-transformers 직접 사용보다 자유도가 낮다.
- 첫 요청 또는 Docker build 이후 model download/cold start 비용은 여전히 존재한다.

판단: v0.1 구현안으로 보정한다. Docker build 확인 중 sentence-transformers가 Torch/CUDA 대용량 의존성을 끌어오는 리스크가 확인되었으므로, local provider는 FastEmbed로 구현한다.

## 모델 선택 기준

v0.1의 첫 local model 후보는 FastEmbed 지원 multilingual 모델인 `sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2`로 둔다.

선택 이유:

- FastEmbed 지원 목록 기준으로 `sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2`는 약 50개 언어를 지원하는 multilingual text embedding model이며 384차원 vector를 생성한다.
- FastEmbed는 ONNX Runtime 기반이라 PyTorch/CUDA GB급 의존성을 피할 수 있다.
- Qdrant와 함께 사용하기 위한 문서와 예제가 잘 정리되어 있다.
- 384차원은 Qdrant local MVP에서 메모리와 색인 비용을 낮추기 좋다.

대안:

- 품질을 더 중시하면 `sentence-transformers/paraphrase-multilingual-mpnet-base-v2`를 후보로 둔다. 다만 768차원, 약 1GB라 MVP Docker와 Qdrant local 비용이 커진다.
- 더 강한 multilingual retrieval을 중시하면 `intfloat/multilingual-e5-large`를 후보로 둔다. 다만 1024차원, 약 2.24GB이며 query/document prefix 정책이 필요하다.
- BGE-M3는 multilingual retrieval 후보로 좋지만, 현재 FastEmbed 경량 provider 기본값으로 바로 쓰기보다는 별도 provider 또는 실험 단계에서 검토한다.

## API 계약

기존 endpoint를 유지한다.

```http
POST /api/embeddings/text
```

요청:

```json
{
  "text": "Graph RAG improves relationship-aware retrieval."
}
```

응답:

```json
{
  "modelName": "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
  "provider": "local",
  "dimension": 384,
  "embedding": [0.0123, -0.0456]
}
```

호환성:

- `modelName`, `dimension`, `embedding`은 유지한다.
- `provider`를 추가한다.
- Spring Boot DTO는 `provider`를 optional 또는 required로 받을지 구현 시 결정한다. 새 FastAPI 응답에서는 항상 포함한다.
- whitespace-only text는 계속 422 또는 client-side validation error로 거절한다.

## FastAPI 구조

추가할 구성:

```txt
ai/app/config.py
ai/app/services/embedding_provider.py
ai/app/services/deterministic_embedding_service.py
ai/app/services/local_fastembed_embedding_service.py
```

Provider interface:

```python
class EmbeddingProvider(Protocol):
    provider_name: str
    model_name: str
    dimension: int

    def embed_text(self, text: str) -> list[float]:
        ...
```

Provider selection:

```txt
SIGAK_EMBEDDING_PROVIDER=local
SIGAK_EMBEDDING_MODEL_NAME=sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2
```

Allowed provider values:

- `local`: real FastEmbed provider
- `deterministic`: current deterministic hash provider

Default:

- local run default는 `local`로 둔다.
- 테스트에서는 provider 객체를 직접 주입하거나 deterministic provider를 사용한다.
- 모델 다운로드나 설치가 실패하면 조용히 fallback하지 않는다. API가 실패해야 운영자가 실제 모델 경로가 깨졌다는 것을 알 수 있다.

## Spring Boot 구조

현재 `FastApiEmbeddingClient`는 그대로 유지한다.

변경할 점:

- `EmbeddingResponse`에 `provider` 필드를 추가한다.
- Qdrant projection은 `dimension`이 설정된 collection dimension과 맞는지 검증한다.
- projection metadata에는 다음 값을 payload 또는 rebuild response에 남긴다.
  - `embeddingProvider`
  - `embeddingModelName`
  - `embeddingDimension`
  - `embeddedAt`

Spring Boot는 embedding model을 직접 로드하지 않는다. AI/RAG 책임은 FastAPI에 둔다.

## Qdrant projection 영향

Qdrant collection은 vector dimension과 distance metric을 collection 설정으로 가진다. 따라서 provider를 바꾸면 기존 collection의 dimension과 맞지 않을 수 있다.

v0.1 정책:

- collection name은 model family 또는 version을 포함한다.
- 예: `sigak-article-vectors-minilm-v1`
- dimension mismatch가 있으면 rebuild endpoint는 실패한다.
- model을 바꿀 때는 새 collection을 만들고 재색인한다.
- distance는 `Cosine`을 기본으로 둔다.

이 정책은 collection을 덮어쓰다가 기존 vector와 새 vector가 섞이는 문제를 막는다.

## Error Handling

- blank text: FastAPI schema validation에서 거절한다.
- model load failure: startup 또는 첫 요청에서 명확한 500 error를 반환한다.
- embedding dimension mismatch: Spring Boot projection 단계에서 실패 처리한다.
- FastAPI unavailable: Qdrant rebuild는 실패로 기록한다. public article API는 영향받지 않는다.
- provider config invalid: FastAPI startup 또는 endpoint 호출 시 명확한 error를 반환한다.

## Testing

FastAPI:

- deterministic provider는 같은 text에 같은 vector를 반환한다.
- local provider는 configured model name과 dimension을 응답에 포함한다.
- whitespace-only text는 거절한다.
- provider config가 invalid이면 명확히 실패한다.

Spring Boot:

- `FastApiEmbeddingClient`가 `provider`, `modelName`, `dimension`, `embedding`을 매핑한다.
- blank text는 FastAPI 호출 전에 거절한다.
- Qdrant projection 구현 시 dimension mismatch를 테스트한다.

Smoke test:

```bash
docker compose -f infra/docker-compose.yml up -d ai
curl -X POST http://localhost:8000/api/embeddings/text \
  -H 'Content-Type: application/json' \
  -d '{"text":"Graph RAG improves relationship-aware retrieval."}'
```

기대:

- `provider=local`
- `modelName=sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2`
- `dimension=384`
- `embedding` length가 384

## 문서 업데이트 대상

구현 시 다음 문서를 함께 갱신한다.

- `ai/README.md`
- `ai/README.ko.md`
- `docs/API_SPEC.md`
- `docs/API_SPEC.ko.md`
- `docs/STATUS.md`
- `docs/STATUS.ko.md`
- `docs/blog/topic-queue.md`

## 근거 자료

- FastEmbed supported models: `sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2`는 multilingual text embedding model이며 384차원 vector를 생성한다.
  - https://qdrant.github.io/fastembed/examples/Supported_Models/
- FastEmbed PyPI documentation: FastEmbed는 ONNX Runtime 기반 경량 embedding library다.
  - https://pypi.org/project/fastembed/
- Qdrant FastEmbed docs: FastEmbed는 Qdrant와 쉽게 통합할 수 있는 embedding generation library다.
  - https://qdrant.tech/documentation/fastembed/
- Qdrant collection docs: 같은 collection 안의 vector는 동일한 dimensionality와 metric을 가져야 한다. collection 생성 시 vector size와 distance를 설정한다.
  - https://qdrant.tech/documentation/manage-data/collections/

## 결정

Sigak v0.1은 FastEmbed 기반 real local embedding provider를 기본 경로로 설계한다. deterministic provider는 fallback/test mode로 유지한다.

첫 구현 순서:

1. FastAPI embedding provider interface를 만든다.
2. deterministic provider를 interface 뒤로 이동한다.
3. local FastEmbed provider를 추가한다.
4. response에 `provider`를 추가한다.
5. Spring Boot `EmbeddingResponse`를 갱신한다.
6. Qdrant projection rebuild에서 `dimension`, `modelName`, `provider`를 검증하고 기록한다.
