# URL Source Registration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax. 기존 작업 브랜치를 재사용하고 외부 HTTP 전송만 한 명에게 위임한다.

**Goal:** URL 등록·무료 검증·활성화 후 재배포 없이 수집하고, 검증된 RSS/Atom 전문을 첫 클릭 요약에 재사용한다.

**Architecture:** PostgreSQL `news_sources`가 운영 registry 정본이다. Spring 운영 명령과 기존 수집/본문 worker가 source ID·설정 version을 공유한다. 공통 외부 HTTPS client는 검증한 IP에 연결을 고정하며 FastAPI·공개 DTO·유료 예산 계약은 유지한다.

**Tech Stack:** Kotlin/Spring Boot, JDBC/JPA, Flyway/PostgreSQL, Jsoup, namespace-aware XML, JUnit/Testcontainers. HTTPS DNS pinning은 Apache HttpComponents 5 전송기로 구현한다.

## Reference Spec

- `docs/superpowers/specs/2026-11-02-url-source-registration-design.md`
- `docs/CODING_CONVENTIONS.md`
- `docs/blog/WRITING_GUIDE.ko.md`

## File Structure

- Create `backend/src/main/resources/db/migration/V6__source_registry.sql`: 기존 source ID/FK를 유지하고 여섯 출처를 이관.
- Create `backend/src/main/kotlin/com/sigak/source/service/SourceDefinition.kt`, `SourceStore.kt`, `SourceManagementService.kt`, `SourceProbe.kt`, `SourceNetworkLock.kt`: 설정 DTO, DB, 명령 business logic, 무료 probe, 출처별 잠금.
- Create `backend/src/main/kotlin/com/sigak/source/runner/SourceCommandRunner.kt`, `scripts/manage-sources.sh`: 운영 명령과 worker 비활성 실행.
- Create `backend/src/main/kotlin/com/sigak/common/http/PublicDocumentClient.kt` 및 전송/정책 파일: 안전한 공통 HTTPS.
- Modify `SourceRegistry.kt`, `NewsSource.kt`, `CollectedArticle.kt`, `CollectedArticlePersistenceService.kt`, `SourceCollectionService.kt`, `CollectionRunService.kt`, `CollectionAutomationConfig.kt`: DB 정본과 고정 ID/설정 version 연결.
- Modify `RssAtomCollector.kt`, `ArxivCollector.kt`, `ArticleBodyExtractor.kt`, `ArticleBodyFetcher.kt`, `FullTextStore.kt`, `FullTextWorker.kt`: 전문 보존·설정 기반 추출·출처별 네트워크 정책.
- Modify `SummaryJobStore.kt`: 본문 enqueue의 출처 조회 경계에 필요한 경우만 수정. paid limit/identity/재시도 정책은 변경하지 않는다.
- Create/modify 위 기능별 unit/integration tests; 공식 표본 fixture는 테스트 입력으로만 보존한다.
- Update ADR/spec/STATUS/ROADMAP/SOURCE_POLICY/운영 안내/dev-log/topic-queue와 RAG review artifact.

## Task 1: DB registry와 고정 source ID

- [x] `SourceRegistryTest`를 PostgreSQL 테스트로 바꿔 여섯 활성 출처와 비활성 seed를 검사한다. 추가한 출처가 즉시 조회되고 표시 이름 변경이 ID를 바꾸지 않는 테스트를 먼저 실패시킨다.

```kotlin
val before = registry.sources().map { it.id }.toSet()
val source = sourceStore.register("https://engineering.example.test/", "engineering.example.test")
assertFalse(registry.sources().any { it.id == source.key })
assertEquals(6, before.size)
```

- [x] `./gradlew test --tests '*SourceRegistryTest'`로 RED를 확인한다.
- [x] V6에 site/feed URL, enabled, validation/version, body mode/selector, 허용 hosts, report와 collection 상태를 추가한다. 명시적 six-source 값으로 upsert하되 기존 ID와 article FK를 보존하고 seed는 비활성으로 둔다. canonical feed 유일성을 DB로 강제한다.
- [x] `SourceDefinition`/`SourceStore`를 구현하고 수집 DTO에 source key/version과 feed body 후보를 전달한다. source 이름 slug로 수집 출처를 생성하지 않는다.
- [x] 출처 변경은 기대 version으로 원자적으로 검사하고 disable 후 commit 경합은 row lock/version 검사로 막는다. unit fixture의 가상 출처도 명시적으로 등록한다.
- [x] focused tests를 GREEN으로 만들고 기존 registry를 쓰던 unit tests는 명시적인 test fixture로 바꾼다.

## Task 2: RSS/Atom와 공통 본문 추출

- [x] `RssAtomCollectorTest`에 prefix가 다른 RSS content namespace, Atom html/xhtml/text content, summary 분리, 외부 src/base64 거부, 코드와 마지막 절 보존 테스트를 추가해 RED를 본다.

