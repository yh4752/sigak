from __future__ import annotations

import re
from functools import cached_property

import tiktoken
from kiwipiepy import Kiwi

TECHNICAL = re.compile(
    r"https?://\S+|/[A-Za-z0-9_{}?&=./:-]+|[A-Z][A-Z0-9_]{2,}|"
    r"[A-Za-z][A-Za-z0-9_.:-]*|[가-힣]+|\d+(?:\.\d+)*"
)
HANGUL = re.compile(r"^[가-힣]+$")


class ChunkTokenCodec:
    def __init__(self, encoding_name: str) -> None:
        self.encoding_name = encoding_name
        self._encoding = tiktoken.get_encoding(encoding_name)

    def encode(self, text: str) -> list[int]:
        return self._encoding.encode(text, disallowed_special=())

    def decode(self, tokens: list[int]) -> str:
        return self._encoding.decode(tokens)

    def windows(self, text: str, max_tokens: int, overlap_tokens: int):
        if max_tokens < 1 or not 0 <= overlap_tokens < max_tokens:
            raise ValueError("overlap must be nonnegative and smaller than max_tokens")
        tokens = self.encode(text)
        raw = text.encode("utf-8")
        offsets = [0]
        for token in tokens:
            offsets.append(offsets[-1] + len(self._encoding.decode_single_token_bytes(token)))
        # 토큰 하나가 UTF-8 문자 일부일 수 있어 문자 경계에서만 잘라 원문을 보존한다.
        boundaries = {i for i, offset in enumerate(offsets) if offset == len(raw) or raw[offset] & 0xC0 != 0x80}
        start = 0
        while start < len(tokens):
            end = min(start + max_tokens, len(tokens))
            while end > start:
                if end in boundaries:
                    content = raw[offsets[start]:offsets[end]].decode("utf-8").strip()
                    count = len(self.encode(content))
                    if count <= max_tokens:
                        break
                end -= 1
            if end == start:
                raise ValueError("chunk budget is too small for a Unicode character")
            if content:
                yield content, count, len(raw[:offsets[start]].decode("utf-8")), len(raw[:offsets[end]].decode("utf-8"))
            if end == len(tokens):
                break
            next_start = max(start + 1, end - overlap_tokens)
            while next_start not in boundaries:
                next_start += 1
            start = next_start


class TechnicalMixedTokenizer:
    @cached_property
    def kiwi(self) -> Kiwi:
        return Kiwi()

    def tokenize(self, text: str) -> list[str]:
        tokens: list[str] = []
        for match in TECHNICAL.finditer(text):
            value = match.group(0)
            if HANGUL.match(value):
                tokens.extend(
                    token.form.lower()
                    for token in self.kiwi.tokenize(value)
                    if token.tag[0] in {"N", "V", "M", "S"}
                )
            else:
                raw = value.strip(".,;:()[]{}\"'")
                if "=" in raw:
                    name, assigned_value = raw.split("=", 1)
                    if name:
                        tokens.append(name.lower())
                    if assigned_value:
                        tokens.append(assigned_value.lower())
                elif raw:
                    tokens.append(raw.lower())
        return tokens


def lexical_overlap(
    query: str,
    evidence: str,
    tokenizer: TechnicalMixedTokenizer,
) -> float:
    query_tokens = set(tokenizer.tokenize(query))
    evidence_tokens = set(tokenizer.tokenize(evidence))
    if not query_tokens:
        return 0.0
    return len(query_tokens & evidence_tokens) / len(query_tokens)
