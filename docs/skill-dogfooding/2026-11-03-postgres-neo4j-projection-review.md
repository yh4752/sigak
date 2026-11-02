# Neo4j projection 원고 작성·검토 기록

- 날짜: 2026-11-03
- 대상: `docs/blog/2026-11-03-postgres-neo4j-projection.md`
- 범위: 담당 원고, 이 검토 기록, 전후 보관본만 작성했다. 앱 코드·공통 문서·블로그 저장소·서비스·Git·유료 API는 이 담당 작업에서 변경하지 않았다.
- 적용 스킬: `/Users/yonghyun/.codex/skills/technical-blog-learning-writer/SKILL.md`, `/Users/yonghyun/.codex/skills/humanizer/SKILL.md`
- 전후 보관: `outputs/blog-batch-2026-11-03/postgres-neo4j-projection/before.md`, `after.md`
- 근거 링크 기준: `yh4752/sigak`의 `2b80dd22af9afef0c9fcc167a1e65044cb10f392`로 고정했다.

## 원고 범위와 학습 스킬 적용

사용자는 이번 일괄 작업의 원고 작성·검토·발행을 승인했다. 이 하위 작업은 초안과 검토 기록까지만 맡았다. 개인 학습 답변을 새로 받았다고 쓰지 않았고, 개인 동기·독립 구현·이해 수준을 지어내지 않았다. 질문을 다시 요구하는 대신 공개 글 끝에 스스로 설명해 볼 질문 네 개를 남겼다.

기능 목록을 소개하는 글에서 벗어나 원본과 사본의 책임, 단계별 도입, 재구축 비용, 공개 API의 장애 범위라는 한 설계 문제를 다뤘다. 공개 글에 개인 면접 답변은 넣지 않았다. Codex가 근거 대조와 초안·문장 검토를 지원했다는 사실은 검증 절에 밝혔다.

참고한 공통 작성 기준은 `AGENTS.md`, `docs/STATUS.md`, `docs/ROADMAP.md`, `docs/blog/WRITING_GUIDE.ko.md`, `docs/blog/topic-queue.md`의 해당 후보, 블로그 저장소의 `docs/blog-learning-pattern.md`, `docs/content-publishing-workflow.md`, `src/data/tags.json`이다. 기존 태그를 사용했고 `Neo4j`라는 새 태그를 추가하지 않았다.

## 대조한 근거와 판단

| 근거 | 원고에 반영한 판단 |
| --- | --- |
| `docs/superpowers/specs/2026-06-03-neo4j-graph-projection-design.md`와 대응 plan | 원본은 PostgreSQL, projection-only 내부 조회부터 시작. LLM 관계 추출·챗봇·탐색기는 초기 범위 밖 |
| `docs/superpowers/specs/2026-06-03-public-graph-aware-article-detail-design.md` | 별도 공개 endpoint의 요청 수 비용과 공용 DTO 계약 보존 |
| `ArticleGraphProjectionReader.kt`·`ArticleGraphProjectionDocument.kt` | API-ready target 필터, trim/lowercase, 표시 이름·순서, 최소 metadata |
| `Neo4jArticleGraphProjectionIndexer.kt` | 제약 생성 후 하나의 Neo4j 쓰기 트랜잭션에서 전체 재구축, 라벨 삭제 범위, 문서별 쿼리 비용 |
| `ArticleGraphProjectionRebuildService.kt` | 재구축 실패 기록과 PostgreSQL 비수정. 저장소 간 원자성으로 확대 해석하지 않음 |
| `ArticlePublicGraphContextService.kt`·공개 DTO | PostgreSQL 기사 확인 후 graph missing/runtime error를 빈 문맥으로 변환. 내부 timing/type 제외 |
| `frontend/src/pages/ArticleDetailPage.tsx` | 관련 기사와 이유를 article ID로 합침 |
| `CollectionAutomationService.kt`, STATUS의 2026-10-30·11-02 기록 | 6월 수동 rebuild를 현재 정책으로 쓰지 않음. 후속 자동화는 새 저장/복구 필요 시 refresh |
| `docs/blog/2026-06-03-dev-log.md` | 26/18/36/10, 404·400·빈 fallback, 당시 test/smoke를 역사적 관측으로 표기 |

저장소 도입을 정당화하려고 PostgreSQL 대비 성능 우위를 덧붙이지 않았다. 실제로 입증된 것은 관계를 투영하고 공개 화면에서 보조 문맥으로 조회하는 흐름이다. 사용자가 관련 이유를 더 유용하게 느꼈다는 데이터는 없다.

## humanizer 수동 검토

40개 패턴을 문장부호, 띄어쓰기, 품사, 어휘, 구조, 번역투의 여섯 범주로 훑었다. 기술 이름과 식별자·JSON·코드는 유지했다. 명사로 뭉친 판단 설명을 동작으로 풀고, 연결어미 뒤 쉼표와 불필요한 접속을 줄였다. `~다`체는 설명문 어조에 맞아 유지했다.

