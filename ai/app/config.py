import os
from dataclasses import dataclass


@dataclass(frozen=True)
class EmbeddingSettings:
    embedding_provider: str = "local"
    embedding_model_name: str = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"


def load_embedding_settings() -> EmbeddingSettings:
    return EmbeddingSettings(
        embedding_provider=os.getenv("SIGAK_EMBEDDING_PROVIDER", "local").strip().lower(),
        embedding_model_name=os.getenv(
            "SIGAK_EMBEDDING_MODEL_NAME",
            "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
        ).strip(),
    )
