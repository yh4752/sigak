# URL로 출처를 등록하는 수집 구조 설계

작성: 2026-11-02
상태: 2026-11-02 사용자 구현 승인. 코드와 backend333개 test/check/bootJar 검증 완료. 실제 배포·후보 활성화 검증 진행 중.
구조 결정: [ADR 0006](../../decisions/0006-database-source-registry.md)

## 목표와 범위

출처를 추가할 때 Kotlin 코드를 수정하거나 서비스를 재배포하지 않는다. 운영자가
블로그 또는 RSS/Atom URL을 등록하면 피드를 찾고, 최근 글과 본문을 무료로 검증한다.
표본을 확인해 활성화한 출처는 다음 자동 수집 회차부터 사용한다.

개발 실무 블로그를 중심으로 개발 트렌드와 구체적인 커리어 경험을 읽는 방향을 지원한다.
첫 검증 후보는 토스 테크·우아한형제들 기술 블로그·NAVER D2다. 후보 등록이 전체 글의
품질 보증은 아니며, 이 설계 세션에서는 기존 출처를 중지하거나 새 출처를 활성화하지 않는다.

첫 버전은 운영 명령과 Spring 내부 서비스로 제공한다. 관리자 웹 화면, 공개 출처 등록
API, 회원/인증 체계, sitemap 수집, 웹 검색 기반 출처 탐색, JavaScript 실행 크롤러,
논문 PDF 전문 처리, AI 기반 기사 선별은 포함하지 않는다.

URL만 입력하는 경험은 표준 피드와 지원 가능한 본문에 적용한다. 피드가 여러 개이거나
본문 구조가 모호하면 선택 또는 설정을 요청한다. 모든 사이트 지원을 약속하지 않는다.

## 현재 코드에서 확인한 문제

- `SourceRegistry.sources()`가 여섯 출처를 코드에 고정한다. `news_sources`는 별도로
  존재하지만 실제 수집 대상의 정본은 아니다.
- `CollectedArticlePersistenceService`는 표시 이름을 영문 slug로 바꿔 출처를 찾는다.
  한글 이름은 `manual-source`로 겹칠 수 있고, 이름 변경은 다른 출처 생성으로 이어질 수 있다.
- 새로 생성한 DB 출처의 `url`에는 피드 주소가 아니라 첫 기사 주소가 들어갈 수 있다.
  기존 `url` 값을 일괄적으로 피드 주소로 해석하면 안 된다.
- RSS의 `content:encoded`는 원본에 남지만 본문 저장소로 연결되지 않는다. Atom은
  `summary`만 읽어 `content`를 놓친다. RSS 텍스트 정리도 정규식이어서 코드 보존에 부적합하다.
- `ArticleBodyPolicy`와 `ArticleBodyExtractor`는 세 호스트를 코드에 고정한다.
- 현재 feed fetch는 고정 출처를 전제로 한다. URL을 운영자가 등록하도록 넓힐 때에는
  본문 fetch의 URL·네트워크 제한을 피드 탐색과 피드 fetch에도 적용해야 한다.

## 대안과 결정

| 방식 | 장점 | 제약 | 결정 |
| --- | --- | --- | --- |
| YAML 출처 목록 | 변경이 작고 Git으로 추적 가능 | 적용·재시작과 DB 정보 동기화 필요 | 초기 대안 |
| PostgreSQL 출처 등록 + 운영 명령 | 재배포 없이 추가·중지, 기존 DB 활용 | 등록·검증 상태와 이관 작업 필요 | 채택 제안 |
| 관리자 웹 화면 | 직접 관리하기 편함 | 인증·화면·공개 쓰기 경계까지 확대 | 후속 |

DB를 유일한 운영 출처 정본으로 삼는다. 코드에는 RSS/Atom·arXiv 같은 형식별
수집기와 공통 추출 알고리즘만 둔다. 출처마다 새 클래스나 호스트 분기를 추가하지 않는다.

## 운영 흐름

1. **등록**: 블로그/피드 URL 하나를 받아 고정 ID의 비활성 출처를 만든다.
2. **탐색**: 직접 피드라면 XML 형식으로 확인한다. HTML이면 `link[rel~=alternate]`의
   RSS/Atom 후보를 읽고 상대 주소를 해석한다. URL 경로를 무작위로 추측하지 않는다.
