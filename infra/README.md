# Infra

[English](README.md) | [한국어](README.ko.md)

Infrastructure files for local Sigak development.

## Responsibilities
- Docker Compose setup
- Local service wiring
- PostgreSQL source-of-truth storage
- Elasticsearch keyword search projection
- Qdrant vector search projection
- Neo4j graph projection
- FastAPI AI server for local enrichment and embedding boundaries

## Services

| Service | Purpose | Local URL / port |
| --- | --- | --- |
| PostgreSQL | Source-of-truth relational storage | `localhost:5432` |
| Elasticsearch | Keyword search projection | `http://localhost:9200` |
| Qdrant | Vector search projection | `http://localhost:6333` |
| Neo4j | Article/topic/relation graph projection | `http://localhost:7474`, `bolt://localhost:7687` |
| AI server | FastAPI mock enrichment and future embedding endpoints | `http://localhost:8000` |

Backend and frontend are still run directly from their project directories during the v0.1 MVP. Keeping them outside Compose makes local debugging faster while the search infrastructure is being connected.

## Start Local Infrastructure

From the repository root:

```bash
docker compose -f infra/docker-compose.yml up -d
```

Start only PostgreSQL:

```bash
docker compose -f infra/docker-compose.yml up -d postgres
```

Stop the local infrastructure:

```bash
docker compose -f infra/docker-compose.yml down
```

Remove local volumes when a clean environment is needed:

```bash
docker compose -f infra/docker-compose.yml down -v
```

## Health Checks

Compose defines container health checks for all local infrastructure services. You can inspect status with:

```bash
docker compose -f infra/docker-compose.yml ps
```

Host-level smoke checks:

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

## Default Local Values

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
