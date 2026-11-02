# 검색 품질·장애 대응 평가 글 검토

작성: 2026-11-03
대상: `docs/blog/2026-11-03-strict-hybrid-public-search-evaluation.md`

## 요청·범위와 사용자 답변

사용자는 앞선 두 글과 같은 검토·발행 과정을 다음 주제에도 요청했다. 글의 중심 메시지를
묻자 ‘검색 품질과 장애 대응을 나눠 평가하는 이유’를 선택했다. 이 답변은 글의 강조점이며
과거 구현의 모든 설계 결정을 사용자가 독립적으로 했다는 증거로 쓰지 않았다.

앱 코드는 변경하지 않으며 주제형 글1개만 발행한다. 다른 미발행 글과 리버트 원고는 범위 밖이다.
기술 글 작성·검토에는 technical-blog-learning-writer와 humanizer를 적용했다.

## 읽은 근거와 바로잡은 해석

- CandidateService/PublicSearchService/EvaluationService: 실패와 정상 빈 결과,
  공개 fallback과 엄격한 FAILED, PostgreSQL 재조회·stale 후보를 대조했다.
- 내부 controller/properties·Caddyfile: 기본 비활성, PUBLIC 요청 거부,
  공개 프록시의 내부 경로 제한을 확인했다. disabled 설정을 인증으로 표현하지 않았다.
- metrics.mjs: strong/acceptable 집합과 MRR의 strong 기준, macro/effective의 분모,
  모두 실패 시 null/0, 동률 배열을 확인했다.
- search-client.mjs/runner.mjs: last-search metadata 경합, metadata 누락 시 false 집계,
  HTTP 실패 시 예외 중단을 확인했다. 모든 실패가 보고서에 남는다고 쓰지 않았다.
- EvaluationService의 lazy 후보 재사용 때문에 뒤의 HYBRID total 시간이 독립 실행 시간이
  아닐 수 있다. 공개 왕복 시간과 섞어 속도 순위를 주장하지 않았다.
- 2026-06-02의 labels/run/report: 기사6개·reviewed 질문3개·K5·limit20·deterministic8차원.
  현재 의미 검색 품질이나 실제 서비스의 신규 측정으로 쓰지 않았다.
- Git history의 7ef308e 원고와 4fb9c3a revert를 참고했으며 새 글을 현재 근거로 작성했다.
  근거8개는 공개 commit `409ae7bb95adfbd47b373893a70e308911aef6bc`에 고정했다.

## 독자·채용 관점

| 기준 | 원고의 근거 |
| --- | --- |
| 처음 읽는 독자가 문제를 이해하는가 | 벡터 실패에도 키워드 결과를 반환하는 사례로 시작 |
| 대안과 비용이 보이는가 | 공개 결과 재명명·공개 mode switch·내부/공개 분리 비교 |
| 다른 프로젝트에 적용할 기준이 있는가 | 장애 대응과 방식 품질의 질문, 평균 분모와 전송 실패 범위를 구분 |
| 점수를 과장하지 않는가 | 가상 계산과 과거 smoke·현재 fixture를 구분; 임베딩·표본·시간 측정 한계 명시 |
| 구현 판단을 따라갈 수 있는가 | 서비스·계산·호출·tests·snapshot의 고정 근거8개 연결 |
| 작성자의 역할을 과장하지 않는가 | 사용자의 이번 강조점 선택과 Codex 지원, 과거 기록/현재 검사를 구분 |

이는 Codex가 독자·채용 관점으로 수행한 검토다. 실제 외부 독자·채용 담당자의 평가나
사용자의 독립 설명 능력을 확인했다고 주장하지 않는다.

## humanizer와 의미 보존

문장부호·띄어쓰기·품사/명사화·어휘·구조·번역투의 40개 패턴을 수동 검토했다.
명확한 S1은 없었고, 개념 설명의 추상적인 명사와 반복적인 대조 표현을 소폭 다듬었다.
자연도 수동 평가는 A이며 자동 AI 탐지 확률을 산출한 것은 아니다.

