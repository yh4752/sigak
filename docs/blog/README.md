# Sigak Development Blog Guide

이 디렉터리의 개발 로그는 단순 작업 기록이 아니라 포트폴리오용 기술 회고입니다.

각 글은 "무엇을 했다"에서 끝나지 않고, 구현 과정에서 드러난 설계 판단, 트레이드오프, 기술 포인트, 공학적 주제를 함께 설명해야 합니다.

## 작성 원칙

- 한국어로 작성한다. 별도 요청이 있을 때만 영어로 작성한다.
- 파일명은 실제 작업일 기준 `YYYY-MM-DD-dev-log.md`를 사용한다.
- 검증하지 않은 결과를 성공처럼 쓰지 않는다.
- 과장된 표현보다 재현 가능한 사실과 판단 근거를 우선한다.
- 내부 구현 세부사항은 포트폴리오 독자가 이해할 수 있는 수준으로 풀어 쓴다.

## 권장 구조

```md
# YYYY-MM-DD 개발 로그: 주제

## 요약

## 오늘 완료한 일

## 설계 고민

## 트레이드오프

## 기술 포인트

## 공학적 주제화

## 검증

## 다음 단계
```

작업 성격에 따라 섹션을 합치거나 순서를 조정할 수 있지만, 다음 네 가지 관점은 가능한 한 포함한다.

- **설계 고민**: 왜 이 구조를 선택했는가?
- **트레이드오프**: 무엇을 얻고 무엇을 미뤘는가?
- **기술 포인트**: 구현에서 중요한 API, 라이브러리, 테스트, 데이터 흐름은 무엇인가?
- **공학적 주제화**: 이 작업을 더 넓은 소프트웨어 엔지니어링 주제로 표현하면 무엇인가?

## 좋은 글의 기준

- 작업 목록만 나열하지 않고 의사결정의 맥락을 설명한다.
- "완성"과 "준비"를 구분한다.
- MVP에서 의도적으로 미룬 일을 명확히 기록한다.
- 테스트, smoke check, 문서 업데이트를 함께 남긴다.
- 다음 작업으로 이어지는 기술적 연결고리를 만든다.

## 취업용 기술 블로그 체크리스트

글을 마무리하기 전에 아래 질문 중 해당하는 내용을 2-4개 정도 반영한다. 모든 글에 모든 항목을 억지로 넣지는 않는다.

- **문제 정의**: 이 작업이 어떤 사용자 문제, 운영 문제, 개발 병목을 해결했는가?
- **기술 선택 이유**: 왜 이 라이브러리, 저장소, 구조, API 모양을 선택했는가?
- **대안 비교**: 선택하지 않은 대안은 무엇이고, 어떤 비용이나 리스크 때문에 미뤘는가?
- **데이터 흐름**: 요청, 데이터, 이벤트, 색인, 응답이 어떤 순서로 이동하는가?
- **경계 설계**: public API, internal API, service, repository, external client 경계를 어떻게 나눴는가?
- **실패와 복구**: 일부 실패, 외부 시스템 장애, 재시도, rebuild, fallback을 어떻게 생각했는가?
- **테스트 전략**: 단위 테스트, 통합 테스트, smoke test가 각각 무엇을 검증했는가?
- **관측 가능성**: count, duration, latency, status, failed reason 같은 지표를 어떻게 남겼는가?
- **보안과 운영**: secret, CORS, 내부 endpoint, 배포 비용, teardown, 권한 경계를 어떻게 다뤘는가?
- **MVP 범위 조절**: 지금 하지 않은 일은 무엇이고, 왜 나중으로 미뤘는가?
- **확장 경로**: 이 작업이 다음 기능이나 더 큰 아키텍처로 어떻게 이어지는가?
- **배운 점**: 구현 중 발견한 제약, 라이브러리 특성, 설계 실수 가능성은 무엇인가?

## 주제별 강조 포인트

### Backend

- controller를 얇게 유지하고 service에 business logic을 둔 이유
- DTO와 entity를 분리한 이유
- transaction, lazy loading, N+1 query, repository query 설계
- 외부 client 호출 실패를 다루는 방식
- 테스트 가능한 service boundary

### Search and RAG

- PostgreSQL source of truth와 Elasticsearch/Qdrant/Neo4j projection store 분리
- indexing, rebuild, fallback, alias, bulk API 같은 검색 운영 주제
- keyword, vector, hybrid search의 역할 차이
- retrieval quality를 어떤 query set과 metric으로 비교할지
- Graph RAG를 제품 기능과 평가 실험으로 나누는 방식

### AI Server

- FastAPI를 AI/RAG 전용 boundary로 둔 이유
- mock mode를 유지하는 이유와 real provider로 교체할 경계
- prompt, schema validation, unsupported claim, evidence coverage
- paid API 없이 local development를 유지하는 방식

### Frontend

- API client boundary에서 Zod로 응답을 검증하는 이유
- loading, error, empty state를 어떻게 설계했는가?
- 검색 상태, URL, detail navigation, related data fetch 흐름
- 복잡한 state management library를 도입하지 않은 이유

### Infra and Deployment

- Docker Compose를 먼저 선택한 이유
- cloud-specific dependency를 MVP 이후로 미루는 이유
- AWS 배포 시 비용, teardown, secret, network boundary
- local reproduction과 demo deployment의 역할 차이
