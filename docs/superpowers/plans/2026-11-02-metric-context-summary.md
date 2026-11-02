# Metric Context Summary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. 단일 agent로 기존 작업 브랜치에서 수행한다.

**Goal:** 주요 수치와 그 결과에 영향을 주는 측정 조건을 함께 요약하고 기존 누적 비용·캐시를 보존한다.

**Architecture:** FastAPI instructions/v3 provenance를 갱신하고 Spring은 기존v2 cache와 UNKNOWN을 계속 인식한다. 공개 DTO와 모델은 그대로다.

**Tech Stack:** Python/FastAPI/pytest, Kotlin/Spring/JDBC/JUnit/Testcontainers, 기존 Compose.

## Task 1: 회귀 RED

- [x] `ai/tests/test_openai_enrichment.py`, `test_fulltext_enrichment.py`의 provenance 기대를 V3로 갱신해 기존 V2에서 실패를 확인한다.
- [x] fixture 입력에 lab/production 조건을 넣고 trusted instructions·전체 원문·출력/요청 한도를 검사한다. fixture는 의미 평가 결과로 간주하지 않는다.
- [x] `ArticleSummaryServiceTest.kt`에 V2 READY 재사용·과거 UNKNOWN 유지·복구 후보 제외를 추가한다.
- [x] `ArticleServiceTest.kt`에 V3 FULL_TEXT 표시 회귀를 추가하고 실패를 확인한다.

## Task 2: 최소 구현과 GREEN

- [x] `ai/app/services/openai_enrichment_service.py`: PROMPT_VERSION을 article-summary-v3-metric-context로 갱신하고 수치/조건·실험/실사용·추측 금지 지시를 추가한다. 출력2048/입력64KiB는 유지한다.
- [x] `SummaryContracts.kt`: SUMMARY_PROMPT와 LEGACY_FULL_TEXT_SUMMARY_PROMPT, 지원 prompt set을 선언한다.

```kotlin
const val SUMMARY_PROMPT = "article-summary-v3-metric-context"
const val LEGACY_FULL_TEXT_SUMMARY_PROMPT = "article-summary-v2-fulltext"
val FULL_TEXT_SUMMARY_PROMPTS = setOf(SUMMARY_PROMPT, LEGACY_FULL_TEXT_SUMMARY_PROMPT)
```

- [x] `SummaryJobStore.kt`의 READY/latest 조회에서 V2/V3를 인식하고 UNKNOWN은 모든 prompt에서 재사용한다. 새 job/claim은 V3만 쓴다.
- [x] `FullTextStore.kt`의 awaitingSummary SQL도 지원 버전과 UNKNOWN을 제외한다. `ArticleService.kt`는 지원 set으로 FULL_TEXT를 표시한다.
- [x] AI `.venv/bin/python -m pytest`, backend `./gradlew test check bootJar`를 실제 실행한다.

## Task 3: 배포와 실제1회

- [x] 기존 budget/jobs 및221 READY를 백업하고 drain 상태를 확인한다. 검증한 AI image/JAR를 일치하는 V3로 배포한다.
- [x] 일회성 guard SQL로221만 V3 PENDING 예약한다. 공용 worker의 기존 과금 경계를 쓰며 이전 결과는 성공 전까지 current로 유지한다.
- [x] 실제 결과의 수치+조건·전체 입력·근거·시간·token·budget delta와 캐시 재사용을 확인한다. 자동 재시도는 하지 않는다.
- [x] local/public HTTP smoke, STATUS/ROADMAP/API_SPEC·dev-log/topic-queue·RAG 근거를 갱신한다.
- [x] diff/secret 검사, 커밋·기존 draft PR 반영. main은 병합하지 않는다.

## Self-review

기존 결과 재생성·UNKNOWN 재시도·공개 refresh·두 번째 검토 호출을 막는 범위가 명시됐다.
사용자가 승인한 개선과 예산 내에서 진행하며 테스트 전에는 의미 품질 성공을 주장하지 않는다.

실행 근거: AI70개, backend343개 test/check/bootJar 통과. 실제221 V3 한 건에서 lab 장비/네트워크·
production 기간/기기/percentile 조건을 확인했다. 이전9개 job/요약 이력 보존, 누적10회/$0.20/UNKNOWN1,
추가 token 추정$0.002363, 캐시 재사용 추가0. HTTP 각7개 및 RAG 근거는 개발 로그에 기록한다.
