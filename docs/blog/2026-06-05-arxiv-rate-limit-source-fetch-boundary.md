---
title: "arXiv Rate Limit을 Source Fetch Boundary에서 해결한 이유"
date: "2026-06-05"
type: "deep-dive"
project: "sigak"
tags: ["Backend", "Collection", "Spring Boot", "Testing"]
summary: "arXiv export API의 429 실패를 단순 재실행이 아니라 source fetch 경계의 throttle, bounded retry, runtime smoke로 해결한 과정을 정리합니다."
featured: false
draft: true
canonicalProjectPath: "docs/blog/2026-06-05-arxiv-rate-limit-source-fetch-boundary.md"
relatedPosts: ["sigak/2026-05-31-collection-failure-evidence", "sigak/2026-06-03-arxiv-rate-limit-dev-log", "sigak/2026-06-05-dev-log"]
---

# arXiv Rate Limit을 Source Fetch Boundary에서 해결한 이유

> 한 줄 요약: Sigak은 arXiv `429 Too Many Requests`를 단순히 “다시 실행하면 되는 실패”로 두지 않고, `export.arxiv.org` source fetch 경계에서 요청 간격, 단일 실행, bounded retry를 보장하도록 바꿨다.

## 배경과 문제

Sigak의 collection flow는 선택한 source를 가져와 article로 변환하고, mock enrichment와 persistence를 거쳐 public article API에서 볼 수 있게 만든다.

```txt
selected sources
-> source fetch
-> parse
-> normalize
-> enrich
-> publish
```

2026-06-03 dataset/search evaluation 확장 중 다음 명령을 실행했다.

```bash
./gradlew bootRun --args='collection-run --sources=openai-blog,google-ai-blog,arxiv-cs-ai,arxiv-cs-lg,arxiv-cs-cl --max=5'
```

결과는 `PARTIAL`이었다. 5개 source 중 4개는 fetched, 1개는 failed였다. 실패 source는 `arxiv-cs-ai`였고, failure summary는 다음 성격이었다.

```txt
stage=FETCH_SOURCE
failureKind=TRANSIENT_FETCH
retryable=true
HTTP 429 Too Many Requests / Rate exceeded
```

흥미로운 점은 같은 run에서 `arxiv-cs-lg`, `arxiv-cs-cl`은 성공했다는 것이다. 그래서 처음 의심해야 할 곳은 parser나 persistence 전체가 아니었다. 문제는 `export.arxiv.org`를 연속 호출하는 source fetch 방식이었다.

arXiv API 문서를 확인하면 legacy API는 관리 머신 합산 기준으로 3초에 1요청 이하와 단일 연결을 요구한다. User Manual도 연속 호출 시 delay와 cache를 권장한다.

- arXiv API Terms of Use: <https://info.arxiv.org/help/api/tou.html>
- arXiv API User Manual: <https://info.arxiv.org/help/api/user-manual.html>

기존 Sigak의 `HttpSourceContentFetcher`는 이 정책을 모른 채 URL을 바로 `RestClient`에 넘겼다.

```kotlin
restClient
    .get()
    .uri(url)
    .retrieve()
    .body(String::class.java)
```

`CollectionFailureClassifier`는 이미 429를 `TRANSIENT_FETCH`, `retryable=true`로 분류했다. 하지만 여기에는 중요한 간극이 있었다. “다시 시도할 수 있는 실패라고 기록하는 것”과 “정책에 맞게 다시 시도하는 것”은 다르다.

## 제약

이번 작업에는 명확한 제약이 있었다.

- Spring Boot backend가 collection의 실행 경계다.
- FastAPI는 AI/RAG 전용이므로 source fetch 정책을 옮기지 않는다.
- frontend, article detail API, Neo4j 작업, dataset/search evaluation artifact는 건드리지 않는다.
- RSS/Atom source fetch 동작을 불필요하게 바꾸지 않는다.
- 실제 arXiv API를 반복 stress test하지 않는다.
- 검증 결과는 실제 실행한 명령만 기록한다.

이 제약 때문에 “전체 collection retry system”보다 “arXiv source fetch 정책”이 더 알맞은 범위였다.

## 선택지와 결정