```kotlin
val parsed = collector.parse(source, atomHtmlFixture).single()
assertTrue(parsed.feedBodyHtml!!.contains("Closing evidence"))
assertEquals("Short excerpt", parsed.extractedText)
assertEquals(source.id, parsed.sourceKey)
```

- [x] namespace-aware DOM parser와 Jsoup 텍스트 정리를 구현한다. Atom XHTML의 child markup을 보존하고 text content는 escape해서 처리한다.
- [x] `ArticleBodyExtractorTest`에 새 호스트 AUTO, 다중 root 거부, 명시 selector 무일치 거부, 링크 목록 거부, 발췌 표시 거부, 코드 보존을 추가해 RED를 본다.
- [x] `extractConfigured`/`extractFeed` 경계를 추가하고 공통 root 선택·noise 제거·유효 문단/문자/링크 비율 검사를 구현한다. 기존 세 사이트 recipe를 DB selector로 이관한다.
- [x] `./gradlew test --tests '*RssAtomCollectorTest' --tests '*ArticleBodyExtractorTest'`를 GREEN으로 만든다.

## Task 3: 공통 외부 HTTPS 전송 (독립 위임)

- [x] `PublicDocumentClientTest`에서 내부 주소, hostname suffix, userinfo, 비표준 port, cross-host redirect, 압축/2MiB 초과, 느린 DNS, DNS 재바인딩 거부 테스트를 RED로 확인한다.

```kotlin
val result = client.fetch("https://public.example.test/feed", setOf("public.example.test"))
assertEquals("https://public.example.test/feed", result.finalUrl)
assertEquals(validatedAddress, observedConnectionAddress)
```

- [x] `PublicDocumentClient.fetch(url, allowedHosts)`와 `ExternalDocument(text, finalUrl, contentType, durationMs)`를 구현한다. 15초 전체 deadline, 인증 host를 유지한 DNS pin, same-host redirect3회, 실제 읽은2MiB, no retry를 적용한다.
- [x] 기존 arXiv cooldown/retry wrapper를 보존하면서 fetchOnce와 본문 fetch가 새 client를 사용하도록 root에서 연결한다.
- [x] focused tests와 실제 공개 피드 무료 fetch로 전송 결과를 검사한다.

## Task 4: 등록·무료 표본 검증·상태 전이

- [x] `SourceManagementServiceTest`에 등록 중복/한글 이름, 피드 탐색0/1/여러 개, 외부 host 미승인, 최근 표본3개 미만, 전부 TOO_LONG, stale version/24시간 초과, configure 비활성 전환을 RED로 추가한다.

```kotlin
val source = service.register("https://engineering.example.test/")
val checked = service.check(source.key, source.version)
assertEquals("PASSED", checked.validationStatus)
assertFalse(checked.enabled)
val enabled = service.enable(checked.key, checked.version, reviewed = true)
assertTrue(enabled.enabled)
```

- [x] SourceProbe는 HTML alternate 피드/직접 XML, 허용 host, 최대3개 표본, 90초 deadline을 처리한다. 본문 앞·중간·뒤와 전체 텍스트는 운영 결과에만 내보내고 DB report는 metadata만 보관한다.
- [x] PASSED/NEEDS_CONFIG/BLOCKED를 영속화하고 current config version/24시간/원문 확인 없이 활성화하지 않는다. configure는 네트워크/추출 설정 변경 때 기존 검증을 무효화한다.
- [x] 출처별 advisory lock을 운영 probe·피드 fetch·본문 fetch에서 공유하며 network I/O 동안 transaction을 열지 않는다.
- [x] focused service tests를 GREEN으로 만든다. 모든 테스트는 fake external client로 paid 호출 없이 실행한다.

## Task 5: 수집·본문 worker와 전문 READY 연결

- [x] persistence integration test에서 FEED_FULL을 명시 검증한 source의 publication이 READY 본문·FEED provenance를 저장하고 `requested_summary=false`, summary jobs 증가0을 검사해 RED를 본다.
- [x] source FK/key/version을 전달하고 활성 version을 기사 commit 때 다시 검사한다. AUTO verified feed는 feed 경로를 쓰고 의심 입력만 PAGE로 넘긴다. FEED_FULL 오류는 해당 기사 실패로 저장한다.
- [x] `FullTextStore`는 DB source 설정으로 지원 여부를 확인하고 READY/FAILED/UNSUPPORTED의 기존 history를 보존한다. 본문 fetch는 source FK로 설정을 읽는다.
- [x] 조회 시 sourceIds filter가 비활성을 재활성화하지 않고 제외 이유가 상태에 드러나도록 한다. 401/403/429 피드 오류는 BLOCKED/비활성, 개별 본문 오류는 기존 캐시를 유지한다.
- [x] `./gradlew test --tests '*CollectedArticlePersistenceServiceTest' --tests '*CollectionRunServiceTest' --tests '*ArticleSummary*Test'`를 GREEN으로 만든다.

## Task 6: 운영 명령과 실행 경계

