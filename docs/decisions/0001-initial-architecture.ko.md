# 0001: 초기 아키텍처

[English](0001-initial-architecture.md) | [한국어](0001-initial-architecture.ko.md)

## 상태
승인됨

## 날짜
2026-05-05

## 맥락
Sigak은 2026년 6월 말까지 포트폴리오급 MVP가 되어야 합니다. 아키텍처는 첫 동작 제품을 늦추지 않으면서도 실용적인 백엔드 설계와 AI 연동을 보여줘야 합니다.

## 결정
Spring Boot를 메인 애플리케이션 백엔드로 사용하고, FastAPI는 AI/RAG 관련 기능에만 사용하며, 프론트엔드는 React + TypeScript + Vite로 구성합니다.

프론트엔드는 주로 Spring Boot와 통신합니다. Spring Boot는 summarization, embedding, future RAG behavior처럼 AI 전용 작업이 필요한 기능에서 FastAPI를 호출할 수 있습니다.

로컬 개발에는 Docker Compose를 사용합니다. Elasticsearch와 Qdrant는 계획에 포함하되, MVP에서 search와 vector retrieval이 실제로 필요해질 때까지 구현하지 않습니다.

초기 MVP에서는 Next.js를 사용하지 않습니다.

## 결과
- 메인 백엔드의 API 경계가 명확해집니다.
- AI 관심사는 LLM과 embedding library를 다루기 쉬운 Python service로 분리됩니다.
- 프론트엔드는 단순하고 빠르게 개발할 수 있습니다.
- MVP 이전에는 cloud-specific dependency를 피합니다.
- Search와 vector infrastructure는 초기 기능을 막지 않고 나중에 추가할 수 있습니다.

## 검토한 대안
- 단일 FastAPI backend: 처음에는 단순하지만, Spring Boot backend 포트폴리오 목표와 맞지 않습니다.
- Next.js full-stack app: 제품 속도에는 편리하지만, 초기 MVP의 백엔드 분리 방향과 충돌합니다.
- Elasticsearch와 Qdrant 즉시 구현: 나중에는 유용하지만, article workflow가 안정되기 전에는 인프라 부담이 큽니다.