3. **검증**: 피드 후보와 최근 글 최대 3건을 읽어 제목·날짜·원문 링크·본문 후보를 점검한다.
4. **미리보기**: 본문 확보 경로, 글자 수, 처음·중간·마지막 부분, 전체 추출문과
   원문 링크를 운영자용 결과로 제공한다. 비용·기사 공개·검색 갱신은 발생하지 않는다.
5. **활성화**: 운영자가 원문과 표본을 대조한 뒤 활성화한다. 이때까지는 자동 수집하지 않는다.
6. **운영**: 기존 10분 fixed-delay 수집이 활성 출처를 DB에서 읽고 기존 저장·projection을 재사용한다.

등록·검증·활성화를 분리하는 이유는 소개글이나 메뉴를 본문으로 오인하는 문제를
새 출처에 반복하지 않기 위해서다. 정상 경로에서 필요한 출처 정보는 URL 하나이며,
미리보기 확인에는 추가 소스 코드나 사이트별 프로그램이 필요하지 않다.

운영 명령의 책임은 `register`, `check`, `configure`, `enable`, `disable`, `list`로
제한한다. 명령 이름은 서비스 계약이며 실행 스크립트와 구체 문법은 구현 계획에서 정한다.
기존 command-runner 패턴을 사용하고, 명령 실행 프로세스에서는 HTTP 서버·자동 수집·
본문/요약 worker·paid provider dispatch를 모두 끈다. 운영 중인 Spring은 DB 변경을
다음 회차에 읽는다. 별도 공개 관리 API나 프록시 허용 경로를 추가하지 않는다.

## 출처 모델과 식별자

기존 `news_sources`를 확장한다. 새 병렬 registry 테이블은 만들지 않는다.

| 항목 | 계약 |
| --- | --- |
| `id`, `source_key` | 기존 ID/FK 보존. 신규 key는 서버 생성 UUID 기반이며 이름·URL 변경과 무관 |
| `name`, `type`, `category_hint` | 표시 이름/수집 형식/기존 category 기본값. 이름은 피드 제목에서 제안하며 수정 가능 |
| `site_url` | 입력한 블로그 주소 또는 확인된 같은 호스트의 사이트 주소 |
| `feed_url` | 검증된 정규화 피드 주소. 자동 수집은 이 값만 사용 |
| `collection_enabled` | 기본 false. 검증과 표본 확인 후 true |
| `validation_status` | `UNVERIFIED`, `PASSED`, `NEEDS_CONFIG`, `BLOCKED` |
| `allowed_hosts` | 이 출처에서 네트워크 접근을 허용한 정확한 호스트 목록. wildcard 없음 |
| `body_mode` | `AUTO`, `FEED_FULL`, `PAGE` |
| `feed_content_verified` | AUTO에서 피드 전문 사용을 허용한 표본 확인 기록. 설정 version 변경 시 false |
| `body_selector`, `exclude_selectors` | 선택적 CSS 본문 위치/제외 위치. 실행 코드나 템플릿은 받지 않음 |
| `config_version`, `validated_config_version` | 설정 변경과 검증을 연결. 이전 설정의 PASS로 새 설정을 활성화할 수 없음 |
| `validation_report`, `validated_at` | 최근 표본별 결과/원인·시각. 본문 전문은 report 대신 운영 출력으로만 제공 |
| `last_collection_at`, `last_collection_error` | 마지막 수집 완료 시각과 안전하게 정리한 실패 코드 |

`allowed_hosts`와 `exclude_selectors`는 JSONB 배열, `validation_report`는 JSONB 객체로
저장한다. `feed_url`은 정규화 후 유일해야 한다. fragment·기본 HTTPS port는 정리하되
의미 있는 경로·query는 보존한다. 서로 다른 피드는 같은 호스트에 있어도 별도 출처다.
동일 피드 재등록은 기존 ID를 반환하고 자동으로 재활성화하거나 설정을 덮어쓰지 않는다.

