# 인프라

[English](README.md) | [한국어](README.ko.md)

Sigak 로컬 개발을 위한 인프라 파일을 모아 둔 디렉터리입니다.

## 책임
- Docker Compose 설정
- 로컬 서비스 연결
- PostgreSQL source-of-truth 저장소
- Elasticsearch keyword search projection
- Qdrant vector search projection
- Neo4j graph projection
- 로컬 enrichment와 embedding boundary를 위한 FastAPI AI 서버

## 서비스

| 서비스 | 목적 | 로컬 URL / 포트 |
| --- | --- | --- |
| PostgreSQL | Source-of-truth relational storage | `localhost:5432` |
| Elasticsearch | Keyword search projection | `http://localhost:9200` |
| Qdrant | Vector search projection | `http://localhost:6333` |
| Neo4j | Article/topic/relation graph projection | `http://localhost:7474`, `bolt://localhost:7687` |
| AI server | FastAPI mock enrichment와 향후 embedding endpoint | `http://localhost:8000` |

v0.1 MVP 기간에는 backend와 frontend를 아직 Compose에 넣지 않고 각 프로젝트 디렉터리에서 직접 실행합니다. Search infrastructure를 연결하는 동안에는 이 방식이 로컬 디버깅에 더 단순합니다.

## 로컬 인프라 실행

저장소 루트에서 실행:

```bash
docker compose -f infra/docker-compose.yml up -d
```

PostgreSQL만 실행:

```bash
docker compose -f infra/docker-compose.yml up -d postgres
```

로컬 인프라 중지:

```bash
docker compose -f infra/docker-compose.yml down
```

깨끗한 환경이 필요할 때 로컬 volume 제거:

```bash
docker compose -f infra/docker-compose.yml down -v
```

## Health check

Compose는 모든 로컬 인프라 서비스에 container health check를 정의합니다. 상태는 다음 명령으로 확인합니다.

```bash
docker compose -f infra/docker-compose.yml ps
```

Host에서 확인하는 smoke check:

```bash
curl http://localhost:9200/_cluster/health
curl http://localhost:6333/healthz
curl http://localhost:8000/health
```

Neo4j browser:

```txt
http://localhost:7474
username: neo4j
password: sigak-neo4j-password
```

## 기본 로컬 값

```txt
PostgreSQL database: sigak
PostgreSQL username: sigak
PostgreSQL password: sigak
PostgreSQL port: 5432
Elasticsearch URL: http://localhost:9200
Qdrant URL: http://localhost:6333
Neo4j URI: bolt://localhost:7687
Neo4j username: neo4j
Neo4j password: sigak-neo4j-password
AI server URL: http://localhost:8000
```
