from fastapi import APIRouter

from app.schemas.embedding import EmbeddingRequest, EmbeddingResponse
from app.services.deterministic_embedding_service import embed_text


router = APIRouter(prefix="/api/embeddings", tags=["embeddings"])


@router.post("/text", response_model=EmbeddingResponse)
def embed_text_endpoint(request: EmbeddingRequest) -> EmbeddingResponse:
    return embed_text(request)
