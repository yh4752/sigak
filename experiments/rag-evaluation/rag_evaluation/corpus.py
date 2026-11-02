from __future__ import annotations

import hashlib
import re
from datetime import UTC, datetime
from pathlib import Path

from rag_evaluation.models import (
    BlockKind,
    CorpusManifest,
    CorpusSnapshot,
    DocumentBlock,
    NormalizedDocument,
)

HEADING = re.compile(r"^(#{1,6})\s+(.+?)\s*$")
LIST_ITEM = re.compile(r"^\s*(?:[-*+]|\d+\.)\s+")
FENCE = re.compile(r"^ {0,3}(`{3,}|~{3,})(.*)$")


def _normalize_prose(lines: list[str]) -> str:
    return " ".join(" ".join(lines).split())


def _section_is_allowed(heading_path: list[str], included_sections: list[str]) -> bool:
    if "*" in included_sections:
        return True
    return any(section in heading_path for section in included_sections)


def parse_markdown_document(
    *,
    document_path: str,
    text: str,
    included_sections: list[str],
) -> NormalizedDocument:
    source = text.replace("\r\n", "\n").replace("\r", "\n")
    lines = source.splitlines()
    heading_path: list[str] = []
    blocks: list[DocumentBlock] = []
    normalized_parts: list[str] = []
    paragraph_lines: list[str] = []
    list_lines: list[str] = []
    code_lines: list[str] = []
    in_code = False
    opening_fence = ""
    seen_headings: set[str] = set()

    def append_block(kind: BlockKind, content: str) -> None:
        if not content or not _section_is_allowed(heading_path, included_sections):
            return
        start = sum(len(part) + 2 for part in normalized_parts)
        normalized_parts.append(content)
        blocks.append(
            DocumentBlock(
                kind=kind,
                heading_path=list(heading_path),
                content=content,
                normalized_start=start,
                normalized_end=start + len(content),
            )
        )

    def flush_prose() -> None:
        nonlocal paragraph_lines, list_lines
        if paragraph_lines:
            append_block(BlockKind.PARAGRAPH, _normalize_prose(paragraph_lines))
            paragraph_lines = []
        if list_lines:
            append_block(BlockKind.LIST, "\n".join(line.rstrip() for line in list_lines))
            list_lines = []

    for line in lines:
        fence_match = FENCE.match(line)
        if fence_match:
            fence, suffix = fence_match.groups()
            if in_code and fence[0] == opening_fence[0] and len(fence) >= len(opening_fence) and not suffix.strip():
                append_block(BlockKind.CODE, "\n".join(code_lines) + "\n")
                code_lines = []
                in_code = False
                continue
            elif not in_code:
                flush_prose()
                in_code = True
                opening_fence = fence
                continue
        if in_code:
            code_lines.append(line)
            continue

        heading_match = HEADING.match(line)
        if heading_match:
            flush_prose()
            level = len(heading_match.group(1))
            title = heading_match.group(2).strip()
            seen_headings.add(title)
            heading_path = heading_path[: level - 1] + [title]
            continue
        if not line.strip():
            flush_prose()
            continue
        if LIST_ITEM.match(line):
            if paragraph_lines:
                flush_prose()
            list_lines.append(line)
            continue
        if list_lines:
            flush_prose()
        paragraph_lines.append(line)

    flush_prose()
    if in_code:
        raise ValueError(f"unclosed fenced code block: {document_path}")
    if "*" not in included_sections and set(included_sections) - seen_headings:
        raise ValueError(f"included section not found: {document_path}")

    normalized_text = "\n\n".join(normalized_parts)
    return NormalizedDocument(
        document_path=document_path,
        sha256=hashlib.sha256(source.encode("utf-8")).hexdigest(),
        included_sections=included_sections,
        normalized_text=normalized_text,
        blocks=blocks,
    )


def build_corpus_snapshot(
    *,
    repository_root: Path,
    manifest: CorpusManifest,
    git_commit: str,
    working_tree_dirty: bool,
) -> CorpusSnapshot:
    documents = []
    for spec in manifest.documents:
        path = (repository_root / spec.document_path).resolve()
        if Path(spec.document_path).is_absolute() or not path.is_relative_to(repository_root.resolve()):
            raise ValueError("corpus document is outside repository")
        documents.append(
            parse_markdown_document(
                document_path=spec.document_path,
                text=path.read_text(encoding="utf-8"),
                included_sections=spec.included_sections,
            )
        )
    return CorpusSnapshot(
        corpus_id=manifest.corpus_id,
        git_commit=git_commit,
        working_tree_dirty=working_tree_dirty,
        generated_at=datetime.now(UTC).isoformat(),
        documents=documents,
    )