새 수집 데이터는 표시 이름이 아니라 `source_key`를 필수로 전달한다. 수집 경로에서는
등록된 ID를 조회하며 이름 기반 `findOrCreateSource`를 제거한다. seed/manual 데이터의
출처는 별도 등록된 비활성 레코드로 유지한다.

설정 변경 시 version을 올리고 검증을 무효화하며 해당 출처의 수집을 일시 중지한다.
표시 이름만 바꾸는 경우에는 네트워크/본문 정책을 바꾸지 않으므로 재검증하지 않는다.
명령은 기대 version을 받아 동시 변경을 거부하고, network I/O 중에는 DB transaction을 열지 않는다.

## 피드 탐색과 검증 상태

- 피드 후보가 1개면 검증한다. 0개면 `FEED_NOT_FOUND`, 여러 개면
  `MULTIPLE_FEEDS`로 `NEEDS_CONFIG`를 반환하고 운영자가 하나를 선택한다.
- 원래 입력 호스트와 다른 피드/기사 호스트는 자동 승인하지 않는다. 후보 주소만 보고하고
  정확한 호스트를 설정한 뒤 다시 검증한다. XML의 `link`나 `canonical`은 허용 범위를 넓히지 못한다.
- 탐색은 입력 문서 1개, 선택한 피드 1개, 기사 최대 3개로 제한한다. 피드가 여러 개면
  모두 가져오지 않는다. 각 HTTP 작업은 redirect 포함 15초, 전체 검증은 90초 이내다.
- 피드가 비었거나 유효한 기사 3건이 없으면 `INSUFFICIENT_SAMPLES`로 남긴다.
  첫 버전은 검증 기준을 낮춰 자동 활성화하지 않는다.
- XML은 namespace-aware하게 파싱한다. RSS content namespace와 Atom `content`
  (`text`, `html`, `xhtml`)를 구별하고, `summary`/`description`은 별도로 보존한다.
  Atom 외부 `content src`와 base64/binary content는 본문으로 읽거나 추가 fetch하지 않는다.
- XXE/DTD/외부 엔티티 금지는 유지한다. 제목·원문 링크·날짜가 유효한 항목만 표본으로 쓴다.
- 3개 표본 모두 선택한 본문 경로의 기계 검사를 통과하면 `PASSED`다. AUTO는 페이지와
  피드 후보를 미리보기에 함께 제시한다. FEED_FULL은 피드 본문을 검사한다. 이는 최소 조건 통과이며
  전문 완전성의 자동 증명은 아니다. 활성화 명령은 표본 대조 확인을 명시적으로 기록한다.
- 표본마다 요약 입력 크기 적합성도 무료로 계산해 표시한다. 3건 모두 입력 제한을 넘으면
  `NO_SUMMARIZABLE_SAMPLE`로 NEEDS_CONFIG다. 일부만 길면 해당 글만 TOO_LONG 후보로
  표시한다. 본문 확보 성공과 유료 요약 가능 여부는 별도 결과다.
- 접근 정책 위반·401/403·429는 `BLOCKED`, 일시 네트워크 실패·추출 모호성은
  `NEEDS_CONFIG`와 세부 코드를 기록한다. 자동 재시도 루프나 차단 우회는 없다.
- 활성화에는 현재 version의 PASSED와 24시간 이내 검증이 필요하다. 이후 24시간마다
  출처를 끄는 정책은 아니며, 운영 중 오류는 마지막 상태에 따로 기록한다.

## 본문 확보와 공통 추출

### 피드에 본문이 있는 경우

`content:encoded`나 Atom `content`라는 필드 이름, 텍스트 길이만으로 전문으로 간주하지
않는다. 등록 미리보기에서 최근 3개 원문과 처음·중간·후반을 대조한 출처만
`FEED_FULL`로 활성화할 수 있다. `AUTO`는 이 확인 전에는 페이지 추출을 사용한다.
AUTO의 피드 전문 확인 여부도 설정 version에 묶어 저장한다.
`feed_content_verified`는 활성화 때 기록하는 검토 결과이며 네트워크 설정 변경이 아니다.
AUTO 검증 report의 피드 후보 3개가 모두 기계 검사를 통과하고 운영자가 대조한 경우만 true다.

