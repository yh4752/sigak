# Sigak 문서

[English](README.md) | [한국어](README.ko.md)

마지막 업데이트: 2026-05-31

이 디렉터리는 포트폴리오 평가자가 읽을 핵심 문서와 개발 과정에서 남긴 기록을 구분해서 정리합니다.

> 에이전트: 개발 *규칙*은 이 디렉터리가 아니라 저장소 루트의 `AGENTS.md`(단일 출처)에 있습니다. 이 디렉터리는 제품·상태·참고 문서를 담습니다.

## 권장 읽기 순서

1. [PRODUCT.ko.md](PRODUCT.ko.md) - 제품 정의, MVP 범위, 사용자, 콘텐츠 모델, 제품 의사결정
2. [ROADMAP.ko.md](ROADMAP.ko.md) - 서비스 트랙과 연구 트랙을 합친 실행 로드맵
3. [STATUS.ko.md](STATUS.ko.md) - 현재 구현 상태, 알려진 리스크, 검증 기록
4. [API_SPEC.ko.md](API_SPEC.ko.md) - 사람이 읽기 쉬운 API 계약 문서
5. [CODING_CONVENTIONS.ko.md](CODING_CONVENTIONS.ko.md) - 네이밍, 주석, 코드 구성 규칙
6. [RESEARCH_STRATEGY.ko.md](RESEARCH_STRATEGY.ko.md) - LLM/NLP 포트폴리오 전략, 연구 질문, 평가 방향
7. [SOURCE_POLICY.ko.md](SOURCE_POLICY.ko.md) - 출처 품질 기준과 수집 정책

## 디렉터리 가이드

| 경로 | 목적 |
| --- | --- |
| `PRODUCT.ko.md` | 핵심 제품 문서입니다. Sigak이 무엇이고 MVP가 무엇을 포함하는지 이해할 때 먼저 읽습니다. |
| `ROADMAP.ko.md` | 서비스 작업과 연구 작업을 함께 관리하는 살아 있는 로드맵입니다. |
| `STATUS.ko.md` | 현재 상태 문서입니다. 큰 수정, phase 완료, 검증 결과 변경이 있을 때 갱신합니다. |
| `API_SPEC.ko.md` | API 설계 기준 문서입니다. 실행 가능한 백엔드 API 문서는 Swagger가 담당합니다. |
| `CODING_CONVENTIONS.ko.md` | 프로젝트 전반의 네이밍, 한글 주석, 코드 구성 규칙입니다. |
| `RESEARCH_STRATEGY.ko.md` | 연구 포트폴리오 방향, 논문에서 영감을 받은 실험 트랙, 지표, 최종 산출물을 정리합니다. |
| `SOURCE_POLICY.ko.md` | 포함/제외할 article source 기준을 정의합니다. |
| `ko/GUIDE.md` | 입문자를 위한 한국어 종합 가이드입니다. 핵심 문서가 바뀌면 함께 맞춥니다. |
| `decisions/` | Architecture Decision Record입니다. 역사적 결정 기록이므로 가볍게 다시 쓰지 않습니다. |
| `blog/` | 일일 dev-log와 주제형 기술 글입니다. 작성 기준은 `blog/WRITING_GUIDE.ko.md`(단일 출처)를 따르고, 블로그 후보는 `blog/topic-queue.md`에 모읍니다. |
| `superpowers/` | 기능별 설계 명세(spec)와 실행 계획(plan)입니다. 포맷과 워크플로는 `superpowers/README.md`를 참고합니다. |

## 유지보수 규칙

- 현재 구현 상태가 바뀌면 `STATUS.md`를 갱신합니다.
- 다음 실행 순서나 phase 상태가 바뀌면 `ROADMAP.md`를 갱신합니다.
- 제품 범위, 사용자 경험, 콘텐츠 정책이 바뀌면 `PRODUCT.md`를 갱신합니다.
- 백엔드 API 계약이 바뀌면 `API_SPEC.md`를 갱신합니다.
- 네이밍, 주석, 코드 구성 규칙이 바뀌면 `CODING_CONVENTIONS.md`와 `CODING_CONVENTIONS.ko.md`를 갱신합니다.
- 큰 아키텍처 결정은 `decisions/` 아래 ADR로 기록합니다.
- 개발 로그나 기술 글을 작성할 때는 `blog/WRITING_GUIDE.ko.md`의 작성 기준을 따릅니다.
- `ko/GUIDE.md`는 신규 독자가 오래된 프로젝트 구조를 배우지 않도록 핵심 문서와 충분히 맞춥니다.
