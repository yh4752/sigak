# 미발행 기술 글 11편 병렬 작성·통합 검토

작성: 2026-11-03
계획: `docs/superpowers/plans/2026-11-03-blog-publication-batch.md`

## 승인과 작업 분리

사용자 답변은 ‘미발행11편 진행 · 보조3개 허용’이다. 이번 배치만 보조1개 기본 제한을
최대3개로 늘렸고, 이전 리버트 원고9개는 복원하지 않았다. root가2편, 세 작성 에이전트가
각3편을 담당했다. 담당 파일 외의 공통 문서·앱·Git·발행은 root만 처리했다.

글의 기준은 사용자의 기존 요청인 humanizer, 제3자 도움, 채용 독자가 설계 판단을 확인할 수
있는 근거다. 개인 동기나 독립 구현 기여, 사용자 이해가 검증됐다고 만들어 쓰지 않았다.
technical-blog-learning-writer·humanizer·writing-plans·subagent-driven-development의
범위 분리·근거 대조·검토 절차를 적용했다. 새 유료 요약·생성·judge는 호출하지 않았다.

## 원고 목록

모두 `docs/blog/2026-11-03-<slug>.md`이며 개별 review도 같은 slug로 남겼다.

| slug | 주제 |
| --- | --- |
| metric-context-summary | 숫자와 측정 조건을 함께 보존하는 요약 |
| navigation-cache-and-summary-consistency | 복귀 대기와 완성된 요약을 함께 다루는 캐시 |
| ops-document-rag-evaluation | 운영 문서 corpus와 근거를 먼저 고정한 RAG 평가 |
| research-artifact-dashboard | null/0/unavailable과 실험 자료 시각을 보존하는 화면 |
| deployable-service-boundaries | 소스 배포·공개 API·데이터·운영 검증 경계 |
| source-rate-limit-boundary | host별 재시도와 실행 간 cooldown |
| postgres-neo4j-projection | PostgreSQL 원본과 재구축 가능한 그래프 사본 |
| graph-detail-explainability-evaluation | 관련 기사 설명의 존재와 검색 품질 구분 |
| search-catalog-expansion | 기사 수와 reviewed 질문 수를 분리한 확장 |
| honest-portfolio-evidence | 작은 실험으로 허용할 주장과 미검증 표시 |
| clean-install-lockfile-reproducibility | 기존 설치와 새 설치·audit의 재현성 구분 |

## 작성자와 다른 에이전트의 교차 검토

새 컨텍스트의 검토 에이전트3개가4/4/3편을 나눠 읽었다. 먼저 요구·범위를 확인하고
코드/기록·문체·중복·독자 도움을 검토했다. root는 지적된 코드와 문단을 직접 대조해 수정했고
각 담당 검토자가 변경 부분을 재확인했다. 최종 잔여 actionable 이슈는 없다고 보고했다.

| 발견 | 수정 근거·결과 |
| --- | --- |
| dashboard가 두 종류 시각을 모두 화면에서 제공한다고 서술 | API에는 있으나 retrieval/graph 원본 시각은 UI 미표시로 한정 |
| graph 질문별 자료 오류도 전체 unavailable로 일반화 | summary 유지+warning과 필수 자료 실패를 구분 |
| Neo4j 당시 비교 대안을 비목표 기능과 혼동 | 연결된 spec의 실제 A/B/C로 교체 |
| 내부 API404 행이 모든 method를 포함 | GET/HEAD로 한정, 다른 쓰기405 우선 경로 보존 |
| graph 실패율이 문맥 호출 실패만 포함한다고 읽힘 | 앞선 기사 상세 조회 실패도 포함한다고 명시 |
| root 원고의 humanizer 개별 trace 작성 전 검토 | trace를 작성한 뒤 재확인해 해소 |

이는 AI 에이전트들의 독립 대조다. 실제 외부 독자나 채용 담당자에게 평가받았다는 뜻은 아니다.

## humanizer와 의미 보존

각 원고의 `before.md`와 `humanized.md`는 초기 윤문 비교를 보존한다. 공통 검사 결과·사실 수정·
발행 메타데이터를 반영한 최종본은 `after.md`다. 원시 파일은 ignored
`outputs/blog-batch-2026-11-03/<slug>/`와 manifest/all-review-checks.json에 둔다.

root의 같은 어절 근사 방식으로 계산한 초기 윤문 변경률은2.75–6.33%다. 각 작성자의
근사 측정 방식과 수치가 조금 다를 수 있으며, 자동 AI 탐지 확률이나 문장 품질 점수가 아니다.
모든 글에서 수치 값 집합과 code block이 보존됐다. Catalog 글은 같은 문장의26을 두 번 쓰던
것을 한 번으로 줄여 숫자 출현 수만1회 줄었다. 문장 diff를 직접 읽어 같은 수치와 비교 의미가
첫 절에 남음을 확인했다. 이를 수치 값 변경과 구분해 자동 검사 예외로 기록했다.