| 선택지 | 내용 | 장점 | 한계 |
| --- | --- | --- | --- |
| 실패 기록만 유지 | 429를 `retryable=true`로 남기고 사람이 다시 실행 | 구현이 가장 작음 | 같은 원인으로 다시 실패할 수 있고, API 정책 준수를 코드가 보장하지 않음 |
| 모든 source fetch에 일반 retry 적용 | RSS/Atom과 arXiv에 같은 retry/backoff 적용 | 재사용성이 좋아 보임 | 4xx/5xx 의미가 source마다 다르고, 불필요한 retry가 source format 문제를 숨길 수 있음 |
| arXiv host 전용 정책 추가 | `export.arxiv.org`만 throttle, single-flight, 429 retry 적용 | 문제 원인과 해결 위치가 일치함 | fetcher 내부에 source-specific 분기가 생김 |
| scheduler/queue 도입 | source별 next-allowed-at과 retry lifecycle 관리 | 운영형 구조로 확장 가능 | MVP 범위에 비해 큼 |

선택은 세 번째였다.

```txt
HttpSourceContentFetcher
-> URL host 확인
-> export.arxiv.org 이면 arXiv policy 적용
-> 그 외 source는 기존 direct fetch 유지
```

이 위치를 고른 이유는 실패가 collection orchestration이나 parsing이 아니라 HTTP source fetch 정책에서 발생했기 때문이다. `CollectionRunService`는 run을 조율하고, `SourceCollectionService`는 한 source를 fetch/parse/publish한다. 그중 “외부 source URL을 HTTP로 가져오는 책임”은 `HttpSourceContentFetcher`에 있다.

## 구현

### 1. arXiv host만 별도 경로로 처리

`HttpSourceContentFetcher`는 URL host가 `export.arxiv.org`인지 확인한다.

```kotlin
override fun fetch(url: String): String =
    if (isArxivExportUrl(url)) {
        fetchArxiv(url)
    } else {
        fetchOnce(url)
    }
```

이렇게 하면 RSS/Atom source는 기존처럼 한 번 fetch한다. arXiv source만 policy를 탄다.

### 2. 같은 JVM 안의 arXiv 요청 직렬화

arXiv export API는 단일 연결과 낮은 요청 빈도를 요구하므로, 같은 Spring Boot process 안에서는 lock으로 arXiv 요청을 직렬화했다.

```kotlin
private val arxivLock = Any()
private var lastArxivRequestStartedAt: Instant? = null
```

다음 arXiv 요청은 마지막 요청 시작 시각에서 `minRequestInterval`이 지난 뒤 시작한다.

```kotlin
private fun waitForNextArxivRequest() {
    val now = clock.instant()
    val nextAllowedAt = lastArxivRequestStartedAt?.plus(arxivFetchProperties.minRequestInterval)
    val waitDuration = nextAllowedAt?.let { allowedAt -> Duration.between(now, allowedAt) }

    if (waitDuration != null && waitDuration.isPositive()) {
        sourceFetchDelay.sleep(waitDuration)
    }

    // arXiv export API는 관리 머신 합산 3초 1요청 정책이 있어 요청 시작 시각을 직렬화한다.
    lastArxivRequestStartedAt = clock.instant()
}
```

여기서 중요한 점은 “요청 완료 시각”이 아니라 “요청 시작 시각” 기준으로 간격을 둔다는 것이다. 정책은 요청 빈도에 대한 것이고, 한 요청이 오래 걸렸다고 다음 요청을 더 촘촘히 보내면 안 된다.

### 3. 429는 Retry-After를 우선한다

429가 오면 무조건 고정 delay를 쓰지 않는다. 응답 header에 `Retry-After`가 있으면 그 값을 우선한다.

```kotlin
private fun retryDelayFor(exception: HttpClientErrorException): Duration {
    val retryAfter = exception.responseHeaders?.getFirst(RETRY_AFTER_HEADER)
    return retryAfterDuration(retryAfter) ?: arxivFetchProperties.rateLimitRetryDelay
}
```

`Retry-After`는 seconds 형식일 수도 있고 HTTP-date 형식일 수도 있다. 둘 다 처리하고, 해석할 수 없으면 보수적인 기본 delay로 fallback한다.

### 4. retry는 반드시 bounded로 둔다

재시도는 무한히 돌면 안 된다. 특히 외부 API rate limit에서는 재시도 자체가 추가 부하가 될 수 있다. 그래서 429 retry와 transient read timeout retry 모두 횟수를 제한했다.

```kotlin
private class ArxivRetryBudget(
    private var remainingRateLimitRetries: Int,
    private var remainingTransientFetchRetries: Int
)
```

이 구조는 작지만 의미가 있다. 실패를 감추기 위한 retry가 아니라, transient 실패를 한정적으로 흡수하는 retry다.

### 5. 실제 sleep 없이 테스트 가능한 경계

