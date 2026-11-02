from __future__ import annotations

import hashlib
import json
import subprocess
import time
import uuid
from collections import defaultdict
from datetime import UTC, datetime
from pathlib import Path

import httpx
import numpy as np

from rag_evaluation.artifacts import ArtifactWriter
from rag_evaluation.chunking import FixedSizeChunker, HeadingAwareChunker, assign_evidence_to_chunks
from rag_evaluation.config import EvaluationSettings
from rag_evaluation.corpus import build_corpus_snapshot
from rag_evaluation.dataset import load_dataset, validate_dataset, validate_evidence
from rag_evaluation.embeddings import EmbeddingCache, EmbeddingClient
from rag_evaluation.metrics import cluster_bootstrap_delta, evidence_coverage_at_k, mean_reciprocal_rank_at_k, ndcg_at_k, percentile_95, recall_at_k, summarize_slices
from rag_evaluation.models import CorpusManifest, RankedChunk, RetrievalRun
from rag_evaluation.report import build_report
from rag_evaluation.retrieval import BM25Index, DenseIndex, reciprocal_rank_fusion
from rag_evaluation.tokenization import ChunkTokenCodec, TechnicalMixedTokenizer, lexical_overlap

BASELINES = ("bm25", "dense", "hybrid")
PUBLIC_CORPUS_PATHS = {"README.md", "docs/DEMO_FLOW.md", "docs/API_SPEC.md", "experiments/README.md",
    "backend/README.md", "ai/README.md", "docs/STATUS.md"}


def git_state(repository_root):
    try:
        commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repository_root, text=True, stderr=subprocess.DEVNULL).strip()
        dirty = bool(subprocess.check_output(["git", "status", "--porcelain"], cwd=repository_root, text=True))
        return commit, dirty
    except subprocess.CalledProcessError:
        return "unavailable", True


def prepare_evidence(repository_root, dataset_path, corpus_manifest_path, settings, *, smoke=False, allow_draft=False):
    dataset = load_dataset(dataset_path)
    if smoke and len(dataset.queries) != 3:
        raise ValueError("smoke dataset must contain exactly three queries")
    summary = validate_dataset(dataset, require_full=not smoke, require_reviewed=not allow_draft)
    if smoke and (summary.answerable_intent_count, summary.answerable_query_count, summary.unanswerable_query_count) != (1, 2, 1):
        raise ValueError("smoke dataset requires one complete pair and one unanswerable query")
    manifest = CorpusManifest.model_validate_json(corpus_manifest_path.read_text(encoding="utf-8"))
    if not smoke and {document.document_path for document in manifest.documents} != PUBLIC_CORPUS_PATHS:
        raise ValueError("full corpus must use the seven public allowlisted documents")
    commit, dirty = git_state(repository_root)
    snapshot = build_corpus_snapshot(repository_root=repository_root, manifest=manifest, git_commit=commit, working_tree_dirty=dirty)
    validate_evidence(dataset, snapshot)
    tokenizer = TechnicalMixedTokenizer()
    for query in dataset.queries:
        # 사람이 질문을 수정해도 저장된 옛 overlap 값을 평가 결과에 재사용하지 않는다.
        query.measured_lexical_overlap = lexical_overlap(query.query, " ".join(span.quote for span in query.evidence), tokenizer) if query.answerable else None
    codec = ChunkTokenCodec(settings.chunk_tokenizer)
    chunkers = {"fixed": FixedSizeChunker, "heading": HeadingAwareChunker}
    chunks = {name: chunker(codec=codec, max_tokens=settings.max_chunk_tokens, overlap_tokens=settings.overlap_tokens).chunk(snapshot.documents)
        for name, chunker in chunkers.items()}
    spans = {span.evidence_id: span for query in dataset.queries for span in query.evidence}
    matches = {strategy: assign_evidence_to_chunks(rows, list(spans.values())) for strategy, rows in chunks.items()}
    for strategy, mapping in matches.items():
        missing = set(spans) - set(mapping)
        if missing:
            raise ValueError(f"evidence not mapped by {strategy}: {', '.join(sorted(missing))}")
    return dataset, summary, snapshot, chunks, matches