- 감지: S1 0건. S2 후보는 명사 중심 설명·간접 서술과 연결어미 뒤 쉼표이며, 세 문단에서 보수적으로 고쳤다. S3는 긴 기술 문장의 리듬을 함께 살폈다.
- 수정 후 자연도: A, 수동 편집 판정. AI 작성 여부의 확률이나 자동 탐지 결과가 아니다.
- 근사 변경률: **5.0%**. 전후 본문을 어절 단위로 비교해 약 49/987어절이 바뀌었다. frontmatter·코드 블록·링크 URL·단순 문장부호를 제외한 문자열 비교의 근사치다. 의미 변화량을 정밀 측정한 값은 아니다.
- 수치·검증 범위에 관한 편집: 과거 표와 배치의 공통 재검증을 혼동하지 않도록 표의 출처 문장을 조정했다. 담당자가 공통 테스트를 직접 실행했다고 쓰지 않았다.

| 수정 전 | 수정 후 | 이유 |
| --- | --- | --- |
| 이 선택의 근거는 단계별 기능 검증과 저장소 책임의 일관성에 있다. | 먼저 저장·조회 흐름을 확인하고 원본의 책임을 한곳에 두려는 선택이었다. | 추상 명사 연결을 구체 동작으로 바꿈 |
| 관계 스키마나 주제 정규화가 바뀌었을 때도 같은 경로를 사용할 수 있다. | 관계 스키마나 주제 정규화가 바뀌었을 때도 같은 경로를 쓴다. | 불필요한 가능 표현을 줄임 |
| 가용성을 얻는 대신 빈 관계와 조회 장애를 공개 응답만으로 구분하기 어렵다. | 본문을 계속 보여 줄 수 있지만 공개 응답만 보면 관계가 없는지 조회가 실패했는지 구분하기 어렵다. | 추상 결과를 실제 화면 동작과 관측 한계로 풀어 씀 |

## 의미 보존 6항

- [x] 수치·날짜·고유명사: 26/18/36/10, 기사 4·1·5, 상태 코드와 과거 날짜를 유지했다.
- [x] 인과관계: projection 분리와 장애 범위 축소의 방향을 뒤집지 않았다.
- [x] 부정 표현: 성능 우위·관계 품질·분산 원자성의 미입증 범위를 유지했다.
- [x] 직접 인용: 외부 직접 인용은 없고 코드·JSON은 바꾸지 않았다.
- [x] 핵심 주장·결론: 원본은 PostgreSQL이고 Neo4j는 재구축 가능한 보조 조회 사본이라는 주장을 유지했다.
- [x] 격식 수준: 평서형 설명문을 유지했다.

✓ 의미 보존 검증: 6항 모두 통과.

## 확인 범위와 남은 검증

담당자는 관련 코드·설계·과거 로그를 읽어 주장과 대조했다. 전후 보관과 근사 변경률 계산만 수행했으며 앱 테스트·live smoke·재구축은 직접 실행하지 않았다. 2026-11-03 공통 테스트와 원고 frontmatter·링크·발행 검사는 root 담당 작업에서 별도로 기록한다. 이 검토 기록만으로 그것들이 통과했다고 판정하지 않는다.

미검증은 실제 Neo4j 장애·복구 재실행, 대규모 재구축, concurrent update의 일관성, 사람이 판정한 relation reason 품질과 사용자 효용이다. 초기 수동 재구축과 현재 자동화는 구분했으며 과거 노드 수를 현재 운영 데이터 개수로 쓰지 않았다.

스킬이 도움이 된 부분은 선택지와 실패 경계를 중심으로 범위를 줄인 점이다. 보완할 부분은 개인 학습 질문을 의무적으로 기다리는 절차가 일괄 작성 승인과 충돌할 수 있다는 점이다. 이런 경우에는 승인된 artifact를 완성하되 개인 답변을 만들어 넣지 않는 방식이 필요하다.


## 통합 검토·검증 보강 (2026-11-03)

초기 humanizer의 비교본은 `before.md`/`humanized.md`, 공통 검사와 사실·화면 수정을 반영한
최종 원고는 같은 출력 폴더의 `after.md`로 구분한다. 초안 담당과 다른 검토자가 요구·근거·
독자/채용 관점을 대조했고 root가 코드와 문장을 확인해 고친 뒤 재확인했다. 실제 외부 독자의
평가를 받은 것은 아니다.

공통 무료 검사는 backend344 test/check·frontend70 lint/build·AI70·RAG70·Node81 통과다.
각 원고의 검증 절에는 관련 결과와 의미 범위만 연결했다. Backend의 최초 날짜 fixture 오류는
준비 SQL2곳만UTC로 맞춰 재검증했으며 운영 로직·서비스·새 유료 호출은 변경/실행하지 않았다.
공개 발행과 전체 검토 결과는 `2026-11-03-eleven-post-publication-review.md`를 따른다.
