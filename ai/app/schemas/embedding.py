from pydantic import BaseModel

from app.schemas.enrichment import RequiredText


class EmbeddingRequest(BaseModel):
    text: RequiredText


class EmbeddingResponse(BaseModel):
    modelName: str
    dimension: int
    embedding: list[float]
