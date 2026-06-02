# Refactor Review Findings Design

## Summary

이번 작업은 2026-06-02 코드 리뷰에서 발견된 중복 코드와 작은 설계 위험을 줄인다. 공개 article 응답 shape는 유지하면서 related article 조회는 bulk API로 바꾸고, collection/search 계층에 흩어진 helper를 좁은 공통 경계로 옮긴다.

## Current State

- Public article list/search/detail API는 `GET /api/articles`, `GET /api/articles?query=...`, `GET /api/articles/{id}`만 제공한다.
- Article detail frontend는 `relatedArticleIds`마다 `fetchArticle(id)`를 호출해 related article 수만큼 HTTP 요청을 만든다.
- elapsed time 측정 helper가 article/search service 내부에 반복된다.
- publishedAt parser가 normalizer와 persistence service에 중복되어 있다.
- Spring collection pipeline은 Kotlin `MockEnrichmentClient`를 사용하고, FastAPI mock enrichment는 AI boundary smoke용으로 별도 존재한다.
- Collected article persistence는 enrichment model name을 `"mock-enrichment"` 문자열로 직접 저장한다.
- `CollectionRunService` 일부 의존성은 생성자 기본값으로 직접 만들어진다.
- HTTP source fetcher는 timeout 없는 기본 `RestClient`를 사용한다.

## Design Goal

- Public article response DTO는 그대로 유지한다.
- Related article 조회는 `GET /api/articles?ids=1,2,3`로 bulk 조회할 수 있게 한다.
- `query`와 `ids`가 함께 들어오면 `ids` 조회를 우선한다. 프론트 related flow가 검색어와 독립적으로 article ID를 다시 읽기 때문이다.
- elapsed helper와 publishedAt parser를 작고 테스트 가능한 파일로 추출한다.
- Spring collection mock과 FastAPI mock을 이번 세션에서 하나로 합치지는 않는다. HTTP enrichment mode 전환은 S2/S3 범위의 구조 변경이므로, 이번 작업은 현재 Spring local mock 사용 사실을 명확히 하고 `modelName`을 response metadata로 전달해 persistence 하드코딩을 줄인다.
- Source HTTP fetch는 application property 기반 timeout을 갖는다.

## Verification

- Backend focused tests: article controller/service, collection service/parser/persistence, search services touched by timing helper.
- Frontend focused tests: API client and detail page related article flow.
- Final gate: backend `./gradlew test`, `./gradlew check`, frontend `npm test`, `npm run lint`, `npm run build`, AI `pytest` if FastAPI enrichment schema changes.
