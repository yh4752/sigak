# Home Latest Feed Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. 기존 브랜치에서 단일 agent로 실행한다.

**Goal:** 홈의 이름·정렬을 최신 글에 맞추고 근거 없는 인기 구분을 없앤다.

**Architecture:** React HomePage에서 복사한 배열을 게시일순으로 표시한다. 검색은 API 순서를 유지한다. 데이터와 AI boundary는 변경하지 않는다.

**Tech Stack:** React/TypeScript/Vitest/Testing Library, 기존 Vite/Compose web image.

## Task 1: UI 회귀 RED

- [x] `frontend/src/pages/HomePage.test.tsx`에 ‘최신 글’ 표시·인기/오늘 제목 제거·날짜순 정렬을 추가한다.
- [x] timezone/동일 날짜 ID 순서와 검색 관련도/원본 배열 보존을 검사한다.
- [x] `npm test -- src/pages/HomePage.test.tsx`로 기존 코드의 실패를 확인한다.

## Task 2: 최소 구현

- [x] `frontend/src/pages/HomePage.tsx`에서 popular/featured 분리를 삭제한다. 배열 복사 정렬은 다음 기준만 사용한다.

```ts
Date.parse(b.publishedAt) - Date.parse(a.publishedAt) || b.id - a.id
```

- [x] 홈은 `최신 글`을 렌더링하고 검색은 원래 articles를 렌더링한다. ArticleSection에 안정적인 id를 전달한다.
- [x] `npm test`, `npm run lint`, `npm run build` 전체 gate를 실행한다.

## Task 3: 실제 표시와 기록

- [x] web source image를 build/deploy하고 local/public HTTP smoke와 브라우저 홈 정렬을 확인한다.
- [x] 홈 방문 전후 AI budget를 읽어 신규 호출0을 확인한다.
- [x] STATUS/ROADMAP·dev-log/topic-queue와 RAG 문서 근거를 갱신하고 기존 draft PR에 반영한다.
