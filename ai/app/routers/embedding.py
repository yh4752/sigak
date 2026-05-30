from fastapi import APIRouter

from app.schemas.embedding import EmbeddingRequest, EmbeddingResponse
from app.services.deterministic_embedding_service import build_embedding_response
from app.services.embedding_provider import get_embedding_provider


router = APIRouter(prefix="/api/embeddings", tags=["embeddings"])


@router.post("/text", response_model=EmbeddingResponse)
def embed_text_endpoint(request: EmbeddingRequest) -> EmbeddingResponse:
    return build_embedding_response(request, get_embedding_provider())
