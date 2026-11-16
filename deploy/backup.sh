#!/bin/sh
# Pause the single writer, back up both persistent state volumes, then restart it.
set -eu
umask 077
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$project_dir"
backup_dir=${1:-"$project_dir/backups"}
mkdir -p "$backup_dir"
backup_dir=$(CDPATH= cd -- "$backup_dir" && pwd)
backup_path="$backup_dir/newsmap-$(date -u +%Y%m%dT%H%M%SZ).tar.gz"
[ ! -e "$backup_path" ] || { echo 'Backup already exists; retry in a second.' >&2; exit 1; }
compose() {
  if [ -f deploy/.env ]; then
    docker compose --env-file deploy/.env -f deploy/compose.yaml "$@"
  else
    docker compose -f deploy/compose.yaml "$@"
  fi
}
backup_complete=false
restart_backend=false
[ -z "$(compose ps --status running -q backend)" ] || restart_backend=true
cleanup() {
  result=$?
  trap - EXIT HUP INT TERM
  if [ "$restart_backend" = true ]; then compose start backend || result=1; fi
  if [ "$backup_complete" = false ]; then rm -f "$backup_path"; fi
  exit "$result"
}
trap cleanup EXIT
trap 'exit 130' HUP INT TERM
compose stop backend
compose run --rm --no-deps -T --entrypoint python3 backend -c '
import sys, tarfile
with tarfile.open(fileobj=sys.stdout.buffer, mode="w|gz") as archive:
    archive.add("/app/data", arcname="data")
    archive.add("/app/embeddings-service/outputs", arcname="embeddings-service/outputs")
' > "$backup_path"
[ -s "$backup_path" ]
backup_complete=true
echo "Saved private backup: $backup_path"
