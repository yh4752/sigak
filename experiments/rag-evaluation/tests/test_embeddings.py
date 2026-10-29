import json
from pathlib import Path

import httpx
import pytest

from rag_evaluation.embeddings import EmbeddingClient, EmbeddingCache


def test_embedding_client_validates_metadata_and_normalizes_vector(tmp_path):
    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/api/embeddings/text"
        return httpx.Response(
            200,
            json={
                "provider": "local",
                "modelName": "multilingual-model",
                "dimension": 2,
                "embedding": [3.0, 4.0],
            },
        )

    client = EmbeddingClient(
        base_url="http://test",
        expected_model="multilingual-model",
        http_client=httpx.Client(transport=httpx.MockTransport(handler)),
        cache=EmbeddingCache(tmp_path / "embeddings.json"),
    )

    result = client.embed("semantic query", cache_key="query:1")

    assert result.dimension == 2
    assert result.vector == pytest.approx([0.6, 0.8])


def test_embedding_client_reuses_cache_without_second_request(tmp_path):
    calls = 0

    def handler(_: httpx.Request) -> httpx.Response:
        nonlocal calls
        calls += 1
        return httpx.Response(
            200,
            json={
                "provider": "local",
                "modelName": "multilingual-model",
                "dimension": 2,
                "embedding": [1.0, 0.0],
            },
        )

    cache = EmbeddingCache(tmp_path / "embeddings.json")
    client = EmbeddingClient(
        base_url="http://test",
        expected_model="multilingual-model",
        http_client=httpx.Client(transport=httpx.MockTransport(handler)),
        cache=cache,
    )

    client.embed("same", cache_key="chunk:abc")
    client.embed("same", cache_key="chunk:abc")

    assert calls == 1


def test_embedding_client_rejects_dimension_mismatch(tmp_path):
    def handler(_: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "provider": "local",
                "modelName": "multilingual-model",
                "dimension": 3,
                "embedding": [1.0, 0.0],
            },
        )

    client = EmbeddingClient(
        base_url="http://test",
        expected_model="multilingual-model",
        http_client=httpx.Client(transport=httpx.MockTransport(handler)),
        cache=EmbeddingCache(tmp_path / "embeddings.json"),
    )

    with pytest.raises(ValueError, match="dimension"):
        client.embed("bad", cache_key="bad")


@pytest.mark.parametrize("vector", [[float("nan"), 1.0], [float("inf"), 1.0], [0.0, 0.0]])
def test_embedding_rejects_nonfinite_and_zero_vectors(tmp_path, vector):
    def handler(_):
        return httpx.Response(200, content=json.dumps({"provider": "local", "modelName": "model",
            "dimension": 2, "embedding": vector}), headers={"Content-Type": "application/json"})
    client = EmbeddingClient(base_url="http://test", expected_model="model",
        http_client=httpx.Client(transport=httpx.MockTransport(handler)),
        cache=EmbeddingCache(tmp_path / "embeddings.json"))
    with pytest.raises(ValueError, match="finite|non-zero"):
        client.embed("bad", cache_key="bad")


def test_cached_embedding_cannot_bypass_configured_model(tmp_path):
    from rag_evaluation.models import EmbeddingResult
    cache = EmbeddingCache(tmp_path / "embeddings.json")
    cache.put("same", EmbeddingResult(provider="local", modelName="old", dimension=2, vector=[1.0, 0.0]))
    client = EmbeddingClient(base_url="http://test", expected_model="new",
        http_client=httpx.Client(), cache=cache)
    with pytest.raises(ValueError, match="model"):
        client.embed("query", cache_key="same")
