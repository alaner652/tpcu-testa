#!/bin/sh
# Backs up the database to /backups once a day and keeps BACKUP_KEEP_DAYS days (default 30).
#
# Restore (replaces the whole database with the backup; stop the backend first, start it afterwards):
#   docker compose -f docker-compose.prod.yml stop server
#   gunzip -c backups/testa-YYYYmmdd-HHMM.sql.gz | docker compose -f docker-compose.prod.yml exec -T db psql -U testa -d testa -v ON_ERROR_STOP=1 --single-transaction
#   docker compose -f docker-compose.prod.yml start server
#
# The admin panel requests an extra backup by creating /backup-control/request.
set -eu
# Without pipefail, a failed pg_dump followed by a successful gzip would count as a good backup
set -o pipefail

control=/backup-control
mkdir -p "$control"
# The backend runs as uid 10001 and can only write request signals here, not to /backups.
chown 10001 "$control"
chmod 0770 "$control"
touch "$control/ready"

write_status() {
  printf '%s\n' "$1" > "$control/status.tmp"
  mv "$control/status.tmp" "$control/status"
}

run_backup() {
  write_status running
  file="/backups/testa-$(date +%Y%m%d-%H%M%S).sql.gz"
  if pg_dump -h db -U testa -d testa --clean --if-exists | gzip > "$file.tmp"; then
    mv "$file.tmp" "$file"
    write_status ok
    echo "Backup done: $file"
  else
    rm -f "$file.tmp"
    write_status error
    echo "Backup failed" >&2
  fi
  find /backups -name 'testa-*.sql.gz' -mtime +"${BACKUP_KEEP_DAYS:-30}" -delete
}

# On first start the backend is still creating tables; wait for them so we do not keep an empty backup
until psql -h db -U testa -d testa -Atc "select 1 from flyway_schema_history limit 1" >/dev/null 2>&1; do
  echo "Waiting for the backend to create the tables..."
  sleep 5
done

next_run=0
while true; do
  now=$(date +%s)
  if [ "$next_run" -eq 0 ] || [ -f "$control/request" ] || [ "$now" -ge "$next_run" ]; then
    rm -f "$control/request"
    run_backup
    next_run=$((now + 86400))
  fi
  sleep 2
done
