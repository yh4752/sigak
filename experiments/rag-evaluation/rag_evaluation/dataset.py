from __future__ import annotations

import json
import hashlib
from collections import Counter, defaultdict
from pathlib import Path

from rag_evaluation.models import (
    DatasetSummary,
    EvaluationDataset,
    ReviewStatus,
    SurfaceForm,
    CorpusSnapshot,
    QueryType,
    NaturalnessReview,
)


def load_dataset(path: Path) -> EvaluationDataset:
    return EvaluationDataset.model_validate(json.loads(path.read_text(encoding="utf-8")))


def validate_dataset(
    dataset: EvaluationDataset,
    *,
    require_full: bool,
    require_reviewed: bool = True,
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
        evidence_sets = {
            tuple(sorted(span.model_dump_json() for span in row.evidence))
            for row in rows
        }
        if len(expected_answers) != 1 or len(evidence_sets) != 1:
            raise ValueError(
                f"answerable intent {intent_id} must share expected answer and evidence"
            )
        if len({row.query_type for row in rows}) != 1 or len({row.evidence_shape for row in rows}) != 1:
            raise ValueError(f"answerable intent {intent_id} must share query type and evidence shape")

    evidence_by_id = {}
    for query in dataset.queries:
        for span in query.evidence:
            definition = span.model_dump_json()
            if span.evidence_id in evidence_by_id and evidence_by_id[span.evidence_id] != definition:
                raise ValueError(f"evidenceId must have one consistent definition: {span.evidence_id}")
            evidence_by_id[span.evidence_id] = definition


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
        counts = Counter(rows[0].query_type for rows in by_intent.values())
        expected_types = set(QueryType) - {QueryType.UNANSWERABLE}
        if set(counts) != expected_types or any(count != 4 for count in counts.values()):
            raise ValueError("full dataset requires four intents for each answerable query type")
        if require_reviewed and any(query.review_status is not ReviewStatus.REVIEWED for query in dataset.queries):
            raise ValueError("full dataset requires every query to be reviewed")
        if require_reviewed and any(query.naturalness_review is not NaturalnessReview.NATURAL for query in dataset.queries):
            raise ValueError("full dataset requires natural reviewed queries")

    return DatasetSummary(
        answerable_intent_count=len(by_intent),
        answerable_query_count=len(answerable),
        unanswerable_query_count=len(unanswerable),
        semantic_cross_lingual_count=cross_lingual_count,
    )


def validate_evidence(dataset: EvaluationDataset, snapshot: CorpusSnapshot) -> None:
    documents = {document.document_path: document for document in snapshot.documents}
    for query in dataset.queries:
        for span in query.evidence:
            quote = " ".join(span.quote.split())
            if not quote or hashlib.sha256(quote.encode("utf-8")).hexdigest() != span.quote_sha256:
                raise ValueError(f"evidence quote hash mismatch: {span.evidence_id}")
            document = documents.get(span.document_path)
            if document is None:
                raise ValueError(f"evidence document missing: {span.evidence_id}")
            section = " ".join(" ".join(block.content.split()) for block in document.blocks
                if block.heading_path == span.heading_path)
            if quote not in section:
                raise ValueError(f"evidence quote not found in heading: {span.evidence_id}")
