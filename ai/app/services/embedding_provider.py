from functools import lru_cache
from typing import Protocol

from app.config import EmbeddingSettings, load_embedding_settings
from app.services.deterministic_embedding_service import DeterministicEmbeddingProvider
from app.services.local_fastembed_embedding_service import (
    LocalFastEmbedEmbeddingProvider,
)


class EmbeddingProvider(Protocol):
    provider_name: str
    model_name: str
    dimension: int

    def embed_text(self, text: str) -> list[float]:
        ...


def get_embedding_provider(settings: EmbeddingSettings | None = None) -> EmbeddingProvider:
    embedding_settings = settings or load_embedding_settings()
    return _get_embedding_provider(
        embedding_settings.embedding_provider,
        embedding_settings.embedding_model_name,
    )


@lru_cache(maxsize=4)
def _get_embedding_provider(provider_name: str, model_name: str) -> EmbeddingProvider:
    if provider_name == "deterministic":
        return DeterministicEmbeddingProvider()
    if provider_name == "local":
        return LocalFastEmbedEmbeddingProvider(model_name=model_name)

    raise ValueError(f"Unsupported embedding provider: {provider_name}")
