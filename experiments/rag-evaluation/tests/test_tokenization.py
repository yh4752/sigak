from rag_evaluation.tokenization import (
    ChunkTokenCodec,
    TechnicalMixedTokenizer,
    lexical_overlap,
)


def test_technical_tokenizer_preserves_identifiers_and_splits_hangul():
    tokenizer = TechnicalMixedTokenizer()

    tokens = tokenizer.tokenize(
        "POST /api/articles 호출 후 SIGAK_SEARCH_MODE=KEYWORD_ONLY로 전환한다."
    )

    assert "post" in tokens
    assert "/api/articles" in tokens
    assert "sigak_search_mode" in tokens
    assert "keyword_only" in tokens
    assert any(token.startswith("전환") for token in tokens)


def test_lexical_overlap_is_lower_for_semantic_paraphrase():
    tokenizer = TechnicalMixedTokenizer()
    evidence = "If one projection path fails, use KEYWORD_ONLY or VECTOR_ONLY."
    lexical = "projection path 실패 시 KEYWORD_ONLY 모드는?"
    semantic = "의미 검색 저장소가 멈춰도 다른 방식으로 검색을 계속하는가?"

    assert lexical_overlap(lexical, evidence, tokenizer) > lexical_overlap(
        semantic, evidence, tokenizer
    )


def test_chunk_codec_round_trips_text():
    codec = ChunkTokenCodec("o200k_base")
    text = "Qdrant 장애 대응과 PostgreSQL fallback"

    assert codec.decode(codec.encode(text)) == text
