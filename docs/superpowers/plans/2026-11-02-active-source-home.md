# Active Source Home Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. 기존 브랜치에서 단일 agent로 실행한다.

**Goal:** 수집과 홈은 활성3개 출처로 좁히고 데모를 숨기며 과거 기사·유료 이력을 보존한다.

**Architecture:** 기존 DB source flag를 ArticleResponse로 전달하고 HomePage에서 표시 정책을 적용한다. 직접 읽기/검색 projection은 보존한다.

**Tech Stack:** Kotlin/Spring/JPA/JDBC, React/TypeScript/Zod/Vitest, 기존 운영 CLI/Compose.

## Task 1: 운영 상태

- [x] 출처별 article/job 수와 버전을 기록하고 옛6개만 disable한다. 세 신규 출처의 설정/version은 유지한다.
- [x] 기존 기사·요약/budget를 백업/대조하고 삭제하지 않는다.

## Task 2: 회귀 RED와 최소 구현

- [x] ArticleServiceTest에 활성 flag의 변경과 비활성 기사 조회 보존을 추가한다. controller 응답 metadata도 검사한다.
- [x] HomePage.test.tsx에 disabled/demo 제외·전부 제외된 빈 상태·실제 보관 검색을 추가하고 실패를 확인한다.
- [x] NewsSourceEntity.collectionEnabled를 기존 열에 매핑하고 ArticleResponse.sourceEnabled와 ArticleService mapping을 연결한다.
- [x] API Zod에 optional boolean 검증을 추가한다. HomePage는 curated-seed를 제외하고 sourceEnabled===true를 최신 홈에만 사용한다.
- [x] backend `./gradlew test check bootJar`, frontend `npm test`, `npm run lint`, `npm run build`를 실행한다.

## Task 3: 배포·실제 확인

- [x] 검증 JAR/web image를 준비해 backend→web 순으로 배포한다. AI는 변경하지 않는다.
- [x] 옛6개 요청 selected0, 활성3개 요청/자동 주기의 duplicate 성공과 source registry 즉시 반영을 확인한다.
- [x] public home의 출처/개수/데모 제외·HTTP smoke·budget/job/기사 보존을 확인한다. 새 기사 상세는 클릭하지 않는다.
- [x] STATUS/ROADMAP/API_SPEC/SOURCE_OPERATIONS·dev-log/topic-queue·RAG 근거를 갱신하고 기존 draft PR에 반영한다.

검증: backend344/실패0, frontend70/lint/build, HTTP local/public7each. 자동3개/중복50/실패0, 홈50, 보관 hash·예산 동일. PR 반영은 완료 시 Git 이력과 함께 확인한다.
