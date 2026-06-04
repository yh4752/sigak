# arXiv Rate Limit Handling Design

## Summary

2026-06-03 collection-run 확장 중 `arxiv-cs-ai` fetch가 `429 Too Many Requests`
로 실패했다. 같은 run의 다른 arXiv source는 성공했으므로 parser나 persistence
전역 문제보다 `export.arxiv.org` 연속 호출 정책 대응 부족이 원인이다.

이번 변경은 selected source collection의 public/API 계약을 바꾸지 않고,
source content fetch 경계에서 arXiv export API 요청만 보수적으로 직렬화한다.

## Current State

- `SourceRegistry`의 arXiv source는 모두 `https://export.arxiv.org/api/query?...`
  URL을 직접 사용한다.
- `SourceCollectionService.HttpSourceContentFetcher`는 `RestClient`로 URL을 바로
  호출한다.
- `--max`는 fetch 요청의 `max_results`가 아니라 fetch 후 `.take(max)` 제한이다.
- `CollectionFailureClassifier`는 HTTP 429를 `TRANSIENT_FETCH`, `retryable=true`로
  분류하지만 실제 retry/backoff 실행자는 없다.

## Design Goal

- arXiv export API 요청 사이에 최소 3초 간격을 둔다.
- arXiv 요청은 한 번에 하나만 실행한다.
- HTTP 429는 `Retry-After`를 우선하고, 없으면 보수적 기본 delay 후 제한된 횟수만
  재시도한다.
- RSS/Atom source fetch 동작은 바꾸지 않는다.
- Neo4j, article detail API, dataset/search evaluation artifact는 건드리지 않는다.

## Design

`HttpSourceContentFetcher`가 URL host를 보고 `export.arxiv.org`만 별도 경로로
처리한다. arXiv 경로는 fetcher 내부 lock으로 직렬화하며, 마지막 arXiv 요청 시작
시각을 기준으로 다음 요청 시작 전 최소 `sigak.collection.arxiv.min-request-interval`
만큼 기다린다.

429 응답은 `HttpClientErrorException`에서 감지한다. `Retry-After`가 seconds 형식이면
그 값을 쓰고, HTTP date 형식이면 현재 clock과의 차이를 계산한다. header가 없거나
해석 불가능하면 `sigak.collection.arxiv.rate-limit-retry-delay`를 쓴다. 재시도 횟수는
`sigak.collection.arxiv.max-rate-limit-retries`로 제한한다.

테스트는 실제 sleep 없이 검증하기 위해 fetch delay와 clock을 주입 가능한 작은
경계로 둔다. 운영에서는 `ThreadSourceFetchDelay`와 `Clock.systemUTC()`를 사용한다.

## Configuration

```yaml
sigak:
  collection:
    arxiv:
      min-request-interval: 3s
      rate-limit-retry-delay: 30s
      max-rate-limit-retries: 2
```

같은 값은 `.env.example`에도 `SIGAK_COLLECTION_ARXIV_*`로 노출한다.

## Verification Plan

- RED: arXiv 연속 요청 throttle, non-arXiv no throttle, 429 `Retry-After` retry,
  retry limit 테스트를 먼저 추가한다.
- GREEN: `HttpSourceContentFetcherTest` focused run.
- Regression: `SourceCollectionServiceTest`, `CollectionRunServiceTest`,
  `CollectionRunCommandRunnerTest`, `CollectionRunControllerTest`.
- Done gate: backend `./gradlew test`, `./gradlew check`.
