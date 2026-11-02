# Documentation Freshness Design

날짜: 2026-07-26

## 목적

오랜 공백 뒤에도 개발자가 현재 Sigak의 실제 구현 범위와 다음 작업을 빠르게
이해할 수 있도록, 재진입과 인수인계에 직접 쓰이는 문서를 현재 커밋 기준으로
갱신한다.

## 사실 기준

- 커밋된 코드, 테스트 구조, 설정, 기존 검증 기록이 확인된 기능만 `완료`로 쓴다.
- 초기 `/research` dashboard와 `GET /api/research/overview`는 구현되었다. 집중 backend
  test와 frontend 전체 gate는 통과했지만, 2026-07-12에 Docker와 PostgreSQL이 없어
  backend 전체 gate와 runtime boot smoke는 미검증으로 남았다.
- 운영 문서 RAG 평가 하네스는 설계와 구현 계획이 존재하지만 구현 전이다.
- PostgreSQL은 source of truth이며 Elasticsearch, Qdrant, Neo4j는 rebuildable
  projection store다.

## 범위

다음 문서를 갱신한다.

- `README.md`, `README.ko.md`
- `docs/README.md`, `docs/README.ko.md`
- `docs/ONBOARDING.ko.md`
- `docs/ko/GUIDE.md`
- `docs/STATUS.md`, `docs/STATUS.ko.md`
- `docs/ROADMAP.md`, `docs/ROADMAP.ko.md`

`/research`는 커밋된 experiment artifact를 읽어 보여 주는 read-only evidence view로
설명한다. smoke artifact는 최종 품질 주장이 아니라 재현성 확인용이라는 한계를
유지한다.

영문 진입 문서와 연결된 `README.ko.md`, `docs/README.ko.md`도 같은 현재 상태를
안내해야 한다. 두 한글 문서는 `/research`와 `GET /api/research/overview`의 읽기 전용
경계, smoke-only 품질 한계, 재시작 개발자 읽기 경로를 영문 문서와 모순 없이
전달한다.

## 제외 범위

- Spring Boot, React, FastAPI, Docker Compose 동작을 변경하지 않는다.
- 문서 갱신만으로 기존에 미검증인 backend 전체 gate와 boot smoke가 통과했다고
  기록하지 않는다.
- API 계약과 제품 범위는 현재 계약 변경이 없으므로 수정하지 않는다.

## 검증

- 변경 문서의 내부 링크와 경로가 실제로 존재해야 한다.
- Neo4j가 예정이라는 오래된 현재 상태 표현이 남지 않아야 한다.
- `git diff --check`가 통과해야 한다.

## 검증 결과

아래는 2026-07-26에 이 문서 갱신 작업에서 실제로 다시 실행한 검증이다.
애플리케이션 코드를 바꾸지 않은 문서 전용 작업이므로 backend, frontend, AI test
suite는 이번 작업에서 실행하지 않았다. `/research`의 focused backend test와 frontend
전체 gate 통과, backend 전체 gate와 runtime boot smoke의 미검증 경계는 2026-07-12의
기록된 결과를 그대로 인용한 것이다.

1. 사용자-facing 재진입·상태·로드맵 문서 열 개의 Markdown 링크를 검사했다. Node
   스크립트가 inline Markdown link를 찾고, `http(s)`와 `mailto`를 제외한 대상은 각
   원본 문서의 디렉터리에서 해석해 존재 여부를 확인했다.

   ```bash
   node - <<'NODE'
   const fs = require('fs');
   const path = require('path');
   const files = [
     'README.md', 'README.ko.md', 'docs/README.md', 'docs/README.ko.md',
     'docs/ONBOARDING.ko.md', 'docs/ko/GUIDE.md', 'docs/STATUS.md',
     'docs/STATUS.ko.md', 'docs/ROADMAP.md', 'docs/ROADMAP.ko.md',
   ];
   const missing = [];
   let checkedLinks = 0;
   const linkPattern = /!?\[[^\]]*\]\(([^)]+)\)/g;
   for (const file of files) {
     for (const match of fs.readFileSync(file, 'utf8').matchAll(linkPattern)) {
       let target = match[1].trim();
       if (target.startsWith('<') && target.endsWith('>')) target = target.slice(1, -1);
       target = target.split(/\s+(?=(?:\"|'))/)[0];
       if (!target || target.startsWith('#') || /^(?:https?:|mailto:)/i.test(target)) continue;
       checkedLinks += 1;
       const localTarget = target.split('#', 1)[0];
       if (localTarget && !fs.existsSync(path.resolve(path.dirname(file), localTarget))) {
         missing.push(`${file} -> ${target}`);
       }
     }
   }
   if (missing.length) process.exitCode = 1;
   else console.log(`markdown-links-ok (${files.length} files, ${checkedLinks} local links)`);
   NODE
   ```

   결과: `markdown-links-ok (10 files, 81 local links)`.

2. 재진입·인수인계·개념 가이드에서 오래된 현재 상태 표현을 검사했다.

   ```bash
   if rg -n -e 'Neo4j.*예정' -e 'graph projection 예정' -e '2026-05-07' \
     README.md README.ko.md docs/README.md docs/README.ko.md \
     docs/ONBOARDING.ko.md docs/ko/GUIDE.md; then
     exit 1
   else
     rg_status=$?
     if [ "$rg_status" -eq 1 ]; then echo stale-current-state-claims-ok; else exit "$rg_status"; fi
   fi
   ```

   결과: 일치 항목이 없었고 `stale-current-state-claims-ok`을 출력했다.

3. 전체 문서 전용 diff의 공백 오류와 사실 경계를 확인했다. 링크 검사의 열 문서와
   달리 이 검사는 설계와 계획을 포함한 전체 범위를 파일 경로 필터 없이 대상으로
   한다.

   ```bash
   git diff --name-only aae2242..HEAD
   git diff --unified=8 aae2242..HEAD
   git diff --check aae2242..HEAD
   ```

   결과: `git diff --name-only aae2242..HEAD`는 `README.md`, `README.ko.md`, 두 문서
   인덱스, 인수인계 가이드, 개념 가이드, 상태 문서 두 개, 로드맵 두 개, 이 설계·계획
   문서를 반환했다. 즉 사용자-facing 문서 10개와 설계·계획 문서 2개로, 총 12개
   문서였다. 파일 경로 필터 없이 전체 diff를 검토했고
   `git diff --check aae2242..HEAD`도 통과했다. `/research`와
   `GET /api/research/overview`의 구현 사실 및 2026-07-12 기록의 검증·미검증 경계
   밖의 새 품질·성능·실행 결과 주장은 추가하지 않았다.

## 완료 조건

재진입 개발자는 `ONBOARDING.ko.md -> STATUS.ko.md -> ROADMAP.ko.md -> Ops RAG 평가
설계/계획` 순서로 읽어 현재 구현과 다음 작업을 판단할 수 있어야 한다.

한글 동반 진입 문서를 추가한 뒤에는 링크 검사와 오래된 상태 표현 검사를 다시
실행하고, `aae2242..HEAD` 전체 문서 diff 범위를 새 파일 수에 맞게 기록한다.
