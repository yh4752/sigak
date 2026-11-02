# 그래프 설명 평가 원고 작성·검토 기록

- 날짜: 2026-11-03
- 대상: `docs/blog/2026-11-03-graph-detail-explainability-evaluation.md`
- 적용 스킬: `/Users/yonghyun/.codex/skills/technical-blog-learning-writer/SKILL.md`, `/Users/yonghyun/.codex/skills/humanizer/SKILL.md`
- 전후 보관: `outputs/blog-batch-2026-11-03/graph-detail-explainability-evaluation/before.md`, `after.md`
- 근거 링크 기준: `yh4752/sigak`의 `2b80dd22af9afef0c9fcc167a1e65044cb10f392`.
- 담당 범위: 이 원고·검토 기록·전후 보관본. 앱 코드·서비스·Git·블로그 저장소·실험 결과 파일은 변경하지 않았다.

## 글의 문제와 학습 스킬 적용

검색 순위를 바꾸지 않는 상세 설명 기능을 검색 개선의 근거로 사용하는 오류를 중심 문제로 잡았다. 새 백엔드 평가 API, 수동 로그, 기존 Node runner 확장의 대안을 비교한 뒤 현재의 공개 API 측정 경계를 설명했다. 독자가 지표를 다시 계산할 수 있도록 분모와 실제 질문 한 건을 함께 넣었다.

사용자가 일괄 작성·검토·발행을 승인한 작업이므로 개인 이해 답변을 새로 요구하지 않았다. 개인 학습 수준·독립 구현·동기는 주장하지 않았다. Codex가 근거 대조·초안·문장 검토를 지원한 범위를 공개 글에 썼고, 답변이 필요한 학습 질문은 글 끝에 남겼다. 개인 면접 답변 노트는 만들지 않았다.

공통 기준으로 AGENTS, STATUS·ROADMAP 관련부, WRITING_GUIDE, topic-queue 해당 후보와 블로그 저장소의 학습 패턴·발행 태그 정책을 읽었다. `type: research`, 기존 Evaluation·Search·Architecture·Testing 태그, `draft: true`, 정확한 canonical path와 repository를 사용했다. 관련 글은 기존 공개 글 두 개로 제한했다.

## 수치와 코드의 대조

| 근거 | 확인한 범위 |
| --- | --- |
| graph-aware 평가 spec와 `2026-06-04` plan | 설명 정보 평가, 공개 API 선택, 내부 API 대안과 미포함 범위 |
| `graph-runner.mjs` | 공개 검색 top-k를 source로 선택, 상세·문맥 조회, labels checksum·reason provenance |
| `graph-metrics.mjs` | positive 합집합, 중복·자기 관계 제외, 전체 positive/검색 hit 등 서로 다른 분모 |
| `graph-client.mjs` | 공개 graph 요청 왕복 지연, 응답 오류와 empty 분리, 상세 조회는 별도 |
| `ArticlePublicGraphContextService.kt` | Neo4j 실패를 빈 공개 context로 숨기는 한계 |
| `graph-context.metrics.by-query.json` | `graph rag failure`의 positive 4·1 / top5 4·23·2·20·19 / missed 1 / among-search-hits 0 |
| `graph-context.metrics.summary.json`·`report.md` | 질문별 macro 2/3·1/3, reason 1, failure·empty 0, 약 33.53ms와 warnings |
| `docs/blog/2026-06-04-dev-log.md` | 실제 projection 26개와 당시 81개 Node 테스트, 26/18/36/10 재구축 |
| label JSON·기존 strict comparison 글 | frozen label catalog 6개와 reviewed 질문 3개. 별도 strict retrieval와 직접 비교 금지 |

검토 중 특히 좁힌 주장:

- Graph coverage는 그래프 related ID가 **known positive**에 얼마나 닿았는지다. 이유 문장 품질이나 실제 사용자 효용 점수가 아니다.
- Reason Coverage 1은 질문별 1·1·null에서 null을 뺀 평균이다. 전체 관계 설명률로 확대하지 않았다.
- Related baseline과 graph coverage는 둘 다 약 1/3이었다. 그래프가 관련 기사 수를 늘렸다고 쓰지 않았다.
- 검색에서 빠진 positive는 source로 조회하지 못해도 다른 source의 관련 대상으로 만날 수 있다. 검색 누락과 그래프에서 절대로 못 만난다는 주장을 구분했다.
- public failure 0은 Neo4j 오류 0을 보장하지 않는다. backend empty fallback을 runner가 원인별로 구분하지 못한다.
- label catalog는 6개, 실제 검색 projection은 26개였다. 23 같은 미판정 대상이 있으므로 완전한 catalog 일치 평가로 설명하지 않았다.
- latency는 graph-context HTTP 요청·JSON 읽기이며 기사 상세 조회·전체 렌더링·순수 Neo4j 지연과 다르다.

## humanizer 수동 검토

본문 전체를 문장부호·띄어쓰기·품사·어휘·구조·번역투의 여섯 범주에서 살폈다. 수식 대신 짧은 집합 표기와 표를 유지했고 기술 식별자는 바꾸지 않았다. 제목의 어색한 술어, 추상적인 ‘관계 부재/장애 축소 응답’, 명사만 나열한 한계 설명을 구체 문장으로 고쳤다.

