# Ops RAG Evaluation Harness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a reproducible operations-document RAG evaluation harness that compares two chunking strategies, four retrieval variants, five answer-generation variants, deterministic grounding checks, a calibrated LLM judge, and human review over a frozen 60-query dataset.

**Architecture:** Implement an isolated Python package under `experiments/rag-evaluation/`; it reads only allowlisted public repository documents and does not change Spring Boot, React, Elasticsearch, Qdrant, or Neo4j product contracts. The runner freezes corpus and dataset snapshots before retrieval, evaluates the complete factorial matrix, records every failure instead of silently dropping rows, and writes JSON plus Markdown artifacts under `results/latest/`.

**Tech Stack:** Python 3.12-3.14, Pydantic 2, pytest, httpx, tiktoken, Kiwi (`kiwipiepy`), NumPy, OpenAI Python SDK Responses API Structured Outputs, FastAPI embedding endpoint.

---

## Reference Spec

Read before implementation:

- `docs/superpowers/specs/2026-06-20-ops-rag-evaluation-harness-design.md`
- `AGENTS.md`
- `docs/STATUS.md`
- `docs/ROADMAP.md`
- `docs/CODING_CONVENTIONS.md`
- `docs/blog/WRITING_GUIDE.ko.md`

Current OpenAI contract reference:

- [Structured Outputs guide](https://developers.openai.com/api/docs/guides/structured-outputs)
  - Use `client.responses.parse` with `text_format=PydanticModel`.
  - Read the parsed object from `response.output_parsed`.
  - Detect API-level refusals separately from schema-valid task refusals.

## Scope And Execution Checkpoints

This plan is one dependent vertical slice, but execution is divided into three checkpoints:

1. **Checkpoint A — deterministic evidence path**
   - package, schemas, corpus snapshot, chunking, 60-query dataset, BM25/dense/hybrid, metrics
   - no OpenAI key required
2. **Checkpoint B — model-assisted evaluation**
   - reranker, generator, judge, deterministic citation checks
   - OpenAI key required only for live runs; tests use fakes
3. **Checkpoint C — portfolio evidence**
   - full run, 50 human reviews, report, README/status/dev-log, self-introduction evidence notes

Do not begin Checkpoint B until the 60-query dataset validation passes. Do not claim quality results until Checkpoint C finishes.

## Fixed Implementation Decisions

- Reuse `SIGAK_AI_SERVER_URL` for the existing FastAPI embedding boundary; do not add a second AI base URL variable.
- Use `o200k_base` as the chunk-budget tokenizer for both chunkers.
- Use Kiwi only for BM25 and lexical-overlap tokenization.
- Use an in-package BM25 implementation to keep parameters and scoring explicit.
- Use the existing FastAPI `POST /api/embeddings/text` endpoint for dense vectors.
- Use cosine similarity over normalized vectors.
- Use RRF with `k=60` and candidate limit `10`.
- Use OpenAI Responses API Structured Outputs for reranking, generation, and judging.
- Keep model IDs environment-configured. The safe example configuration is:
  - reranker: `gpt-5.4-mini`
  - generator: `gpt-5.4`
  - judge: `gpt-5.5`
- Require generator and judge model IDs to differ in a full run.
- Keep API pricing outside source code. Optional input/output prices belong in the run configuration.
- Commit the final reviewed dataset and final sanitized artifacts; do not commit `.venv`, embedding cache, API keys, request headers, or raw environment dumps.

## File Structure

Create:

```text
experiments/rag-evaluation/
├── README.md
├── pyproject.toml
├── datasets/
│   ├── corpus-manifest.json
│   ├── ops-questions.json
│   ├── smoke-questions.json
│   └── human-review.json
├── prompts/
│   ├── reranker-v1.md
│   ├── generator-v1.md
│   └── judge-v1.md
├── rag_evaluation/
│   ├── __init__.py
│   ├── __main__.py
│   ├── artifacts.py
│   ├── chunking.py
│   ├── cli.py
│   ├── config.py
│   ├── corpus.py
│   ├── dataset.py
│   ├── embeddings.py
│   ├── generation.py
│   ├── judging.py
│   ├── metrics.py
│   ├── models.py
│   ├── openai_client.py
│   ├── report.py
│   ├── reranking.py
│   ├── retrieval.py
│   ├── runner.py
│   └── tokenization.py
├── tests/
│   ├── fixtures/
│   │   ├── corpus/
│   │   │   ├── manual-a.md
│   │   │   └── manual-b.md
│   │   ├── embedding-response.json
│   │   └── openai-responses.json
│   ├── test_artifacts.py
│   ├── test_chunking.py
│   ├── test_cli.py
│   ├── test_config.py
│   ├── test_corpus.py
│   ├── test_dataset.py
│   ├── test_embeddings.py
│   ├── test_generation.py
│   ├── test_judging.py
│   ├── test_metrics.py
│   ├── test_reranking.py
│   ├── test_report.py
│   ├── test_retrieval.py
│   ├── test_runner.py
│   └── test_tokenization.py
└── results/
    └── latest/
        ├── run-manifest.json
        ├── corpus.snapshot.json
        ├── dataset.snapshot.json
        ├── chunks.fixed.json
        ├── chunks.heading.json
        ├── retrieval.runs.json
        ├── retrieval.metrics.json
        ├── generation.runs.json
        ├── generation.metrics.json
        ├── judge.runs.json
        ├── human-review.json
        ├── failures.json
        └── report.md
```

Modify:

- `.env.example`
  - add empty `OPENAI_API_KEY` and RAG model/config names
- `README.md`
  - add final Ops RAG evaluation command and honest result summary after verification
- `experiments/README.md`
  - link the new harness and distinguish it from article-search smoke evaluation
- `docs/STATUS.md`, `docs/STATUS.ko.md`
- `docs/ROADMAP.md`, `docs/ROADMAP.ko.md`
- `docs/blog/2026-06-20-dev-log.md`
- `docs/blog/topic-queue.md`

Do not modify:

- Spring Boot public API
- frontend routes or components
- existing article-search benchmark artifacts
- Elasticsearch/Qdrant/Neo4j schemas
- `ai/` embedding implementation unless a real contract incompatibility is discovered

## Public CLI Contract

Primary command:

```bash
cd experiments/rag-evaluation
.venv/bin/python -m rag_evaluation run \
  --dataset datasets/ops-questions.json \
  --corpus-manifest datasets/corpus-manifest.json \
  --output results/latest
```

Deterministic checkpoint:

```bash
.venv/bin/python -m rag_evaluation evaluate-retrieval \
  --dataset datasets/ops-questions.json \
  --corpus-manifest datasets/corpus-manifest.json \
  --output results/latest
```

Report-only rebuild:

```bash
.venv/bin/python -m rag_evaluation build-report \
  --output results/latest
```

---

### Task 1: Bootstrap The Isolated Python Package And Settings

**Files:**

- Create: `experiments/rag-evaluation/pyproject.toml`
- Create: `experiments/rag-evaluation/rag_evaluation/__init__.py`
- Create: `experiments/rag-evaluation/rag_evaluation/__main__.py`
- Create: `experiments/rag-evaluation/rag_evaluation/config.py`
- Create: `experiments/rag-evaluation/tests/test_config.py`
- Modify: `.env.example`

- [ ] **Step 1: Create package metadata before the first import test**

Create `experiments/rag-evaluation/pyproject.toml`:

```toml
[build-system]
requires = ["setuptools>=80.9.0"]
build-backend = "setuptools.build_meta"

[project]
name = "sigak-rag-evaluation"
version = "0.1.0"
requires-python = ">=3.12,<3.15"
dependencies = [
  "httpx==0.28.1",
  "kiwipiepy==0.23.2",
  "numpy==2.4.6",
  "openai==2.43.0",
  "pydantic==2.13.4",
  "tiktoken==0.13.0",
]

[project.optional-dependencies]
test = ["pytest==9.1.1"]

[tool.pytest.ini_options]
testpaths = ["tests"]
addopts = "-q"
```

Create `experiments/rag-evaluation/rag_evaluation/__init__.py`:

```python
"""Sigak 운영 문서 RAG 평가 패키지."""
```

Create `experiments/rag-evaluation/rag_evaluation/__main__.py`:

```python
from rag_evaluation.cli import main

raise SystemExit(main())
```

- [ ] **Step 2: Create and install the local virtual environment**

Run:

```bash
cd experiments/rag-evaluation
python3 -m venv .venv
.venv/bin/python -m pip install --upgrade pip
.venv/bin/python -m pip install -e '.[test]'
```

Expected:

```text
Successfully installed sigak-rag-evaluation
```

- [ ] **Step 3: Write the failing settings tests**

Create `experiments/rag-evaluation/tests/test_config.py`:

```python
import pytest

from rag_evaluation.config import EvaluationSettings


def test_settings_load_deterministic_defaults_without_openai_key(monkeypatch):
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    monkeypatch.setenv("SIGAK_AI_SERVER_URL", "http://localhost:8000")

    settings = EvaluationSettings.from_env()

    assert settings.ai_server_url == "http://localhost:8000"
    assert settings.chunk_tokenizer == "o200k_base"
    assert settings.top_k == 5
    assert settings.candidate_limit == 10
    assert settings.rrf_k == 60
    assert settings.openai_api_key is None


def test_settings_require_distinct_generator_and_judge_for_full_run(monkeypatch):
    monkeypatch.setenv("OPENAI_API_KEY", "test-key")
    monkeypatch.setenv("SIGAK_RAG_RERANKER_MODEL", "reranker-model")
    monkeypatch.setenv("SIGAK_RAG_GENERATOR_MODEL", "same-model")
    monkeypatch.setenv("SIGAK_RAG_JUDGE_MODEL", "same-model")

    settings = EvaluationSettings.from_env()

    with pytest.raises(ValueError, match="generator and judge models must differ"):
        settings.validate_full_run()
```

- [ ] **Step 4: Run the focused tests and confirm RED**

Run:

```bash
.venv/bin/python -m pytest tests/test_config.py
```

Expected: FAIL because `rag_evaluation.config` does not exist.

- [ ] **Step 5: Implement the minimal settings model**

Create `experiments/rag-evaluation/rag_evaluation/config.py`:

```python
from __future__ import annotations

import os
from dataclasses import dataclass
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
    openai_api_key: str | None
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
```

- [ ] **Step 6: Add safe environment examples**

Append to `.env.example`:

```dotenv
OPENAI_API_KEY=
SIGAK_RAG_EMBEDDING_MODEL=sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2
SIGAK_RAG_RERANKER_MODEL=gpt-5.4-mini
SIGAK_RAG_GENERATOR_MODEL=gpt-5.4
SIGAK_RAG_JUDGE_MODEL=gpt-5.5
SIGAK_RAG_INPUT_PRICE_PER_MILLION_USD=
SIGAK_RAG_OUTPUT_PRICE_PER_MILLION_USD=
```

- [ ] **Step 7: Run the settings tests and confirm GREEN**

Run:

```bash
.venv/bin/python -m pytest tests/test_config.py
```

Expected: `2 passed`.

- [ ] **Step 8: Commit**

```bash
git add .env.example experiments/rag-evaluation
git commit -m "chore: bootstrap ops RAG evaluation package"
```

---

### Task 2: Define Stable Schemas And Dataset Validation

**Files:**

- Create: `experiments/rag-evaluation/rag_evaluation/models.py`
- Create: `experiments/rag-evaluation/rag_evaluation/dataset.py`
- Create: `experiments/rag-evaluation/tests/test_dataset.py`

- [ ] **Step 1: Write failing schema and pair-validation tests**

Create `experiments/rag-evaluation/tests/test_dataset.py`:

```python
import pytest
from pydantic import ValidationError

from rag_evaluation.dataset import validate_dataset
from rag_evaluation.models import EvaluationDataset


def answerable_row(query_id: str, surface_form: str, query: str) -> dict:
    return {
        "intentId": "fallback-qdrant",
        "queryId": query_id,
        "query": query,
        "language": "ko",
        "expectedResponseLanguage": "ko",
        "surfaceForm": surface_form,
        "queryType": "failure_fallback",
        "evidenceShape": "SINGLE_SECTION",
        "answerable": True,
        "expectedAnswer": "다른 검색 경로만 사용해 결과를 제공한다.",
        "evidence": [
            {
                "evidenceId": "single-projection-fallback",
                "documentPath": "docs/API_SPEC.md",
                "headingPath": ["Search Behavior"],
                "quote": "if one projection path fails, the other path can serve",
                "quoteSha256": "a" * 64,
            }
        ],
        "naturalnessReview": "NATURAL",
        "reviewStatus": "REVIEWED",
        "notes": "",
    }


def test_validate_dataset_accepts_one_complete_pair_and_one_unanswerable():
    payload = {
        "datasetVersion": 1,
        "queries": [
            answerable_row(
                "fallback-qdrant-lexical-ko",
                "LEXICAL_ALIGNED",
                "Qdrant 장애 시 검색 모드는 무엇인가?",
            ),
            answerable_row(
                "fallback-qdrant-semantic-ko",
                "SEMANTIC_PARAPHRASE",
                "의미 검색 저장소가 멈춰도 결과를 제공하는 방식은?",
            ),
            {
                "intentId": "missing-sla",
                "queryId": "missing-sla-ko",
                "query": "production SLA는 몇 퍼센트인가?",
                "language": "ko",
                "expectedResponseLanguage": "ko",
                "surfaceForm": "ADVERSARIAL_UNANSWERABLE",
                "queryType": "unanswerable",
                "evidenceShape": None,
                "answerable": False,
                "expectedAnswer": None,
                "evidence": [],
                "naturalnessReview": "NATURAL",
                "reviewStatus": "REVIEWED",
                "notes": "",
            },
        ],
    }

    dataset = EvaluationDataset.model_validate(payload)
    summary = validate_dataset(dataset, require_full=False)

    assert summary.answerable_intent_count == 1
    assert summary.answerable_query_count == 2
    assert summary.unanswerable_query_count == 1


def test_validate_dataset_rejects_pair_with_different_evidence():
    lexical = answerable_row(
        "fallback-qdrant-lexical-ko",
        "LEXICAL_ALIGNED",
        "Qdrant 장애 시 검색 모드는 무엇인가?",
    )
    semantic = answerable_row(
        "fallback-qdrant-semantic-ko",
        "SEMANTIC_PARAPHRASE",
        "의미 검색 저장소가 멈춰도 결과를 제공하는 방식은?",
    )
    semantic["evidence"][0]["evidenceId"] = "different-evidence"

    dataset = EvaluationDataset.model_validate(
        {"datasetVersion": 1, "queries": [lexical, semantic]}
    )

    with pytest.raises(ValueError, match="must share expected answer and evidence"):
        validate_dataset(dataset, require_full=False)


def test_query_schema_rejects_answerable_row_without_evidence():
    payload = answerable_row(
        "fallback-qdrant-lexical-ko",
        "LEXICAL_ALIGNED",
        "Qdrant 장애 시 검색 모드는 무엇인가?",
    )
    payload["evidence"] = []

    with pytest.raises(ValidationError):
        EvaluationDataset.model_validate({"datasetVersion": 1, "queries": [payload]})
```

- [ ] **Step 2: Run the focused tests and confirm RED**

Run:

```bash
.venv/bin/python -m pytest tests/test_dataset.py
```

Expected: FAIL because the models and validator do not exist.

- [ ] **Step 3: Implement the core enums and dataset schemas**

Create `experiments/rag-evaluation/rag_evaluation/models.py`:

```python
from __future__ import annotations

from enum import StrEnum
from typing import Any

from pydantic import BaseModel, ConfigDict, Field, model_validator


def _to_camel(value: str) -> str:
    head, *tail = value.split("_")
    return head + "".join(part.title() for part in tail)


class StrictModel(BaseModel):
    model_config = ConfigDict(
        alias_generator=_to_camel,
        populate_by_name=True,
    )


class SurfaceForm(StrEnum):
    LEXICAL_ALIGNED = "LEXICAL_ALIGNED"
    SEMANTIC_PARAPHRASE = "SEMANTIC_PARAPHRASE"
    ADVERSARIAL_UNANSWERABLE = "ADVERSARIAL_UNANSWERABLE"


class QueryType(StrEnum):
    ARCHITECTURE_RATIONALE = "architecture_rationale"
    COMMAND_PROCEDURE = "command_procedure"
    FAILURE_FALLBACK = "failure_fallback"
    API_CONTRACT = "api_contract"
    CONFIGURATION_SECURITY = "configuration_security"
    CROSS_SECTION_SYNTHESIS = "cross_section_synthesis"
    UNANSWERABLE = "unanswerable"


class EvidenceShape(StrEnum):
    SINGLE_SECTION = "SINGLE_SECTION"
    CROSS_SECTION = "CROSS_SECTION"


class ReviewStatus(StrEnum):
    DRAFT = "DRAFT"
    REVIEWED = "REVIEWED"


class NaturalnessReview(StrEnum):
    NATURAL = "NATURAL"
    REVISE = "REVISE"


class EvidenceSpan(StrictModel):
    evidence_id: str
    document_path: str
    heading_path: list[str]
    quote: str
    quote_sha256: str = Field(pattern=r"^[0-9a-f]{64}$")


class EvaluationQuery(StrictModel):
    intent_id: str
    query_id: str
    query: str
    language: str
    expected_response_language: str
    surface_form: SurfaceForm
    query_type: QueryType
    evidence_shape: EvidenceShape | None
    answerable: bool
    expected_answer: str | None
    evidence: list[EvidenceSpan]
    measured_lexical_overlap: float | None = Field(default=None, ge=0.0, le=1.0)
    naturalness_review: NaturalnessReview
    review_status: ReviewStatus
    notes: str

    @model_validator(mode="after")
    def validate_answerability_contract(self) -> "EvaluationQuery":
        if self.answerable:
            if not self.expected_answer or not self.evidence or self.evidence_shape is None:
                raise ValueError("answerable queries require answer, evidence, and evidence shape")
            if self.surface_form is SurfaceForm.ADVERSARIAL_UNANSWERABLE:
                raise ValueError("answerable query cannot use unanswerable surface form")
        else:
            if self.expected_answer is not None or self.evidence or self.evidence_shape is not None:
                raise ValueError("unanswerable queries must not declare answer or evidence")
            if self.surface_form is not SurfaceForm.ADVERSARIAL_UNANSWERABLE:
                raise ValueError("unanswerable query must use adversarial surface form")
        return self


class EvaluationDataset(StrictModel):
    dataset_version: int = Field(ge=1)
    queries: list[EvaluationQuery]


class DatasetSummary(StrictModel):
    answerable_intent_count: int
    answerable_query_count: int
    unanswerable_query_count: int
    semantic_cross_lingual_count: int


class RunStatus(StrEnum):
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"
    SKIPPED = "SKIPPED"


class FailureType(StrEnum):
    CORPUS_PARSE_FAILURE = "CORPUS_PARSE_FAILURE"
    EVIDENCE_NOT_FOUND = "EVIDENCE_NOT_FOUND"
    EMBEDDING_FAILURE = "EMBEDDING_FAILURE"
    RETRIEVAL_MISS = "RETRIEVAL_MISS"
    RERANK_INVALID_OUTPUT = "RERANK_INVALID_OUTPUT"
    RERANK_REGRESSION = "RERANK_REGRESSION"
    GENERATION_SCHEMA_FAILURE = "GENERATION_SCHEMA_FAILURE"
    INVALID_CITATION = "INVALID_CITATION"
    UNSUPPORTED_CLAIM = "UNSUPPORTED_CLAIM"
    OVER_REFUSAL = "OVER_REFUSAL"
    UNDER_REFUSAL = "UNDER_REFUSAL"
    JUDGE_SCHEMA_FAILURE = "JUDGE_SCHEMA_FAILURE"
    JUDGE_DISAGREEMENT = "JUDGE_DISAGREEMENT"


class FailureRecord(StrictModel):
    query_id: str | None
    variant: str | None
    failure_type: FailureType
    evidence: dict[str, Any]
    notes: str
```

- [ ] **Step 4: Implement dataset loading and invariant validation**

Create `experiments/rag-evaluation/rag_evaluation/dataset.py`:

```python
from __future__ import annotations

import json
from collections import defaultdict
from pathlib import Path

from rag_evaluation.models import (
    DatasetSummary,
    EvaluationDataset,
    ReviewStatus,
    SurfaceForm,
)


def load_dataset(path: Path) -> EvaluationDataset:
    return EvaluationDataset.model_validate(json.loads(path.read_text(encoding="utf-8")))


def validate_dataset(
    dataset: EvaluationDataset,
    *,
    require_full: bool,
) -> DatasetSummary:
    query_ids = [query.query_id for query in dataset.queries]
    if len(query_ids) != len(set(query_ids)):
        raise ValueError("queryId values must be unique")

    answerable = [query for query in dataset.queries if query.answerable]
    unanswerable = [query for query in dataset.queries if not query.answerable]
    by_intent: dict[str, list] = defaultdict(list)
    for query in answerable:
        by_intent[query.intent_id].append(query)

    for intent_id, rows in by_intent.items():
        if len(rows) != 2:
            raise ValueError(f"answerable intent {intent_id} must contain exactly two queries")
        forms = {row.surface_form for row in rows}
        if forms != {
            SurfaceForm.LEXICAL_ALIGNED,
            SurfaceForm.SEMANTIC_PARAPHRASE,
        }:
            raise ValueError(f"answerable intent {intent_id} must contain lexical and semantic forms")
        expected_answers = {row.expected_answer for row in rows}
        evidence_ids = {
            tuple(span.evidence_id for span in row.evidence)
            for row in rows
        }
        if len(expected_answers) != 1 or len(evidence_ids) != 1:
            raise ValueError(
                f"answerable intent {intent_id} must share expected answer and evidence"
            )

    cross_lingual_count = sum(
        1
        for query in answerable
        if query.surface_form is SurfaceForm.SEMANTIC_PARAPHRASE
        and query.language != query.expected_response_language
    )

    if require_full:
        if len(by_intent) != 24 or len(answerable) != 48 or len(unanswerable) != 12:
            raise ValueError("full dataset must contain 24 intents, 48 answerable, and 12 unanswerable")
        if cross_lingual_count < 8:
            raise ValueError("full dataset requires at least 8 cross-lingual semantic queries")
        if any(query.review_status is not ReviewStatus.REVIEWED for query in dataset.queries):
            raise ValueError("full dataset requires every query to be reviewed")

    return DatasetSummary(
        answerable_intent_count=len(by_intent),
        answerable_query_count=len(answerable),
        unanswerable_query_count=len(unanswerable),
        semantic_cross_lingual_count=cross_lingual_count,
    )
```

- [ ] **Step 5: Run the focused tests and confirm GREEN**

Run:

```bash
.venv/bin/python -m pytest tests/test_dataset.py
```

Expected: `3 passed`.

- [ ] **Step 6: Commit**

```bash
git add experiments/rag-evaluation
git commit -m "feat: define RAG evaluation dataset contract"
```

---

### Task 3: Build A Versioned Corpus Snapshot And Markdown Block Parser

**Files:**

- Create: `experiments/rag-evaluation/datasets/corpus-manifest.json`
- Create: `experiments/rag-evaluation/rag_evaluation/corpus.py`
- Create: `experiments/rag-evaluation/tests/fixtures/corpus/manual-a.md`
- Create: `experiments/rag-evaluation/tests/fixtures/corpus/manual-b.md`
- Create: `experiments/rag-evaluation/tests/test_corpus.py`

- [ ] **Step 1: Write deterministic fixture documents**

Create `tests/fixtures/corpus/manual-a.md`:

````markdown
# Manual A

## Startup

Start PostgreSQL before the backend.

```bash
docker compose up -d postgres
```

## Fallback

If vector search fails, use keyword-only mode.
````

Create `tests/fixtures/corpus/manual-b.md`:

```markdown
# Manual B

## Security

- Do not store API keys.
- Do not expose stack traces.
```

- [ ] **Step 2: Write failing parser and snapshot tests**

Create `experiments/rag-evaluation/tests/test_corpus.py`:

```python
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
```

- [ ] **Step 3: Run focused tests and confirm RED**

Run:

```bash
.venv/bin/python -m pytest tests/test_corpus.py
```

Expected: FAIL because corpus models and parser do not exist.

- [ ] **Step 4: Add corpus models**

Append to `rag_evaluation/models.py`:

```python
class BlockKind(StrEnum):
    PARAGRAPH = "PARAGRAPH"
    LIST = "LIST"
    CODE = "CODE"


class CorpusDocumentSpec(StrictModel):
    document_path: str
    included_sections: list[str]


class CorpusManifest(StrictModel):
    corpus_id: str
    documents: list[CorpusDocumentSpec]


class DocumentBlock(StrictModel):
    kind: BlockKind
    heading_path: list[str]
    content: str
    normalized_start: int
    normalized_end: int


class NormalizedDocument(StrictModel):
    document_path: str
    sha256: str
    included_sections: list[str]
    normalized_text: str
    blocks: list[DocumentBlock]


class CorpusSnapshot(StrictModel):
    corpus_id: str
    git_commit: str
    working_tree_dirty: bool
    generated_at: str
    documents: list[NormalizedDocument]
```

- [ ] **Step 5: Implement the line-oriented Markdown parser**

Create `experiments/rag-evaluation/rag_evaluation/corpus.py`:

```python
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
        if line.startswith("```"):
            if in_code:
                append_block(BlockKind.CODE, "\n".join(code_lines) + "\n")
                code_lines = []
                in_code = False
            else:
                flush_prose()
                in_code = True
            continue
        if in_code:
            code_lines.append(line)
            continue

        heading_match = HEADING.match(line)
        if heading_match:
            flush_prose()
            level = len(heading_match.group(1))
            title = heading_match.group(2).strip()
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
        path = repository_root / spec.document_path
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
```

- [ ] **Step 6: Create the real corpus allowlist**

Create `experiments/rag-evaluation/datasets/corpus-manifest.json`:

```json
{
  "corpusId": "sigak-ops-2026-06-20",
  "documents": [
    {"documentPath": "README.md", "includedSections": ["*"]},
    {"documentPath": "docs/DEMO_FLOW.md", "includedSections": ["*"]},
    {"documentPath": "docs/API_SPEC.md", "includedSections": ["*"]},
    {"documentPath": "experiments/README.md", "includedSections": ["*"]},
    {"documentPath": "backend/README.md", "includedSections": ["*"]},
    {"documentPath": "ai/README.md", "includedSections": ["*"]},
    {
      "documentPath": "docs/STATUS.md",
      "includedSections": [
        "4.4 Hybrid public search with PostgreSQL fallback",
        "4.5 Qdrant internal vector search",
        "4.6 Neo4j internal graph projection and context lookup",
        "4.7 Public graph-aware article detail",
        "4.9 arXiv export API rate-limit handling",
        "4.10 arXiv repeated command-run cooldown",
        "6.3 Risk controls"
      ]
    }
  ]
}
```

- [ ] **Step 7: Run focused tests and confirm GREEN**

Run:

```bash
.venv/bin/python -m pytest tests/test_corpus.py
```

Expected: `2 passed`.

- [ ] **Step 8: Commit**

```bash
git add experiments/rag-evaluation
git commit -m "feat: build versioned operations corpus"
```

---

### Task 4: Implement Shared Tokenization, Two Chunkers, And Evidence Mapping

**Files:**

- Create: `experiments/rag-evaluation/rag_evaluation/tokenization.py`
- Create: `experiments/rag-evaluation/rag_evaluation/chunking.py`
- Create: `experiments/rag-evaluation/tests/test_tokenization.py`
- Create: `experiments/rag-evaluation/tests/test_chunking.py`

- [ ] **Step 1: Write failing tokenizer tests**

Create `experiments/rag-evaluation/tests/test_tokenization.py`:

```python
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
```

- [ ] **Step 2: Write failing chunking and evidence tests**

Create `experiments/rag-evaluation/tests/test_chunking.py`:

```python
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
```

- [ ] **Step 3: Run focused tests and confirm RED**

Run:

```bash
.venv/bin/python -m pytest tests/test_tokenization.py tests/test_chunking.py
```

Expected: FAIL because tokenization, chunking, and chunk models do not exist.

- [ ] **Step 4: Implement chunk and evidence models**

Append to `rag_evaluation/models.py`:

```python
class ChunkStrategy(StrEnum):
    FIXED = "fixed"
    HEADING = "heading"


class Chunk(StrictModel):
    chunk_id: str
    strategy: ChunkStrategy
    document_path: str
    heading_path: list[str]
    content: str
    token_count: int
    contains_code_block: bool
    content_sha256: str


class EvidenceChunkMatch(StrictModel):
    evidence_id: str
    chunk_id: str
    grade: int = Field(ge=0, le=2)
```

- [ ] **Step 5: Implement the two tokenizers**

Create `experiments/rag-evaluation/rag_evaluation/tokenization.py`:

```python
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
        return self._encoding.encode(text)

    def decode(self, tokens: list[int]) -> str:
        return self._encoding.decode(tokens)


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
```

- [ ] **Step 6: Implement stable IDs and fixed-size chunking**

Create `experiments/rag-evaluation/rag_evaluation/chunking.py` with these helpers:

```python
from __future__ import annotations

import hashlib
from collections import defaultdict

from rag_evaluation.models import (
    Chunk,
    ChunkStrategy,
    EvidenceChunkMatch,
    EvidenceSpan,
    NormalizedDocument,
)
from rag_evaluation.tokenization import ChunkTokenCodec


def _digest(value: str, length: int) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()[:length]


def _build_chunk(
    *,
    strategy: ChunkStrategy,
    document_path: str,
    heading_path: list[str],
    content: str,
    token_count: int,
    contains_code_block: bool,
) -> Chunk:
    content_sha = hashlib.sha256(content.encode("utf-8")).hexdigest()
    chunk_id = ":".join(
        [
            strategy.value,
            _digest(document_path, 8),
            _digest(" > ".join(heading_path), 8),
            content_sha[:12],
        ]
    )
    return Chunk(
        chunkId=chunk_id,
        strategy=strategy,
        documentPath=document_path,
        headingPath=heading_path,
        content=content,
        tokenCount=token_count,
        containsCodeBlock=contains_code_block,
        contentSha256=content_sha,
    )


class FixedSizeChunker:
    def __init__(
        self,
        *,
        codec: ChunkTokenCodec,
        max_tokens: int,
        overlap_tokens: int,
    ) -> None:
        self.codec = codec
        self.max_tokens = max_tokens
        self.overlap_tokens = overlap_tokens

    def chunk(self, documents: list[NormalizedDocument]) -> list[Chunk]:
        chunks: list[Chunk] = []
        step = self.max_tokens - self.overlap_tokens
        for document in documents:
            tokens = self.codec.encode(document.normalized_text)
            for start in range(0, len(tokens), step):
                window = tokens[start : start + self.max_tokens]
                if not window:
                    break
                content = self.codec.decode(window).strip()
                heading_path = next(
                    (
                        block.heading_path
                        for block in document.blocks
                        if block.content and block.content[:24] in content
                    ),
                    [],
                )
                contains_code_block = any(
                    block.kind.value == "CODE"
                    and block.content.strip()
                    and block.content.strip()[:24] in content
                    for block in document.blocks
                )
                chunks.append(
                    _build_chunk(
                        strategy=ChunkStrategy.FIXED,
                        document_path=document.document_path,
                        heading_path=heading_path,
                        content=content,
                        token_count=len(window),
                        contains_code_block=contains_code_block,
                    )
                )
                if start + self.max_tokens >= len(tokens):
                    break
        return chunks
```

The fixed chunker must not use heading boundaries to choose windows. Heading metadata is
best-effort annotation only and must not alter boundaries.

- [ ] **Step 7: Implement heading-aware chunking and evidence matching**

Continue `chunking.py`:

```python
class HeadingAwareChunker:
    def __init__(
        self,
        *,
        codec: ChunkTokenCodec,
        max_tokens: int,
        overlap_tokens: int,
    ) -> None:
        self.codec = codec
        self.max_tokens = max_tokens
        self.overlap_tokens = overlap_tokens

    def chunk(self, documents: list[NormalizedDocument]) -> list[Chunk]:
        chunks: list[Chunk] = []
        for document in documents:
            grouped: dict[tuple[str, ...], list] = defaultdict(list)
            for block in document.blocks:
                grouped[tuple(block.heading_path)].append(block)
            for heading_tuple, blocks in grouped.items():
                parts: list[str] = []
                contains_code = False
                for block in blocks:
                    candidate = "\n\n".join([*parts, block.content])
                    candidate_tokens = self.codec.encode(candidate)
                    if parts and len(candidate_tokens) > self.max_tokens:
                        chunks.extend(
                            self._split_group(
                                document.document_path,
                                list(heading_tuple),
                                "\n\n".join(parts),
                                contains_code,
                            )
                        )
                        parts = [block.content]
                        contains_code = block.kind.value == "CODE"
                    else:
                        parts.append(block.content)
                        contains_code = contains_code or block.kind.value == "CODE"
                if parts:
                    chunks.extend(
                        self._split_group(
                            document.document_path,
                            list(heading_tuple),
                            "\n\n".join(parts),
                            contains_code,
                        )
                    )
        return chunks

    def _split_group(
        self,
        document_path: str,
        heading_path: list[str],
        content: str,
        contains_code_block: bool,
    ) -> list[Chunk]:
        tokens = self.codec.encode(content)
        step = self.max_tokens - self.overlap_tokens
        result = []
        for start in range(0, len(tokens), step):
            window = tokens[start : start + self.max_tokens]
            result.append(
                _build_chunk(
                    strategy=ChunkStrategy.HEADING,
                    document_path=document_path,
                    heading_path=heading_path,
                    content=self.codec.decode(window).strip(),
                    token_count=len(window),
                    contains_code_block=contains_code_block,
                )
            )
            if start + self.max_tokens >= len(tokens):
                break
        return result


def assign_evidence_to_chunks(
    chunks: list[Chunk],
    evidence_spans: list[EvidenceSpan],
) -> dict[str, list[EvidenceChunkMatch]]:
    matches: dict[str, list[EvidenceChunkMatch]] = defaultdict(list)
    for evidence in evidence_spans:
        normalized_quote = " ".join(evidence.quote.split())
        for chunk in chunks:
            if chunk.document_path != evidence.document_path:
                continue
            normalized_content = " ".join(chunk.content.split())
            if normalized_quote in normalized_content:
                grade = 2
            elif (
                chunk.heading_path == evidence.heading_path
                and any(
                    sentence
                    for sentence in normalized_quote.split(". ")
                    if len(sentence) >= 24 and sentence in normalized_content
                )
            ):
                grade = 1
            else:
                continue
            matches[evidence.evidence_id].append(
                EvidenceChunkMatch(
                    evidenceId=evidence.evidence_id,
                    chunkId=chunk.chunk_id,
                    grade=grade,
                )
            )
    return matches
```

- [ ] **Step 8: Run focused tests and fix only contract failures**

Run:

```bash
.venv/bin/python -m pytest tests/test_tokenization.py tests/test_chunking.py
```

Expected: `6 passed`.

- [ ] **Step 9: Add a chunk-balance regression test**

Add to `tests/test_chunking.py`:

```python
def test_both_chunkers_share_the_same_max_token_budget():
    codec = ChunkTokenCodec("o200k_base")
    document = fixture_document()

    fixed = FixedSizeChunker(codec=codec, max_tokens=20, overlap_tokens=5).chunk([document])
    heading = HeadingAwareChunker(codec=codec, max_tokens=20, overlap_tokens=5).chunk(
        [document]
    )

    assert max(chunk.token_count for chunk in fixed) <= 20
    assert max(chunk.token_count for chunk in heading) <= 20
```

Run:

```bash
.venv/bin/python -m pytest tests/test_chunking.py
```

Expected: `4 passed`.

- [ ] **Step 10: Commit**

```bash
git add experiments/rag-evaluation
git commit -m "feat: add balanced RAG chunking strategies"
```

---

### Task 5: Author And Freeze The Reviewed 60-Query Dataset

**Files:**

- Create: `experiments/rag-evaluation/datasets/ops-questions.json`
- Create: `experiments/rag-evaluation/datasets/smoke-questions.json`
- Modify: `experiments/rag-evaluation/tests/test_dataset.py`

This task is data work, not filler. Do not generate the final labels from retrieval results.
Draft the queries, verify the quoted evidence against the frozen corpus, review naturalness,
then freeze the dataset before running any retrieval comparison.

- [ ] **Step 1: Add the full-dataset acceptance test before writing the data**

Append to `tests/test_dataset.py`:

```python
from pathlib import Path

from rag_evaluation.corpus import build_corpus_snapshot
from rag_evaluation.dataset import load_dataset
from rag_evaluation.models import CorpusManifest


PROJECT_ROOT = Path(__file__).resolve().parents[3]


def test_committed_full_dataset_satisfies_size_pair_and_evidence_contracts():
    dataset = load_dataset(PROJECT_ROOT / "experiments/rag-evaluation/datasets/ops-questions.json")
    summary = validate_dataset(dataset, require_full=True)

    assert summary.answerable_intent_count == 24
    assert summary.answerable_query_count == 48
    assert summary.unanswerable_query_count == 12
    assert summary.semantic_cross_lingual_count >= 8
```

- [ ] **Step 2: Run the acceptance test and confirm RED**

Run:

```bash
.venv/bin/python -m pytest tests/test_dataset.py::test_committed_full_dataset_satisfies_size_pair_and_evidence_contracts
```

Expected: FAIL because `ops-questions.json` does not exist.

- [ ] **Step 3: Use this exact 24-intent content matrix**

Each row below becomes two answerable query objects with the same `intentId`,
`expectedAnswer`, and evidence list. `L` is `LEXICAL_ALIGNED`; `S` is
`SEMANTIC_PARAPHRASE`. For rows marked `S language=en`, set
`expectedResponseLanguage=ko` so they count as cross-lingual.

| ID | Type | L query | S query | S language | Evidence source / heading | Expected answer |
| --- | --- | --- | --- | --- | --- | --- |
| arch-public-boundary | architecture_rationale | Spring Boot와 FastAPI 중 frontend의 public API boundary는 무엇인가? | Which service must the browser call, and where are AI-specific requests routed afterward? | en | `README.md` / `Architecture` | Frontend는 Spring Boot만 호출하고 Spring Boot가 embedding·enrichment 같은 AI/RAG 작업에 한해 FastAPI를 호출한다. |
| arch-source-truth | architecture_rationale | PostgreSQL과 Elasticsearch·Qdrant·Neo4j의 저장소 역할은 어떻게 다른가? | If every search index vanished, which database would still hold authoritative article data? | en | `README.md` / `Architecture` | PostgreSQL이 source of truth이고 Elasticsearch, Qdrant, Neo4j는 재생성 가능한 projection store다. |
| arch-experiment-boundary | architecture_rationale | `experiments/`에는 무엇을 저장하며 제품 API와 어떤 경계를 유지하는가? | Why are benchmark outputs kept beside the app instead of changing the user-facing search response? | en | `README.md` / `Architecture`; `Evaluation Artifacts` | 실험 디렉터리는 재현 가능한 평가 artifact를 보관하고 제품의 공개 응답 계약은 바꾸지 않는다. |
| arch-stable-response | architecture_rationale | graph-aware retrieval이 추가돼도 public search response shape는 어떻게 해야 하는가? | Can the retrieval implementation evolve without forcing article-card clients to adopt a new payload? | en | `docs/API_SPEC.md` / `Search Behavior` | 내부 검색 구현이 변해도 public article response shape는 안정적으로 유지한다. |
| proc-start-services | command_procedure | PostgreSQL, Elasticsearch, Qdrant, Neo4j, AI server를 한 번에 시작하는 명령은? | 로컬 검색·그래프·임베딩 의존성을 함께 올리려면 어떤 Compose 절차를 실행해야 하는가? | ko | `docs/DEMO_FLOW.md` / `Prerequisites` | repository root에서 지정된 docker compose up 명령을 실행하고 ps로 healthy 상태를 확인한다. |
| proc-rebuild-all | command_procedure | Elasticsearch, Qdrant, Neo4j projection rebuild endpoint 세 개는? | After the backend starts, how do I refresh every derived search and graph store? | en | `README.md` / `Run Locally` | 세 internal POST endpoint를 순서대로 호출해 keyword, vector, graph projection을 재구축한다. |
| proc-collection-run | command_procedure | `github-blog` 한 건을 수집하는 internal collection curl 명령은? | 선택한 뉴스 소스에서 최대 한 건만 가져와 저장 흐름을 확인하려면 어떤 요청을 보내는가? | ko | `docs/DEMO_FLOW.md` / `Step 1. Run Selected-Source Collection` | `/api/internal/collections/runs`에 sourceIds와 maxArticlesPerSource JSON을 POST한다. |
| proc-graph-eval | command_procedure | graph-aware evaluation runner에 필요한 `--include-graph-context` 명령은? | How can I measure explanation evidence on public article detail for the preserved smoke labels? | en | `experiments/README.md` / `Graph-Aware Evaluation Runner` | public system과 graph-context 옵션, label·base URL·output·k·limit를 지정해 Node runner를 실행한다. |
| fallback-one-path | failure_fallback | Elasticsearch 또는 Qdrant 한쪽만 실패하면 `KEYWORD_ONLY`/`VECTOR_ONLY`로 어떻게 degrade되는가? | 검색 후보를 만드는 두 경로 중 하나가 멈추면 서비스는 결과 제공을 어떻게 이어 가는가? | ko | `docs/API_SPEC.md` / `Search Behavior` | 살아 있는 projection 경로만 사용해 keyword-only 또는 vector-only 결과를 제공한다. |
| fallback-both-paths | failure_fallback | Elasticsearch와 Qdrant가 모두 실패하면 PostgreSQL fallback은 어떤 필드를 검색하는가? | When neither derived retrieval store is available, where does search continue and what article attributes are checked? | en | `docs/API_SPEC.md` / `Search Behavior` | PostgreSQL에서 title, summary, primaryCategory, topics를 대소문자 구분 없이 필터링한다. |
| fallback-neo4j | failure_fallback | Neo4j가 unavailable이면 `/api/articles/{id}/graph-context` 응답은? | If graph storage is down, does article detail fail or return a degraded companion payload? | en | `docs/API_SPEC.md` / `Public Article Graph Context` | article detail을 깨지 않고 같은 응답 shape에 빈 relatedArticleReasons와 topics를 반환한다. |
| fallback-transient-fetch | failure_fallback | `TRANSIENT_FETCH`와 `retryable=true` 실패는 언제 다시 시도해야 하는가? | 일시적인 upstream/network 수집 장애가 기록됐을 때 반복 실행 전에 무엇을 확인해야 하는가? | ko | `docs/DEMO_FLOW.md` / `Troubleshooting` | 강제 프록시를 제거하고 네트워크·Docker·upstream 회복을 확인하거나 기다린 뒤 같은 source를 재실행한다. |
| api-bulk-order | api_contract | `GET /api/articles?ids=1,2,3`의 순서와 누락 규칙은? | How are duplicate, missing, or non-public IDs handled when related articles are loaded in bulk? | en | `docs/API_SPEC.md` / `Article Response`; `Bulk Article Lookup` | 첫 요청 순서를 유지하고 중복은 첫 항목만 남기며 missing/non-public ID는 생략한다. |
| api-ids-precedence | api_contract | `query`와 `ids`가 함께 있으면 어느 파라미터가 우선하는가? | 검색어와 명시적 article reload 목록을 동시에 보내면 backend는 어느 의도를 선택하는가? | ko | `docs/API_SPEC.md` / `Bulk Article Lookup` | 명시적 reload 경로인 ids 조회가 우선한다. |
| api-strict-vs-public | api_contract | strict `HYBRID`와 `PUBLIC` benchmark의 실패·degrade 규칙 차이는? | Why can the experiment-mode fusion run fail while the user-visible search still returns results? | en | `docs/API_SPEC.md` / `Internal Retrieval Benchmark Run Contract` | strict HYBRID는 한 경로 실패도 실패로 기록하고, PUBLIC은 keyword/vector/PostgreSQL fallback으로 degrade할 수 있다. |
| api-embedding-contract | api_contract | `POST /api/embeddings/text` 응답의 provider, modelName, dimension, embedding 필드는? | 의미 벡터 서비스가 모델 교체 가능한 내부 경계임을 확인하려면 어떤 metadata를 읽어야 하는가? | ko | `docs/API_SPEC.md` / `Internal Embedding Contract` | provider, modelName, dimension, embedding을 반환하며 whitespace-only 입력은 거부한다. |
| config-internal-eval | configuration_security | internal retrieval evaluation endpoint의 기본 활성화 상태와 opt-in 환경 변수는? | How do I deliberately enable the local-only comparison API that is disabled during normal startup? | en | `docs/API_SPEC.md` / `Internal Retrieval Benchmark Run Contract` | 기본은 disabled이며 `SIGAK_INTERNAL_SEARCH_EVALUATION_ENABLED=true`로 로컬 실행에서 opt-in한다. |
| config-deterministic-limit | configuration_security | deterministic embedding을 semantic quality 근거로 쓰면 안 되는 이유는? | Why is the tiny repeatable vector mode suitable for wiring checks but not retrieval-quality claims? | en | `docs/API_SPEC.md` / `Internal Embedding Contract`; `ai/README.md` / `Current Status` | deterministic 8차원 벡터는 wiring smoke용이고 실제 다국어 semantic 평가는 local multilingual model을 사용해야 한다. |
| config-no-secret-metadata | configuration_security | retrieval benchmark response metadata에 API key·header·raw vector를 포함해도 되는가? | Which operational details must never leak into experiment metadata even though model name and dimension are allowed? | en | `docs/API_SPEC.md` / `Internal Retrieval Benchmark Run Contract` | provider/model/dimension 외에 API key, header, env, URL, raw vector, credential, stack trace를 노출하지 않는다. |
| config-arxiv-cooldown | configuration_security | arXiv cross-run cooldown state file과 override 환경 변수는? | 짧은 JVM 재실행 사이에도 요청 간격을 공유하는 로컬 장치는 무엇이며 분산 환경까지 보장하는가? | ko | `docs/STATUS.md` / `4.10 arXiv repeated command-run cooldown` | 기본 tmp state file과 `SIGAK_COLLECTION_ARXIV_COOLDOWN_STATE_FILE` override를 사용하며 single-machine guard다. |
| synth-full-flow | cross_section_synthesis | collection부터 smoke artifact inspection까지 local demo 전체 순서는? | Summarize the reproducible path from selected news sources to grounded search and graph evidence. | en | `docs/DEMO_FLOW.md` / `What This Flow Proves` | 수집→PostgreSQL→failure diagnostics→ES/Qdrant/Neo4j rebuild→public search/detail→metrics→artifact inspection 순서다. |
| synth-manual-projection | cross_section_synthesis | collection 이후 projection rebuild가 자동인가 수동인가? 필요한 후속 단계는? | 뉴스가 저장됐다고 해서 검색 인덱스가 즉시 갱신되지 않는 이유와 운영자가 해야 할 일은 무엇인가? | ko | `docs/DEMO_FLOW.md` / `Troubleshooting`; `README.md` / `Run Locally` | collection은 projection을 자동 갱신하지 않으므로 세 rebuild endpoint를 수동 호출해야 한다. |
| synth-qdrant-ai | cross_section_synthesis | Qdrant rebuild가 AI server와 multilingual embedding 설정에 의존하는 방식은? | Which service must be healthy before vector projection rebuild, and what kind of model should back quality evaluation? | en | `docs/DEMO_FLOW.md` / `Step 4. Rebuild Qdrant Vector Projection`; `Troubleshooting`; `ai/README.md` / `Current Status` | AI server embedding endpoint가 reachable해야 하며 실제 평가는 multilingual local model을 사용한다. |
| synth-smoke-claim | cross_section_synthesis | 3-query smoke artifact와 41-article expanded catalog의 주장 가능 범위는? | Why can the committed mini benchmark prove reproducibility but not yet support broad ranking-quality claims? | en | `README.md` / `Current Status`; `Evaluation Artifacts` | 3-query 결과는 wiring 재현성만 보여 주며 41-article catalog는 reviewed labels 전까지 품질 주장을 보류한다. |

- [ ] **Step 4: Use exact evidence quotes and hashes**

For every evidence object:

1. Copy a contiguous quote from the stated heading.
2. Preserve identifiers, endpoint text, environment variables, and command text.
3. Normalize prose whitespace only.
4. Compute `quoteSha256` from the normalized quote.
5. For cross-section rows, use two or more evidence objects.
6. Set `evidenceShape=CROSS_SECTION` only for:
   - `arch-experiment-boundary`
   - `api-bulk-order`
   - `config-deterministic-limit`
   - all four `synth-*` intents
7. Set all other answerable rows to `SINGLE_SECTION`.

Use this evidence object shape:

```json
{
  "evidenceId": "fallback-one-path-api-search",
  "documentPath": "docs/API_SPEC.md",
  "headingPath": ["Search Behavior"],
  "quote": "if one projection path fails, the other path can serve `KEYWORD_ONLY` or `VECTOR_ONLY` results",
  "quoteSha256": "computed-lowercase-sha256"
}
```

- [ ] **Step 5: Add these exact 12 unanswerable/adversarial queries**

| ID | Subtype | Query |
| --- | --- | --- |
| missing-production-sla | missing_number | Sigak의 production SLA는 몇 퍼센트인가? |
| missing-max-qps | missing_number | public article search가 보장하는 최대 초당 요청 수는 얼마인가? |
| missing-rto | missing_number | Elasticsearch 전체 장애 시 공식 RTO는 몇 분인가? |
| missing-monthly-uptime | missing_number | Neo4j graph context의 월간 가용성 목표는 얼마인가? |
| false-frontend-fastapi | false_premise | frontend가 FastAPI의 embedding endpoint를 직접 호출하는 코드를 설명해 줘. |
| false-neo4j-truth | false_premise | Neo4j가 원본 기사와 요약의 source of truth인 이유는 무엇인가? |
| false-auto-rebuild | false_premise | collection이 끝날 때 세 projection이 자동 rebuild되는 순서를 알려 줘. |
| false-deterministic-quality | false_premise | deterministic 8차원 embedding이 multilingual model보다 검색 품질이 높다는 결과는? |
| out-k8s-autoscale | out_of_scope | Kubernetes HPA가 Sigak backend pod를 확장하는 기준을 알려 줘. |
| out-password-reset | out_of_scope | 사용자 비밀번호 재설정 API와 이메일 토큰 만료 시간을 알려 줘. |
| out-aws-region | out_of_scope | 운영 AWS 배포 리전과 multi-AZ 토폴로지를 알려 줘. |
| out-robot-estop | out_of_scope | 로봇 actuator emergency stop 명령과 안전 PLC 회로를 알려 줘. |

Each object uses:

```json
{
  "intentId": "missing-production-sla",
  "queryId": "missing-production-sla-ko",
  "query": "Sigak의 production SLA는 몇 퍼센트인가?",
  "language": "ko",
  "expectedResponseLanguage": "ko",
  "surfaceForm": "ADVERSARIAL_UNANSWERABLE",
  "queryType": "unanswerable",
  "evidenceShape": null,
  "answerable": false,
  "expectedAnswer": null,
  "evidence": [],
  "measuredLexicalOverlap": null,
  "naturalnessReview": "NATURAL",
  "reviewStatus": "REVIEWED",
  "notes": "문서에 없는 정보이므로 추측하지 않고 답변 불가를 표시해야 한다."
}
```

- [ ] **Step 6: Create the 3-query smoke fixture**

Create `datasets/smoke-questions.json` with:

- `arch-source-truth-lexical-ko`
- `arch-source-truth-semantic-en`
- `missing-production-sla-ko`

Set `datasetVersion=1`. This keeps one complete paired intent plus one unanswerable query,
so pair validation remains active even in smoke mode. The fixture is never used for quality claims.

- [ ] **Step 7: Review before retrieval**

Manually check:

- all 24 semantic rows are natural paraphrases rather than keyword deletion exercises
- at least 8 semantic rows have `language=en`, `expectedResponseLanguage=ko`
- each pair asks the same information need
- each pair shares the exact same expected answer and ordered evidence IDs
- no evidence quote contains a claim from a different heading
- all 60 rows are `REVIEWED`

- [ ] **Step 8: Run the full dataset acceptance test**

Run:

```bash
.venv/bin/python -m pytest tests/test_dataset.py
```

Expected: all tests pass, including `24`, `48`, `12`, and cross-lingual count gates.

- [ ] **Step 9: Commit the frozen dataset before retrieval implementation**

```bash
git add experiments/rag-evaluation/datasets experiments/rag-evaluation/tests/test_dataset.py
git commit -m "test: freeze reviewed ops RAG dataset"
```

Record the commit SHA; the first full retrieval artifact must reference a descendant of this
dataset-freeze commit. Do not edit query wording or evidence after seeing retrieval rankings
without incrementing `datasetVersion` and explaining the change.

---

### Task 6: Implement FastAPI Embeddings, BM25, Dense, And Hybrid Retrieval

**Files:**

- Create: `experiments/rag-evaluation/rag_evaluation/embeddings.py`
- Create: `experiments/rag-evaluation/rag_evaluation/retrieval.py`
- Create: `experiments/rag-evaluation/tests/test_embeddings.py`
- Create: `experiments/rag-evaluation/tests/test_retrieval.py`
- Create: `experiments/rag-evaluation/tests/fixtures/embedding-response.json`

- [ ] **Step 1: Write failing embedding client and cache tests**

Create `tests/test_embeddings.py`:

```python
import json
from pathlib import Path

import httpx
import pytest

from rag_evaluation.embeddings import EmbeddingClient, EmbeddingCache


def test_embedding_client_validates_metadata_and_normalizes_vector(tmp_path):
    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/api/embeddings/text"
        return httpx.Response(
            200,
            json={
                "provider": "local",
                "modelName": "multilingual-model",
                "dimension": 2,
                "embedding": [3.0, 4.0],
            },
        )

    client = EmbeddingClient(
        base_url="http://test",
        expected_model="multilingual-model",
        http_client=httpx.Client(transport=httpx.MockTransport(handler)),
        cache=EmbeddingCache(tmp_path / "embeddings.json"),
    )

    result = client.embed("semantic query", cache_key="query:1")

    assert result.dimension == 2
    assert result.vector == pytest.approx([0.6, 0.8])


def test_embedding_client_reuses_cache_without_second_request(tmp_path):
    calls = 0

    def handler(_: httpx.Request) -> httpx.Response:
        nonlocal calls
        calls += 1
        return httpx.Response(
            200,
            json={
                "provider": "local",
                "modelName": "multilingual-model",
                "dimension": 2,
                "embedding": [1.0, 0.0],
            },
        )

    cache = EmbeddingCache(tmp_path / "embeddings.json")
    client = EmbeddingClient(
        base_url="http://test",
        expected_model="multilingual-model",
        http_client=httpx.Client(transport=httpx.MockTransport(handler)),
        cache=cache,
    )

    client.embed("same", cache_key="chunk:abc")
    client.embed("same", cache_key="chunk:abc")

    assert calls == 1


def test_embedding_client_rejects_dimension_mismatch(tmp_path):
    def handler(_: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "provider": "local",
                "modelName": "multilingual-model",
                "dimension": 3,
                "embedding": [1.0, 0.0],
            },
        )

    client = EmbeddingClient(
        base_url="http://test",
        expected_model="multilingual-model",
        http_client=httpx.Client(transport=httpx.MockTransport(handler)),
        cache=EmbeddingCache(tmp_path / "embeddings.json"),
    )

    with pytest.raises(ValueError, match="dimension"):
        client.embed("bad", cache_key="bad")
