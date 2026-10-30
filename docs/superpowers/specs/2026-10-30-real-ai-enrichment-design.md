# 예산 제한이 있는 실제 AI 기사 요약 설계

작성: 2026-10-30
상태: 설계 초안 작성·자가 검토 완료. 사용자 문서 검토 전이며 구현·유료 호출은 하지 않았다.

## 목표와 승인된 예산

사용자는 Sigak의 mock 기사 요약을 실제 AI로 연결하고, 기존 OpenAI API 잔액으로
추정하는 5달러 이내에서 사용하도록 요청했다. 5달러는 월별 갱신 예산이 아닌
이번 연결·시험·운영의 누적 상한이다. 계정 잔액·만료·자동 충전 설정은 확인하지 않았다.

한국어 핵심 요약, 근거에 기반한 중요성 설명, 주제를 생성하고 저장해서 재사용한다.
기존의 Spring Boot API 경계, PostgreSQL 원본 저장, 검색 projection 구조를 따른다.
운영 문서 RAG의 질문 검토·Checkpoint B와는 독립적인 제품 기능이다.

## 현재 확인한 구조와 문제

- `CollectionPipelineService.publish`는 enrichment를 먼저 호출한다.
- `CollectedArticlePersistenceService.publish` 안에서 그다음 중복을 검사한다.
  이대로 provider만 교체하면 중복 수집 기사에도 유료 요청이 발생한다.
- 현재 원문과 enrichment는 함께 저장된다. AI 실패 전 원문 보존 경계가 필요하다.
- Spring의 enrichment client와 FastAPI `/api/enrichment/article` 모두 현재 mock이다.
- enrichment에는 모델·prompt version·current 이력이 있지만 prompt version은
  `collection-v1`로 고정되어 있다.
- 입력은 수집된 RSS 텍스트 또는 논문 초록 등이다. 모든 URL의 전문을 확보한 것이 아니다.
- 공개 조회는 `PUBLISHED`이며 current enrichment가 있는 기사를 대상으로 한다.

## 대안과 선택

| 방식 | 장점 | 비용·운영 부담 | 결정 |
| --- | --- | --- | --- |
| 소형 OpenAI API + 저장된 결과 재사용 | 별도 모델 서버 없이 연결 가능 | 유료 호출·실패·예산 관리 필요 | 첫 버전 선택 |
| 로컬 생성 모델 | 외부 생성 API 비용 감소 | 모델 메모리·속도·배포·한국어 품질 평가 필요 | 후속 검토 |
| 고성능 모델을 모든 기사에 적용 | 복잡한 입력의 품질 개선 가능 | 동일 예산에서 처리량 감소 | 자동 승격하지 않음 |

첫 후보는 `gpt-5.6-luna`, Responses API의 structured output,
`reasoning.effort=none`, `max_output_tokens=2048`이다. 계정에서 사용할 수 있는지는
미검증이다. 사용 불가 시 다른 모델로 조용히 바꾸지 않고 운영 오류를 표시한다.

2026-10-30 확인한 공식 문서 기준 표준 요금은 입력 100만 token당 $0.20,
출력 100만 token당 $1.20이다. 모델은 설정으로 고정하고 변경 시 가격·호환성을 다시
검토한다. 이 문서의 가격은 실제 청구 또는 계정 잔액 확인 결과가 아니다.

