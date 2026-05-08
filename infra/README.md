# Infra

[English](README.md) | [한국어](README.ko.md)

Infrastructure files for local Sigak development.

## Responsibilities
- Docker Compose setup
- Local service wiring
- PostgreSQL for the Phase 6 persistence MVP
- Future search and vector service configuration

## PostgreSQL

Start the local database:

```bash
docker compose up -d postgres
```

Stop the local database:

```bash
docker compose down
```

Remove the local PostgreSQL volume when a clean database is needed:

```bash
docker compose down -v
```

Default local values:

```txt
database: sigak
username: sigak
password: sigak
port: 5432
```
