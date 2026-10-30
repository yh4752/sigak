# Sigak 운영 문서 검색 평가

공개 운영 문서 7개에서 fixed/heading chunking과 BM25/dense/hybrid의 6개 조합을 실행하는 독립 실험 도구다. 제품 API나 PostgreSQL 데이터를 수정하지 않고 기존 FastAPI `/api/embeddings/text` 경계를 재사용한다.

현재는 Checkpoint A의 준비 단계다. 60개 질문 초안과 실제 원문 근거를 준비했지만 사람 검토는 0개다. Reranker, 생성기, LLM judge와 답변 calibration은 미구현이다. `run` 명령도 이를 알리고 실패한다.

## 설치와 자동 검사

저장소 루트에서 Python 3.12–3.14로 실행한다. 의존성은 별도 가상 환경에 설치한다.

```bash
python3 -m venv experiments/rag-evaluation/.venv
experiments/rag-evaluation/.venv/bin/python -m pip install -e 'experiments/rag-evaluation[test]'
cd experiments/rag-evaluation
.venv/bin/python -m pytest
.venv/bin/python -m rag_evaluation validate-dataset --allow-draft
.venv/bin/python -m rag_evaluation prepare-review --output datasets/review.md
```

`--allow-draft`는 초안의 구조와 근거 확인용이다. 전체 평가 실행에서는 우회 옵션으로 사용할 수 없다. [검토 문서](datasets/review.md)의 질문, 정답, 근거, 답변 불가 판정을 사람이 확인한 후 query의 `reviewStatus`를 `REVIEWED`로 기록한다. 표현이나 정답을 수정하면 `datasetVersion`도 올린다. 부자연스러운 질문은 `naturalnessReview=REVISE`로 남기고 수정한다. 자동 검사와 AI 검토를 사람 검토로 기록하지 않는다.

Tokenizer의 최초 실행에는 encoding 사전 다운로드를 위한 네트워크 접근이 필요하다. 오프라인 환경에서는 사전 cache를 먼저 준비해야 한다.

## 유료 API 없는 연결 확인

저장소 루트의 별도 터미널에서 기존 AI 서버를 deterministic 모드로 실행한다. AI 서버 가상 환경은 먼저 [AI 안내](../../ai/README.md)에 따라 준비한다.

```bash
cd ai
SIGAK_EMBEDDING_PROVIDER=deterministic .venv/bin/python -m uvicorn app.main:app --host 127.0.0.1 --port 8000
```

평가 패키지 디렉터리에서 실행한다.

```bash
SIGAK_AI_SERVER_URL=http://localhost:8000 SIGAK_RAG_EMBEDDING_MODEL=sigak-deterministic-hash-v1 .venv/bin/python -m rag_evaluation evaluate-retrieval --smoke --dataset datasets/smoke-questions.json --output ../../outputs/rag-smoke-local
.venv/bin/python -m rag_evaluation build-report --output ../../outputs/rag-smoke-local
```

Smoke는 같은 intent의 질문 2개와 답변 불가 질문 1개만 허용한다. 정상 출력은 `queryCount=3`, `runCount=18`, `failedRunCount=0`이다. 해시 기반 embedding은 실행 연결 확인용이며 검색 품질을 입증하지 않는다. 보통 수 초 이내지만 환경과 초기 tokenizer 준비에 따라 달라진다. 기존 결과가 있는 출력 디렉터리는 거부하므로 재실행할 때 새 이름을 사용한다.

## 사람 검토 이후 전체 baseline

AI 서버 모델과 `SIGAK_RAG_EMBEDDING_MODEL`이 일치해야 한다. 기본값은 `sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2`다. Local provider의 최초 모델 준비 시간은 smoke 시간에 포함되지 않는다.

```bash
.venv/bin/python -m rag_evaluation validate-dataset
.venv/bin/python -m rag_evaluation evaluate-retrieval --output ../../outputs/rag-baseline-reviewed
```

60개 질문 모두 사람 검토와 자연스러움 검사를 통과해야 한다. 이번 세션에서는 전체 baseline과 local multilingual 모델 평가를 실행하지 않았다.

## 결과와 해석

- Corpus Git commit, dirty 상태, 원문 SHA-256, dataset/settings hash를 기록한다.
- Chunk별 evidence는 문서·heading·정확한 quote가 함께 일치해야 한다.
- JSON마다 같은 `runId`를 붙이고 파일 단위로 원자적으로 저장한다.
- `Recall@5`는 설계 계약에 따라 관련 chunk가 하나라도 검색됐는지 보는 hit rate다. 전체 gold evidence 회수 비율은 `evidenceCoverageAt5`로 구분한다.
- nDCG, MRR, lexical/semantic/cross-lingual/type slice와 intent 단위 10,000회 bootstrap을 계산한다.
- 실패한 answerable query도 품질 평균의 분모에 포함하고 0점 처리한다. Unanswerable query는 ranking 평균에서 제외하며 실행 결과에는 보존한다.
- Embedding cache는 `.cache/`에 저장한다. 캐시 상태에 따라 latency가 달라지므로 순수 모델 성능이나 서비스 운영 지연으로 해석하지 않는다.
- Smoke interval, dirty snapshot, 6개 baseline만으로 full RAG 품질이나 우위를 주장하지 않는다.

원본 run/metric/failure JSON과 `report.md`가 출력 디렉터리에 저장된다. 후속 범위는 [평가 설계](../../docs/superpowers/specs/2026-06-20-ops-rag-evaluation-harness-design.md)와 [구현 계획](../../docs/superpowers/plans/2026-06-20-ops-rag-evaluation-harness.md)을 따른다.

## Dataset 버전 2

2026-10-30 정기 수집과 자동 projection 갱신을 도입해 수동 작업 질문 두 개의 범위를 명확히 하고, 더 이상 false premise가 아닌 자동 갱신 질문을 문서에 없는 30일 가동률 질문으로 교체했다. 모든 질문은 여전히 DRAFT이고 사람 검토는 0개다. 버전 1은 `datasets/history/ops-questions.v1.json`에 보존했으며, 과거 corpus용이므로 현재 문서에 대한 평가 입력으로 재사용하지 않는다. 이번 변경은 검색 결과에 맞춘 label 수정이 아닌 제품 계약 변경에 따른 갱신이다.
