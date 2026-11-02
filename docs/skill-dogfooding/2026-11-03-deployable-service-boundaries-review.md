# 배포 경계 검증 글 검토

대상: `docs/blog/2026-11-03-deployable-service-boundaries.md`
작성: 2026-11-03

사용자는 미발행11편을 보조3개로 병렬 작성하고 앞선 글처럼 검토·발행하도록 승인했다.
이 글은 소스 배포 구성과 경계 검증을 다루며 새 서버 구매·운영 배포는 범위 밖이다.
technical-blog-learning-writer와 humanizer를 적용했다.

## 근거와 검토

- 현재 Dockerfile·배포 Compose·Caddy·automation service/properties·HTTP smoke와10/30·11/02
  기록을 대조했다. 실제 환경 파일·비밀번호·키를 읽거나 공개하지 않았다.
- 개발 설정 공개·화면/API 분리·단일 서버 경계의 대안을 설명했다.
- web host port와 내부 서비스, API 제한과 SPA fallback 순서, summary POST 예외를 구분했다.
- 초기 dev41/배포seed5, 과거6→7개 smoke, 초기6개 출처와최근3개 정책을 시점별로 설명했다.
- fixed-delay는 완료 뒤10분이며 wall-clock cron·분산 스케줄러라고 설명하지 않았다.
- 당시 ARM/Jammy·Linux lock 문제와11/02의 clean backend 소스 빌드 후속을 함께 기록했다.
- 임시HTTPS 접근과 상시 호스팅·custom domain TLS·백업·HA·장기 수집 품질을 구분했다.

처음 보는 독자가 실행 상태와 배포 근거를 구분하고 자신의 서비스에서도 검사할 경계를 찾을 수
있는지 검토했다. 코드 근거6개를 고정했다. 채용/외부 독자의 실제 평가나 작성자의 독립 구현을
주장하지 않는다.

## humanizer

40개 패턴을 문장부호·띄어쓰기·품사·어휘·구조·번역투 범주로 수동 검토했다.
명확한 S1은 없었고 부정적인 부연과 현재성 표현을 직접적인 범위 설명으로 바꿨다. 수동 자연도 A다.

| 수정 전 | 수정 후 |
| --- | --- |
| 네트워크 격리가 관리자 인증을 대신한다고 보지는 않았고 | 서버를 옮길 때는 이 네트워크 분리와 함께 방화벽·관리자 인증도 확인해야 한다 |
| 이 두 수는 당시 초기 관측이며 현재 운영 기사 수가 아니다 | 두 값은 그때의 초기 관측으로 한정한다 |

초안922어절·윤문913어절, 근사 변경47어절(5.1%)이다. 숫자 출현 Counter와 code block은 동일하다.
이는 자동 AI 탐지 확률이 아니다. 수치/날짜/고유명사·인과·부정·직접 인용·핵심 주장·격식6항을 확인했다.
✓ 의미 보존 검증: 6항 모두 통과.

전후는 ignored `outputs/blog-batch-2026-11-03/deployable-service-boundaries/`에 두며
후속 사실 수정·발행 결과와 초기 윤문 비교를 구분한다.

## 실제 검사와 후속

root가 backend344 test/check·FE70 lint/build·AI70·Node81·RAG70을 실행해 통과했다.
backend fixture의UTC 날짜 정리는 별도 진단 기록에 남긴다. 이번 원고 작성에서 이미지 빌드·
운영 HTTP smoke·외부 터널·projection·수집을 재실행하지 않았다. 현재 의미의 상시 운영이나
배포 재현성 전체를 새로 검증한 것으로 쓰지 않았다. 독립 검토·발행 결과는 배치 기록에 추가한다.


## 통합 검토·검증 보강 (2026-11-03)

초기 humanizer의 비교본은 `before.md`/`humanized.md`, 공통 검사와 사실·화면 수정을 반영한
최종 원고는 같은 출력 폴더의 `after.md`로 구분한다. 초안 담당과 다른 검토자가 요구·근거·
독자/채용 관점을 대조했고 root가 코드와 문장을 확인해 고친 뒤 재확인했다. 실제 외부 독자의
평가를 받은 것은 아니다.

공통 무료 검사는 backend344 test/check·frontend70 lint/build·AI70·RAG70·Node81 통과다.
각 원고의 검증 절에는 관련 결과와 의미 범위만 연결했다. Backend의 최초 날짜 fixture 오류는
준비 SQL2곳만UTC로 맞춰 재검증했으며 운영 로직·서비스·새 유료 호출은 변경/실행하지 않았다.
공개 발행과 전체 검토 결과는 `2026-11-03-eleven-post-publication-review.md`를 따른다.
