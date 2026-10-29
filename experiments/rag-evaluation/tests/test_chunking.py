from rag_evaluation.chunking import (
    FixedSizeChunker,
    HeadingAwareChunker,
    assign_evidence_to_chunks,
)
from rag_evaluation.models import (
    BlockKind,
    DocumentBlock,
    EvidenceSpan,
    NormalizedDocument,
)
from rag_evaluation.tokenization import ChunkTokenCodec


def fixture_document() -> NormalizedDocument:
    first = "A " * 30
    second = "If vector search fails, use keyword-only mode."
    normalized = f"{first.strip()}\n\n{second}"
    return NormalizedDocument(
        documentPath="manual.md",
        sha256="a" * 64,
        includedSections=["*"],
        normalizedText=normalized,
        blocks=[
            DocumentBlock(
                kind=BlockKind.PARAGRAPH,
                headingPath=["Manual", "Overview"],
                content=first.strip(),
                normalizedStart=0,
                normalizedEnd=len(first.strip()),
            ),
            DocumentBlock(
                kind=BlockKind.PARAGRAPH,
                headingPath=["Manual", "Fallback"],
                content=second,
                normalizedStart=len(first.strip()) + 2,
                normalizedEnd=len(normalized),
            ),
        ],
    )


def test_fixed_chunker_uses_overlap_and_stable_ids():
    codec = ChunkTokenCodec("o200k_base")
    chunker = FixedSizeChunker(codec=codec, max_tokens=20, overlap_tokens=5)

    first = chunker.chunk([fixture_document()])
    second = chunker.chunk([fixture_document()])

    assert len(first) > 1
    assert [chunk.chunk_id for chunk in first] == [chunk.chunk_id for chunk in second]
    assert all(chunk.token_count <= 20 for chunk in first)


def test_heading_chunker_preserves_fallback_heading():
    codec = ChunkTokenCodec("o200k_base")
    chunker = HeadingAwareChunker(codec=codec, max_tokens=40, overlap_tokens=5)

    chunks = chunker.chunk([fixture_document()])

    fallback = next(chunk for chunk in chunks if "keyword-only" in chunk.content)
    assert fallback.heading_path == ["Manual", "Fallback"]
    assert fallback.token_count <= 40


def test_evidence_mapping_is_independent_of_chunk_id():
    codec = ChunkTokenCodec("o200k_base")
    chunks = HeadingAwareChunker(codec=codec, max_tokens=40, overlap_tokens=5).chunk(
        [fixture_document()]
    )
    evidence = EvidenceSpan(
        evidenceId="fallback",
        documentPath="manual.md",
        headingPath=["Manual", "Fallback"],
        quote="If vector search fails, use keyword-only mode.",
        quoteSha256="b" * 64,
    )

    mapped = assign_evidence_to_chunks(chunks, [evidence])

    assert mapped["fallback"]
    assert mapped["fallback"][0].grade == 2


def test_both_chunkers_share_the_same_max_token_budget():
    codec = ChunkTokenCodec("o200k_base")
    document = fixture_document()

    fixed = FixedSizeChunker(codec=codec, max_tokens=20, overlap_tokens=5).chunk([document])
    heading = HeadingAwareChunker(codec=codec, max_tokens=20, overlap_tokens=5).chunk(
        [document]
    )

    assert max(chunk.token_count for chunk in fixed) <= 20
    assert max(chunk.token_count for chunk in heading) <= 20


def test_no_automatic_supporting_grade_from_partial_sentence():
    chunks = HeadingAwareChunker(codec=ChunkTokenCodec("o200k_base"), max_tokens=40, overlap_tokens=5).chunk([fixture_document()])
    evidence = EvidenceSpan(evidenceId="partial", documentPath="manual.md",
        headingPath=["Manual", "Fallback"], quote="If vector search fails, use keyword-only mode. Unverified extra claim.",
        quoteSha256="b" * 64)
    assert not assign_evidence_to_chunks(chunks, [evidence]).get("partial")


