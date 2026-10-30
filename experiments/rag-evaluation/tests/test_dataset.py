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


@pytest.mark.parametrize("field,value", [("quote", "changed"), ("documentPath", "other.md"), ("headingPath", ["Other"])])
def test_pair_must_share_complete_evidence_not_only_ids(field, value):
    lexical = answerable_row("lexical", "LEXICAL_ALIGNED", "Qdrant 장애?")
    semantic = answerable_row("semantic", "SEMANTIC_PARAPHRASE", "검색이 멈추면?")
    semantic["evidence"][0][field] = value
    dataset = EvaluationDataset.model_validate({"datasetVersion": 1, "queries": [lexical, semantic]})
    with pytest.raises(ValueError, match="must share expected answer and evidence"):
        validate_dataset(dataset, require_full=False)


def test_evidence_ids_have_consistent_definitions_across_intents():
    rows = [answerable_row("a-lexical", "LEXICAL_ALIGNED", "fallback?"), answerable_row("a-semantic", "SEMANTIC_PARAPHRASE", "continue?")]
    for form in ("LEXICAL_ALIGNED", "SEMANTIC_PARAPHRASE"):
        row = answerable_row("b-" + form, form, "other question?")
        row["intentId"] = "other-intent"
        row["evidence"][0]["quote"] = "different quote with same evidence ID"
        rows.append(row)
    dataset = EvaluationDataset.model_validate({"datasetVersion": 1, "queries": rows})
    with pytest.raises(ValueError, match="evidenceId"):
        validate_dataset(dataset, require_full=False)
