# 미발행 기술 글 11편 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to execute the independent writing domains and review each article for scope, then evidence and writing quality. Steps use checkbox syntax for tracking.

**Goal:** 미발행 후보11편을 근거 대조·humanizer·독자/채용 관점 검토 후 기존 블로그에 발행한다.

**Architecture:** 원고 담당은 지정된 글과 검토 기록만 편집한다. root가 공통 검사·교차 검토·주제 큐와 상태 문서·원격 발행을 소유한다. 운영 로직과 서비스는 변경하지 않는다. 공통 검사에서 확인된 테스트 날짜 fixture 오류는 root가 최소 수정·재검증한다.

**Tech Stack:** Markdown/frontmatter, Kotlin/React/Python의 기존 무료 fixture, Astro validation/test/build, GitHub/Vercel.

## 승인과 범위

사용자 답변: ‘미발행11편 진행 · 보조3개 허용’. 이번 배치에 한해 대화의 보조1개 기본 제한을
최대3개로 늘린다. 과거 리버트 원고9개는 이번 범위 밖이며 복원하지 않는다. rate-limit 후보의
기존 초안은 참고로 보존하고 새 날짜 원고를 작성한다. 기사 요약용 유료 API를 새로 호출하지 않는다.

전체 글의 기준은 앞선 사용자 요청인 humanizer, 제3자 도움, 채용 독자가 설계 판단을 확인할 수
있는 근거다. 개인 동기·사용자의 독립 구현 기여나 이해를 만들어내지 않는다.

## 원고와 담당

각 파일은 `docs/blog/2026-11-03-<slug>.md`다. 초기에는 draft=true로 저장한다.

| 담당 | slug | 중심 질문 |
| --- | --- | --- |
| root | metric-context-summary | 수치가 맞아도 조건이 빠지면 왜 요약이 오해를 만드는가 |
| cache worker | navigation-cache-and-summary-consistency | 빠른 복귀와 완성된 요약 보존을 어떻게 함께 지키는가 |
| RAG worker | ops-document-rag-evaluation | 짧은 기사 대신 운영 문서를 평가 자료로 선택한 이유는 무엇인가 |
| cache worker | research-artifact-dashboard | 실험 결과와 미측정 상태를 화면에서 어떻게 구분하는가 |
| root | deployable-service-boundaries | 로컬 실행과 배포 검증을 어떤 근거로 구분하는가 |
| RAG worker | source-rate-limit-boundary | 외부 요청 제한과 실행 간 대기를 어디서 책임지는가 |
| graph worker | postgres-neo4j-projection | 그래프를 원본 저장소와 분리한 이유는 무엇인가 |
| graph worker | graph-detail-explainability-evaluation | 관계 설명의 존재와 검색·사실 품질은 무엇이 다른가 |
| graph worker | search-catalog-expansion | 기사 수와 검토된 질문 수를 왜 별도로 관리하는가 |
| RAG worker | honest-portfolio-evidence | 작은 실험의 한계를 숨기지 않고 무엇을 공개할 수 있는가 |
| cache worker | clean-install-lockfile-reproducibility | 기존 설치 환경에서 안 보이던 재현성 실패를 어떻게 찾았는가 |

검토 기록은 `docs/skill-dogfooding/2026-11-03-<slug>-review.md`, 수정 전후와 원시 검사 출력은
ignored `outputs/blog-batch-2026-11-03/`에 둔다. 공통 근거 링크는 이미 공개된
`2b80dd22af9afef0c9fcc167a1e65044cb10f392` commit에 고정한다.

## 작업

- [x] 사용자 범위·병렬 예외와 전용 파일 소유권을 확인한다.
- [x] 세 도메인의 원고 작업을 독립 컨텍스트로 배정한다.
- [x] 11편 모두 현재 코드·과거 기록·불확실성을 대조해 작성한다.
- [x] 각 글의 humanizer 수정 전후와 의미6항을 확인한다.
- [x] root가 범위·실제 역할·중복을 먼저 검토하고 코드/수치·독자 도움·문체를 검토한다.
- [x] 다른 담당자의 글을 읽는 독립 검토를 거쳐 실질 문제를 수정한다.
- [x] 공통 무료 검사를 완료하고 해당 기능에만 검증 의미를 연결한다.
- [x] 원본/발행본 형식과 코드 링크·secret pattern·수치 보존을 검사한다.
- [x] 11개 원고와 필요한 표 표시 수정만 발행본으로 반영하고 다른 대기 변경2개는 보류한다.
- [x] 블로그 테스트·빌드와 각 글의 desktop/mobile·테마·표/코드·목차를 확인한다.
- [x] GitHub CI와 Vercel preview 성공 후 검증한 head를 지정해 발행 PR을 병합한다.
- [x] 공개11주소·블로그 목록·프로젝트·RSS/canonical 및 production을 확인한다.
- [x] STATUS EN/KO·같은 날 dev-log·기존 topic-queue11항목·검토 기록을 갱신한다.
- [x] 무료 RAG 근거 검증/검토 자료를 갱신하고 사람 검토 gate는 유지한다.
- [x] 원본 저장소의 요청 문서와 검증된 fixture 수정만 커밋·푸시하고 미검증 범위를 보고한다.

## 검사와 주장 제한

공통 명령은 root만 실행한다. 과거 smoke·실제 요약 관측은 해당 날짜의 기록이며 이번 검사와 구분한다.

```bash
./backend/gradlew -p backend test check
# frontend/: npm test, npm run lint, npm run build
# ai/: OPENAI_API_KEY=test-only-not-a-real-key .venv/bin/python -m pytest
node --test experiments/scripts/retrieval-benchmark/*.test.mjs
# experiments/rag-evaluation/: .venv/bin/python -m pytest
```

결과는 실제 출력과 XML/JSON에서 읽고 기록한다. 무료 fixture 통과로 의미 요약 품질·운영
가용성·독립 지연 비교·취업 효과를 주장하지 않는다. 운영 수집·projection rebuild·실제 유료
요약·새 이미지 배포는 실행하지 않는다. 오래된 외부 규칙·의존성 경고는 관측 시점으로 한정하고
현재 정책이나 취약점 상태를 새로 단정하지 않는다. source/public validation은 발행 후보다 먼저,
RSS와 실제 공개 화면은 production 성공 이후에 검사한다.
