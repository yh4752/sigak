from pathlib import Path

from rag_evaluation.corpus import (
    build_corpus_snapshot,
    parse_markdown_document,
)
from rag_evaluation.models import BlockKind, CorpusManifest


FIXTURE_ROOT = Path(__file__).parent / "fixtures" / "corpus"


def test_parse_markdown_preserves_heading_path_and_code_whitespace():
    document = parse_markdown_document(
        document_path="manual-a.md",
        text=(FIXTURE_ROOT / "manual-a.md").read_text(encoding="utf-8"),
        included_sections=["*"],
    )

    startup_blocks = [
        block for block in document.blocks if block.heading_path == ["Manual A", "Startup"]
    ]

    assert startup_blocks[0].kind is BlockKind.PARAGRAPH
    assert startup_blocks[1].kind is BlockKind.CODE
    assert startup_blocks[1].content == "docker compose up -d postgres\n"


def test_build_snapshot_records_git_and_content_hashes(tmp_path):
    manifest = CorpusManifest.model_validate(
        {
            "corpusId": "fixture-corpus",
            "documents": [
                {"documentPath": "manual-a.md", "includedSections": ["Startup"]},
                {"documentPath": "manual-b.md", "includedSections": ["*"]},
            ],
        }
    )

    snapshot = build_corpus_snapshot(
        repository_root=FIXTURE_ROOT,
        manifest=manifest,
        git_commit="abc123",
        working_tree_dirty=True,
    )

    assert snapshot.corpus_id == "fixture-corpus"
    assert snapshot.git_commit == "abc123"
    assert snapshot.working_tree_dirty is True
    assert len(snapshot.documents) == 2
    assert all(len(document.sha256) == 64 for document in snapshot.documents)


def test_manifest_cannot_read_outside_repository(tmp_path):
    import pytest
    (tmp_path / "repo").mkdir()
    (tmp_path / "private.md").write_text("private")
    manifest = CorpusManifest.model_validate({"corpusId": "test", "documents": [
        {"documentPath": "../private.md", "includedSections": ["*"]}
    ]})
    with pytest.raises(ValueError, match="outside repository"):
        build_corpus_snapshot(repository_root=tmp_path / "repo", manifest=manifest,
                              git_commit="test", working_tree_dirty=False)


def test_parser_supports_tilde_fences_and_ignores_headings_inside_code():
    document = parse_markdown_document(document_path="manual.md",
        text="# Manual\n\n## Setup\n\n~~~bash\n  # preserved\n  command\n~~~\n",
        included_sections=["Setup"])
    assert len(document.blocks) == 1
    assert document.blocks[0].kind is BlockKind.CODE
    assert document.blocks[0].content == "  # preserved\n  command\n"


def test_parser_rejects_allowlisted_section_that_no_longer_exists():
    import pytest
    with pytest.raises(ValueError, match="included section"):
        parse_markdown_document(document_path="manual.md", text="# Manual\n\nbody",
                                included_sections=["Missing"])