- 감지: S1 0건. S2 후보는 추상 명사·간접 서술과 제목/설명 리듬이었다. 고친 세 문단에서 동사로 풀었다. S3 수준의 접속 반복도 줄였다.
- 수정 후 자연도: A, 수동 편집 판정. 자동 탐지나 작성 주체 판정은 실행하지 않았다.
- 근사 변경률: **5.9%**, 약 69/1179어절. frontmatter·코드 블록·링크 URL·단순 문장부호를 제외한 전후 본문의 어절 비교다. 의미 변화량을 정밀 측정한 수치는 아니다.
- 별도 범위 수정: 과거 실행 표의 출처를 명시하면서 배치의 현재 공통 재검증과 충돌할 수 있는 ‘이번 작성에서는 재실행하지 않았다’의 포괄 문구를 줄였다.

| 수정 전 | 수정 후 | 이유 |
| --- | --- | --- |
| 관련 이유가 보인다는 것과 검색이 좋아졌다는 것은 다른 평가다 | 관련 기사 설명을 검색 순위와 따로 평가한 이유 | 제목의 어색한 주술 관계를 고침 |
| 이는 원본에 저장돼 있던 이유를 projection에서 읽었다는 뜻이다. 독립적으로 사실 확인한 설명이라는 뜻은 아니다. | 원본에 있던 이유를 projection에서 읽었다는 뜻이며, 그 내용을 독립적으로 사실 확인했다는 뜻은 아니다. | 같은 뜻의 끝맺음을 줄임 |
| 주제의 정확성, 동의어 통합, 독자가 느끼는 도움을 점수화하지 않는다. | 주제가 정확한지, 동의어를 잘 묶었는지, 독자에게 도움이 됐는지는 점수에 들어 있지 않다. | 추상 명사 목록을 독자가 점검할 질문으로 풂 |
| 그 응답이 정상적인 관계 부재인지 장애 축소 응답인지 알 수 없다. | 그 응답만 보고 관계가 없는지 장애 때문에 비었는지 알 수 없다. | 구현과 직접 대응하는 쉬운 말 사용 |

## 의미 보존 6항

- [x] 수치·날짜·고유명사: 6개 label catalog / 26개 live projection / 질문 3개 / k5 / 각 coverage·33.53ms를 유지했다.
- [x] 인과관계: 검색 순위는 그대로이고 상세 문맥을 평가한다는 관계를 유지했다.
- [x] 부정 표현: 사실 정확도·검색 개선·Neo4j 오류 부재를 입증하지 않았다는 범위를 유지했다.
- [x] 직접 인용: 외부 직접 인용은 없으며 지표 식별자와 예시 ID는 보존했다.
- [x] 핵심 주장·결론: 연결·이유 존재·검색 누락을 분리하고 smoke 이상으로 해석하지 않는 결론을 유지했다.
- [x] 격식 수준: 평서형 설명문 어조를 유지했다.

✓ 의미 보존 검증: 6항 모두 통과.

## 검증과 미검증

담당자는 로컬 코드·저장 JSON·보고서·과거 로그를 읽었다. 파일 작성과 전후 비교 계산 외에 Node 테스트·앱 테스트·서비스 호출·실제 graph smoke는 직접 실행하지 않았다. 공통 2026-11-03 재검증과 발행 gate는 root가 별도로 기록한다. 과거의 81개 테스트 기록을 그 재검증의 증거로 대체하지 않았다.

현재 원고가 입증하지 않는 범위는 사람의 이유 정확도 판정, 실제 독자의 효용, 충분한 라벨 다양성, 완전한 live/frozen 대상 일치, 큰 데이터·동시 트래픽 지연이다. 새 실행·실제 서비스 실패·유료 AI는 이 담당 작업에서 만들지 않았다.

스킬은 지표마다 무엇을 묻는지 드러내는 데 도움이 됐다. 추가 개선 후보는 일반적인 ‘검증’ 목록만으로 끝내지 않고 분모·null 평균·라벨 밖 후보·공개 fallback의 관측 사각지대를 확인하는 평가 글 체크리스트다. 이런 점은 자연스러운 문장만 고치는 humanizer로는 보완되지 않으므로 코드 대조가 먼저 필요했다.


## 통합 검토·검증 보강 (2026-11-03)

초기 humanizer의 비교본은 `before.md`/`humanized.md`, 공통 검사와 사실·화면 수정을 반영한
최종 원고는 같은 출력 폴더의 `after.md`로 구분한다. 초안 담당과 다른 검토자가 요구·근거·
독자/채용 관점을 대조했고 root가 코드와 문장을 확인해 고친 뒤 재확인했다. 실제 외부 독자의
평가를 받은 것은 아니다.

공통 무료 검사는 backend344 test/check·frontend70 lint/build·AI70·RAG70·Node81 통과다.
각 원고의 검증 절에는 관련 결과와 의미 범위만 연결했다. Backend의 최초 날짜 fixture 오류는
준비 SQL2곳만UTC로 맞춰 재검증했으며 운영 로직·서비스·새 유료 호출은 변경/실행하지 않았다.
공개 발행과 전체 검토 결과는 `2026-11-03-eleven-post-publication-review.md`를 따른다.
