from __future__ import annotations

import json
from collections import defaultdict
from pathlib import Path


def build_report(output_dir: Path) -> str:
    artifacts = {}
    run_id = None
    for name in ("run-manifest", "retrieval.metrics", "failures"):
        payload = json.loads((output_dir / f"{name}.json").read_text(encoding="utf-8"))
        if run_id is not None and payload["runId"] != run_id:
            raise ValueError("report artifacts must share runId")
        run_id = payload["runId"]
        artifacts[name] = payload["data"]
    manifest = artifacts["run-manifest"]
    metrics = artifacts["retrieval.metrics"]
    lines = ["# Sigak Ops Retrieval Evaluation", "", f"Run: `{run_id}`", "", "## English summary", ""]
    if manifest["smoke"]:
        lines.append("This three-query wiring smoke checks artifact generation. It does not support retrieval-quality claims.")
    else:
        lines.append("Baseline retrieval results are limited to the reviewed Sigak operations corpus and query set.")
    lines += ["", "## Limitations", "", "- Checkpoint B generation, LLM judging, and human answer calibration are not implemented.",
        "- hybrid_rerank is absent: this is a six-combination baseline, not the full eight-combination evaluation.",
        "- No answer faithfulness, token cost, or generation-quality claims are supported.",
        "- Latency is a local diagnostic; embedding cache warmth can differ between calls.",
        "- Failed answerable queries contribute zero scores to attempted-query metrics.",
        "- Unanswerable queries are recorded but excluded from ranking-quality averages.",
        f"- Embedding provider: `{manifest.get('embeddingProvider', 'unavailable')}`. Deterministic embeddings support wiring checks only.",
        f"- Working tree dirty: `{manifest['workingTreeDirty']}`. Final portfolio evidence requires a clean commit.", "",
        "## Retrieval baselines", "", "| Chunking | Retrieval | Attempts | Failed | nDCG@5 | Recall@5 | MRR@5 | Evidence@5 | Avg ms | p95 ms |",
        "| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |"]
    for system in metrics["systems"]:
        values = [system["chunkStrategy"], system["retrievalSystem"], str(system["attemptedQueryCount"]), str(system["failedQueryCount"])]
        values += [f"{system[name]:.4f}" for name in ("ndcgAt5", "recallAt5", "mrrAt5", "evidenceCoverageAt5", "averageLatencyMs", "p95LatencyMs")]
        lines.append("| " + " | ".join(values) + " |")
    lines += ["", "## Slices", ""]
    for system in metrics["systems"]:
        lines.append(f"### {system['chunkStrategy']} / {system['retrievalSystem']}")
        lines.append("")
        for name, value in system["slices"].items():
            lines.append(f"- `{name}` nDCG@5: {value:.4f}")
        lines.append("")
    lines += ["## Paired comparisons", "", "The sampling unit is an intent cluster, not independent lexical/semantic queries.", ""]
    for row in metrics["pairedComparisons"]:
        interval = row["interval"]
        if manifest["smoke"]:
            interpretation = "smoke interval is not quality evidence"
        else:
            interpretation = "명확한 우위가 관찰되지 않았다" if interval["lower95"] <= 0 <= interval["upper95"] else "이 corpus와 query set에서만 관찰된 차이"
        lines.append(f"- {row['baseline']} → {row['candidate']}: delta {interval['meanDelta']:.4f}, 95% CI [{interval['lower95']:.4f}, {interval['upper95']:.4f}], intents {interval['intentCount']}, W/T/L {interval['winCount']}/{interval['tieCount']}/{interval['lossCount']}. {interpretation}.")
    lines += ["", "## Failures", ""]
    if artifacts["failures"]:
        for failure in artifacts["failures"]:
            lines.append(f"- `{failure['failureType']}`: query `{failure['queryId']}`, variant `{failure['variant']}`; {failure['notes']}")
    else:
        lines.append("No execution failures or answerable retrieval misses were recorded.")
    return "\n".join(lines) + "\n"


def build_review_document(dataset, snapshot):
    groups = defaultdict(list)
    for query in dataset.queries:
        groups[query.intent_id].append(query)
    lines = ["# 운영 문서 RAG 평가 질문 검토", "", "이 문서는 AI가 작성한 초안입니다. `DRAFT`는 사람 검토가 끝나지 않았다는 뜻입니다.",
        "질문의 자연스러움, 정답의 정확성, 인용 근거의 충분성, 답변 불가 판정을 확인해 주세요.",
        "질문 표현이나 정답이 바뀌면 datasetVersion을 올리고 이전 평가 결과를 보존합니다.",
        "자동 검사는 사람 검토를 대신하지 않으며, 이 명령은 reviewStatus를 바꾸지 않습니다.", "",
        f"Corpus: `{snapshot.corpus_id}`, Git: `{snapshot.git_commit}`, dirty: `{snapshot.working_tree_dirty}`", "",
        "## 원문 해시", ""]
    for document in snapshot.documents:
        lines.append(f"- `{document.document_path}`: `{document.sha256}`")
    for intent, queries in groups.items():
        lines += ["", f"## {intent}", "", "- [ ] 사람이 검토 완료", ""]
        for query in queries:
            lines.append(f"- **{query.surface_form.value} / {query.language} / {query.review_status.value}**: {query.query}")
        first = queries[0]
        lines += ["", f"예상 답변: {first.expected_answer or '문서만으로 답변할 수 없음. 잘못된 전제이면 이를 바로잡아야 함.'}", ""]
        for span in first.evidence:
            lines += [f"근거 `{span.evidence_id}` — `{span.document_path}` / {' > '.join(span.heading_path)}", "",
                "> " + span.quote.replace("\n", "\n> "), "", f"SHA-256: `{span.quote_sha256}`", ""]
    return "\n".join(lines) + "\n"
