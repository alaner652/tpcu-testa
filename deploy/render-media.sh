#!/bin/sh
set -eu

MODE=${1:-videos}
case "$MODE" in
  videos|screenshots|all) ;;
  *) echo "usage: $0 [videos|screenshots|all]" >&2; exit 2 ;;
esac

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ROOT_DIR=$(dirname "$SCRIPT_DIR")
COMPOSE_FILE="$SCRIPT_DIR/media.compose.yml"

export MEDIA_UID="$(id -u)"
export MEDIA_GID="$(id -g)"

cleanup() {
  docker compose -f "$COMPOSE_FILE" down --volumes --remove-orphans
}
trap cleanup EXIT INT TERM

cd "$ROOT_DIR"
docker compose -f "$COMPOSE_FILE" up -d --build db server web

RUN='node -e '\''let n=0; const poll=()=>fetch("http://web/api/health").then(r=>{if(!r.ok)throw Error(r.status)}).then(()=>process.exit(0)).catch(e=>{if(++n>=120){console.error(e);process.exit(1)}setTimeout(poll,1000)});poll()'\'''
case "$MODE" in
  videos) RUN="$RUN && node tools/record-videos.mjs" ;;
  screenshots) RUN="$RUN && node tools/capture-screenshots.mjs" ;;
  all) RUN="$RUN && node tools/record-videos.mjs && node tools/capture-screenshots.mjs" ;;
esac
RUN="$RUN && chown -R \"\$MEDIA_UID:\$MEDIA_GID\" testa/public/manual docs/images"

docker compose -f "$COMPOSE_FILE" run --rm renderer sh -lc "$RUN"
