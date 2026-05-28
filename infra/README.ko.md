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
| DB schema | 필요할 때 실행하는 SchemaSpy ERD/HTML 생성 | `outputs/db-schema/index.html` |

v0.1 MVP 기간에는 backend와 frontend를 아직 Compose에 넣지 않고 각 프로젝트 디렉터리에서 직접 실행합니다. Search infrastructure를 연결하는 동안에는 이 방식이 로컬 디버깅에 더 단순합니다.

`db-schema` 서비스는 `tools` profile 뒤에 두었기 때문에 기본 Compose 실행에는 포함되지 않습니다.

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

## Docker 명령어 치트시트

아래 명령은 모두 저장소 루트(`/Users/yonghyun/my-projects/sigak`)에서 실행합니다.

| 상황 | 명령어 | 설명 |
| --- | --- | --- |
| 전체 인프라 켜기 | `docker compose -f infra/docker-compose.yml up -d` | PostgreSQL, Elasticsearch, Qdrant, Neo4j, AI server를 백그라운드로 실행합니다. |
| PostgreSQL만 켜기 | `docker compose -f infra/docker-compose.yml up -d postgres` | DB만 필요할 때 사용합니다. SchemaSpy 실행 전에도 이 명령이면 충분합니다. |
| 실행 상태 보기 | `docker compose -f infra/docker-compose.yml ps` | 어떤 컨테이너가 켜져 있고 healthy인지 확인합니다. |
| 특정 서비스 로그 보기 | `docker compose -f infra/docker-compose.yml logs -f postgres` | PostgreSQL 로그를 실시간으로 봅니다. `postgres` 대신 `elasticsearch`, `qdrant`, `neo4j`, `ai`를 넣을 수 있습니다. |
| 특정 서비스 재시작 | `docker compose -f infra/docker-compose.yml restart postgres` | DB만 다시 시작합니다. 다른 서비스명도 같은 방식으로 사용할 수 있습니다. |
| PostgreSQL만 잠시 끄기 | `docker compose -f infra/docker-compose.yml stop postgres` | 데이터는 유지하고 DB 컨테이너만 중지합니다. |
| 전체 인프라 끄기 | `docker compose -f infra/docker-compose.yml down` | 컨테이너와 네트워크를 정리합니다. Docker volume 데이터는 유지됩니다. |
| DB ERD 생성 | `docker compose -f infra/docker-compose.yml --profile tools run --rm db-schema` | SchemaSpy를 한 번 실행하고 종료 후 컨테이너를 삭제합니다. |
| 전체 데이터 초기화 | `docker compose -f infra/docker-compose.yml down -v` | 로컬 volume까지 삭제합니다. PostgreSQL, Elasticsearch, Qdrant, Neo4j 데이터가 사라지므로 신중하게 사용합니다. |

평소 개발에서는 보통 `up -d`, `ps`, `logs -f`, `down`만 알면 충분합니다. `down -v`는 로컬 데이터를 완전히 지우고 다시 시작하고 싶을 때만 사용합니다.

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

## Elasticsearch keyword search smoke test

Article search, projection rebuild, Elasticsearch mapping, 로컬 인프라를 변경한 뒤에는 이 흐름으로 확인합니다. 목적은 네 가지를 분리해 증명하는 것입니다. PostgreSQL이 살아 있는지, Elasticsearch가 살아 있는지, projection rebuild가 되는지, Elasticsearch가 내려갔을 때 public search API가 fallback하는지를 각각 확인합니다.

Keyword search에 필요한 서비스만 실행합니다.

```bash
docker compose -f infra/docker-compose.yml up -d postgres elasticsearch
docker compose -f infra/docker-compose.yml ps postgres elasticsearch
```

다른 터미널에서 backend를 실행합니다.

```bash
cd backend
./gradlew bootRun
```

Elasticsearch article projection을 rebuild합니다.

```bash
curl -X POST http://localhost:8080/api/internal/search-projections/articles/rebuild
```

기대 신호:

```json
{"status":"completed","indexName":"sigak-articles-v1","indexedCount":5,"durationMs":347,"failedReason":null}
```

Elasticsearch index count를 확인합니다.

```bash
curl http://localhost:9200/sigak-articles-v1/_count
```

기대 신호:

```json
{"count":5}
```

Public search API를 호출합니다.

```bash
curl "http://localhost:8080/api/articles?query=graph"
```

기대 신호: 응답에 article `4`, `New Research Maps Failure Modes in Graph RAG Systems`가 포함됩니다. Backend log에 `fallback=false`가 남으면 Elasticsearch 경로를 사용했다는 뜻입니다.

Internal search metric을 확인합니다.

```bash
curl http://localhost:8080/api/internal/search-metrics/articles
```

기대 신호: `totalSearchCount`와 `elasticsearchSearchCount`가 증가하고, `lastSearch.fallback`이 `false`입니다.

Fallback 동작을 확인합니다.

```bash
docker compose -f infra/docker-compose.yml stop elasticsearch
curl "http://localhost:8080/api/articles?query=graph"
docker compose -f infra/docker-compose.yml up -d elasticsearch
```

기대 신호: public API가 여전히 matching article을 반환하고, backend log에 `fallback=true`가 남습니다. 이후 검색 개발을 계속할 예정이면 Elasticsearch를 다시 켜 둡니다.

Fallback 이후 metrics endpoint를 다시 호출하면 `fallbackSearchCount`가 증가하고 `lastSearch.fallback`이 `true`여야 합니다.

## DB schema 시각화

Sigak은 SchemaSpy로 PostgreSQL 스키마 기반 HTML ERD를 생성합니다. PostgreSQL은 켜 둔 상태로 실행해도 됩니다. SchemaSpy 컨테이너는 아래 명령을 실행하는 동안에만 잠깐 뜨고, 완료 후 삭제됩니다.

PostgreSQL이 아직 실행 중이 아니라면 먼저 실행합니다.

```bash
docker compose -f infra/docker-compose.yml up -d postgres
```

스키마 문서를 생성합니다.

```bash
docker compose -f infra/docker-compose.yml --profile tools run --rm db-schema
```

생성된 파일은 브라우저에서 열어 확인합니다.

```txt
outputs/db-schema/index.html
```

마이그레이션이나 entity mapping이 바뀌면 다시 생성하면 됩니다. `outputs/` 아래 생성물은 로컬 산출물이므로 커밋하지 않습니다.

SchemaSpy 실행 중 PostgreSQL catalog나 Graphviz label 관련 경고가 출력될 수 있습니다. 명령이 exit code `0`으로 끝나고 `outputs/db-schema/index.html`이 생성되면 정상 생성으로 보면 됩니다.

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
