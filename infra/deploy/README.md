# Sigak 단일 서버 배포

React 화면과 실제 Spring Boot API를 Caddy 한 주소로 연결한다. PostgreSQL, Elasticsearch, Qdrant, Neo4j, FastAPI는 Compose 내부에서 통신한다. Sites의 정적 화면 배포로 기존 JVM 백엔드를 대체하지 않는다.

이 구성은 로컬 검증과 서버 이전을 위한 것이다. 클라우드 계정·서버·도메인은 자동으로 구매하거나 생성하지 않는다. 기본 접속은 `http://127.0.0.1:8088`이며 인터넷 공개 URL이 아니다.

## 최초 실행

필수 입력은 Docker Engine/Compose와 소스 체크아웃이다. 저장소 루트에서 실행한다. 첫 이미지 빌드는 네트워크와 캐시에 따라 수 분 이상 걸릴 수 있다. PostgreSQL 원본 데이터는 deployment 전용 named volume에 보존하며 기존 개발용 DB volume을 공유하지 않는다.

다음 명령은 `.env`가 없을 때만 서로 다른 비밀번호를 생성한다. 재실행해도 기존 비밀번호를 바꾸지 않는다. 이 파일은 Git에서 제외된다.

```bash
python3 - <<'PY'
from pathlib import Path
import secrets
path = Path('infra/deploy/.env')
if not path.exists():
    text = Path('infra/deploy/.env.example').read_text()
    text = text.replace('SIGAK_DB_PASSWORD=\n', 'SIGAK_DB_PASSWORD=' + secrets.token_hex(24) + '\n')
    text = text.replace('SIGAK_NEO4J_PASSWORD=\n', 'SIGAK_NEO4J_PASSWORD=' + secrets.token_hex(24) + '\n')
    path.write_text(text)
    path.chmod(0o600)
print('deployment environment ready')
PY
docker compose --env-file infra/deploy/.env -f infra/deploy/compose.yml config --quiet
docker compose --env-file infra/deploy/.env -f infra/deploy/compose.yml up -d --build
docker compose --env-file infra/deploy/.env -f infra/deploy/compose.yml ps
```

`config --quiet`는 설정 유효성만 검사한다. 실제 비밀번호가 포함된 resolved config를 로그나 채팅에 붙이지 않는다. DB/Neo4j 비밀번호가 비어 있으면 시작을 거부한다.

Backend가 시작된 후 다음 검사를 실행한다. 일시적인 502이면 backend 초기화가 끝났는지 `ps`와 backend log를 확인한 뒤 다시 실행한다.

```bash
python3 infra/deploy/smoke_test.py http://127.0.0.1:8088
```

검사 항목은 홈/상세/research 새로고침, 실제 기사 목록·검색·상세·graph context, research artifact, 내부 API 404, 쓰기 method 405, 숨김 파일 404다. 성공 표시는 `Ran 6 tests`와 `OK`다.

새 DB에는 Flyway seed가 들어간다. 기존 개발 DB의 수집 기사 41개가 자동으로 복사되지는 않는다. 데이터 이전을 원하면 별도 백업/복원 절차를 먼저 수행해야 한다.

## 검색 projection 준비 — 운영자만 실행

처음에는 검색 index가 없으므로 PostgreSQL fallback으로 기사를 검색할 수 있다. Elasticsearch/Qdrant/Neo4j/AI가 준비된 후 내부 네트워크에서 projection을 재생성한다. 원본 PostgreSQL 기사는 삭제하지 않는다. Local embedding 모델은 첫 호출에 내려받을 수 있어 이 단계는 최초 실행 시 시간이 더 걸린다.

```bash
docker compose --env-file infra/deploy/.env -f infra/deploy/compose.yml exec -T ai python - <<'PY'
import json
from urllib.request import Request, urlopen
for projection in ('search-projections/articles', 'search-projections/article-vectors', 'graph-projections/articles'):
    url = 'http://backend:8080/api/internal/' + projection + '/rebuild'
    with urlopen(Request(url, method='POST'), timeout=180) as response:
        print(projection, json.load(response))
PY
```

Public edge에서는 내부 수집·rebuild·metrics API에 접근할 수 없다. 서버 shell이나 제한된 관리 연결을 통해서만 운영한다. 기사 조회와 검색, 상세 graph context, research overview만 GET/HEAD로 허용한다.

