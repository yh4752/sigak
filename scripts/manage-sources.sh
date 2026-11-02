#!/usr/bin/env bash
set -euo pipefail

# Spring 시작 전에도 worker를 꺼서 운영 설정의 유료 호출이 명령 실행에 섞이지 않게 한다.
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source_action="${1:-list}"
if [[ $# -gt 0 ]]; then shift; fi
case "$source_action" in register|check|configure|enable|disable|list) ;; *) echo 'Use register|check|configure|enable|disable|list' >&2; exit 1;; esac
source_options=("--sigak.sources.command=$source_action")
for source_arg in "$@"; do
    case "$source_arg" in
        --url=*|--key=*|--version=*|--patch=*|--reviewed|--reviewed=*|--feed-reviewed|--feed-reviewed=*|--full|--full=*)
            source_options+=("--sigak.sources.${source_arg#--}") ;;
        *) echo 'Unknown source option' >&2; exit 1 ;;
    esac
done
exec docker compose --env-file "$repo_root/infra/deploy/.env" -f "$repo_root/infra/deploy/compose.yml" \
    run --rm --no-deps -T backend \
    --spring.main.web-application-type=none --spring.main.banner-mode=off --logging.level.root=ERROR \
    --sigak.collection.automation.enabled=false --sigak.summary.enabled=false \
    --sigak.summary.worker-enabled=false --sigak.fulltext.worker-enabled=false "${source_options[@]}"
