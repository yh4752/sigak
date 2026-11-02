# 검색 catalog 확장 원고 작성·검토 기록

- 날짜: 2026-11-03
- 대상: `docs/blog/2026-11-03-search-catalog-expansion.md`
- 적용 스킬: `/Users/yonghyun/.codex/skills/technical-blog-learning-writer/SKILL.md`, `/Users/yonghyun/.codex/skills/humanizer/SKILL.md`
- 전후 보관: `outputs/blog-batch-2026-11-03/search-catalog-expansion/before.md`, `after.md`
- 근거 링크 기준: `yh4752/sigak`의 `2b80dd22af9afef0c9fcc167a1e65044cb10f392`.
- 담당 범위: 이 원고·검토 기록·전후 보관본만 편집. 공통 문서·코드·Git·블로그 저장소·DB·projection은 조작하지 않았다.

## 원고 범위와 사용자 기여 경계

사용자의 일괄 원고 작성·검토·발행 승인에 따라 작성한 research 글이다. 새 학습 답변을 받았거나 사용자가 이유를 직접 설명했다고 쓰지 않았다. 개인 동기나 독립 구현 이력을 덧붙이지 않았고 Codex의 근거 대조·초안·문장 검토 지원을 공개 글에 표시했다. 공개 질문 네 개는 남겼지만 개인 면접 답변은 만들지 않았다.

핵심은 데이터 개수 증가를 품질 평가 완료로 바꾸지 않는 것이다. 문제·대안·결정·검증·남은 일의 흐름을 사용하되 다른 두 원고보다 운영 절차와 artifact 대응표를 중심으로 썼다. AGENTS, STATUS·ROADMAP, WRITING_GUIDE, topic-queue 후보와 블로그 학습 패턴·tag intake·허용 태그를 읽었다. 새 태그 없이 Evaluation·Search·Testing·Tooling·PostgreSQL을 사용했다.

## 근거 대조

| 근거 | 원고에 반영한 사실과 제한 |
| --- | --- |
| `2026-06-05-catalog-expansion-design.md`와 plan | 기존 export 재사용, 새 파일/ID, 10–15개 reviewed 질문과 별도 expanded 경로는 후속 단계 |
| `docs/blog/2026-06-05-dev-log.md` | 실제 export 41개, old catalog6·query3·explicit labels12 보존, 당시 새 label 없음 |
| `articles.catalog.json` | baseline ID와 생성 시각, seed 예시·영어 요약 포함 |
| `articles.catalog.api-ready-2026-06-05.json` | frozen ID·source·기사 정보, 6월 snapshot이며 현재 운영 목록이 아님 |
| 기존 label JSON | catalogArticleCount·reviewed query·질문별 판정은 각각 별도 값 |
| `SearchCatalogArticleReader.kt` | ArticleService 경계 재사용 |
| `SearchCatalogExportService.kt`·`SearchCatalogFactory.kt` | limit·빈 목록 실패·시각/ID 부여, 현재 summary null 시 sourceExcerpt 사용 |
| `ArticleService.kt` | 현재 원문 발췌/요약 경계와 공개 가능한 기사 조회 |
| `labels.mjs` | reviewed≥1과 positive≥1 등 기본 검사. 실제 catalog 파일/ID 집합·10개 기준은 검증하지 않음 |
| `labeling.html` | catalogId 불일치 시 경고 뒤 import, 저장 상태 `reviewed`, export 전 catalog 밖 ID 경고 |
| STATUS 현재 출처/홈 정책과 후속 작업 | 과거 6/41개를 현재 홈/검색 데이터 개수로 쓰지 않고 expanded label 작업은 미완으로 구분 |

설계에 있는 gate를 구현된 강제 검사라고 쓰지 않았다. 특히 ‘catalogId로 불일치를 막는다’는 설명을 그대로 확대하지 않고 metadata·수동 절차·도구의 실제 검사 범위를 나눴다. 기사 20개·reviewed 질문 10개도 프로젝트 내부 진행 기준이며 통계적 충분성의 보편 기준으로 제시하지 않았다.

## humanizer 수동 검토

문장부호, 띄어쓰기, 품사, 어휘, 구조, 번역투의 여섯 범주에서 40개 패턴을 확인했다. 파일 이름·필드·날짜·명령은 그대로 두고 명사 밀도가 높은 문장, 긴 병렬 절과 추상 서술을 줄였다. 경어체를 섞기보다 설명문 `~다`체를 유지하고 문장 길이를 조절했다.

