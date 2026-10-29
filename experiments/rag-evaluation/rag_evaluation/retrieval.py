from __future__ import annotations

import math
from collections import Counter, defaultdict

import numpy as np

from rag_evaluation.models import Chunk, RankedChunk
from rag_evaluation.tokenization import TechnicalMixedTokenizer


class BM25Index:
    def __init__(
        self,
        *,
        chunks: list[Chunk],
        tokenizer: TechnicalMixedTokenizer,
        k1: float,
        b: float,
    ) -> None:
        self.chunks = chunks
        self.tokenizer = tokenizer
        self.k1 = k1
        self.b = b
        self.documents = [tokenizer.tokenize(chunk.content) for chunk in chunks]
        self.lengths = [len(document) for document in self.documents]
        self.average_length = sum(self.lengths) / len(self.lengths) if self.lengths else 0
        self.frequencies = [Counter(document) for document in self.documents]
        document_frequency = Counter()
        for document in self.documents:
            document_frequency.update(set(document))
        count = len(self.documents)
        self.idf = {
            token: math.log(1 + (count - frequency + 0.5) / (frequency + 0.5))
            for token, frequency in document_frequency.items()
        }

    def search(self, query: str, *, limit: int) -> list[RankedChunk]:
        query_tokens = self.tokenizer.tokenize(query)
        scored = []
        for chunk, frequencies, length in zip(
            self.chunks, self.frequencies, self.lengths, strict=True
        ):
            score = 0.0
            for token in query_tokens:
                frequency = frequencies[token]
                if not frequency:
                    continue
                denominator = frequency + self.k1 * (
                    1 - self.b + self.b * length / self.average_length
                )
                score += self.idf.get(token, 0.0) * (
                    frequency * (self.k1 + 1) / denominator
                )
            if score > 0:
                scored.append((chunk.chunk_id, score))
        ranked = sorted(scored, key=lambda row: (-row[1], row[0]))[:limit]
        return [
            RankedChunk(chunkId=chunk_id, score=score, rank=index + 1)
            for index, (chunk_id, score) in enumerate(ranked)
        ]


class DenseIndex:
    def __init__(self, *, chunks: list[Chunk], vectors: dict[str, list[float]]) -> None:
        self.chunks = chunks
        self.vectors = {key: self._normalize(vector) for key, vector in vectors.items()}

    @staticmethod
    def _normalize(vector: list[float]) -> np.ndarray:
        array = np.asarray(vector, dtype=float)
        if array.ndim != 1 or not len(array) or not np.isfinite(array).all():
            raise ValueError("dense vector must be finite and one-dimensional")
        norm = float(np.linalg.norm(array))
        if norm == 0 or not np.isfinite(norm):
            raise ValueError("dense vector norm must be finite and non-zero")
        return array / norm

    def search(self, query_vector: list[float], *, limit: int) -> list[RankedChunk]:
        query = self._normalize(query_vector)
        scored = [
            (chunk.chunk_id, float(np.dot(query, np.asarray(self.vectors[chunk.chunk_id]))))
            for chunk in self.chunks
        ]
        ranked = sorted(scored, key=lambda row: (-row[1], row[0]))[:limit]
        return [
            RankedChunk(chunkId=chunk_id, score=score, rank=index + 1)
            for index, (chunk_id, score) in enumerate(ranked)
        ]


def reciprocal_rank_fusion(
    ranked_lists: list[list[RankedChunk] | list[tuple[str, float]]],
    *,
    rrf_k: int,
    limit: int,
) -> list[tuple[str, float]]:
    scores: dict[str, float] = defaultdict(float)
    for ranked in ranked_lists:
        for index, item in enumerate(ranked):
            chunk_id = item.chunk_id if isinstance(item, RankedChunk) else item[0]
            scores[chunk_id] += 1.0 / (rrf_k + index + 1)
    return sorted(scores.items(), key=lambda row: (-row[1], row[0]))[:limit]
