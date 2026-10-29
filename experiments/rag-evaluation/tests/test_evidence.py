import hashlib

import pytest

from rag_evaluation.corpus import build_corpus_snapshot
from rag_evaluation.dataset import validate_evidence
from rag_evaluation.models import CorpusManifest, EvaluationDataset
from test_corpus import FIXTURE_ROOT
from test_dataset import answerable_row


def evidence_fixture():
    snapshot = build_corpus_snapshot(repository_root=FIXTURE_ROOT, git_commit="test", working_tree_dirty=False,
        manifest=CorpusManifest(corpusId="fixture", documents=[{"documentPath": "manual-a.md", "includedSections": ["*"]}]))
    lexical = answerable_row("lexical", "LEXICAL_ALIGNED", "fallback?")
    semantic = answerable_row("semantic", "SEMANTIC_PARAPHRASE", "what remains?")
    for row in (lexical, semantic):
        quote = "If vector search fails, use keyword-only mode."
        row["evidence"][0].update(documentPath="manual-a.md", headingPath=["Manual A", "Fallback"],
            quote=quote, quoteSha256=hashlib.sha256(quote.encode()).hexdigest())
    return snapshot, {"datasetVersion": 1, "queries": [lexical, semantic]}


def test_evidence_quote_is_bound_to_document_heading_and_hash():
    snapshot, payload = evidence_fixture()
    validate_evidence(EvaluationDataset.model_validate(payload), snapshot)


@pytest.mark.parametrize("field,value", [("quoteSha256", "a" * 64), ("headingPath", ["Manual A", "Startup"]), ("documentPath", "unknown.md")])
def test_evidence_rejects_stale_or_misattributed_ground_truth(field, value):
    snapshot, payload = evidence_fixture()
    for row in payload["queries"]:
        row["evidence"][0][field] = value
    with pytest.raises(ValueError, match="evidence"):
        validate_evidence(EvaluationDataset.model_validate(payload), snapshot)
