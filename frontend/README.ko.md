# 프론트엔드

[English](README.md) | [한국어](README.ko.md)

Sigak 프론트엔드는 React, TypeScript, Vite를 사용합니다.

## 책임
- Article list UI
- Article detail UI
- Search UI
- AI summary와 insight 표시
- 백엔드 통신을 위한 API client module
- API boundary에서 런타임 응답 검증

## 현재 상태
첫 번째 프론트엔드 구현은 Spring Boot article API를 기반으로 하는 최소 홈/검색 UI와 article 상세 페이지를 포함합니다.

Article 상세 페이지는 summary, why-it-matters 맥락, topics, source metadata, related articles를 강조합니다. 원시 `importanceScore`는 의도적으로 표시하지 않으며, 현재는 article list ranking에만 사용합니다.

## API Client 방향
- Spring Boot 백엔드 HTTP 요청에는 Axios를 사용합니다.
- UI component가 응답을 사용하기 전에 Zod로 backend API response를 검증합니다.
- component에서 백엔드를 직접 호출하지 않고, API 접근은 `src/api/` 아래에 둡니다.
- 백엔드 base URL은 `VITE_API_BASE_URL`로 설정하며, 로컬 개발 기본값은 `http://localhost:8080`입니다.

## 로컬 실행
필요 조건:
- Node.js
- npm

이 디렉터리에서 실행합니다.

```bash
npm install
npm run dev
```

기본 Vite dev server URL:

```txt
http://localhost:5173
```

## 테스트
이 디렉터리에서 실행합니다.

```bash
npm test
```
