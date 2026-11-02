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
        extra="forbid",
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
    grade: int = Field(default=2, ge=1, le=2)


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


class ChunkStrategy(StrEnum):
    FIXED = "fixed"
    HEADING = "heading"


class ChunkSection(StrictModel):
    heading_path: list[str]
    content: str


class Chunk(StrictModel):
    chunk_id: str
    strategy: ChunkStrategy
    document_path: str
    heading_path: list[str]
    content: str
    token_count: int
    contains_code_block: bool
    content_sha256: str
    covered_heading_paths: list[list[str]] = Field(default_factory=list)
    source_sections: list[ChunkSection] = Field(default_factory=list)


class EvidenceChunkMatch(StrictModel):
    evidence_id: str
    chunk_id: str
    grade: int = Field(ge=0, le=2)


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