공식 근거:
- [GPT-5.6 Luna](https://developers.openai.com/api/docs/models/gpt-5.6-luna)
- [Structured outputs](https://developers.openai.com/api/docs/guides/structured-outputs)

## 데이터 흐름과 책임

1. Spring이 기존 URL/external ID/제목·발행일 기준으로 먼저 중복을 판별한다.
2. 새 기사와 수집 원문을 PostgreSQL에 저장하고 enrichment job을 같은 transaction에서
   등록한다. 신규 기사의 processing status는 `NORMALIZED`로 둔다.
3. 별도 Spring worker가 작업을 하나씩 확보하고 비용·호출 횟수를 먼저 예약한다.
4. DB transaction 밖에서 private FastAPI enrichment endpoint를 호출한다.
5. FastAPI가 입력·prompt·출력 schema를 검증하고 OpenAI를 호출한다.
6. Spring이 검증된 결과와 사용량을 저장하고 current enrichment를 원자적으로 교체한다.
   신규 기사는 `PUBLISHED`로 전환한다.
7. 공개 내용이 바뀌면 영속적인 projection 갱신 필요 표시를 남긴다. worker가 이를
   묶어서 처리하고 실패하면 다음 회차·재시작 후에도 복구한다.

기존 자동 수집과 신규 요약 worker는 분리한다. `collected/pending/enriched/published`
집계를 구분해 원문 저장을 공개 성공으로 잘못 보고하지 않는다. 별도 메시지 브로커나
Celery는 도입하지 않고 PostgreSQL의 작업·비용 테이블을 사용한다.

## 작업·이력 저장 계약

- 작업 식별자는 article ID, 정규화 입력 전체 hash, prompt version, model의 조합이다.
  입력 hash는 텍스트 외에도 제목·출처·URL·발행일·언어·입력 범위를 포함한다.
- 동일 입력·버전의 성공 작업은 다시 호출하지 않는다. 수집 중복은 유료 경계 전에 끝낸다.
- 작업에는 상태, attempt, 다음 시도 시각, claim token·lease, 오류 종류, 현재/예상 비용을
  남긴다. worker는 짧은 DB transaction에서 claim하고 외부 응답 동안 DB lock을 잡지 않는다.
- Spring의 PostgreSQL 원장이 비용과 attempt 상태를 관리한다. 원장 기록에 실패하면
  private FastAPI를 호출하지 않는다. FastAPI는 별도 DB나 작업 큐를 갖지 않는다.
- Spring HTTP client·FastAPI provider client 양쪽의 숨은 전송 retry를 끈다.
  한 번 확보한 attempt는 private endpoint에 한 번만 전송하며, timeout 후 같은 attempt를
  재전송하지 않는다. worker 재시작도 전송 중 상태를 미확정으로 격리한다.
- 외부 호출 전 attempt를 `DISPATCHED`로 기록한다. 전송 여부가 불확실한 채 lease가
  만료되면 예약 비용을 유지하고 `UNKNOWN`으로 격리한다. 자동 재호출하지 않는다.
- 성공 결과는 summary/whyItMatters/topics/category/score와 함께 근거 인용, 입력 범위,
  model/prompt version/input hash/provider response ID/token 사용량/latency를 저장한다.
- 기존 enrichment 이력은 보존한다. 성공 후에만 current를 바꾼다. 처리 중 입력이
  달라지면 이전 결과를 current로 적용하지 않는다.

## 5달러 이내로 사용하기 위한 제어

앱이 관리하는 누적 예산은 $4.00으로 설정하고 $1.00은 계정 잔액 오차와 외부 청구를
위한 여유로 남긴다. 재시작·날짜 변경·배포로 자동 초기화하지 않는다.

- 최초 운영은 전체 배치가 아닌 최대 10회 시도의 pilot 모드다. 신규 자동 요약과 과거
  재처리 모두 같은 원장을 사용한다. 별도 평가·재시도도 예산을 공유한다.
- 유료 dispatch 1회마다 $0.02를 먼저 영구적으로 차감한다. 초기 버전은 성공 후에도
  이 보수적 차감액을 환급하지 않는다. 실제 사용량 기준 추정액은 별도 표시한다.
- 따라서 누적 최대 200회, 하루 최대 20회이며 pilot에서는 전체 최대 10회다.
  예약과 횟수 증가는 원자적이며 동시에 요청해도 한도를 넘기지 않는다.
- prompt·본문·출력 schema 등 모델 입력 전체의 UTF-8 직렬화 크기를 16 KiB 이하로
  제한하고, 입력 token은 최대 32,768개를 비용 계산의 보수적인 가정으로 잡는다.
  위 요금에서 입력 32,768·출력 2,048의 계산액은 $0.0090112이다.
  $0.02 예약은 이보다 큰 값이며, tokenizer/공급자 사용량 경계는 구현 시 검증한다.
- 입력 상한을 넘는 기사는 결정적으로 앞부분을 잘라 입력 범위를 기록한다. 시스템
  지시문·출력 schema 자체가 상한을 넘으면 요청하지 않는다. 명시적 cache write,
  검색·파일·실행 도구, priority/fast 등 추가 비용 옵션은 사용하지 않는다.
- SDK의 숨은 자동 retry는 끈다. 인증·결제·모델 오류는 전체 회로를 멈추고,
  명확한 rate-limit 등만 최대 2회 재시도한다. 각각 새 예약과 횟수가 필요하다.
  timeout·연결 유실·청구 여부 불명 오류는 `UNKNOWN`이며 자동 재시도하지 않는다.
- 실제 usage가 예약 가정 또는 허용 모델·요금 설정과 맞지 않으면 다음 호출을 막는다.
- 내부 상태에는 앱 차감액, 실제 추정 사용액, 미확정 요청, 남은 dispatch 수와 중단 이유를
  제공한다. 이는 OpenAI 계정 전체 잔액이 아니다.

앱 밖에서 같은 계정으로 쓴 비용, 크레딧 만료, 자동 충전까지 이 장치가 제어하지는 못한다.
실제 연결 전에 사용자 계정의 잔액·자동 충전 여부를 확인하고 전용 project key를 설정한다.
키는 서버 secret 환경변수로 주입하며 채팅·Git·로그·브라우저에는 남기지 않는다.

## 출력과 품질 계약

- 한국어 요약은 핵심 사실 위주 최대 3문장, why-it-matters는 최대 2문장으로 한다.
- 원문이 말하는 내용과 추론을 구분한다. 근거가 부족하면 과장된 의미를 만들지 않고
  정보 부족을 반환한다. 출처가 주장한 수치·성능·예정은 그 주장으로 표현한다.
- 근거 인용 1~3개는 AI에 실제 전달한 입력 안의 연속 문자열인지 검증한다.
  인용이 존재한다는 검사만으로 요약의 사실 정확성을 보장한다고 표시하지 않는다.
- 기사 내용은 명령이 아닌 자료다. 기사에 포함된 지시를 따르지 않고 추가 도구를 호출하지 않는다.
- 빈 응답, schema 위반, refusal, incomplete, 유효하지 않은 category·score는 성공이 아니다.
- 입력 범위는 `FEED_EXCERPT`, `ABSTRACT`, `FULL_TEXT`와 절단 여부로 보존한다.
  전문 추출기가 검증되지 않은 출처를 `FULL_TEXT`로 표시하지 않는다.

## 공개 API·UI와 실패 시 동작

React는 Spring API만 사용한다. AI provider와 키는 private FastAPI에만 둔다.
개발 기본값 `mock`은 유지하고 실제 환경은 명시적으로 `openai`를 켠다.
OpenAI 실패 시 mock으로 자동 전환하지 않는다.

신규 기사는 유효한 요약이 준비될 때까지 공개 목록에 노출하지 않는다. 예산을 다 써도
수집·원문 저장은 계속하고 요약 대기 상태가 쌓인다. 기존 공개 기사는 유지한다.
기존 mock 기사는 임시 요약으로, 실제 결과는 AI 요약으로 표시하고 입력 범위와 원문
링크를 제공한다. 공개 DTO는 provenance와 범위를 추가하고 frontend Zod/화면을 함께 갱신한다.
미완료 신규 기사를 공개하는 별도 UI는 첫 버전 범위에서 제외한다.

## 적용 순서와 검증 기준

1. 비용 원장·중복 검사·비동기 저장 경계를 fixture/test double로 검증한다.
2. private FastAPI real provider, Spring HTTP client, provenance UI를 연결한다.
3. 키를 안전하게 설정한 뒤 명시적 pilot 10회 이내로 실제 호출한다.
   source별 짧은 소개글과 초록 등 가능한 서로 다른 입력을 포함한다.
4. 실제 요약/근거/원문/토큰/비용을 검토한다. 중복 재수집·재시작·응답 유실 시
   추가 호출을 하지 않는지 확인한다.
5. pilot이 품질·예산 기준을 통과하면 신규 기사 자동 요약을 켠다. 기존 mock 결과의
   전체 재처리는 별도 검토 후 활성화하며 seed 기사는 제외한다.

필수 검증: backend `./gradlew test check`, AI `.venv/bin/python -m pytest`,
frontend `npm test`, `npm run lint`, `npm run build`; private HTTP 계약과 공개
내부 API 차단, 저장된 실제 결과 조회·검색 projection 갱신 smoke.
핵심 회귀 사례는 동시 예산 예약, 경계 금액, 재시작 후 원장 보존, 중복 입력,
SDK retry 비활성, timeout 비용 유지, 성공 작업 재사용, 오래된 결과 거부,
current 이력 전환, 예산 소진 후 수집 지속이다.

## 예상 변경 영역

- backend: collection 수집/영속화 경계, enrichment 작업·HTTP client·상태·worker,
  원장 설정, article provenance DTO, DB migration, 자동 수집 집계·projection 복구.
- AI: 설정·provider client·prompt·schema·사용량 반환·endpoint test.
- frontend: API schema와 기사 요약 출처·입력 범위 표시.
- infra: 두 Compose 환경의 provider/secret/예산 설정 예시.
- docs: API 계약, 운영 안내, 실제 검증 후 STATUS/ROADMAP/dev-log 갱신.

## 첫 버전에서 제외

임의 URL 전문 수집, 로컬 생성 모델, 고가 모델 자동 승격, 장기 품질 benchmark,
LLM judge, GraphRAG 답변 생성, 다중 backend 운영, 과거 전체 무제한 backfill,
계정 전체 결제·자동 충전 관리, 상시 호스팅 신규 계약은 포함하지 않는다.
