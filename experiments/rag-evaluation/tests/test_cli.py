import json
from pathlib import Path

from rag_evaluation.cli import main

PACKAGE = Path(__file__).resolve().parents[1]
ROOT = PACKAGE.parents[1]


def test_cli_reviewed_dataset_with_unnatural_query_is_not_ready(capsys, tmp_path):
    payload = json.loads((PACKAGE / "datasets/ops-questions.json").read_text())
    for query in payload["queries"]:
        query["reviewStatus"] = "REVIEWED"
    payload["queries"][0]["naturalnessReview"] = "REVISE"
    dataset = tmp_path / "reviewed.json"
    dataset.write_text(json.dumps(payload))
    assert main(["validate-dataset", "--allow-draft", "--dataset", str(dataset),
        "--corpus-manifest", str(PACKAGE / "datasets/corpus-manifest.json")]) == 0
    assert json.loads(capsys.readouterr().out)["fullEvaluationReady"] is False


def test_cli_validates_draft_without_running_models(capsys):
    assert main(["validate-dataset", "--allow-draft", "--repository-root", str(ROOT),
        "--dataset", str(PACKAGE / "datasets/ops-questions.json"),
        "--corpus-manifest", str(PACKAGE / "datasets/corpus-manifest.json")]) == 0
    result = json.loads(capsys.readouterr().out)
    assert result["dataset"]["answerableIntentCount"] == 24
    assert result["reviewedQueryCount"] == 0
    assert result["fullEvaluationReady"] is False


def test_cli_full_evaluation_reports_draft_gate(capsys, tmp_path):
    assert main(["evaluate-retrieval", "--repository-root", str(ROOT),
        "--dataset", str(PACKAGE / "datasets/ops-questions.json"),
        "--corpus-manifest", str(PACKAGE / "datasets/corpus-manifest.json"),
        "--output", str(tmp_path / "result")]) == 1
    assert "reviewed" in capsys.readouterr().err


def test_cli_cannot_bypass_full_dataset_gate_with_smoke_flag(capsys, tmp_path):
    assert main(["evaluate-retrieval", "--smoke", "--repository-root", str(ROOT),
        "--dataset", str(PACKAGE / "datasets/ops-questions.json"),
        "--corpus-manifest", str(PACKAGE / "datasets/corpus-manifest.json"),
        "--output", str(tmp_path / "result")]) == 1
    assert "three" in capsys.readouterr().err


def test_cli_prepares_review_with_exact_evidence(capsys, tmp_path):
    output = tmp_path / "review.md"
    assert main(["prepare-review", "--repository-root", str(ROOT),
        "--dataset", str(PACKAGE / "datasets/ops-questions.json"),
        "--corpus-manifest", str(PACKAGE / "datasets/corpus-manifest.json"),
        "--output", str(output)]) == 0
    text = output.read_text()
    assert "arch-public-boundary" in text
    assert "missing-production-sla" in text
    assert "PostgreSQL is the source of truth" in text
    assert "DRAFT" in text


def test_model_assisted_command_explicitly_reports_unimplemented_checkpoint(capsys):
    assert main(["run"]) == 1
    assert "Checkpoint B" in capsys.readouterr().err


def test_smoke_requires_one_complete_pair_and_one_unanswerable(capsys, tmp_path):
    payload = json.loads((PACKAGE / "datasets/ops-questions.json").read_text())
    payload["queries"] = payload["queries"][-3:]
    dataset = tmp_path / "no-pair.json"
    dataset.write_text(json.dumps(payload))
    assert main(["evaluate-retrieval", "--smoke", "--dataset", str(dataset),
        "--corpus-manifest", str(PACKAGE / "datasets/corpus-manifest.json"), "--output", str(tmp_path / "run")]) == 1
    assert "complete pair" in capsys.readouterr().err
