# 인프라

[English](README.md) | [한국어](README.ko.md)

Sigak 로컬 개발을 위한 인프라 파일을 모아 둔 디렉터리입니다.

## 책임
- Docker Compose 설정
- 로컬 서비스 연결
- Phase 6 persistence MVP를 위한 PostgreSQL
- 향후 search/vector service 설정

## PostgreSQL

로컬 데이터베이스 실행:

```bash
docker compose up -d postgres
```

로컬 데이터베이스 중지:

```bash
docker compose down
```

깨끗한 데이터베이스가 필요할 때 로컬 PostgreSQL volume 제거:

```bash
docker compose down -v
```

기본 로컬 값:

```txt
database: sigak
username: sigak
password: sigak
port: 5432
```