수치/날짜/고유명사·인과·부정·직접 인용·핵심 주장·격식의6항을 각 trace와 함께 확인했다.
고정 Git 링크의 서로 다른 경로72개, 상대 Markdown 링크·코드 fence·secret pattern도 검사했다.
사실 대조 중 설명을 바로잡은 변경은 초기 윤문과 구분한다.

## root가 실제 실행한 무료 검사

| 영역 | 실제 결과 |
| --- | --- |
| backend test/check |344개, 실패/오류/skip0 |
| frontend test/lint/build |70개 통과, lint/build 성공 |
| AI pytest |70개 통과, 기존 dependency warning41개 |
| RAG pytest |70개 통과 |
| 검색·그래프 Node fixture |81개 통과, 실패/skip/cancel0 |
| 원본·발행본 validation |44개/47개 통과 |
| 블로그 test |264개, 실패/skip/cancel0 |
| 블로그 build |Astro81파일 오류/경고/hint0,62페이지·47페이지 Pagefind 색인 검증 |

첫 frontend/Node 실행은 공통 출력 폴더 생성과 동시에 시작해 로그 redirect에서 중단됐다.
폴더 생성 후 해당 명령만 실행했다. 앱 테스트 실패로 해석하지 않았다.

Backend 최초 전체344개 중1개가 실패했다. 진단 쿼리에서 세션은Asia/Seoul, current_date는
2026-11-03, UTC 날짜는2026-11-02임을 확인했다. 운영 claim은UTC를 쓰는데 테스트 fixture가
세션 날짜를 넣고 있었다. `ArticleSummaryServiceTest`의 준비 SQL2곳만UTC 날짜로 고친 뒤
해당 검사와 전체 test/check가 통과했다. 운영 로직·API·배포 JAR는 바꾸지 않았다.

## 렌더링과 발행 범위

11편의 desktop1280px와 mobile390px 본문·표·code를 읽었다. 초기 모바일에서 rate-limit488px,
Neo4j399px,catalog507px로 문서가 넘쳤다. 긴 표의 최소 너비가 본문 폭을 늘리고 있었다.
블로그 `prose.css`에 table block/overflow-x를 적용해 표 안에서 스크롤하도록 수정했다.
이후11편 모두 문서390px이며 긴 표는358px 폭 안에서 내부 스크롤한다. Neo4j 검증 표는
두 열로 정리하고 긴 실행 명령을 별도 code block으로 옮겨 좁은 열의 세로 끊김도 줄였다.

일괄 publish 도구가 동기화한 무관한 기존 변경2개는 사본을 남기고 보류했다. 이번 발행은
새 글11개와 렌더링에 필요한 스타일 수정이다.

## 공개 발행

- [블로그 PR45](https://github.com/yh4752/yonghyun-blog/pull/45)의 Node22 checks·Vercel preview
  success 후 head `a15bb05784eb2ead278703e7abc3029a5f6fda5f`를 지정해 병합했다.
- production commit `60d9c270694a60ecd3a939600b84de79759e63ae`의 Vercel과 main Node22 CI가 success다.
- 공개11주소의 HTTP200·제목·canonical 일치, 전체 archive와RSS의11편 노출을 확인했다.
  RSS의Sigak 글은34개다. 프로젝트 목록은 페이지가 나뉘어1~2페이지를 합쳐11편을 확인했다.
- 처음의 공개 검사에서는 프로젝트 첫 페이지에11편을 기대해 assertion이 실패했다.
  실제 다음 페이지 링크와 기존 pagination을 확인하고 기대 범위를 바로잡았다. 글 누락이 아니었다.
- 실제 공개 브라우저에서11편 모두 desktop1280/mobile390과 table 내부 스크롤 적용을 확인했다.
  원본/발행본 byte는 같고 발행 hash를 저장했다. 로컬 블로그 main도 병합 commit과 같으며 깨끗하다.
- 테스트 날짜 fixture 수정은 `c8d261e2235a9908c772dc104a80e3a280626441`로 별도 기록했다.
  원고·리뷰·상태 문서는 root가 별도 통합 기록하며 Sigak draft PR20은 병합하지 않는다.

HTTP 결과와 hash는 ignored 출력의 production-http.json/publication-hashes.json에 둔다.
기능 범위/API가 바뀌지 않아 ROADMAP은 유지한다. 외부 사람 평가·새 운영 성능·최신 audit·
전체RAG 품질·상시 운영은 이번 검증으로 완료됐다고 주장하지 않는다.

STATUS 갱신 뒤 무료 `validate-dataset --allow-draft`와 `prepare-review`도 통과했다.
evidenceValidated=true, fixed50/heading89, query60/reviewed0/fullEvaluationReady=false다.
사람 검토·생성·judge gate를 바꾸지 않았으며 원문 hash와 검토 문서만 갱신했다.