검증한 피드 본문은 신규 기사 저장 transaction에서 기존 `article_full_texts`에 READY로
연결한다. 원본 피드 HTML·소개글·추출문은 구별하고, Jsoup으로 문단·목록·코드 블록을
보존한다. 자동 수집에서 저장하는 `requested_summary`는 항상 false다.
추가 원문 HTTP 요청 없이 클릭 때 요약 입력을 바로 사용할 수 있다.

각 새 글에서도 최소 본문 조건, 더 읽기/발췌 표시, 올바른 형식 여부를 검사한다.
의심스러운 입력은 READY가 되지 않는다. AUTO는 페이지 추출로 넘기고 FEED_FULL은
해당 글의 본문 실패를 저장한다. 출처 표본 통과가 미래 모든 글의 완전성을 보증하지는 않는다.

### 피드가 소개글만 제공하는 경우

공통 Jsoup 추출은 정리한 DOM에서 `main article` → `article` → `main` 순으로 본문
후보를 찾는다. 선택 단계에서 root가 여러 개이거나 링크/탐색 목록이 대부분이면 추측하지
않고 실패한다. 단일 main fallback에는 본문 heading과 문단이 필요하다.

공통 추출이 안 되는 출처에는 `body_selector`와 `exclude_selectors`를 저장한다.
명시한 본문 root는 정확히 1개여야 하며, 실패했을 때 다른 root로 조용히 대체하지 않는다.
저자 소개·관련 글·탐색 메뉴·script 등을 제거하되 코드와 마지막 절을 유지한다.
기존 세 출처의 특수 규칙도 명시적 selector/제외 selector 데이터로 옮기고 fixture로 보존한다.

최소 조건은 기존처럼 본문 200자, 20자 이상인 유효 문단 2개, 문자/숫자 100개 이상이다. 여기에
링크 텍스트 비율 20% 이하를 적용한다. 이 기준은 완전성 증명이 아니라 목록·짧은 발췌를
걸러내는 하한이며, 원문 대조와 source fixture가 별도로 필요하다.

페이지 추출은 기존 무료 worker가 담당한다. 출처 조회는 article의 source FK로 하며,
URL의 호스트만으로 다른 출처의 설정을 찾아 적용하지 않는다. 자동 수집과 무관한 기존
기사의 요청 시 본문 준비도 해당 출처의 승인된 설정을 사용한다.

READY 본문에는 `body_origin=FEED|PAGE`, 실제 fetch URL, 내용 hash, 설정 version,
추출 알고리즘 version을 저장한다. 저장된 요약의 근거가 된 입력을 보존하며, 출처 이름이나
selector 변경만으로 기존 READY 본문·유료 요약을 덮어쓰거나 재생성하지 않는다.
기존 실패·UNSUPPORTED 기록도 출처 등록만으로 일괄 재실행하지 않는다.

## 외부 요청 경계

운영 등록이라도 입력 URL은 검증한다. 피드 탐색·피드 읽기·본문 읽기가 공통 정책을
사용하고 arXiv의 기존 속도 제한/cooldown은 그 위에 그대로 둔다.

- HTTPS, port 443, 사용자 정보 없는 URL만 허용한다. 로컬·내부·metadata·예약 주소를 거부한다.
- 출처별 정확한 허용 호스트만 접근하고 redirect는 같은 호스트 내 최대 3회다.
  다른 호스트로 이동해야 한다면 확정 URL/호스트를 운영자가 따로 설정한다.
- DNS의 모든 후보를 검사하고 검증한 공개 IP에 실제 연결을 고정한다. 사전 DNS 검사 후
  전송기가 다시 임의 DNS 조회를 하는 틈이 없도록 한다. TLS 인증은 원래 호스트 기준이다.
- HTML/XML 응답은 실제 읽은 비압축 bytes 기준 2MiB, 압축은 첫 버전에서 받지 않는다.
  고정 Content-Length만 신뢰하지 않는다. timeout에는 DNS·redirect·body 읽기가 포함된다.
- 로그인·유료벽·봇 차단을 우회하지 않는다. HTML 문서에 포함된 script/이미지 URL을 실행·fetch하지 않는다.

