#!/bin/sh
# Backs up the database to /backups once a day and keeps BACKUP_KEEP_DAYS days (default 30).
#
# Restore (replaces the whole database with the backup; stop the backend first, start it afterwards):
#   docker compose -f docker-compose.prod.yml stop server
#   gunzip -c backups/testa-YYYYmmdd-HHMM.sql.gz | docker compose -f docker-compose.prod.yml exec -T db psql -U testa -d testa -v ON_ERROR_STOP=1 --single-transaction
#   docker compose -f docker-compose.prod.yml start server
#
# Take an extra backup now: docker compose -f docker-compose.prod.yml restart backup
set -eu
# Without pipefail, a failed pg_dump followed by a successful gzip would count as a good backup
set -o pipefail

# On first start the backend is still creating tables; wait for them so we do not keep an empty backup
until psql -h db -U testa -d testa -Atc "select 1 from flyway_schema_history limit 1" >/dev/null 2>&1; do
  echo "等後端把資料表建好…"
  sleep 5
done

while true; do
  file="/backups/testa-$(date +%Y%m%d-%H%M%S).sql.gz"
  if pg_dump -h db -U testa -d testa --clean --if-exists | gzip > "$file.tmp"; then
    mv "$file.tmp" "$file"
    echo "備份完成：$file"
  else
    rm -f "$file.tmp"
    echo "備份失敗" >&2
  fi
  find /backups -name 'testa-*.sql.gz' -mtime +"${BACKUP_KEEP_DAYS:-30}" -delete
  sleep 86400
done
