# 연구 산출물 대시보드 글 검토 기록

작성일: 2026-11-03
대상: `docs/blog/2026-11-03-research-artifact-dashboard.md`
근거 링크 기준 commit: `2b80dd22af9afef0c9fcc167a1e65044cb10f392`
상태: 원고 준비 완료, `draft: true`. 통합 검사·발행은 이 작업 범위 밖이다.

## 적용 절차와 검토 범위

`technical-blog-learning-writer`, 로컬 writing guide, `humanizer` v1.6.0 및 명사화·번역투 참조를 적용했다. 사용자가 요청한 여러 글 중 이 글은 실험 결과를 API와 UI로 전달하는 판단을 맡는다. 문제·대안·책임 경계·검증·한계를 연결하고, 개인의 독립 구현이나 이해도를 대신 서술하지 않았다. 설명 질문은 본문에 남기되 개인 답안은 작성하지 않았다.

검토는 Codex가 수행했다. 외부 독자나 채용 담당자의 실제 평가로 표현하지 않았다.

## 읽은 근거와 사실 대조

- `AGENTS.md`, `docs/STATUS.md`, `docs/blog/WRITING_GUIDE.ko.md`, `topic-queue.md`의 research 후보.
- `docs/superpowers/specs/2026-06-11-research-dashboard-design.md`의 대안·범위.
- `docs/blog/2026-07-12-dev-log.md`, `2026-07-26-dev-log.md`, `2026-10-10-dev-log.md`.
- backend research controller/properties/reader/service/DTO와 reader/service/controller fixture.
- `frontend/src/api/research.ts`, `ResearchPage.tsx`와 API·화면 fixture.
- retrieval comparison, graph summary/by-query, 41개 확장 catalog JSON.

이번에 JSON을 읽어 6개 기사·3개 질문·K=5, retrieval 생성일 2026-06-02, graph 생성일 2026-06-04, 확장 catalog 41개를 확인했다. `postgres vector search`의 `reasonCoverage=null`과 `graphReasonedCoverageAtK=0`도 대조했다. 이는 정적 파일 관측이며 runtime·실험 재실행이 아니다.

과거 검증은 7월 focused 성공과 전체 backend/runtime 차단, 10월 후속 전체 gate·runtime 성공으로 날짜를 나눴다. 이번 작업에서 테스트·앱 기동·브라우저·실험 runner를 실행하지 않았다. 재현 명령은 미실행이라고 적었다.

## 내용 검토에서 지킨 판단

| 관점 | 원고에 반영한 것 |
| --- | --- |
| 처음 읽는 개발자 | JSON을 화면으로 옮길 때 표본·누락·시각의 의미가 바뀌는 문제를 도입 |
| 대안 비교 | 상수 복사, 보고서 링크, backend DTO 경로의 유지 비용을 비교 |
| 설계 평가 | reader·mapping·Zod·화면 책임, graph summary와 by-query의 오류 단위를 연결 |
| 지표 해석 | null/0/unavailable, 6개 smoke와 41개 catalog, 응답 시각과 실험 시각을 분리 |
| 과장 방지 | Zod가 의미 품질을 검증한다고 하지 않음. graph reason의 독립 사실성을 주장하지 않음 |

확장 catalog의 label 상태는 service의 고정 정책이며 실제 사람 검토 현황의 자동 집계가 아니라는 점을 명시했다. 부분 fallback은 backend가 유효한 DTO를 만든 경우에 해당하고, API/Zod 오류는 전체 오류로 남는 경계도 설명했다.

## humanizer 적용과 실제 수정

40개 패턴을 6범주로 수동 점검했다. S1 필수 수정은 찾지 않았고, S2 편집 후보 5곳을 동작과 구체적인 판단으로 바꿨다. 기술 용어와 필요한 null 표현은 보존했다. 이 분류는 문체 편집 기록이며 AI 판별 점수가 아니다.

| 패턴 | 실제 수정 전 | 실제 수정 후 |
| --- | --- | --- |
| 추상 목적어 | Sigak의 `/research`는 저장된 실험 산출물을 읽어 그 관계를 보여 주는 화면이다. | Sigak의 `/research`는 저장된 실험 산출물에서 검색 방식별 결과와 평가 범위를 모아 보여 준다. |
| 명사 중심 제목 | 화면의 완성도와 실험의 완성도 | 화면이 완성돼도 실험 범위는 그대로다 |
| 구현 용어에 기대는 질문 | graph summary와 질문별 evidence는 왜 같은 실패 단위를 쓰지 않는가? | graph summary와 질문별 evidence는 실패했을 때 왜 서로 다르게 처리하는가? |