class EvaluationRunner:
    def __init__(self, *, repository_root: Path, dataset_path: Path, corpus_manifest_path: Path,
                 output_dir: Path, settings: EvaluationSettings, smoke=False, http_client=None, cache_path=None):
        self.repository_root = repository_root
        self.dataset_path = dataset_path
        self.corpus_manifest_path = corpus_manifest_path
        self.output_dir = output_dir
        self.settings = settings
        self.smoke = smoke
        self.http_client = http_client
        self.cache_path = cache_path or repository_root / ".cache/rag-evaluation/embeddings.json"

    def evaluate_retrieval(self):
        settings = self.settings
        dataset, summary, snapshot, chunks, matches = prepare_evidence(self.repository_root, self.dataset_path,
            self.corpus_manifest_path, settings, smoke=self.smoke)
        if self.output_dir.exists() and any(self.output_dir.iterdir()):
            raise ValueError("output directory is non-empty; choose a new output to preserve previous runs")
        run_id = datetime.now(UTC).strftime("%Y-%m-%dT%H-%M-%SZ") + "-" + uuid.uuid4().hex[:8]
        writer = ArtifactWriter(self.output_dir, run_id)
        manifest = {"smoke": self.smoke, "gitCommit": snapshot.git_commit, "workingTreeDirty": snapshot.working_tree_dirty,
            "corpusId": snapshot.corpus_id, "datasetVersion": dataset.dataset_version, "dataset": summary.model_dump(by_alias=True),
            "datasetSha256": hashlib.sha256(self.dataset_path.read_bytes()).hexdigest(),
            "corpusManifestSha256": hashlib.sha256(self.corpus_manifest_path.read_bytes()).hexdigest(),
            "chunking": {"tokenizer": settings.chunk_tokenizer, "maxTokens": settings.max_chunk_tokens, "overlapTokens": settings.overlap_tokens},
            "retrieval": {"systems": list(BASELINES), "topK": settings.top_k, "candidateLimit": settings.candidate_limit,
                "rrfK": settings.rrf_k, "bm25K1": settings.bm25_k1, "bm25B": settings.bm25_b, "tokenizer": "technical-mixed-kiwi"},
            "bootstrapSeed": settings.bootstrap_seed, "bootstrapResamples": settings.bootstrap_resamples,
            "embeddingModel": settings.embedding_model, "status": "RUNNING"}
        writer.write_json("run-manifest.json", manifest)
        writer.write_json("corpus.snapshot.json", snapshot.model_dump(by_alias=True))
        writer.write_json("dataset.snapshot.json", dataset.model_dump(by_alias=True))
        for strategy, rows in chunks.items():
            writer.write_json(f"chunks.{strategy}.json", [chunk.model_dump(by_alias=True) for chunk in rows])
        client = self.http_client or httpx.Client()
        try:
            embeddings = EmbeddingClient(base_url=settings.ai_server_url, expected_model=settings.embedding_model,
                http_client=client, cache=EmbeddingCache(self.cache_path))
            failures = []
            runs = []
            for strategy, rows in chunks.items():
                lexical = BM25Index(chunks=rows, tokenizer=TechnicalMixedTokenizer(), k1=settings.bm25_k1, b=settings.bm25_b)
                dense = None
                try:
                    vectors = {}
                    for chunk in rows:
                        key = f"{settings.embedding_model}:{snapshot.corpus_id}:chunk:{chunk.content_sha256}"
                        embedding = embeddings.embed(chunk.content, cache_key=key)
                        if not self.smoke and embedding.provider == "deterministic":
                            raise ValueError("deterministic embeddings cannot support full evaluation")
                        vectors[chunk.chunk_id] = embedding.vector
                        manifest["embeddingProvider"] = embedding.provider
                        manifest["embeddingDimension"] = embedding.dimension
                    dense = DenseIndex(chunks=rows, vectors=vectors)
                except (httpx.HTTPError, ValueError, KeyError, TypeError) as error:
                    dense_failure = type(error).__name__
                for query in dataset.queries:
                    query_vector = None
                    embedding_latency = 0.0
                    query_failure = None
                    if dense is not None:
                        started = time.perf_counter()
                        try:
                            digest = hashlib.sha256(query.query.encode()).hexdigest()
                            key = f"{settings.embedding_model}:dataset:{dataset.dataset_version}:{query.query_id}:{digest}"
                            query_vector = embeddings.embed(query.query, cache_key=key).vector
                        except (httpx.HTTPError, ValueError, KeyError, TypeError) as error:
                            query_failure = type(error).__name__
                        embedding_latency = (time.perf_counter() - started) * 1000
                    for system in BASELINES:
                        started = time.perf_counter()
                        reason = None
                        ranked = []
                        try:
                            if system == "bm25":
                                ranked = lexical.search(query.query, limit=settings.candidate_limit)
                            elif dense is None or query_vector is None:
                                reason = query_failure or dense_failure
                            elif system == "dense":
                                ranked = dense.search(query_vector, limit=settings.candidate_limit)
                            else:
                                fused = reciprocal_rank_fusion([lexical.search(query.query, limit=settings.candidate_limit),
                                    dense.search(query_vector, limit=settings.candidate_limit)], rrf_k=settings.rrf_k, limit=settings.candidate_limit)
                                ranked = [RankedChunk(chunkId=chunk_id, score=score, rank=index + 1) for index, (chunk_id, score) in enumerate(fused)]
                        except (ValueError, KeyError, TypeError) as error:
                            reason = type(error).__name__
                        latency = (time.perf_counter() - started) * 1000 + (embedding_latency if system != "bm25" else 0)
                        runs.append(RetrievalRun(queryId=query.query_id, intentId=query.intent_id, chunkStrategy=strategy,
                            retrievalSystem=system, status="FAILED" if reason else "COMPLETED", rankedChunks=ranked,
                            latencyMs=latency, failureReason=reason).model_dump(by_alias=True))
                        if reason:
                            failures.append({"queryId": query.query_id, "variant": f"{strategy}:{system}",
                                "failureType": "EMBEDDING_FAILURE", "notes": reason})
                    # 도중 중단돼도 이미 실행한 query 결과가 남도록 query 단위로 저장한다.
                    writer.write_json("retrieval.runs.json", runs)
                    writer.write_json("failures.json", failures)
            metrics = self._metrics(dataset, snapshot, chunks, matches, runs, failures)
            manifest["status"] = "PARTIAL" if any(row["status"] == "FAILED" for row in runs) else "COMPLETED"
            writer.write_json("run-manifest.json", manifest)
            writer.write_json("retrieval.metrics.json", metrics)
            writer.write_json("failures.json", failures)
            writer.write_text("report.md", build_report(self.output_dir))
            return {"runId": run_id, "queryCount": len(dataset.queries), "runCount": len(runs),
                "failedRunCount": sum(row["status"] == "FAILED" for row in runs), "output": str(self.output_dir)}
        finally:
            if self.http_client is None:
                client.close()

    def _metrics(self, dataset, snapshot, chunks, matches, runs, failures):
        queries = {query.query_id: query for query in dataset.queries}
        by_query = []
        for run in runs:
            query = queries[run["queryId"]]
            if not query.answerable:
                continue
            grades = {}
            by_chunk = defaultdict(set)
            mapping = matches[run["chunkStrategy"]]
            for span in query.evidence:
                for match in mapping[span.evidence_id]:
                    grades[match.chunk_id] = max(grades.get(match.chunk_id, 0), match.grade)
                    by_chunk[match.chunk_id].add(span.evidence_id)
            ranked = [row["chunkId"] for row in run["rankedChunks"]] if run["status"] == "COMPLETED" else []
            coverage = evidence_coverage_at_k(ranked, by_chunk, expected_evidence_ids={span.evidence_id for span in query.evidence}, k=5)
            by_query.append({"queryId": query.query_id, "intentId": query.intent_id, "surfaceForm": query.surface_form.value,
                "language": query.language, "expectedResponseLanguage": query.expected_response_language,
                "queryType": query.query_type.value, "evidenceShape": query.evidence_shape.value,
                "chunkStrategy": run["chunkStrategy"], "retrievalSystem": run["retrievalSystem"], "status": run["status"],
                "recallAt5": recall_at_k(ranked, grades, k=5), "mrrAt5": mean_reciprocal_rank_at_k(ranked, grades, k=5),
                "ndcgAt5": ndcg_at_k(ranked, grades, k=5), "evidenceCoverageAt5": coverage})
            if coverage < 1 and run["status"] == "COMPLETED":
                failures.append({"queryId": query.query_id, "variant": f"{run['chunkStrategy']}:{run['retrievalSystem']}",
                    "failureType": "RETRIEVAL_MISS", "notes": "expected evidence was not fully covered at top 5"})
        systems = []
        for strategy in chunks:
            for system in BASELINES:
                group = [row for row in by_query if row["chunkStrategy"] == strategy and row["retrievalSystem"] == system]
                attempts = [row for row in runs if row["chunkStrategy"] == strategy and row["retrievalSystem"] == system]
                slices = summarize_slices(group, metric_name="ndcgAt5")
                cross = [row["ndcgAt5"] for row in group if row["language"] != row["expectedResponseLanguage"]]
                if cross:
                    slices["language:cross_lingual"] = float(np.mean(cross))
                systems.append({"chunkStrategy": strategy, "retrievalSystem": system, "attemptedQueryCount": len(attempts),
                    "failedQueryCount": sum(row["status"] == "FAILED" for row in attempts), "answerableQueryCount": len(group),
                    **{name: float(np.mean([row[name] for row in group])) for name in ("ndcgAt5", "recallAt5", "mrrAt5", "evidenceCoverageAt5")},
                    "averageLatencyMs": float(np.mean([row["latencyMs"] for row in attempts])),
                    "p95LatencyMs": percentile_95([row["latencyMs"] for row in attempts]), "slices": slices})
        comparisons = []
        combinations = [(f"fixed:{system}", f"heading:{system}") for system in BASELINES]
        combinations += [(f"{strategy}:bm25", f"{strategy}:{system}") for strategy in chunks for system in ("dense", "hybrid")]
        for baseline, candidate in combinations:
            left = {row["queryId"]: row for row in by_query if f"{row['chunkStrategy']}:{row['retrievalSystem']}" == baseline}
            right = {row["queryId"]: row for row in by_query if f"{row['chunkStrategy']}:{row['retrievalSystem']}" == candidate}
            pairs = [{"intentId": left[query_id]["intentId"], "baseline": left[query_id]["ndcgAt5"], "candidate": right[query_id]["ndcgAt5"]} for query_id in left]
            interval = cluster_bootstrap_delta(pairs, seed=self.settings.bootstrap_seed, resamples=self.settings.bootstrap_resamples)
            comparisons.append({"baseline": baseline, "candidate": candidate, "interval": interval.model_dump(by_alias=True)})
        codec = ChunkTokenCodec(self.settings.chunk_tokenizer)
        source_tokens = sum(len(codec.encode(document.normalized_text)) for document in snapshot.documents)
        balance = {}
        for strategy, rows in chunks.items():
            counts = [row.token_count for row in rows]
            balance[strategy] = {"chunkCount": len(rows), "averageTokenCount": float(np.mean(counts)), "p95TokenCount": percentile_95(counts),
                "duplicatedTokenRatio": max(0, sum(counts) - source_tokens) / sum(counts)}
        return {"byQuery": by_query, "systems": systems, "pairedComparisons": comparisons, "chunkBalance": balance}
