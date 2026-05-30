import hashlib
import math

from app.schemas.embedding import EmbeddingRequest, EmbeddingResponse


MODEL_NAME = "sigak-deterministic-hash-v1"
DIMENSION = 8


class DeterministicEmbeddingProvider:
    provider_name = "deterministic"
    model_name = MODEL_NAME
    dimension = DIMENSION

    def embed_text(self, text: str) -> list[float]:
        normalized_text = text.strip().lower()
        digest = hashlib.sha256(normalized_text.encode("utf-8")).digest()
        raw_vector = [
            int.from_bytes(digest[index * 4 : (index + 1) * 4], "big") / 0xFFFFFFFF
            for index in range(self.dimension)
        ]
        centered_vector = [(value * 2.0) - 1.0 for value in raw_vector]
        magnitude = math.sqrt(sum(value * value for value in centered_vector)) or 1.0

        # 외부 embedding 모델 없이 Qdrant 연동 경계를 검증하기 위한 결정론적 local vector다.
        return [round(value / magnitude, 6) for value in centered_vector]


def embed_text(request: EmbeddingRequest) -> EmbeddingResponse:
    provider = DeterministicEmbeddingProvider()
    embedding = provider.embed_text(request.text)

    return EmbeddingResponse(
        provider=provider.provider_name,
        modelName=provider.model_name,
        dimension=provider.dimension,
        embedding=embedding,
    )


def build_embedding_response(request: EmbeddingRequest, provider) -> EmbeddingResponse:
    embedding = provider.embed_text(request.text)

    return EmbeddingResponse(
        provider=provider.provider_name,
        modelName=provider.model_name,
        dimension=provider.dimension,
        embedding=embedding,
    )