수집 주기·회차당 20건 상한은 유지한다. 출처별 네트워크 동시성은 1로 제한하며,
미리보기/등록 실행과 수집이 겹치더라도 다중 worker로 사이트 요청을 늘리지 않는다.
single-instance 운영을 전제로 출처별 PostgreSQL advisory lock을 공유하고, arXiv의
별도 프로세스 간 cooldown도 보존한다.

## 기존 시스템으로 이관

1. Flyway에서 새 필드를 추가하고 기존 모든 출처는 기본 수집 비활성으로 둔다.
2. 코드의 여섯 등록 항목을 명시적으로 이관한다. 기존 collected source key와 연결되는
   레코드는 같은 ID/FK를 유지하고 올바른 feed URL을 채운다. 없는 항목만 생성한다.
   수동 seed 출처는 수집 대상에 넣지 않는다. 이관에는 네트워크 요청이 없다.
   예상과 다른 key/URL 충돌은 자동 병합하지 않고 migration 실패로 보고한다.
3. 기존 여섯 출처의 collection_enabled를 true로 두고 기존 자동 수집 전역 설정과
   sourceIds 필터를 유지해 배포 전 수집 대상을 보존한다. 다른 출처는 false다.
   `LEGACY_MIGRATION` 검증 근거를 기록한다. 이 예외는 고정된 여섯 출처에만 적용한다.
   기존 Google/GitHub/OpenAI 본문 selector와 arXiv 전문 미지원 정책을 보존한다.
4. `SourceRegistry`를 DB 조회로 바꾸고, 수집 ID를 article 저장까지 전달한다.
   자동 수집은 회차 시작 시 활성 출처를 읽고 각 출처 실행/기사 저장 전 설정 version과
   활성 여부를 재확인한다. 중지된 출처의 이미 시작한 HTTP 요청은 끝날 수 있지만 새 기사로 commit하지 않는다.
5. 환경 변수 `sourceIds`는 임시 호환 필터로 유지한다. 비어 있으면 DB 활성 출처 전체,
   값이 있으면 그 ID와 활성 출처의 교집합이다. 비활성을 되살리지 않는다. 새 출처가
   필터로 제외됐다는 상태를 표시하며, 잘못된 ID는 오류로 보고한다.
6. 세 후보를 비활성 등록하고 실제 운영 JVM/네트워크에서 무료 검증한 뒤 활성화한다.
   기존 출처의 중단·카탈로그 삭제·과거 기사 일괄 수집은 별도 운영 선택으로 남긴다.

`disable`은 신규 자동 수집을 중단한다. 기존 기사/요약은 보존하고 이미 확보한 본문에 대한
첫 클릭 요약도 기존 예산 안에서 가능하다. 승인된 설정에 따른 기존 기사 본문 fetch도
허용한다. 반면 `BLOCKED`는 무료 fetch까지 중단한다. 재개는 check 후 명시적 enable이다.
운영 중 피드 요청에서 401/403/429나 접근 정책 위반을 받으면 즉시 BLOCKED/비활성으로 바꾸며,
일시 timeout/개별 본문 추출 실패는 출처 전체를 자동 중지하지 않고 다음 정상 회차와
기사별 실패 캐시를 사용한다. 즉시 반복 재시도는 하지 않는다.

## 요약·비용·공개 계약

- 수집/등록/검증/분류 기본값 설정에는 유료 AI를 쓰지 않는다. 첫 상세 진입만 기존
  비동기 요약을 요청하고 공용 저장 결과를 재사용한다.
- 총 5달러 조건, 앱 누적 4달러 중단, 회당 보수적 0.02달러 차감, 총 200회/일 20회,
  UNKNOWN 차감 보존·자동 재시도 금지·기존 누적 이력은 그대로다. 예산을 초기화하지 않는다.
- 전체 provider 요청 64KiB와 Spring 입력 56KiB 제한을 유지한다. 긴 D2 글 등은
  TOO_LONG이 될 수 있으며, 일부만 자르거나 여러 유료 호출로 나누지 않는다.
- 공개 article DTO·요약 상태·검색 경계는 유지한다. 피드 전문은 내부 요약 입력이며,
  원문 전체를 공개 API로 재게시하는 기능은 추가하지 않는다.