- [x] `SourceCommandRunnerTest`에 register/check/configure/enable/disable/list parser, 잘못된 옵션, JSON 결과, worker 활성 상태에서 실행 거부를 RED로 추가한다.
- [x] `--sigak.sources.command=<action>`과 명시적 option 파서/runner를 구현한다. wrapper가 web=none, collection automation=false, summary enabled/worker=false, fulltext worker=false를 강제한다.
- [x] scripts/manage-sources.sh는 기존 Compose의 backend image를 one-off로 실행한다. service dependency/ports를 새로 열지 않는다. 입력 URL·key만 받으며 비밀번호/API key를 출력하지 않는다.
- [x] shell syntax check와 focused runner tests를 GREEN으로 만든다.

## Task 7: 전체 검증·배포·출처 표본·문서

- [x] `./gradlew test check bootJar` 전체 gate를 실행하고 JUnit 결과의 실제 test/failure 수를 기록한다.
- [x] 배포 전 DB의 article/source/summary/budget counts와 hash를 비밀 노출 없이 저장한다. migration 백업을 보존하고 검증한 backend image로 교체한다.
- [x] 세 후보를 비활성 등록한다. 최근3개 원문 표본을 실제 JVM에서 비교하고 본문이 완전한 경로를 설정한다. 통과한 출처만 enable하며 실패는 원인을 그대로 기록한다.
- [x] 수집을 출처별 소수 글로 실행하고 무료 본문 READY·검색/graph projection·중복 수집·재시작·source disable을 검사한다. 실제 유료 summary POST는 보내지 않는다.
- [x] 누적 budget/summary history가 유지되고 새로운 paid attempt가0인지 확인한다. HTTP public/internal 제한을 smoke한다.
- [x] spec/ADR을 승인 상태로, STATUS/ROADMAP/SOURCE_POLICY와 운영 안내를 실제 결과로 갱신한다. RAG `validate-dataset --allow-draft`/`prepare-review`로 문서 근거를 맞춘다.
- [x] 개발 로그는 설계 세션 기록에 구현·실패·검증 결과를 구분해 추가하고 topic-queue 후보를 보강한다. 블로그 초안은 실제 구현/검증 근거만 사용하며 외부에 게시하지 않는다.
- [x] 독립 review 또는 자체 diff review, `git diff --check`, staged secret 검사 후 기능별 commit을 남긴다. 기존 draft PR은 필요 시 검증된 변경으로 갱신하되 main은 병합하지 않는다.

실행 근거: backend 전체 test/check/bootJar 338개·실패/오류/skip0. registry/Atom/한글 identity/피드 READY/외부 요청/상태 경합과 공개 소개글 없는 검색 회귀에서 RED 후 GREEN을 확인했다. 배포·원문 대조·출처 활성화 결과는 같은 날짜 개발 로그에 기록한다.

## 추가 발견: 공개 소개글과 검색 입력 분리

- [x] D2 실제 한글 검색 누락을 재현하고, 빈 sourceExcerpt의 PostgreSQL fallback 회귀를 RED로 확인했다.
- [x] `ArticleSearchContent`와 `ArticleService.getSearchArticles()`를 추가해 내부 검색 원문 1,200자와 공개 DTO를 분리했다. ES/Qdrant rebuild도 내부 입력을 사용한다. 전문 요약 입력은 자르지 않는다.
- [x] ES/vector/fallback 회귀와 backend 전체338개 gate를 통과했다.
- [x] 새 image 배포·색인 재생성 후 원래 한글 query와 공개 필드 비노출을 확인한다.

## 사용자 요청 후속: 세 출처 실제 AI 시험

- [x] 토스218·우아한형제들221·D2226의 본문/이력을 읽고 기존 예산 안의3회 요청으로 한정했다.
- [x] 렌더링 원문 대조로 토스 aside 목록 누락을 발견하고 회귀 RED 후 공통 추출을 최소 수정했다.
- [x] backend339개 test/check/bootJar를 통과하고 본문 추출 버전v2를 배포했다. 유료 작업이 없는 토스20개를 보존 원문에서 재추출했고13개가 달라졌다.
- [x] 세 본문 전체 텍스트/실제 AI 입력/근거를 확인하고 공개 첫 요청·재사용 시간, queue·처리시간·token을 기록했다.
- [x] 유료 시도+3/보수 차감+0.06달러/token 추정+0.005743달러, 재접속 추가0을 확인했다. 기존 UNKNOWN/한도는 보존했다.
- [x] HTTP 최종 검사·STATUS/ROADMAP/dev-log/topic-queue·RAG 근거와 저장소를 마감한다.

## Self-review

설계의 registry·identity·feed discovery·body provenance·network limits·migration·budget·operator/public 경계·검증 요구를 Task1–7에 대응했다. 관리자 UI·새 paid model·CAREER enum·일괄 backfill·기존 출처 중단은 추가하지 않는다. 구현 중 실제 구조로 method/file명이 달라지면 이 계획과 검증 근거도 함께 갱신한다.
