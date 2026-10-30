import json
from dataclasses import replace
from pathlib import Path

import httpx
import pytest

from rag_evaluation.config import EvaluationSettings
from rag_evaluation.runner import EvaluationRunner
from test_corpus import FIXTURE_ROOT
from test_evidence import evidence_fixture


def runner_fixture(tmp_path, *, fail_query=False):
    _, payload = evidence_fixture()
    payload["queries"].append({"intentId": "missing-sla", "queryId": "missing-sla", "query": "production SLA?",
        "language": "ko", "expectedResponseLanguage": "ko", "surfaceForm": "ADVERSARIAL_UNANSWERABLE",
        "queryType": "unanswerable", "evidenceShape": None, "answerable": False, "expectedAnswer": None,
        "evidence": [], "naturalnessReview": "NATURAL", "reviewStatus": "DRAFT", "notes": "fixture"})
    dataset = tmp_path / "dataset.json"
    dataset.write_text(json.dumps(payload))
    manifest = tmp_path / "manifest.json"
    manifest.write_text(json.dumps({"corpusId": "fixture", "documents": [
        {"documentPath": "manual-a.md", "includedSections": ["*"]},
        {"documentPath": "manual-b.md", "includedSections": ["*"]}]}))
    def handler(request):
        text = json.loads(request.content)["text"]
        if fail_query and text == "fallback?":
            return httpx.Response(503)
        return httpx.Response(200, json={"provider": "deterministic", "modelName": "fixture-model",
            "dimension": 2, "embedding": [1.0, 0.0] if "fallback" in text.lower() or "keyword" in text.lower() else [0.0, 1.0]})
    settings = replace(EvaluationSettings.from_env(), embedding_model="fixture-model", bootstrap_resamples=100)
    return EvaluationRunner(repository_root=FIXTURE_ROOT, dataset_path=dataset, corpus_manifest_path=manifest,
        output_dir=tmp_path / "output", settings=settings, smoke=True,
        cache_path=tmp_path / "embeddings.json",
        http_client=httpx.Client(transport=httpx.MockTransport(handler)))


def test_runner_executes_real_chunking_search_metrics_and_artifacts(tmp_path):
    runner = runner_fixture(tmp_path)
    summary = runner.evaluate_retrieval()
    assert summary["queryCount"] == 3
    assert summary["runCount"] == 18
    assert summary["failedRunCount"] == 0
    runs = json.loads((runner.output_dir / "retrieval.runs.json").read_text())
    assert len(runs["data"]) == 18
    assert {(row["chunkStrategy"], row["retrievalSystem"]) for row in runs["data"]} == {
        (strategy, system) for strategy in ("fixed", "heading") for system in ("bm25", "dense", "hybrid")}
    for path in runner.output_dir.glob("*.json"):
        artifact = json.loads(path.read_text())
        assert artifact["runId"] == runs["runId"]
    report = (runner.output_dir / "report.md").read_text()
    assert "wiring smoke" in report
    assert "quality claims" in report
    assert "hybrid_rerank" in report
    assert "smoke interval is not quality evidence" in report


def test_validation_recomputes_overlap_for_edited_query(tmp_path):
    from rag_evaluation.runner import prepare_evidence
    from rag_evaluation.tokenization import TechnicalMixedTokenizer, lexical_overlap
    runner = runner_fixture(tmp_path)
    payload = json.loads(runner.dataset_path.read_text())
    payload["queries"][0]["query"] = "vector search fails"
    payload["queries"][0]["measuredLexicalOverlap"] = 0.123456
    runner.dataset_path.write_text(json.dumps(payload))
    dataset, _, _, _, _ = prepare_evidence(runner.repository_root, runner.dataset_path,
        runner.corpus_manifest_path, runner.settings, smoke=True)
    row = dataset.queries[0]
    expected = lexical_overlap(row.query, " ".join(span.quote for span in row.evidence), TechnicalMixedTokenizer())
    assert row.measured_lexical_overlap == expected


def test_embedding_failure_keeps_bm25_and_records_failed_dense_and_hybrid(tmp_path):
    runner = runner_fixture(tmp_path, fail_query=True)
    summary = runner.evaluate_retrieval()
    assert summary["failedRunCount"] == 4
    runs = json.loads((runner.output_dir / "retrieval.runs.json").read_text())["data"]
    assert len(runs) == 18
    lexical = [row for row in runs if row["queryId"] == "lexical"]
    assert sum(row["status"] == "COMPLETED" for row in lexical) == 2
    metrics = json.loads((runner.output_dir / "retrieval.metrics.json").read_text())["data"]
    dense = next(row for row in metrics["systems"] if row["chunkStrategy"] == "fixed" and row["retrievalSystem"] == "dense")
    assert dense["attemptedQueryCount"] == 3
    assert dense["failedQueryCount"] == 1
    assert len([row for row in metrics["byQuery"] if row["retrievalSystem"] == "dense"]) == 4


def test_nonempty_output_is_preserved_on_rerun(tmp_path):
    runner = runner_fixture(tmp_path)
    runner.evaluate_retrieval()
    original = (runner.output_dir / "run-manifest.json").read_bytes()
    with pytest.raises(ValueError, match="non-empty"):
        runner.evaluate_retrieval()
    assert (runner.output_dir / "run-manifest.json").read_bytes() == original


def test_full_run_rejects_draft_before_embedding_calls(tmp_path):
    package = Path(__file__).resolve().parents[1]
    def forbidden(_):
        pytest.fail("embedding called before dataset review gate")
    runner = EvaluationRunner(repository_root=package.parents[1], dataset_path=package / "datasets/ops-questions.json",
        corpus_manifest_path=package / "datasets/corpus-manifest.json", output_dir=tmp_path / "full",
        settings=EvaluationSettings.from_env(), smoke=False, http_client=httpx.Client(transport=httpx.MockTransport(forbidden)))
    with pytest.raises(ValueError, match="reviewed"):
        runner.evaluate_retrieval()
    assert not runner.output_dir.exists()
