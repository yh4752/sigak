# DB Schema Visualization Design

## Goal

Sigak의 PostgreSQL 스키마를 로컬에서 쉽게 시각화할 수 있도록 SchemaSpy 기반의 일회성 ERD 생성 도구를 추가한다.

## Context

현재 로컬 인프라는 `infra/docker-compose.yml`에서 PostgreSQL, Elasticsearch, Qdrant, Neo4j, AI server를 실행한다. DB 구조 확인은 개발 중 반복적으로 필요하지만, ERD 도구를 상시 실행 서비스로 추가하면 기본 개발 환경이 불필요하게 무거워질 수 있다.

## Decision

SchemaSpy를 Docker Compose `tools` profile에 포함한다. 기본 `docker compose up -d`에는 실행되지 않고, 사용자가 명시적으로 `--profile tools run --rm db-schema`를 실행할 때만 PostgreSQL 스키마를 읽어 HTML 문서를 생성한다.

## Output

생성 결과는 `outputs/db-schema/`에 둔다. 이 디렉터리는 로컬 산출물이므로 Git에 커밋하지 않는다.

## Trade-offs

- SchemaSpy는 실시간 DB 탐색 도구가 아니라 문서 생성 도구다.
- DBeaver나 IntelliJ Database 탭보다 즉석 탐색성은 낮지만, 프로젝트에 재현 가능한 ERD 생성 방법을 남길 수 있다.
- Compose profile로 분리하면 기본 인프라 실행 비용을 늘리지 않는다.

## Success Criteria

- 기본 Compose 실행에 `db-schema`가 포함되지 않는다.
- PostgreSQL이 떠 있는 상태에서 SchemaSpy를 실행하면 ERD HTML이 생성된다.
- README에서 실행 명령과 결과 확인 위치를 알 수 있다.
