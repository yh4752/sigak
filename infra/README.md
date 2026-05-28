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
| DB schema | On-demand SchemaSpy ERD/HTML generation | `outputs/db-schema/index.html` |

Backend and frontend are still run directly from their project directories during the v0.1 MVP. Keeping them outside Compose makes local debugging faster while the search infrastructure is being connected.

The `db-schema` service is behind the `tools` profile, so it is not started by the default Compose command.

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

## Docker Command Cheat Sheet

Run these commands from the repository root (`/Users/yonghyun/my-projects/sigak`).

| Situation | Command | Description |
| --- | --- | --- |
| Start all infrastructure | `docker compose -f infra/docker-compose.yml up -d` | Starts PostgreSQL, Elasticsearch, Qdrant, Neo4j, and the AI server in the background. |
| Start only PostgreSQL | `docker compose -f infra/docker-compose.yml up -d postgres` | Use this when only the database is needed. This is enough before running SchemaSpy. |
| Inspect running status | `docker compose -f infra/docker-compose.yml ps` | Shows which containers are running and healthy. |
| Tail one service log | `docker compose -f infra/docker-compose.yml logs -f postgres` | Streams PostgreSQL logs. Replace `postgres` with `elasticsearch`, `qdrant`, `neo4j`, or `ai` as needed. |
| Restart one service | `docker compose -f infra/docker-compose.yml restart postgres` | Restarts only PostgreSQL. The same pattern works for other services. |
| Stop only PostgreSQL | `docker compose -f infra/docker-compose.yml stop postgres` | Stops the database container while keeping local data. |
| Stop all infrastructure | `docker compose -f infra/docker-compose.yml down` | Removes containers and the Compose network. Docker volume data is preserved. |
| Generate DB ERD | `docker compose -f infra/docker-compose.yml --profile tools run --rm db-schema` | Runs SchemaSpy once and removes the tool container afterward. |
| Reset all local data | `docker compose -f infra/docker-compose.yml down -v` | Deletes local volumes. This removes PostgreSQL, Elasticsearch, Qdrant, and Neo4j data, so use it carefully. |

For everyday development, `up -d`, `ps`, `logs -f`, and `down` are usually enough. Use `down -v` only when you intentionally want a clean local data reset.

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

## DB Schema Visualization

Sigak uses SchemaSpy to generate a local HTML ERD from the PostgreSQL schema. PostgreSQL can stay running while this tool runs; the SchemaSpy container starts only for this command and is removed afterward.

Start PostgreSQL if it is not already running:

```bash
docker compose -f infra/docker-compose.yml up -d postgres
```

Generate the schema documentation:

```bash
docker compose -f infra/docker-compose.yml --profile tools run --rm db-schema
```

Open the generated file in a browser:

```txt
outputs/db-schema/index.html
```

Regenerate it whenever migrations or entity mappings change. Generated files under `outputs/` are local artifacts and should not be committed.

SchemaSpy may print non-fatal PostgreSQL catalog or Graphviz label warnings while generating the report. Treat the run as successful when the command exits with code `0` and `outputs/db-schema/index.html` exists.

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
