from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from pydantic import ValidationError

from rag_evaluation.artifacts import ArtifactWriter
from rag_evaluation.config import EvaluationSettings
from rag_evaluation.report import build_report, build_review_document
from rag_evaluation.runner import EvaluationRunner, prepare_evidence


def build_parser():
    parser = argparse.ArgumentParser(description="Sigak operations-document retrieval evaluation")
    commands = parser.add_subparsers(dest="command", required=True)
    for command in ("validate-dataset", "prepare-review", "evaluate-retrieval"):
        sub = commands.add_parser(command)
        sub.add_argument("--repository-root", type=Path, default=Path(__file__).resolve().parents[3])
        sub.add_argument("--dataset", type=Path, default=Path("datasets/ops-questions.json"))
        sub.add_argument("--corpus-manifest", type=Path, default=Path("datasets/corpus-manifest.json"))
        sub.add_argument("--smoke", action="store_true")
        if command == "validate-dataset":
            sub.add_argument("--allow-draft", action="store_true")
        else:
            sub.add_argument("--output", type=Path, default=Path("datasets/review.md") if command == "prepare-review" else Path("results/latest"))
    sub = commands.add_parser("build-report")
    sub.add_argument("--output", type=Path, default=Path("results/latest"))
    commands.add_parser("run", help="model-assisted evaluation (Checkpoint B pending)")
    return parser


def main(argv=None):
    args = build_parser().parse_args(argv)
    settings = EvaluationSettings.from_env()
    try:
        if args.command == "run":
            raise ValueError("Checkpoint B is not implemented; review the 60-query dataset before model-assisted evaluation")
        if args.command == "build-report":
            content = build_report(args.output)
            run_id = json.loads((args.output / "run-manifest.json").read_text())["runId"]
            ArtifactWriter(args.output, run_id).write_text("report.md", content)
            print(json.dumps({"report": str(args.output / "report.md")}))
            return 0
        if args.command == "evaluate-retrieval":
            summary = EvaluationRunner(repository_root=args.repository_root, dataset_path=args.dataset,
                corpus_manifest_path=args.corpus_manifest, output_dir=args.output, settings=settings, smoke=args.smoke).evaluate_retrieval()
            print(json.dumps(summary, ensure_ascii=False))
            return 1 if summary["failedRunCount"] else 0
        allow_draft = args.command == "prepare-review" or args.allow_draft
        dataset, summary, snapshot, chunks, _ = prepare_evidence(args.repository_root, args.dataset, args.corpus_manifest,
            settings, smoke=args.smoke, allow_draft=allow_draft)
        reviewed = sum(query.review_status.value == "REVIEWED" for query in dataset.queries)
        if args.command == "prepare-review":
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(build_review_document(dataset, snapshot), encoding="utf-8")
            print(json.dumps({"review": str(args.output), "queryCount": len(dataset.queries), "reviewedQueryCount": reviewed}, ensure_ascii=False))
        else:
            print(json.dumps({"dataset": summary.model_dump(by_alias=True), "reviewedQueryCount": reviewed,
                "fullEvaluationReady": len(dataset.queries) == 60 and reviewed == 60
                    and all(query.naturalness_review.value == "NATURAL" for query in dataset.queries),
                "evidenceValidated": True, "chunkCounts": {strategy: len(rows) for strategy, rows in chunks.items()}}, ensure_ascii=False))
        return 0
    except (ValueError, OSError, KeyError, TypeError) as error:
        message = "invalid dataset or corpus schema" if isinstance(error, ValidationError) else str(error)
        print(f"ERROR: {message}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
