import pytest

from rag_evaluation.models import Chunk, ChunkStrategy, RetrievalSystem
from rag_evaluation.retrieval import (
    BM25Index,
    DenseIndex,
    reciprocal_rank_fusion,
)
from rag_evaluation.tokenization import TechnicalMixedTokenizer


def chunk(chunk_id: str, content: str) -> Chunk:
    return Chunk(
        chunkId=chunk_id,
        strategy=ChunkStrategy.HEADING,
        documentPath="manual.md",
        headingPath=["Manual"],
        content=content,
        tokenCount=10,
        containsCodeBlock=False,
        contentSha256=(chunk_id[-1] * 64),
    )


def test_bm25_ranks_exact_identifier_match_first():
    chunks = [
        chunk("heading:a:a:aaaaaaaaaaaa", "Qdrant vector projection rebuild"),
        chunk("heading:b:b:bbbbbbbbbbbb", "general search fallback"),
    ]
    index = BM25Index(
        chunks=chunks,
        tokenizer=TechnicalMixedTokenizer(),
        k1=1.5,
        b=0.75,
    )

    ranked = index.search("Qdrant rebuild", limit=2)

    assert ranked[0].chunk_id == chunks[0].chunk_id


def test_dense_ranks_highest_cosine_first():
    chunks = [
        chunk("heading:a:a:aaaaaaaaaaaa", "first"),
        chunk("heading:b:b:bbbbbbbbbbbb", "second"),
    ]
    index = DenseIndex(
        chunks=chunks,
        vectors={
            chunks[0].chunk_id: [1.0, 0.0],
            chunks[1].chunk_id: [0.0, 1.0],
        },
    )

    ranked = index.search([0.1, 0.9], limit=2)

    assert ranked[0].chunk_id == chunks[1].chunk_id


def test_rrf_combines_keyword_and_dense_rankings():
    keyword = [
        ("a", 3.0),
        ("b", 2.0),
    ]
    dense = [
        ("b", 0.9),
        ("c", 0.8),
    ]

    fused = reciprocal_rank_fusion([keyword, dense], rrf_k=60, limit=3)

    assert fused[0][0] == "b"
    assert {chunk_id for chunk_id, _ in fused} == {"a", "b", "c"}


def test_dense_sanity_fixture_prefers_paraphrase_over_hard_negative():
    chunks = [
        chunk("heading:a:a:aaaaaaaaaaaa", "keyword path remains available"),
        chunk("heading:b:b:bbbbbbbbbbbb", "reset the user password"),
    ]
    index = DenseIndex(
        chunks=chunks,
        vectors={
            chunks[0].chunk_id: [0.8, 0.2],
            chunks[1].chunk_id: [-0.2, 0.8],
        },
    )

    result = index.search([0.9, 0.1], limit=2)

    assert result[0].chunk_id == chunks[0].chunk_id


def test_dense_uses_cosine_even_when_inputs_are_not_normalized():
    chunks = [chunk("a", "match"), chunk("b", "large magnitude distractor")]
    index = DenseIndex(chunks=chunks, vectors={"a": [1.0, 0.0], "b": [100.0, 100.0]})
    ranked = index.search([4.0, 0.0], limit=2)
    assert ranked[0].chunk_id == "a"
    assert ranked[0].score == pytest.approx(1.0)


def test_bm25_does_not_return_zero_match_documents():
    index = BM25Index(chunks=[chunk("a", "PostgreSQL")], tokenizer=TechnicalMixedTokenizer(), k1=1.5, b=0.75)
    assert index.search("unrelated", limit=10) == []