```

- [ ] **Step 2: Write failing retrieval tests**

Create `tests/test_retrieval.py`:

```python
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
```

- [ ] **Step 3: Run focused tests and confirm RED**

Run:

```bash
.venv/bin/python -m pytest tests/test_embeddings.py tests/test_retrieval.py
```

Expected: FAIL because embedding and retrieval classes do not exist.

- [ ] **Step 4: Add retrieval models**

Append to `rag_evaluation/models.py`:

```python
class EmbeddingResult(StrictModel):
    provider: str
    model_name: str
    dimension: int
    vector: list[float]


class RetrievalSystem(StrEnum):
    BM25 = "bm25"
    DENSE = "dense"
    HYBRID = "hybrid"
    HYBRID_RERANK = "hybrid_rerank"


class RankedChunk(StrictModel):
    chunk_id: str
    score: float
    rank: int


class RetrievalRun(StrictModel):
    query_id: str
    intent_id: str
    chunk_strategy: ChunkStrategy
    retrieval_system: RetrievalSystem
    status: RunStatus
    ranked_chunks: list[RankedChunk]
    latency_ms: float
    failure_reason: str | None = None
    input_tokens: int = 0
    output_tokens: int = 0
    fallback_used: bool = False
```

- [ ] **Step 5: Implement normalized embedding cache and HTTP client**

Create `rag_evaluation/embeddings.py`:

```python
from __future__ import annotations

