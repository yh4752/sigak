from __future__ import annotations

import hashlib
from collections import defaultdict
from itertools import groupby

from rag_evaluation.models import Chunk, ChunkStrategy, EvidenceChunkMatch, EvidenceSpan, NormalizedDocument
from rag_evaluation.tokenization import ChunkTokenCodec


def _digest(value: str, length: int) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()[:length]


def _build_chunk(strategy, document_path, heading_paths, content, token_count, contains_code_block, source_sections=None):
    content_sha = _digest(content, 64)
    heading_path = heading_paths[0] if heading_paths else []
    return Chunk(
        chunkId=f"{strategy.value}:{_digest(document_path, 8)}:{_digest(' > '.join(heading_path), 8)}:{content_sha[:12]}",
        strategy=strategy, documentPath=document_path, headingPath=heading_path,
        coveredHeadingPaths=heading_paths, content=content, tokenCount=token_count,
        containsCodeBlock=contains_code_block, contentSha256=content_sha,
        sourceSections=source_sections or [{"headingPath": heading_path, "content": content}],
    )


def _unique_chunks(chunks):
    # 동일한 본문을 검색 후보로 중복 집계하지 않도록 같은 안정 ID는 한 번만 남긴다.
    return list({chunk.chunk_id: chunk for chunk in chunks}.values())


class FixedSizeChunker:
    def __init__(self, *, codec: ChunkTokenCodec, max_tokens: int, overlap_tokens: int):
        if max_tokens < 1 or not 0 <= overlap_tokens < max_tokens:
            raise ValueError("overlap must be smaller than max_tokens")
        self.codec = codec
        self.max_tokens = max_tokens
        self.overlap_tokens = overlap_tokens

    def chunk(self, documents: list[NormalizedDocument]) -> list[Chunk]:
        chunks = []
        for document in documents:
            for content, count, start, end in self.codec.windows(document.normalized_text, self.max_tokens, self.overlap_tokens):
                blocks = [block for block in document.blocks if block.normalized_start < end and block.normalized_end > start]
                headings = list(dict.fromkeys(tuple(block.heading_path) for block in blocks))
                sections = []
                for heading, group in groupby(blocks, key=lambda block: tuple(block.heading_path)):
                    section_blocks = list(group)
                    section_start = max(start, section_blocks[0].normalized_start)
                    section_end = min(end, section_blocks[-1].normalized_end)
                    sections.append({"headingPath": list(heading), "content": document.normalized_text[section_start:section_end]})
                chunks.append(_build_chunk(ChunkStrategy.FIXED, document.document_path,
                    [list(path) for path in headings], content, count, any(block.kind.value == "CODE" for block in blocks), sections))
        return _unique_chunks(chunks)


class HeadingAwareChunker(FixedSizeChunker):
    def chunk(self, documents: list[NormalizedDocument]) -> list[Chunk]:
        chunks = []
        for document in documents:
            for heading, group in groupby(document.blocks, key=lambda block: tuple(block.heading_path)):
                blocks = list(group)
                units = []
                index = 0
                while index < len(blocks):
                    block = blocks[index]
                    content = block.content
                    contains_code = block.kind.value == "CODE"
                    # 설명과 명령이 함께 들어갈 때는 앞 문단을 다음 코드 블록과 묶는다.
                    if index + 1 < len(blocks) and blocks[index + 1].kind.value == "CODE":
                        pair = content + "\n\n" + blocks[index + 1].content
                        if len(self.codec.encode(pair)) <= self.max_tokens:
                            content = pair
                            contains_code = True
                            index += 1
                    units.append((content, contains_code))
                    index += 1
                parts = []
                contains_code = False
                for content, has_code in units:
                    candidate = "\n\n".join([*parts, content])
                    if parts and len(self.codec.encode(candidate)) > self.max_tokens:
                        chunks.extend(self._split_group(document.document_path, list(heading), "\n\n".join(parts), contains_code))
                        parts = []
                        contains_code = False
                    parts.append(content)
                    contains_code |= has_code
                if parts:
                    chunks.extend(self._split_group(document.document_path, list(heading), "\n\n".join(parts), contains_code))
        return _unique_chunks(chunks)

    def _split_group(self, document_path, heading_path, content, contains_code_block):
        return [_build_chunk(ChunkStrategy.HEADING, document_path, [heading_path], text, count, contains_code_block)
            for text, count, _, _ in self.codec.windows(content, self.max_tokens, self.overlap_tokens)]


def assign_evidence_to_chunks(chunks: list[Chunk], evidence_spans: list[EvidenceSpan]) -> dict[str, list[EvidenceChunkMatch]]:
    matches = defaultdict(list)
    for evidence in evidence_spans:
        quote = " ".join(evidence.quote.split())
        for chunk in chunks:
            if chunk.document_path != evidence.document_path:
                continue
            # 보조 근거도 명시한 인용만 인정해 문장 일부로 정답 범위를 추측하지 않는다.
            sections = [(section.heading_path, section.content) for section in chunk.source_sections] or [(chunk.heading_path, chunk.content)]
            if quote and any(heading == evidence.heading_path and quote in " ".join(content.split()) for heading, content in sections):
                matches[evidence.evidence_id].append(EvidenceChunkMatch(evidenceId=evidence.evidence_id,
                    chunkId=chunk.chunk_id, grade=evidence.grade))
    return dict(matches)