| 수정 전 | 수정 후 | 이유 |
| --- | --- | --- |
| 검색 결과의 점수를 계산하려면 먼저 질문별 관련도 라벨이 필요하다 | 검색 결과에 점수를 매기려면 질문마다 어떤 글이 관련 있는지 먼저 정해야 한다 | 라벨이 필요한 행동을 풀어 설명 |
| 둘 다 필요하다. 첫 번째는 … 두 번째는 … | 완료한 경우의 검색 품질과 실패를 포함한 전체 시도의 결과를 함께 읽을 수 있다 | 정형적인 반복을 줄임 |
| 실패율과 축소 동작 비율이 같은 뜻은 아니다 | 요청 실패율과 대체 경로 사용 비율이 각각 필요한 이유다 | 정의한 두 지표를 직접 연결 |

frontmatter·code·링크 대상·문장부호를 제외한 근사 비교는 원문1573어절, 수정본1567어절,
변경104어절(6.6%)이다. 문장 수정 외에 검증 제목과 모바일 상태표 정리도 포함한다.
수치 출현 Counter와 code block은 동일하며 고정 Git 경로8개가 존재한다.
✓ 의미 보존 검증: 수치/날짜/고유명사, 인과, 부정, 인용, 핵심 주장, 격식의 6항 모두 통과.

## 이번 세션에서 직접 실행한 검증

- 관련 backend 5개 class의22개 검사 통과, 실패/오류/skip0. Fake/Mock 의존성을 사용한다.
- Node benchmark 전체81개 검사 통과, 실패/skip/cancel0. 가짜 응답·localhost HTTP fixture다.
- Gradle cache 접근과 localhost listen 제한으로 최초 실행이 중단됐고, 필요한 로컬 접근을
  허용해 같은 검사를 다시 실행했다. 코드 변경·실서버 의존성 장애 주입은 없었다.
- 가상 완료1건/실패1건은 실제 집계 함수로 macro1/effective0.5/실패율0.5를 확인했다.
- 앱 전체 검사, 새로운 운영 검색·projection rebuild·유료 AI 호출은 실행하지 않았다.

## 발행 형식·로컬 화면 검증

- `npm run publish:posts -- --project sigak` 실행 후 무관한 동기화2개를 보류했다.
  백업을 ignored outputs에 보관했고 이번 변경은 새 글1개뿐이다.
- source validation33개, published validation36개 통과. 처음의 검증 제목 경고는
  `## 검증: 이번에 다시 실행한 검사`로 맞춰 없앴다.
- 블로그264개 테스트 통과, 실패/skip/cancel0. Astro81파일 오류/경고/hint0,
  최종49페이지 빌드와36페이지 Pagefind 색인 검증을 확인했다.
- 데스크톱1280px에서 문서 폭1280, 모바일390px에서 문서 폭390이었다.
  긴 code의 폭356/scroll 최대611이며 블록 내부에서 스크롤한다.
- 처음 모바일4열 상태표의 한글이 세로로 끊겨2열로 바꾸고 실패 경로 이름을 간결하게 했다.
  표7개·code4개·목차 이동을 확인했다. 공개 배포에서도 최종 표를 확인한다.
- plain code 배경/글자는 dark rgb(36,41,46)/rgb(225,228,232),
  light rgb(255,255,255)/rgb(36,41,46)다. 브라우저의 크기·테마를 복원했다.

## CI·병합

- [블로그 PR #44](https://github.com/yh4752/yonghyun-blog/pull/44)의 Node22 checks와
  Vercel preview success를 확인하고 head `48b7334caeccd0d8c746f3db2f60c29d439d5923`을 지정해 병합했다.
- 병합 commit은 `1c58f9fe3cd149c6cf7eab37667dde69cac5ae8d`다. 변경 파일은 새 글1개다.
- 병합 commit의 production Vercel과 main Node22 CI success를 확인했다.
- [공개 글](https://yonghyun-blog.vercel.app/blog/sigak/2026-11-03-strict-hybrid-public-search-evaluation),
  `/blog`, `/projects/sigak`, `/rss.xml` 모두 HTTP200이며 새 글과 canonical을 확인했다.
- 실제 공개 브라우저에서 제목·최종2열 상태표·code4개·표7개·상대 Markdown 링크0개,
  production dark 코드 대비를 확인했다. 해당 탭은 사용자에게 남겼다.
- 블로그 로컬 main은 병합 commit과 같고 깨끗하다. 원본과 발행본 byte도 일치한다.

원시 관측·검사 출력과 보류 사본은 Git 제외 `outputs/search-evaluation-blog-2026-11-03/`에 남겼다.
