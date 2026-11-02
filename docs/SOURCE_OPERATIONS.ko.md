# 출처 등록과 운영

URL 기반 출처 관리 명령은 배포 Compose의 DB를 사용한다. 관리자 웹 화면이나 공개 등록
API는 없다. 운영 명령은 HTTP 서버·자동 수집·유료 요약·본문 worker를 끈 별도 프로세스에서
실행하며, 실제 서비스는 DB의 활성 출처를 다음 수집 회차에 읽는다.

## 준비

- 새 backend image와 Flyway V6 이상이 배포되어 있어야 한다.
- 저장소 루트에서 실행한다. Docker와 `infra/deploy/.env`가 필요하다.
- `.env`나 API key를 명령 인자/채팅/로그에 복사하지 않는다.

## URL 등록과 미리보기

```bash
./scripts/manage-sources.sh register --url=https://toss.tech/
```

블로그 URL에서 공개 RSS/Atom을 찾거나 피드 URL을 직접 사용할 수 있다. 최근3건을
검사하고 출처 `key`, 현재 `version`, `validationStatus`, 실패 이유와 본문 앞·중간·뒤를
출력한다. 자동 수집은 아직 비활성이다. 전체 추출문 비교가 필요하면 `--full`을 붙여
Git에서 제외된 `outputs/` 아래 파일로 보관한다.

같은 URL/피드는 기존 출처 ID를 재사용하며, 이미 운영 중인 출처를 다시 등록해도
자동 중지하거나 설정을 덮어쓰지 않는다. 별도의 `check` 명령은 재검증을 위해 해당 출처를
비활성으로 바꾸므로 원문을 확인하고 다시 enable해야 한다.

## 결과 읽기

- `SIGAK_SOURCE_RESULT=` 뒤의 JSON이 결과다. 등록/검증 결과에는 `source`, `samples`가 있다.
  기존 출처 재등록·설정 변경·활성화·중지는 출처 객체, list는 출처 배열을 반환한다.
- 종료0: 명령 성공. check/register의 새 검증은 PASSED.
- 종료2: 검증은 실행했지만 NEEDS_CONFIG 또는 BLOCKED. 원인을 보고 설정한 뒤 다시 검사한다.
- 종료1: 입력/version 오류, 잠금 충돌 또는 명령 실행 실패. `SIGAK_SOURCE_ERROR`를 확인한다.
- PASSED는 본문 최소 조건 통과다. 전문 완전성은 원문과 표본을 대조해야 한다.

## 예외 설정과 재검증

피드가 여러 개면 선택한 `feedUrl`, 다른 호스트를 쓰면 정확한 `allowedHosts`를 설정한다.
`allowedHosts`는 목록 전체를 교체하며 wildcard를 허용하지 않는다. 본문을 찾기 어려우면
CSS `bodySelector`와 `excludeSelectors`를 설정한다. 본문 안의 aside 강조 상자는 보존하므로
관련 글 widget은 정확한 class/selector로 제외한다. 설정 변경 때마다 version이 증가한다.
아래 key/version은 바로 전 결과의 값으로 바꾼다.

```bash
./scripts/manage-sources.sh configure --key=source-key --version=2 \
  --patch='{"bodyMode":"FEED_FULL"}'
./scripts/manage-sources.sh check --key=source-key --version=3 --full
```

- AUTO: 검토된 피드 전문 사용 기록이 있으면 피드, 그전에는 페이지 추출.
- FEED_FULL: 전문으로 확인한 피드만 사용. 발췌/누락이면 해당 글의 본문 실패를 기록.
- PAGE: 승인된 원문 HTML을 공통/설정된 root로 추출.
- 본문 전문을 제공하는 피드인지 글3개의 처음·중간·끝과 코드를 비교한다.
- `BODY_AMBIGUOUS`/`BODY_NOT_FOUND`는 본문 위치 설정, `HOST_NOT_ALLOWED`는
  `requiredHosts`/`rejectedUrl`, `NO_SUMMARIZABLE_SAMPLE`은 입력 크기를 확인한다.
- 로그인·구독·봇 차단을 우회하거나 긴 글의 앞부분만 요약하지 않는다.

## 활성화·중지

현재 설정으로24시간 이내 PASSED를 받았고 원문을 대조한 경우만 활성화한다.
AUTO에서 피드 전문을 사용하려면 `--feed-reviewed`도 명시한다. 피드 경로의 크기 적합성도
별도로 검사하므로 PAGE 검증을 통과한 사실만으로 FEED를 사용하지 않는다.

```bash
./scripts/manage-sources.sh enable --key=source-key --version=4 --reviewed --feed-reviewed
./scripts/manage-sources.sh list
./scripts/manage-sources.sh disable --key=source-key --version=5
```

enable/disable도 version을 증가시킨다. 오래된 version은 거부한다. disable은 새 자동 수집을
중단하고 기존 기사·본문·요약은 보존한다. BLOCKED는 외부 fetch까지 막으며 다시 검사해야 한다.
자동 수집의 `SIGAK_COLLECTION_AUTOMATION_SOURCE_IDS`가 비어 있으면 활성 출처 전체,
값이 있으면 활성 출처와의 교집합을 쓴다. 새 출처가 포함되지 않은 고정 필터는 따로 수정해야 한다.
내부 수집 응답/상태의 `excludedSourceIds`는 비활성으로 제외한 ID를 보여 준다.

## 비용과 데이터

등록·표본 검사·본문 확보에는 유료 AI를 쓰지 않는다. 검증된 피드 전문은 기사 저장과 함께
READY로 준비하며 `requested_summary=false`다. 실제 상세 첫 방문만 기존 비동기 요약을
요청하고 결과를 공유한다. 총5달러 조건, 앱 누적4달러 중단·총200회·일20회·회당0.02달러
보수 차감, UNKNOWN 자동 재시도 금지와 기존 누적 이력은 유지한다.

전체 요청에 들어가지 않는 전문은 TOO_LONG이다. 출처 등록/설정 변경으로 과거 기사를
일괄 backfill하거나 기존 요약을 자동 재생성하지 않는다. 기존 arXiv 수집 connector는
유지되며 논문 전문은 지원 범위에 추가되지 않았다.

설계: [출처 등록 계약](superpowers/specs/2026-11-02-url-source-registration-design.md)

## 2026-11-02 배포 검증

| 출처 | 본문 방식 | 예외 설정 |
| --- | --- | --- |
| 토스 테크 | AUTO / 검토된 피드 전문 | 없음 |
| 우아한형제들 | PAGE | root `.post-content-body`, 제외 `.pp-multiple-authors-boxes-wrapper` |
| NAVER D2 | FEED_FULL | 렌더링 원문과 피드 표본 대조; 동영상 자막은 미포함 |

세 출처는 활성 상태다. 관측한 새 기사50개 중49개 본문이 준비됐고 링크 목록 성격의
D2 한 건은 BODY_LINK_LIST로 제외됐다. 이 수치는 특정 수집 시점이며 계속 증가할 수 있다.
출처 등록 단계 이후 사용자 요청으로 세 출처 각1건 실제 요약을 확인했다. 본문·입력 일치와
근거는 검증했고 자세한 시간·비용·품질 한계는 같은 날짜 개발 로그에 기록했다.
