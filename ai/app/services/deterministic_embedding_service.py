import hashlib
import math

from app.schemas.embedding import EmbeddingRequest, EmbeddingResponse


MODEL_NAME = "sigak-deterministic-hash-v1"
DIMENSION = 8


def embed_text(request: EmbeddingRequest) -> EmbeddingResponse:
    normalized_text = request.text.strip().lower()
    digest = hashlib.sha256(normalized_text.encode("utf-8")).digest()
    raw_vector = [
        int.from_bytes(digest[index * 4 : (index + 1) * 4], "big") / 0xFFFFFFFF
        for index in range(DIMENSION)
    ]
    centered_vector = [(value * 2.0) - 1.0 for value in raw_vector]
    magnitude = math.sqrt(sum(value * value for value in centered_vector)) or 1.0

    # 외부 embedding 모델 없이 Qdrant 연동 경계를 검증하기 위한 결정론적 local vector다.
    embedding = [round(value / magnitude, 6) for value in centered_vector]

    return EmbeddingResponse(
        modelName=MODEL_NAME,
        dimension=DIMENSION,
        embedding=embedding,
    )
