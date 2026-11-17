#!/bin/sh
# Owns all writes to /backups. The backend can only create small request files in /backup-control.
set -eu
# Without pipefail, a failed pg_dump followed by a successful gzip would count as a good backup
set -o pipefail

control=/backup-control
mkdir -p "$control"
# The backend runs as uid 10001 and can only write request signals here, not to /backups.
chown 10001 "$control"
chmod 0770 "$control"
touch "$control/ready"

cleanup_maintenance() {
  rm -f "$control/maintenance"
}
trap cleanup_maintenance EXIT INT TERM

if [ -f "$control/maintenance" ] && [ ! -f "$control/restore-request" ]; then
  rm -f "$control/maintenance"
  printf '%s\n' error > "$control/status"
fi

write_status() {
  printf '%s\n' "$1" > "$control/status.tmp"
  mv "$control/status.tmp" "$control/status"
}

# One line per finished operation for the admin page: epoch, kind (auto/manual/delete/restore), ok|error, file name
record() {
  printf '%s\t%s\t%s\t%s\n' "$(date +%s)" "$1" "$2" "${3:-}" >> "$control/history"
  tail -n 100 "$control/history" > "$control/history.tmp" && mv "$control/history.tmp" "$control/history"
}

valid_name() {
  printf '%s\n' "$1" | grep -Eq '^testa-[0-9]{8}-[0-9]{6}\.sql\.gz$'
}

keep_days() {
  value=${BACKUP_KEEP_DAYS:-30}
  if [ -f "$control/keep-days" ]; then value=$(cat "$control/keep-days"); fi
  case "$value" in
    ''|*[!0-9]*) value=30 ;;
  esac
  if [ "$value" -lt 1 ] || [ "$value" -gt 365 ]; then value=30; fi
  printf '%s\n' "$value"
}

cleanup_backups() {
  find /backups -name 'testa-*.sql.gz' -mtime "+$(keep_days)" -delete
}

create_backup() {
  file="/backups/testa-$(date +%Y%m%d-%H%M%S).sql.gz"
  while [ -e "$file" ] || [ -e "$file.tmp" ]; do
    sleep 1
    file="/backups/testa-$(date +%Y%m%d-%H%M%S).sql.gz"
  done
  if pg_dump -h db -U testa -d testa --clean --if-exists | gzip > "$file.tmp"; then
    mv "$file.tmp" "$file"
    echo "Backup done: $file"
    return 0
  else
    rm -f "$file.tmp"
    echo "Backup failed" >&2
    return 1
  fi
}

run_backup() {
  write_status backup
  if create_backup; then
    cleanup_backups
    write_status ok
    record "$1" ok "$(basename "$file")"
  else
    write_status error
    record "$1" error
  fi
}

run_delete() {
  name=$(sed -n '1p' "$control/delete-request")
  write_status deleting
  if valid_name "$name" && [ -f "/backups/$name" ] && rm -f "/backups/$name"; then
    rm -f "$control/delete-request"
    write_status ok
    record delete ok "$name"
    echo "Backup deleted: $name"
  else
    rm -f "$control/delete-request"
    write_status error
    record delete error "$name"
    echo "Backup delete failed: $name" >&2
  fi
}

run_restore() {
  name=$(sed -n '1p' "$control/restore-request")
  write_status restoring
  touch "$control/maintenance"
  # Let in-flight API transactions finish before replacing tables.
  sleep 3
  if ! valid_name "$name" || [ ! -f "/backups/$name" ] || ! gzip -t "/backups/$name"; then
    rm -f "$control/restore-request" "$control/maintenance"
    write_status error
    record restore error "$name"
    echo "Backup restore validation failed: $name" >&2
    return
  fi
  # A dump from another schema version would leave the running backend on tables it does not expect.
  # Those restores go through the host procedure, where the backend restarts and Flyway migrates.
  dump_migrations=$(gunzip -c "/backups/$name" | awk '/^COPY public\.flyway_schema_history /{f=1; next} f && /^\\\.$/{exit} f{n++} END{print n+0}')
  live_migrations=$(psql -h db -U testa -d testa -Atc "select count(*) from flyway_schema_history")
  if [ "$dump_migrations" != "$live_migrations" ]; then
    rm -f "$control/restore-request" "$control/maintenance"
    write_status version-mismatch
    record restore error "$name"
    echo "Backup has $dump_migrations migrations, database has $live_migrations; restore it on the host" >&2
    return
  fi
  # Always preserve the current database before replacing it.
  if ! create_backup; then
    rm -f "$control/restore-request" "$control/maintenance"
    write_status error
    record restore error "$name"
    echo "Pre-restore backup failed; restore cancelled" >&2
    return
  fi
  if gunzip -c "/backups/$name" | psql -h db -U testa -d testa -v ON_ERROR_STOP=1 --single-transaction; then
    mv "$control/restore-request" "$control/restore-completed"
    cleanup_backups
    write_status ok
    record restore ok "$name"
    echo "Backup restored: $name"
  else
    rm -f "$control/restore-request"
    write_status error
    record restore error "$name"
    echo "Backup restore failed: $name" >&2
  fi
  rm -f "$control/maintenance"
}

# On first start the backend is still creating tables; wait for them so we do not keep an empty backup
until psql -h db -U testa -d testa -Atc "select 1 from flyway_schema_history limit 1" >/dev/null 2>&1; do
  echo "Waiting for the backend to create the tables..."
  sleep 5
done

next_run=0
while true; do
  now=$(date +%s)
  if [ -f "$control/restore-request" ]; then
    run_restore
  elif [ -f "$control/delete-request" ]; then
    run_delete
  elif [ "$next_run" -eq 0 ] || [ -f "$control/request" ] || [ "$now" -ge "$next_run" ]; then
    kind=auto
    if [ -f "$control/request" ]; then kind=manual; fi
    rm -f "$control/request"
    run_backup "$kind"
    next_run=$((now + 86400))
    printf '%s\n' "$next_run" > "$control/next-run"
  fi
  sleep 2
done