import json
from pathlib import Path

import httpx
import numpy as np

from rag_evaluation.models import EmbeddingResult


class EmbeddingCache:
    def __init__(self, path: Path) -> None:
        self.path = path
        self._values = (
            json.loads(path.read_text(encoding="utf-8"))
            if path.exists()
            else {}
        )

    def get(self, key: str) -> EmbeddingResult | None:
        value = self._values.get(key)
        return EmbeddingResult.model_validate(value) if value else None

    def put(self, key: str, value: EmbeddingResult) -> None:
        self._values[key] = value.model_dump(by_alias=True)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.path.write_text(
            json.dumps(self._values, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )


class EmbeddingClient:
    def __init__(
        self,
        *,
        base_url: str,
        expected_model: str,
        http_client: httpx.Client,
        cache: EmbeddingCache,
    ) -> None:
        self.base_url = base_url.rstrip("/")
        self.expected_model = expected_model
        self.http_client = http_client
        self.cache = cache

    def embed(self, text: str, *, cache_key: str) -> EmbeddingResult:
        cached = self.cache.get(cache_key)
        if cached:
            return cached
        response = self.http_client.post(
            f"{self.base_url}/api/embeddings/text",
            json={"text": text},
            timeout=60.0,
        )
        response.raise_for_status()
        payload = response.json()
        vector = payload["embedding"]
        dimension = payload["dimension"]
        if len(vector) != dimension:
            raise ValueError("embedding dimension does not match vector length")
        if payload["modelName"] != self.expected_model:
            raise ValueError("embedding model does not match configured model")
        array = np.asarray(vector, dtype=float)
        norm = float(np.linalg.norm(array))
        if norm == 0:
            raise ValueError("embedding vector norm must be non-zero")
        result = EmbeddingResult(
            provider=payload["provider"],
            modelName=payload["modelName"],
            dimension=dimension,
            vector=(array / norm).tolist(),
        )
        self.cache.put(cache_key, result)
        return result
```

The cache key used by the runner must include:

```text
embedding model + corpus ID + document/chunk content hash
```

For query embeddings include:

```text
embedding model + dataset version + query ID + query text hash
```

- [ ] **Step 6: Implement explicit BM25, dense cosine, and RRF**

Create `rag_evaluation/retrieval.py`:

```python
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
        self.average_length = sum(self.lengths) / len(self.lengths)
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
            scored.append((chunk.chunk_id, score))
        ranked = sorted(scored, key=lambda row: (-row[1], row[0]))[:limit]
        return [
            RankedChunk(chunkId=chunk_id, score=score, rank=index + 1)
            for index, (chunk_id, score) in enumerate(ranked)
        ]


class DenseIndex:
    def __init__(self, *, chunks: list[Chunk], vectors: dict[str, list[float]]) -> None:
        self.chunks = chunks
        self.vectors = vectors

    def search(self, query_vector: list[float], *, limit: int) -> list[RankedChunk]:
        query = np.asarray(query_vector, dtype=float)
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
```

- [ ] **Step 7: Add dense sanity-fixture tests**

Append to `tests/test_retrieval.py`:

```python
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
```

- [ ] **Step 8: Run focused tests and confirm GREEN**

Run:

```bash
.venv/bin/python -m pytest tests/test_embeddings.py tests/test_retrieval.py
```

Expected: `7 passed`.

- [ ] **Step 9: Commit**

```bash
git add experiments/rag-evaluation
git commit -m "feat: add BM25 dense and hybrid retrieval"
```

---

### Task 7: Implement Retrieval Metrics, Slices, And Intent-Cluster Bootstrap

**Files:**

- Create: `experiments/rag-evaluation/rag_evaluation/metrics.py`
- Create: `experiments/rag-evaluation/tests/test_metrics.py`

- [ ] **Step 1: Write failing metric tests**

Create `tests/test_metrics.py`:

```python
import pytest

from rag_evaluation.metrics import (
    cluster_bootstrap_delta,
    evidence_coverage_at_k,
    mean_reciprocal_rank_at_k,
    ndcg_at_k,
    recall_at_k,
    summarize_slices,
)


def test_retrieval_metrics_use_direct_and_supporting_grades():
    ranked = ["c1", "c2", "c3"]
    grades = {"c1": 0, "c2": 2, "c3": 1}
    evidence_by_chunk = {"c2": {"e1"}, "c3": {"e2"}}

    assert recall_at_k(ranked, grades, k=3) == 1.0
    assert mean_reciprocal_rank_at_k(ranked, grades, k=3) == 0.5
    assert ndcg_at_k(ranked, grades, k=3) == pytest.approx(0.6590018)
    assert evidence_coverage_at_k(
        ranked,
        evidence_by_chunk,
        expected_evidence_ids={"e1", "e2"},
        k=3,
    ) == 1.0


def test_cluster_bootstrap_is_seed_deterministic():
    rows = [
        {"intentId": "a", "baseline": 0.2, "candidate": 0.4},
        {"intentId": "a", "baseline": 0.3, "candidate": 0.5},
        {"intentId": "b", "baseline": 0.7, "candidate": 0.6},
        {"intentId": "b", "baseline": 0.8, "candidate": 0.7},
    ]

    first = cluster_bootstrap_delta(rows, seed=20260620, resamples=1000)
    second = cluster_bootstrap_delta(rows, seed=20260620, resamples=1000)

    assert first == second
    assert first.intent_count == 2
    assert first.win_count == 1
    assert first.loss_count == 1


def test_slice_summary_keeps_lexical_and_semantic_separate():
    rows = [
        {"surfaceForm": "LEXICAL_ALIGNED", "language": "ko", "ndcgAt5": 1.0},
        {"surfaceForm": "SEMANTIC_PARAPHRASE", "language": "en", "ndcgAt5": 0.5},
    ]

    summary = summarize_slices(rows, metric_name="ndcgAt5")

    assert summary["surface:LEXICAL_ALIGNED"] == 1.0
    assert summary["surface:SEMANTIC_PARAPHRASE"] == 0.5
    assert summary["language:en"] == 0.5
```

- [ ] **Step 2: Run focused tests and confirm RED**

Run:

```bash
.venv/bin/python -m pytest tests/test_metrics.py
```

Expected: FAIL because the metric module does not exist.

- [ ] **Step 3: Add metric result models**

Append to `rag_evaluation/models.py`:

```python
class QueryRetrievalMetrics(StrictModel):
    query_id: str
    intent_id: str
    chunk_strategy: ChunkStrategy
    retrieval_system: RetrievalSystem
    recall_at_5: float
    mrr_at_5: float
    ndcg_at_5: float
    evidence_coverage_at_5: float
    latency_ms: float


class BootstrapInterval(StrictModel):
    mean_delta: float
    lower_95: float
    upper_95: float
    intent_count: int
    win_count: int
    tie_count: int
    loss_count: int
    seed: int
    resamples: int
```

- [ ] **Step 4: Implement deterministic retrieval metrics**

Create `rag_evaluation/metrics.py`:

```python
from __future__ import annotations

import math
from collections import defaultdict

import numpy as np

from rag_evaluation.models import BootstrapInterval


def recall_at_k(ranked: list[str], grades: dict[str, int], *, k: int) -> float:
    return 1.0 if any(grades.get(chunk_id, 0) > 0 for chunk_id in ranked[:k]) else 0.0


def mean_reciprocal_rank_at_k(
    ranked: list[str],
    grades: dict[str, int],
    *,
    k: int,
) -> float:
    for index, chunk_id in enumerate(ranked[:k]):
        if grades.get(chunk_id, 0) == 2:
            return 1.0 / (index + 1)
    return 0.0


def ndcg_at_k(ranked: list[str], grades: dict[str, int], *, k: int) -> float:
    gains = [grades.get(chunk_id, 0) for chunk_id in ranked[:k]]
    dcg = sum((2**grade - 1) / math.log2(index + 2) for index, grade in enumerate(gains))
    ideal = sorted(grades.values(), reverse=True)[:k]
    idcg = sum((2**grade - 1) / math.log2(index + 2) for index, grade in enumerate(ideal))
    return dcg / idcg if idcg else 0.0


def evidence_coverage_at_k(
    ranked: list[str],
    evidence_by_chunk: dict[str, set[str]],
    *,
    expected_evidence_ids: set[str],
    k: int,
) -> float:
    if not expected_evidence_ids:
        return 0.0
    found = set()
    for chunk_id in ranked[:k]:
        found.update(evidence_by_chunk.get(chunk_id, set()))
    return len(found & expected_evidence_ids) / len(expected_evidence_ids)


def cluster_bootstrap_delta(
    rows: list[dict],
    *,
    seed: int,
    resamples: int,
) -> BootstrapInterval:
    by_intent: dict[str, list[float]] = defaultdict(list)
    for row in rows:
        by_intent[row["intentId"]].append(row["candidate"] - row["baseline"])
    intent_deltas = {
        intent_id: float(np.mean(values))
        for intent_id, values in by_intent.items()
    }
    values = np.asarray(list(intent_deltas.values()), dtype=float)
    rng = np.random.default_rng(seed)
    samples = np.asarray(
        [
            float(np.mean(rng.choice(values, size=len(values), replace=True)))
            for _ in range(resamples)
        ]
    )
    epsilon = 1e-12
    return BootstrapInterval(
        meanDelta=float(np.mean(values)),
        lower95=float(np.quantile(samples, 0.025)),
        upper95=float(np.quantile(samples, 0.975)),
        intentCount=len(values),
        winCount=sum(value > epsilon for value in values),
        tieCount=sum(abs(value) <= epsilon for value in values),
        lossCount=sum(value < -epsilon for value in values),
        seed=seed,
        resamples=resamples,
    )


def summarize_slices(rows: list[dict], *, metric_name: str) -> dict[str, float]:
    groups: dict[str, list[float]] = defaultdict(list)
    for row in rows:
        groups["overall"].append(row[metric_name])
        groups[f"surface:{row['surfaceForm']}"].append(row[metric_name])
        groups[f"language:{row['language']}"].append(row[metric_name])
        if row.get("queryType"):
            groups[f"queryType:{row['queryType']}"].append(row[metric_name])
        if row.get("evidenceShape"):
            groups[f"evidenceShape:{row['evidenceShape']}"].append(row[metric_name])
    return {name: float(np.mean(values)) for name, values in groups.items()}
```

- [ ] **Step 5: Add p95 latency and factorial-completeness tests**

Append to `tests/test_metrics.py`:

```python
from rag_evaluation.metrics import percentile_95, validate_factorial_matrix


def test_percentile_95_uses_observed_latency_values():
    assert percentile_95([10.0, 20.0, 30.0, 40.0]) == pytest.approx(38.5)


def test_factorial_matrix_requires_eight_answerable_combinations():
    combinations = {
        ("fixed", "bm25"),
        ("fixed", "dense"),
        ("fixed", "hybrid"),
        ("fixed", "hybrid_rerank"),
        ("heading", "bm25"),
        ("heading", "dense"),
        ("heading", "hybrid"),
        ("heading", "hybrid_rerank"),
    }

    validate_factorial_matrix(combinations)

    with pytest.raises(ValueError, match="factorial matrix"):
        validate_factorial_matrix(combinations - {("heading", "dense")})
```

Add to `metrics.py`:

```python
def percentile_95(values: list[float]) -> float:
    return float(np.quantile(np.asarray(values, dtype=float), 0.95))


def validate_factorial_matrix(combinations: set[tuple[str, str]]) -> None:
    expected = {
        (chunking, retrieval)
        for chunking in ("fixed", "heading")
        for retrieval in ("bm25", "dense", "hybrid", "hybrid_rerank")
    }
    if combinations != expected:
        raise ValueError("factorial matrix must contain all eight combinations")
```

- [ ] **Step 6: Run focused tests and confirm GREEN**

Run:

```bash
.venv/bin/python -m pytest tests/test_metrics.py
```

Expected: `5 passed`.

- [ ] **Step 7: Commit**

```bash
git add experiments/rag-evaluation
git commit -m "feat: add paired retrieval evaluation metrics"
```

---

### Task 8: Implement Structured OpenAI Calls And Reranking Fallback

**Files:**

- Create: `experiments/rag-evaluation/prompts/reranker-v1.md`
- Create: `experiments/rag-evaluation/rag_evaluation/openai_client.py`
- Create: `experiments/rag-evaluation/rag_evaluation/reranking.py`
- Create: `experiments/rag-evaluation/tests/test_reranking.py`

- [ ] **Step 1: Write the reranker prompt**

Create `prompts/reranker-v1.md`:

```markdown
You rerank candidate chunks for an operations-manual question.

Rules:
- Use only the supplied candidate IDs.
- Rank direct answer evidence before supporting context.
- Preserve all candidate IDs exactly once.
- Do not answer the question.
- Return concise reasons tied to the candidate text.
```

- [ ] **Step 2: Write failing normalization and fallback tests**

Create `tests/test_reranking.py`:

```python
from rag_evaluation.models import RankedChunk
from rag_evaluation.reranking import (
    RerankerOutput,
    normalize_reranker_output,
    rerank_with_retry,
)


def candidates() -> list[RankedChunk]:
    return [
        RankedChunk(chunkId="c1", score=0.9, rank=1),
        RankedChunk(chunkId="c2", score=0.8, rank=2),
        RankedChunk(chunkId="c3", score=0.7, rank=3),
    ]


def test_normalization_removes_duplicates_and_appends_missing_candidates():
    output = RerankerOutput(
        orderedChunkIds=["c2", "c2", "c1"],
        reasonByChunkId={"c2": "direct", "c1": "support"},
    )

    normalized = normalize_reranker_output(candidates(), output)

    assert [item.chunk_id for item in normalized.ranked_chunks] == ["c2", "c1", "c3"]
    assert normalized.invalid_ids == []
    assert normalized.duplicate_ids == ["c2"]


def test_reranker_retries_once_then_falls_back_to_hybrid_order():
    calls = 0

    def invalid_call() -> RerankerOutput:
        nonlocal calls
        calls += 1
        return RerankerOutput(
            orderedChunkIds=["unknown"],
            reasonByChunkId={"unknown": "bad"},
        )

    result = rerank_with_retry(candidates(), invalid_call)

    assert calls == 2
    assert result.fallback_used is True
    assert [item.chunk_id for item in result.ranked_chunks] == ["c1", "c2", "c3"]
```

- [ ] **Step 3: Run focused tests and confirm RED**

Run:

```bash
.venv/bin/python -m pytest tests/test_reranking.py
```

Expected: FAIL because reranking types and functions do not exist.

- [ ] **Step 4: Add structured OpenAI response models**

Append to `rag_evaluation/models.py`:

```python
class RerankerOutput(StrictModel):
    ordered_chunk_ids: list[str]
    reason_by_chunk_id: dict[str, str]


class RerankResult(StrictModel):
    ranked_chunks: list[RankedChunk]
    invalid_ids: list[str]
    duplicate_ids: list[str]
    fallback_used: bool
    retry_count: int
    input_tokens: int
    output_tokens: int
```

- [ ] **Step 5: Implement a small Responses API wrapper**

Create `rag_evaluation/openai_client.py`:

```python
from __future__ import annotations

from pathlib import Path
from typing import TypeVar

from openai import OpenAI
from pydantic import BaseModel

T = TypeVar("T", bound=BaseModel)


class StructuredOpenAIClient:
    def __init__(self, *, api_key: str, client: OpenAI | None = None) -> None:
        self.client = client or OpenAI(api_key=api_key)

    def parse(
        self,
        *,
        model: str,
        system_prompt: str,
        user_prompt: str,
        schema: type[T],
    ) -> tuple[T, int, int]:
        response = self.client.responses.parse(
            model=model,
            input=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
            text_format=schema,
        )
        if response.output_parsed is None:
            refusal = next(
                (
                    item.refusal
                    for output in response.output
                    if getattr(output, "type", None) == "message"
                    for item in output.content
                    if getattr(item, "type", None) == "refusal"
                ),
                None,
            )
            raise ValueError(f"OpenAI structured output missing; refusal={refusal}")
        usage = response.usage
        return (
            response.output_parsed,
            int(usage.input_tokens if usage else 0),
            int(usage.output_tokens if usage else 0),
        )


def load_prompt(path: Path) -> str:
    return path.read_text(encoding="utf-8").strip()
```

- [ ] **Step 6: Implement normalization and bounded retry**

Create `rag_evaluation/reranking.py`:

```python
from __future__ import annotations

from collections.abc import Callable

from rag_evaluation.models import (
    RankedChunk,
    RerankResult,
    RerankerOutput,
)


def normalize_reranker_output(
    candidates: list[RankedChunk],
    output: RerankerOutput,
) -> RerankResult:
    allowed = {candidate.chunk_id for candidate in candidates}
    seen: set[str] = set()
    ordered: list[str] = []
    invalid: list[str] = []
    duplicates: list[str] = []
    for chunk_id in output.ordered_chunk_ids:
        if chunk_id not in allowed:
            invalid.append(chunk_id)
            continue
        if chunk_id in seen:
            duplicates.append(chunk_id)
            continue
        seen.add(chunk_id)
        ordered.append(chunk_id)
    ordered.extend(candidate.chunk_id for candidate in candidates if candidate.chunk_id not in seen)
    by_id = {candidate.chunk_id: candidate for candidate in candidates}
    ranked = [
        RankedChunk(chunkId=chunk_id, score=by_id[chunk_id].score, rank=index + 1)
        for index, chunk_id in enumerate(ordered)
    ]
    return RerankResult(
        rankedChunks=ranked,
        invalidIds=invalid,
        duplicateIds=duplicates,
        fallbackUsed=False,
        retryCount=0,
        inputTokens=0,
        outputTokens=0,
    )


def rerank_with_retry(
    candidates: list[RankedChunk],
    call: Callable[[], RerankerOutput],
) -> RerankResult:
    for attempt in range(2):
        normalized = normalize_reranker_output(candidates, call())
        if not normalized.invalid_ids:
            return normalized.model_copy(update={"retry_count": attempt})
    return RerankResult(
        rankedChunks=candidates,
        invalidIds=normalized.invalid_ids,
        duplicateIds=normalized.duplicate_ids,
        fallbackUsed=True,
        retryCount=1,
        inputTokens=0,
        outputTokens=0,
    )
```

The production reranker adapter must:

- serialize query plus top-10 chunk IDs, heading paths, and content
- call `StructuredOpenAIClient.parse` with `schema=RerankerOutput`
- aggregate input/output tokens across retry
- mark `RERANK_INVALID_OUTPUT` on invalid IDs
- mark `RERANK_REGRESSION` when evidence rank worsens after reranking

- [ ] **Step 7: Run focused tests and confirm GREEN**

Run:

```bash
.venv/bin/python -m pytest tests/test_reranking.py
```

Expected: `2 passed`.

- [ ] **Step 8: Commit**

```bash
git add experiments/rag-evaluation
git commit -m "feat: add structured RAG reranking"
```

---

### Task 9: Implement Five Generation Variants And Deterministic Grounding Checks

**Files:**

- Create: `experiments/rag-evaluation/prompts/generator-v1.md`
- Create: `experiments/rag-evaluation/rag_evaluation/generation.py`
- Create: `experiments/rag-evaluation/tests/test_generation.py`

- [ ] **Step 1: Write the grounded generation prompt**

Create `prompts/generator-v1.md`:

```markdown
You answer questions about the supplied Sigak operations documents.

Rules:
- Use only the supplied context for RAG variants.
- If the context does not support the answer, set answerable=false.
- Do not invent SLA, limits, deployment topology, credentials, or commands.
- Write the answer in expectedResponseLanguage.
- For an answerable RAG response, cite at least one supplied chunk ID.
- Every citation may reference only evidence IDs declared for that chunk.
- NO_RAG receives no context and must return citations=[].
```

- [ ] **Step 2: Write failing schema and deterministic-check tests**

Create `tests/test_generation.py`:

```python
from rag_evaluation.generation import validate_generated_answer
from rag_evaluation.models import (
    Citation,
    GeneratedAnswer,
    GenerationVariant,
)


def test_answerable_rag_requires_valid_candidate_citation():
    answer = GeneratedAnswer(
        answerable=True,
        answer="Keyword-only mode를 사용합니다.",
        citations=[Citation(chunkId="c1", evidenceIds=["e1"])],
    )

    result = validate_generated_answer(
        variant=GenerationVariant.HEADING_HYBRID_RAG,
        answer=answer,
        expected_answerable=True,
        candidate_chunk_ids={"c1"},
        evidence_ids_by_chunk={"c1": {"e1"}},
        expected_evidence_ids={"e1"},
    )

    assert result.valid is True
    assert result.evidence_coverage == 1.0


def test_invalid_citation_and_unknown_evidence_are_reported():
    answer = GeneratedAnswer(
        answerable=True,
        answer="Unsupported.",
        citations=[Citation(chunkId="unknown", evidenceIds=["e2"])],
    )

    result = validate_generated_answer(
        variant=GenerationVariant.FIXED_HYBRID_RERANK_RAG,
        answer=answer,
        expected_answerable=True,
        candidate_chunk_ids={"c1"},
        evidence_ids_by_chunk={"c1": {"e1"}},
        expected_evidence_ids={"e1"},
    )

    assert result.valid is False
    assert "unknown" in result.invalid_chunk_ids
    assert result.evidence_coverage == 0.0


def test_no_rag_rejects_citations_and_unanswerable_query_rewards_refusal():
    answer = GeneratedAnswer(
        answerable=False,
        answer="문서에서 확인할 수 없습니다.",
        citations=[],
    )

    result = validate_generated_answer(
        variant=GenerationVariant.NO_RAG,
        answer=answer,
        expected_answerable=False,
        candidate_chunk_ids=set(),
        evidence_ids_by_chunk={},
        expected_evidence_ids=set(),
    )

    assert result.valid is True
    assert result.refusal_correct is True
```

- [ ] **Step 3: Run focused tests and confirm RED**

Run:

```bash
.venv/bin/python -m pytest tests/test_generation.py
```

Expected: FAIL because generation schemas and validator do not exist.

- [ ] **Step 4: Add generation schemas**

Append to `rag_evaluation/models.py`:

```python
class GenerationVariant(StrEnum):
    NO_RAG = "NO_RAG"
    FIXED_HYBRID_RAG = "FIXED_HYBRID_RAG"
    FIXED_HYBRID_RERANK_RAG = "FIXED_HYBRID_RERANK_RAG"
    HEADING_HYBRID_RAG = "HEADING_HYBRID_RAG"
    HEADING_HYBRID_RERANK_RAG = "HEADING_HYBRID_RERANK_RAG"


class Citation(StrictModel):
    chunk_id: str
    evidence_ids: list[str]


class GeneratedAnswer(StrictModel):
    answerable: bool
    answer: str
    citations: list[Citation]


class DeterministicGenerationCheck(StrictModel):
    valid: bool
    schema_valid: bool
    citation_constraint_valid: bool
    invalid_chunk_ids: list[str]
    invalid_evidence_ids: list[str]
    duplicate_citations: list[str]
    evidence_coverage: float
    refusal_correct: bool


class GenerationRun(StrictModel):
    query_id: str
    variant: GenerationVariant
    status: RunStatus
    answer: GeneratedAnswer | None
    deterministic_check: DeterministicGenerationCheck | None
    latency_ms: float
    input_tokens: int
    output_tokens: int
    estimated_cost_usd: str | None
    failure_reason: str | None = None
```

- [ ] **Step 5: Implement deterministic validation**

Create `rag_evaluation/generation.py`:

```python
from __future__ import annotations

from collections import Counter

from rag_evaluation.models import (
    DeterministicGenerationCheck,
    GeneratedAnswer,
    GenerationVariant,
)


def validate_generated_answer(
    *,
    variant: GenerationVariant,
    answer: GeneratedAnswer,
    expected_answerable: bool,
    candidate_chunk_ids: set[str],
    evidence_ids_by_chunk: dict[str, set[str]],
    expected_evidence_ids: set[str],
) -> DeterministicGenerationCheck:
    citation_keys = [
        f"{citation.chunk_id}:{','.join(citation.evidence_ids)}"
        for citation in answer.citations
    ]
    duplicate_citations = [
        key for key, count in Counter(citation_keys).items() if count > 1
    ]
    invalid_chunk_ids = sorted(
        {
            citation.chunk_id
            for citation in answer.citations
            if citation.chunk_id not in candidate_chunk_ids
        }
    )
    invalid_evidence_ids = sorted(
        {
            evidence_id
            for citation in answer.citations
            for evidence_id in citation.evidence_ids
            if evidence_id not in evidence_ids_by_chunk.get(citation.chunk_id, set())
        }
    )
    cited_expected = {
        evidence_id
        for citation in answer.citations
        for evidence_id in citation.evidence_ids
        if evidence_id in expected_evidence_ids
    }
    coverage = (
        len(cited_expected) / len(expected_evidence_ids)
        if expected_evidence_ids
        else 0.0
    )
    if variant is GenerationVariant.NO_RAG:
        citation_constraint_valid = not answer.citations
    elif answer.answerable:
        citation_constraint_valid = bool(answer.citations)
    else:
        citation_constraint_valid = True
    refusal_correct = answer.answerable == expected_answerable
    valid = (
        citation_constraint_valid
        and not invalid_chunk_ids
        and not invalid_evidence_ids
        and not duplicate_citations
        and refusal_correct
    )
    return DeterministicGenerationCheck(
        valid=valid,
        schemaValid=True,
        citationConstraintValid=citation_constraint_valid,
        invalidChunkIds=invalid_chunk_ids,
        invalidEvidenceIds=invalid_evidence_ids,
        duplicateCitations=duplicate_citations,
        evidenceCoverage=coverage,
        refusalCorrect=refusal_correct,
    )
```

- [ ] **Step 6: Implement context serialization and five-variant mapping**

Continue `generation.py`:

```python
VARIANT_SOURCE = {
    GenerationVariant.NO_RAG: (None, None),
    GenerationVariant.FIXED_HYBRID_RAG: ("fixed", "hybrid"),
    GenerationVariant.FIXED_HYBRID_RERANK_RAG: ("fixed", "hybrid_rerank"),
    GenerationVariant.HEADING_HYBRID_RAG: ("heading", "hybrid"),
    GenerationVariant.HEADING_HYBRID_RERANK_RAG: ("heading", "hybrid_rerank"),
}


def build_generation_user_prompt(
    *,
    query: str,
    expected_response_language: str,
    chunks: list[dict],
) -> str:
    context = "\n\n".join(
        (
            f"[chunkId={chunk['chunkId']}]\n"
            f"heading={' > '.join(chunk['headingPath'])}\n"
            f"evidenceIds={','.join(chunk['evidenceIds'])}\n"
            f"{chunk['content']}"
        )
        for chunk in chunks
    )
    return (
        f"query={query}\n"
        f"expectedResponseLanguage={expected_response_language}\n"
        f"context:\n{context if context else '(no context)'}"
    )
```

The runner must execute all five variants for all 60 queries. It must not choose a
“best” retrieval configuration after viewing the retrieval metrics.

- [ ] **Step 7: Run focused tests and confirm GREEN**

Run:

```bash
.venv/bin/python -m pytest tests/test_generation.py
```

Expected: `3 passed`.

- [ ] **Step 8: Commit**

```bash
git add experiments/rag-evaluation
git commit -m "feat: add grounded RAG generation checks"
```

---

### Task 10: Implement Blind LLM Judging And The 50-Item Human Calibration Gate

**Files:**

- Create: `experiments/rag-evaluation/prompts/judge-v1.md`
- Create: `experiments/rag-evaluation/datasets/human-review.json`
- Create: `experiments/rag-evaluation/rag_evaluation/judging.py`
- Create: `experiments/rag-evaluation/tests/test_judging.py`

- [ ] **Step 1: Write the judge rubric**

Create `prompts/judge-v1.md`:

```markdown
You evaluate one answer to one operations-manual question.

You do not know the generation variant or retrieval system.

Score each dimension from 0 to 4:
- correctness: matches the expected answer
- faithfulness: every factual claim is supported by cited evidence
- completeness: covers the necessary parts without irrelevant expansion
- citationSupport: citations actually support the claims

Verdicts:
- SUPPORTED
- PARTIALLY_SUPPORTED
- UNSUPPORTED
- CORRECT_REFUSAL
- INCORRECT_REFUSAL

List every unsupported claim separately. Do not reward fluent wording.
```

- [ ] **Step 2: Write failing judge and agreement tests**

Create `tests/test_judging.py`:

```python
import pytest

from rag_evaluation.judging import (
    build_blind_judge_items,
    calculate_human_agreement,
    validate_human_review,
)


def test_blind_items_shuffle_variants_without_exposing_variant_name():
    items = build_blind_judge_items(
        query_id="q1",
        rows=[
            {"variant": "NO_RAG", "answer": "a"},
            {"variant": "HEADING_HYBRID_RAG", "answer": "b"},
        ],
        seed=20260620,
    )

    assert {item["answer"] for item in items} == {"a", "b"}
    assert all("variant" not in item for item in items)
    assert all("blindId" in item for item in items)


def test_human_review_requires_ten_queries_and_five_variants_each():
    rows = [
        {
            "queryId": f"q{query_index}",
            "variant": f"v{variant_index}",
            "reviewStatus": "REVIEWED",
            "verdict": "SUPPORTED",
            "correctness": 4,
            "faithfulness": 4,
            "completeness": 4,
            "unsupportedClaim": False,
        }
        for query_index in range(10)
        for variant_index in range(5)
    ]

    validate_human_review(rows)

    with pytest.raises(ValueError, match="50 reviewed"):
        validate_human_review(rows[:-1])


def test_agreement_reports_exact_verdict_and_score_difference():
    agreement = calculate_human_agreement(
        [
            {
                "humanVerdict": "SUPPORTED",
                "judgeVerdict": "SUPPORTED",
                "humanCorrectness": 4,
                "judgeCorrectness": 3,
            },
            {
                "humanVerdict": "UNSUPPORTED",
                "judgeVerdict": "PARTIALLY_SUPPORTED",
                "humanCorrectness": 1,
                "judgeCorrectness": 2,
            },
        ]
    )

    assert agreement["exactVerdictAgreement"] == 0.5
    assert agreement["meanAbsoluteScoreDifference"] == 1.0
```

- [ ] **Step 3: Run focused tests and confirm RED**

Run:

```bash
.venv/bin/python -m pytest tests/test_judging.py
```

Expected: FAIL because judging functions do not exist.

- [ ] **Step 4: Add judge and human-review schemas**

Append to `rag_evaluation/models.py`:

```python
class JudgeVerdict(StrEnum):
    SUPPORTED = "SUPPORTED"
    PARTIALLY_SUPPORTED = "PARTIALLY_SUPPORTED"
    UNSUPPORTED = "UNSUPPORTED"
    CORRECT_REFUSAL = "CORRECT_REFUSAL"
    INCORRECT_REFUSAL = "INCORRECT_REFUSAL"


class UnsupportedClaim(StrictModel):
    claim: str
    reason: str


class JudgeResult(StrictModel):
    correctness: int = Field(ge=0, le=4)
    faithfulness: int = Field(ge=0, le=4)
    completeness: int = Field(ge=0, le=4)
    citation_support: int = Field(ge=0, le=4)
    unsupported_claims: list[UnsupportedClaim]
    verdict: JudgeVerdict
    rationale: str


class JudgeRun(StrictModel):
    query_id: str
    blind_id: str
    status: RunStatus
    result: JudgeResult | None
    latency_ms: float
    input_tokens: int
    output_tokens: int
    failure_reason: str | None = None
```

- [ ] **Step 5: Implement blind ordering, review validation, and agreement**

Create `rag_evaluation/judging.py`:

```python
from __future__ import annotations

import hashlib
import random

import numpy as np


def build_blind_judge_items(
    *,
    query_id: str,
    rows: list[dict],
    seed: int,
) -> list[dict]:
    shuffled = list(rows)
    random.Random(f"{seed}:{query_id}").shuffle(shuffled)
    return [
        {
            "blindId": hashlib.sha256(
                f"{query_id}:{index}:{row['answer']}".encode("utf-8")
            ).hexdigest()[:16],
            "answer": row["answer"],
            "citations": row.get("citations", []),
        }
        for index, row in enumerate(shuffled)
    ]


def validate_human_review(rows: list[dict]) -> None:
    reviewed = [row for row in rows if row["reviewStatus"] == "REVIEWED"]
    if len(reviewed) < 50:
        raise ValueError("human calibration requires at least 50 reviewed answers")
    query_ids = {row["queryId"] for row in reviewed}
    if len(query_ids) < 10:
        raise ValueError("human calibration requires at least 10 distinct queries")
    for query_id in query_ids:
        variants = {row["variant"] for row in reviewed if row["queryId"] == query_id}
        if len(variants) != 5:
            raise ValueError(f"query {query_id} must review all five variants")


def calculate_human_agreement(rows: list[dict]) -> dict[str, float]:
    return {
        "exactVerdictAgreement": sum(
            row["humanVerdict"] == row["judgeVerdict"] for row in rows
        )
        / len(rows),
        "meanAbsoluteScoreDifference": float(
            np.mean(
                [
                    abs(row["humanCorrectness"] - row["judgeCorrectness"])
                    for row in rows
                ]
            )
        ),
    }
```

- [ ] **Step 6: Create a review file with the exact selection policy**

Create `datasets/human-review.json` initially with:

```json
{
  "reviewer": "project-owner",
  "reviewedAt": null,
  "selection": {
    "lexicalQueryIds": [],
    "semanticQueryIds": [],
    "crossLingualQueryIds": [],
    "retrievalMissOrRerankRegressionQueryIds": [],
    "incorrectRefusalQueryIds": [],
    "unanswerableQueryIds": []
  },
  "items": [],
  "agreement": null,
  "disagreements": []
}
```

After generation results exist, select exactly 10 distinct queries:

- 2 lexical-aligned
- 2 semantic-paraphrase
- 2 English/cross-lingual
- 1 retrieval miss or rerank regression
- 1 incorrect refusal on an answerable query
- 2 unanswerable

If no incorrect refusal exists, select the lowest-correctness answerable query and state that
the intended failure slice was unavailable. Review all five variants for each selected query.

- [ ] **Step 7: Run focused tests and confirm GREEN**

Run:

```bash
.venv/bin/python -m pytest tests/test_judging.py
```

Expected: `3 passed`.

- [ ] **Step 8: Commit**

```bash
git add experiments/rag-evaluation
git commit -m "feat: add calibrated LLM judge workflow"
```

---

### Task 11: Orchestrate Runs, Persist Partial Artifacts, And Build The Honest Report

**Files:**

- Create: `experiments/rag-evaluation/rag_evaluation/artifacts.py`
- Create: `experiments/rag-evaluation/rag_evaluation/report.py`
- Create: `experiments/rag-evaluation/rag_evaluation/runner.py`
- Create: `experiments/rag-evaluation/rag_evaluation/cli.py`
- Create: `experiments/rag-evaluation/tests/test_artifacts.py`
- Create: `experiments/rag-evaluation/tests/test_report.py`
- Create: `experiments/rag-evaluation/tests/test_runner.py`
- Create: `experiments/rag-evaluation/tests/test_cli.py`

- [ ] **Step 1: Write failing atomic artifact tests**

Create `tests/test_artifacts.py`:

```python
import json

from rag_evaluation.artifacts import ArtifactWriter


def test_artifact_writer_uses_aliases_and_atomic_replace(tmp_path):
    writer = ArtifactWriter(tmp_path)

    path = writer.write_json("sample.json", {"runId": "r1", "items": [1]})

    assert json.loads(path.read_text(encoding="utf-8")) == {
        "runId": "r1",
        "items": [1],
    }
    assert not (tmp_path / "sample.json.tmp").exists()
```

- [ ] **Step 2: Write failing report interpretation tests**

Create `tests/test_report.py`:

```python
from rag_evaluation.report import describe_chunking_delta, render_report


def test_chunking_claim_is_conservative_when_interval_includes_zero():
    claim = describe_chunking_delta(
        mean_delta=0.03,
        lower_95=-0.01,
        upper_95=0.07,
    )

    assert "명확한 우위가 관찰되지 않았다" in claim


def test_report_contains_required_limitations_and_failures():
    report = render_report(
        run_manifest={"runId": "r1", "workingTreeDirty": False},
        corpus_summary={"documentCount": 7},
        dataset_summary={
            "answerableIntentCount": 24,
            "answerableQueryCount": 48,
            "unanswerableQueryCount": 12,
        },
        retrieval_summary={"rows": []},
        generation_summary={"rows": []},
        human_summary={"reviewedCount": 50},
        failures=[{"failureType": "RETRIEVAL_MISS"}],
    )

    assert "## Limitations" in report
    assert "단일 Sigak 운영 문서 corpus" in report
    assert "RETRIEVAL_MISS" in report
    assert "English Summary" in report
```

- [ ] **Step 3: Write failing runner smoke test with fakes**

Create `tests/test_runner.py`:

```python
from pathlib import Path

from rag_evaluation.config import EvaluationSettings
from rag_evaluation.runner import EvaluationRunner


def test_smoke_runner_writes_two_chunkers_three_retrievers_and_report(
    tmp_path,
    monkeypatch,
):
    def fake_retrieval_stage(paths, settings, writer, failures):
        writer.write_json("retrieval.runs.json", [{"queryId": "q1"}])
        writer.write_text("report.md", "# smoke")
        return type(
            "Summary",
            (),
            {
                "query_count": 3,
                "chunking_strategies": ["fixed", "heading"],
                "retrieval_systems": ["bm25", "dense", "hybrid"],
            },
        )()

    monkeypatch.setattr(
        "rag_evaluation.runner.evaluate_retrieval_stage",
        fake_retrieval_stage,
    )
    runner = EvaluationRunner.from_paths(
        repository_root=Path(__file__).parent / "fixtures" / "corpus",
        dataset_path=Path("smoke.json"),
        corpus_manifest_path=Path("manifest.json"),
        output_dir=tmp_path,
        settings=EvaluationSettings.from_env(),
    )

    summary = runner.evaluate_retrieval()

    assert summary.query_count == 3
    assert summary.chunking_strategies == ["fixed", "heading"]
    assert summary.retrieval_systems == ["bm25", "dense", "hybrid"]
    assert (tmp_path / "retrieval.runs.json").exists()
    assert (tmp_path / "report.md").exists()
```

- [ ] **Step 4: Write failing CLI parsing tests**

Create `tests/test_cli.py`:

```python
import pytest

from rag_evaluation.cli import parse_args


def test_cli_parses_full_run_contract():
    args = parse_args(
        [
            "run",
            "--dataset",
            "datasets/ops-questions.json",
            "--corpus-manifest",
            "datasets/corpus-manifest.json",
            "--output",
            "results/latest",
        ]
    )

    assert args.command == "run"
    assert args.output == "results/latest"


def test_cli_requires_dataset_and_manifest_for_retrieval():
    with pytest.raises(SystemExit):
        parse_args(["evaluate-retrieval", "--output", "results/latest"])
```

- [ ] **Step 5: Run focused tests and confirm RED**

Run:

```bash
.venv/bin/python -m pytest \
  tests/test_artifacts.py \
  tests/test_report.py \
  tests/test_runner.py \
  tests/test_cli.py
```

Expected: FAIL because orchestration modules do not exist.

- [ ] **Step 6: Implement atomic artifact writing**

Create `rag_evaluation/artifacts.py`:

```python
from __future__ import annotations

import json
from pathlib import Path

from pydantic import BaseModel


class ArtifactWriter:
    def __init__(self, output_dir: Path) -> None:
        self.output_dir = output_dir
        self.output_dir.mkdir(parents=True, exist_ok=True)

    def write_json(self, filename: str, value) -> Path:
        path = self.output_dir / filename
        temporary = path.with_suffix(path.suffix + ".tmp")
        payload = (
            value.model_dump(by_alias=True)
            if isinstance(value, BaseModel)
            else value
        )
        temporary.write_text(
            json.dumps(payload, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        temporary.replace(path)
        return path

    def write_text(self, filename: str, value: str) -> Path:
        path = self.output_dir / filename
        temporary = path.with_suffix(path.suffix + ".tmp")
        temporary.write_text(value, encoding="utf-8")
        temporary.replace(path)
        return path
```

- [ ] **Step 7: Implement conservative report language**

Create `rag_evaluation/report.py`:

```python
from __future__ import annotations


def describe_chunking_delta(
    *,
    mean_delta: float,
    lower_95: float,
    upper_95: float,
) -> str:
    if lower_95 <= 0 <= upper_95:
        return (
            "paired intent-cluster bootstrap 95% 신뢰구간이 0을 포함해 "
            "두 chunking 전략 사이의 명확한 우위가 관찰되지 않았다."
        )
    direction = "높게" if mean_delta > 0 else "낮게"
    return (
        "Sigak 운영 문서와 이번 query set에서 heading-aware nDCG@5가 "
        f"fixed-size보다 {direction} 관찰됐다. 다른 corpus로 일반화하지 않는다."
    )


def render_report(
    *,
    run_manifest: dict,
    corpus_summary: dict,
    dataset_summary: dict,
    retrieval_summary: dict,
    generation_summary: dict,
    human_summary: dict,
    failures: list[dict],
) -> str:
    failure_lines = "\n".join(
        f"- `{failure['failureType']}`" for failure in failures
    ) or "- 기록된 실패 없음"
    return f"""# Sigak Ops RAG Evaluation Report

## Run

- runId: `{run_manifest['runId']}`
- workingTreeDirty: `{run_manifest['workingTreeDirty']}`
- corpus documents: `{corpus_summary['documentCount']}`
- answerable intents: `{dataset_summary['answerableIntentCount']}`
- answerable queries: `{dataset_summary['answerableQueryCount']}`
- unanswerable queries: `{dataset_summary['unanswerableQueryCount']}`

## Retrieval

The primary metric is nDCG@5. Overall, lexical, semantic, cross-lingual,
query-type, and evidence-shape slices are reported together.

## Generation And Grounding

Five fixed variants are compared. Citation validity and refusal correctness are
deterministic checks; judge scores are supporting evidence only.

## Human Calibration

Reviewed answer count: `{human_summary['reviewedCount']}`

## Failures

{failure_lines}

## Limitations

- 단일 Sigak 운영 문서 corpus의 결과이므로 다른 운영·로봇 도메인으로 일반화하지 않는다.
- 24개 answerable intent와 12개 unanswerable query는 비교용으로 충분히 검토했지만 대규모 benchmark는 아니다.
- LLM judge는 사람 평가를 대체하지 않으며 disagreement를 별도 기록한다.
- 실제 수치가 없는 개선 주장은 하지 않는다.

## English Summary

This experiment compares structure-aware and fixed-size chunking across lexical,
dense, hybrid, and reranked retrieval, then checks whether retrieval differences
transfer to grounded answers. Results are limited to this versioned Sigak operations
corpus and reviewed query set.
"""
```

- [ ] **Step 8: Implement the runner in four explicit stages**

Create `rag_evaluation/runner.py` with a focused coordinator:

```python
from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

from rag_evaluation.artifacts import ArtifactWriter
from rag_evaluation.config import EvaluationSettings


@dataclass(frozen=True)
class RunnerPaths:
    repository_root: Path
    dataset_path: Path | None
    corpus_manifest_path: Path | None
    output_dir: Path


class EvaluationRunner:
    def __init__(
        self,
        *,
        paths: RunnerPaths,
        settings: EvaluationSettings,
    ) -> None:
        self.paths = paths
        self.settings = settings
        self.writer = ArtifactWriter(paths.output_dir)
        self.failures: list[dict] = []

    @classmethod
    def from_paths(
        cls,
        *,
        repository_root: Path,
        dataset_path: Path | None,
        corpus_manifest_path: Path | None,
        output_dir: Path,
        settings: EvaluationSettings,
    ) -> "EvaluationRunner":
        return cls(
            paths=RunnerPaths(
                repository_root=repository_root,
                dataset_path=dataset_path,
                corpus_manifest_path=corpus_manifest_path,
                output_dir=output_dir,
            ),
            settings=settings,
        )

    def build_corpus_and_chunks(self):
        return build_corpus_stage(self.paths, self.settings, self.writer, self.failures)

    def evaluate_retrieval(self):
        return evaluate_retrieval_stage(
            self.paths,
            self.settings,
            self.writer,
            self.failures,
        )

    def evaluate_generation_and_judge(self):
        return evaluate_generation_stage(
            self.paths,
            self.settings,
            self.writer,
            self.failures,
        )

    def build_report(self):
        return build_report_stage(self.paths.output_dir, self.writer)

    def run_full(self) -> None:
        self.settings.validate_full_run()
        try:
            self.build_corpus_and_chunks()
            self.evaluate_retrieval()
            self.evaluate_generation_and_judge()
        finally:
            self.writer.write_json("failures.json", self.failures)
            self.build_report()
```

In the same file, implement the four module functions called above. Their exact required
behavior is:

`build_corpus_and_chunks`:

1. read git SHA with `git rev-parse HEAD`
2. read dirty state with `git status --porcelain`
3. load and validate corpus manifest
4. build `corpus.snapshot.json`
5. create fixed and heading chunks with identical 500-token maximum
6. write both chunk artifacts
7. calculate chunk count, average/p95 token length, and duplicated-token ratio
8. recompute every normalized evidence `quoteSha256` and reject mismatches
9. validate every answerable evidence quote maps to at least one chunk for both strategies

`evaluate_retrieval`:

1. validate the dataset with `require_full` based on CLI mode
2. compute and persist measured lexical overlap in `dataset.snapshot.json` before rankings
3. build BM25 and dense indexes for both chunk strategies
4. execute BM25, dense, hybrid for answerable queries
5. execute hybrid reranking when OpenAI configuration is enabled
6. preserve failed query rows with `status=FAILED`
7. calculate query metrics, slices, latency average/p95, factorial completeness
8. calculate heading-vs-fixed and system-vs-system intent-cluster bootstrap intervals
9. exclude unanswerable rows from retrieval-superiority calculations

`evaluate_generation_and_judge`:

1. require all five variants
2. run all 60 queries for every variant
3. use the same generator model and schema across variants
4. run deterministic checks before LLM judging
5. blind variant identity and shuffle per query for the judge
6. preserve API/schema failures
7. calculate refusal accuracy, citation validity, evidence coverage, judge means, token usage
8. compute cost only when explicit prices exist

`run_full`:

1. call `settings.validate_full_run()`
2. execute the three stages in order
3. always write partial artifacts in `finally`
4. never silently omit failed rows from denominators
5. create `failures.json` from the fixed taxonomy
6. build report even for partial runs

Add these run-manifest fields:

```json
{
  "runId": "UTC timestamp safe for filenames",
  "gitCommit": "full SHA",
  "workingTreeDirty": false,
  "corpusId": "sigak-ops-2026-06-20",
  "datasetVersion": 1,
  "dataset": {
    "answerableIntentCount": 24,
    "answerableQueryCount": 48,
    "unanswerableQueryCount": 12,
    "bootstrapSeed": 20260620,
    "bootstrapResamples": 10000
  },
  "chunking": {
    "tokenizer": "o200k_base",
    "fixed": {"maxTokens": 500, "overlapTokens": 100},
    "heading": {"maxTokens": 500, "maxBoundaryOverlapTokens": 100}
  },
  "retrieval": {
    "systems": ["bm25", "dense", "hybrid", "hybrid_rerank"],
    "topK": 5,
    "candidateLimit": 10,
    "rrfK": 60,
    "bm25K1": 1.5,
    "bm25B": 0.75
  },
  "models": {
    "embedding": "configured value",
    "reranker": "configured value",
    "generator": "configured value",
    "judge": "configured value"
  },
  "promptVersions": {
    "reranker": "v1",
    "generator": "v1",
    "judge": "v1"
  }
}
```

- [ ] **Step 9: Implement CLI parsing and command dispatch**

Create `rag_evaluation/cli.py`:

```python
from __future__ import annotations

import argparse


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(prog="rag_evaluation")
    subparsers = parser.add_subparsers(dest="command", required=True)
    for command in (
        "run",
        "build-corpus",
        "evaluate-retrieval",
        "evaluate-generation",
        "build-report",
    ):
        subparser = subparsers.add_parser(command)
        subparser.add_argument("--output", required=True)
        if command in {"run", "build-corpus", "evaluate-retrieval", "evaluate-generation"}:
            subparser.add_argument("--dataset", required=True)
            subparser.add_argument("--corpus-manifest", required=True)
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    settings = EvaluationSettings.from_env()
    repository_root = Path(__file__).resolve().parents[3]
    runner = EvaluationRunner.from_paths(
        repository_root=repository_root,
        dataset_path=Path(args.dataset) if hasattr(args, "dataset") else None,
        corpus_manifest_path=(
            Path(args.corpus_manifest)
            if hasattr(args, "corpus_manifest")
            else None
        ),
        output_dir=Path(args.output),
        settings=settings,
    )
    actions = {
        "run": runner.run_full,
        "build-corpus": runner.build_corpus_and_chunks,
        "evaluate-retrieval": runner.evaluate_retrieval,
        "evaluate-generation": runner.evaluate_generation_and_judge,
        "build-report": runner.build_report,
    }
    try:
        actions[args.command]()
    except Exception as error:
        print(f"rag_evaluation failed: {error}", file=sys.stderr)
        return 1
    return 0
```

Add these imports at the top of `cli.py`:

```python
import sys
from pathlib import Path

from rag_evaluation.config import EvaluationSettings
from rag_evaluation.runner import EvaluationRunner
```

`main` returns:

- `0` when the requested stage completes
- `1` for validation, missing configuration, or execution failure after partial artifacts are written

- [ ] **Step 10: Run focused tests and confirm GREEN**

Run:

```bash
.venv/bin/python -m pytest \
  tests/test_artifacts.py \
  tests/test_report.py \
  tests/test_runner.py \
  tests/test_cli.py
```

Expected: all focused tests pass.

- [ ] **Step 11: Run the complete experiment-package test suite**

Run:

```bash
.venv/bin/python -m pytest
```

Expected: all tests pass.

- [ ] **Step 12: Commit**

```bash
git add experiments/rag-evaluation
git commit -m "feat: orchestrate reproducible ops RAG evaluation"
```

---

### Task 12: Run Smoke, Full Evaluation, Human Review, And Documentation Gates

**Files:**

- Create/Modify: `experiments/rag-evaluation/README.md`
- Generate: `experiments/rag-evaluation/results/latest/*`
- Modify: `README.md`
- Modify: `experiments/README.md`
- Modify: `docs/STATUS.md`
- Modify: `docs/STATUS.ko.md`
- Modify: `docs/ROADMAP.md`
- Modify: `docs/ROADMAP.ko.md`
- Modify: `docs/blog/2026-06-20-dev-log.md`
- Modify: `docs/blog/topic-queue.md`

- [ ] **Step 1: Document setup and interpretation before the live run**

Create `experiments/rag-evaluation/README.md` with:

````markdown
# Sigak Ops RAG Evaluation

This package evaluates a versioned public Sigak operations-document corpus.

## Setup

```bash
python3 -m venv .venv
.venv/bin/python -m pip install -e '.[test]'
```

## Deterministic Retrieval Run

Start the existing AI server with the multilingual local embedding provider:

```bash
docker compose -f ../../infra/docker-compose.yml up -d --pull never ai
.venv/bin/python -m rag_evaluation evaluate-retrieval \
  --dataset datasets/ops-questions.json \
  --corpus-manifest datasets/corpus-manifest.json \
  --output results/latest
```

## Full Run

Set `OPENAI_API_KEY` and the three model environment variables locally, then run:

```bash
.venv/bin/python -m rag_evaluation run \
  --dataset datasets/ops-questions.json \
  --corpus-manifest datasets/corpus-manifest.json \
  --output results/latest
```

## Interpretation Limits

- The 3-query fixture proves wiring only.
- The 60-query set supports comparison only on this Sigak corpus.
- Query pairs share intents; statistical inference uses 24 intent clusters.
- Judge scores do not replace human review.
- Failed rows remain visible in artifacts and denominators.
````

- [ ] **Step 2: Run the deterministic 3-query smoke**

Start the AI server:

```bash
docker compose -f ../../infra/docker-compose.yml up -d --pull never ai
curl --fail http://localhost:8000/health
```

Run:

```bash
.venv/bin/python -m rag_evaluation evaluate-retrieval \
  --dataset datasets/smoke-questions.json \
  --corpus-manifest datasets/corpus-manifest.json \
  --output results/smoke
```

Verify:

```bash
.venv/bin/python - <<'PY'
import json
from pathlib import Path

root = Path("results/smoke")
runs = json.loads((root / "retrieval.runs.json").read_text())
assert len({row["queryId"] for row in runs}) == 3
assert {row["chunkStrategy"] for row in runs} == {"fixed", "heading"}
assert {row["retrievalSystem"] for row in runs} == {"bm25", "dense", "hybrid"}
assert (root / "report.md").exists()
print("smoke artifact contract passed")
PY
```

Expected:

```text
smoke artifact contract passed
```

Do not write any retrieval-quality conclusion from this run.

- [ ] **Step 3: Run the full reviewed baseline retrieval checkpoint**

Run:

```bash
.venv/bin/python -m rag_evaluation evaluate-retrieval \
  --dataset datasets/ops-questions.json \
  --corpus-manifest datasets/corpus-manifest.json \
  --output results/latest
```

Verify:

```bash
.venv/bin/python - <<'PY'
import json
from pathlib import Path

root = Path("results/latest")
manifest = json.loads((root / "run-manifest.json").read_text())
metrics = json.loads((root / "retrieval.metrics.json").read_text())
assert manifest["dataset"]["answerableIntentCount"] == 24
assert manifest["dataset"]["answerableQueryCount"] == 48
assert manifest["dataset"]["unanswerableQueryCount"] == 12
assert manifest["dataset"]["bootstrapResamples"] == 10000
assert metrics["baselineMatrixComplete"] is True
assert metrics["factorialComplete"] is False
assert set(metrics["requiredSlices"]) >= {
    "overall",
    "LEXICAL_ALIGNED",
    "SEMANTIC_PARAPHRASE",
    "cross_lingual",
}
print("full baseline retrieval artifact contract passed")
PY
```

Expected:

```text
full baseline retrieval artifact contract passed
```

At this checkpoint the six BM25/dense/hybrid combinations are complete. The two
`hybrid_rerank` combinations are intentionally pending until the OpenAI-backed full run.

- [ ] **Step 4: Configure and run the full model-assisted evaluation**

Export locally; never paste the real key into source or command history shared in docs:

```bash
export OPENAI_API_KEY='local-secret'
export SIGAK_RAG_RERANKER_MODEL='gpt-5.4-mini'
export SIGAK_RAG_GENERATOR_MODEL='gpt-5.4'
export SIGAK_RAG_JUDGE_MODEL='gpt-5.5'
```

Run:

```bash
.venv/bin/python -m rag_evaluation run \
  --dataset datasets/ops-questions.json \
  --corpus-manifest datasets/corpus-manifest.json \
  --output results/latest
```

Expected:

- 8 retrieval combinations for all 48 answerable queries
- 5 generation variants for all 60 queries
- judge rows for every completed generation row
- failures preserved explicitly
- report produced even if some API rows fail

Verify the full factorial gate after the run:

```bash
.venv/bin/python - <<'PY'
import json
from pathlib import Path

metrics = json.loads(Path("results/latest/retrieval.metrics.json").read_text())
assert metrics["factorialComplete"] is True
assert len(metrics["configuredCombinations"]) == 8
print("full factorial retrieval gate passed")
PY
```

- [ ] **Step 5: Complete the 50-answer human review**

Open:

- `results/latest/generation.runs.json`
- `results/latest/judge.runs.json`
- `datasets/human-review.json`

Select the 10 queries using Task 10 policy. For each of five variants, record:

```json
{
  "queryId": "selected-query-id",
  "variant": "NO_RAG",
  "reviewStatus": "REVIEWED",
  "verdict": "SUPPORTED",
  "correctness": 4,
  "faithfulness": 4,
  "completeness": 4,
  "refusalCorrectness": true,
  "unsupportedClaim": false,
  "notes": "근거와 답변을 대조한 사람 검토 메모"
}
```

Then rebuild the report:

```bash
cp datasets/human-review.json results/latest/human-review.json
.venv/bin/python -m rag_evaluation build-report --output results/latest
```

Verify:

```bash
.venv/bin/python - <<'PY'
import json
from pathlib import Path

review = json.loads(Path("results/latest/human-review.json").read_text())
assert len([item for item in review["items"] if item["reviewStatus"] == "REVIEWED"]) >= 50
assert review["agreement"] is not None
print("human calibration gate passed")
PY
```

Expected:

```text
human calibration gate passed
```

- [ ] **Step 6: Run secret and artifact integrity scans**

Run:

```bash
rg -n --hidden --glob '!**/.venv/**' \
  '(sk-[A-Za-z0-9_-]{20,}|Authorization: Bearer|OPENAI_API_KEY=.+)' \
  experiments/rag-evaluation .env.example
```

Expected: no output.

Run:

```bash
.venv/bin/python - <<'PY'
import json
from pathlib import Path

root = Path("results/latest")
required = {
    "run-manifest.json",
    "corpus.snapshot.json",
    "dataset.snapshot.json",
    "chunks.fixed.json",
    "chunks.heading.json",
    "retrieval.runs.json",
    "retrieval.metrics.json",
    "generation.runs.json",
    "generation.metrics.json",
    "judge.runs.json",
    "human-review.json",
    "failures.json",
    "report.md",
}
missing = sorted(name for name in required if not (root / name).exists())
assert not missing, missing
for path in root.glob("*.json"):
    json.loads(path.read_text())
print("artifact integrity passed")
PY
```

Expected:

```text
artifact integrity passed
```

- [ ] **Step 7: Update portfolio documentation with observed facts only**

Update:

- root `README.md`
  - link to the report and one-command reproduction
  - include only metrics observed in the final artifact
  - include CI and failure/regression caveat beside any improvement
- `experiments/README.md`
  - distinguish article retrieval smoke from operations RAG full comparison
- `docs/STATUS.md`, `docs/STATUS.ko.md`
  - mark implementation complete only after tests and artifact gates pass
- `docs/ROADMAP.md`, `docs/ROADMAP.ko.md`
  - mark R1-R3 items supported by actual artifacts
  - keep dashboard and robot-manual corpus deferred

Do not write:

- “최적 chunking”
- “hallucination 제거”
- “정확도 증명”
- “judge가 사람을 대체”

Use the report’s conditional wording and actual CI.

- [ ] **Step 8: Write the self-introduction evidence note**

Add a non-public working note only under the already ignored directory
`docs/interview-notes/private/ops-rag-evidence.md`:

```markdown
# Ops RAG 자기소개서 근거

## AI 활용 사례
- 문제:
- 내가 설계한 평가 단위:
- 구현:
- 실제 결과:
- 실패/한계:
- 증거 artifact:

## 1지망 수행 계획 연결
- 운영 매뉴얼 전처리:
- chunking/retrieval/reranking:
- 답변·citation:
- LLM judge와 사람 보정:
- 로봇 운영 도메인으로 확장할 때 바뀌는 점:

## 2지망 연결
- query dataset ↔ issue/PR task dataset
- evidence coverage ↔ FAIL_TO_PASS
- no regression ↔ PASS_TO_PASS
- run manifest ↔ agent configuration
- failure taxonomy ↔ patch/build/test failure
```

Populate it only from final artifacts. Keep military documents, IDs, signatures, seals, and
internal-system details out of the repository and prompts.

- [ ] **Step 9: Run the complete verification gate**

Experiment package:

```bash
cd experiments/rag-evaluation
.venv/bin/python -m pytest
```

Repository documentation:

```bash
cd ../..
git diff --check
rg -n 'TB[D]|TO[D]O|implement la[t]er|fill in detai[l]s' \
  docs/superpowers/plans/2026-06-20-ops-rag-evaluation-harness.md \
  experiments/rag-evaluation
```

Expected:

- pytest passes
- `git diff --check` produces no output
- placeholder scan produces no output

No backend/frontend/AI source was changed, so their full suites are not required solely for this
experiment package. If implementation changes `ai/`, run:

```bash
cd ai
.venv/bin/python -m pytest
```

If implementation changes backend code, run:

```bash
cd backend
./gradlew test
./gradlew check
```

- [ ] **Step 10: Close the documentation loop**

Follow `docs/blog/WRITING_GUIDE.ko.md`:

1. update `docs/blog/2026-06-20-dev-log.md` with commands and actual outputs only
2. update the existing RAG evaluation candidate in `docs/blog/topic-queue.md`
3. separate completed work from deferred dashboard/robot-manual work
4. state any failed OpenAI rows or incomplete reviews as `미검증` rather than hiding them

- [ ] **Step 11: Final commit**

```bash
git add \
  README.md \
  experiments/README.md \
  experiments/rag-evaluation \
  docs/STATUS.md \
  docs/STATUS.ko.md \
  docs/ROADMAP.md \
  docs/ROADMAP.ko.md \
  docs/blog/2026-06-20-dev-log.md \
  docs/blog/topic-queue.md
git commit -m "feat: complete ops RAG evaluation harness"
```

Do not add `docs/interview-notes/private/ops-rag-evidence.md`; it is intentionally private and
ignored.

## Final Verification Checklist

- [ ] corpus uses only the 7 public allowlisted files
- [ ] corpus snapshot records Git SHA, dirty state, document hashes, and included sections
- [ ] fixed and heading chunkers share tokenizer and 500-token maximum
- [ ] chunk balance stats include count, average/p95 tokens, and duplication ratio
- [ ] 24 answerable intents produce exactly 48 paired queries
- [ ] 12 unanswerable queries are present
- [ ] at least 8 semantic rows are cross-lingual
- [ ] every answerable evidence span maps under both chunkers
- [ ] BM25 uses technical mixed tokenization, not whitespace-only Korean tokenization
- [ ] dense sanity fixture passes before full run
- [ ] all 8 chunking/retrieval combinations are present
- [ ] retrieval report includes overall, lexical, semantic, cross-lingual, query-type, and evidence-shape slices
- [ ] nDCG@5 intent-cluster bootstrap uses seed `20260620` and 10,000 resamples
- [ ] reranker invalid IDs, retry, fallback, latency, and token usage are recorded
- [ ] all 5 generation variants run on all 60 queries
- [ ] citation membership and evidence support are checked deterministically
- [ ] answerable and unanswerable refusal behavior is measured
- [ ] generator and judge configurations differ
- [ ] judge input hides variant/retrieval names
- [ ] at least 50 human answer reviews are complete
- [ ] disagreement rows and failure taxonomy are visible
- [ ] report contains English summary and explicit limitations
- [ ] no keys, credentials, military personal data, or internal military details are committed
- [ ] final wording does not generalize beyond the Sigak corpus and reviewed query set

## Execution record — 2026-10-10

- Branch: `codex/ops-rag-evaluation`; single-agent implementation with one independent read-only code review.
- Tasks 1–4: package/settings/schemas, corpus snapshot, tokenizer and chunking implemented and fixture-tested.
- Task 5: the planned 60-query matrix and exact source evidence are prepared, with 16 cross-lingual semantic questions. All rows remain `DRAFT`; no human review is claimed.
- Tasks 6–7: BM25/dense/hybrid retrieval, evidence metrics, slices and intent bootstrap implemented and tested with fixtures. The 3-query deterministic HTTP smoke produced 18 runs and 0 execution failures.
- Task 11: only deterministic CLI orchestration, atomic artifacts, baseline report and review-document preparation implemented. Full model-assisted `run` explicitly remains unavailable.
- Deviation: fixture-based retrieval preparation and deterministic smoke were performed before human dataset freeze, to make the review handoff concrete. No full dataset evaluation or Checkpoint B work was performed.
- OpenAI dependencies and paid calls are deferred to Checkpoint B. No reranker, generator, judge or human answer calibration is implemented.
- Independent review findings about section-specific evidence, global evidence IDs, current lexical overlap, Recall@5 contract and smoke interpretation were fixed with regression tests and re-reviewed.
- Final fixture suite: 70 passed. Existing backend test/check (218), frontend test/lint/build (41), AI pytest (8), research API and fallback search smoke were also verified.
- Remaining gate: human review of question naturalness, expected answers, answerability and gold evidence in `experiments/rag-evaluation/datasets/review.md`, as required by the design. Checkpoint B and final quality claims remain pending.

### Dataset maintenance — 2026-10-30 automation follow-up

Product scheduling now refreshes projections automatically. Dataset v2 therefore clarifies the existing manual-projection pair as a disabled-automation/manual-run scenario and replaces the obsolete automatic-rebuild false-premise question with missing 30-day uptime evidence. Version1 is archived, all60 remain DRAFT, and this change follows the product contract rather than observed retrieval ranking. No full evaluation or human-review claim is added.
