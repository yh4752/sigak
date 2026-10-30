from __future__ import annotations

import math
from collections import defaultdict

import numpy as np

from rag_evaluation.models import BootstrapInterval


def recall_at_k(ranked: list[str], grades: dict[str, int], *, k: int) -> float:
    # 설계의 Recall@5는 근거 chunk 하나라도 찾았는지 보는 hit rate다. 중복 window를 분모로 쓰지 않는다.
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
    if not rows or resamples < 1:
        raise ValueError("bootstrap requires non-empty rows and positive resamples")
    by_intent: dict[str, list[float]] = defaultdict(list)
    for row in rows:
        by_intent[row["intentId"]].append(row["candidate"] - row["baseline"])
    intent_deltas = {
        intent_id: float(np.mean(values))
        for intent_id, values in by_intent.items()
    }
    values = np.asarray(list(intent_deltas.values()), dtype=float)
    rng = np.random.default_rng(seed)
    samples = rng.choice(values, size=(resamples, len(values)), replace=True).mean(axis=1)
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
