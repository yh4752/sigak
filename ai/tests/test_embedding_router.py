import os

os.environ["SIGAK_EMBEDDING_PROVIDER"] = "deterministic"

from fastapi.testclient import TestClient

from app.main import app


client = TestClient(app)


def test_embed_text_returns_deterministic_vector():
    request = {"text": "Graph RAG improves relationship-aware retrieval."}

    first_response = client.post("/api/embeddings/text", json=request)
    second_response = client.post("/api/embeddings/text", json=request)

    assert first_response.status_code == 200
    assert second_response.status_code == 200
    assert first_response.json() == second_response.json()
    assert first_response.json()["provider"] == "deterministic"
    assert first_response.json()["modelName"] == "sigak-deterministic-hash-v1"
    assert first_response.json()["dimension"] == 8
    assert len(first_response.json()["embedding"]) == 8


def test_embed_text_rejects_whitespace_only_text():
    response = client.post("/api/embeddings/text", json={"text": "   "})

    assert response.status_code == 422
