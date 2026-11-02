import json
from pathlib import Path

import pytest

from rag_evaluation.corpus import build_corpus_snapshot
from rag_evaluation.dataset import load_dataset, validate_dataset, validate_evidence
from rag_evaluation.models import CorpusManifest

ROOT = Path(__file__).resolve().parents[3]
PACKAGE = ROOT / "experiments/rag-evaluation"


def test_draft_has_complete_matrix_and_exact_corpus_evidence():
    dataset = load_dataset(PACKAGE / "datasets/ops-questions.json")
    summary = validate_dataset(dataset, require_full=True, require_reviewed=False)
    assert (summary.answerable_intent_count, summary.answerable_query_count, summary.unanswerable_query_count) == (24, 48, 12)
    assert summary.semantic_cross_lingual_count >= 8
    manifest = CorpusManifest.model_validate(json.loads((PACKAGE / "datasets/corpus-manifest.json").read_text()))
    snapshot = build_corpus_snapshot(repository_root=ROOT, manifest=manifest, git_commit="fixture", working_tree_dirty=True)
    validate_evidence(dataset, snapshot)


def test_agent_authored_dataset_does_not_claim_human_review():
    dataset = load_dataset(PACKAGE / "datasets/ops-questions.json")
    assert all(query.review_status.value == "DRAFT" for query in dataset.queries)
    with pytest.raises(ValueError, match="reviewed"):
        validate_dataset(dataset, require_full=True)


def test_full_matrix_requires_four_intents_per_answerable_type():
    from rag_evaluation.models import QueryType
    dataset = load_dataset(PACKAGE / "datasets/ops-questions.json")
    for query in dataset.queries:
        if query.intent_id == "arch-public-boundary":
            query.query_type = QueryType.COMMAND_PROCEDURE
    with pytest.raises(ValueError, match="four intents"):
        validate_dataset(dataset, require_full=True, require_reviewed=False)