rate limit 정책은 시간과 sleep이 들어가므로 테스트가 느려지기 쉽다. 그래서 `Clock`과 `SourceFetchDelay`를 주입 가능하게 분리했다.

운영에서는 실제 `Clock.systemUTC()`와 `ThreadSourceFetchDelay`를 쓴다. 테스트에서는 `MutableClock`과 `RecordingSourceFetchDelay`를 써서 실제로 기다리지 않고 delay 기록만 검증한다.

```kotlin
private class RecordingSourceFetchDelay(
    private val clock: MutableClock
) : SourceFetchDelay {
    val durations = mutableListOf<Duration>()

    override fun sleep(duration: Duration) {
        durations.add(duration)
        clock.advance(duration)
    }
}
```

이 덕분에 “3초 기다렸는가”를 3초 동안 기다리지 않고 검증할 수 있다.

## 설정

최종 설정은 다음처럼 구성했다.

```yaml
sigak:
  collection:
    http:
      connect-timeout: 5s
      read-timeout: 30s
    arxiv:
      min-request-interval: 3s
      rate-limit-retry-delay: 30s
      transient-fetch-retry-delay: 5s
      max-transient-fetch-retries: 1
      max-rate-limit-retries: 2
```

초기에는 HTTP read timeout이 10초였다. 실제 runtime smoke에서 `arxiv-cs-cl` read timeout이 반복되어 기본값을 30초로 올렸다. 이 변경도 `CollectionHttpPropertiesTest`로 고정했다.

## 검증

검증은 세 층으로 나눴다.

### 1. 정책 단위 테스트

`HttpSourceContentFetcherTest`는 실제 arXiv를 호출하지 않는다. 대신 `MockRestServiceServer`, fake clock, fake delay로 다음을 검증한다.

| 테스트 | 검증 내용 |
| --- | --- |
| `delaysConsecutiveArxivExportRequestsByTheConfiguredMinimumInterval` | arXiv 연속 요청 사이에 3초 delay 기록 |
| `doesNotThrottleNonArxivSources` | non-arXiv source는 delay 없음 |
| `retriesArxivRateLimitAfterRetryAfterHeader` | 429 후 `Retry-After: 7`을 따른 뒤 성공 |
| `stopsRetryingArxivRateLimitAfterConfiguredRetryLimit` | retry limit 이후 429를 그대로 던짐 |
| `retriesArxivReadTimeoutWithConfiguredTransientDelay` | read timeout 후 transient delay로 한 번 재시도 |

실제로 실행한 명령:

```bash
./gradlew test --tests com.sigak.collection.service.HttpSourceContentFetcherTest
```

결과는 `BUILD SUCCESSFUL`이었다.

### 2. 설정과 regression 테스트

source HTTP timeout과 arXiv 설정 binding은 별도 테스트로 고정했다.

```bash
./gradlew test --tests com.sigak.collection.config.CollectionHttpPropertiesTest --tests com.sigak.collection.service.HttpSourceContentFetcherTest
```

결과는 `BUILD SUCCESSFUL`이었다.

collection 주변 regression도 실행했다.

```bash
./gradlew test \
  --tests com.sigak.collection.service.HttpSourceContentFetcherTest \
  --tests com.sigak.collection.service.SourceCollectionServiceTest \
  --tests com.sigak.collection.service.CollectionRunServiceTest \
  --tests com.sigak.collection.runner.CollectionRunCommandRunnerTest \
  --tests com.sigak.collection.controller.CollectionRunControllerTest
```

결과는 `BUILD SUCCESSFUL`이었다.

전체 backend gate도 실행했다.

```bash
./gradlew test
./gradlew check
```

둘 다 `BUILD SUCCESSFUL`이었다.

### 3. 실제 arXiv runtime smoke

처음 runtime smoke는 반복 실행 때문에 `arxiv-cs-cl`에서 read timeout과 429가 남았다. 그래서 issue #14는 바로 닫지 않았다. 실제 외부 API 검증에서는 테스트 자체가 rate limit을 유발할 수 있기 때문이다.

이후 cooldown을 둔 뒤 2026-06-05에 원래 5-source command를 한 번만 실행했다.

```bash
./gradlew bootRun --args='collection-run --sources=openai-blog,google-ai-blog,arxiv-cs-ai,arxiv-cs-lg,arxiv-cs-cl --max=5'
```

결과:

```txt
Collection run status: COMPLETED
runId=78c09e27-6975-41a4-bea8-50c164223e6a
sources selected=5 fetched=5 failed=0
articles discovered=25 published=15 skipped=10 failed=0
durationMs=8125
```