- 감지: S1 0건. S2 후보는 긴 병렬 문장과 추상 명사 중심 서술이었다. 네 문단을 보수적으로 고쳤다. S3 수준의 접속·반복은 문장 분리로 줄였다.
- 수정 후 자연도: A, 수동 편집 판정. 작성 주체 감별이나 자동 품사·AI 탐지는 실행하지 않았다.
- 근사 변경률: **6.1%**, 약 61/1006어절. frontmatter·코드 블록·링크 URL·단순 문장부호를 제외한 본문 어절 비교의 근사값이다. 의미 변화량이나 외부 탐지 점수는 아니다.

| 수정 전 | 수정 후 | 이유 |
| --- | --- | --- |
| 후보 기사에 비슷한 주제의 다른 글이 늘어야 검색의 구분 능력을 살펴볼 수 있고, 질문과 정답 후보를 사람이 검토해야 무엇을 잘 찾았는지 판정할 수 있다. | 후보에 비슷한 주제의 다른 글이 늘어야 검색이 그 차이를 구분하는지 살펴볼 수 있다. 질문과 정답 후보도 사람이 검토해야 무엇을 잘 찾았는지 판정할 수 있다. | 긴 병렬 절을 나누고 추상 능력 명사를 풀어 씀 |
| 평가에는 어느 시점의 기사 집합인가라는 경계가 하나 더 생긴다. | 평가에서는 원본 DB와 검색 사본을 구분하는 데 더해 어느 시점의 기사 집합인가도 정해야 한다. | ‘경계가 생긴다’ 대신 실행자가 정할 조건을 설명 |
| 통계적으로 충분한 평가를 자동 보장하는 보편적 임계값이 아니다. | 이 숫자를 넘었다고 통계적으로 충분한 평가가 되지는 않는다. | 추상 수식과 명사 연결을 줄임 |

## 의미 보존 6항

- [x] 수치·날짜·고유명사: catalog6/41, query3, labels12, ID3/16, 기준20/10–15, catalogId를 보존했다.
- [x] 인과관계: 기사 증가가 평가 완료를 뜻하지 않으며 라벨/실행 조건의 확인이 필요하다는 방향을 유지했다.
- [x] 부정 표현: 확장 라벨·결과의 미완, 자동 일치 검사 부재를 완료로 바꾸지 않았다.
- [x] 직접 인용: 외부 직접 인용은 없고 실행 명령·저장 필드는 바꾸지 않았다.
- [x] 핵심 주장·결론: 이전 artifact 보존과 새 catalog·label·projection 대응 관리라는 결론을 유지했다.
- [x] 격식 수준: 설명문 평서형 어조를 유지했다.

✓ 의미 보존 검증: 6항 모두 통과.

## 검증 범위와 다음 확인

담당자가 직접 한 일은 설계·코드·저장 파일·과거 검증 기록의 읽기와 원고·전후 비교 작성이다. DB export, 실제 benchmark, 서비스 호출과 테스트는 직접 실행하지 않았다. 2026-11-03 공통 gate와 원고 링크·frontmatter·발행 검사는 root 작업에서 별도로 기록한다. 역사적 테스트 기록은 날짜를 붙여 현재 관측과 구분했다.

미검증: 새 catalog 브라우저 import, 확장 reviewed 라벨, expanded retrieval/graph 품질 평가, live projection과 frozen 자료의 자동 일치 검증, model/text snapshot의 완전한 재현성. 이번 작성 중 기존 baseline이나 결과 파일을 덮어쓰지 않았다. 홈 활성 출처·데모 제외의 최신 정책을 과거 snapshot에 소급 적용하지 않았다.

스킬이 유용했던 부분은 ‘준비됨’과 ‘검증됨’을 구분하고 태그를 새로 늘리지 않은 점이다. 개선 후보는 설계 문서의 gate를 실제 자동 검사로 오해하지 않도록 구현과 강제 조건의 차이를 별도 표로 점검하는 것이다. 자연스러운 표현보다 먼저 그 차이를 확인해야 사실을 과장하지 않는다.


## 통합 검토·검증 보강 (2026-11-03)

초기 humanizer의 비교본은 `before.md`/`humanized.md`, 공통 검사와 사실·화면 수정을 반영한
최종 원고는 같은 출력 폴더의 `after.md`로 구분한다. 초안 담당과 다른 검토자가 요구·근거·
독자/채용 관점을 대조했고 root가 코드와 문장을 확인해 고친 뒤 재확인했다. 실제 외부 독자의
평가를 받은 것은 아니다.

공통 무료 검사는 backend344 test/check·frontend70 lint/build·AI70·RAG70·Node81 통과다.
각 원고의 검증 절에는 관련 결과와 의미 범위만 연결했다. Backend의 최초 날짜 fixture 오류는
준비 SQL2곳만UTC로 맞춰 재검증했으며 운영 로직·서비스·새 유료 호출은 변경/실행하지 않았다.
공개 발행과 전체 검토 결과는 `2026-11-03-eleven-post-publication-review.md`를 따른다.
