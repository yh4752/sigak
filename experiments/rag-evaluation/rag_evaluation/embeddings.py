from __future__ import annotations

import json
from pathlib import Path

import httpx
import numpy as np

from rag_evaluation.models import EmbeddingResult


class EmbeddingCache:
    def __init__(self, path: Path) -> None:
        self.path = path
        self._values = (
            json.loads(path.read_text(encoding="utf-8"))
            if path.exists()
            else {}
        )

    def get(self, key: str) -> EmbeddingResult | None:
        value = self._values.get(key)
        return EmbeddingResult.model_validate(value) if value else None

    def put(self, key: str, value: EmbeddingResult) -> None:
        self._values[key] = value.model_dump(by_alias=True)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.path.write_text(
            json.dumps(self._values, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )


class EmbeddingClient:
    def __init__(
        self,
        *,
        base_url: str,
        expected_model: str,
        http_client: httpx.Client,
        cache: EmbeddingCache,
    ) -> None:
        self.base_url = base_url.rstrip("/")
        self.expected_model = expected_model
        self.http_client = http_client
        self.cache = cache

    def embed(self, text: str, *, cache_key: str) -> EmbeddingResult:
        cached = self.cache.get(cache_key)
        if cached:
            self._validate(cached.model_name, cached.dimension, cached.vector)
            return cached
        response = self.http_client.post(
            f"{self.base_url}/api/embeddings/text",
            json={"text": text},
            timeout=60.0,
        )
        response.raise_for_status()
        payload = response.json()
        vector = payload["embedding"]
        dimension = payload["dimension"]
        self._validate(payload["modelName"], dimension, vector)
        array = np.asarray(vector, dtype=float)
        norm = float(np.linalg.norm(array))
        result = EmbeddingResult(
            provider=payload["provider"],
            modelName=payload["modelName"],
            dimension=dimension,
            vector=(array / norm).tolist(),
        )
        self.cache.put(cache_key, result)
        return result

    def _validate(self, model: str, dimension: int, vector: list[float]) -> None:
        array = np.asarray(vector, dtype=float)
        if dimension < 1 or array.ndim != 1 or len(vector) != dimension:
            raise ValueError("embedding dimension does not match vector length")
        if model != self.expected_model:
            raise ValueError("embedding model does not match configured model")
        norm = float(np.linalg.norm(array))
        if not np.isfinite(array).all() or not np.isfinite(norm):
            raise ValueError("embedding vector must be finite")
        if norm == 0:
            raise ValueError("embedding vector norm must be non-zero")
