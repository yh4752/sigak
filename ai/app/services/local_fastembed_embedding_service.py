from collections.abc import Callable
from typing import Any


class LocalFastEmbedEmbeddingProvider:
    provider_name = "local"

    def __init__(
        self,
        model_name: str,
        model_loader: Callable[[str], Any] | None = None,
    ) -> None:
        self.model_name = model_name
        self._model_loader = model_loader or self._load_fastembed_model
        self._model = None
        self._dimension: int | None = None

    @property
    def dimension(self) -> int:
        if self._dimension is None:
            self.embed_text("Sigak embedding dimension probe")
        return self._dimension or 0

    def embed_text(self, text: str) -> list[float]:
        embedding = next(self._load_model().embed([text]))
        values = [float(value) for value in embedding]
        self._dimension = len(values)
        return values

    def _load_model(self):
        if self._model is None:
            # FastEmbed는 ONNX Runtime 기반이라 PyTorch/CUDA 의존성 없이 local embedding을 제공한다.
            self._model = self._model_loader(self.model_name)
        return self._model

    @staticmethod
    def _load_fastembed_model(model_name: str):
        try:
            from fastembed import TextEmbedding
        except ImportError as exception:
            raise RuntimeError(
                "fastembed is required when SIGAK_EMBEDDING_PROVIDER=local."
            ) from exception

        return TextEmbedding(model_name=model_name)