- 이번 범위에는 CAREER category나 새 요약 prompt를 추가하지 않는다. category 미지정은
  기존 SOFTWARE_ENGINEERING을 사용한다. 세 출처의 모든 글을 개인 맞춤으로 골라주는
  기능은 아니며, 취업 정보 전용 분류·편집 규칙은 실제 표본을 읽은 뒤 별도 설계한다.

## 2026-11-02 무료 사전 관찰

호스트 Python urllib에서 공개 URL을 한 번씩 읽어 XML 구조와 크기를 확인했다.
실제 배포 JVM의 접근 가능성, 전문 완전성, AI 품질을 검증한 결과는 아니다.

| 후보 | 관찰한 피드 | HTTP / 항목 수 | 첫 3개 본문 필드 |
| --- | --- | --- | --- |
| 토스 테크 | `https://toss.tech/rss.xml` | 200 / 20 | RSS `content:encoded` 있음 |
| 우아한형제들 | `https://techblog.woowahan.com/feed/` | 200 / 10 | RSS `content:encoded` 있음 |
| NAVER D2 | `https://d2.naver.com/d2.atom` | 200 / 20 | Atom `content type=html` 있음 |

토스 루트 HTML에 `/rss.xml` alternate link도 확인했다. 본문 필드가 있다는 사실만으로
FEED_FULL 승인을 내리지 않는다. 세 피드는 이 관찰에서 모두 2MiB 이하였다.

## 구현 후 완료 기준

- 표준 RSS/Atom 출처를 URL로 등록하고 코드 수정/재시작 없이 다음 수집 회차에서 사용한다.
- 다중/없는 피드, 중복 등록, 한글 이름·이름 변경, 설정 version 경합, 중지 직후 저장 경합을 검사한다.
- 최근 3개 본문을 원문과 대조하고, 전문·소개글 분리 및 코드·후반 절 보존 fixture를 둔다.
- RSS namespace/Atom text·html·xhtml과 외부 content 거부, XXE, 다중 article root,
  잘못된 selector, 차단 페이지, DNS 재바인딩, 다른 호스트 redirect, 초과 응답을 검사한다.
- 마이그레이션 전후 기사·출처 FK·요약·누적 비용이 보존되고 seed가 자동 수집되지 않아야 한다.
- FEED READY는 별도 페이지 fetch 없이 저장하고, 요약 요청 false·유료 작업 수 증가 0을 확인한다.
- 유료 boundary는 fixture/mock로 검사한다. 실제 AI 재시험이 필요해도 기존 한도 안에서
  명시적으로 선택한 소수 기사만 방문하고 UNKNOWN 작업은 재시도하지 않는다.
- backend `./gradlew test check`와 DB 이관/수집/검색 smoke를 실행한다. 변경한 경우에만
  frontend test/lint/build·AI pytest를 추가한다. 정적 문서 설계를 이 검증의 완료로 간주하지 않는다.

## 구현 순서와 문서 반영

DB 이관·고정 source ID → 공통 외부 fetch 경계 → 피드 탐색/운영 등록·검증 →
Atom/RSS 전문 보존·본문 연결 → 공통/설정 본문 추출 → 후보 검증·runtime smoke 순으로 진행한다.
각 단계는 독립적으로 검증하고 수집 활성화는 마지막에 수행한다.

예상 변경 경계는 `source` entity/repository/service, `collection` registry/collector/
persistence/command runner, `article/fulltext`, Flyway와 tests다. FastAPI·프론트엔드
기능 변경이나 유료 provider 교체는 필요하지 않다.
구현 때 `SOURCE_POLICY`와 운영 안내를 갱신하고, 실제 연결 결과 이후 README의
제품 방향·아키텍처를 정리한다. 구현 단계는 [실행 계획](../plans/2026-11-02-url-source-registration.md)으로 추적한다.

## 형식 근거

- [Atom RFC 4287](https://www.rfc-editor.org/rfc/rfc4287): content와 summary 및 content type 구분.
- 후보 피드의 현재 관찰은 위 표의 각 공식 URL을 직접 읽은 결과다.