윤문 후 자연도는 A로 수동 판단했다. 추가 S1/S2 수정이 필요하지 않다는 편집 판단이며 외부 독자의 반응을 측정한 결과가 아니다.

수정 전 913어절, 수정 후 915어절, 근사 변경량 40어절로 **4.38%**다. frontmatter·코드·링크 목적지·문장부호를 뺀 공백 어절을 SequenceMatcher로 비교했다. 형태소 분석이나 완전한 의미 변경량은 아니며 30% 가드 안이다.

✓ 의미 보존 검증: 6항 모두 통과.

1. 수치·날짜·고유명사: 원본 산출물과 수동 대조했고 숫자 토큰도 전후 동일하다.
2. 인과관계: 결측을 0으로 치환하면 의미가 달라짐, 일부 파일 오류와 전체 요청 오류의 방향을 유지했다.
3. 부정 표현: 실험을 새로 실행하지 않음, 라벨 검토가 자동 집계가 아님을 유지했다.
4. 직접 인용: 외부 직접 인용을 바꾸지 않았고 코드·링크 목적지도 전후 동일하다.
5. 핵심 주장: 화면은 근거와 한계를 전달하며 실험 범위를 늘리지 않는다는 주장을 보존했다.
6. 격식 수준: 한국어 기술 블로그 평서체를 유지했다.

snapshot과 계산 기록은 `outputs/blog-batch-2026-11-03/research-artifact-dashboard/` 아래 전후 Markdown과 `humanizer-metrics.json`, `meaning-preservation.json`에 보존했다.

## 미검증과 다음 검토

현재 배포가 같은 artifact를 읽는지, 현재 `/research`의 실제 화면, 더 넓은 label 기반 품질은 이번에 확인하지 않았다. top-level Generated의 의미를 UI에서 더 명확히 할 여지와 초기 dashboard 밖의 RAG·비용 영역을 후속으로 남겼다. 고정 commit 링크의 공개 접근과 블로그 검사·화면·CI·발행은 통합 담당자의 후속 범위다.

스킬의 근거 중심 구조는 실험 결과와 화면 기능을 혼동하지 않게 하는 데 도움이 됐다. 개선 후보는 산출물 생성 시각과 응답 시각을 별도로 점검하고, DTO schema가 향후 nullable 지표를 어떻게 처리하는지도 확인하는 항목이다.

## 배치 공통 검증과 작성 담당 범위 보정

통합 담당자가 공통 검사를 별도로 실행하고 있어, 본문의 미실행 표현을 이 글의 작성 담당자가 별도로 재실행하지 않았다는 뜻으로 좁혔다. 공통 실행 결과는 통합 담당자가 확인한 범위와 함께 검증 절에 추가한다. 앞의 변경률은 humanizer 적용 단계의 전후 snapshot을 비교한 값이며 이 범위 문구 보정은 포함하지 않는다. 보정 후 원고는 같은 outputs 디렉터리의 `after-scope-review.md`에 별도 보존했다. 이 보정에서 역사 수치·명령·원문 링크를 바꾸지 않았다.

최종 코드 대조에서 nullable 지원의 범위를 한 번 더 설명했다. retrieval 행의 macro 필드는 `requiredDouble`이므로 전부 실패한 실험의 null macro를 현재 계약은 영역 unavailable로 낮춘다. `reasonCoverage`의 null 지원을 모든 지표로 넓혀 주장하지 않도록 한계 단락을 보강했다. 이는 코드에서 도출한 조건부 동작이며 이번 runtime 관측은 아니다. 이 보강도 humanizer 변경률에는 포함하지 않으며 최종 snapshot에는 반영했다.


## 통합 검토·검증 보강 (2026-11-03)

초기 humanizer의 비교본은 `before.md`/`humanized.md`, 공통 검사와 사실·화면 수정을 반영한
최종 원고는 같은 출력 폴더의 `after.md`로 구분한다. 초안 담당과 다른 검토자가 요구·근거·
독자/채용 관점을 대조했고 root가 코드와 문장을 확인해 고친 뒤 재확인했다. 실제 외부 독자의
평가를 받은 것은 아니다.

공통 무료 검사는 backend344 test/check·frontend70 lint/build·AI70·RAG70·Node81 통과다.
각 원고의 검증 절에는 관련 결과와 의미 범위만 연결했다. Backend의 최초 날짜 fixture 오류는
준비 SQL2곳만UTC로 맞춰 재검증했으며 운영 로직·서비스·새 유료 호출은 변경/실행하지 않았다.
공개 발행과 전체 검토 결과는 `2026-11-03-eleven-post-publication-review.md`를 따른다.