## 공개 서버로 이전

서버 계정과 비용을 정한 후 Docker를 설치하고 이 저장소를 배치한다. 공개 서버에서는 개발용 `infra/docker-compose.yml`을 사용하지 않는다. 개발용 구성은 DB·검색 엔진 포트를 host에 노출한다.

`infra/deploy/.env`에서 아래 값을 서버 환경에 맞춰 변경한다.

| 설정 | 서버 값 |
| --- | --- |
| `SIGAK_BIND_ADDRESS` | `0.0.0.0` |
| `SIGAK_HTTP_PORT` | `80` |
| `SIGAK_HTTPS_PORT` | `443` |
| `SIGAK_SITE_ADDRESS` | 소유한 도메인 이름, 예: `sigak.example.com` |

도메인 DNS가 서버를 가리키고 외부 80/443 연결이 가능해야 Caddy가 인증서를 발급받는다. 도메인 연결 전에는 `SIGAK_SITE_ADDRESS=:80`으로 HTTP 연결을 확인할 수 있다. 서버 방화벽은 웹 80/443과 제한된 관리자 SSH만 허용하고 DB/검색 서비스 포트를 열지 않는다.

도메인·외부 접속·실제 인증서 발급은 서버가 준비된 뒤 검증할 항목이다. 현재 로컬 smoke 성공만으로 이를 완료했다고 간주하지 않는다. Compose가 동작하는 한 서버의 구조이며 다중 서버 HA, 자동 백업, 자동 기사 수집 스케줄러는 포함하지 않는다. 원격 서버 CPU/메모리 여유는 전체 embedding/search 서비스 부하를 측정한 뒤 결정한다.

공식 동작 참고: [Caddy routing](https://caddyserver.com/docs/caddyfile/directives/handle), [request matchers](https://caddyserver.com/docs/caddyfile/matchers), [automatic HTTPS](https://caddyserver.com/docs/automatic-https), [Compose 환경변수](https://docs.docker.com/compose/how-tos/environment-variables/variable-interpolation/).

## 중지·재시작·데이터 보존

```bash
docker compose --env-file infra/deploy/.env -f infra/deploy/compose.yml stop
docker compose --env-file infra/deploy/.env -f infra/deploy/compose.yml up -d
```

`stop`은 volume을 지우지 않는다. 데이터 보존을 위해 `down -v`를 실행하지 않는다. 기존 DB가 있는 상태에서 `.env` 비밀번호만 바꾸면 DB 사용자 비밀번호가 자동으로 바뀌지 않으므로 기존 파일을 보존한다. `.env`와 DB 백업은 접근을 제한한 별도 저장소에 보관한다.

## 계정 없이 임시 공유 주소 만들기

[Cloudflare Quick Tunnels](https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/)는 계정·도메인 없이 임시 HTTPS 주소를 만든다. 이 기능은 시연용이고 상시 서비스의 가용성을 보장하지 않는다. 컴퓨터와 Docker, tunnel 프로세스가 계속 실행돼야 하며 재시작 시 주소가 바뀐다.

위 deployment가 실행 중일 때 별도 터미널에서 실행한다. DB나 backend를 직접 연결하지 않고 검증된 Caddy 웹 경계에만 연결한다.

```bash
docker run --rm --name sigak-demo-tunnel --network sigak-deploy_default cloudflare/cloudflared:latest tunnel --no-autoupdate --url http://web:80
```

로그에 표시된 `https://…trycloudflare.com` 주소를 브라우저에서 연다. 이 주소를 아는 사람은 샘플 기사와 공개 연구 화면을 볼 수 있다. 내부 API 제한은 그대로 적용된다. 새 DB를 사용하면 샘플 5개가 보이며 최신 기사 자동 수집 서비스로 간주하지 않는다.

```bash
python3 infra/deploy/smoke_test.py https://실제로-발급된-주소.trycloudflare.com
docker stop sigak-demo-tunnel
```

첫 줄은 실제 발급 주소로 바꿔 연결 확인할 때 사용한다. 둘째 줄은 임시 공유를 중단할 때만 실행한다. Docker 이미지의 소프트웨어는 공식 Cloudflare 배포물이며 호스팅 계정이나 유료 서버를 생성하지 않는다.
