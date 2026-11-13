#!/bin/sh
# 每天備份一次資料庫到 /backups，保留 BACKUP_KEEP_DAYS 天（預設 30）。
# 還原：gunzip -c testa-YYYYmmdd-HHMM.sql.gz | docker compose -f docker-compose.prod.yml exec -T db psql -U testa -d testa
set -eu
while true; do
  file="/backups/testa-$(date +%Y%m%d-%H%M).sql.gz"
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
