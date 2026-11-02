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


def test_recall_hit_rate_is_independent_of_overlapping_evidence_chunks():
    assert recall_at_k(["c1"], {"c1": 2, "c2": 2}, k=5) == 1.0


def test_bootstrap_rejects_empty_comparison_instead_of_nan():
    with pytest.raises(ValueError, match="non-empty"):
        cluster_bootstrap_delta([], seed=1, resamples=100)
