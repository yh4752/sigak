from app.config import EmbeddingSettings, load_embedding_settings
from app.services.embedding_provider import get_embedding_provider
from app.services.local_fastembed_embedding_service import (
    LocalFastEmbedEmbeddingProvider,
)


class FakeFastEmbedModel:
    def embed(self, documents: list[str]):
        assert documents == ["Graph RAG"]
        return iter([[0.1, -0.2, 0.3]])


def test_default_embedding_settings_use_lightweight_multilingual_model(monkeypatch):
    monkeypatch.delenv("SIGAK_EMBEDDING_PROVIDER", raising=False)
    monkeypatch.delenv("SIGAK_EMBEDDING_MODEL_NAME", raising=False)

    settings = load_embedding_settings()

    assert settings.embedding_provider == "local"
    assert settings.embedding_model_name == "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"


def test_selects_deterministic_embedding_provider_from_settings():
    provider = get_embedding_provider(
        EmbeddingSettings(
            embedding_provider="deterministic",
            embedding_model_name="sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
        )
    )

    assert provider.provider_name == "deterministic"
    assert provider.model_name == "sigak-deterministic-hash-v1"
    assert provider.dimension == 8


def test_local_fastembed_provider_wraps_configured_model():
    provider = LocalFastEmbedEmbeddingProvider(
        model_name="sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
        model_loader=lambda model_name: FakeFastEmbedModel(),
    )

    embedding = provider.embed_text("Graph RAG")

    assert provider.provider_name == "local"
    assert provider.model_name == "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
    assert provider.dimension == 3
    assert embedding == [0.1, -0.2, 0.3]
