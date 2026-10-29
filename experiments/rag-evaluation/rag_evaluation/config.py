from __future__ import annotations

import os
from dataclasses import dataclass, field
from decimal import Decimal


def _optional_decimal(name: str) -> Decimal | None:
    value = os.getenv(name, "").strip()
    return Decimal(value) if value else None


@dataclass(frozen=True)
class EvaluationSettings:
    ai_server_url: str
    embedding_model: str
    reranker_model: str | None
    generator_model: str | None
    judge_model: str | None
    openai_api_key: str | None = field(repr=False)
    chunk_tokenizer: str = "o200k_base"
    max_chunk_tokens: int = 500
    overlap_tokens: int = 100
    top_k: int = 5
    candidate_limit: int = 10
    rrf_k: int = 60
    bm25_k1: float = 1.5
    bm25_b: float = 0.75
    bootstrap_seed: int = 20260620
    bootstrap_resamples: int = 10_000
    input_price_per_million_usd: Decimal | None = None
    output_price_per_million_usd: Decimal | None = None

    @classmethod
    def from_env(cls) -> "EvaluationSettings":
        return cls(
            ai_server_url=os.getenv("SIGAK_AI_SERVER_URL", "http://localhost:8000").rstrip("/"),
            embedding_model=os.getenv(
                "SIGAK_RAG_EMBEDDING_MODEL",
                "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
            ),
            reranker_model=os.getenv("SIGAK_RAG_RERANKER_MODEL") or None,
            generator_model=os.getenv("SIGAK_RAG_GENERATOR_MODEL") or None,
            judge_model=os.getenv("SIGAK_RAG_JUDGE_MODEL") or None,
            openai_api_key=os.getenv("OPENAI_API_KEY") or None,
            input_price_per_million_usd=_optional_decimal(
                "SIGAK_RAG_INPUT_PRICE_PER_MILLION_USD"
            ),
            output_price_per_million_usd=_optional_decimal(
                "SIGAK_RAG_OUTPUT_PRICE_PER_MILLION_USD"
            ),
        )

    def validate_full_run(self) -> None:
        if not self.openai_api_key:
            raise ValueError("OPENAI_API_KEY is required for a full run")
        if not self.reranker_model or not self.generator_model or not self.judge_model:
            raise ValueError("reranker, generator, and judge models are required")
        if self.generator_model == self.judge_model:
            raise ValueError("generator and judge models must differ for a full run")