def test_chunk_windows_do_not_corrupt_korean_unicode():
    from rag_evaluation.corpus import parse_markdown_document
    document = parse_markdown_document(document_path="ko.md", text="# 한국어\n\n" + "검색 장애 복구 절차를 확인하세요. " * 50, included_sections=["*"])
    codec = ChunkTokenCodec("o200k_base")
    for chunker in (FixedSizeChunker, HeadingAwareChunker):
        chunks = chunker(codec=codec, max_tokens=13, overlap_tokens=3).chunk([document])
        assert all("\ufffd" not in chunk.content for chunk in chunks)
        assert all(chunk.token_count == len(codec.encode(chunk.content)) <= 13 for chunk in chunks)


def test_chunk_windows_preserve_multibyte_token_boundaries():
    from rag_evaluation.corpus import parse_markdown_document
    document = parse_markdown_document(document_path="unicode.md", text="# 문자\n\n" + "뷁쀍힣🍎🚀 " * 40, included_sections=["*"])
    codec = ChunkTokenCodec("o200k_base")
    for chunker in (FixedSizeChunker, HeadingAwareChunker):
        chunks = chunker(codec=codec, max_tokens=17, overlap_tokens=4).chunk([document])
        assert all("\ufffd" not in chunk.content for chunk in chunks)
        assert all(chunk.content in document.normalized_text for chunk in chunks)
        assert all(chunk.token_count == len(codec.encode(chunk.content)) <= 17 for chunk in chunks)


def test_chunker_rejects_overlap_equal_to_budget():
    import pytest
    for chunker in (FixedSizeChunker, HeadingAwareChunker):
        with pytest.raises(ValueError, match="overlap"):
            chunker(codec=ChunkTokenCodec("o200k_base"), max_tokens=5, overlap_tokens=5)


def test_heading_chunker_keeps_explanation_with_code_when_pair_fits_budget():
    from rag_evaluation.corpus import parse_markdown_document
    document = parse_markdown_document(document_path="manual.md", included_sections=["*"],
        text="# Manual\n\n## Procedure\n\n" + "Earlier context. " * 15 + "\n\nRun this command:\n\n```bash\ndocker compose up -d postgres\n```\n")
    chunks = HeadingAwareChunker(codec=ChunkTokenCodec("o200k_base"), max_tokens=55, overlap_tokens=5).chunk([document])
    command = next(chunk for chunk in chunks if "docker compose" in chunk.content)
    assert "Run this command:" in command.content


def test_evidence_match_requires_correct_heading_as_well_as_quote():
    chunks = HeadingAwareChunker(codec=ChunkTokenCodec("o200k_base"), max_tokens=40, overlap_tokens=5).chunk([fixture_document()])
    evidence = EvidenceSpan(evidenceId="wrong-heading", documentPath="manual.md",
        headingPath=["Manual", "Other"], quote="If vector search fails, use keyword-only mode.", quoteSha256="b" * 64)
    assert not assign_evidence_to_chunks(chunks, [evidence]).get("wrong-heading")


def test_fixed_window_cannot_borrow_quote_from_neighboring_section():
    from rag_evaluation.corpus import parse_markdown_document
    text = "# Manual\n\n## Alpha\n\nExpected evidence phrase.\n\n## Beta\n\nUnrelated material. " + "Padding. " * 40 + "\n\nExpected evidence phrase."
    document = parse_markdown_document(document_path="manual.md", text=text, included_sections=["*"])
    chunks = FixedSizeChunker(codec=ChunkTokenCodec("o200k_base"), max_tokens=30, overlap_tokens=5).chunk([document])
    evidence = EvidenceSpan(evidenceId="beta-only", documentPath="manual.md", headingPath=["Manual", "Beta"],
        quote="Expected evidence phrase.", quoteSha256="b" * 64)
    mapped = assign_evidence_to_chunks(chunks, [evidence])
    assert chunks[0].chunk_id not in {match.chunk_id for match in mapped["beta-only"]}