이 결과를 GitHub issue #14에 comment로 남기고 issue를 닫았다.

## 트레이드오프

### 얻은 것

- arXiv source 간 요청이 같은 JVM 안에서 3초보다 촘촘해지지 않는다.
- 429는 `Retry-After`를 우선하는 bounded retry로 처리한다.
- read timeout도 한 번은 transient하게 재시도한다.
- fake clock/delay 덕분에 시간 정책을 빠르게 테스트할 수 있다.
- 실제 arXiv runtime smoke까지 통과한 뒤 issue를 닫았다.

### 미룬 것

- 여러 process나 여러 command-run이 동시에 실행되는 cross-process cooldown guard는 없다.
- arXiv fetch cache는 아직 없다.
- full scheduler/queue 기반 collection lifecycle은 아직 없다.
- 실제 arXiv API를 상대로 stress test하지 않았다.

여기서 stress test를 하지 않은 것은 회피가 아니라 정책 준수다. 외부 API rate limit 문제를 해결하면서 외부 API를 반복 호출해 검증하는 것은 검증 자체가 문제를 만들 수 있다. 반복 실행 시나리오는 fake server와 fake clock으로 검증해야 한다.

## 내가 이해한 것

이번 작업에서 배운 핵심은 `retryable=true`와 retry 실행은 다르다는 점이다.

`retryable=true`는 운영자에게 “다시 시도해 볼 수 있는 실패”라고 알려주는 진단 정보다. 하지만 실제 retry는 다음 질문에 답해야 한다.

- 언제 다시 시도할 것인가?
- 몇 번까지만 시도할 것인가?
- 어떤 실패에만 적용할 것인가?
- 외부 API 정책을 어기지 않는가?
- 테스트는 실제 sleep과 실제 외부 API 없이 검증 가능한가?

또 하나는 boundary의 중요성이다. 이 문제를 `CollectionRunService`에 넣으면 run orchestration과 HTTP 정책이 섞인다. `SourceRegistry`에 넣으면 source metadata와 runtime behavior가 섞인다. `HttpSourceContentFetcher`에 넣으면 “외부 source를 가져오는 방법”이라는 책임 안에서 해결할 수 있다.

## AI와 검토 경계

Codex는 원인 추적, 테스트 작성, 구현, 문서 초안 정리를 도왔다. 검증 결과는 실제로 실행한 명령과 출력만 사용했다. 특히 runtime smoke가 `PARTIAL`이던 상태에서는 issue를 닫지 않았고, cooldown 후 단일 smoke가 `COMPLETED`로 끝난 뒤에만 #14를 close했다.

## 다시 볼 코드

- `backend/src/main/kotlin/com/sigak/collection/service/SourceCollectionService.kt`
  - `HttpSourceContentFetcher`, arXiv host detection, throttle, retry budget이 들어 있다.
- `backend/src/main/kotlin/com/sigak/collection/config/CollectionHttpConfig.kt`
  - source HTTP timeout, arXiv fetch properties, clock/delay bean을 정의한다.
- `backend/src/test/kotlin/com/sigak/collection/service/HttpSourceContentFetcherTest.kt`
  - 실제 arXiv 호출 없이 throttle/retry 정책을 검증한다.
- `backend/src/test/kotlin/com/sigak/collection/config/CollectionHttpPropertiesTest.kt`
  - timeout과 arXiv 설정 binding을 고정한다.
- `docs/blog/2026-06-03-arxiv-rate-limit-dev-log.md`
  - 구현 당일의 실패, 보강, runtime smoke 실패 기록이다.
- `docs/blog/2026-06-05-dev-log.md`
  - cooldown 후 runtime smoke 통과와 #14 close 기록이다.

## 면접 질문

1. 왜 이 문제를 `CollectionRunService`가 아니라 `HttpSourceContentFetcher`에서 해결했나요?
2. `retryable=true`와 실제 retry 실행은 어떻게 다른가요?
3. arXiv source에만 source-specific policy를 둔 이유는 무엇인가요?
4. 외부 API rate limit을 테스트할 때 실제 API stress test를 피해야 하는 이유는 무엇인가요?
5. `Retry-After` header를 우선하는 것이 왜 중요한가요?
6. fake clock과 fake delay는 어떤 테스트 문제를 해결했나요?
7. read timeout 기본값을 10초에서 30초로 올린 trade-off는 무엇인가요?
8. cross-process cooldown guard를 아직 넣지 않은 이유와, 나중에 넣는다면 어디에 둘 수 있나요?
